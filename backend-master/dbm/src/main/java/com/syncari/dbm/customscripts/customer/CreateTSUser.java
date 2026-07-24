package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.insights.InsightsProviderIntegrator;
import com.syncari.core.model.User;
import com.syncari.core.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

@Slf4j
public class CreateTSUser {

    @ChangeSet(order = "001", id = "createSeededDashboard", author = "abhinav", runAlways = true)
    public void createSeededDashboard(MongoTemplate template){
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        String email = System.getProperty("userEmail");
        InsightsProviderIntegrator insightsProviderIntegrator = MigrationContext.getInsightsProviderIntegrator();
        UserService userService = MigrationContext.getUserService();

        if (StringUtils.isEmpty(email)){
            throw new IllegalArgumentException("userEmail is mandatory parameter for this script");
        }
        Optional<User> userToSetContext = userService.findActiveUserByEmail("systemuser@syncari.com");
        userToSetContext.ifPresentOrElse(usr -> SyncariContext.setUser(usr), () -> {
            SyncariContext.setUser(userService.findActiveUserByEmail("system_syncari_admin@syncari.com").get());
        });
        Optional<User> user = userService.findActiveUserByEmail(email);
        user.ifPresentOrElse(u -> {
            String insightsProviderUserName = u.getInsightsProviderUserName();
            if (StringUtils.isNotEmpty(insightsProviderUserName)){
                log.info("User with email {} already exists in TS with username {} and id {}", email, insightsProviderUserName, u.getInsightsProviderUserId());
            }else{
                if (!dryRunMode){
                    insightsProviderIntegrator.createUserByAdmin(u);
                    Optional<User> userAfterUpdate = userService.findActiveUserByEmail(email);
                    userAfterUpdate.ifPresent(uA -> {
                        log.info("Create user with email {} in TS  with insightsUserName {} and id {}", email, uA.getInsightsProviderUserName(), uA.getInsightsProviderUserId());
                    });
                }else{
                    log.info("Running in dry run mode, did not create user with email {} in TS", email);
                }
            }
        }, () -> log.info("There is no active user with email {}", email));

    }
}
