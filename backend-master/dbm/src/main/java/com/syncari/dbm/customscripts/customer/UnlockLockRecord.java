package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.syncari.core.model.Lock;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class UnlockLockRecord {

    @ChangeSet(order = "001", id = "unlockLockRecord", author = "venkat", runAlways = true)
    public void unlockLockRecord(MongoTemplate db) {

        String lockKey = System.getProperty("lockKey");

        MongoCollection<Document> lock = db.getCollection("lock");
        lock.updateOne(Filters.eq("lockKey", lockKey), new Document("$set", new Document("status", "UNLOCKED")));
    }
}
