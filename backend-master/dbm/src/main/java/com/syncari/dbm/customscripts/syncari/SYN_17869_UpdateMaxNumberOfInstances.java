package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.Organization;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

@Slf4j
public class SYN_17869_UpdateMaxNumberOfInstances {
    @ChangeSet(order = "001", id = "updateMaxNumberOfInstanceForAnOrg", author = "rohit", runAlways = true)
    public void updateMaxNumberOfInstanceForAnOrg(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        String orgId = System.getProperty("orgId");
        String maxInstancesCount = System.getProperty("maxInstances");
        OrganizationRepo organizationRepo  = MigrationContext.getOrganizationRepo();
        if (StringUtils.isEmpty(orgId)){
            throw new RuntimeException("orgId cannot be empty, it needs to be passed");
        }
        if (StringUtils.isEmpty(maxInstancesCount)){
            throw new RuntimeException("maxInstances cannot be empty, it needs to be passed");
        }
        int number = Integer.parseInt(maxInstancesCount);
        Optional<Organization> organization = organizationRepo.findById(orgId);
        organization.ifPresentOrElse(o -> {
            if (!dryRunMode){
                o.setMaxNumberOfInstances(maxInstancesCount);
                organizationRepo.save(o);
                log.info("Updated org with id {} maxNumberOfInstances to {}", orgId,maxInstancesCount);
            }else{
                log.info("Running in dry run mode, not updating org with id {}", orgId);
            }

        },() -> log.info("Org with id {} does not exist", orgId));
    }
}
