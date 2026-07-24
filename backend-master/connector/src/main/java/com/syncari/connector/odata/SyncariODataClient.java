package com.syncari.connector.odata;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.connector.auth.SyncariOauth2HttpClientFactory;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.ODataEntityDataIterator;
import com.syncari.connector.exception.HttpException;
import com.syncari.connector.service.MsDynamicsService;
import com.syncari.utils.DateUtil;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.olingo.client.api.ODataClient;
import org.apache.olingo.client.api.communication.request.batch.BatchManager;
import org.apache.olingo.client.api.communication.request.batch.ODataBatchResponseItem;
import org.apache.olingo.client.api.communication.request.batch.ODataChangeset;
import org.apache.olingo.client.api.communication.request.cud.ODataDeleteRequest;
import org.apache.olingo.client.api.communication.request.cud.ODataEntityCreateRequest;
import org.apache.olingo.client.api.communication.request.cud.ODataEntityUpdateRequest;
import org.apache.olingo.client.api.communication.request.cud.UpdateType;
import org.apache.olingo.client.api.communication.request.retrieve.ODataRawRequest;
import org.apache.olingo.client.api.communication.response.*;
import org.apache.olingo.client.api.domain.ClientEntity;
import org.apache.olingo.client.api.domain.ClientObjectFactory;
import org.apache.olingo.client.api.domain.ClientPrimitiveValue;
import org.apache.olingo.client.api.uri.URIBuilder;
import org.apache.olingo.client.core.ODataClientFactory;
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static com.syncari.connector.ConnectorHelper.withBackoffAndErrorHandling;

@Slf4j
@Builder
public class SyncariODataClient {
    ConnectorInfo connector;
    ObjectMapper mapper;
    DateUtil dateUtil;
    String namespace;
    String serviceURL;
    String oAuthURL;
    @Builder.Default
    private ODataClient client = null;

    // The max allowed is 199 here however we are seeing TimedoutExceptions.
    private static final int POST_BATCH_SIZE = 25;

    public ODataClient getODataClient() {
        if (client == null) {
            AuthConfig config = connector.getAuthConfig();
            final SyncariOauth2HttpClientFactory oauth2HCF = 
                new SyncariOauth2HttpClientFactory(config.getAccessToken(), oAuthURL);
            client = ODataClientFactory.getClient();
            client.getConfiguration().setHttpClientFactory(oauth2HCF);
            client.getConfiguration().setDefaultPubFormat(ContentType.APPLICATION_JSON);
        }
        return client;
    }

    public String getServiceURL() {
        return serviceURL;
    }

    public List<Map<String, Object>> executeODataRequest(URI absoluteUri) 
            throws IOException {
        return withBackoffAndErrorHandling(() -> {
            ODataRawRequest request = getODataClient().getRetrieveRequestFactory().getRawRequest(absoluteUri);
            request.setAccept("application/json; charset=utf-8");
            log.info("Odata request: {}", request.getHttpRequest());
            ODataRawResponse oDataRawResponse = request.execute();
            Map<String, Object> result = mapper.readValue(oDataRawResponse.getRawResponse(), 
                new TypeReference<Map<String, Object>>(){});
            if (oDataRawResponse != null) {
                oDataRawResponse.close();
            }
            return mapper.convertValue(result.get("value"), new TypeReference<List<Map<String, Object>>>(){});
        });
    }

    public Map<String, List<String>> getEntityPickListValues(String entityName) 
        throws IOException {
        final String picklistMetadataName = namespace + ".PicklistAttributeMetadata";
        URI uriMod = URI.create(serviceURL + "/EntityDefinitions(LogicalName='" + entityName + 
            "')/Attributes/" + picklistMetadataName + "?$select=LogicalName&$expand=OptionSet,GlobalOptionSet");
        List<Map<String, Object>> values = executeODataRequest(uriMod);
        if (values.size() == 0) {
            return new HashMap<>();
        }
        final Map<String, List<String>> pickListValsByKey = new HashMap<>();
        for (Map<String, Object> optionSets: values) {
            Map<String, Object> optionSet = 
                mapper.convertValue(optionSets.get("OptionSet"), new TypeReference<Map<String, Object>>(){});
            List<Map<String, Object>> options = 
                mapper.convertValue(optionSet.get("Options"), new TypeReference<List<Map<String, Object>>>(){});
            for (Map<String, Object> option: options) {
                if (!pickListValsByKey.containsKey(optionSets.get("LogicalName").toString())) {
                    pickListValsByKey.put(optionSets.get("LogicalName").toString(), new ArrayList<String>());
                }
                pickListValsByKey.get(optionSets.get("LogicalName").toString()).add(option.get("Value").toString());
            }
        }
        return pickListValsByKey;
    }

    public ODataEntityDataIterator query(String filter, SyncRequest request) {
        ODataEntityDataIterator iterator = null;
        int limit = (request.getWatermark() != null && request.getWatermark().getLimit() > 0) ? 
            request.getWatermark().getLimit() : 0;
        try {
            log.info(filter);
            List<AttributeSchema> attributes = request.getEntitySchema().getAttributes();
            final String entityName = request.getEntityName().toLowerCase();
            iterator = ODataEntityDataIterator.builder().attributes(attributes).entityName(entityName)
                    .client(getODataClient())
                    .entityPluralName(request.getEntitySchema().getPluralName())
                    .isInitial(request.getWatermark() == null ? false : request.getWatermark().isInitial())
                    .isInitial(request.getWatermark() == null ? false : request.getWatermark().isInitial())
                    .offset(request.getWatermark() == null ? 0 : request.getWatermark().getOffset()).dateUtil(dateUtil)
                    .serviceUri(serviceURL)
                    .filter(filter)
                    .limit(limit)
                    .pageSize(request.getPageSize())
                    .connectorId(request.getConnector().getId())
                    .watermarkInfo(request.getWatermark())
                    .build();

            //logLimits(request.getConnector(), conn);

        } catch (Exception e) {
            log.error("Failed to query records for filter {} ", filter, e);
            ConnectorHelper.handleException(e);
        }
        return iterator;
    }

    private List<ClientEntity> toODataEntities(EntitySchema entitySchema, ClientObjectFactory factory, String entityName, 
        List<EntityData> entityDatas, String serviceURL) {
        List<ClientEntity> oDataCEs = new ArrayList<>();
        for (EntityData entityData: entityDatas) {
            final ClientEntity entity = factory.newEntity(new FullQualifiedName(namespace, entitySchema.getPluralName()));
            // For updates and deletes we need to explicitly set the id column.
            if (StringUtils.isNotEmpty(entityData.getId())) {
                entity.getProperties().add(
                    factory.newPrimitiveProperty(entitySchema.getIdField().getApiName(), 
                        factory.newPrimitiveValueBuilder().buildString(entityData.getId().toString())));
            }
            List<String> nullFields = new ArrayList<>();
            entityData.getValues().forEach((k, v) -> {
                Optional<AttributeSchema> attr = entitySchema.getField(k);
                attr.ifPresent(at -> {
                    if (v != null) {
                        if (ZonedDateTime.class.isAssignableFrom(v.getClass())) {
                            entity.getProperties().add(
                                factory.newPrimitiveProperty(k, 
                                    factory.newPrimitiveValueBuilder().setType(EdmPrimitiveTypeKind.Date)
                                        .buildString(v.toString())));
                        } else if (Date.class.isAssignableFrom(v.getClass())) {
                            entity.getProperties().add(
                                    factory.newPrimitiveProperty(k,
                                            factory.newPrimitiveValueBuilder().setType(EdmPrimitiveTypeKind.Date)
                                                    .buildString(DateUtil.format((Date) v, DateUtil.dateOnlyFormat))));
                        } else if (Double.class.isAssignableFrom(v.getClass())) {
                            entity.getProperties().add(
                                factory.newPrimitiveProperty(k, 
                                    factory.newPrimitiveValueBuilder().buildDouble((Double) v)));
                        } else if (Long.class.isAssignableFrom(v.getClass())) {
                            entity.getProperties().add(
                                factory.newPrimitiveProperty(k, 
                                    factory.newPrimitiveValueBuilder().buildInt64((Long) v)));
                        } else if (Integer.class.isAssignableFrom(v.getClass())) {
                            entity.getProperties().add(
                                factory.newPrimitiveProperty(k, 
                                    factory.newPrimitiveValueBuilder().buildInt32((Integer) v)));
                        } else if (at.isReference()) {
                            entity.addLink(
                                factory.newEntityNavigationLink(at.getApiName(), client.newURIBuilder(serviceURL)
                                    .appendEntitySetSegment(at.getReferenceToPluralName())
                                    .appendKeySegment(UUID.fromString(v.toString())).build()));
                        } else {
                            entity.getProperties().add(
                                factory.newPrimitiveProperty(k, 
                                    factory.newPrimitiveValueBuilder().buildString(v.toString())));
                        }
                    } else {
                        nullFields.add(k);
                    }
                });
            });
            oDataCEs.add(entity);
        }
        return oDataCEs;
    }

    public SyncResponse post(SyncRequest request, Operation op) {
        return withBackoffAndErrorHandling(() -> {
            SyncResponse fullResponse = new SyncResponse();
            boolean isSuccess = true;
            final String entityName = request.getEntityName().toLowerCase();
            final String idField = request.getEntitySchema().getIdField().getApiName();
            final ODataClient client = getODataClient();
            client.getConfiguration().setContinueOnError(true);
            final ClientObjectFactory factory = client.getObjectFactory();
            final URIBuilder targetURI = client.newURIBuilder(serviceURL)
                .appendEntitySetSegment(request.getEntitySchema().getPluralName());
            
            List<EntityData> entityList = request.getData().get(request.getConnector().getId());
            List<List<EntityData>> partitions = Lists.partition(entityList, POST_BATCH_SIZE);
            for (List<EntityData> partition : partitions) {
                final BatchManager payloadManager = client.getBatchRequestFactory()
                    .getBatchRequest(serviceURL).payloadManager();
                final ODataChangeset changeset = payloadManager.addChangeset();

                List<ClientEntity> oDataCEs = toODataEntities(request.getEntitySchema(), factory, entityName, partition, serviceURL);
                for (ClientEntity ce: oDataCEs) {
                    switch (op) {
                        case create:
                            final ODataEntityCreateRequest<ClientEntity> createRequest = client.getCUDRequestFactory()
                                .getEntityCreateRequest(targetURI.build(), ce);
                            createRequest.setAccept("application/json; charset=utf-8");
                            createRequest.setPrefer("return=representation");
                            changeset.addRequest(createRequest);
                            break;
                        case update:
                            // Has to be new URI and not mutate the existing targetURI.
                            URI updateURI = client.newURIBuilder(serviceURL)
                                .appendEntitySetSegment(request.getEntitySchema().getPluralName())
                                .appendKeySegment(ce.getProperty(idField).getValue()).build();
                            final ODataEntityUpdateRequest<ClientEntity> updateRequest = client.getCUDRequestFactory()
                                .getEntityUpdateRequest(updateURI, UpdateType.PATCH, ce);
                            updateRequest.setAccept("application/json; charset=utf-8");
                            // If-Match: * will prevent creating an entry (upsert) if not present, 
                            // instead would throw an error.
                            updateRequest.setIfMatch("*");
                            updateRequest.setPrefer("return=representation");
                            changeset.addRequest(updateRequest);
                            break;
                        case delete:
                            // Has to be new URI and not mutate the existing targetURI.
                            URI deleteURI = client.newURIBuilder(serviceURL)
                                .appendEntitySetSegment(request.getEntitySchema().getPluralName())
                                .appendKeySegment(ce.getProperty(idField).getValue()).build();
                            final ODataDeleteRequest deleteRequest = client.getCUDRequestFactory()
                                .getDeleteRequest(deleteURI);
                            deleteRequest.setAccept("application/json; charset=utf-8");
                            changeset.addRequest(deleteRequest);
                            break;
                        default:
                            break;
                    }
                }
                
                SyncResponse response = toSyncResponse(request, op, payloadManager, partition);
                if (response.getErrors().size() > 0) {
                    List<String> successSyncariIds = response.getResults().stream().filter(x -> x.isSuccess())
                        .map(Result::getId).collect(Collectors.toList());
                    // Fallback to one-by-one processing to capture the actual error due to a bug in error processing.
                    // https://issues.apache.org/jira/browse/OLINGO-1342
                    for (int i = 0; i < oDataCEs.size(); i++) {
                        if (successSyncariIds.contains(partition.get(i).getSyncariEntityId())) {
                            continue;
                        }
                        response = handleSingleOp(request, op, client, targetURI, oDataCEs.get(i), partition.get(i));
                        fullResponse = fullResponse.merge(response);
                        isSuccess = isSuccess && response.isSuccess() && response.getErrors().isEmpty();
                    }
                } else {
                    fullResponse = fullResponse.merge(response);
                }
                isSuccess = isSuccess && response.isSuccess() && response.getErrors().isEmpty();
                //logLimits(request.getConnector(), conn);
            }
            fullResponse.setSuccess(isSuccess);
            return fullResponse;
        });
    }

    public Map<String, String> getReferenceEntityByPluralName(Set<String> referenceEntityNames) {
        Map<String, String> pluralNameByEntityName = Maps.newHashMap();
        URIBuilder absoluteUri = getODataClient().newURIBuilder(getServiceURL())
            .appendEntitySetSegment("EntityDefinitions");
        List<List<String>> partitions = Lists.partition(new ArrayList<>(referenceEntityNames), MsDynamicsService.DESCRIBE_BATCH_SIZE);
        for (List<String> partition: partitions) {
            String entityFilters = null;
                for (String entity: partition) {
                if (entityFilters == null) {
                    entityFilters = String.format("LogicalName eq ('%s') ", entity);
                } else {
                    entityFilters += String.format(" or LogicalName eq ('%s')", entity);
                }
            }
            absoluteUri.filter(entityFilters);
            absoluteUri.select("LogicalName", "EntitySetName", "DisplayName", "Description", "IsCustomEntity");
            try {
                List<Map<String, Object>> values = executeODataRequest(absoluteUri.build());
                if (!CollectionUtils.isEmpty(values)) {
                    for (Map<String, Object> entityDefinition: values) {
                        if (entityDefinition.containsKey("EntitySetName") && entityDefinition.get("EntitySetName") != null &&
                            StringUtils.isNotEmpty(entityDefinition.get("EntitySetName").toString())) {
                            pluralNameByEntityName.put(entityDefinition.get("LogicalName").toString(),
                                entityDefinition.get("EntitySetName").toString());
                        }
                    }
                }
            } catch (IOException e) {
                log.error("Failed to get entity plural names ", e);
            }
        }
        return pluralNameByEntityName;
    }

    public EntitySchema toEntitySchema(Map<String, Object> entityDefinitionMap, 
            String watermarkField, Set<String> systemFields) throws IOException {
        EntitySchema entityDefinition = new EntitySchema();
        entityDefinition.setApiName(entityDefinitionMap.get("LogicalName").toString());
        String displayName = getLabelAsString(entityDefinitionMap.get("DisplayName"));
        entityDefinition.setDisplayName(StringUtils.isEmpty(displayName) ? entityDefinitionMap.get("LogicalName").toString() : displayName);
        if (entityDefinitionMap.containsKey("EntitySetName") && entityDefinitionMap.get("EntitySetName") != null &&
                StringUtils.isNotEmpty(entityDefinitionMap.get("EntitySetName").toString())) {
            entityDefinition.setPluralName(entityDefinitionMap.get("EntitySetName").toString());
        }
        entityDefinition.setDescription(getLabelAsString(entityDefinitionMap.get("Description")));
        entityDefinition.setCustom(Boolean.parseBoolean(entityDefinitionMap.get("IsCustomEntity").toString()));

        Map<String, List<String>> pickListValsByKey = getEntityPickListValues(entityDefinition.getApiName());
        Set<String> referenceEntities = Sets.newHashSet();
        Map<String, String> pluralNameByEntityName = Maps.newHashMap();
        
        // Process attributes for the entity.
        List<Map<String, Object>> odataAttributes = 
            mapper.convertValue(entityDefinitionMap.get("Attributes"), 
                new TypeReference<List<Map<String, Object>>>(){});
        Map<String, AttributeSchema> attrByName = new HashMap<>();
        for (Map<String, Object> f: odataAttributes) {
            final String attrName = f.get("LogicalName").toString();
            final String attrType = resolveAttributeType(f);

            boolean isIdField = Boolean.parseBoolean(f.get("IsPrimaryId").toString());
            if (isIdField) {
                // The MSD metadata API for an entityDefinition returns more than one field with "IsPrimaryId": true,
                // this is mostly for related FKs that it sends as IsPrimaryId: true.
                // There is no other reliable way other than looking at the logicalname to follow this naming pattern.
                String idFieldName = entityDefinitionMap.get("LogicalName").toString() + "id";
                if(!idFieldName.equalsIgnoreCase(attrName)) {
                    isIdField = false;
                }
            }

            AttributeSchema attr = new AttributeSchema();
            attr.setApiName(attrName);
            String attrDisplayName = getLabelAsString(f.get("DisplayName"));
            attr.setDisplayName(StringUtils.isEmpty(attrDisplayName) ? f.get("LogicalName").toString() : attrDisplayName);

            // TODO: generalize this like core.DatatypeFactory
            if ("money".equalsIgnoreCase(attrType) || "bigint".equalsIgnoreCase(attrType) || 
                "decimal".equalsIgnoreCase(attrType)) {
                attr.setDataType("double");
            } else if ("lookup".equalsIgnoreCase(attrType) || "owner".equalsIgnoreCase(attrType) || "customer".equalsIgnoreCase(attrType)) {
                attr.setDataType("reference");
                attr.setApiName(attrName);
                if (f.containsKey("Targets")) {
                    List targetObjects = (List) f.get("Targets");
                    if (!CollectionUtils.isEmpty(targetObjects) && targetObjects.size() > 0) {
                        String targetObjectName = targetObjects.get(0).toString();
                        attr.setReferenceTo(targetObjectName);
                        attr.setReferenceTargetField(targetObjectName + "id");
                        referenceEntities.add(targetObjectName);
                    }
                }
            } else {
                attr.setDataType(attrType);
            }
            attr.setCustom(Boolean.parseBoolean(f.get("IsCustomAttribute").toString()));
            if (f.get("DefaultValue") != null) {
                attr.setDefaultValue(f.get("DefaultValue").toString());
            }
            attr.setInitializable(Boolean.parseBoolean(f.get("IsValidForCreate").toString()));
            attr.setCalculated(f.get("SourceType") != null && Integer.parseInt(f.get("SourceType").toString()) > 0);
            attr.setNillable(!isIdField);
            attr.setUnique(isIdField);
            attr.setUpdateable(Boolean.parseBoolean(f.get("IsValidForUpdate").toString()));
            if (f.get("MaxLength") != null) {
                attr.setLength(Integer.parseInt(f.get("MaxLength").toString()));
            }
            if (f.get("Precision") != null) {
                int precision = Integer.parseInt(f.get("Precision").toString());
                // MSD does not define the scale of double, decimal or money type. we use the best possible value as 4.
                int scale = 4;
                // The default precision provided by MSD is 2. This messes up money type fields, we use best possible value as 19.
                // We also have scenarios like annualrevenue_base that has a precision of 4, thats not enough to hold values. 
                // Anything < 5, we would use 19.
                if (precision <= 5) {
                    precision = 19;
                }
                attr.setPrecision(precision);
                attr.setScale(scale);
            }
            attr.setIdField(isIdField);
            if ("Picklist".equalsIgnoreCase(attrType) || "MultiSelectPicklist".equalsIgnoreCase(attrType)) {
                if (pickListValsByKey.containsKey(attrName)) {
                    attr.setPicklistValues(pickListValsByKey.get(attrName));
                }
                if ("MultiSelectPicklist".equalsIgnoreCase(attrType)) {
                    attr.setDataType("picklist");
                    attr.setMultiValueField(true);
                }
            }
            if (watermarkField.equalsIgnoreCase(attrName)) {
                attr.setWatermarkField(true);
            }
            if(isIdField || systemFields.contains(attrName)) {
                attr.setSystem(true);
            }
            attrByName.put(attrName, attr);
        }

        pluralNameByEntityName = getReferenceEntityByPluralName(referenceEntities);
        for (Entry<String, AttributeSchema> entry: attrByName.entrySet()) {
            AttributeSchema refAttr = entry.getValue();
            if (refAttr.isReference() && pluralNameByEntityName.containsKey(refAttr.getReferenceTo())) {
                refAttr.setReferenceToPluralName(pluralNameByEntityName.get(refAttr.getReferenceTo()));
            }
        }

        // Process relations.
        /* This is already handled above when processing reference fields.
        List<Map<String, Object>> oDataFKRelations = 
            mapper.convertValue(entityDefinitionMap.get("ManyToOneRelationships"), 
                new TypeReference<List<Map<String, Object>>>(){});
        for (Map<String, Object> r: oDataFKRelations) {
            final String referencingAttrName = r.get("ReferencingAttribute").toString();
            if (attrByName.containsKey(referencingAttrName)) {
                attrByName.get(referencingAttrName).setReferenceTargetField(r.get("ReferencedAttribute").toString());
                attrByName.get(referencingAttrName).setReferenceTo(r.get("ReferencedEntity").toString());
            }
        }
        */
        entityDefinition.getAttributes().addAll(attrByName.values());

        return entityDefinition;
    }

    private String resolveAttributeType(Map<String, Object> attribute) {
        String attrType = attribute.get("AttributeType").toString().toLowerCase();

        if ("datetime".equalsIgnoreCase(attrType)) {
            Map<String, String> dateTimeBehavior = Optional.ofNullable(mapper.convertValue(attribute.get("DateTimeBehavior"),
                    new TypeReference<Map<String, String>>() {})).orElse(Collections.emptyMap());
            String format = Optional.ofNullable(attribute.get("Format")).map(Object::toString).orElse("");

            if ("DateOnly".equalsIgnoreCase(dateTimeBehavior.getOrDefault("Value", "")) && "DateOnly".equalsIgnoreCase(format)) {
                return "date";
            }
        }
        return attrType;
    }

    private String getLabelAsString(Object labelObj) {
        Map<String, Object> labelMap = mapper.convertValue(labelObj, new TypeReference<Map<String, Object>>(){});
        Map<String, Object> userLocalizedLabel = 
            mapper.convertValue(labelMap.get("UserLocalizedLabel"), new TypeReference<Map<String, Object>>(){});
        if (userLocalizedLabel == null) {
            return "";
        }
        return userLocalizedLabel.get("Label").toString();
    }

    private SyncResponse toSyncResponse(SyncRequest request, Operation op, BatchManager payloadManager, 
        List<EntityData> entityList) throws IOException {
        SyncResponse response = new SyncResponse();
        ODataBatchResponse oDataResponse = null;
        try {
            final String idField = request.getEntitySchema().getIdField().getApiName();
            oDataResponse = payloadManager.getResponse();
            Iterator<ODataBatchResponseItem> results = oDataResponse.getBody();
            // No response or failed response, not expected.
            if (results == null || !results.hasNext()) {
                throw new IOException("Did not receive any response for OData batch execution.");
            }
            int i = 0;
            ODataBatchResponseItem changeSetResponse = results.next();
            while (changeSetResponse.hasNext()) {
                ODataResponse r = changeSetResponse.next();
                boolean isSucccess = false;

                if (op == Operation.create) {
                    isSucccess = HttpStatusCode.CREATED.getStatusCode() == r.getStatusCode();
                } else if (op == Operation.update) {
                    isSucccess = HttpStatusCode.OK.getStatusCode() == r.getStatusCode();
                } else if (op == Operation.delete) {
                    isSucccess = HttpStatusCode.NO_CONTENT.getStatusCode() == r.getStatusCode();
                }
                if (!isSucccess) {
                    HttpException e = new HttpException(String.format("Operation %s failed.", op), 
                        HttpStatus.valueOf(r.getStatusCode()));
                    log.error("Operation {} failed.", op, e);
                    throw e;
                }
                // Capture response content if available.
                Map<String, Object> respBody = new HashMap<>();
                String oDataObjectId = null;
                if (op == Operation.create || op == Operation.update) {
                    respBody = mapper.readValue(r.getRawResponse(), new TypeReference<Map<String, Object>>(){});
                    if (respBody.containsKey(idField)) {
                        oDataObjectId = respBody.get(idField).toString();
                    }
                } else {
                    oDataObjectId = entityList.get(i).getId();
                }
                if (r != null) {
                    r.close();
                }
                response.getResults().add(
                    new Result(isSucccess, oDataObjectId, entityList.get(i).getSyncariEntityId()));
                i++;
            }
        } catch (Exception e) {
            response.setSuccess(false);
            response.appendError(e);
            log.error("Batch operation failed", e);
        } finally {
            if (oDataResponse != null) oDataResponse.close();
        }
		return response;
    }
    
    private SyncResponse handleSingleOp(SyncRequest request, Operation op, ODataClient client, 
            URIBuilder absoluteUri, ClientEntity ce, EntityData eData) {
        SyncResponse response = new SyncResponse();
        Result result = new Result(true, null, eData.getSyncariEntityId());
        final String idField = request.getEntitySchema().getIdField().getApiName();
        try {
            switch (op) {
                case create:
                    ODataEntityCreateRequest<ClientEntity> createRequest = client.getCUDRequestFactory()
                        .getEntityCreateRequest(absoluteUri.build(), ce);
                    createRequest.setAccept("application/json; charset=utf-8");
                    createRequest.setPrefer("return=representation");
                    ODataEntityCreateResponse<ClientEntity> resp = createRequest.execute();
                    result.setSuccess(HttpStatusCode.CREATED.getStatusCode() == resp.getStatusCode());
                    result.setId(resp.getBody().getProperty(idField).getValue().toString());
                    if (resp != null) resp.close();
                    break;
                case update:
                    result.setId(ce.getProperty(idField).getValue().toString());
                    URI updateURI = client.newURIBuilder(serviceURL)
                        .appendEntitySetSegment(request.getEntitySchema().getPluralName())
                        .appendKeySegment(ce.getProperty(idField).getValue()).build();
                    ODataEntityUpdateRequest<ClientEntity> updateRequest = client.getCUDRequestFactory()
                        .getEntityUpdateRequest(updateURI, UpdateType.PATCH, ce);
                    updateRequest.setAccept("application/json; charset=utf-8");
                    // If-Match: * will prevent creating an entry (upsert) if not present, instead would throw an error.
                    updateRequest.setIfMatch("*");
                    updateRequest.setPrefer("return=representation");
                    ODataEntityUpdateResponse<ClientEntity> updateResponse = updateRequest.execute();
                    result.setSuccess(HttpStatusCode.OK.getStatusCode() == updateResponse.getStatusCode());
                    result.setId(updateResponse.getBody().getProperty(idField).getValue().toString());
                    if (updateResponse != null) updateResponse.close();
                    break;
                case delete:
                    result.setId(ce.getProperty(idField).getValue().toString());
                    // Has to be new URI and not mutate the existing targetURI.
                    URI deleteURI = client.newURIBuilder(serviceURL)
                        .appendEntitySetSegment(request.getEntitySchema().getPluralName())
                        .appendKeySegment(ce.getProperty(idField).getValue()).build();
                    final ODataDeleteRequest deleteRequest = client.getCUDRequestFactory().getDeleteRequest(deleteURI);
                    deleteRequest.setAccept("application/json; charset=utf-8");
                    deleteRequest.setPrefer("return=representation");
                    ODataDeleteResponse deleteResponse = deleteRequest.execute();
                    result.setSuccess((HttpStatusCode.NO_CONTENT.getStatusCode() == deleteResponse.getStatusCode()));
                    result.setId(eData.getValues().get(idField).toString());
                    if (deleteResponse != null) deleteResponse.close();
                    break;
                default:
                    break;
            }
            
        } catch (Exception e) {
            response.setSuccess(false);
            response.appendError(e);
            log.error("Failed to {} due to {}", op, e.getMessage(), e);
            result.setSuccess(false);
            result.addError(e.getMessage());
        }
        response.getResults().add(result);
        return response;
    }

    public String createWebhook(List<String> supportedEntities, String spectrumHost) throws IOException {
        log.info("Creating MS Dynamics webhook");
        getODataClient();

        String serviceEndpoint = createServiceEndpoint(spectrumHost);
        String[] parts = serviceEndpoint.split(":");
        if(parts.length != 2) {
            throw new RuntimeException("Failed to create webhook");
        }
        String serviceEndpointId = parts[0];

        URIBuilder uriBuilder = client.newURIBuilder(serviceURL).appendEntitySetSegment("sdkmessages").filter("name eq 'Delete'");
        List<Map<String, Object>> values = executeODataRequest(uriBuilder.build());
        if(values.isEmpty() || !values.get(0).containsKey("sdkmessageid")) {
            throw new RuntimeException("Failed to create webhook");
        }

        String sdkMessageId = (String) values.get(0).get("sdkmessageid");
        for(String suportedEntity: supportedEntities) {
            log.debug("Creating MS Dynamics SdkMessageProcessingSteps for supported entities for serviceendpointid - {} and sdkmessage - {} and object {}", serviceEndpointId, sdkMessageId, suportedEntity);
            uriBuilder = client.newURIBuilder(serviceURL).appendEntitySetSegment("sdkmessagefilters").filter("_sdkmessageid_value eq '"+ sdkMessageId +"' and primaryobjecttypecode eq '"+ suportedEntity + "'");
            values = executeODataRequest(uriBuilder.build());
            if(values.isEmpty() || !values.get(0).containsKey("sdkmessagefilterid")) {
                throw new RuntimeException("Failed to create webhook");
            }
            String sdkMessageFilterId = (String) values.get(0).get("sdkmessagefilterid");

            addSdkMessageProcessingSteps(sdkMessageId, sdkMessageFilterId, serviceEndpointId, suportedEntity);
        }
        return serviceEndpoint;
    }

    private String addSdkMessageProcessingSteps(String sdkMessageId, String sdkMessageFilterId, String serviceEndpointId, String entity) {
        URIBuilder uriBuilder;
        uriBuilder = client.newURIBuilder(serviceURL)
                .appendEntitySetSegment("sdkmessageprocessingsteps");
        ClientObjectFactory factory = client.getObjectFactory();
        ClientEntity sdkMessageProcessingStepEntity = factory.newEntity(new FullQualifiedName(namespace, "sdkmessageprocessingsteps"));
        Map<String, ClientPrimitiveValue> sdkMessageProcessingStepProperties = Map.of(
                "name", factory.newPrimitiveValueBuilder().buildString("Syncari Webhook Delete Step for " + entity),
                "stage", factory.newPrimitiveValueBuilder().buildInt32(40),
                "rank", factory.newPrimitiveValueBuilder().buildInt32(1),
                "supporteddeployment", factory.newPrimitiveValueBuilder().buildInt32(0),
                "description", factory.newPrimitiveValueBuilder().buildString("Syncari Webhook Delete Step for " + entity)
        );
        sdkMessageProcessingStepProperties.forEach((k,v) -> {
            sdkMessageProcessingStepEntity.getProperties().add(
                    factory.newPrimitiveProperty(k, v));
        });
        sdkMessageProcessingStepEntity.addLink(factory.newEntityNavigationLink("sdkmessageid", client.newURIBuilder(serviceURL)
                .appendEntitySetSegment("sdkmessages")
                .appendKeySegment(UUID.fromString(sdkMessageId)).build()));
        sdkMessageProcessingStepEntity.addLink(factory.newEntityNavigationLink("sdkmessagefilterid", client.newURIBuilder(serviceURL)
                .appendEntitySetSegment("sdkmessagefilters")
                .appendKeySegment(UUID.fromString(sdkMessageFilterId)).build()));
        sdkMessageProcessingStepEntity.addLink(factory.newEntityNavigationLink("eventhandler_serviceendpoint", client.newURIBuilder(serviceURL)
                .appendEntitySetSegment("serviceendpoints")
                .appendKeySegment(UUID.fromString(serviceEndpointId)).build()));
        ODataEntityCreateRequest<ClientEntity> req = client.getCUDRequestFactory().getEntityCreateRequest(uriBuilder.build(), sdkMessageProcessingStepEntity);
        req.setAccept("application/json; charset=utf-8");
        req.setPrefer("return=representation");
        ODataEntityCreateResponse<ClientEntity> res = req.execute();
        if(res.getStatusCode() != HttpStatusCode.CREATED.getStatusCode() || res.getBody().getProperty("sdkmessageprocessingstepid") == null) {
            throw new RuntimeException("Failed to create webhook");
        }
        log.debug("SdkMessageProcessingStep created successfully for {}", entity);
        return res.getBody().getProperty("sdkmessageprocessingstepid").getPrimitiveValue().toString();
    }

    private String createServiceEndpoint(String spectrumHost) {
        URIBuilder uriBuilder;
        uriBuilder = client.newURIBuilder(serviceURL)
                .appendEntitySetSegment("serviceendpoints");
        ClientObjectFactory factory = client.getObjectFactory();
        ClientEntity serviceEndpointEntity = factory.newEntity(new FullQualifiedName(namespace, "serviceendpoints"));
        String webhookKey = UUID.randomUUID().toString();
        Map<String, ClientPrimitiveValue> serviceEndpointProperties = Map.of(
                "name", factory.newPrimitiveValueBuilder().buildString("Syncari Webhook"),
                "url", factory.newPrimitiveValueBuilder().buildString(spectrumHost),
                "contract", factory.newPrimitiveValueBuilder().buildInt32(8),
                "authtype", factory.newPrimitiveValueBuilder().buildInt32(4),
                "authvalue", factory.newPrimitiveValueBuilder().buildString(webhookKey));
        serviceEndpointProperties.forEach((k, v) -> {
            serviceEndpointEntity.getProperties().add(
                    factory.newPrimitiveProperty(k, v));
        });
        ODataEntityCreateRequest<ClientEntity> req = client.getCUDRequestFactory().getEntityCreateRequest(uriBuilder.build(), serviceEndpointEntity);
        req.setAccept("application/json; charset=utf-8");
        req.setPrefer("return=representation");
        ODataEntityCreateResponse<ClientEntity> res = req.execute();
        if(res.getStatusCode() != HttpStatusCode.CREATED.getStatusCode() || res.getBody().getProperty("serviceendpointid") == null) {
            throw new RuntimeException("Failed to create webhook");
        }
        log.info("MS Dynamics service endpoint created");
        return res.getBody().getProperty("serviceendpointid").getPrimitiveValue().toString() + ":" + webhookKey;
    }

    public void deleteWebhook() throws IOException {
        if(connector.getMetaConfig().containsKey("webhook_id")) {
            getODataClient();

            ClientObjectFactory factory = client.getObjectFactory();
            URIBuilder uriBuilder = client.newURIBuilder(serviceURL).appendEntitySetSegment("sdkmessageprocessingsteps").filter("_eventhandler_value eq '" + connector.getMetaConfig().get("webhook_id") + "'");
            List<Map<String, Object>> values = executeODataRequest(uriBuilder.build());
            values.forEach(value -> {
                URI deleteURI = client.newURIBuilder(serviceURL)
                        .appendEntitySetSegment("sdkmessageprocessingsteps")
                        .appendKeySegment(factory.newPrimitiveValueBuilder().buildString((String) value.get("sdkmessageprocessingstepid"))).build();
                ODataDeleteRequest deleteRequest = client.getCUDRequestFactory()
                        .getDeleteRequest(deleteURI);
                deleteRequest.setAccept("application/json; charset=utf-8");
                deleteRequest.execute();
            });

            URI deleteURI = client.newURIBuilder(serviceURL)
                    .appendEntitySetSegment("serviceendpoints")
                    .appendKeySegment(factory.newPrimitiveValueBuilder().buildString((String)connector.getMetaConfig().get("webhook_id"))).build();
            final ODataDeleteRequest deleteRequest = client.getCUDRequestFactory()
                    .getDeleteRequest(deleteURI);
            deleteRequest.setAccept("application/json; charset=utf-8");
            deleteRequest.execute();
        }
    }

    public boolean isWebhookActive() throws IOException {
        getODataClient();

        ClientObjectFactory factory = client.getObjectFactory();
        URI getURI = client.newURIBuilder(serviceURL)
                .appendEntitySetSegment("serviceendpoints")
                .filter("serviceendpointid eq '" + connector.getMetaConfig().get("webhook_id") + "'").build();
        try {
            List<Map<String, Object>> serviceEndpoint = executeODataRequest(getURI);
            return !serviceEndpoint.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
