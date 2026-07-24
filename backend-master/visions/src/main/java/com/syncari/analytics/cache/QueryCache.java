package com.syncari.analytics.cache;

public interface QueryCache {
	public Object get(String key);
	public void put(String key, Object value);

	<T> T getCached(String key);
}
