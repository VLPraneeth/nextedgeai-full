package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class SYN_9719_RemoveLastModified {

    @ChangeSet(order = "001", id = "removeLastModified", author = "blesson", runAlways = true)
    public void removeLastModified(MongoTemplate template) {
        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        var attributeDefId = System.getProperty("attributeDefId");
        MongoCollection<Document> attributeDefinition = template.getCollection("attributeDefinition");
        Bson query = new Document("_id", new ObjectId(attributeDefId));
        var attribute = attributeDefinition.find(query).first();
        log.info("Attribute found - {}", attribute);
        if(!dryRun) {
            var deleteResult = attributeDefinition.deleteOne(query);
            log.info("Attribute deleted - {}", deleteResult.getDeletedCount());
        }
    }
}
