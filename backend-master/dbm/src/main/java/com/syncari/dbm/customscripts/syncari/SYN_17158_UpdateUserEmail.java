package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.User;
import com.syncari.core.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class SYN_17158_UpdateUserEmail {

    @ChangeSet(order = "001", id = "unlockUser", author = "rohit", runAlways = true)
    public void updateUsers(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        UserService userService = MigrationContext.getUserService();
        Map<String, String> userEmailMap = Map.of("aapolinar@paypal.com","alvi.apolinar@happyreturns.com","bensun@paypal.com","ben.sun@happyreturns.com","broconnell@paypal.com","brian.o'connell@happyreturns.com",
                "damclean@paypal.com","david.mclean@happyreturns.com","hthatukuru@paypal.com","harika.thatukuru@happyreturns.com","jolivengood@paypal.com","joseph.livengood@happyreturns.com",
                "mlonsinger@paypal.com","michael.lonsinger@happyreturns.com","neden@paypal.com","nicholas.eden@happyreturns.com","sbossous@paypal.com","steve.bossous@happyreturns.com",
                "wbrownpopst@paypal.com","whitney.brown@happyreturns.com");
        userEmailMap.forEach((k,v) -> {
            Optional<User> user = userService.findActiveUserByEmail(k);
            user.ifPresentOrElse(u -> {
                if (!dryRunMode){
                    log.info("User with email {} is present , updating it to {}", k, v);
                    u.setEmail(v);
                    userService.saveUser(u);
                }else{
                    log.info("Running in dry running, not updating User with email {} to email {}", k, v);
                }
            },() -> {
                log.info("User with email {} not present, not updating it to email {}",k,v);
            });
        });


    }
}
