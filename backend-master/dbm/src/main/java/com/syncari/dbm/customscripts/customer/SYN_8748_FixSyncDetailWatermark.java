package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class SYN_8748_FixSyncDetailWatermark {
    @ChangeSet(order = "001", id = "fixSyncDetailWatermark", author = "venkat", runAlways = true)
    public void fixSyncDetailWatermark(MongoTemplate template) {
        MongoCollection<Document> synDetail = template.getCollection("syncDetail");
        synDetail.updateOne(new Document("_id", new ObjectId("625340c228d25e000156be13")), new Document("$set", new Document("watermark.end", 1659741682721L)));
    }
}
