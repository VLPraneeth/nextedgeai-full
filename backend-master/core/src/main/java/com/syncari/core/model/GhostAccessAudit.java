package com.syncari.core.model;

import java.time.Instant;

import javax.validation.constraints.NotNull;

import com.syncari.core.model.util.Status;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class GhostAccessAudit extends UUIDAuditModel {
	@NotNull(message = "RequesterId is required")
	private String requesterId;
	@NotNull(message = "RequesterEmail is required")
	private String requesterEmail;
	@NotNull(message = "ApproverId is required")
	private String approverId;
	@NotNull(message = "ApproverEmail is required")
	private String approverEmail;
	@NotNull(message = "SyncariId is required")
	private String syncariId;
	@NotNull(message = "RoleName is required")
	private String roleName;
	private Instant requestedAt;
	private Instant approvedAt;
	private Instant expireAt;
	private String reason;
	private String accessDetails;
	private Status status;
	private String auditTrail = "";
}
