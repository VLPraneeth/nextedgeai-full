package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.model.util.Scope;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog(order = "0067")
public class M0067_DateParseFunction {

    @ChangeSet(order = "001", id = "dateParseFunction", author = "venkat")
    public void dateParseFunction(MongoTemplate template) {
        MongoCollection<Document> functions = template.getCollection("functionDefinition");

        if (functions.find(new Document("name", "parse")).first() == null) {
            functions.insertOne(new Document("name", "parse")
                    .append("seeded", true)
                    .append("scope", Scope.ATTRIBUTE.name()));
        }
    }

}
