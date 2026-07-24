package com.syncari.core.changelogs.customer;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;

@ChangeLog(order = "0015")
public class M0015_FixIdFields {

    @ChangeSet(order = "001", id = "fixIdFields", author = "neelesh")
    public void fixIdFields(MongoTemplate template) {
        // Noop
    }

    @ChangeSet(order = "002", id = "setIdFields", author = "neelesh")
    public void setIdFields(MongoTemplate template) {
        MongoCollection<Document> attributes = template.getCollection("attributeDefinition");
        attributes.updateMany(new Document("apiName", "id"), new Document("$set",new Document("isIdField",true)), new UpdateOptions().upsert(false));
        attributes.updateMany(new Document("apiName", "Id"), new Document("$set",new Document("isIdField",true)), new UpdateOptions().upsert(false));
        attributes.updateMany(new Document("dataType", "id"), new Document("$set",new Document("isIdField",true)), new UpdateOptions().upsert(false));
    }
}
