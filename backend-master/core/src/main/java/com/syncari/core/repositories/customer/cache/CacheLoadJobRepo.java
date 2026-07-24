package com.syncari.core.repositories.customer.cache;

import com.syncari.core.model.cache.CacheLoadJob;
import com.syncari.core.repositories.SyncariRepo;


public interface CacheLoadJobRepo extends SyncariRepo<CacheLoadJob>, CustomCacheLoadRepo {
    CacheLoadJob findByEntityName(String entityName);
}
