package com.syncari.core.repositories.customer;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.Query;

import com.syncari.core.model.UserPreference;
import com.syncari.core.repositories.SyncariRepo;

public interface UserPreferenceRepo extends SyncariRepo<UserPreference> {

    Optional<UserPreference> findByUserId(String userId);
    @Query("{'errorNotification': {'$ne': null}}")
    List<UserPreference> findByErrorNotificationNotNull();

}
