package com.syncari.core.changelogs.customer;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.model.util.Scope;

@ChangeLog(order = "0063")
public class M0065_AggregateFunctions {

    @ChangeSet(order = "001", id = "aggregateFunctions", author = "neelesh")
    public void aggregateFunctions(MongoTemplate template) {
        MongoCollection<Document> functions = template.getCollection("functionDefinition");

        functions.insertOne(new Document("name", "sumRecords")
                .append("seeded", true)
                .append("scope", Scope.ENTITY.name()));
        functions.insertOne(new Document("name", "sumRecordsOnField")
                .append("seeded", true)
                .append("scope", Scope.ATTRIBUTE.name()));
        functions.insertOne(new Document("name", "avgRecords")
                .append("seeded", true)
                .append("scope", Scope.ENTITY.name()));
        functions.insertOne(new Document("name", "avgRecordsOnField")
                .append("seeded", true)
                .append("scope", Scope.ATTRIBUTE.name()));
        functions.insertOne(new Document("name", "stdDevRecords")
                .append("seeded", true)
                .append("scope", Scope.ENTITY.name()));
        functions.insertOne(new Document("name", "stdDevRecordsOnField")
                .append("seeded", true)
                .append("scope", Scope.ATTRIBUTE.name()));
        functions.insertOne(new Document("name", "countRecords")
                .append("seeded", true)
                .append("scope", Scope.ENTITY.name()));
        functions.insertOne(new Document("name", "countRecordsOnField")
                .append("seeded", true)
                .append("scope", Scope.ATTRIBUTE.name()));
    }

}
