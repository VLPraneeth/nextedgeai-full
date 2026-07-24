package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.model.util.Scope;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog(order = "0082")
public class M0082_DateDiffFunction {

    @ChangeSet(order = "001", id = "addDateDiff", author = "santosh")
    public void addDateDiff(MongoTemplate mongoTemplate){
        MongoCollection<Document> functions = mongoTemplate.getCollection("functionDefinition");
        functions.insertOne(new Document("name", "dateDiff")
                .append("seeded", true)
                .append("scope", Scope.ATTRIBUTE.name()));
    }
}
