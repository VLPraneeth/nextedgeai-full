package com.syncari.viper.scheduler;

import com.syncari.core.service.cache.CacheLoaderService;
import com.syncari.viper.InstanceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class CacheLoader {
    @Autowired
    CacheLoaderService cacheLoaderService;

    @Autowired
    InstanceUtil instanceUtil;

    @Scheduled(fixedRate = 30000)
    public void runCacheLoadJob() {
        instanceUtil.forEachInstance((context -> {
            cacheLoaderService.runAvailableJob();
        }));

    }
}
