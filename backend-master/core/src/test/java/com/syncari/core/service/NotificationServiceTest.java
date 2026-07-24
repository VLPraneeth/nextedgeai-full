package com.syncari.core.service;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Notification;
import com.syncari.core.model.Organization;
import com.syncari.core.model.User;
import com.syncari.core.model.misc.NotificationType;
import com.syncari.core.model.misc.RoleConstants;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.NotificationRepo;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.*;

public class NotificationServiceTest extends AbstractSyncariTest {

    @Autowired
    NotificationService notificationService;

    @Autowired
    NotificationRepo notificationRepo;

    @Autowired
    UserService userService;

    @Autowired
    SubscriptionService subService;

    @Override
    public void tearDown() {
        super.tearDown();
        resetRepos(notificationRepo);
    }

    @Test
    public void testSendToSubscribers(){
        List<User> admins = userService.getAdmins();
        assertEquals(1, admins.size());
        assertEquals(0, notificationRepo.findAll().size());
        notificationService.sendToSubscribers("Subject", "Message", NotificationType.WARN);
        assertEquals(admins.size(), notificationRepo.findAll().size());

        notificationRepo.reset();

        User newUser = userService.addUser(new User("notiftest@email.com", "NewPassw0rd", Status.ACTIVE, SyncariContext.getInstance().getSyncariId()));
        userService.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), newUser, Set.of(RoleConstants.ORG_ADMIN));
        admins = userService.getAdmins();
        assertEquals(2, admins.size());
        notificationService.sendToSubscribers("Subject2", "Message2", NotificationType.ERROR);
        assertEquals(1, notificationRepo.findAll().size());
        assertEquals(0, notificationService.get(newUser.getId(), Optional.empty(), false, false).size());
        assertEquals(0, notificationService.get(newUser.getId(), Optional.of(NotificationType.ANNOUNCEMENT), false, false).size());
        assertEquals(0, notificationService.get(newUser.getId(), Optional.of(NotificationType.ERROR), false, false).size());
        assertEquals(0, notificationService.get(newUser.getId(), Optional.of(NotificationType.WARN), false, false).size());
        assertEquals(0, notificationService.get(newUser.getId(), Optional.of(NotificationType.INFO), false, false).size());

        assertEquals(1, notificationService.get(SyncariContext.getUser().getId(), Optional.empty(), false, false).size());
        assertEquals(0, notificationService.get(SyncariContext.getUser().getId(), Optional.of(NotificationType.ANNOUNCEMENT), false, false).size());
        assertEquals(1, notificationService.get(SyncariContext.getUser().getId(), Optional.of(NotificationType.ERROR), false, false).size());
        assertEquals(0, notificationService.get(SyncariContext.getUser().getId(), Optional.of(NotificationType.WARN), false, false).size());
        assertEquals(0, notificationService.get(SyncariContext.getUser().getId(), Optional.of(NotificationType.INFO), false, false).size());

        userService.deleteUser(newUser.getId());

    }

    @Test
    public void unreadCount(){
        User newUser = userService.addUser(new User("notiftest@email.com", User.generatePassword(), Status.ACTIVE, SyncariContext.getInstance().getSyncariId()));
        userService.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(),newUser, Set.of(RoleConstants.ORG_ADMIN));
        notificationService.sendToSubscribers("Subject2", "Message2", NotificationType.ERROR);
        notificationService.sendToSubscribers("Subject3", "Message3", NotificationType.WARN);
        assertEquals(2, notificationService.getUnreadCount(SyncariContext.getUser().getId()));
        
        // mark 1 as read
        notificationService.readMany(SyncariContext.getUser().getId(), List.of(notificationService.get(SyncariContext.getUser().getId()).get(0).getId()));
        assertEquals(1, notificationService.getUnreadCount(SyncariContext.getUser().getId()));
        
        // mark all as read
        notificationService.readMany(SyncariContext.getUser().getId(), List.of(notificationService.get(SyncariContext.getUser().getId()).get(0).getId()));
        notificationService.readMany(SyncariContext.getUser().getId(), List.of(notificationService.get(SyncariContext.getUser().getId()).get(1).getId()));
        assertEquals(0, notificationService.getUnreadCount(SyncariContext.getUser().getId()));
        
        userService.deleteUser(newUser.getId());
    }

    @Test
    public void sendToSuperAdmins(){

        List<User> superAdmins = userService.getSuperAdmins();
        assertFalse(superAdmins.isEmpty());
        Organization adminOrg = subService.getSyncariMasterOrg();
        Instance instance = adminOrg.getInstance(SubscriptionService.SYNCARI_ADMIN_INSTANCE).get();
        // clear syncari_admin instance's notification repo first
        SyncariContext.runWithContext(adminOrg, instance, superAdmins.get(0), () -> {
            notificationRepo.reset();
        });

        String subject = "Test Subject";
        String message = "Test Message";

        notificationService.sendToSuperAdmins(subject, message, NotificationType.INFO);
        var currentInstanceNotifications = notificationRepo.findAll();
        assertTrue(currentInstanceNotifications.isEmpty());

        // check notification for superAdmin in syncari_admin instance
        SyncariContext.runWithContext(adminOrg, instance, superAdmins.get(0), () -> {
            var adminNotifs = notificationRepo.findAll();
            assertFalse(adminNotifs.isEmpty());
            assertEquals(subject, adminNotifs.get(0).getSubject());
            assertEquals(message, adminNotifs.get(0).getBody());
            notificationRepo.reset();
        });
    }

    @Test
    public void sendWithFrequency(){
        Notification lastNotif = notificationRepo.save(new Notification("KEY", "subject", "body", NotificationType.WARN, SyncariContext.getUser().getId()));
        Notification newNotif = new Notification("KEY", "subject", "body", NotificationType.WARN, SyncariContext.getUser().getId());
        lastNotif.setCreatedAt(new Date(Instant.now().minus(1, ChronoUnit.HOURS).toEpochMilli()));
        lastNotif = notificationRepo.save(lastNotif);

        List<Notification> notifications = notificationService.getByKey(SyncariContext.getUser().getId(), "KEY");
        assertFalse(notifications.isEmpty());
        assertEquals(1, notifications.size());

        notificationService.sendWithFrequency(newNotif, NotificationService.NotificationFrequency.HOURLY);
        notifications = notificationService.getByKey(SyncariContext.getUser().getId(), "KEY");
        assertEquals(2, notifications.size()); // new notification sent

        notificationService.sendWithFrequency(newNotif, NotificationService.NotificationFrequency.DAILY);
        notifications = notificationService.getByKey(SyncariContext.getUser().getId(), "KEY");
        assertEquals(2, notifications.size()); // new notification not sent

        notificationService.sendWithFrequency(newNotif, NotificationService.NotificationFrequency.IMMEDIATE);
        notifications = notificationService.getByKey(SyncariContext.getUser().getId(), "KEY");
        assertEquals(2, notifications.size()); // new notification not sent
    }

    @Test
    public void shouldSendNotification() {
        Notification lastNotif = notificationRepo.save(new Notification("KEY", "subject", "body", NotificationType.WARN, SyncariContext.getUser().getId()));
        Notification newNotif = new Notification("KEY", "subject", "body", NotificationType.WARN, SyncariContext.getUser().getId());
        lastNotif.setCreatedAt(new Date(Instant.now().minus(1, ChronoUnit.HOURS).toEpochMilli()));
        lastNotif = notificationRepo.save(lastNotif);
        assertTrue(notificationService.shouldSendNotification(newNotif, NotificationService.NotificationFrequency.HOURLY));
        assertTrue(notificationService.shouldSendNotification(newNotif, NotificationService.NotificationFrequency.IMMEDIATE));
        assertFalse(notificationService.shouldSendNotification(newNotif, NotificationService.NotificationFrequency.DAILY));
        assertFalse(notificationService.shouldSendNotification(newNotif, NotificationService.NotificationFrequency.WEEKLY));
        assertFalse(notificationService.shouldSendNotification(newNotif, NotificationService.NotificationFrequency.MONTHLY));

        lastNotif.setCreatedAt(new Date(Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli()));
        lastNotif = notificationRepo.save(lastNotif);
        assertTrue(notificationService.shouldSendNotification(newNotif, NotificationService.NotificationFrequency.HOURLY));
        assertTrue(notificationService.shouldSendNotification(newNotif, NotificationService.NotificationFrequency.IMMEDIATE));
        assertTrue(notificationService.shouldSendNotification(newNotif, NotificationService.NotificationFrequency.DAILY));
        assertFalse(notificationService.shouldSendNotification(newNotif, NotificationService.NotificationFrequency.WEEKLY));
        assertFalse(notificationService.shouldSendNotification(newNotif, NotificationService.NotificationFrequency.MONTHLY));

        lastNotif.setCreatedAt(new Date(Instant.now().minus(7, ChronoUnit.DAYS).toEpochMilli()));
        lastNotif = notificationRepo.save(lastNotif);
        assertTrue(notificationService.shouldSendNotification(newNotif, NotificationService.NotificationFrequency.HOURLY));
        assertTrue(notificationService.shouldSendNotification(newNotif, NotificationService.NotificationFrequency.IMMEDIATE));
        assertTrue(notificationService.shouldSendNotification(newNotif, NotificationService.NotificationFrequency.DAILY));
        assertTrue(notificationService.shouldSendNotification(newNotif, NotificationService.NotificationFrequency.WEEKLY));
        assertFalse(notificationService.shouldSendNotification(newNotif, NotificationService.NotificationFrequency.MONTHLY));

        lastNotif.setCreatedAt(new Date(Instant.now().minus(30, ChronoUnit.DAYS).toEpochMilli()));
        lastNotif = notificationRepo.save(lastNotif);
        assertTrue(notificationService.shouldSendNotification(newNotif, NotificationService.NotificationFrequency.HOURLY));
        assertTrue(notificationService.shouldSendNotification(newNotif, NotificationService.NotificationFrequency.IMMEDIATE));
        assertTrue(notificationService.shouldSendNotification(newNotif, NotificationService.NotificationFrequency.DAILY));
        assertTrue(notificationService.shouldSendNotification(newNotif, NotificationService.NotificationFrequency.WEEKLY));
        assertTrue(notificationService.shouldSendNotification(newNotif, NotificationService.NotificationFrequency.MONTHLY));
    }
}
