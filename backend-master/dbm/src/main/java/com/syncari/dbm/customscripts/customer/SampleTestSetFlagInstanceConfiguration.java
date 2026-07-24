package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

public class SampleTestSetFlagInstanceConfiguration {

    @ChangeSet(order = "001", id = "setTestFlag", author = "sudee")
    public void setTestFlag(MongoTemplate template) {
        MongoCollection<Document> instanceConfiguration = template.getCollection("instanceConfiguration");
        instanceConfiguration.insertOne(new Document("key","testFlag").append("value",false).append("seeded",true));
    }

    @ChangeSet(order = "002", id = "setTestFlag2", author = "sudee")
    public void setTestFlag2(MongoTemplate template) {
        MongoCollection<Document> instanceConfiguration = template.getCollection("instanceConfiguration");
        instanceConfiguration.insertOne(new Document("key","testFlag2").append("value",false).append("seeded",true));
    }
}
