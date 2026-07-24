package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.User;
import com.syncari.core.repositories.syncari.UserRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class SYN7493_SetUserLastPasswordResetTimestamp {

    @ChangeSet(order = "001", id = "setUserLastPasswordResetTimestamp", author = "abhinav")
    public void setUserLastPasswordResetTimestamp(MongoTemplate template) {

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        UserRepo userRepo = MigrationContext.getUserRepo();
        Instant now = Instant.now();
        List<User> allUsers = userRepo.findAll();

        List<User> updatedUsers = new ArrayList<>();
        allUsers.forEach(u -> {
            if(!(u.isSystemUser() || u.isApiUser() || User.DEFAULT_SUPER_ADMIN_EMAIL.equalsIgnoreCase(u.getEmail()))){
                u.setLastPasswordResetTimestamp(now);
                log.info("Updating lastPasswordResetTimestamp for user {} to {}", u.getEmail(), now.toEpochMilli());
                updatedUsers.add(u);
            }
        });

        if(!dryRunMode) {
            userRepo.saveAll(updatedUsers);
        }

    }
}
