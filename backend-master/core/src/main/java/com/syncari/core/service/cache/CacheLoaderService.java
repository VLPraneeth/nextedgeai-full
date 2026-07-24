package com.syncari.core.service.cache;

import com.syncari.core.model.cache.CacheLoadJob;

public interface CacheLoaderService {
    /**
     * Synchronous, long-running method, that reads records and idmapping data from mongo and caches in redis
     *
     * @param
     */
    void load(CacheLoadJob cacheLoadConfig);

    /**
     * Synchronous, long-running method, that reads records and idmapping data from mongo and caches in redis
     * Assumes that a cache load job for an entity is already queued
     * @param
     */
    void runAvailableJob();

    CacheLoadJob status(String entityName);

    CacheLoadJob queueCacheLoadJob(CacheLoadJob cacheLoadJob);

}
