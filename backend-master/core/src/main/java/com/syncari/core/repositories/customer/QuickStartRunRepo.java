package com.syncari.core.repositories.customer;

import com.syncari.core.model.QuickStartRun;
import com.syncari.core.repositories.SyncariRepo;

import java.util.List;

public interface QuickStartRunRepo extends SyncariRepo<QuickStartRun>, CustomQuickStartRunRepo {

    List<QuickStartRun> findAllBySyncariEntityIdAndStatusIn(String syncariEntityId, List<QuickStartRun.Status> statuses);
}
