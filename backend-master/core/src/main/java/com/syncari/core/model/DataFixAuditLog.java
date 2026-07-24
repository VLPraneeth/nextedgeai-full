package com.syncari.core.model;

import com.syncari.core.model.misc.DataFixAuditAction;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.validation.constraints.NotNull;
import java.util.Date;
import java.util.Map;

/**
 * Immutable audit log for all data fix tool activities
 * Retained for 3 years for SOC compliance
 */
@Data
@Document
@Accessors(chain = true)
public class DataFixAuditLog extends UUIDAuditModel {

    @NotNull(message = "Timestamp is required")
    @Indexed(expireAfterSeconds = 94608000) // 3 years in seconds (3 * 365 * 24 * 60 * 60)
    private Date timestamp;

    @NotNull(message = "User ID is required")
    private String userId;

    @NotNull(message = "User email is required")
    private String userEmail;

    @NotNull(message = "Action type is required")
    private DataFixAuditAction actionType; // QUERY_SUBMITTED, QUERY_APPROVED, QUERY_REJECTED, QUERY_EXECUTED, USER_LOGIN, etc.

    private String queryId; // Reference to DataFixQuery if applicable

    private String queryText; // Full SQL/MongoDB query text (sanitized)

    private String targetDatabase; // Database targeted

    private String targetCollection; // Collection/table targeted

    private Integer affectedRows; // Number of rows modified/returned

    private String status; // SUCCESS or FAILURE

    private String failureReason; // Reason for failure if applicable

    private String justification; // Original justification/ticket link

    private String instanceId; // Customer instance ID

    private Map<String, Object> additionalDetails; // Any extra context

    private String ipAddress; // User's IP address

    private String userAgent; // User's browser/client info

    public DataFixAuditLog() {
        this.timestamp = new Date();
    }

    public DataFixAuditLog(String userId, String userEmail, DataFixAuditAction actionType) {
        this();
        this.userId = userId;
        this.userEmail = userEmail;
        this.actionType = actionType;
        this.status = "SUCCESS";
    }

    /**
     * Create an audit log entry for query execution
     */
    public static DataFixAuditLog forQueryExecution(DataFixQuery query, String userId, String userEmail, boolean success, String failureReason) {
        DataFixAuditLog log = new DataFixAuditLog(userId, userEmail, DataFixAuditAction.QUERY_EXECUTED);
        log.setQueryId(query.getId());
        log.setQueryText(query.getQueryText());
        log.setTargetDatabase(query.getTargetDatabase());
        log.setTargetCollection(query.getTargetCollection());
        log.setAffectedRows(query.getActualAffectedRowCount());
        log.setStatus(success ? "SUCCESS" : "FAILURE");
        log.setFailureReason(failureReason);
        log.setJustification(query.getJustification());
        log.setInstanceId(query.getInstanceId());
        return log;
    }

    /**
     * Create an audit log entry for query approval
     */
    public static DataFixAuditLog forQueryApproval(DataFixQuery query, String approverId, String approverEmail, boolean approved, String reason) {
        DataFixAuditAction action = approved ? DataFixAuditAction.QUERY_APPROVED : DataFixAuditAction.QUERY_REJECTED;
        DataFixAuditLog log = new DataFixAuditLog(approverId, approverEmail, action);
        log.setQueryId(query.getId());
        log.setQueryText(query.getQueryText());
        log.setJustification(query.getJustification());
        log.setFailureReason(reason);
        log.setInstanceId(query.getInstanceId());
        return log;
    }

    /**
     * Create an audit log entry for query submission
     */
    public static DataFixAuditLog forQuerySubmission(DataFixQuery query, String requesterId, String requesterEmail) {
        DataFixAuditLog log = new DataFixAuditLog(requesterId, requesterEmail, DataFixAuditAction.QUERY_SUBMITTED);
        log.setQueryId(query.getId());
        log.setQueryText(query.getQueryText());
        log.setTargetDatabase(query.getTargetDatabase());
        log.setTargetCollection(query.getTargetCollection());
        log.setJustification(query.getJustification());
        log.setInstanceId(query.getInstanceId());
        return log;
    }
}
