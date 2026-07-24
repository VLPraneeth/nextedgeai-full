package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.User;
import com.syncari.core.service.SubscriptionService;
import com.syncari.core.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

@Slf4j
public class UnlockUserAccount {

    private final UserService userService = MigrationContext.getUserService();

    @ChangeSet(order = "001", id = "unlockUser", author = "rohit", runAlways = true)
    public void unlockUser(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        String email = System.getProperty("emailToUnlock");
        Optional<User> user = userService.findActiveUserByEmail(email);
        user.ifPresentOrElse(u -> {
            if (!dryRunMode){
                userService.unlockUser(email);
            }else{
                log.info(String.format("User with email %s can be unlocked if not run in dry mode", email));
            }
        }, () -> {
            log.info(String.format("User with email %s does not exists", email));
        });
    }
}
