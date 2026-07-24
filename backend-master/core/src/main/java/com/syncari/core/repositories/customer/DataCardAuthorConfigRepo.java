package com.syncari.core.repositories.customer;

import com.syncari.core.model.insights.DataCardAuthorConfig;
import com.syncari.core.repositories.SyncariRepo;

import java.util.Optional;

public interface DataCardAuthorConfigRepo extends SyncariRepo<DataCardAuthorConfig> {

    Optional<DataCardAuthorConfig> findDataCardAuthorConfigByDashboardIdAndDatacardId(String dashboardId, String datacardId);
}
