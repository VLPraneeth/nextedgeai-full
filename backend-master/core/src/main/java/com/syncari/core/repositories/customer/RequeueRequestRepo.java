package com.syncari.core.repositories.customer;

import com.syncari.core.model.RequeueRequest;
import com.syncari.core.repositories.SyncariRepo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;

import java.time.ZonedDateTime;
import java.util.List;

public interface RequeueRequestRepo extends SyncariRepo<RequeueRequest>, CustomRequeueRequestRepo {
	@Query("{'entityDefinitionId': ?0, graphId : ?1, 'retryTimeLimit':{'$gte' : ?2}}")
	Page<RequeueRequest> findRequestsAfter(String entityDefinitionId, String graphId, ZonedDateTime after, Pageable page);

	/**
	 * @param entityDefinitionId
	 * @param graphId
	 * @param before
	 * @param page
	 * @return All expired requests, without the processExpiredRecord flag set. Records with the flag set need to go through
	 * the pipeline and get cleaned up automatically
	 */
	@Query("{'entityDefinitionId':?0, graphId : ?1, 'retryTimeLimit':{'$lt':?2},'processExpiredRecord': {'$ne': true}}")
	Page<RequeueRequest> findRequestsBefore(String entityDefinitionId, String graphId, ZonedDateTime before, Pageable page);

	@Query(value = "{'entityDefinitionId':?0, graphId : ?1, 'recordId':{'$in':?2}}", delete = true)
	void deleteProcessed(String entityDefinitionId, String graphId, List<String> recordIds);

	@Query("{'entityDefinitionId': ?0, graphId : ?1, recordType: ?2, 'retryTimeLimit':{'$gte' : ?3}}")
	Page<RequeueRequest> findRequests(String entityDefinitionId, String graphId, RequeueRequest.RecordType recordType, ZonedDateTime after, Pageable page);

	@Query("{'entityDefinitionId': ?0, graphId : ?1, recordType: ?2, 'retryTimeLimit':{'$lt' : ?3},'processExpiredRecord': true}")
	Page<RequeueRequest> findExpiredRequestsToProcess(String entityDefinitionId, String graphId, RequeueRequest.RecordType recordType, ZonedDateTime after, Pageable page);

}
