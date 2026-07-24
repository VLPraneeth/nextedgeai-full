package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.Features;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

@Slf4j
public class CreateDatasetSchemas {

    @ChangeSet(order = "001", id = "createDatasetSchemas", author = "blesson", runAlways = true)
    public void createDatasetSchemas(MongoTemplate template) {
        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        FeatureService featureService = MigrationContext.getFeatureService();
        DatasetService datasetService = MigrationContext.getDatasetService();
        SchemaService schemaService = MigrationContext.getSchemaService();
        ConnectorService connectorService = MigrationContext.getConnectorService();
        DatasetSchemaService datasetSchemaService = MigrationContext.getDatasetSchemaService();
        if(featureService.isEnabled(Features.Insights) || featureService.isEnabled(Features.InsightsProvider)) {
            Optional<Connector> datasetConnector = connectorService.getDatasetConnector();
            datasetConnector.ifPresentOrElse(dsc -> {
                datasetService.getAllApprovedDatasetsWithVersion().forEach(dataset -> {
                    log.info("Creating schema for dataset {}", dataset.getDisplayName());
                    if (!dryRun) {
                        Optional<EntityDefinition> edef  = schemaService.findEntity(dsc.getId(), dataset.getName());
                        edef.ifPresentOrElse(e -> {
                           log.info("EntityDefinition with api name already exists {}", dataset.getName());
                        },()-> {
                            datasetSchemaService.createDatasetSyncariSourceSchema(dataset);
                            log.info("Created schema for dataset {}", dataset.getDisplayName());
                        });
                    }
                });
            },() -> {
                log.info("Dataset connector is not present");
            });
        } else {
            log.info("Skipping as insights is not enabled");
        }
    }
}
