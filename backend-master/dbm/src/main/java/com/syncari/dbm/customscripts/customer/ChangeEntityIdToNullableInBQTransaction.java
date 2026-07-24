package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.event.store.FieldDefinition;
import com.syncari.core.event.store.StoreSchema;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog(order = "001")
public class ChangeEntityIdToNullableInBQTransaction {
    @ChangeSet(order = "001", id = "createTxnLogInBQ", author = "varsha", runAlways = true)
    public void createTxnLogInBQ(MongoTemplate db) {
        MigrationContext.getBigQueryTransactionLogStore().updateField(new FieldDefinition(SyncariContext.getSyncariId(),
                StoreSchema.TXNS_LOG_TABLE_NAME, "entityId", StandardSQLTypeName.STRING, false));
    }
}

