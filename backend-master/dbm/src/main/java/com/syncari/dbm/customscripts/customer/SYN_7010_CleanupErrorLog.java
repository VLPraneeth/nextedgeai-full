package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class SYN_7010_CleanupErrorLog {

    @ChangeSet(order = "001", id = "cleanupErrorLog", author = "sudee")
    public void removeErrorLog(MongoTemplate template) {
        MongoCollection<Document> errorLog = template.getCollection("apiErrorLog");
        var deleteRec = errorLog.deleteMany(new Document());
        log.info("Deleted apiErrorLog records {}", deleteRec.getDeletedCount());
    }
}
