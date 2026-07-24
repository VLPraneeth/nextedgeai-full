package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class ResetOngoingSync {

    @ChangeSet(order = "001", id = "resetOngoingSync", author = "blesson", runAlways = true)
    public void resetOngoingSync(MongoTemplate template) {
        MongoCollection<Document> collection = template.getCollection("syncDetail");
        var syncDetailIds = System.getProperty("syncDetailIds");
        var dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        String[] ids = syncDetailIds.split(";");
        for(String id: ids) {
            Bson query = new Document("_id", new ObjectId(id));
            Document syncDetail = collection.find(query).first();
            log.info("Sync detail found - {}", syncDetail);
            if(!dryRun) {
                log.info("Updating ongoingsync");
                collection.findOneAndUpdate(query, new Document("$set", new Document("onGoingSync", false).append("startTime", 0L).append("endTime", 0L).append("watermark.changeStream", "")));
            }
        }
    }
}
