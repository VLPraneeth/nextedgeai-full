package com.syncari.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.bulk.BulkWriteResult;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.connector.custom.CloudFunctionInfo;
import com.syncari.connector.data.WebhookRequest;
import com.syncari.connector.service.def.WebhookService;
import com.syncari.core.DataTransformer;
import com.syncari.core.SyncariContextHandler;
import com.syncari.core.config.AppConfig;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.event.Message;
import com.syncari.core.event.store.model.WebhookReceiverLog;
import com.syncari.core.event.store.repo.WebhookReceiverLogRepo;
import com.syncari.core.model.*;
import com.syncari.core.repositories.customer.ApiErrorLogRepo;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.repositories.customer.EventDataRepo;
import com.syncari.core.repositories.syncari.GlobalConfigurationRepo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Slf4j
@Component
public class EventDataService {
	@Autowired
	EventDataRepo repo;
	@Autowired
	DataServiceFactory factory;
	@Autowired
	EventDataService service;
	@Autowired
	ConnectorService connectorService;
	@Autowired
	ConnectorMetadataService connectorMetadataService;
	@Autowired
	ConnectorMetadataService metaService;
	@Autowired
	DataTransformer dataTransformer;
	@Autowired
	SyncariContextHandler contextHandler;
	@Autowired
	GlobalConfigurationRepo globalRepo;
	@Autowired
	MappingGraphService mappingGraphService;
	@Autowired
	SchemaService schemaService;
	@Autowired
	ApiErrorLogRepo errorRepo;
	@Autowired
	SubscriptionService subscriptionService;
    @Autowired
    AppConfig appConfig;
    @Autowired
	MongoTemplate customerMongoTemplate;
    @Autowired
    WebhookReceiverLogRepo whLogRepo;
    @Autowired
    EntityRepo entityRepo;
    ObjectMapper objectMapper = new ObjectMapper();

	public void save(List<EventData> data) {
		repo.saveAll(data);
	}

	public Page<EventData> findAllByConnectorIdAndGraphId(String connectorId, String graphId, Pageable pageable) {
		return repo.findAllByConnectorIdAndGraphId(connectorId, graphId, pageable);
	}
	
	public Page<EventData> findAllByConnectorId(String connectorId, Pageable pageable) {
		return repo.findAllByConnectorId(connectorId, pageable);
	}

	public void deleteByBatchId(String batchId) {
		Pageable pageReq = PageRequest.of(0, 500);
		Page<EventData> page = null;
		do {
			page = repo.findAllByBatchId(batchId, pageReq);
			log.debug("Deleting {} event data records", page.getContent().size());
			repo.deleteAll(page.getContent());
		} while (page != null && page.hasNext());
	}

	public void updateByBatchId(List<EventData> events, String batchId) {

		var updateList = events.stream().map(event -> {
			Update update = new Update().set("batchId", batchId).set("updatedAt", new Date());;
			return Pair.of(new Query().addCriteria(where("_id").is(new ObjectId(event.getId()))),
					update
					);
		}).collect(Collectors.toList());

		final BulkWriteResult results = customerMongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, EventData.class)
				.updateMulti(updateList)
				.execute();
		log.debug("Updated {} Event Data records with result {}", updateList.size(), results);
	}

	public void handleEvent(Message message) {
		String synapse = message.getEvent().getDetails().get("synapse").toString();
		metaService.findByName(synapse).ifPresentOrElse(m -> {
            WebhookService webhookService = factory.getWebhookService(m);
			Map<String, Object> headers = (Map<String, Object>) message.getEvent().getDetails().get("headers");
			Map<String, Object> params = (Map<String, Object>) message.getEvent().getDetails().get("params");
			String body = message.getEvent().getDetails().get("body").toString();
			String md5 = body != null ? DigestUtils.md5Hex(body) : null;
			log.info("Received request for synapse {} with body digest {}", synapse, md5);
			String identifier = params != null && params.containsKey("instanceId") ? params.get("instanceId").toString() : webhookService.extractIdentifier(getWebhookRequest(m, body, headers, params));
			if(m.isWebhook()) {
			  String connectorId = (String) message.getEvent().getDetails().get("connectorId");
			  boolean authenticated = (boolean) message.getEvent().getDetails().get("authenticated");
			  boolean verified = (boolean) message.getEvent().getDetails().get("verified");
			  String syncariId = (String) message.getEvent().getDetails().get("syncariId");
              WebhookReceiverLog whLog = null;
              try {
                whLog = new WebhookReceiverLog().setConnectorId(connectorId)
                  .setAuthenticated(authenticated).setVerified(verified).setPayload(body)
                  .setHeaders(objectMapper.writeValueAsString(headers)).setReceivedOn(Instant.now());
              } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
              }
              contextHandler.setContext(syncariId, true);
              whLog = whLogRepo.insertWebhookReceiverLog(whLog);
			  if(!authenticated) {
			    log.warn("Webhook request not authenticated refer log entry with id {}", whLog.getId());
			    return;
			  }
			}
			if(StringUtils.isNotBlank(identifier)) {
				log.debug("Recieved request for synapse {} and id {}", synapse, identifier);
				String key = m.isWebhook()
	                ? String.format("%s_%s_%s", WebhookService.PREFIX, Constants.WEBHOOK_RECEIVER,
	                    identifier)
	                : String.format("%s_%s_%s", WebhookService.PREFIX,
	                    synapse, identifier);
				globalRepo.findByKey(key).ifPresentOrElse(value -> {
					List<String> values = (List<String>) value.getValue();
					for (String instanceValue : values) {
						parse(synapse, webhookService, headers, params, body, key, instanceValue);
					}
				}, () -> {
					String msg = String.format("Could not find value for synapse %s with key %s", synapse, key);
					log.error(msg);
//				errorRepo.save(new ApiErrorLog().setBody(body).setError(msg));
				});
			} else {
				log.error("Failed to parse instanceId or identifier from the event payload");
			}
		}, () -> {
			String msg = String.format("Could not find metadata for synapse %s", synapse);
			log.error(msg);
//			errorRepo.save(new ApiErrorLog().setError(msg));
		});
	}

    private WebhookRequest getWebhookRequest(Connector c, String body, Map<String, Object> headers, Map<String, Object> params) {
        WebhookRequest webhookRequest = getWebhookRequest(c.getMetadata(), body, headers, params);
        if(c.getMetadata().isWebhook()) {
          webhookRequest.setActiveEntities(schemaService.getEntities(c.getId(), false).stream()
              .filter(e -> e.isActive()).map(e -> e.getApiName()).collect(Collectors.toList()));
        }
        return webhookRequest;
    }
    
    private WebhookRequest getWebhookRequest(ConnectorMetadata m, String body, Map<String, Object> headers, Map<String, Object> params) {
      WebhookRequest webhookRequest = new WebhookRequest().setBody(body).setHeaders(headers).setParams(params);
      if (m.isCustom()) {
          CloudFunctionInfo cfInfo = connectorMetadataService.getCloudFunctionInfo(m);
          webhookRequest.setCloudFunctionInfo(cfInfo);
      }
      return webhookRequest;
  }

	private void parse(String synapse, WebhookService webhookService, Map<String, Object> headers, Map<String, Object> params,
			String body, String key, String instanceValue) {
		String[] parts = instanceValue.split("_");
		Stream<String> partsStream = Arrays.stream(parts);
		String syncariId = partsStream.limit(parts.length - 1).collect(Collectors.joining("_"));
		try {
			Instance instance = subscriptionService.getInstance(syncariId);
			if (instance.isActive()) {
				String connectorId = parts[parts.length - 1];
				contextHandler.setContext(syncariId, true);
				connectorService.find(connectorId).ifPresentOrElse(connector -> {
					if (!connector.isActive()) {
						log.warn("Connector {} not active, key {}", connectorId, key);
						return;
					}
					try {
						List<com.syncari.connector.data.EventData> parseEventData = webhookService
								.parseEventData(getWebhookRequest(connector, body, headers, params)
                                    .setConfig(dataTransformer.toConnectorInfo(connector)));
						List<EventData> eventData = dataTransformer.toEventData(parseEventData);
						saveEventData(eventData, connector);
					} catch (Exception e) {
						String msg = String.format("error %s synapse %s with id %s", ExceptionUtils.getStackTrace(e), synapse, connectorId);
						errorRepo.save(new ApiErrorLog().setBody(body).setConnectorId(connectorId).setError(msg));
						log.error(msg);
					}
				}, () -> {
					String msg = String.format("Could not find synapse %s with id %s", synapse, connectorId);
					errorRepo.save(new ApiErrorLog().setBody(body).setConnectorId(connectorId).setError(msg));
					log.error(msg);
				});
			}
		} catch (InstanceNotFound|OrgNotFound e) {
			log.debug("Instance or Org for instance {} not found", syncariId, e);
		}
	}

	private void saveEventData(List<EventData> data, Connector connector) {
	    log.debug("Saving eventData for connector {}", connector.getId());
		if (data == null || data.isEmpty()) {
			log.warn("No event data to be stored!");
			return;
		}

		// Process association deletion webhooks with placeholder IDs by querying and matching on fields
		List<EventData> processedData = processAssociationDeletionWebhooks(data, connector);

		Set<String> entities = processedData.stream().filter(eventData -> eventData.getData() != null).map(eventData -> eventData.getData().getName()).collect(Collectors.toSet());
		log.debug("Found {} entities ", entities.size());
		entities.forEach(entity -> {
		    log.debug("Saving for {} entity ", entity);
			schemaService.findEntity(connector.getId(), entity).ifPresent(e -> {
			   log.debug("Found entity {} entity ", e.getId());
				Map<String, List<MappingGraph>> allGraphs = mappingGraphService
						.findEntityGraphsByConnectorEntityId(List.of(e.getId()));
				List<MappingGraph> list = allGraphs.getOrDefault(e.getId(), List.of());
				log.debug("Found {} mapping graphs ", list.size());
				if (list.isEmpty())
					return;
				List<EventData> toBeSaved = new ArrayList<>();
				list.forEach(g -> {
					// Find all approved graphs where the source entity is used and save a copy of event data for each graph
				    log.debug("Saving for mapping graph {} ", g.getId());
					if (g.getDraftStatus() == DraftStatus.APPROVED) {
						processedData.forEach(d -> {
							if(d.getData() != null && d.getData().getName() != null && d.getData().getName().equalsIgnoreCase(entity)) {
								log.debug("Saving event for entity {} with id {} for pipeline {} ",
										d.getData().getName(), d.getData().getId(), g.getId());
								toBeSaved.add(new EventData().setBatchId(d.getBatchId()).setConnectorId(d.getConnectorId())
										.setData(d.getData()).setEventId(d.getEventId()).setOperation(d.getOperation())
										.setGraphId(g.getId()));
							} else {
							  log.debug("Not saving data for graph {} data {}", g.getId(), processedData);
							}
						});
					}
				});
				service.save(toBeSaved);
			});
		});
	}

	/**
	 * Process association deletion webhooks that have placeholder IDs by querying
	 * MongoDB for matching associations and creating proper deletion events.
	 */
	public List<EventData> processAssociationDeletionWebhooks(List<EventData> data, Connector connector) {
		List<EventData> result = new ArrayList<>();

		for (EventData eventData : data) {
			if (eventData != null && eventData.getData() != null &&
				eventData.getData().getName() != null &&
				eventData.getData().getName().endsWith("_association") &&
				eventData.getData().getId() != null &&
				eventData.getData().getId().contains("UNKNOWN-UNKNOWN") &&
				eventData.getOperation() == Operation.delete) {

				log.debug("Processing association deletion webhook with placeholder ID: {}", eventData.getData().getId());

				String entityName = eventData.getData().getName();
				String fromObjectId = (String) eventData.getData().getValue("fromObjectId");
				String toObjectId = (String) eventData.getData().getValue("toObjectId");
				String toObjectType = (String) eventData.getData().getValue("toObjectType");

				if (fromObjectId == null || toObjectId == null || toObjectType == null) {
					log.info("Missing required fields for association matching. fromObjectId={}, toObjectId={}, toObjectType={}",
						fromObjectId, toObjectId, toObjectType);
					result.add(eventData);
					continue;
				}

				List<Map<String, Object>> matchingAssociations = entityRepo.findAssociationsByFields(
					entityName, fromObjectId, toObjectId, toObjectType);

				if (matchingAssociations.isEmpty()) {
					log.info("No matching associations found for deletion. Entity={}, fromObjectId={}, toObjectId={}, toObjectType={}",
						entityName, fromObjectId, toObjectId, toObjectType);
				} else {
					log.debug("Found {} matching associations to delete", matchingAssociations.size());

					for (Map<String, Object> association : matchingAssociations) {
						String actualId = extractAssociationId(association);
						if (actualId == null) {
							log.info("Could not find association ID field in document");
							continue;
						}

						EntityData newEntityData = new EntityData(entityName);
						newEntityData.setId(actualId);
						newEntityData.setConnectorId(connector.getId());
						newEntityData.setDeleted(true);
						newEntityData.setLastModified(eventData.getData().getLastModified());

						eventData.getData().getValues().forEach(newEntityData::addValue);

						EventData newEventData = new EventData()
							.setData(newEntityData)
							.setOperation(Operation.delete)
							.setConnectorId(eventData.getConnectorId())
							.setBatchId(eventData.getBatchId())
							.setEventId(eventData.getEventId());

						result.add(newEventData);
					}
				}
			} else {
				result.add(eventData);
			}
		}

		return result;
	}

	private String extractAssociationId(Map<String, Object> association) {
		for (String key : association.keySet()) {
			if (key.contains("_association_id")) {
				return (String) association.get(key);
			}
		}
		return null;
	}
}
