package com.syncari.viper.scheduler;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.syncari.viper.InstanceUtil;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.syncari.core.config.AppConfig;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import com.syncari.core.model.User;
import com.syncari.core.repositories.customer.LockRepo;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.service.EmailService;
import com.syncari.core.service.EntityRepoService;
import com.syncari.core.service.UserService;
import com.syncari.viper.ViperContext;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DfiSnapshotManager {
    @Autowired
    OrganizationRepo organizationRepo;
    @Autowired
    UserService userService;
    @Autowired
    EntityRepoService repoService;
    @Autowired
    LockRepo lockRepo;
    @Autowired
    @Qualifier("defaultEmailService")
    EmailService emailService;
    @Autowired
    AppConfig appConfig;
    static String lockOwner;
    static {
        lockOwner = UUID.randomUUID().toString();
    }

    @Autowired
    InstanceUtil instanceUtil;
    
    // Run every hour. 10 minutes past hour to make sure sub cleaner job has run before this one begins
    @Scheduled(cron = "0 10 */1 * * *")
    public void dfiSnapshot() {
        // for each subscription, each dfi enabled entity, capture snapshot
        instanceUtil.forEachInstance((context -> {
            var lockId = "dfi_"+context.getInstance().getSyncariId();
            try {
                var locked = lockRepo.lock(lockId, lockOwner, Duration.ofHours(6));
                if(locked.isPresent()) {
                    log.info("Snapshotting dfi for {}", context.getInstance().getSyncariId());
                    repoService.snapshotScore();
                }
            } catch (Exception e) {
//                emailService.sendErrorEmail(List.of(), appConfig.getErrorEmail(),
//                        "Error during dfi snapshot for " + context.getInstance().getSyncariId()
//                        + " " + context.getInstance().getName(),
//                        e + ExceptionUtils.getStackTrace(e));
            } finally {
                lockRepo.unlock(lockId, lockOwner);
            }
        }));
    }
}