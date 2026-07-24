package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.event.store.StoreSchema;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog(order = "0099")
public class M0099_CreateAbacAuditLog {
    @ChangeSet(order = "001", id = "createAbacAuditLog", author = "sibin")
    public void createAbacAuditLog(MongoTemplate db) {
        MigrationContext.getEventStore().provision(SyncariContext.getSyncariId(), StoreSchema.ABAC_AUDIT_LOG_TABLE_NAME);
    }
}