package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.syncari.core.model.insights.DashboardLayout;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

@ChangeLog(order = "0078")
public class M0078_CreateInsightsDashboard {

    @ChangeSet(order = "001", id = "marketingDashboard", author = "abhinav")
    public void marketingDashboard(MongoTemplate template){
        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");
        Document dashboard = new Document("name", "marketing")
                .append("displayName", "Marketing")
                .append("description", "Marketing Dashboard")
                .append("dataCardIds", List.of())
                .append("draftStatus", "APPROVED")
                .append("seeded", true);

        dashboardCollection.insertOne(dashboard);
    }

    @ChangeSet(order = "002", id = "salesDashboard", author = "abhinav")
    public void salesDashboard(MongoTemplate template){
        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");
        Document dashboard = new Document("name", "sales")
                .append("displayName", "Sales")
                .append("description", "Sales Dashboard")
                .append("dataCardIds", List.of())
                .append("draftStatus", "APPROVED")
                .append("seeded", true);

        dashboardCollection.insertOne(dashboard);
    }

    @ChangeSet(order = "003", id = "ceoDashboard", author = "abhinav")
    public void ceoDashboard(MongoTemplate template){
        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");
        Document dashboard = new Document("name", "executive")
                .append("displayName", "Executive")
                .append("description", "Executive Dashboard")
                .append("dataCardIds", List.of())
                .append("draftStatus", "APPROVED")
                .append("seeded", true);

        dashboardCollection.insertOne(dashboard);
    }

    @ChangeSet(order = "004", id = "successDashboard", author = "abhinav")
    public void successDashboard(MongoTemplate template){
        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");
        Document dashboard = new Document("name", "success")
                .append("displayName", "Success")
                .append("description", "Success Dashboard")
                .append("dataCardIds", List.of())
                .append("draftStatus", "APPROVED")
                .append("seeded", true);

        dashboardCollection.insertOne(dashboard);
    }

}
