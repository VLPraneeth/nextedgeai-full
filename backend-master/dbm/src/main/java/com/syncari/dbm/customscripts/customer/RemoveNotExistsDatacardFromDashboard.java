package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.Features;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.insights.DataCardSetting;
import com.syncari.core.model.insights.Datacard;
import com.syncari.core.model.insights.InsightsDashboard;
import com.syncari.core.repositories.customer.DatacardRepo;
import com.syncari.core.repositories.customer.InsightsDashboardRepo;
import com.syncari.core.service.FeatureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class RemoveNotExistsDatacardFromDashboard {

    @ChangeSet(order = "001", id = "removeDatacardFromDashboard", author = "rohit", runAlways = true)
    public void removeDatacardFromDashboard(MongoTemplate template){
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        FeatureService featureService = MigrationContext.getFeatureService();
        if(featureService.isEnabled(Features.Datastore) && featureService.isEnabled(Features.Insights)) {
            removeNotExistsDatacards("marketing",dryRunMode);
            removeNotExistsDatacards("executive",dryRunMode);
            removeNotExistsDatacards("success",dryRunMode);
            removeNotExistsDatacards("sales",dryRunMode);
        }
    }

    private void removeNotExistsDatacards(String dashboardName, boolean dryRun){
        InsightsDashboardRepo repo = MigrationContext.getInsightDashboardRepo();
        DatacardRepo datacardRepo = MigrationContext.getDatacardRepo();
        Optional<InsightsDashboard> dashboard = repo.findByName(dashboardName);
        List<String> datacardIdsTobeRemoved = new ArrayList<>();
        dashboard.ifPresentOrElse(d -> {
            List<String> datacardIds = d.getDataCardIds();
            List<DataCardSetting> dataCardSettings = d.getDataCardSettings();
            datacardIds.forEach(id -> {
                Optional<Datacard> dc = datacardRepo.findById(id);
                dc.ifPresentOrElse(dcp -> {
                    log.info("Datacard {} is present", dcp.getName());
                },()-> {
                    log.info("Datacard id {} is not present", id);
                    if (!dryRun){
                        datacardIdsTobeRemoved.add(id);
                    }
                });
            });
            List<DataCardSetting> settings = dataCardSettings.stream().filter(dc -> !datacardIdsTobeRemoved.contains(dc.getDatacardId())).collect(Collectors.toList());
            datacardIds.removeAll(datacardIdsTobeRemoved);
            d.setDataCardSettings(settings);
            d.setDataCardIds(datacardIds);
            repo.save(d);
        },() -> log.info("Dashboard {} does exists", dashboardName));
    }
}

