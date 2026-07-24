package com.syncari.core.repositories.customer;

import com.syncari.core.model.InsightsUserPreference;
import com.syncari.core.repositories.SyncariRepo;

import java.util.Optional;


public interface InsightsUserPreferenceRepo extends SyncariRepo<InsightsUserPreference> {

    Optional<InsightsUserPreference> findByUserId(String userId);
}
