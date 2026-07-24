package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.service.DatacardService;
import com.syncari.core.service.DatasetService;
import com.syncari.core.service.InsightsDashboardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
@ChangeLog(order = "0084")
public class M0084_SeedInsightsComponentDependencies {

    @ChangeSet(order = "001", id = "seedDatasetDependencies", author = "abhinav")
    public void seedDatasetDependencies(MongoTemplate mongoTemplate) {
        DatasetService datasetService = MigrationContext.getDatasetService();
        datasetService.getAllUserCreatedDatasets().forEach(d -> {
            log.info("Seeding dependencies for dataset {} with id {}", d.getDisplayName(), d.getId());
            datasetService.updateDatasetDependencies(d);
        });
    }

    @ChangeSet(order = "002", id = "seedDatacardDependencies", author = "abhinav")
    public void seedDatacardDependencies(MongoTemplate mongoTemplate) {
        DatacardService datacardService = MigrationContext.getDatacardService();
        datacardService.getAllDatacards().forEach(d -> {
            log.info("Seeding dependencies for datacard {} with id {}", d.getDisplayName(), d.getId());
            datacardService.updateDatacardDependencies(d);
        });
    }

    @ChangeSet(order = "002", id = "seedDashboardDependencies", author = "abhinav")
    public void seedDashboardDependencies(MongoTemplate mongoTemplate) {
        InsightsDashboardService dashboardService = MigrationContext.getDashboardService();
        dashboardService.getAllDashboards().forEach(d -> {
            log.info("Seeding dependencies for dashboard {} with id {}", d.getDisplayName(), d.getId());
            dashboardService.updateDashboardDependencies(d);
        });
    }
}
