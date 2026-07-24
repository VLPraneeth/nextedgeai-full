package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import com.syncari.core.model.User;
import com.syncari.core.model.misc.RoleConstants;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.repositories.syncari.UserRepo;
import com.syncari.core.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.*;

@Slf4j
public class SYN_11422_FixOrgAdminRoleForUsers {

    @ChangeSet(order = "001", id = "assignOrgAdminRoleToUser", author = "rohit", runAlways = true)
    public void assignOrgAdminRoleToUser(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        UserRepo userRepo = MigrationContext.getUserRepo();
        OrganizationRepo organizationRepo = MigrationContext.getOrganizationRepo();
        UserService userService = MigrationContext.getUserService();
        // Book keep Organizations which are done for a user so we do no run those again for different available instance.
        //Get all Users
        // Get all availableInstances Of a user
        // For each instance getOrganization
        // Check if user is OrgAdmin for any Instance of given Org
        // If user isAnOrgAdminInAnyInstance then assign Admin Role forAllInstances

        List<User> allUsers = userRepo.findAll();
        allUsers.forEach(u -> {
            Set<String> allAvailableInstances = u.getAvailableInstances();
            List<String> allAvailableInstancesCopy = new ArrayList<>(allAvailableInstances);
            Set<String> orgSet = new HashSet<>();
            allAvailableInstancesCopy.forEach(instanceId -> {
                Optional<Organization> orgOpt = organizationRepo.findBySyncariId(instanceId);
                orgOpt.ifPresentOrElse(o -> {
                    if (!orgSet.contains(o.getId())){
                        if (userService.isOrgAdminInAnyInstanceOfOrg(u, o)){
                            log.info("User {} is found Org Admin in one of the instance of Org {}, Make it org admin in all", u.getEmail(), o.getName());
                            List<Instance> allOrgInstances = o.getInstances();
                            if (!dryRunMode){
                                allOrgInstances.forEach(i -> {
                                    Optional<User> userToSetContext = userService.findActiveUserByEmail("systemuser@syncari.com");
                                    userToSetContext.ifPresentOrElse(usr -> SyncariContext.setUser(usr), () -> {
                                                SyncariContext.setUser(userService.findActiveUserByEmail("system_syncari_admin@syncari.com").get());
                                            });
                                    userService.assignRolesToUser(o, i, u, Set.of(RoleConstants.ORG_ADMIN));
                                });
                            }
                        }else{
                            log.info("User {} is not found Org Admin in any of the instances of Org {}", u.getEmail(), o.getName());
                        }
                        // this org is done, don't do it again
                        orgSet.add(o.getId());
                    }else{
                        log.info("No need to iterate Org again, it was already iterated {}", o.getName());
                    }
                }, () -> log.info("No org found"));
            } );
        });


    }
}
