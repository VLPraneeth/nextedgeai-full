package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.Organization;
import com.syncari.core.model.User;
import com.syncari.core.model.insights.provider.InsightsProviderUser;
import com.syncari.core.model.insights.provider.ts.TSUserResponse;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.repositories.syncari.UserRepo;
import com.syncari.core.service.TSService;
import com.syncari.core.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.Optional;

@Slf4j
public class CreateTSUsers {

    @ChangeSet(order = "001", id = "createUser", author = "rohit", runAlways = true)
    public void createUserinTS(MongoTemplate template) {

        UserRepo userRepo = MigrationContext.getUserRepo();
        UserService userService = MigrationContext.getUserService();
        List<User> superAdminUsers = userRepo.findAllSuperAdmins();
        List<User> ghostUsers = userRepo.findAllSuperAdmins();
        OrganizationRepo organizationRepo = MigrationContext.getOrganizationRepo();

        TSService tsService = MigrationContext.getTSService();
        Optional<User> userToSetContext = userService.findActiveUserByEmail("systemuser@syncari.com");
        userToSetContext.ifPresentOrElse(usr -> SyncariContext.setUser(usr), () -> {
            SyncariContext.setUser(userService.findActiveUserByEmail("system_syncari_admin@syncari.com").get());
        });

        Optional<Organization> syncari_admin_org = organizationRepo.findByName("Syncari Master");
        syncari_admin_org.ifPresent(o -> {
            o.setInsightsProviderOrgId("0");
            SyncariContext.setOrganziation(o);
        });

        HttpHeaders headers = tsService.getHeaders(Optional.of(TSService.TS_ADMIN_USER),600L); // Creating 10 mins token, for searching connection and create/update connection
        superAdminUsers.forEach(u -> {
            String userId = u.getId();
            String displayName = u.getId();
            InsightsProviderUser tsUser = new InsightsProviderUser();
            tsUser.setPassword("hardcoded@1"); // no use
            tsUser.setName(userId);
            tsUser.setDisplay_name(displayName);
            tsUser.setEmail(userId + "@syncari.io");
            tsUser.setGroup_identifiers(List.of("Administrator"));
            log.info("Creating user {} in ts with username {} and email {}", u.getEmail(), userId,userId + "@syncari.io" );
            Optional<TSUserResponse> userResponse =  tsService.searchUser(tsUser,Optional.of(TSService.TS_ADMIN_USER),true,headers);
            userResponse.ifPresentOrElse(usr -> log.info("User with id {} already exists, syncari email {}", userId, u.getEmail()), () -> {
                TSUserResponse tsUserResponse = tsService.createUser(tsUser, Optional.of(TSService.TS_ADMIN_USER),headers);
                u.setInsightsProviderUserName(userId);
                u.setInsightsProviderUserId(tsUserResponse.getId());
                log.info("Updating superadmin user {} in syncari with username {} and ts userId {}", u.getEmail(), userId,tsUserResponse.getId() );
                userService.saveUser(u);
            });
        });

        /*ghostUsers.forEach(u -> {
            String userId = u.getId();
            String displayName = u.getId();
            InsightsProviderUser tsUser = new InsightsProviderUser();
            tsUser.setPassword("hardcoded@1"); // no use
            tsUser.setName(userId);
            tsUser.setDisplay_name(displayName);
            tsUser.setEmail(userId + "@syncari.io");
            tsUser.setGroup_identifiers(List.of("Administrator"));
            log.info("Creating ghost user {} in ts with username {} and email {}", u.getEmail(), userId,userId + "@syncari.io" );
            Optional<TSUserResponse> userResponse =  tsService.searchUser(tsUser,Optional.of(TSService.TS_ADMIN_USER),true,headers);
            userResponse.ifPresentOrElse(usr -> log.info("User with id {} already exists, syncari email {}", userId, u.getEmail()), () -> {
                TSUserResponse tsUserResponse = tsService.createUser(tsUser, Optional.of(TSService.TS_ADMIN_USER),headers);
                u.setInsightsProviderUserName(userId);
                u.setInsightsProviderUserId(tsUserResponse.getId());
                log.info("Updating ghost user {} in syncari with username {} and ts userId {}", u.getEmail(), userId,tsUserResponse.getId() );
                userService.saveUser(u);
            });

        });*/


    }
}
