package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

public class CreateInsightsDashboard {

    @ChangeSet(order = "001", id = "createSeededDashboard", author = "abhinav", runAlways = true)
    public void createSeededDashboard(MongoTemplate template){
        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");
        Document marketing = new Document("name", "marketing")
                .append("displayName", "Marketing")
                .append("description", "Marketing Dashboard")
                .append("dataCardIds", List.of())
                .append("draftStatus", "APPROVED")
                .append("seeded", true);

        dashboardCollection.insertOne(marketing);

        Document sales = new Document("name", "sales")
                .append("displayName", "Sales")
                .append("description", "Sales Dashboard")
                .append("dataCardIds", List.of())
                .append("draftStatus", "APPROVED")
                .append("seeded", true);
        dashboardCollection.insertOne(sales);

        Document ceo = new Document("name", "executive")
                .append("displayName", "Executive")
                .append("description", "Executive Dashboard")
                .append("dataCardIds", List.of())
                .append("draftStatus", "APPROVED")
                .append("seeded", true);
        dashboardCollection.insertOne(ceo);

        Document success = new Document("name", "success")
                .append("displayName", "Success")
                .append("description", "Success Dashboard")
                .append("dataCardIds", List.of())
                .append("draftStatus", "APPROVED")
                .append("seeded", true);
        dashboardCollection.insertOne(success);
    }
}
