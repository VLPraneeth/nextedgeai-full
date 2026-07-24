package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.model.util.Scope;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog(order = "0061")
public class M0061_InsertRecord {

    @ChangeSet(order = "001", id = "insertRecord", author = "neelesh")
    public void insertRecord(MongoTemplate template) {
        MongoCollection<Document> functions = template.getCollection("functionDefinition");

        functions.insertOne(new Document("name", "insertRecord")
                .append("seeded", true)
                .append("scope", Scope.ENTITY.name()));
        functions.insertOne(new Document("name", "insertRecordOnField")
                .append("seeded", true)
                .append("scope", Scope.ATTRIBUTE.name()));
    }
}
