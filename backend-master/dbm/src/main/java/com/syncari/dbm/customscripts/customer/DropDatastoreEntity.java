package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.DatastoreService;
import com.syncari.core.service.SchemaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class DropDatastoreEntity {

    @ChangeSet(order = "001", id = "deleteDatastoreEntity", author = "venkat", runAlways = true)
    public void deleteDatastoreEntity(MongoTemplate db) {

        ConnectorService connectorService = MigrationContext.getConnectorService();
        DatastoreService datastoreService = MigrationContext.getDatastoreService();
        SchemaService schemaService = MigrationContext.getSchemaService();

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        Connector datastore = connectorService.getSyncariDatastore()
                .orElseThrow(() -> new RuntimeException("Datastore connector missing"));


        String syncariEntityName = System.getProperty("entityName");
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName(syncariEntityName)
                .orElseThrow(() -> new RuntimeException(String.format("Syncari entity for apiName %s not found", syncariEntityName)));
        log.info("Deleting Datastore table for entity {} and watermark will be reset to epoch", syncariEntityName);
        if(!dryRunMode) {
            datastoreService.deleteEntity(syncariEntity, datastore);
        }
    }
}
