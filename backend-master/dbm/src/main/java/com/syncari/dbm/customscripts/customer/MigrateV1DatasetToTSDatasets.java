package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.insights.InsightsProviderIntegrator;
import com.syncari.core.model.User;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.repositories.customer.DatasetRepo;
import com.syncari.core.service.DatasetService;
import com.syncari.core.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class MigrateV1DatasetToTSDatasets {
    @ChangeSet(order = "001", id = "migrateDatasets", author = "rohit", runAlways = true)
    public void migrateDatasets(MongoTemplate template) {
        DatasetRepo repo = MigrationContext.getDatasetRepo();
        DatasetService service = MigrationContext.getDatasetService();
        DatasetRepo datasetRepo = MigrationContext.getDatasetRepo();
        List<Dataset> datasets = repo.findAllApprovedDatasetsWithVersion();
        UserService userService = MigrationContext.getUserService();
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        Optional<User> userToSetContext = userService.findActiveUserByEmail("systemuser@syncari.com");
        userToSetContext.ifPresentOrElse(usr -> SyncariContext.setUser(usr), () -> {
            SyncariContext.setUser(userService.findActiveUserByEmail("system_syncari_admin@syncari.com").get());
        });
        List<Dataset> v1Datasets = datasets.stream().filter(d -> d.getVersion().equalsIgnoreCase("v1") && !d.isSeeded()).collect(Collectors.toList());
        try{
            v1Datasets.forEach(ds -> {
                if (StringUtils.isEmpty(ds.getInsightsProviderId())){
                    log.info("Dataset getting created in TS is {} with display name {}, ", ds.getId(), ds.getDisplayName());
                    if (!dryRunMode){
                        service.createOrUpdateDatasetInInsightsProvider(ds, true);
                    }
                }else{
                    log.info("Dataset getting updated in TS is {} with display name {}, type {} and insights provider id {} ",
                            ds.getId(), ds.getDisplayName(), ds.getDatasetType(),ds.getInsightsProviderId());
                    if (!dryRunMode){
                        service.createOrUpdateDatasetInInsightsProvider(ds, false);
                    }
                }
                datasetRepo.save(ds);
            });
        }catch (Exception e){
            log.error("Exception occurred while migrating data from old to new ",e );
        }

        List<Dataset> v2Datasets = datasets.stream().filter(d -> d.getVersion().equalsIgnoreCase("v2") && !d.isSeeded()).collect(Collectors.toList());
        try{
            v2Datasets.forEach(ds -> {
                if (StringUtils.isEmpty(ds.getInsightsProviderId())){
                    log.info("Dataset getting created in TS is {} with display name {}, ", ds.getId(), ds.getDisplayName());
                    if (!dryRunMode){
                        service.createOrUpdateDatasetInInsightsProvider(ds, true);
                    }
                }else{
                    log.info("Dataset getting updated in TS is {} with display name {}, type {} and insights provider id {} ",
                            ds.getId(), ds.getDisplayName(), ds.getDatasetType(),ds.getInsightsProviderId());
                    if (!dryRunMode){
                        service.createOrUpdateDatasetInInsightsProvider(ds, false);
                    }
                }
                datasetRepo.save(ds);
            });
        }catch (Exception e){
            log.error("Exception occurred while migrating data from old to new ",e );
        }
    }
}
