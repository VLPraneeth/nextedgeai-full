package com.syncari.core.repositories.customer;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;

import com.syncari.core.model.Notification;
import com.syncari.core.model.misc.NotificationType;
import com.syncari.core.repositories.SyncariRepo;

public interface NotificationRepo extends SyncariRepo<Notification>, CustomNotificationRepo {
    @Query(value = "{ 'userId' : ?0, 'archived' : false}", sort = "{ _id : -1 }")
    List<Notification> findByUserId(String userId, Pageable pageable);

    @Query(value = "{ 'userId' : ?0, 'type' : ?1 }", sort = "{ _id : -1 }")
    List<Notification> findByType(String userId, NotificationType type, Pageable pageable);

    @Query(value = "{ 'userId' : ?0, 'read' : ?1 }", sort = "{ _id : -1 }")
    List<Notification> findByRead(String userId, boolean read, Pageable pageable);

    @Query(value = "{ 'userId' : ?0, 'archived' : ?1 }", sort = "{ _id : -1 }")
    List<Notification> findByArchived(String userId, boolean archived, Pageable pageable);

    @Query(value = "{ 'userId' : ?0, 'type' : ?1, 'read' : ?2 }", sort = "{ _id : -1 }")
    List<Notification> findByTypeAndRead(String userId, NotificationType type, boolean read, Pageable pageable);

    @Query(value = "{ 'userId' : ?0, 'type' : ?1, 'archived' : ?2 }", sort = "{ _id : -1 }")
    List<Notification> findByTypeAndArchived(String userId, NotificationType type, boolean archived, Pageable pageable);

    @Query(value = "{ 'userId' : ?0, 'read' : ?1, 'archived' : ?2 }", sort = "{ _id : -1 }")
    List<Notification> findByReadAndArchived(String userId, boolean read, boolean archived, Pageable pageable);

    @Query(value = "{ 'userId' : ?0, 'type' : ?1, 'archived' : ?2, 'read' : ?3 }", sort = "{ _id : -1 }")
    List<Notification> findByTypeAndArchivedAndRead(String userId, NotificationType type, boolean archived, boolean read, Pageable pageable);

    @Query(value = "{ 'userId' : ?0, 'key' : ?1 }", sort = "{ _id : -1 }")
    List<Notification> findByKey(String userId, String key, Pageable pageable);

    @Query(value = "{ 'userId' : ?0, 'read' : false, 'archived' : false}", count = true)
    long unreadCount(String userId);
}
