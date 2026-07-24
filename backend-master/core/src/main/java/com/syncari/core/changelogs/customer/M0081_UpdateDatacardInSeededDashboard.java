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
@ChangeLog(order = "0081")
public class M0081_UpdateDatacardInSeededDashboard {

    @ChangeSet(order = "001", id = "updateMarketingDashboardDatacard", author = "rohit")
    public void updateMarketingDashboardDatacard(MongoTemplate template){

        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");
        Document dashboard = dashboardCollection.find(new Document("name", "marketing")).first();
        addDatacard("allOpenPipelineNewCount", dashboard, template, 0, 0);

    }

    @ChangeSet(order = "002", id = "updateSalesDashboardDatacard", author = "rohit")
    public void updateSalesDashboardDatacard(MongoTemplate template){

        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");
        Document dashboard = dashboardCollection.find(new Document("name", "sales")).first();
        addDatacard("allOpenPipelineNewCount", dashboard, template, 8, 0);


    }

    @ChangeSet(order = "003", id = "updateCEODashboardDatacard", author = "rohit")
    public void updateCEODashboardDatacard(MongoTemplate template){

        MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");
        Document dashboard = dashboardCollection.find(new Document("name", "executive")).first();
        addDatacard("allOpenPipelineNewCount", dashboard, template, 4, 2);
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
            log.error("Exception occurred while trying to add datacard, exception is {}", ExceptionUtils.getStackTrace(e));
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
