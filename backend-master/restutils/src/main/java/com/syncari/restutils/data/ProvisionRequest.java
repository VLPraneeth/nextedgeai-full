package com.syncari.restutils.data;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProvisionRequest {
	private String organizationName;
	private String instanceName;
	private String instanceType;
	private String orgType;
	private String instanceDisplayName;
	private String adminUserName;
	private String planName;
	private String adminFirstName;
	private String adminLastName;
	private String maxInstance;

	public ProvisionRequest() {}
}
