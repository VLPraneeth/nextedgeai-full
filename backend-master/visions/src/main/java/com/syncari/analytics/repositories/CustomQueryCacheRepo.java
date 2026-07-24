package com.syncari.analytics.repositories;

import com.syncari.analytics.QueryEngine;
import com.syncari.analytics.model.QueryCache;

public interface CustomQueryCacheRepo {
    QueryCache upsert(String key, Object value);
}
