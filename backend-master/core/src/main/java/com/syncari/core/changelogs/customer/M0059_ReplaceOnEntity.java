package com.syncari.core.changelogs.customer;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.model.util.Scope;

@ChangeLog(order = "0059")
public class M0059_ReplaceOnEntity {

    @ChangeSet(order = "001", id = "replaceOnEntity", author = "neelesh")
    public void replaceOnEntity(MongoTemplate template) {
        MongoCollection<Document> functions = template.getCollection("functionDefinition");

        functions.insertOne(new Document("name", "replaceOnEntity")
                .append("seeded", true)
                .append("scope", Scope.ENTITY.name()));
    }
}
