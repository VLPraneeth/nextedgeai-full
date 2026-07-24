package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog(order = "0085")
public class M0089_DeleteSyncariRecords {
    @ChangeSet(order = "001", id = "deleteSyncariRecords", author = "blesson")
    public void addActionsMetadataSeed(MongoTemplate template) {
        MongoCollection<Document> actions = template.getCollection("actionDefinition");

        actions.insertOne(new Document("name", "deleteSyncariRecords")
                .append("seeded", true)
                .append("type", Type.STANDARD.name())
                .append("scope", Scope.ENTITY_AND_ATTRIBUTE.name()));

    }

}
