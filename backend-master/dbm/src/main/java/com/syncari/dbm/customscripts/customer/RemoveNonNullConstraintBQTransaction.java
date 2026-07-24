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
public class RemoveNonNullConstraintBQTransaction {
    @ChangeSet(order = "001", id = "removeConstraintTxnLogBQ", author = "venkat", runAlways = true)
    public void removeConstraintTxnLogBQ(MongoTemplate db) {
        MigrationContext.getEventStore().addFieldToTable(new FieldDefinition(SyncariContext.getSyncariId(),
                StoreSchema.TXNS_LOG_TABLE_NAME, "batchId", StandardSQLTypeName.STRING, false));
    }
}
