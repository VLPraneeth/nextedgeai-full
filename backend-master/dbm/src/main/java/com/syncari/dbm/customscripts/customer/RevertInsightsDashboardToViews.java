package com.syncari.dbm.customscripts.customer;

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
public class RevertInsightsDashboardToViews {

    @ChangeSet(order = "001", id = "updateMarketingDashboardDatacardRevert", author = "rohit", runAlways = true)
    public void updateMarketingDashboardDatacardRevert(MongoTemplate template){

        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");
        Document dashboard = dashboardCollection.find(new Document("name", "marketing")).first();
        removeDatacardFromDashboard("quarterlyClosedPipelineRevenueDC", dashboard, template, 8, 4);
        addDatacard("quarterlyClosedPipelineRevenue", dashboard, template, 8, 4);
        removeDatacardFromDashboard("annualRecurringRevenueDC", dashboard, template, 0, 4);
        addDatacard("annualRecurringRevenue", dashboard, template, 0, 4);
        removeDatacardFromDashboard("quarterlyClosedPipelineRevenueByTypeDC", dashboard, template, 4, 4);
        addDatacard("quarterlyClosedPipelineRevenueByType", dashboard, template, 4, 4);
        removeDatacardFromDashboard("avgRevenueForAllAccountsDC", dashboard, template, 4, 6);
        addDatacard("avgRevenueForAllAccounts", dashboard, template, 4, 6);
        removeDatacardFromDashboard("existingCustomerCountDC", dashboard, template, 0, 6);
        addDatacard("existingCustomerCount", dashboard, template, 0, 6);
        removeDatacardFromDashboard("revenueChurnByQuarterDC", dashboard, template, 4, 2);
        addDatacard("revenueChurnByQuarter", dashboard, template, 4, 2);

        removeDatacardFromDashboard("leadCountBySourceDC", dashboard, template, 8, 0);
        addDatacard("leadCountBySource", dashboard, template, 8, 0);

        removeDatacardFromDashboard("mqlCountInQuarterDC", dashboard, template, 0, 2);
        addDatacard("mqlCountInQuarter", dashboard, template, 0, 2);

        removeDatacardFromDashboard("sqlLeadCountByOwnerDC", dashboard, template, 8, 2);
        addDatacard("sqlLeadCountByOwner", dashboard, template, 8, 2);

        removeDatacardFromDashboard("userGrowthDC", dashboard, template, 8, 6);
        addDatacard("userGrowth", dashboard, template, 8, 6);

    }

    @ChangeSet(order = "002", id = "updateSalesDashboardDatacardRevert", author = "rohit", runAlways = true)
    public void updateSalesDashboardDatacardRevert(MongoTemplate template){

        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");
        Document dashboard = dashboardCollection.find(new Document("name", "sales")).first();
        removeDatacardFromDashboard("quarterlyClosedPipelineRevenueDC", dashboard, template, 8, 0);
        addDatacard("quarterlyClosedPipelineRevenue", dashboard, template, 0, 2);
        removeDatacardFromDashboard("annualRecurringRevenueDC", dashboard, template, 0, 0);
        addDatacard("annualRecurringRevenue", dashboard, template, 0, 0);
        removeDatacardFromDashboard("nextFewQuaterOpenPipelinesDC", dashboard, template, 0, 0);
        addDatacard("nextFewQuaterOpenPipelines", dashboard, template, 8, 4);
        removeDatacardFromDashboard("upcomingRenewalDatesDC", dashboard, template, 8, 6);
        addDatacard("upcomingRenewalDates", dashboard, template, 8, 6);
        removeDatacardFromDashboard("quarterlyClosedPipelineRevenueByTypeDC", dashboard, template, 4, 2);
        addDatacard("quarterlyClosedPipelineRevenueByType", dashboard, template, 4, 2);
        removeDatacardFromDashboard("avgRevenueForAllAccountsDC", dashboard, template, 8, 2);
        addDatacard("avgRevenueForAllAccounts", dashboard, template, 8, 2);
        removeDatacardFromDashboard("existingCustomerCountDC", dashboard, template, 0, 4);
        addDatacard("existingCustomerCount", dashboard, template, 0, 4);
        removeDatacardFromDashboard("top10CustomersByRevenueDC", dashboard, template, 0, 6);
        addDatacard("top10CustomersByRevenue", dashboard, template, 0, 6);
        removeDatacardFromDashboard("openTicketsAccountforOpenPipelineDC", dashboard, template, 4, 6);
        addDatacard("openTicketsAccountforOpenPipeline", dashboard, template, 4, 6);
    }

    @ChangeSet(order = "003", id = "updateCEODashboardDatacardRevert", author = "rohit", runAlways = true)
    public void updateCEODashboardDatacardRevert(MongoTemplate template){

        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");
        Document dashboard = dashboardCollection.find(new Document("name", "executive")).first();
        removeDatacardFromDashboard("quarterlyClosedPipelineRevenueDC", dashboard, template, 4, 2);
        addDatacard("quarterlyClosedPipelineRevenue", dashboard, template, 0, 0);

        removeDatacardFromDashboard("annualRecurringRevenueDC", dashboard, template, 0, 0);
        addDatacard("annualRecurringRevenue", dashboard, template, 8, 0);
        removeDatacardFromDashboard("nextFewQuaterOpenPipelinesDC", dashboard, template, 0, 0);
        addDatacard("nextFewQuaterOpenPipelines", dashboard, template, 8, 2);
        removeDatacardFromDashboard("upcomingRenewalDatesDC", dashboard, template, 8, 6);
        addDatacard("upcomingRenewalDates", dashboard, template, 8, 6);
        removeDatacardFromDashboard("quarterlyClosedPipelineRevenueByTypeDC", dashboard, template, 4, 0);
        addDatacard("quarterlyClosedPipelineRevenueByType", dashboard, template, 4, 0);
        removeDatacardFromDashboard("avgRevenueForAllAccountsDC", dashboard, template, 8, 2);
        addDatacard("avgRevenueForAllAccounts", dashboard, template, 4, 4);
        removeDatacardFromDashboard("existingCustomerCountDC", dashboard, template, 0, 2);
        addDatacard("existingCustomerCount", dashboard, template, 0, 2);
        removeDatacardFromDashboard("top10CustomersByRevenueDC", dashboard, template, 0, 4);
        addDatacard("top10CustomersByRevenue", dashboard, template, 0, 4);

        removeDatacardFromDashboard("openEscalatedTicketCountDC", dashboard, template, 8, 4);
        addDatacard("openEscalatedTicketCount", dashboard, template, 8, 4);

        removeDatacardFromDashboard("openTicketsByPriorityDC", dashboard, template, 4, 6);
        addDatacard("openTicketsByPriority", dashboard, template, 4, 6);
    }

    @ChangeSet(order = "004", id = "updateSuccessDashboardDatacardRevert", author = "rohit", runAlways = true)
    public void updateSuccessDashboardDatacardRevert(MongoTemplate template){

        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");
        Document dashboard = dashboardCollection.find(new Document("name", "success")).first();
        removeDatacardFromDashboard("annualRecurringRevenueDC", dashboard, template, 0, 0);
        addDatacard("annualRecurringRevenue", dashboard, template, 0, 6);
        removeDatacardFromDashboard("nextFewQuaterOpenPipelinesDC", dashboard, template, 0, 0);
        addDatacard("nextFewQuaterOpenPipelines", dashboard, template, 8, 6);
        removeDatacardFromDashboard("upcomingRenewalDatesDC", dashboard, template, 4, 4);
        addDatacard("upcomingRenewalDates", dashboard, template, 4, 4);

        removeDatacardFromDashboard("openRenewalsDC", dashboard, template, 0, 4);
        addDatacard("openRenewals", dashboard, template, 0, 4);
        removeDatacardFromDashboard("existingCustomerCountDC", dashboard, template, 4, 6);
        addDatacard("existingCustomerCount", dashboard, template, 4, 6);

        removeDatacardFromDashboard("openTicketsAccountforOpenPipelineDC", dashboard, template, 0, 0);
        addDatacard("openTicketsAccountforOpenPipeline", dashboard, template, 0, 0);

        removeDatacardFromDashboard("openEscalatedTicketCountDC", dashboard, template, 4, 0);
        addDatacard("openEscalatedTicketCount", dashboard, template, 4, 0);

        removeDatacardFromDashboard("openTicketsByPriorityDC", dashboard, template, 0, 2);
        addDatacard("openTicketsByPriority", dashboard, template, 0, 2);

        removeDatacardFromDashboard("trendOfIssuesResolvedIn24hoursDC", dashboard, template, 4, 2);
        addDatacard("trendOfIssuesResolvedIn24hours", dashboard, template, 4, 2);

        removeDatacardFromDashboard("trendOfIssuesResolvedIn7DaysDC", dashboard, template, 8, 2);
        addDatacard("trendOfIssuesResolvedIn7Days", dashboard, template, 8, 2);
    }

    @ChangeSet(order = "005", id = "removeDatacardFromCollectionV2", author = "rohit", runAlways = true)
    public void removeDatacardFromCollectionV2(MongoTemplate template){
        removeDatacard("quarterlyClosedPipelineRevenue", template);
    }

    private void addDatacard(String datacardName, Document dashboard, MongoTemplate template, int x, int y){
        ObjectId dashboardId = dashboard.getObjectId("_id");
        MongoCollection<Document> datacardCollection = template.getCollection("datacard");
        Document datacard = datacardCollection.find(new Document("name", datacardName)).first();
        ObjectId datacardId = datacard.getObjectId("_id");

        List<String> existingDatacardIds = dashboard.get("dataCardIds", List.class);
        if(!doesExists(datacardId.toHexString(), existingDatacardIds)) {
            DashboardLayout layout = new DashboardLayout().setMinH(2).setMaxH(0)
                    .setWidth(4).setHeight(2).setX(x).setY(y).setResizable(false);
            Document dashboardLayout = new Document("minH", layout.getMinH()).append("maxH", layout.getMaxH())
                    .append("width", layout.getWidth()).append("height", layout.getHeight())
                    .append("x", layout.getX()).append("y", layout.getY()).append("resizable", layout.isResizable());
            var dcSetting = new Document("datacardId", datacardId.toHexString()).append("layout", dashboardLayout);
            addDatacardToDashboard(dashboardId, datacardId, dcSetting, template);
        }
    }

    private void removeDatacardFromDashboard(String datacardName, Document dashboard, MongoTemplate template, int x, int y){
        ObjectId dashboardId = dashboard.getObjectId("_id");
        MongoCollection<Document> datacardCollection = template.getCollection("datacard");
        Document datacard = datacardCollection.find(new Document("name", datacardName)).first();

        List<String> existingDatacardIds = dashboard.get("dataCardIds", List.class);
        if (null != datacard){
            ObjectId datacardId = datacard.getObjectId("_id");
            if(doesExists(datacardId.toHexString(), existingDatacardIds)) {
                log.info("Removing datacard {} from dashboard id {}", datacardId, dashboardId);
                removeDatacardFromDashboard(dashboardId, datacardId, template);
                DashboardLayout layout = new DashboardLayout().setMinH(2).setMaxH(0)
                        .setWidth(4).setHeight(2).setX(x).setY(y).setResizable(false);
                removeDatacardLayout(dashboardId, datacardId, layout, template);
            }
        }
    }

    private void addDatacardToDashboard(ObjectId dashboardId, ObjectId datacardId, Document dcSetting, MongoTemplate template){
        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");

        dashboardCollection.updateOne(
                Filters.eq("_id", dashboardId),
                Updates.combine(
                        Updates.push("dataCardIds", datacardId.toHexString()),
                        Updates.push("dataCardSettings", dcSetting)
                )
        );
    }

    private void removeDatacardFromDashboard(ObjectId dashboardId, ObjectId datacardId, MongoTemplate template){
        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");

        dashboardCollection.updateOne(
                Filters.eq("_id", dashboardId),
                Updates.pull("dataCardIds", datacardId.toHexString())
        );
    }

    private void removeDatacard(String datacardName, MongoTemplate template){
        MongoCollection<Document> datacardCollection = template.getCollection("datacard");
        Document datacard = datacardCollection.find(new Document("name", datacardName)).first();
        log.info("datacard is {}", datacard);
        if (null != datacard){
            log.info("Removing datacard {}", datacard.getObjectId("_id"));
            datacardCollection.deleteOne(datacard);
        }
    }

    private void removeDatacardLayout(ObjectId dashboardId, ObjectId datacardId, DashboardLayout layout, MongoTemplate template){
        MongoCollection<Document> dataCardAuthorConfigCollection = template.getCollection("dataCardAuthorConfig");
        log.info("Removing layout of datacard {} from dashboard id {}", datacardId, dashboardId);
        Document datacardLayout = dataCardAuthorConfigCollection.find(Filters.and(new Document("datacardId", datacardId.toHexString()),
                new Document("dashboardId", dashboardId.toHexString()) )).first();
        log.info("datacardlayout is {}", datacardLayout);
        try{
            dataCardAuthorConfigCollection.deleteOne(datacardLayout);
        }catch (Exception e){
            log.error("datacardlayout in dataCardAuthorConfig does not exists, we need to deprecate this. ");
        }
    }


    private boolean doesExists(String datacardId, List<String> existingDatacardIds){
        if(existingDatacardIds == null || existingDatacardIds.isEmpty()) {
            return false;
        }
        return existingDatacardIds.contains(datacardId);
    }
}
