package com.syncari.core.model.misc;

/**
 * Types of actions that can be audited in the data fix tool
 */
public enum DataFixAuditAction {
    QUERY_SUBMITTED,    // Query submitted for approval
    QUERY_APPROVED,     // Query approved by approver
    QUERY_REJECTED,     // Query rejected by approver
    QUERY_EXECUTED,     // Query executed successfully or with failure
    DRY_RUN_EXECUTED,   // Dry run executed to preview changes
    USER_LOGIN,         // User logged into data fix tool
    TOOL_ACCESSED       // Data fix tool page accessed
}
