package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.Plan;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.repositories.syncari.PlanRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class SYN_15027_UpdateStatusToActiveFromHardDel {

    @ChangeSet(order = "001", id = "updateStatusToActiveFromHardDeleting", author = "rohit", runAlways = true)
    public void updateStatusToActiveFromHardDeleting(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        OrganizationRepo organizationRepo = MigrationContext.getOrganizationRepo();
        String orgId = System.getProperty("orgId");
        String newstatus = System.getProperty("newstatus");

        organizationRepo.findById(orgId).ifPresent(org -> {
            org.getInstances().forEach(instance -> {
                if(instance.isTrial() && instance.getStatus().equals(Status.HARD_DELETING)){
                    //update status to active
                    log.info("Updating Instance {}. Existing Status: {}", instance.getSyncariId(), instance.getStatus());
                    Status newStatus = Status.valueOf(newstatus.toUpperCase());
                    instance.setStatus(newStatus);
                }
            });

            // check dryMode and update org
            if(!dryRunMode){
                organizationRepo.save(org);
                log.info("Successfully updated org with Id: {}", orgId);
            }
        });

    }
}
