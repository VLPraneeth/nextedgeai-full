package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.syncari.core.model.insights.DashboardLayout;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

@Slf4j
@ChangeLog(order = "0077")
public class M0077_InsightsDashboardSeed {
    // TODO - Remove this file

    //@ChangeSet(order = "001", id = "marketingDashboard", author = "abhinav")
    public void marketingDashboard(MongoTemplate template){

        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");

        Document dashboard = new Document("name", "marketing")
                .append("displayName", "Marketing")
                .append("description", "Marketing Dashboard")
                .append("dataCardIds", List.of());

        dashboardCollection.insertOne(dashboard);

        // retrieve saved dashboard
        Document retrievedDashboard = dashboardCollection.find(new Document("name", "marketing")).first();
        ObjectId dashboardId = retrievedDashboard.getObjectId("_id");

        // add datacards
        addQuarterlyClosedPipelineRevenue(dashboardId, template);
        addQuarterlyClosedPipelineRevenueByType(dashboardId, template);
        addAnnualRecurringRevenue(dashboardId, template);
        addLeadsBySource(dashboardId, template);
    }

    //@ChangeSet(order = "002", id = "marketingDashboardOpenPipelineCount", author = "rohit")
    public void marketingDashboardOpenPipelineCount(MongoTemplate template){

        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");

        // retrieve saved dashboard
        Document retrievedDashboard = dashboardCollection.find(new Document("name", "marketing")).first();
        ObjectId dashboardId = retrievedDashboard.getObjectId("_id");
        addOpenPipelineTotal(dashboardId, template);
    }
    //@ChangeSet(order = "003", id = "marketingDashboardOpenPipeline", author = "rohit")
    public void marketingDashboardOpenPipeline(MongoTemplate template){

        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");

        // retrieve saved dashboard
        Document retrievedDashboard = dashboardCollection.find(new Document("name", "marketing")).first();
        ObjectId dashboardId = retrievedDashboard.getObjectId("_id");
        addOpenPipeline(dashboardId, template);
    }

    //@ChangeSet(order = "004", id = "marketingDashboardExistingCustomerCount", author = "rohit")
    public void marketingDashboardExistingCustomerCount(MongoTemplate template){

        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");

        // retrieve saved dashboard
        Document retrievedDashboard = dashboardCollection.find(new Document("name", "marketing")).first();
        ObjectId dashboardId = retrievedDashboard.getObjectId("_id");
        addExistingCustomercount(dashboardId, template);
    }

    //@ChangeSet(order = "005", id = "salesDashboard", author = "rohit")
    public void salesDashboard(MongoTemplate template){

        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");

        Document dashboard = new Document("name", "sales")
                .append("displayName", "Sales Dashboard")
                .append("description", "Sales Dashboard")
                .append("dataCardIds", List.of());

        dashboardCollection.insertOne(dashboard);

        // retrieve saved dashboard
        Document retrievedDashboard = dashboardCollection.find(new Document("name", "sales")).first();
        ObjectId dashboardId = retrievedDashboard.getObjectId("_id");

        // add datacards
        // add datacards
        addQuarterlyClosedPipelineRevenue(dashboardId, template);
        addQuarterlyClosedPipelineRevenueByType(dashboardId, template);
        addAnnualRecurringRevenue(dashboardId, template);
        addOpenPipelineTotal(dashboardId, template);
        addOpenPipeline(dashboardId, template);
        addExistingCustomercount(dashboardId, template);
        addOpenPipelineByType(dashboardId, template);
    }

    //@ChangeSet(order = "006", id = "salesDashboardSalesFunnel", author = "rohit")
    public void salesDashboardSalesFunnel(MongoTemplate template){

        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");

        // retrieve saved dashboard
        Document retrievedDashboard = dashboardCollection.find(new Document("name", "sales")).first();
        ObjectId dashboardId = retrievedDashboard.getObjectId("_id");
        addSalesFunnel(dashboardId, template);
    }

    //@ChangeSet(order = "007", id = "marketingDashboardSalesQualifiedLeadByOwner", author = "abhinav")
    public void marketingDashboardSalesQualifiedLeadByOwner(MongoTemplate template){

        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");

        // retrieve saved dashboard
        Document retrievedDashboard = dashboardCollection.find(new Document("name", "marketing")).first();
        ObjectId dashboardId = retrievedDashboard.getObjectId("_id");
        addQualifiedLeadsByOwner(dashboardId, template);
    }

    //@ChangeSet(order = "008", id = "top10custbyrevenue", author = "rohit")
    public void top10custbyrevenue(MongoTemplate template){

        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");

        // retrieve saved dashboard
        Document retrievedDashboard = dashboardCollection.find(new Document("name", "sales")).first();
        ObjectId dashboardId = retrievedDashboard.getObjectId("_id");
        addTop10custsbyrev(dashboardId, template);
    }

    //@ChangeSet(order = "009", id = "supportDashboard", author = "rohit")
    public void supportDashboard(MongoTemplate template){

        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");

        Document dashboard = new Document("name", "support")
                .append("displayName", "Support Dashboard")
                .append("description", "Support Dashboard")
                .append("dataCardIds", List.of());

        dashboardCollection.insertOne(dashboard);

        // retrieve saved dashboard
        Document retrievedDashboard = dashboardCollection.find(new Document("name", "support")).first();
        ObjectId dashboardId = retrievedDashboard.getObjectId("_id");

        addQuarterlyClosedPipelineRevenue(dashboardId, template);
        addQuarterlyClosedPipelineRevenueByType(dashboardId, template);
        addAnnualRecurringRevenue(dashboardId, template);
        addOpenPipelineTotal(dashboardId, template);
        addOpenPipeline(dashboardId, template);
        addExistingCustomercount(dashboardId, template);
    }

    //@ChangeSet(order = "010", id = "marketingDashboardMarketingQualifiedLeadCount", author = "abhinav")
    public void marketingDashboardMarketingQualifiedLeadCount(MongoTemplate template){

        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");

        // retrieve saved dashboard
        Document retrievedDashboard = dashboardCollection.find(new Document("name", "marketing")).first();
        ObjectId dashboardId = retrievedDashboard.getObjectId("_id");
        addMQLCount(dashboardId, template);
    }

    private void addTop10custsbyrev(ObjectId dashboardId, MongoTemplate template){
        MongoCollection<Document> datacardCollection = template.getCollection("datacard");
        Document datacard = datacardCollection.find(new Document("name", "top10CustomersByRevenue")).first();
        ObjectId datacardId = datacard.getObjectId("_id");

        addDatacardToDashboard(dashboardId, datacardId, template);
        DashboardLayout layout = new DashboardLayout().setMinH(1).setMaxH(0)
                .setWidth(4).setHeight(1).setX(0).setY(0).setResizable(false);

        addDatacardLayout(dashboardId, datacardId, layout, template);
    }

    private void addSalesFunnel(ObjectId dashboardId, MongoTemplate template){
        MongoCollection<Document> datacardCollection = template.getCollection("datacard");
        Document datacard = datacardCollection.find(new Document("name", "salesFunnel")).first();
        ObjectId datacardId = datacard.getObjectId("_id");

        addDatacardToDashboard(dashboardId, datacardId, template);
        DashboardLayout layout = new DashboardLayout().setMinH(1).setMaxH(0)
                .setWidth(4).setHeight(1).setX(0).setY(0).setResizable(false);

        addDatacardLayout(dashboardId, datacardId, layout, template);
    }
    private void addQuarterlyClosedPipelineRevenue(ObjectId dashboardId, MongoTemplate template){
        MongoCollection<Document> datacardCollection = template.getCollection("datacard");
        Document datacard = datacardCollection.find(new Document("name", "quarterlyClosedPipelineRevenue")).first();
        ObjectId datacardId = datacard.getObjectId("_id");

        addDatacardToDashboard(dashboardId, datacardId, template);
        DashboardLayout layout = new DashboardLayout().setMinH(1).setMaxH(0)
                .setWidth(4).setHeight(1).setX(0).setY(0).setResizable(false);

        addDatacardLayout(dashboardId, datacardId, layout, template);
    }

    private void addQuarterlyClosedPipelineRevenueByType(ObjectId dashboardId, MongoTemplate template){
        MongoCollection<Document> datacardCollection = template.getCollection("datacard");
        Document datacard = datacardCollection.find(new Document("name", "quarterlyClosedPipelineRevenueByType")).first();
        ObjectId datacardId = datacard.getObjectId("_id");

        addDatacardToDashboard(dashboardId, datacardId, template);
        DashboardLayout layout = new DashboardLayout().setMinH(1).setMaxH(0)
                .setWidth(4).setHeight(1).setX(0).setY(0).setResizable(false);

        addDatacardLayout(dashboardId, datacardId, layout, template);
    }

    private void addAnnualRecurringRevenue(ObjectId dashboardId, MongoTemplate template){
        MongoCollection<Document> datacardCollection = template.getCollection("datacard");
        Document datacard = datacardCollection.find(new Document("name", "annualRecurringRevenue")).first();
        ObjectId datacardId = datacard.getObjectId("_id");

        addDatacardToDashboard(dashboardId, datacardId, template);
        DashboardLayout layout = new DashboardLayout().setMinH(1).setMaxH(0)
                .setWidth(4).setHeight(1).setX(0).setY(0).setResizable(false);

        addDatacardLayout(dashboardId, datacardId, layout, template);
    }

    private void addLeadsBySource(ObjectId dashboardId, MongoTemplate template){
        MongoCollection<Document> datacardCollection = template.getCollection("datacard");
        Document datacard = datacardCollection.find(new Document("name", "leadCountBySource")).first();
        ObjectId datacardId = datacard.getObjectId("_id");

        addDatacardToDashboard(dashboardId, datacardId, template);
        DashboardLayout layout = new DashboardLayout().setMinH(1).setMaxH(0)
                .setWidth(4).setHeight(1).setX(0).setY(0).setResizable(false);

        addDatacardLayout(dashboardId, datacardId, layout, template);
    }

    private void addOpenPipelineTotal(ObjectId dashboardId, MongoTemplate template){
        MongoCollection<Document> datacardCollection = template.getCollection("datacard");
        Document datacard = datacardCollection.find(new Document("name", "allOpenPipelineTotal")).first();
        ObjectId datacardId = datacard.getObjectId("_id");

        addDatacardToDashboard(dashboardId, datacardId, template);
        DashboardLayout layout = new DashboardLayout().setMinH(1).setMaxH(0)
                .setWidth(4).setHeight(1).setX(0).setY(0).setResizable(false);

        addDatacardLayout(dashboardId, datacardId, layout, template);
    }

    private void addOpenPipeline(ObjectId dashboardId, MongoTemplate template){
        MongoCollection<Document> datacardCollection = template.getCollection("datacard");
        Document datacard = datacardCollection.find(new Document("name", "nextFewQuaterOpenPipelines")).first();
        ObjectId datacardId = datacard.getObjectId("_id");

        addDatacardToDashboard(dashboardId, datacardId, template);
        DashboardLayout layout = new DashboardLayout().setMinH(1).setMaxH(0)
                .setWidth(4).setHeight(1).setX(0).setY(0).setResizable(false);

        addDatacardLayout(dashboardId, datacardId, layout, template);
    }



    private void addExistingCustomercount(ObjectId dashboardId, MongoTemplate template){
        MongoCollection<Document> datacardCollection = template.getCollection("datacard");
        Document datacard = datacardCollection.find(new Document("name", "existingCustomerCount")).first();
        ObjectId datacardId = datacard.getObjectId("_id");

        addDatacardToDashboard(dashboardId, datacardId, template);
        DashboardLayout layout = new DashboardLayout().setMinH(1).setMaxH(0)
                .setWidth(4).setHeight(1).setX(0).setY(0).setResizable(false);

        addDatacardLayout(dashboardId, datacardId, layout, template);
    }





    private void addOpenPipelineByType(ObjectId dashboardId, MongoTemplate template){
        MongoCollection<Document> datacardCollection = template.getCollection("datacard");
        Document datacard = datacardCollection.find(new Document("name", "allOpenPipelineByType")).first();
        ObjectId datacardId = datacard.getObjectId("_id");

        addDatacardToDashboard(dashboardId, datacardId, template);
        DashboardLayout layout = new DashboardLayout().setMinH(1).setMaxH(0)
                .setWidth(4).setHeight(1).setX(0).setY(0).setResizable(false);

        addDatacardLayout(dashboardId, datacardId, layout, template);
    }

    private void addQualifiedLeadsByOwner(ObjectId dashboardId, MongoTemplate template){
        MongoCollection<Document> datacardCollection = template.getCollection("datacard");
        Document datacard = datacardCollection.find(new Document("name", "sqlLeadCountByOwner")).first();
        ObjectId datacardId = datacard.getObjectId("_id");

        addDatacardToDashboard(dashboardId, datacardId, template);
        DashboardLayout layout = new DashboardLayout().setMinH(1).setMaxH(0)
                .setWidth(4).setHeight(1).setX(0).setY(0).setResizable(false);

        addDatacardLayout(dashboardId, datacardId, layout, template);
    }

    private void addMQLCount(ObjectId dashboardId, MongoTemplate template){
        MongoCollection<Document> datacardCollection = template.getCollection("datacard");
        Document datacard = datacardCollection.find(new Document("name", "mqlCountInQuarter")).first();
        ObjectId datacardId = datacard.getObjectId("_id");

        addDatacardToDashboard(dashboardId, datacardId, template);
        DashboardLayout layout = new DashboardLayout().setMinH(1).setMaxH(0)
                .setWidth(4).setHeight(1).setX(0).setY(0).setResizable(false);

        addDatacardLayout(dashboardId, datacardId, layout, template);
    }

    private void addDatacardToDashboard(ObjectId dashboardId, ObjectId datacardId, MongoTemplate template){
        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");

        dashboardCollection.updateOne(
                Filters.eq("_id", dashboardId),
                Updates.push("dataCardIds", datacardId.toHexString())
        );
    }

    private void addDatacardLayout(ObjectId dashboardId, ObjectId datacardId, DashboardLayout layout, MongoTemplate template){
        MongoCollection<Document> dataCardAuthorConfigCollection = template.getCollection("dataCardAuthorConfig");
        Document dashboardLayout = new Document("minH", layout.getMinH()).append("maxH", layout.getMaxH())
                .append("width", layout.getWidth()).append("height", layout.getHeight())
                .append("x", layout.getX()).append("y", layout.getY()).append("resizable", layout.isResizable());
        Document authorConfig = new Document("dashboardId", dashboardId.toHexString())
                .append("datacardId", datacardId.toHexString())
                .append("dataCardSetting", new Document("layout", dashboardLayout));

        dataCardAuthorConfigCollection.insertOne(authorConfig);
    }
}
