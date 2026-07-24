package com.syncari.api.rest.controllers.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DataFixApprovalRequest {

    private String approvalNote; // Required when approving (validated in service)

    private String rejectionReason; // Required when rejecting (validated in service)
}
