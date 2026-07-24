package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

public class SYN_10510_FixSourceWatermark {

    @ChangeSet(order = "001", id = "fixSourceWatermark", author = "abhinav", runAlways = true)
    public void fixSyncDetailWatermark(MongoTemplate template) {
        MongoCollection<Document> synDetail = template.getCollection("syncDetail");
        String syncDetailId = System.getProperty("syncDetailId");
        Long watermark = Long.valueOf(System.getProperty("watermark"));
        Document update = new Document();
        update.append("$set", new Document("watermark.end", watermark).append("watermark.start", watermark));
        synDetail.updateOne(new Document("_id", new ObjectId(syncDetailId)), update, new UpdateOptions().upsert(false));
    }
}
