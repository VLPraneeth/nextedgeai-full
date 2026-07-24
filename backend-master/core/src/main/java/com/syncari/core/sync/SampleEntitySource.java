package com.syncari.core.sync;

import com.syncari.connector.EntityData;
import com.syncari.connector.data.*;
import com.syncari.connector.service.def.DataService;
import com.syncari.connector.service.def.WebhookService;
import com.syncari.core.DataSourceRequest;
import com.syncari.core.DataTransformer;
import com.syncari.core.SyncariContext;
import com.syncari.core.event.store.EventStore;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.file.GCSFileManager;
import com.syncari.core.model.*;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.repositories.customer.StagedBatchRecordRepo;
import com.syncari.core.repositories.customer.StagedBatchRepo;
import com.syncari.core.repositories.customer.StagedExternalRecordRepo;
import com.syncari.core.service.*;
import com.syncari.core.webhook.receiver.WebhookReceiverService;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.syncari.utils.CollectionUtils.map;

@Slf4j
@Component
public class SampleEntitySource implements DataSource {
    @Autowired
    ConnectorService connectorService;
    @Autowired
    StagedBatchRepo stagingRepo;
    @Autowired
    StagedBatchRecordRepo recordRepo;
    @Autowired
    StagedExternalRecordRepo stagedExternalRecordRepo;
    @Autowired
    DataServiceFactory factory;
    @Autowired
    DateUtil dateUtil;
    @Autowired
    DataTransformer transformer;
    @Autowired
    EntitySourceHelper helper;
    @Autowired
    SchemaService schemaService;
    @Autowired
    EventStore eventStore;
    @Autowired
    IdMappingService idMappingService;
    @Autowired
    EntityRepoService entityRepoService;
    @Autowired
    FeatureService featureService;
    @Autowired
    GCSFileManager storage;
    @Autowired
    WebhookReceiverService webhookReceiverService;

    @Override
    public CurrentBatch fetch(DataSourceRequest req) {

        Map<Connector, List<EntityDefinition>> systemEntityMap = req.getSourceEntities().stream()
                .collect(Collectors.groupingBy(e -> connectorService.get(e.getConnectorId())));
        final EntityDefinition syncariEntity = req.getSyncariEntity();

        String entityName = syncariEntity.getApiName();
        CurrentBatch currentBatch = new CurrentBatch(recordRepo, stagingRepo,idMappingService,entityRepoService, featureService, stagedExternalRecordRepo);
        currentBatch.setSyncariEntity(syncariEntity);
        String syncCycleId = UUID.randomUUID().toString();

        systemEntityMap.forEach((k, entities) -> {
            entities.forEach(entity -> {
                Map<String, AttributeDefinition> apiNameToAttrMap = entity.getApiNameLowerCasedToAttributes();
                var connector = connectorService.refreshAuthentication(k);
                DataService dataService = factory.getDataService(k.getMetadata());

                //Extract this first, because the getSourceEntityWithMappedAndSystemFields
                //makes a copy of the schema and unsets ids
                final Map<String, Object> sourceParameters = req.getSourceParamMap().getOrDefault(entity.getId(), Map.of());
                EntitySchema entitySchema = transformer.toEntitySchema(schemaService.getSourceEntityWithMappedAndSystemFields(syncariEntity, entity, req.getGraph()), connector);

                final Map<String, Object> additionalParams = req.getAdditionalParamMap().getOrDefault(entity.getId(), Map.of());
                
                log.debug("About to do sync request");
                SyncRequest request = new SyncRequest()
                        .Builder(transformer.toConnectorInfo(connector), transformer.toEntitySchema(entity, connector))
                        .setPipeline(new Pipeline(req.getGraph().getName(), req.getGraph().getDraftStatus().name(), SyncariContext.getSyncariId()))
                        .setSourceParams(sourceParameters)
                		.setAdditionalParams(additionalParams);
                request.setEntitySchemaWithMappedFields(entitySchema);
                //don't save stagedbatch
            if (req.getWatermark() != null || req.getRecordIds().containsKey(entity.getId())
                || (req.getWebhook() != null && req.getWebhook().containsKey(entity.getId()))) {
                    log.debug("Saving staged batch record");
                    StagedBatch staged = stagingRepo.save(new StagedBatch(entityName).setConnectorId(connector.getId())
                                    .setCurrentBatchId(syncCycleId).setSourceEntityName(entity.getApiName()))
                            .setSourceEntityDefinitionId(entity.getId());

                    if (req.getWatermark() != null) {
                        request.setWatermark(transformer.toWatermarkInfo(req.getWatermark()).setTest(true));
                        request.setExcludeDeleted(true);
                        staged.setWatermark(req.getWatermark());
                        FetchResponse respByDate = dataService.getByWatermark(request);
                        int limit = (int) req.getWatermark().getLimit();
                        try {
                            while (respByDate.getIterator() != null && respByDate.getIterator().hasNext() && limit > 0) {
                                List<EntityData> batchData = saveBatchRecords(entity, apiNameToAttrMap, connector, staged,
                                        respByDate.getIterator().next(), limit, dataService, entitySchema);
                                limit = limit - batchData.size();
                                log.info("Test: Got {} records for connector {} and entity {} with {}", batchData.size(),
                                        connector.getName(), entityName, req.getWatermark());
                            }
                        } catch (RuntimeException e) {
                            log.warn("Test: Pagination failed for connector {} and entity {}: {}", 
                                    connector.getName(), entityName, e.getMessage(), e);
                        }
                        helper.logSyncStats(eventStore, currentBatch, entity, connector, respByDate);
                    } else {
                        // add all of our id's
                        if (req.getRecordIds().containsKey(entity.getId())) {
                        	req.getRecordIds().get(entity.getId()).forEach((id) -> {
                                request.addData(entity.getConnectorId(), new EntityData().setId(id));
                            });

                            List<EntityData> respById = dataService.getByIds(request);

                            saveBatchRecords(entity, apiNameToAttrMap, connector, staged, respById, respById.size(), dataService, entitySchema);
                            log.info("Test: Got {} records for connector {}} and entity {} for record ID's {} and request EntitySchema is {}", respById.size(),
                                    connector.getName(), entityName, String.join(",", req.getRecordIds().get(entity.getId())), request.getEntitySchema().getApiName());
                        } else if (req.getWebhook().containsKey(entity.getId())) {
                          // by webhook
                          List<String> activeEntities = entities.stream().filter(e -> e.isActive()).map(e -> e.getApiName()).collect(Collectors.toList());
                          var whReq = req.getWebhook().get(entity.getId());
                          Map<String, Object> fullHeaders = new HashMap<>();
                          Optional.ofNullable(whReq.getHeaders()).ifPresent(h -> h.forEach(fullHeaders::put));
                          Optional.ofNullable(webhookReceiverService.buildWebhookAuthHeaders(whReq.getAuthType(), whReq.getAuthConfig(), whReq.getPayload()))
                              .ifPresent(h -> h.forEach(fullHeaders::put));
                          WebhookRequest webhookReq = new WebhookRequest().setBody(whReq.getPayload()).setConfig(transformer.toConnectorInfo(connector))
                          .setHeaders(fullHeaders).setParams(Map.of()).setActiveEntities(activeEntities);
                          if(!webhookReceiverService.isValidRequest(webhookReq)) {
                            throw new SyncariValidationException("Authentication failed for entity source " + entity.getDisplayName());
                          }
                          List<EntityData> whRes = webhookReceiverService.parseEventData(webhookReq).stream().map(ed -> ed.getData()).collect(Collectors.toList());
  
                          saveBatchRecords(entity, apiNameToAttrMap, connector, staged, whRes, whRes.size(), dataService, entitySchema);
                          log.info("Test: Got {} records for connector {}} and entity {} for request EntitySchema is {}", whRes.size(),
                                  connector.getName(), entityName, request.getEntitySchema().getApiName());
                        } else {
                            log.info("Test: No record ids set. Skipping test for connector {}} and entity {}",
                                    connector.getName(), entityName);
                        }
                    }

                    currentBatch.setEntityBatch(entity, staged);
                }
            });
        });

        return currentBatch.setSuccess(true).setSyncariEntityName(entityName).setCurrentBatchId(syncCycleId);
    }

    private List<EntityData> saveBatchRecords(EntityDefinition entity,
                                              Map<String, AttributeDefinition> apiNameToAttrMap,
                                              Connector connector,
                                              StagedBatch staged,
                                              List<EntityData> batchData,
                                              int limit,
                                              DataService dataService,
                                              EntitySchema entitySchema) {
        List<EntityData> batchToSave = batchData.subList(0, Math.min(batchData.size(),limit));
        String idFieldName = entity.getIdField().map(AttributeDefinition::getApiName).orElse("Id");
        List<StagedBatchRecord> saved = recordRepo.saveAll(map(batchToSave, d -> {
            EntityData entityData = helper.fixDatatypes(apiNameToAttrMap, d);
            entityData.setConnectorId(connector.getId());
            if(!entityData.has(idFieldName)){
                entityData.addValue(idFieldName, entityData.getId());
            }

            List<AttributeDefinition> fileLinks = entity.getFileLinkAttributes();
            fileLinks.forEach(fileLink -> {
                DocumentRequest docReq = new DocumentRequest(transformer.toConnectorInfo(connector), entitySchema, entityData);
                if (fileLink.isMultiValueField()) {
                    List<String> filePaths = new ArrayList<>();
                    Map<String, InputStream> fileContents = dataService.getFileContents(docReq).getContentMap();
                    for (Map.Entry<String, InputStream> entry : fileContents.entrySet()) {
                        String filePath = String.format("%s/%s_%s_%s_%s_%s", SyncariContext.getInstance().getSyncariId(),
                                connector.getId(), entity.getApiName(), fileLink.getApiName(), entityData.getId(), entry.getKey());
                        storage.write(entry.getValue(), filePath);
                        filePaths.add(filePath);
                    }
                    entityData.addValue(fileLink.getApiName(), filePaths);
                } else {
                    String filePath = String.format("%s/%s_%s_%s_%s", SyncariContext.getInstance().getSyncariId(),
                            connector.getId(), entity.getApiName(), fileLink.getApiName(), entityData.getId());
                    storage.write(dataService.getFileContents(docReq).getContents(), filePath);
                    entityData.addValue(fileLink.getApiName(), filePath);
                }
            });

            log.debug("Test: Got Record from connector {}:{}, data {} ", connector.getName(), entity.getApiName(),
                    entityData);

            return new StagedBatchRecord()
                    .setStagedBatchId(staged.getId())
                    .setEntityData(entityData)
                    .setExternalRecordId(entityData.getId())
                    .setExternalEntityDefinitionId(entity.getId());
        }));
        stagedExternalRecordRepo.upsert(helper.toExternal(saved, null), entity);
        return batchToSave;
    }


    @Override
    public CurrentBatch fetchSource(DataSourceRequest req) {
        return fetch(req);
    }

    @Override
    public CurrentBatch fetchSourceById(DataSourceRequest req) {
        return fetch(req);
    }

    @Override
    public CurrentBatch fetchSourceFromTestInput(EntityDefinition syncariEntity, PipelineTest test) {
        return null;
    }

    @Override
	public void closeSource(GraphContext context) {
	}

}
