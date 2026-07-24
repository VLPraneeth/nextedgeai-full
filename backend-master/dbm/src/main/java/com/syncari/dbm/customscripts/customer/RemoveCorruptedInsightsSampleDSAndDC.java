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
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.repositories.customer.DatacardRepo;
import com.syncari.core.repositories.customer.DatasetRepo;
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

@Slf4j
public class RemoveCorruptedInsightsSampleDSAndDC {

    List<String> sampleDashboardNames = List.of("marketing", "sales", "executive");
    List<String> removeDatacards = List.of("allOpenPipelineNewCount2");


    @ChangeSet(order = "001", id = "removeCorruptedSampleDSAndDC", author = "rohit", runAlways = true)
    public void removeCorruptedSampleDSAndDC(MongoTemplate template){
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        String datasetToBeRemoved = System.getProperty("dataset");
        String datacardToBeRemoved = System.getProperty("datacard");
        boolean errorOccured = false;
        if (null != datasetToBeRemoved){
            DatasetRepo datasetRepo = MigrationContext.getDatasetRepo();
            try{
                List<Dataset> datasetToDelete = datasetRepo.findByName(datasetToBeRemoved);
            }catch (Exception e){
                errorOccured = true;
                log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
                if (!dryRunMode){
                    removeDataset(datasetToBeRemoved, template);
                    log.info("Dataset {} removed after exception", datasetToBeRemoved);
                }else{
                    log.info("Dataset {} will be removed after exception", datasetToBeRemoved);
                }
            }catch (Error error){
                errorOccured = true;
                log.error("Error occurred {}", ExceptionUtils.getStackTrace(error));
                if (!dryRunMode){
                    removeDataset(datasetToBeRemoved, template);
                    log.info("Dataset {} removed after error", datasetToBeRemoved);
                }else{
                    log.info("Dataset {} will be removed after error", datasetToBeRemoved);
                }
            }
        }else{
            log.info("Dataset name not passed {}", datasetToBeRemoved);
        }

        if ((null != datacardToBeRemoved) && (errorOccured)){
            DatacardRepo datacardRepo = MigrationContext.getDatacardRepo();
            try{
                Optional<Datacard> datacard = datacardRepo.findByName(datacardToBeRemoved);
                datacard.ifPresentOrElse(dc -> {
                    if (!dryRunMode){
                        // datacard removed, remove it from associated dashboards
                        removeDatacardFromDashboard(datacardToBeRemoved, template, dryRunMode);
                        removeDatacard(datacardToBeRemoved, template);
                        log.info("Datacard {} removed ", datacardToBeRemoved);
                    }else{
                        log.info("Datacard {} will be removed ", datacardToBeRemoved);
                    }
                },() -> log.error("Datacard {} not found", datacardToBeRemoved));
            }catch (Exception e){
                log.error("Exception occurred {} for datacard", ExceptionUtils.getStackTrace(e));
                if (!dryRunMode){
                    // datacard removed, remove it from associated dashboards
                    removeDatacardFromDashboard(datacardToBeRemoved, template, dryRunMode);
                    removeDatacard(datacardToBeRemoved, template);
                    log.info("Datacard {} removed after exception", datacardToBeRemoved);
                }else{
                    log.info("Datacard {} will be removed after exception", datacardToBeRemoved);
                }
            }catch (Error error){
                log.error("Error occurred {} for datacard", ExceptionUtils.getStackTrace(error));
                if (!dryRunMode){
                    // datacard removed, remove it from associated dashboards
                    removeDatacardFromDashboard(datacardToBeRemoved, template, dryRunMode);
                    removeDatacard(datacardToBeRemoved, template);
                    log.info("Datacard {} removed after error", datacardToBeRemoved);
                }else{
                    log.info("Datacard {} will be removed after error", datacardToBeRemoved);
                }
            }
        }else{
            log.info("Datacard name not passed {}", datacardToBeRemoved);
        }
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

    private void removeDataset(String datasetName, MongoTemplate template){
        MongoCollection<Document> datasetCollection = template.getCollection("dataset");
        Document dataset = datasetCollection.find(new Document("name", datasetName)).first();
        log.info("dataset is {}", dataset);
        if (null != dataset){
            log.info("Removing dataset {}", dataset.getObjectId("_id"));
            datasetCollection.deleteOne(dataset);
        }
    }

    private void removeDatacardFromDashboard(String datacardName, MongoTemplate template, boolean dryRun){
        InsightsDashboardRepo repo = MigrationContext.getInsightDashboardRepo();
        DatacardRepo datacardRepo = MigrationContext.getDatacardRepo();

        List<InsightsDashboard> allActiveDashboards = repo.findAllDashboards();
        log.info("Number of all dashboards {}",allActiveDashboards.size());
        List<InsightsDashboard> nonSeededAllActive = allActiveDashboards.stream().filter(x -> sampleDashboardNames.contains(x.getName())).collect(Collectors.toList());

        nonSeededAllActive.forEach(d -> {
            log.info("dashboard seeded is {} for name {}",d.isSeeded(), d.getName());

            List<String> datacardIds = d.getDataCardIds();
            List<DataCardSetting> settings = d.getDataCardSettings();
            datacardIds.forEach(dcId -> {
                Optional<Datacard> card = datacardRepo.findById(dcId);
                card.ifPresent(c -> {
                    List<DataCardSetting> cardSettings = settings.stream().filter(s -> s.getDatacardId().equalsIgnoreCase(c.getId())).collect(Collectors.toList());
                    if (c.isSeeded() && removeDatacards.contains(c.getName()) && datacardName.equalsIgnoreCase(c.getName())){
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
                            removeDatacardFromDashboard(new ObjectId(d.getId()), new ObjectId(c.getId()), template, x, y);
                        }else{
                            log.info("Not removing and adding as running in dry run mode for card {}, dashboard {} and card settings {}", dcId, d.getDisplayName(), cardSettings);
                        }
                    }
                });
            });
        });
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

    private void removeDatacardFromDashboard(ObjectId dashboardId, ObjectId datacardId, MongoTemplate template, int x, int y){
        try{
            MongoCollection<Document> dashboardCollection = template.getCollection("insightsDashboard");

            dashboardCollection.updateOne(
                    Filters.eq("_id", dashboardId),
                    Updates.pull("dataCardIds", datacardId.toHexString())
            );
            DashboardLayout layout = new DashboardLayout().setMinH(2).setMaxH(0)
                    .setWidth(4).setHeight(2).setX(x).setY(y).setResizable(false);
            removeDatacardLayout(dashboardId, datacardId, layout, template);
        }catch (Exception e){
            log.error("Could not remove datacard from dashboard");
        }

    }
}
