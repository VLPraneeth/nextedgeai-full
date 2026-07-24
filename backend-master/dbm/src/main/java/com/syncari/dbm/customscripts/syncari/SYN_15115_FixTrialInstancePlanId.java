package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.Plan;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.repositories.syncari.PlanRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class SYN_15115_FixTrialInstancePlanId {

    @ChangeSet(order = "001", id = "fixTrialInstancePlanId", author = "abhinav", runAlways = true)
    public void fixTrialInstancePlanId(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        OrganizationRepo organizationRepo = MigrationContext.getOrganizationRepo();
        PlanRepo planRepo = MigrationContext.getPlanRepo();

        String orgId = System.getProperty("orgId");
        Plan trialPlan = planRepo.findByName("trial").orElseThrow();

        organizationRepo.findById(orgId).ifPresent(org -> {
            org.getInstances().forEach(instance -> {
                if(instance.isTrial() && !instance.getPlanId().equals(trialPlan.getId())){
                    //assign right planId and update quota if needed
                    log.info("Updating Instance {}. Existing PlanId: {} and Quota: {}", instance.getSyncariId(), instance.getPlanId(), instance.getQuota().size());
                    instance.setPlanId(trialPlan.getId());
                    if(instance.getQuota().isEmpty()){
                        instance.setQuota(trialPlan.getQuota());
                    }

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
