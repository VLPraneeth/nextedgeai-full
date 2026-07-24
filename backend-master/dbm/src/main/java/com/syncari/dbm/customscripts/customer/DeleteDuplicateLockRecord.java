package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class DeleteDuplicateLockRecord {

    @ChangeSet(order = "001", id = "deleteDuplicateLockRecord", author = "abhinav", runAlways = true)
    public void deleteDuplicateLockRecord(MongoTemplate db) {

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        String lockKey = System.getProperty("lockKey");

        MongoCollection<Document> lock = db.getCollection("lock");
        long count = lock.countDocuments(Filters.eq("lockKey", lockKey));
        log.info("Number of records with lockKey {} : {}", lockKey, count);
        if(count > 1) {
            log.info("Deleting all locks belonging to key {}", lockKey);
            if(!dryRunMode) {
                lock.deleteMany(Filters.eq("lockKey", lockKey));
            }
        }

    }
}
