package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.cache.CacheLoadJob;
import com.syncari.core.model.cache.CacheLoadStatus;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;

public class CreateCacheLoadJob {
    // MigrationContext.getFeatureService();

    @ChangeSet(order = "001", id = "createCacheLoadJob", author = "venkat", runAlways = true)
    public void createCacheLoadJob(MongoTemplate template) {
        var cacheLoaderService= MigrationContext.getCacheLoaderService();
        var entityName = System.getProperty("entityName");

        final CacheLoadJob cacheLoadConfig = new CacheLoadJob()
                .setInstanceId(SyncariContext.getSyncariId())
                .setStatus(CacheLoadStatus.PENDING)
                .setEntityName(entityName);
        cacheLoaderService.queueCacheLoadJob(cacheLoadConfig);
        //cacheLoaderService.runAvailableJob();
    }

}
