package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.syncari.core.model.insights.DashboardLayout;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

@Slf4j
public class UpdatedDatacardInDashboardSeededV2 {
    @ChangeSet(order = "001", id = "updateMarketingDashboardDatacardV2", author = "rohit", runAlways = true)
    public void updateMarketingDashboardDatacardV2(MongoTemplate template){

        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");
        Document dashboard = dashboardCollection.find(new Document("name", "marketing")).first();
        removeDatacardFromDashboard("quarterlyClosedPipelineRevenue", dashboard, template, 0, 0);
        addDatacard("quarterlyClosedPipelineRevenueDC", dashboard, template, 8, 4);
        removeDatacardFromDashboard("annualRecurringRevenue", dashboard, template, 0, 4);
        addDatacard("annualRecurringRevenueDC", dashboard, template, 0, 4);
        removeDatacardFromDashboard("quarterlyClosedPipelineRevenueByType", dashboard, template, 4, 4);
        addDatacard("quarterlyClosedPipelineRevenueByTypeDC", dashboard, template, 4, 4);
        removeDatacardFromDashboard("avgRevenueForAllAccounts", dashboard, template, 4, 6);
        addDatacard("avgRevenueForAllAccountsDC", dashboard, template, 4, 6);
        removeDatacardFromDashboard("existingCustomerCount", dashboard, template, 0, 6);
        addDatacard("existingCustomerCountDC", dashboard, template, 0, 6);
        removeDatacardFromDashboard("revenueChurnByQuarter", dashboard, template, 4, 2);
        addDatacard("revenueChurnByQuarterDC", dashboard, template, 4, 2);
        removeDatacardFromDashboard("nextFewQuaterOpenPipelines", dashboard, template, 4, 0);
        addDatacard("nextFewQuaterOpenPipelinesDC", dashboard, template, 4, 0); //pipleineByCloseDate
    }

    @ChangeSet(order = "002", id = "updateSalesDashboardDatacardV2", author = "rohit", runAlways = true)
    public void updateSalesDashboardDatacardV2(MongoTemplate template){

        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");
        Document dashboard = dashboardCollection.find(new Document("name", "sales")).first();
        removeDatacardFromDashboard("quarterlyClosedPipelineRevenue", dashboard, template, 8, 0);
        addDatacard("quarterlyClosedPipelineRevenueDC", dashboard, template, 0, 2);
        removeDatacardFromDashboard("annualRecurringRevenue", dashboard, template, 0, 0);
        addDatacard("annualRecurringRevenueDC", dashboard, template, 0, 0);
        removeDatacardFromDashboard("nextFewQuaterOpenPipelines", dashboard, template, 0, 0);
        addDatacard("nextFewQuaterOpenPipelinesDC", dashboard, template, 8, 4);
        removeDatacardFromDashboard("upcomingRenewalDates", dashboard, template, 8, 6);
        addDatacard("upcomingRenewalDatesDC", dashboard, template, 8, 6);
        removeDatacardFromDashboard("quarterlyClosedPipelineRevenueByType", dashboard, template, 4, 2);
        addDatacard("quarterlyClosedPipelineRevenueByTypeDC", dashboard, template, 4, 2);
        removeDatacardFromDashboard("avgRevenueForAllAccounts", dashboard, template, 8, 2);
        addDatacard("avgRevenueForAllAccountsDC", dashboard, template, 8, 2);
        removeDatacardFromDashboard("existingCustomerCount", dashboard, template, 0, 4);
        addDatacard("existingCustomerCountDC", dashboard, template, 0, 4);
        removeDatacardFromDashboard("top10CustomersByRevenue", dashboard, template, 0, 6);
        addDatacard("top10CustomersByRevenueDC", dashboard, template, 0, 6);
        removeDatacardFromDashboard("openTicketsAccountforOpenPipeline", dashboard, template, 4, 6);
        addDatacard("openTicketsAccountforOpenPipelineDC", dashboard, template, 4, 6);
        removeDatacardFromDashboard("salesFunnel", dashboard, template, 4, 4);
        addDatacard("salesFunnelDC", dashboard, template, 4, 4);
        removeDatacardFromDashboard("allOpenPipelineByType", dashboard, template, 4, 0);
        addDatacard("allOpenPipelineByTypeDC", dashboard, template, 4, 0);

    }

    @ChangeSet(order = "003", id = "updateCEODashboardDatacardV2", author = "rohit", runAlways = true)
    public void updateCEODashboardDatacardV2(MongoTemplate template){

        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");
        Document dashboard = dashboardCollection.find(new Document("name", "executive")).first();
        removeDatacardFromDashboard("quarterlyClosedPipelineRevenue", dashboard, template, 4, 2);
        addDatacard("quarterlyClosedPipelineRevenueDC", dashboard, template, 0, 0);

        removeDatacardFromDashboard("annualRecurringRevenue", dashboard, template, 0, 0);
        addDatacard("annualRecurringRevenueDC", dashboard, template, 8, 0);
        removeDatacardFromDashboard("nextFewQuaterOpenPipelines", dashboard, template, 0, 0);
        addDatacard("nextFewQuaterOpenPipelinesDC", dashboard, template, 8, 2);
        removeDatacardFromDashboard("upcomingRenewalDates", dashboard, template, 8, 6);
        addDatacard("upcomingRenewalDatesDC", dashboard, template, 8, 6);
        removeDatacardFromDashboard("quarterlyClosedPipelineRevenueByType", dashboard, template, 4, 0);
        addDatacard("quarterlyClosedPipelineRevenueByTypeDC", dashboard, template, 4, 0);
        removeDatacardFromDashboard("avgRevenueForAllAccounts", dashboard, template, 8, 2);
        addDatacard("avgRevenueForAllAccountsDC", dashboard, template, 4, 4);
        removeDatacardFromDashboard("existingCustomerCount", dashboard, template, 0, 2);
        addDatacard("existingCustomerCountDC", dashboard, template, 0, 2);
        removeDatacardFromDashboard("top10CustomersByRevenue", dashboard, template, 0, 4);
        addDatacard("top10CustomersByRevenueDC", dashboard, template, 0, 4);
        removeDatacardFromDashboard("salesFunnel", dashboard, template, 0, 6);
        addDatacard("salesFunnelDC", dashboard, template, 0, 6);

    }

    @ChangeSet(order = "004", id = "updateSuccessDashboardDatacardV2", author = "rohit", runAlways = true)
    public void updateSuccessDashboardDatacardV2(MongoTemplate template){

        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");
        Document dashboard = dashboardCollection.find(new Document("name", "success")).first();
        removeDatacardFromDashboard("annualRecurringRevenue", dashboard, template, 0, 0);
        addDatacard("annualRecurringRevenueDC", dashboard, template, 0, 6);
        removeDatacardFromDashboard("nextFewQuaterOpenPipelines", dashboard, template, 0, 0);
        addDatacard("nextFewQuaterOpenPipelinesDC", dashboard, template, 8, 6);
        removeDatacardFromDashboard("upcomingRenewalDates", dashboard, template, 4, 4);
        addDatacard("upcomingRenewalDatesDC", dashboard, template, 4, 4);

        removeDatacardFromDashboard("openRenewals", dashboard, template, 0, 4);
        addDatacard("openRenewalsDC", dashboard, template, 0, 4);
        removeDatacardFromDashboard("existingCustomerCount", dashboard, template, 4, 6);
        addDatacard("existingCustomerCountDC", dashboard, template, 4, 6);

        removeDatacardFromDashboard("openTicketsAccountforOpenPipeline", dashboard, template, 0, 0);
        addDatacard("openTicketsAccountforOpenPipelineDC", dashboard, template, 0, 0);
        removeDatacardFromDashboard("openRenewalLogoCount", dashboard, template, 8, 4);
        addDatacard("openRenewalLogoCountDC", dashboard, template, 8, 4);
        removeDatacardFromDashboard("openTicketsCountByAccount", dashboard, template, 8, 0);
        addDatacard("openTicketsCountByAccountDC", dashboard, template, 8, 0);


    }
    private void addDatacard(String datacardName, Document dashboard, MongoTemplate template, int x, int y){
        try{
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
        }catch (Exception e){
            log.error("Add datacard failed for datacard {} in dashboard {} , exception is {}", datacardName, dashboard.get("name"), ExceptionUtils.getStackTrace(e));
        }

    }

    private void removeDatacardFromDashboard(String datacardName, Document dashboard, MongoTemplate template, int x, int y){
        try{
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
        }catch (Exception e){
            log.error("Remove datacard failed for datacard {} in dashboard {} , exception is {}", datacardName, dashboard.get("name"), ExceptionUtils.getStackTrace(e));
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
