package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.model.util.Scope;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog(order = "0094")
public class M0094_CaseFunctionMetadataSeed {
    @ChangeSet(order = "001", id = "caseFunction", author = "sathish")
    public void caseFunctions(MongoTemplate template) {
        MongoCollection<Document> functions = template.getCollection("functionDefinition");

        functions.insertOne(new Document("name", "case")
                .append("seeded", true)
                .append("scope", Scope.ENTITY.name()));

        functions.insertOne(new Document("name", "case")
                .append("seeded", true)
                .append("scope", Scope.ATTRIBUTE.name()));

        functions.insertOne(new Document("name", "caseBranch")
                .append("seeded", true)
                .append("scope", Scope.ENTITY.name()));

        functions.insertOne(new Document("name", "caseBranch")
                .append("seeded", true)
                .append("scope", Scope.ATTRIBUTE.name()));

    }
}
