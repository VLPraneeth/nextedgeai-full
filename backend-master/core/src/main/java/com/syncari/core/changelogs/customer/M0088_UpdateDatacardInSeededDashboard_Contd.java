package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
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
@ChangeLog(order = "0088")
public class M0088_UpdateDatacardInSeededDashboard_Contd {

    @ChangeSet(order = "001", id = "updateMarketingDashboardDatacardV2", author = "rohit")
    public void updateMarketingDashboardDatacardV2(MongoTemplate template){

        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");
        Document dashboard = dashboardCollection.find(new Document("name", "marketing")).first();

        removeDatacardFromDashboard("leadCountBySource", dashboard, template, 8, 0);
        addDatacard("leadsBySourceDC", dashboard, template, 8, 0);

        removeDatacardFromDashboard("mqlCountInQuarter", dashboard, template, 0, 2);
        addDatacard("mqlCountInQuarterDC", dashboard, template, 0, 2);

        removeDatacardFromDashboard("sqlLeadCountByOwner", dashboard, template, 8, 2);
        addDatacard("sqlCountByOwnerDC", dashboard, template, 8, 2);

        removeDatacardFromDashboard("userGrowth", dashboard, template, 8, 6);
        addDatacard("userGrowthDC", dashboard, template, 8, 6);

    }

    @ChangeSet(order = "002", id = "updateSalesDashboardDatacardV2", author = "rohit")
    public void updateSalesDashboardDatacardV2(MongoTemplate template){

        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");
        Document dashboard = dashboardCollection.find(new Document("name", "sales")).first();
    }

    @ChangeSet(order = "003", id = "updateCEODashboardDatacardV2", author = "rohit")
    public void updateCEODashboardDatacardV2(MongoTemplate template){

        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");
        Document dashboard = dashboardCollection.find(new Document("name", "executive")).first();

        removeDatacardFromDashboard("openEscalatedTicketCount", dashboard, template, 8, 4);
        addDatacard("openEscalatedTicketCountDC", dashboard, template, 8, 4);

        removeDatacardFromDashboard("openTicketsByPriority", dashboard, template, 4, 6);
        addDatacard("openTicketsByPriorityDC", dashboard, template, 4, 6);
    }

    @ChangeSet(order = "004", id = "updateSuccessDashboardDatacardV2", author = "rohit")
    public void updateSuccessDashboardDatacardV2(MongoTemplate template){

        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");
        Document dashboard = dashboardCollection.find(new Document("name", "success")).first();

        removeDatacardFromDashboard("openEscalatedTicketCount", dashboard, template, 4, 0);
        addDatacard("openEscalatedTicketCountDC", dashboard, template, 4, 0);

        removeDatacardFromDashboard("openTicketsByPriority", dashboard, template, 0, 2);
        addDatacard("openTicketsByPriorityDC", dashboard, template, 0, 2);

        removeDatacardFromDashboard("trendOfIssuesResolvedIn24hours", dashboard, template, 4, 2);
        addDatacard("trendOfIssuesResolvedIn24HoursDC", dashboard, template, 4, 2);

        removeDatacardFromDashboard("trendOfIssuesResolvedIn7Days", dashboard, template, 8, 2);
        addDatacard("trendOfIssuesResolvedIn7DaysDC", dashboard, template, 8, 2);

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
            log.error("Exception occured while adding datacard {}, exception {}", datacardName, ExceptionUtils.getStackTrace(e));
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
            log.error("Exception occured while removing datacard {}, exception {}", datacardName, ExceptionUtils.getStackTrace(e));
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
