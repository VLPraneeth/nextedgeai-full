package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.Features;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.repositories.customer.DatasetRepo;
import com.syncari.core.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

@Slf4j
public class UpdateDatasetsEntityDefId {

    @ChangeSet(order = "001", id = "updateDatasetEntdefId", author = "rohit", runAlways = true)
    public void updateDatasetEntdefId(MongoTemplate template) {
        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        FeatureService featureService = MigrationContext.getFeatureService();
        DatasetService datasetService = MigrationContext.getDatasetService();
        DatasetRepo datasetRepo = MigrationContext.getDatasetRepo();
        SchemaService schemaService = MigrationContext.getSchemaService();
        ConnectorService service = MigrationContext.getConnectorService();
        if(featureService.isEnabled(Features.Insights)) {
            datasetService.getAllApprovedDatasetsWithVersion().forEach(dataset -> {
                log.info("Updating dataset for name {}", dataset.getDisplayName());
                String apiName = dataset.getName();
                String connectorId = service.getDatasetConnector().get().getId();
                if (!dryRun) {
                    Optional<EntityDefinition> entityDefinition = schemaService.findEntity(connectorId, apiName);
                    entityDefinition.ifPresentOrElse(e -> {
                        dataset.setEntityDefinitionId(e.getId());
                        datasetRepo.save(dataset);
                        log.info("Updated dataset {}", dataset.getDisplayName());
                    },() -> {
                        log.error("ApiName {} is not present in Entity definition", apiName);
                    });
                }
            });
        } else {
            log.info("Skipping as insights is not enabled");
        }
    }
}
