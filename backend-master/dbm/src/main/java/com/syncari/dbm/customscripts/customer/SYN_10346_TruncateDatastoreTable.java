package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class SYN_10346_TruncateDatastoreTable {

    @ChangeSet(order = "001", id = "SYN_10346_TruncateDatastoreTable", author = "venkat", runAlways = true)
    public void updateDatastoreTable(MongoTemplate mongoTemplate) {
        var dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        var datastoreName = System.getProperty("datastoreName");

        var datastoreService = MigrationContext.getDatastoreService();
        if (!dryRun) {
            datastoreService.truncate(datastoreName, datastoreName);
        }
    }
}
