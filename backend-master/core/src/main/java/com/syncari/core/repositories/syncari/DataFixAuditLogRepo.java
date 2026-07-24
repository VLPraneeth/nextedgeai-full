package com.syncari.core.repositories.syncari;

import com.syncari.core.model.DataFixAuditLog;
import com.syncari.core.model.misc.DataFixAuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface DataFixAuditLogRepo extends MongoRepository<DataFixAuditLog, String> {

    /**
     * Find all audit logs for a specific query
     */
    @Query("{ 'queryId' : ?0 }")
    List<DataFixAuditLog> findByQueryId(String queryId);

    /**
     * Find all audit logs by user ID
     */
    @Query("{ 'userId' : ?0 }")
    List<DataFixAuditLog> findByUserId(String userId);

    /**
     * Find all audit logs by action type
     */
    @Query("{ 'actionType' : ?0 }")
    List<DataFixAuditLog> findByActionType(DataFixAuditAction actionType);

    /**
     * Find all audit logs within a date range
     */
    @Query("{ 'timestamp' : { '$gte' : ?0, '$lte' : ?1 } }")
    List<DataFixAuditLog> findByTimestampBetween(Date startDate, Date endDate);

    /**
     * Find all audit logs for a specific instance
     */
    @Query("{ 'instanceId' : ?0 }")
    List<DataFixAuditLog> findByInstanceId(String instanceId);

    /**
     * Find all audit logs by status (SUCCESS or FAILURE)
     */
    @Query("{ 'status' : ?0 }")
    List<DataFixAuditLog> findByStatus(String status);

    /**
     * Find all failed audit logs
     */
    @Query("{ 'status' : 'FAILURE' }")
    List<DataFixAuditLog> findAllFailed();

    /**
     * Find all audit logs with pagination
     */
    Page<DataFixAuditLog> findAll(Pageable pageable);

    /**
     * Find audit logs by user and date range with pagination
     */
    @Query("{ 'userId' : ?0, 'timestamp' : { '$gte' : ?1, '$lte' : ?2 } }")
    Page<DataFixAuditLog> findByUserIdAndTimestampBetween(String userId, Date startDate, Date endDate, Pageable pageable);

    /**
     * Find audit logs by instance and action type
     */
    @Query("{ 'instanceId' : ?0, 'actionType' : ?1 }")
    List<DataFixAuditLog> findByInstanceIdAndActionType(String instanceId, DataFixAuditAction actionType);
}
