package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Indexes;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.event.store.EventStore;
import com.syncari.core.event.store.StoreSchema;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog(order = "0092")
public class M0092_CreatenewTxnLogInBQ {
    @ChangeSet(order = "001", id = "createTxnLogInBQ", author = "varsha")
    public void createTxnLogInBQ(MongoTemplate db) {
        MigrationContext.getEventStore().provision(SyncariContext.getSyncariId(), StoreSchema.TXNS_LOG_TABLE_NAME);
    }
}

