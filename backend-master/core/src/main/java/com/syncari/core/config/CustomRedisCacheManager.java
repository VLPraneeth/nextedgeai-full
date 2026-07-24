package com.syncari.core.config;

import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;

@EnableCaching
public class CustomRedisCacheManager extends RedisCacheManager {

    RedisCacheManager oldCacheManager;

    RedisCacheManager newCacheManager;
    public CustomRedisCacheManager(RedisCacheWriter cacheWriterOld, RedisCacheWriter cacheWriterNew,RedisCacheConfiguration cacheConfiguration) {
        super(cacheWriterNew, cacheConfiguration);
        oldCacheManager = new RedisCacheManager(cacheWriterOld, cacheConfiguration);
        newCacheManager = new RedisCacheManager(cacheWriterNew, cacheConfiguration);
    }

    public CustomRedisCacheManager(RedisCacheWriter cacheWriter, RedisCacheConfiguration defaultCacheConfiguration) {
        super(cacheWriter, defaultCacheConfiguration);
    }

    @Override
    public Cache getCache(String name) {
        var syncariId = SyncariContext.getInstance().getSyncariId();
        return newCacheManager.getCache(syncariId + ":" + name);
    }
}
