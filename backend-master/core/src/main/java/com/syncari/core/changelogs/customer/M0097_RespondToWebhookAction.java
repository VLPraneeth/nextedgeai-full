package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.actions.ActionConstants;
import com.syncari.core.actions.ActionsSeed;
import com.syncari.core.event.store.StoreSchema;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog(order = "0096")
public class M0097_RespondToWebhookAction {

    @ChangeSet(order = "001", id = "respondToWebhook", author = "venkat")
    public void addrespondToWebhookSeed(MongoTemplate template) {
        MongoCollection<Document> actions = template.getCollection("actionDefinition");

        actions.insertOne(new Document("name", ActionConstants.RESPOND_TO_WEBHOOK)
                .append("seeded", true)
                .append("type", Type.STANDARD.name())
                .append("scope", Scope.ENTITY_AND_ATTRIBUTE.name()));
    }
}

