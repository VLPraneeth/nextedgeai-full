package com.syncari.core.repositories.customer.cache;

import com.syncari.core.model.cache.CacheLoadJob;

public interface CustomCacheLoadRepo {

    CacheLoadJob findAndReserveJob();
}
