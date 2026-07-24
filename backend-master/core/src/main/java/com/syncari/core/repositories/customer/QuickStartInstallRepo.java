package com.syncari.core.repositories.customer;

import com.syncari.core.quickstart.v2.QuickStartInstall;
import com.syncari.core.repositories.SyncariRepo;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface QuickStartInstallRepo extends SyncariRepo<QuickStartInstall> {

    @Query("{ 'status' : ?0, 'quickStart.id':?1}")
    public List<QuickStartInstall> findAllByStatusAndQuickStartId(QuickStartInstall.Status status, String quickStartId);

    @Query("{ 'quickStart.id':?0}")
    public List<QuickStartInstall> findAllByStatusAndQuickStartId(String quickStartId);
    
}
