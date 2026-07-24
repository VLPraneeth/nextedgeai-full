package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.Instance;
import com.syncari.core.service.ProvisioningService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class UpgradeTrialInstance {

    @ChangeSet(order = "001", id = "upgradeTrialInstance", author = "abhinav")
    public void upgradeTrialInstance(MongoTemplate template) {
        ProvisioningService provisioningService = MigrationContext.getProvisioningService();
        Instance instance = SyncariContext.getInstance();
        try {
            provisioningService.upgradeTrialInstance();
        } catch (Exception e){
            log.error(String.format("Upgrading of trial instance %s failed", instance.getSyncariId()), e);
            throw e;
        }
    }
}
