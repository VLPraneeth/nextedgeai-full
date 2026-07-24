package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.model.util.Scope;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog(order = "0066")
public class M0066_ListFunctions {

    @ChangeSet(order = "001", id = "listFunctions", author = "venkat")
    public void listFunctions(MongoTemplate template) {
        MongoCollection<Document> functions = template.getCollection("functionDefinition");

        functions.insertOne(new Document("name", "addToList")
                .append("seeded", true)
                .append("scope", Scope.ATTRIBUTE.name()));
        functions.insertOne(new Document("name", "removeFromList")
                .append("seeded", true)
                .append("scope", Scope.ATTRIBUTE.name()));
    }

}
