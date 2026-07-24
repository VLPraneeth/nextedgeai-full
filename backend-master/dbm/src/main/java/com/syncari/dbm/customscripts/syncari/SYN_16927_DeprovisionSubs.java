package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import com.syncari.core.model.User;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.service.ProvisioningService;
import com.syncari.core.service.SubscriptionService;
import com.syncari.core.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Optional;

@Slf4j

public class SYN_16927_DeprovisionSubs {

    @ChangeSet(order = "001", id = "deprovisionharddeletingsubs", author = "rohit", runAlways = true)
    public void deprovisionharddeletingsubs(MongoTemplate template) {
        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        ProvisioningService provisioningService = MigrationContext.getProvisioningService();
        OrganizationRepo organizationRepo = MigrationContext.getOrganizationRepo();
        List<Organization> orgs = organizationRepo.findHardDeletingCustomers();
        UserService userService = MigrationContext.getUserService();
        Optional<User> userToSetContext = userService.findActiveUserByEmail("systemuser@syncari.com");
        userToSetContext.ifPresentOrElse(usr -> SyncariContext.setUser(usr), () -> {
            SyncariContext.setUser(userService.findActiveUserByEmail("system_syncari_admin@syncari.com").get());
        });
        orgs.forEach( o -> {
            if (!dryRun){
                provisioningService.deprovision(o.getId(),true);
            }else{
                log.info("Deleting all instances of org {}", o.getName());
            }
        });


    }
}
