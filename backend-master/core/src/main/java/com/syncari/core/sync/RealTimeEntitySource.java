package com.syncari.core.sync;

import com.syncari.connector.EntityData;
import com.syncari.connector.data.*;
import com.syncari.core.DataSourceRequest;
import com.syncari.core.DataTransformer;
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

import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.utils.CollectionUtils.map;

@Slf4j
@Component
public class RealTimeEntitySource implements DataSource {
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

        if (req.getSourceEntities().size() != 1) {
            log.error("Real time request cannot have more than one source entity {}", req);
            return null;
        }

        EntityDefinition sourceEntity = req.getSourceEntities().get(0);
        Connector connector = connectorService.get(sourceEntity.getConnectorId());
        final EntityDefinition syncariEntity = req.getSyncariEntity();

        String entityName = syncariEntity.getApiName();
        CurrentBatch currentBatch = new CurrentBatch(recordRepo, stagingRepo,idMappingService,entityRepoService, featureService, stagedExternalRecordRepo);
        currentBatch.setSyncariEntity(syncariEntity);
        String syncCycleId = UUID.randomUUID().toString();


        log.debug("Saving staged batch record");
        StagedBatch staged = stagingRepo.save(new StagedBatch(entityName).setConnectorId(sourceEntity.getConnectorId())
                        .setCurrentBatchId(syncCycleId).setSourceEntityName(sourceEntity.getApiName()))
                .setSourceEntityDefinitionId(sourceEntity.getId());

        saveBatchRecords(sourceEntity, sourceEntity.getApiNameLowerCasedToAttributes(), connector, staged, List.of(req.getRealTimeSourceData()));

        return currentBatch.setSuccess(true).setSyncariEntityName(entityName).setCurrentBatchId(syncCycleId).setEntityBatch(sourceEntity, staged);
    }

    private List<EntityData> saveBatchRecords(EntityDefinition entity,
                                              Map<String, AttributeDefinition> apiNameToAttrMap,
                                              Connector connector,
                                              StagedBatch staged,
                                              List<EntityData> batchData) {
        String idFieldName = entity.getIdField().map(AttributeDefinition::getApiName).orElse("Id");
        List<StagedBatchRecord> saved = recordRepo.saveAll(map(batchData, d -> {
            EntityData entityData = helper.fixDatatypes(apiNameToAttrMap, d);
            entityData.setConnectorId(connector.getId());
            if(!entityData.has(idFieldName)){
                entityData.addValue(idFieldName, entityData.getId());
            }

            log.debug("Test: Got Record from connector {}:{}, data {} ", connector.getName(), entity.getApiName(),
                    entityData);

            return new StagedBatchRecord()
                    .setStagedBatchId(staged.getId())
                    .setEntityData(entityData)
                    .setExternalRecordId(entityData.getId())
                    .setExternalEntityDefinitionId(entity.getId());
        }));
        stagedExternalRecordRepo.upsert(helper.toExternal(saved, null), entity);
        return batchData;
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
