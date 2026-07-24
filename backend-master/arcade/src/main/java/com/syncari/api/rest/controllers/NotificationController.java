package com.syncari.api.rest.controllers;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.syncari.api.rest.controllers.exceptions.ResourceNotFoundException;
import com.syncari.core.SyncariContext;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.model.Notification;
import com.syncari.core.model.misc.NotificationType;
import com.syncari.core.service.NotificationService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/notification")
public class NotificationController {
    @Autowired
    NotificationService service;

    @RequestMapping(method = RequestMethod.GET)
    public List<Notification> get(@RequestParam(required = false) String type, @RequestParam(required = false) Boolean isArchived,
            @RequestParam(required = false) Boolean isRead) {
        String userId = SyncariContext.getUser().getId();
        Optional<NotificationType> notificationType = Optional.empty();
        if(!StringUtils.isBlank(type) && !"All".equalsIgnoreCase(type)) {
            notificationType = Optional.of(NotificationType.valueOf(type.toUpperCase()));
        }
        
        // All filters
        if(!StringUtils.isBlank(type) && isRead != null && isArchived != null) {
            if("All".equalsIgnoreCase(type)) {
                return service.getByArchivedAndRead(userId, isArchived, isRead);
            }
            return service.get(userId, notificationType, isArchived, isRead);
        }
        
        // By Type and Read
        if(!StringUtils.isBlank(type) && isRead != null && isArchived == null) {
            if("All".equalsIgnoreCase(type)) {
                return service.getByRead(userId, isRead);
            }
            return service.getByTypeAndRead(userId, notificationType, isRead);
        }
        
        // By Archived and Read
        if(StringUtils.isBlank(type) && isRead != null && isArchived != null) {
            return service.getByArchivedAndRead(userId, isArchived, isRead);
        }
        
        // By Type and Archived
        if(!StringUtils.isBlank(type) && isRead == null && isArchived != null) {
            if("All".equalsIgnoreCase(type)) {
                return service.getByArchived(userId, isArchived);
            }
            return service.getByTypeAndArchived(userId, notificationType, isArchived);
        }
        
        // By Type
        if(!StringUtils.isBlank(type) && isRead == null && isArchived == null) {
            if("All".equalsIgnoreCase(type)) {
                return service.get(userId);
            }
            return service.get(userId, notificationType);
        }
        
        // By Read
        if(StringUtils.isBlank(type) && isRead != null && isArchived == null) {
            return service.getByRead(userId, isRead);
        }
        
        // By Archived
        if(StringUtils.isBlank(type) && isRead == null && isArchived != null) {
            return service.getByArchived(userId, isArchived);
        }
        
        return service.get(SyncariContext.getUser().getId());
    }

    @RequestMapping(method = RequestMethod.GET, value = "/unreadcount")
    public long getUnreadCount() {
        return service.getUnreadCount(SyncariContext.getUser().getId());
    }

    @RequestMapping(method = RequestMethod.PUT, value = "/unread")
    public void markUnreadBulk(@RequestBody List<String> notificationIds) {
        try {
            service.unreadMany(SyncariContext.getUser().getId(), notificationIds);
        }
        catch (NotFoundException ex) {
            throw new ResourceNotFoundException(ex.getMessage());
        }
    }

    @RequestMapping(method = RequestMethod.PUT, value = "/read")
    public void markReadBulk(@RequestBody List<String> notificationIds) {
        try {
            service.readMany(SyncariContext.getUser().getId(), notificationIds);
        }
        catch (NotFoundException ex) {
            throw new ResourceNotFoundException(ex.getMessage());
        }
    }
    
    @RequestMapping(method = RequestMethod.PUT, value = "/archive")
    public void archiveBulk(@RequestBody List<String> notificationIds) {
        try {
            service.archiveMany(SyncariContext.getUser().getId(), notificationIds);
        }
        catch (NotFoundException ex) {
            throw new ResourceNotFoundException(ex.getMessage());
        }
    }

    @RequestMapping(method = RequestMethod.PUT, value = "/readAll")
    public void markReadAll() {
        service.readAll(SyncariContext.getUser().getId());
    }
    
    @RequestMapping(method = RequestMethod.PUT, value = "/unreadAll")
    public void markunReadAll() {
        service.unreadAll(SyncariContext.getUser().getId());
    }
    
    @RequestMapping(method = RequestMethod.PUT, value = "/archiveAll")
    public void archiveAll() {
        service.archiveAll(SyncariContext.getUser().getId());
    }
    
}
