package com.syncari.connector.service;

import com.sforce.soap.partner.Error;
import com.sforce.soap.partner.*;
import com.sforce.soap.partner.sobject.SObject;
import com.sforce.ws.bind.XmlObject;
import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.connector.data.MergeRequest;
import com.syncari.connector.data.*;
import com.syncari.utils.Storage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class Transformer {

    public final static Map<String, String> DOC_OBJECT_CONTENT_FIELD = Map.of("Document", "Body", "ContentDocument", "VersionData", "Attachment", "Body");

	public SyncResponse toSyncResponse(SaveResult[] results, List<EntityData> entityList, Operation op) {
		SyncResponse response = new SyncResponse();
		assert results.length == entityList.size() : String.format("Results length %s not same as entities sent %s",
				results.length, entityList.size());
		for (int i = 0; i < results.length; i++) {
			SaveResult r = results[i];
			log.debug("Save result - {}", r);
			Result result = new Result(r.isSuccess(), !StringUtils.isBlank(r.getId()) ? r.getId() : entityList.get(i).getId(), entityList.get(i).getSyncariEntityId());
			for (Error err : r.getErrors()) {
				String message = "cannot reference converted lead";
				String statusCode = "CANNOT_UPDATE_CONVERTED_LEAD";
				if(message.equalsIgnoreCase(err.getMessage()) && statusCode.equalsIgnoreCase(err.getStatusCode().name())) {
				    result.setSuccess(true);
				} else if (err instanceof DuplicateError) {
					var duplicateError = (DuplicateError) err;

					var duplicateIds = Optional.ofNullable(duplicateError.getDuplicateResult())
							.map(d -> Arrays.stream(d.getMatchResults()).flatMap(m -> Arrays.stream(m.getMatchRecords()).filter(rec -> rec.getRecord() != null)
									.map(re -> re.getRecord().getId())).collect(Collectors.joining(","))).orElse("");

					String duplicateRule = Optional.ofNullable(duplicateError.getDuplicateResult()).map(d -> d.getDuplicateRule()).orElse("");
					String error = String.format("%s Duplicate Rule: %s. IDs of the Duplicate records: %s", err.getMessage(), duplicateRule, duplicateIds);
					result.getErrors().add(error);
				}else {
				    result.getErrors().add(err.getMessage());
				}
			}
			if(result.isSuccess() && result.getId() == null && op != Operation.update) {
				log.debug("Id is null for syncari id - {}", result.getSyncariId());
				result.setSuccess(false);
				result.getErrors().add("ID is missing in the salesforce response. Hence marking this as a failure");
			}
			response.getResults().add(result);
		}
		return response;
	}

	public SyncResponse toSyncResponse(DeleteResult[] results, Map<String, String> syncariIdBySfdcIdMap) {
		SyncResponse response = new SyncResponse();
		for (DeleteResult r : results) {
			Result result = new Result(r.isSuccess(), r.getId());
            if (syncariIdBySfdcIdMap.containsKey(r.getId())) {
                result.setSyncariId(syncariIdBySfdcIdMap.get(r.getId()));
            }
			for (Error err : r.getErrors()) {
				result.getErrors().add(err.getMessage());
			}
			response.getResults().add(result);
		}
		return response;
	}

	public SyncResponse toSyncResponse(LeadConvertResult[] results) {
		SyncResponse response = new SyncResponse();
		for (LeadConvertResult r : results) {
			Result result = new Result(r.isSuccess(), null);
			for (Error err : r.getErrors()) {
				result.getErrors().add(err.getMessage());
			}
			// TODO set acc/cont and oppty id in response
			response.getResults().add(result);
		}
		return response;
	}

	public MergeResponse toSyncResponse(MergeRequest request, MergeResult[] results) {
	    MergeResponse response = new MergeResponse();
	   for(MergeResult result : results) {
	       response.combine(toMergeResponse(request, result));
	   }
	   return response;
	}
	
    private MergeResponse toMergeResponse(MergeRequest request, MergeResult result) {
        MergeResponse response = new MergeResponse();
        var winnerResult = new Result(result.isSuccess(),result.getId());
        for (Error err : result.getErrors()) {
            winnerResult.addError(err.getMessage());
        }
        //wrap in arraylist to make it mutable
        response.setWinnerResult(new SyncResponse().setResults(new ArrayList<>(List.of(winnerResult))));
        
        List<Result> loserResults= new ArrayList<>();
        for(String loserId : result.getMergedRecordIds()) {
            request.findLoser(loserId).ifPresentOrElse(loser -> {
                loserResults.add(new Result(result.isSuccess(),loserId, loser.getSyncariEntityId()));
            }, () ->{
                loserResults.add(new Result(result.isSuccess(),loserId));
            });
        }
        response.setLoserResult(new SyncResponse(result.isSuccess()).setResults(loserResults));
        return response;
    }

	public SObject[] toSObjects(SyncRequest request, List<EntityData> entityList) {
		SObject[] sObjects = new SObject[entityList.size()];
		int i = 0;
		for (EntityData entityData : entityList) {
			sObjects[i] = toSObject(request.getEntitySchema(), request.getStorage(), entityData);
			i++;
		}
		return sObjects;
	}

	public SObject toSObject(EntitySchema entitySchema, Storage storage, EntityData entityData) {
        entityData = handleDocumentEntity(entitySchema, entityData);
        SObject obj = new SObject(entityData.getName());
		obj.setId(entityData.getId());
		List<String> nullFields = new ArrayList<>();
        List<String> fileLinks = entitySchema.getFileLinkAttributes().stream().map(x -> x.getApiName()).collect(Collectors.toList());
		entityData.getValues().forEach((k, v) -> {
            // File links and files references are to be ignored.
            if (fileLinks.contains(k.toString())) return;
            if (EntityData.SYNCARI_FILE_REFERENCE_FIELD_NAME.equalsIgnoreCase(k.toString())) return;
			if (v != null) {
				if (entitySchema.getField(k).filter(a -> a.getDataType().equalsIgnoreCase("picklist")).isPresent()) {
					if (List.class.isAssignableFrom(v.getClass())) {
						List<Object> values = (List)v;
						if (values.size() > 0) {
							List<String> vals = values.stream().map(x -> Objects.toString(x)).collect(Collectors.toList());
							obj.setField(k, String.join(";", vals));
						} else {
							// Reset the picklist
							nullFields.add(k);
						}
					} else {
						obj.setField(k, v.toString());
					}
				} else if (ZonedDateTime.class.isAssignableFrom(v.getClass())) {
					obj.setField(k, toCalendar((ZonedDateTime) v));
				} else if (Long.class.isAssignableFrom(v.getClass())) {
					// Salesforce does not have Long, it only supports int
					// See https://developer.salesforce.com/docs/atlas.en-us.object_reference.meta/object_reference/primitive_data_types.htm
					obj.setField(k, ((Long) v).intValue());
				} else {
					obj.setField(k, v);
				}
			} else {
				nullFields.add(k);
			}
		});
		if(!nullFields.isEmpty()){
			obj.setFieldsToNull(nullFields.toArray(new String[nullFields.size()]));
		}
        if (SalesforceService.SFDC_DOCUMENT_OBJECTS.contains(entitySchema.getApiName())) {
            // Process file links. I.e, upload them as stream.
            if (storage != null && !fileLinks.isEmpty() && 
                StringUtils.isNotEmpty(entityData.getValueAsString(EntityData.SYNCARI_FILE_LINK_FIELD_NAME))) {
                String syncariFileLink = entityData.getValueAsString(EntityData.SYNCARI_FILE_LINK_FIELD_NAME);
                try (InputStream is = storage.read(syncariFileLink)) {
                    obj.setField(DOC_OBJECT_CONTENT_FIELD.get(entitySchema.getApiName()), is.readAllBytes());
                } catch (IOException e) {
                    throw new RuntimeException(String.format("Failed to read file %s ", syncariFileLink), e);
                }
            }
        }
        log.debug("Salesforce object - {}", obj);
		return obj;
	}

    private EntityData handleDocumentEntity(EntitySchema entitySchema, EntityData entityData) {
        if ("ContentDocument".equalsIgnoreCase(entitySchema.getApiName())) {
            EntityData contentVersionEntityData = new EntityData("ContentVersion");
            if (StringUtils.isNotEmpty(entityData.getId())) {
                contentVersionEntityData.addValue("ContentDocumentId", entityData.getId());
            }
            contentVersionEntityData.addValue("Title", entityData.getValue("Title"));
            contentVersionEntityData.addValue("IsMajorVersion", true);
            contentVersionEntityData.addValue("PathOnClient", entityData.getValue("Title"));
            contentVersionEntityData.addValue("Description", entityData.getValue("Description"));
            contentVersionEntityData.addValue(EntityData.SYNCARI_FILE_LINK_FIELD_NAME, 
                entityData.getValue(EntityData.SYNCARI_FILE_LINK_FIELD_NAME));
            return contentVersionEntityData;
        }
        return entityData;
    }

	private GregorianCalendar toCalendar(ZonedDateTime v) {
		return GregorianCalendar.from(v);
	}

	public List<EntityData> toEntityData(String connectorId, String entityName, SObject[] results,
			List<AttributeSchema> attributes) {

		List<EntityData> response = new ArrayList<>();

		String watermarkFieldName = "SystemModstamp";

		if (StringUtils.endsWithIgnoreCase(entityName, "History")){
			Optional<AttributeSchema> watermarkField = attributes.stream().filter(a -> a.isWatermarkField()).findFirst();
			if(watermarkField.isPresent() && !"SystemModstamp".equalsIgnoreCase(watermarkField.get().getApiName())){
				watermarkFieldName = watermarkField.get().getApiName();
			}
		}

		for (SObject r : results) {
			EntityData data = new EntityData(entityName);
			data.setIgnoreFieldChanges(Set.of(watermarkFieldName,"LastModifiedDate"));
			data.setId(r.getId());
			if (r.getSObjectField(watermarkFieldName) != null) {
				data.setLastModified(
						ZonedDateTime.parse(r.getSObjectField(watermarkFieldName).toString()).toInstant().toEpochMilli());
			}
			if (r.getSObjectField("CreatedDate") != null) {
				data.setCreatedAt(
						ZonedDateTime.parse(r.getSObjectField("CreatedDate").toString()).toInstant().toEpochMilli());
			}

			data.setConnectorId(connectorId);
			for (AttributeSchema attr : attributes) {

				Object value = r.getField(attr.getApiName());
				if (!(value instanceof XmlObject)) {
				    if("IsDeleted".equalsIgnoreCase(attr.getApiName()) && value != null && (Boolean.valueOf(value.toString()))) {
				        data.setDeleted(true);
				    } else if ("picklist".equalsIgnoreCase(attr.getDataType().toLowerCase()) && attr.isMultiValueField() &&
                        !Objects.isNull(value)) {
                        String[] values = value.toString().split(";");
                        List<String> vals = new ArrayList<>();
                        if (values.length > 0) {
                            vals = Arrays.asList(values);
                        } else {
                            vals.add(value.toString());
                        }
                        value = vals;
                    }
					data.addValue(attr.getApiName(), value);
				} else {
					log.warn(String.format(
							"Value for attribute '%s' not proccessed in SFDC for entity %s because its a complex XML type",
							attr.getApiName(), entityName));
				}
			}
			response.add(data);
		}
		return response;
	}

	public List<EntityData> toEntityData(String connectorId, SObject[] results, String[] fields, String entity) {
		List<EntityData> response = new ArrayList<>();
		for (SObject r : results) {
			EntityData data = new EntityData(entity);
			data.setId(r.getId());
			if (r.getSObjectField("CreatedDate") != null) {
				data.setCreatedAt(
						ZonedDateTime.parse(r.getSObjectField("CreatedDate").toString()).toInstant().toEpochMilli());
			}
			data.setConnectorId(connectorId);
			for(String field : fields) {
				Object value = r.getField(field);
				if (!(value instanceof XmlObject)) {
					if("IsDeleted".equalsIgnoreCase(field) && value != null && (Boolean.valueOf(value.toString()))) {
						data.setDeleted(true);
					}
					if(field.contains(".")) {
					    String[] parts = field.split("\\.");
					    if(parts.length == 2 && r.hasChildren()) {
							XmlObject child = r.getChild(parts[0]);
							if(child != null) {
								XmlObject child1 = child.getChild(parts[1]);
								if(child1 != null) {
									data.addValue(field, child1.getValue());
								}
							}
						}
					} else {
						data.addValue(field, value);
					}
				} else {
					log.warn(String.format(
							"Value for attribute '%s' not proccessed in SFDC for entity %s because its a complex XML type",
							field, data.getName()));
				}
			}
			response.add(data);
		}
		return response;
	}

    public List<EntityData> toContentDocumentFileEntityData(String entityName, SObject[] results, String fileAttrName) {
        List<EntityData> response = new ArrayList<>();
		for (SObject r : results) {
			EntityData data = new EntityData(entityName);
            data.setId(r.getId());
            Object value = r.getField(fileAttrName);
            data.addValue(fileAttrName, value);
            response.add(data);
        }
        return response;
    }

}
