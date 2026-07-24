package com.syncari.api.rest.controllers;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.syncari.core.SyncariContext;
import com.syncari.core.model.User;
import com.syncari.core.model.misc.NotificationType;
import com.syncari.core.model.util.Status;
import com.syncari.core.service.NotificationService;
import com.syncari.core.service.UserService;

public class NotificationControllerTest extends AbstractSyncariTest {
	@Autowired
	NotificationController controller;
	@Autowired
	NotificationService notificationService;
	@Autowired
	UserService userService;

    @Override
    public void tearDown() {
    }
    
    @Test
    public void get(){
        pushContext();
        User newUser = userService.addUser(new User("notiftest@email.com", "NewPassw0rd", Status.ACTIVE, SyncariContext.getInstance().getSyncariId()));
        SyncariContext.setUser(newUser);
        notificationService.sendToSubscribers("Subject2", "Message2", NotificationType.ERROR);
        notificationService.sendToSubscribers("Subject3", "Message3", NotificationType.WARN);
        assertEquals(2, notificationService.getUnreadCount(newUser.getId()));
        String id1 = notificationService.get(newUser.getId()).get(0).getId();
        String id2 = notificationService.get(newUser.getId()).get(1).getId();
        // mark 1 as read
        notificationService.readMany(newUser.getId(), List.of(id1));
        assertEquals(1, controller.get(null, null, true).size());
        
        // mark 1 as read and unarchived
        notificationService.readMany(newUser.getId(), List.of(id1));
        assertEquals(1, controller.get(null, false, true).size());

        // mark 1 as unread and unarchived
        notificationService.unreadMany(newUser.getId(), List.of(id1));
        assertEquals(2, controller.get(null, false, false).size());
        assertEquals(0, controller.get(null, true, false).size());
        
        // mark 1 as archived
        notificationService.archiveMany(newUser.getId(), List.of(id1));
        assertEquals(1, controller.get(null, true, null).size());
        assertEquals(1, controller.get(null, false, null).size());
        
        // mark all as read
        notificationService.readMany(newUser.getId(), List.of(id1));
        notificationService.readMany(newUser.getId(), List.of(id2));
        assertEquals(2, controller.get(null, null, true).size());
        assertEquals(0, controller.get(null, null, false).size());
        
        // mark 1 as unread and archived
        notificationService.unreadMany(newUser.getId(), List.of(id1));
        notificationService.unreadMany(newUser.getId(), List.of(id2));
        assertEquals(1, controller.get(null, false, false).size());
        assertEquals(1, controller.get(null, true, false).size());
        assertEquals(1, controller.get(null, true, null).size());
        
        // mark 1 as read and archived
        notificationService.readMany(newUser.getId(), List.of(id1));
        assertEquals(1, controller.get(null, false, false).size());
        assertEquals(1, controller.get(null, true, true).size());
        assertEquals(0, controller.get(null, true, false).size());
        
        restoreContext();
        userService.deleteUser(newUser.getId());
    }
}
