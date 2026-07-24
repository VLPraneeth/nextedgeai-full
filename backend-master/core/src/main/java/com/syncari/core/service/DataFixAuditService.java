package com.syncari.core.service;

import com.syncari.core.model.DataFixAuditLog;
import com.syncari.core.model.DataFixQuery;
import com.syncari.core.model.misc.DataFixAuditAction;
import com.syncari.core.repositories.syncari.DataFixAuditLogRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * Service for managing data fix audit logs
 * Provides comprehensive audit trail for SOC compliance (3-year retention)
 */
@Slf4j
@Component
public class DataFixAuditService {

    @Autowired
    private DataFixAuditLogRepo auditLogRepo;

    /**
     * Log a query submission
     */
    public DataFixAuditLog logQuerySubmission(DataFixQuery query, String requesterId, String requesterEmail) {
        DataFixAuditLog log = DataFixAuditLog.forQuerySubmission(query, requesterId, requesterEmail);
        DataFixAuditLog saved = auditLogRepo.save(log);
//        log.info("Query submission logged: queryId={}, requesterId={}", query.getId(), requesterId);
        return saved;
    }

    /**
     * Log a query approval or rejection
     */
    public DataFixAuditLog logQueryApproval(DataFixQuery query, String approverId, String approverEmail,
                                           boolean approved, String reason) {
        DataFixAuditLog log = DataFixAuditLog.forQueryApproval(query, approverId, approverEmail, approved, reason);
        DataFixAuditLog saved = auditLogRepo.save(log);
//        log.info("Query {} logged: queryId={}, approverId={}",
//                approved ? "approval" : "rejection", query.getId(), approverId);
        return saved;
    }

    /**
     * Log a query execution
     */
    public DataFixAuditLog logQueryExecution(DataFixQuery query, String executorId, String executorEmail,
                                            boolean success, String failureReason) {
        DataFixAuditLog log = DataFixAuditLog.forQueryExecution(query, executorId, executorEmail, success, failureReason);
        DataFixAuditLog saved = auditLogRepo.save(log);
//        log.info("Query execution logged: queryId={}, executorId={}, success={}",
//                query.getId(), executorId, success);
        return saved;
    }

    /**
     * Log a dry run execution
     */
    public DataFixAuditLog logDryRun(DataFixQuery query, String userId, String userEmail) {
        DataFixAuditLog log = new DataFixAuditLog(userId, userEmail, DataFixAuditAction.DRY_RUN_EXECUTED);
        log.setQueryId(query.getId());
        log.setQueryText(query.getQueryText());
        log.setTargetDatabase(query.getTargetDatabase());
        log.setTargetCollection(query.getTargetCollection());
        log.setAffectedRows(query.getAffectedRowCount());
        log.setJustification(query.getJustification());
        log.setInstanceId(query.getInstanceId());
        DataFixAuditLog saved = auditLogRepo.save(log);
//        log.info("Dry run logged: queryId={}, userId={}", query.getId(), userId);
        return saved;
    }

    /**
     * Log tool access
     */
    public DataFixAuditLog logToolAccess(String userId, String userEmail, String instanceId) {
        DataFixAuditLog log = new DataFixAuditLog(userId, userEmail, DataFixAuditAction.TOOL_ACCESSED);
        log.setInstanceId(instanceId);
        DataFixAuditLog saved = auditLogRepo.save(log);
//        log.info("Tool access logged: userId={}, instanceId={}", userId, instanceId);
        return saved;
    }

    /**
     * Get all audit logs for a specific query
     */
    public List<DataFixAuditLog> getLogsForQuery(String queryId) {
        return auditLogRepo.findByQueryId(queryId);
    }

    /**
     * Get all audit logs for a specific user
     */
    public List<DataFixAuditLog> getLogsForUser(String userId) {
        return auditLogRepo.findByUserId(userId);
    }

    /**
     * Get all audit logs by action type
     */
    public List<DataFixAuditLog> getLogsByActionType(DataFixAuditAction actionType) {
        return auditLogRepo.findByActionType(actionType);
    }

    /**
     * Get all audit logs within a date range
     */
    public List<DataFixAuditLog> getLogsByDateRange(Date startDate, Date endDate) {
        return auditLogRepo.findByTimestampBetween(startDate, endDate);
    }

    /**
     * Get all audit logs for an instance
     */
    public List<DataFixAuditLog> getLogsForInstance(String instanceId) {
        return auditLogRepo.findByInstanceId(instanceId);
    }

    /**
     * Get all failed audit logs
     */
    public List<DataFixAuditLog> getAllFailedLogs() {
        return auditLogRepo.findAllFailed();
    }

    /**
     * Get paginated audit logs
     */
    public Page<DataFixAuditLog> getLogsPaginated(Pageable pageable) {
        return auditLogRepo.findAll(pageable);
    }

    /**
     * Get audit logs for user within date range with pagination
     */
    public Page<DataFixAuditLog> getLogsForUserByDateRange(String userId, Date startDate, Date endDate, Pageable pageable) {
        return auditLogRepo.findByUserIdAndTimestampBetween(userId, startDate, endDate, pageable);
    }
}
