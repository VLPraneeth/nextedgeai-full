package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import org.springframework.data.mongodb.core.MongoTemplate;

public class SYN_10450_DropDatastoreIndex {
    @ChangeSet(order = "001", id = "SYN_10450_DropDatastoreIndex", author = "venkat", runAlways = true)
    public void dropDatastoreIndex(MongoTemplate mongoTemplate) {
        var dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        var tableName = System.getProperty("tableName");
        var constraintName = System.getProperty("constraintName");
        var indexName = System.getProperty("indexName");
        var syncariId = MigrationContext.getSyncariId();
        var datastoreService = MigrationContext.getDatastoreService();
        datastoreService.getIndexes(tableName);
        datastoreService.getConstraints(tableName);
        if (!dryRun) {
            datastoreService.dropConstraint(constraintName, tableName, syncariId);
            datastoreService.dropIndex(indexName, syncariId);
        }
    }
}
