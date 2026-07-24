package com.syncari.core.repositories.syncari;

import com.syncari.core.model.DataFixQuery;
import com.syncari.core.model.misc.DataFixQueryStatus;
import com.syncari.core.model.misc.DataFixQueryType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DataFixQueryRepo extends MongoRepository<DataFixQuery, String> {

    /**
     * Find all queries by requester ID
     */
    @Query("{ 'requesterId' : ?0 }")
    List<DataFixQuery> findByRequesterId(String requesterId);

    /**
     * Find all queries by approver ID
     */
    @Query("{ 'approverId' : ?0 }")
    List<DataFixQuery> findByApproverId(String approverId);

    /**
     * Find all queries by status
     */
    @Query("{ 'status' : ?0 }")
    List<DataFixQuery> findByStatus(DataFixQueryStatus status);

    /**
     * Find all queries by requester and status
     */
    @Query("{ 'requesterId' : ?0, 'status' : ?1 }")
    List<DataFixQuery> findByRequesterIdAndStatus(String requesterId, DataFixQueryStatus status);

    /**
     * Find all queries by approver and status
     */
    @Query("{ 'approverId' : ?0, 'status' : ?1 }")
    List<DataFixQuery> findByApproverIdAndStatus(String approverId, DataFixQueryStatus status);

    /**
     * Find all queries by instance ID
     */
    @Query("{ 'instanceId' : ?0 }")
    List<DataFixQuery> findByInstanceId(String instanceId);

    /**
     * Find all queries by query type
     */
    @Query("{ 'queryType' : ?0 }")
    List<DataFixQuery> findByQueryType(DataFixQueryType queryType);

    /**
     * Find all pending approval queries for a specific approver
     */
    @Query("{ 'approverId' : ?0, 'status' : 'PENDING_APPROVAL' }")
    List<DataFixQuery> findPendingApprovalsByApproverId(String approverId);

    /**
     * Find all approved queries ready for execution
     */
    @Query("{ 'status' : 'APPROVED' }")
    List<DataFixQuery> findAllApproved();

    /**
     * Find all queries for a specific instance and status
     */
    @Query("{ 'instanceId' : ?0, 'status' : ?1 }")
    List<DataFixQuery> findByInstanceIdAndStatus(String instanceId, DataFixQueryStatus status);
}
