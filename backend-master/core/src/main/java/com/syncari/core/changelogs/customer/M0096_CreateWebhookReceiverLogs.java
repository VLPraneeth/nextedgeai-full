package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.event.store.StoreSchema;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog(order = "0096")
public class M0096_CreateWebhookReceiverLogs {
    @ChangeSet(order = "001", id = "createWebhookReceiverLogs", author = "sibin")
    public void createWebhookReceiverLogs(MongoTemplate db) {
        MigrationContext.getEventStore().provision(SyncariContext.getSyncariId(), StoreSchema.WEBHOOK_TXN_LOG_TABLE_NAME);
    }
}

