package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class ClearSyncaribeeLock {

    @ChangeSet(order = "001", id = "clearSyncaribeeLock", author = "varsha", runAlways = true)
    public void clearSyncaribeeLock(MongoTemplate db) {

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        MongoCollection<Document> lock = db.getCollection("syncaribeelock");
        if(!dryRunMode) {
            lock.deleteMany(Filters.eq("key", "LOCK"));
        }
    }
}
