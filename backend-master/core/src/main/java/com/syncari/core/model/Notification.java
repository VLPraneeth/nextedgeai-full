package com.syncari.core.model;

import javax.validation.constraints.NotNull;

import com.syncari.core.model.misc.NotificationType;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class Notification extends UUIDAuditModel {
    private String subject;
    private String body;
    private boolean read;
    private boolean archived;
    NotificationType type;
    @NotNull(message = "User id is required")
    private String userId;
    private String key;
    public Notification() {
    }

    public Notification(String subject, String body, NotificationType type, String userId) {
        this.subject = subject;
        this.body = body;
        this.type = type;
        this.userId = userId;
    }

    public Notification(String key, String subject, String body, NotificationType type, String userId) {
        this.key = key;
        this.subject = subject;
        this.body = body;
        this.type = type;
        this.userId = userId;
    }
    
    public void setType(NotificationType type) {
        this.type = type;
    }
    
    public void setType(String type) {
        try {
            this.type = NotificationType.valueOf(type);
        } catch (Exception e) {
            log.error("Unknown notification type {}", type);
        }
    }
}
