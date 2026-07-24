package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.User;
import com.syncari.core.repositories.syncari.UserRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

@Slf4j
public class RestrictUserLogin {

    @ChangeSet(order = "001", id = "restrictUserLogin", author = "abhinav", runAlways = true)
    public void restrictUserLogin(MongoTemplate template) {

        UserRepo userRepo = MigrationContext.getUserRepo();
        String email = System.getProperty("email");

        Optional<User> userMaybe = userRepo.findByEmail(email);

        if(userMaybe.isPresent()){
            User user = userMaybe.get();
            if(user.isRestrictedFromLogin()){
                log.error("User {} is already restricted", email);
                return;
            }
            log.info("Restricting user {}", email);
            user.setRestrictedFromLogin(true);
            userRepo.save(user);
        } else {
            log.error("User with email {} does not exist.", email);
        }
    }
}
