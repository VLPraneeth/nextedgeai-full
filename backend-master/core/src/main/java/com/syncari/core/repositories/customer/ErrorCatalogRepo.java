package com.syncari.core.repositories.customer;

import java.util.List;

import com.syncari.core.model.ErrorCatalog;
import com.syncari.core.model.ErrorCategory;
import com.syncari.core.model.ErrorPriority;
import com.syncari.core.repositories.SyncariRepo;

public interface ErrorCatalogRepo extends SyncariRepo<ErrorCatalog> {
	List<ErrorCatalog> findByCategoryAndPriority(ErrorCategory category, ErrorPriority priority);
	List<ErrorCatalog> findByActive(boolean active);
}
