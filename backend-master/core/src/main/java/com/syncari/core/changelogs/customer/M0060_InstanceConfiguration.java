package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog(order = "0060")
public class M0060_InstanceConfiguration {

    @ChangeSet(order = "001", id = "setDefaultDebugMode", author = "sudee")
    public void setDefaultDebugMode(MongoTemplate template) {
        MongoCollection<Document> instanceConfiguration = template.getCollection("instanceConfiguration");
        instanceConfiguration.insertOne(new Document("key","debugMode").append("value",false).append("seeded",true));
    }

    @ChangeSet(order = "002", id = "setDefaultDebugModeExpirySeconds", author = "sudee")
    public void setDefaultDebugModeExpirySeconds(MongoTemplate template) {
        MongoCollection<Document> instanceConfiguration = template.getCollection("instanceConfiguration");
        instanceConfiguration.insertOne(new Document("key","debugModeExpirySecs").append("value",60).append("seeded",true));
    }
}

