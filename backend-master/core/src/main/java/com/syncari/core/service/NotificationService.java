package com.syncari.core.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.syncari.core.SyncariContext;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Notification;
import com.syncari.core.model.Organization;
import com.syncari.core.model.User;
import com.syncari.core.model.misc.NotificationType;
import com.syncari.core.repositories.customer.NotificationRepo;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NotificationService {
	private static final String SUPPORT = "support@syncari.com";

    private static final int LIMIT = 1000;

    @Autowired
	private NotificationRepo notificationRepo;

	@Autowired
	private UserService userService;

	@Autowired
	SubscriptionService subscriptionService;
	
	@Autowired
	@Qualifier("defaultEmailService")
	EmailService emailService;

	public enum NotificationFrequency {
		IMMEDIATE,
		HOURLY,
		DAILY,
		WEEKLY,
		MONTHLY
	}

	public void readAll(String userId) {
	    notificationRepo.readAll(userId);
	}

	public void unreadAll(String userId) {
	    notificationRepo.unreadAll(userId);
	}

	public void archiveAll(String userId) {
        notificationRepo.archiveAll(userId);
    }

    public void unreadMany(String userId, List<String> notificationIds) {
        notificationRepo.unreadMany(userId, notificationIds);
    }

	public void archiveMany(String userId, List<String> notificationIds) {
        notificationRepo.archiveMany(userId, notificationIds);
	}

    public void readMany(String userId, List<String> notificationIds) {
        notificationRepo.readMany(userId, notificationIds);
    }

	public boolean sendWithFrequency(Notification notification, NotificationFrequency frequency) {
		if(shouldSendNotification(notification, frequency)){
			send(notification);
			return true;
		}
		return false;
	}

	public void send(Notification notification) {
		notificationRepo.save(notification);
	}

	public List<Notification> get(String userId) {
		return notificationRepo.findByUserId(userId, PageRequest.of(0, LIMIT));
	}

	public List<Notification> get(String userId, Optional<NotificationType> type, boolean isArchived, boolean read) {
	    if(type.isPresent()) {
	        return notificationRepo.findByTypeAndArchivedAndRead(userId, type.get(), isArchived, read, PageRequest.of(0, LIMIT));
	    }
	    return notificationRepo.findByUserId(userId, PageRequest.of(0, LIMIT));
	}

	public List<Notification> getByTypeAndArchived(String userId, Optional<NotificationType> type, boolean isArchived) {
	    if(type.isPresent()) {
	        return notificationRepo.findByTypeAndRead(userId, type.get(), isArchived, PageRequest.of(0, LIMIT));
	    }
	    return notificationRepo.findByUserId(userId, PageRequest.of(0, LIMIT));
	}

	public List<Notification> getByTypeAndRead(String userId, Optional<NotificationType> type, boolean read) {
	    if(type.isPresent()) {
	        return notificationRepo.findByTypeAndArchived(userId, type.get(), read, PageRequest.of(0, LIMIT));
	    }
	    return notificationRepo.findByUserId(userId, PageRequest.of(0, LIMIT));
	}

	public List<Notification> getByArchivedAndRead(String userId, boolean archived, boolean read) {
	    return notificationRepo.findByReadAndArchived(userId, read, archived, PageRequest.of(0, LIMIT));
	}

	public List<Notification> getByRead(String userId, boolean read) {
	    return notificationRepo.findByRead(userId, read, PageRequest.of(0, LIMIT));
	}

	public List<Notification> getByKey(String userId, String key) {
		return notificationRepo.findByKey(userId, key, PageRequest.of(0, LIMIT));
	}

	public List<Notification> getByArchived(String userId, boolean archived) {
	    return notificationRepo.findByArchived(userId, archived, PageRequest.of(0, LIMIT));
	}

	public List<Notification> get(String userId, Optional<NotificationType> type) {
	    if(type.isPresent()) {
	        return notificationRepo.findByType(userId, type.get(), PageRequest.of(0, LIMIT));
	    }
	    return notificationRepo.findByUserId(userId, PageRequest.of(0, LIMIT));
	}

    public long getUnreadCount(String userId) {
        return notificationRepo.unreadCount(userId);
    }
	/**
	 * Sends notification to all subscribed users (Currently logged in user)
	 * @param subject String: Subject of notification
	 * @param message String:- Message in notification
	 * @param type NotificationType- Type of notification
	 */
    public void sendToSubscribers(String subject, String message, NotificationType type) {
        Notification notification = new Notification(subject, message, type, SyncariContext.getUser().getId());
        send(notification);
    }

	/**
	 * Sends notification to all superAdmins in syncari_admin instance
	 * @param subject String: Subject of notification
	 * @param message String:- Message in notification
	 * @param type NotificationType- Type of notification
	 */
    public void sendToSuperAdmins(String subject, String message, NotificationType type) {
		Organization org = subscriptionService.getSyncariMasterOrg();
		Instance instance = org.getInstance(SubscriptionService.SYNCARI_ADMIN_INSTANCE)
				.orElseThrow(() -> new NotFoundException(Instance.class, "syncariId", SubscriptionService.SYNCARI_ADMIN_INSTANCE));
        List<User> superAdmins = userService.getSuperAdmins();

        superAdmins.forEach(user -> {
			SyncariContext.runWithContext(org, instance, user, () -> {
				send(new Notification(subject, message, type, user.getId()));
			});
		});
        emailService.sendHtml(List.of(SUPPORT), subject, message);

    }

	public void broadcast(String subject, String message, NotificationType type){
	    // When we have the user opt out (notification subscription), exclude the opted out users
	    userService.listByInstance(SyncariContext.getSyncariId()).forEach(user -> {
	        Notification notification = new Notification(subject, message, type, user.getId());
	        send(notification);
	    });
	}

	public Optional<Notification> findLastNotificationForUserByKey(String key, String userId){
		return notificationRepo.findLatestNotifForUserByKey(key, userId);
	}

	protected boolean shouldSendNotification(Notification notification, NotificationFrequency frequency){
		Optional<Notification> lastNotificationByKey = findLastNotificationForUserByKey(notification.getKey(), notification.getUserId());
    	if(lastNotificationByKey.isEmpty()) return true;
    	long timeGapMillis = Instant.now().toEpochMilli() - lastNotificationByKey.get().getCreatedAt().getTime();
    	switch (frequency){
			case IMMEDIATE:
				return true;
			case HOURLY:
				return TimeUnit.MILLISECONDS.toHours(timeGapMillis) >= 1;
			case DAILY:
				return TimeUnit.MILLISECONDS.toDays(timeGapMillis) >= 1;
			case WEEKLY:
				return TimeUnit.MILLISECONDS.toDays(timeGapMillis) >= 7;
			case MONTHLY:
				return TimeUnit.MILLISECONDS.toDays(timeGapMillis) >= 30;

			default:
				log.warn("Unhandled NotificationFrequency {}", frequency);
				return false;
		}
	}
}
