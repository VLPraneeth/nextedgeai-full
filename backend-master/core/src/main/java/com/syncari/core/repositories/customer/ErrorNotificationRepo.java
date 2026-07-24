package com.syncari.core.repositories.customer;

import java.util.Date;
import java.util.List;

import com.syncari.core.model.ErrorNotification;
import com.syncari.core.repositories.SyncariRepo;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public interface ErrorNotificationRepo extends SyncariRepo<ErrorNotification> {
	@Deprecated
	List<ErrorNotification> findByKeyAndCreatedAtGreaterThanEqual(String key, Date start);
	List<ErrorNotification> findByCatalogIdInAndCreatedAtGreaterThanEqual(List<String> catalogId, Date start, Pageable pageable);
	List<ErrorNotification> findByCatalogIdIn(List<String> catalogId, Pageable pageable);
}
