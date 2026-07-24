package com.syncari.core.changelogs.customer;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.DatasetSchemaService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeLog(order = "0099")
public class M0099_AddOrderByToDatasetEntities {

    @ChangeSet(order = "001", id = "addOrderByToDatasetEntities", author = "sathish", runAlways = false)
    public void addOrderByToDatasetEntities(MongoTemplate mongoTemplate) {
        log.info("Starting migration: M0099_AddOrderByToDatasetEntities");

        EntityDefinitionRepo entityDefinitionRepo = MigrationContext.getEntityDefinitionRepo();
        ConnectorService connectorService = MigrationContext.getConnectorService();

        Optional<Connector> datasetConnectorOpt = connectorService.getDatasetConnector();
        if (datasetConnectorOpt.isEmpty() || datasetConnectorOpt.get() == null || datasetConnectorOpt.get().getId() == null) {
            log.error("Dataset connector not found or has null ID. Migration M0099_AddOrderByToDatasetEntities aborted.");
            return;
        }

        String datasetConnectorId = datasetConnectorOpt.get().getId();
        List<EntityDefinition> allDatasetEntityDefs = entityDefinitionRepo.findAllByConnectorId(datasetConnectorId);

        int updatedCount = 0;
        for (EntityDefinition entityDef : allDatasetEntityDefs) {
            List<AttributeDefinition> sourceParams = entityDef.getSourceParams();

            boolean hasOrderBy = sourceParams != null && sourceParams.stream()
                .anyMatch(param -> "orderBy".equals(param.getApiName()));

            if (!hasOrderBy) {
                AttributeDefinition orderByParam = DatasetSchemaService.createOrderBySourceParam();
                if (sourceParams == null) {
                    entityDef.setSourceParams(List.of(orderByParam));
                } else {
                    sourceParams.add(orderByParam);
                }
                entityDefinitionRepo.save(entityDef);
                updatedCount++;
            }
        }

        log.info("Migration M0099_AddOrderByToDatasetEntities completed. Updated {} EntityDefinitions", updatedCount);
    }
}