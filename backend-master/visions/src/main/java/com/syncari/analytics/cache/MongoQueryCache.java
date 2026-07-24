package com.syncari.analytics.cache;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import com.syncari.analytics.repositories.QueryCacheRepo;

@Component
public class MongoQueryCache implements QueryCache {
	@Autowired
	QueryCacheRepo queryCacheRepo;

	@Override
	public Object get(String key) {
		com.syncari.analytics.model.QueryCache cacheValue = queryCacheRepo.findByKey(key);
		if (cacheValue == null)
			return null;
		return cacheValue.getValue();
	}

	@Override
	public void put(String key, Object value) {
		queryCacheRepo.upsert(key, value);
	}

	@Override
	public <T> T getCached(String key) {
		return (T) get(key);
	}

}
