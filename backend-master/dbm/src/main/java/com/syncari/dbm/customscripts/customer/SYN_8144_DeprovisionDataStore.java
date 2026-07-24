package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.Features;
import com.syncari.core.MigrationContext;
import com.syncari.core.service.FeatureService;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class SYN_8144_DeprovisionDataStore {

    @ChangeSet(order = "001", id = "deprovisionDataStore", author = "varsha")
    public void deprovisionDataStore(MongoTemplate template) {
        MongoCollection<Document> connector = template.getCollection("connector");
        MongoCollection<Document> datastoreWatermark = template.getCollection("datastoreWatermark");
        MongoCollection<Document> features = template.getCollection("feature");
        log.info("Disable datastore");
        features.findOneAndUpdate(new Document("name", "Datastore"), new Document("$set", new Document("status", "inactive")));
        connector.deleteOne(new Document("name", "Syncari Datastore").append("metadataId", "5ed6de6d7df51d500f5d237b"));
        log.info("Deleted datastore connector");
        datastoreWatermark.deleteMany(new Document());
        log.info("Cleared datastore watermarks");
    }
}