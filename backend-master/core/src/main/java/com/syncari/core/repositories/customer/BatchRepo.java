package com.syncari.core.repositories.customer;

import java.util.List;

import org.springframework.data.mongodb.repository.Query;

import com.syncari.connector.Operation;
import com.syncari.core.model.Batch;
import com.syncari.core.repositories.SyncariRepo;

public interface BatchRepo extends SyncariRepo<Batch>{
	List<Batch> findByEntityId(String entityId);
	
	@Query(value = "{ 'entityId' : ?0 , 'operation' : ?1 }", sort = "{ createdAt : -1 }")
	List<Batch> findBatch(String entityId, Operation operation);
}
