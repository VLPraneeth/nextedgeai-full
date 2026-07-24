package com.syncari.core.changelogs.customer;

import static com.mongodb.client.model.Filters.eq;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;

import lombok.extern.slf4j.Slf4j;

@ChangeLog(order = "0051")
@Slf4j
public class M0051_DashboardSeed {

    @ChangeSet(order = "001", id = "addDqsDashboard", author = "varsha")
    public void addDqsDashboard(MongoTemplate template) {
        MongoCollection<Document> dashboards = template.getCollection("dashboard");
        dashboards.insertOne(new Document("name", "dqsOverview").append("seeded", true));
        dashboards.insertOne(new Document("name", "account_dqsOverview").append("seeded", true));
        dashboards.insertOne(new Document("name", "lead_dqsOverview").append("seeded", true));
        dashboards.insertOne(new Document("name", "contact_dqsOverview").append("seeded", true));
    }
    
    @ChangeSet(order = "002", id = "addDashboardCategory", author = "varsha")
    public void addDashboardCategory(MongoTemplate template) {
        updateCategory(template, "dqsOverview");
        updateCategory(template, "account_dqsOverview");
        updateCategory(template, "lead_dqsOverview");
        updateCategory(template, "contact_dqsOverview");
    }
    
    private void updateCategory(MongoTemplate template, String name) {
        MongoCollection<Document> dashboards = template.getCollection("dashboard");
        Bson query = eq("name", name);
        dashboards.findOneAndUpdate(query,
                new Document("$set", new Document("category", "dqs")));
    }
    
}
