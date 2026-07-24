package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class SYN_10262_UpdateDatastoreTable {

    @ChangeSet(order = "001", id = "SYN_10262_UpdateDatastoreTable", author = "blesson", runAlways = true)
    public void updateDatastoreTable(MongoTemplate mongoTemplate) {
        var dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        var syncariId = MigrationContext.getSyncariId();
        var datastoreService = MigrationContext.getDatastoreService();
        var tableName = System.getProperty("tableName");
        var columns = System.getProperty("columns").split(":");
        var newLength = System.getProperty("newLength");
        for(String column: columns) {
            log.info("Updating column {} to length {}", column, newLength);
            if(!dryRun) {
                if(datastoreService.alterLength(tableName, column, Integer.parseInt(newLength), syncariId)) log.info("Updated column {} to length {}", column, newLength);
            }
        }
    }
}
