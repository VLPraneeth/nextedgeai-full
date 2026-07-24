package com.syncari.core.repositories.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;

import com.syncari.core.model.EventData;
import com.syncari.core.repositories.SyncariRepo;

public interface EventDataRepo extends SyncariRepo<EventData> {
	@Query("{ 'connectorId' : ?0, 'graphId' : ?1 }")
	Page<EventData> findAllByConnectorIdAndGraphId(String connectorId, String graphId, Pageable pageable);

	@Query("{ 'connectorId' : ?0 }")
	Page<EventData> findAllByConnectorId(String connectorId, Pageable pageable);

	@Query("{ 'batchId' : ?0 }")
	Page<EventData> findAllByBatchId(String batchId, Pageable pageable);
}
