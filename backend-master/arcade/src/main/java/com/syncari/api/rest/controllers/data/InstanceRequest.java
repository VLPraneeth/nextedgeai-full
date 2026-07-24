package com.syncari.api.rest.controllers.data;

import com.syncari.core.model.misc.InstanceType;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InstanceRequest {
	private String orgId;
	private String syncariId;
	private String instanceName;
	private String displayName;
	private InstanceType type;
	private String planName;
	
	public InstanceRequest() {}

	public InstanceRequest(String orgId, String instanceName, String displayName, InstanceType type, String planName) {
		this.orgId = orgId;
		this.instanceName = instanceName;
		this.displayName = displayName;
		this.type = type;
		this.planName = planName;
	}
}
