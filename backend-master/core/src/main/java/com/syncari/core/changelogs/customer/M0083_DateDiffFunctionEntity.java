package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.model.util.Scope;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog(order = "0083")
public class M0083_DateDiffFunctionEntity {

    @ChangeSet(order = "001", id = "addDateDiffOnEntity", author = "santosh")
    public void addDateDiffOnEntity(MongoTemplate mongoTemplate){
        MongoCollection<Document> functions = mongoTemplate.getCollection("functionDefinition");
        functions.insertOne(new Document("name", "dateDiffOnEntity")
                .append("seeded", true)
                .append("scope", Scope.ENTITY.name()));
    }
}
