package com.syncari.core.repositories.customer;

import com.syncari.core.model.Notification;

import java.util.List;
import java.util.Optional;

public interface CustomNotificationRepo {
    long archiveAll(String userId);

    long readAll(String userId);

    long unreadAll(String userId);
    
    long archiveMany(String userId, List<String> ids);
    
    long readMany(String userId, List<String> ids);
    
    long unreadMany(String userId, List<String> ids);

    Optional<Notification> findLatestNotifForUserByKey(String key, String userId);
}
