package com.syncari.core.repositories.customer;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;

import com.syncari.core.model.TransactionLog;
import com.syncari.core.repositories.SyncariRepo;

@Deprecated
public interface TransactionLogRepo extends SyncariRepo<TransactionLog> {

	@Query("{batchId: ?0, operation:{$ne:'merge'}}")
	Page<TransactionLog> findByBatchId(String batchId, Pageable page);

	List<TransactionLog> findByBatchIdAndSyncariIdIn(String batchId, List<String> syncariIds);

	@Query("{batchId: ?0, operation:'merge'}")
	Page<TransactionLog> findMergesByBatchId(String batchId, Pageable page);

	@Query("{_id : {'$lt' : ?0}}")
	Stream<TransactionLog> findAllStream(ObjectId pageMarker);

	@Query("{}")
	Stream<TransactionLog> findAllStream();

	Page<TransactionLog> findAll(Pageable page);

	@Query("{_id : {'$gte' : ?0, '$lt' : ?1}}")
	Stream<TransactionLog> findByObjectIdRange(ObjectId start, ObjectId end);

	@Query("{'createdAt' : {'$gte' : ?0, '$lt' : ?1}}")
	Page<TransactionLog> findByRange(Pageable page, Date start, Date end);

	@Query("{'createdAt' : {'$gt' : ?0, '$lt' : ?1}, 'entityName' : ?2, 'operation' : ?3}")
	Page<TransactionLog> findEntityOperationByRange(Pageable page, Date start, Date end, String entityName, String operation);

	@Query("{'entityName' : ?0, 'operation' : ?1}")
	Page<TransactionLog> findByEntityAndOperation(Pageable page, String entityName, String operation);

	@Query(value = "{'syncariId' : ?0}",  sort = "{ createdAt : -1 }")
	Page<TransactionLog> findBySyncariId(Pageable page, String syncariId);

	@Query(value = "{entityName : ?0, 'syncariId' : {'$in': ?1}, 'createdAt' : {'$gt' :  ?2}}")
	List<TransactionLog> findEntitySyncariIdsByDate(String entityName, List<String> syncariIds, Date date);

	@Query(value = "{entityName : ?0, 'sourceTransactionId' : {'$in': ?1}, 'createdAt' : {'$gt' :  ?2}, 'operation' :  {'$in' :  ['external_create', 'external_update']}}")
	List<TransactionLog> findDestinationLogs(String entityName, List<String> sourceTxns, Date createdAt);

	Long countByBatchId(String batchId);
	
}
