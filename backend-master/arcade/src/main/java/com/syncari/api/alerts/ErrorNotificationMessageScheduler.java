package com.syncari.api.alerts;

import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.syncari.core.SyncariContext;
import com.syncari.core.repositories.customer.LockRepo;
import com.syncari.core.service.ErrorNotificationService;
import com.syncari.core.service.SubscriptionService;
import com.syncari.core.service.UserService;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ErrorNotificationMessageScheduler {
	@Autowired
	private ErrorNotificationService errorNotificationService;
	@Autowired
	private SubscriptionService subscriptionService;
	@Autowired
	private UserService userService;
	@Autowired
    private LockRepo lockRepo;
	
	static String lockOwner;
    static {
        lockOwner = UUID.randomUUID().toString();
    }

	//@Scheduled(cron = "0 0/1 * * * ?") // one minute .. for testing
	// Scheduled to run every hour
	@Scheduled(cron = "0 0 * * * *")
	public void sendErrorNotifications() {
		log.info("Running ErrorNotificationMessageSender");
		var user = userService.getSystemUser();
		subscriptionService.getAllOrg().forEach(org -> {
			org.getActiveInstances().forEach(ins -> {
				SyncariContext.runWithContext(org, ins, user, () -> {
					var lockId = "errornotification"+ins.getSyncariId();
					try {
						var locked = lockRepo.lock(lockId, lockOwner, Duration.ofMinutes(15));
                        if(locked.isPresent()) {
                        	log.debug("Acquired lock {}", lockId);
                        	errorNotificationService.processDelayedErrorNotification();
                        }
					} catch (Exception e) {
						log.error("Error while running error notification job org {} instance {}", org.getId(),
								ins.getSyncariId(), e);
					} finally {
                        lockRepo.unlock(lockId,  lockOwner);
                        log.info("Releasing lock {} after processing delayed Error notifications", lockId);
                    }
				});
			});
		});
	}

}
