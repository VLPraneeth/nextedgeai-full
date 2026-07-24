package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.Features;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.service.FeatureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class EnableFeature {

    @ChangeSet(order = "001", id = "enableFeature", author = "blesson", runAlways = true)
    public void enableFeature(MongoTemplate db) {
        FeatureService featureService = MigrationContext.getFeatureService();
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        var featureParam = System.getProperty("feature");
        boolean enable = Boolean.parseBoolean(System.getProperty("enable"));
        // bydefault this property is false.
        boolean isdelete = Boolean.parseBoolean(System.getProperty("isdelete", "false"));
        boolean existingFeature = false;
        for(Features f: Features.values()) {
            log.info("Feature: {}", f.name());
            if(f.name().equalsIgnoreCase(featureParam)) existingFeature = true;
        }
        if(!existingFeature) {
            log.error("{} is not an existing feature", featureParam);
            return;
        }
        Features feature = Features.valueOf(featureParam);
        if(featureService.isEnabled(feature) && enable) {
            log.error("{} is already enabled for instance {}", featureParam, SyncariContext.getSyncariId());
            return;
        }
        if(!featureService.isEnabled(feature) && !enable) {
            log.error("{} is already disabled for instance {}", featureParam, SyncariContext.getSyncariId());
            return;
        }
        if(enable) {
            log.info("Enabling feature {} for instance {}", featureParam, SyncariContext.getSyncariId());
            if (!dryRunMode) {
                featureService.enableFeature(feature);
            }
        } else {
            log.info("Disabling feature {} for instance {}", featureParam, SyncariContext.getSyncariId());
            if (!dryRunMode) {
                featureService.disableFeature(feature);
                if (isdelete){
                    featureService.deleteFeature(feature);
                }
            }
        }
    }

}
