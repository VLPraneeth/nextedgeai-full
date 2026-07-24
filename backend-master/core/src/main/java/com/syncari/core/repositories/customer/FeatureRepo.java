package com.syncari.core.repositories.customer;

import java.util.Optional;

import com.syncari.core.model.Feature;
import com.syncari.core.repositories.SyncariRepo;

public interface FeatureRepo extends SyncariRepo<Feature>{
	Optional<Feature> findByName(String featureName);
}
