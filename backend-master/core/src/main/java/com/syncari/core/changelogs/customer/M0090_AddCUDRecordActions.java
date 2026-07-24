package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog(order = "0090")
public class M0090_AddCUDRecordActions {
    @ChangeSet(order = "001", id = "addCUDRecordActions", author = "neelesh")
    public void addCUDRecordActions(MongoTemplate template) {
        MongoCollection<Document> actions = template.getCollection("actionDefinition");

        actions.insertOne(new Document("name", "insertSyncariRecord")
                .append("seeded", true)
                .append("type", Type.STANDARD.name())
                .append("scope", Scope.ENTITY_AND_ATTRIBUTE.name()));
        actions.insertOne(new Document("name", "updateMultipleSyncariRecords")
                .append("seeded", true)
                .append("type", Type.STANDARD.name())
                .append("scope", Scope.ENTITY_AND_ATTRIBUTE.name()));

    }

}
