package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.service.SubscriptionService;
import com.syncari.core.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class UpdateUserCurrentInstanceId {

    @ChangeSet(order = "001", id = "updateUserCurrentInstanceId", author = "abhinav", runAlways = true)
    public void updateUserCurrentInstanceId(MongoTemplate template) {
        String userEmail = System.getProperty("user");
        String instance = System.getProperty("instance");

        UserService userService = MigrationContext.getUserService();
        SubscriptionService subscriptionService = MigrationContext.getSubscriptionService();

        userService.getUserByEmail(userEmail).ifPresentOrElse(user -> {
            if(subscriptionService.isActiveInstance(instance)){
                log.info("Changing user {} currentInstanceId to {}", userEmail, instance);
                user.setCurrentInstanceId(instance);
                userService.saveUser(user);
            } else {
                log.error("Instance {} is not active", instance);
            }

        },() -> {
            log.error("User with email {} does not exists", userEmail);
        });
    }


}
