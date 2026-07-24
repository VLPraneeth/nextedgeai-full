package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

public class SYN_10515_FixOngoingSynFlag {

    @ChangeSet(order = "001", id = "fixOngoingSyncFlag", author = "venkat", runAlways = true)
    public void fixSyncDetailWatermark(MongoTemplate template) {
        MongoCollection<Document> synDetail = template.getCollection("syncDetail");
        String syncDetailId = System.getProperty("syncDetailId");
        Document update = new Document();
        update.append("$set", new Document("onGoingSync", false));
        synDetail.updateOne(new Document("_id", new ObjectId(syncDetailId)), update, new UpdateOptions().upsert(false));
    }
}
