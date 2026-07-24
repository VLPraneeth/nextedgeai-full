package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog(order = "0091")
public class M0091_AddLookupdatasetFunctions {
    @ChangeSet(order = "001", id = "addLookupdatasetFunctions", author = "neelesh")
    public void addCUDRecordActions(MongoTemplate template) {
        MongoCollection<Document> actions = template.getCollection("functionDefinition");

        actions.insertOne(new Document("name", "lookupDataset")
                .append("seeded", true)
                .append("type", Type.STANDARD.name())
                .append("scope", Scope.ENTITY.name()));
        actions.insertOne(new Document("name", "lookupDatasetOnField")
                .append("seeded", true)
                .append("type", Type.STANDARD.name())
                .append("scope", Scope.ATTRIBUTE.name()));

    }

}
