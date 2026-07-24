package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.event.store.StoreSchema;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog(order = "0094")
public class M0094_CreatePipelineNodeAuditInBQ {
    @ChangeSet(order = "001", id = "createNodeAuditInBQ", author = "varsha")
    public void createNodeAuditInBQ(MongoTemplate db) {
        MigrationContext.getEventStore().provision(SyncariContext.getSyncariId(), StoreSchema.NODE_AUDIT_TABLE_NAME);
    }
}

