package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;

import static com.mongodb.client.model.Updates.*;

@Slf4j
public class SYN_9719_UpdateLastModified {

    @ChangeSet(order = "001", id = "updateLastModified", author = "blesson", runAlways = true)
    public void updateLastModified(MongoTemplate template) {
        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        var entity = System.getProperty("entity");
        MongoCollection<Document> entityRepo = template.getCollection("syncari_" + entity);
        Bson query = Filters.type("lastModified", "date");
        long count = entityRepo.countDocuments(query);
        log.info("{} records will be updated", count);
        if(!dryRun) {
            long currentTime = Instant.now().toEpochMilli();
            UpdateResult result = entityRepo.updateMany(query, set("lastModified", currentTime));
            log.info("{} records updated", result.getModifiedCount());
        }
    }

}
