package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import com.syncari.core.model.util.Type;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import static com.mongodb.client.model.Filters.eq;

public class UpdateTypeForRequeueAction {

    @ChangeSet(order = "001", id = "requeueActionUpdateType", author = "abhinav")
    public void requeueActionUpdateType(MongoTemplate template) {
        MongoCollection<Document> actionDefinition = template.getCollection("actionDefinition");

        actionDefinition.updateOne(eq("name", "requeueRecord"),
                new Document("$set", new Document("type", Type.STANDARD.name())),
                new UpdateOptions().upsert(false));
    }
}
