package com.syncari.dbm.customscripts.customer;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import lombok.extern.slf4j.Slf4j;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;

@Slf4j
public class SYN_8163_Update_Ceo_Dashboard {

    @ChangeSet(order = "001", id = "updateCeoDashboard", author = "francis")
    public void updateCeoDashboard(MongoTemplate template) {
        MongoCollection<Document> insightsDashboard = template.getCollection("insightsDashboard");
        insightsDashboard.updateOne(and(eq("name", "ceo"), eq("description", "CEO Dashboard"), eq("displayName", "CEO")),
                new Document("$set", new Document("name", "executive").append("description", "Executive Dashboard").append("displayName", "Executive")),
                new UpdateOptions().upsert(false)
        );
    }
}