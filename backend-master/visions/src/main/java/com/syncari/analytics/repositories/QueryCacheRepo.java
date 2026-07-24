package com.syncari.analytics.repositories;

import com.syncari.analytics.model.QueryCache;
import com.syncari.core.repositories.SyncariRepo;

public interface QueryCacheRepo extends SyncariRepo<QueryCache>, CustomQueryCacheRepo {
	QueryCache findByKey(String key);
}
