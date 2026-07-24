package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.Features;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.service.FeatureService;
import com.syncari.core.service.ResyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class CancelResync {

    @ChangeSet(order = "001", id = "cancelResync", author = "venkat", runAlways = true)
    public void cancelResync(MongoTemplate db) {

        String entityId = System.getProperty("entityId");

        var schemaService = MigrationContext.getSchemaService();

        var entityDefinition = schemaService.getEntity(entityId);

        ResyncService resyncService = MigrationContext.getResyncService();
        resyncService.cancel(entityDefinition);
    }

}
