package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.Block;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import static com.mongodb.client.model.Updates.set;

@Slf4j
public class SYN_5743_ResetMySQLIdField {

    @ChangeSet(order = "001", id = "resetMySQLIdField", author = "blesson")
    public void resetMySQLIdField(MongoTemplate template) {
        log.info("Updating attributeDefinition");
        MongoCollection<Document> attributeDefinition = template.getCollection("attributeDefinition");
        ObjectId id = new ObjectId("615b3a663422e0000151c028");
        attributeDefinition.find(new Document("_id", id)).forEach((Block<? super Document>) doc -> {
            ObjectId adId = doc.getObjectId("_id");
            log.info("Updating attributeDefinition object with _id {} ", adId);
            attributeDefinition.findOneAndUpdate(new Document("_id", adId), set("isIdField", false));
        });
    }
}
