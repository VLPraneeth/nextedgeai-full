package com.syncari.api.rest.controllers.data;

import com.syncari.core.model.misc.DataFixQueryStatus;
import com.syncari.core.model.misc.DataFixQueryType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DataFixQueryResponse {

    private String id;
    private String queryText;
    private DataFixQueryType queryType;
    private DataFixQueryStatus status;
    private String justification;

    private String requesterId;
    private String requesterEmail;
    private String approverId;
    private String approverEmail;
    private String executorId;
    private String executorEmail;

    private Date submittedAt;
    private Date approvedAt;
    private Date rejectedAt;
    private Date executedAt;

    private String rejectionReason;
    private String approvalNote;

    private Map<String, Object> dryRunResult;
    private Integer affectedRowCount;

    private Map<String, Object> executionResult;
    private Integer actualAffectedRowCount;

    private String errorMessage;

    private String targetDatabase;
    private String targetCollection;
    private String instanceId;

    private Date createdAt;
    private Date updatedAt;
    private String createdBy;
    private String updatedBy;
}
