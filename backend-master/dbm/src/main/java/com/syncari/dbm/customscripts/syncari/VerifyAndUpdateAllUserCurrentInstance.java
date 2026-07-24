package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.Organization;
import com.syncari.core.service.SubscriptionService;
import com.syncari.core.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public class VerifyAndUpdateAllUserCurrentInstance {

    @ChangeSet(order = "001", id = "verifyAndUpdateAllUserCurrentInstance", author = "abhinav", runAlways = true)
    public void verifyAndUpdateAllUserCurrentInstance(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        UserService userService = MigrationContext.getUserService();
        SubscriptionService subscriptionService = MigrationContext.getSubscriptionService();

        List<Organization> allOrgs = subscriptionService.getAllOrg();
        Set<String> allActiveInstances = new HashSet<>();
        allOrgs.forEach(o -> allActiveInstances.addAll(o.getAllSyncariIds()));

        userService.getAllActiveStandardUsers().forEach(user -> {
            if(!allActiveInstances.contains(user.getCurrentInstanceId())){
                log.info("User {} has invalid currentInstanceId {}", user.getEmail(), user.getCurrentInstanceId());
                if(!dryRunMode){
                    var validInstance = user.getAvailableInstances().stream().filter(i -> allActiveInstances.contains(i)).findFirst();
                    if(validInstance.isPresent()){
                        log.info("Setting current instance for user {} as {}", user.getEmail(), validInstance.get());
                        user.setCurrentInstanceId(validInstance.get());
                        userService.saveUser(user);
                    } else {
                        log.error("There are no valid available instances for user {}", user.getEmail());
                    }
                }
            }
        });
    }
}
