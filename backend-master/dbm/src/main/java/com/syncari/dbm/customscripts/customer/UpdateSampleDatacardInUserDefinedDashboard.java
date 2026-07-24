package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.insights.DashboardLayout;
import com.syncari.core.model.insights.DataCardSetting;
import com.syncari.core.model.insights.Datacard;
import com.syncari.core.model.insights.InsightsDashboard;
import com.syncari.core.repositories.customer.DatacardRepo;
import com.syncari.core.repositories.customer.InsightsDashboardRepo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class UpdateSampleDatacardInUserDefinedDashboard {

    @ChangeSet(order = "001", id = "updateSampleDatacards", author = "rohit", runAlways = true)
    public void updateSampleDatacards(MongoTemplate template){
        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        InsightsDashboardRepo repo = MigrationContext.getInsightDashboardRepo();
        DatacardRepo datacardRepo = MigrationContext.getDatacardRepo();
        Map<String, String> map1 = Map.of("quarterlyClosedPipelineRevenueByType","quarterlyClosedPipelineRevenueByTypeDC"
                ,"annualRecurringRevenue","annualRecurringRevenueDC","leadCountBySource","leadsBySourceDC","nextFewQuaterOpenPipelines","nextFewQuaterOpenPipelinesDC"
        ,"existingCustomerCount","existingCustomerCountDC","allOpenPipelineByType","allOpenPipelineByTypeDC");

        Map<String, String> map2 = Map.of("sqlLeadCountByOwner","sqlCountByOwnerDC"
                ,"salesFunnel","salesFunnelDC","top10CustomersByRevenue","top10CustomersByRevenueDC","mqlCountInQuarter","mqlCountInQuarterDC","openEscalatedTicketCount","openEscalatedTicketCountDC"
                ,"openTicketsCountByAccount","openTicketsCountByAccountDC","allOpenPipelineTotal","","openTicketsByPriority","openTicketsByPriorityDC",
                "trendOfIssuesResolvedIn24hours","trendOfIssuesResolvedIn24hoursDC","allOpenPipelineCount","allOpenPipelineNewCount");

        Map<String, String> map3 = Map.of("trendOfIssuesResolvedIn7Days","trendOfIssuesResolvedIn7DaysDC","openTicketsInAccountsWithOpenPipeline","openTicketsAccountforOpenPipelineDC"
        ,"openRenewalLogoCount","openRenewalLogoCountDC","openRenewals","openRenewalsDC","openTicketsAccountforOpenPipeline","openTicketsAccountforOpenPipelineDC"
                ,"upcomingRenewalDates","upcomingRenewalDatesDC","revenueChurnByQuarter","revenueChurnByQuarterDC","userGrowth","userGrowthDC","avgRevenueForAllAccounts","avgRevenueForAllAccountsDC"
                ,"quarterlyClosedPipelineRevenue","quarterlyClosedPipelineRevenueDC");
        Map<String, String> newDatacardsMap = new HashMap<>();
        newDatacardsMap.putAll(map1);
        newDatacardsMap.putAll(map2);
        newDatacardsMap.putAll(map3);
        List<String> sampleDashboardNames = List.of("marketing", "sales", "success", "executive");

        List<InsightsDashboard> allActiveDashboards = repo.findAllDashboards();
        log.info("Number of all dashboards {}",allActiveDashboards.size());
        List<InsightsDashboard> nonSeededAllActive = allActiveDashboards.stream().filter(x -> !sampleDashboardNames.contains(x.getName())).collect(Collectors.toList());

        log.info("Number of all nonSeededAllActive {}",nonSeededAllActive.size());
        nonSeededAllActive.forEach(d -> {
            log.info("dashboard seeded is {} for name {}",d.isSeeded(), d.getName());

            List<String> datacardIds = d.getDataCardIds();
            List<DataCardSetting> settings = d.getDataCardSettings();
            datacardIds.forEach(dcId -> {
                Optional<Datacard> card = datacardRepo.findById(dcId);
                card.ifPresent(c -> {
                    List<DataCardSetting> cardSettings = settings.stream().filter(s -> s.getDatacardId().equalsIgnoreCase(c.getId())).collect(Collectors.toList());
                    if (c.isSeeded() && newDatacardsMap.containsKey(c.getName())){
                        log.info("Seeded Datacard name is {}, id is {} and dashboard name is {}",c.getName(), c.getId(), d.getName());
                        int x= 0;
                        int y = 0;
                        if (CollectionUtils.isNotEmpty(cardSettings)){
                            DataCardSetting cardSetting = cardSettings.stream().findFirst().get();
                            x = cardSetting.getLayout().getX();
                            y = cardSetting.getLayout().getY();
                        }
                        log.info("Card Id is {}, card setting is {} , x {} and y {}",dcId,cardSettings, x, y);
                        if (!dryRun){
                            removeDatacardFromDashboard(c.getName(), d, template, x, y);
                            addDatacard(newDatacardsMap.get(c.getName()), d, template, x, y);
                        }else{
                            log.info("Not removing and adding as running in dry run mode for card {}, dashboard {} and card settings {}", dcId, d.getDisplayName(), cardSettings);
                        }
                    }
                });
            });
        });
    }


    private void addDatacard(String datacardName, InsightsDashboard dashboard, MongoTemplate template, int x, int y){
        try{
            String dashboardId = dashboard.getId();
            MongoCollection<Document> datacardCollection = template.getCollection("datacard");
            Document datacard = datacardCollection.find(new Document("name", datacardName)).first();
            ObjectId datacardId = datacard.getObjectId("_id");

            List<String> existingDatacardIds = dashboard.getDataCardIds();
            if(!doesExists(datacardId.toHexString(), existingDatacardIds)) {
                DashboardLayout layout = new DashboardLayout().setMinH(2).setMaxH(0)
                        .setWidth(4).setHeight(2).setX(x).setY(y).setResizable(false);
                Document dashboardLayout = new Document("minH", layout.getMinH()).append("maxH", layout.getMaxH())
                        .append("width", layout.getWidth()).append("height", layout.getHeight())
                        .append("x", layout.getX()).append("y", layout.getY()).append("resizable", layout.isResizable());
                var dcSetting = new Document("datacardId", datacardId.toHexString()).append("layout", dashboardLayout);
                addDatacardToDashboard(new ObjectId(dashboardId), datacardId, dcSetting, template);
            }
        }catch (Exception e){
            log.error("Add datacard failed for datacard {} in dashboard {} , exception is {}", datacardName, dashboard.getName(), ExceptionUtils.getStackTrace(e));
        }

    }

    private void removeDatacardFromDashboard(String datacardName, InsightsDashboard dashboard, MongoTemplate template, int x, int y){
        try{
            String dashboardId = dashboard.getId();
            MongoCollection<Document> datacardCollection = template.getCollection("datacard");
            Document datacard = datacardCollection.find(new Document("name", datacardName)).first();

            List<String> existingDatacardIds = dashboard.getDataCardIds();
            if (null != datacard){
                ObjectId datacardId = datacard.getObjectId("_id");
                if(doesExists(datacardId.toHexString(), existingDatacardIds)) {
                    log.info("Removing datacard {} from dashboard id {}", datacardId, dashboardId);
                    removeDatacardFromDashboard(new ObjectId(dashboardId), datacardId, template);
                    DashboardLayout layout = new DashboardLayout().setMinH(2).setMaxH(0)
                            .setWidth(4).setHeight(2).setX(x).setY(y).setResizable(false);
                    removeDatacardLayout(new ObjectId(dashboardId), datacardId, layout, template);
                }
            }
        }catch (Exception e){
            log.error("Remove datacard failed for datacard {} in dashboard {} , exception is {}", datacardName, dashboard.getName(), ExceptionUtils.getStackTrace(e));

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
