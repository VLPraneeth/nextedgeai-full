package com.syncari.core.repositories.customer;

import java.util.Optional;

import com.syncari.core.model.DataQualityCategory;
import com.syncari.core.repositories.SyncariRepo;

public interface DataQualityCategoryRepo extends SyncariRepo<DataQualityCategory>{

	Optional<DataQualityCategory> findByName(String name);
}
