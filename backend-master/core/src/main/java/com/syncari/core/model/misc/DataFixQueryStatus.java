package com.syncari.core.model.misc;

/**
 * Status of a data fix query request
 */
public enum DataFixQueryStatus {
    DRAFT,              // Initial state, not submitted
    PENDING_APPROVAL,   // Submitted for approval
    APPROVED,           // Approved by approver, ready to execute
    REJECTED,           // Rejected by approver
    EXECUTED,           // Successfully executed
    FAILED              // Execution failed
}
