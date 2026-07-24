package com.syncari.core.model;

import com.syncari.core.model.misc.DataFixQueryStatus;
import com.syncari.core.model.misc.DataFixQueryType;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.validation.constraints.NotNull;
import java.util.Date;
import java.util.Map;

/**
 * Represents a data fix query request with approval workflow
 */
@Data
@Document
@Accessors(chain = true)
public class DataFixQuery extends UUIDAuditModel {

    @NotNull(message = "Query text is required")
    private String queryText;

    @NotNull(message = "Query type is required")
    private DataFixQueryType queryType; // READ, UPDATE, DELETE, INSERT

    @NotNull(message = "Status is required")
    private DataFixQueryStatus status; // DRAFT, PENDING_APPROVAL, APPROVED, REJECTED, EXECUTED, FAILED

    @NotNull(message = "Justification is required")
    private String justification; // Ticket link or explanation

    private String requesterId; // User who created the query

    private String requesterEmail;

    private String approverId; // User who can approve

    private String approverEmail;

    private String executorId; // User who executed the query

    private String executorEmail;

    private Date submittedAt; // When request was submitted for approval

    private Date approvedAt; // When request was approved

    private Date rejectedAt; // When request was rejected

    private Date executedAt; // When query was executed

    private String rejectionReason; // Reason for rejection

    private String approvalNote; // Optional note from approver

    // Dry run results
    private Map<String, Object> dryRunResult; // Preview data

    private Integer affectedRowCount; // Number of rows affected in dry run

    // Execution results
    private Map<String, Object> executionResult; // Actual execution data

    private Integer actualAffectedRowCount; // Actual rows affected

    private String errorMessage; // Error message if execution failed

    private String targetDatabase; // Database name

    private String targetCollection; // Collection/table name

    private String instanceId; // Customer instance ID

    public DataFixQuery() {
        this.status = DataFixQueryStatus.DRAFT;
    }

    public DataFixQuery(String queryText, DataFixQueryType queryType, String justification) {
        this();
        this.queryText = queryText;
        this.queryType = queryType;
        this.justification = justification;
    }
}
