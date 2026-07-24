package com.syncari.core.repositories.customer;

import java.util.Optional;

import com.syncari.core.model.DatastoreWatermark;
import com.syncari.core.repositories.SyncariRepo;

public interface DatastoreWatermarkRepo extends SyncariRepo<DatastoreWatermark>{
	Optional<DatastoreWatermark> findByEntityId(String entityId);
}
