package com.syncari.core.changelogs.customer;

import java.util.List;

import com.syncari.core.event.store.BigQueryHelper;
import com.syncari.core.event.store.FieldDefinition;
import com.syncari.core.event.store.StoreSchema;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.event.store.EventStore;

@ChangeLog(order = "0019")
public class M0019_UpdateBigQuery {

    @ChangeSet(order = "001", id = "updateBigQueryTables", author = "neelesh")
    public void updateBigQueryTables(MongoTemplate template) {
        EventStore eventStore = MigrationContext.getEventStore();
        BigQueryHelper helper = MigrationContext.getBigQueryHelper();
        var newFields = List.of(
                new FieldDefinition(SyncariContext.getSyncariId(), StoreSchema.SYNC_LOG_TABLE_NAME,"failedWinningRecord", StandardSQLTypeName.STRING, false),
                new FieldDefinition(SyncariContext.getSyncariId(),StoreSchema.SYNC_LOG_TABLE_NAME,"failedRecords", StandardSQLTypeName.ARRAY, false)
        );
        helper.addFields(newFields);
        eventStore.provision(SyncariContext.getSyncariId(), StoreSchema.TXN_LOG_TABLE_NAME);
    }
    
    @ChangeSet(order = "002", id = "addErrorLogTable", author = "varsha")
    public void addErrorLogTable(MongoTemplate template) {
        EventStore eventStore = MigrationContext.getEventStore();
        eventStore.provision(SyncariContext.getSyncariId(), StoreSchema.ERROR_LOG_TABLE_NAME);
    }
}
	

