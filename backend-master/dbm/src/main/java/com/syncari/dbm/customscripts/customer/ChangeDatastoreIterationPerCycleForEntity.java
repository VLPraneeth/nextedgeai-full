package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.Features;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.Connector;
import com.syncari.core.model.DatastoreWatermark;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.misc.Watermark;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.DatastoreService;
import com.syncari.core.service.FeatureService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.service.WatermarkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class ChangeDatastoreIterationPerCycleForEntity {

    @ChangeSet(order = "001", id = "changeDatastoreIterationPerCycleForEntity", author = "abhinav", runAlways = true)
    public void changeDatastoreIterationPerCycleForEntity(MongoTemplate db) {

        ConnectorService connectorService = MigrationContext.getConnectorService();
        DatastoreService datastoreService = MigrationContext.getDatastoreService();
        SchemaService schemaService = MigrationContext.getSchemaService();
        WatermarkService wmService = MigrationContext.getWatermarkService();
        FeatureService featureService = MigrationContext.getFeatureService();

        if(!featureService.isEnabled(Features.Datastore)){
            log.error("Datastore is not enabled for instance {}", SyncariContext.getSyncariId());
            return;
        }
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        Connector datastore = connectorService.getSyncariDatastore()
                .orElseThrow(() -> new RuntimeException("Datastore connector missing"));

        String entityName = System.getProperty("entityName");
        int iterationPerCycle = Integer.parseInt(System.getProperty("iterationPerCycle"));
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName(entityName)
                .orElseThrow(() -> new RuntimeException(String.format("Syncari entity for apiName %s not found", entityName)));
        log.info("Updating datastore watermark for entity {} to change iterations per cycle to {}", entityName, iterationPerCycle);
        DatastoreWatermark dsWm = wmService.getDatastoreWatermark(syncariEntity.getId())
                .orElseThrow(() -> new RuntimeException(String.format("Datastore wm for entityId %s not found", syncariEntity.getId())));
        log.info("Old datastore wm: {}", dsWm);
        dsWm.setIterationsPerCycle(iterationPerCycle);
        if(!dryRunMode) {
            wmService.saveDatastoreWatermark(dsWm);
        }
        log.info("Updated datastore wm: {}", dsWm);
    }
}
