package com.syncari.api.rest.controllers.data;

import java.util.List;
import java.util.Date;
import com.syncari.core.model.misc.OrganizationType;
import com.syncari.core.model.util.Status;
import com.syncari.restutils.data.InstanceResponse;
import lombok.Data;

@Data
public class Organization {
	private String id;
	private String name;
	private List<InstanceResponse> instances;
	private List<UserResponse> users;
	private List<String> errorMessage;
	private OrganizationType type;
	private String createdBy;
	private Date createdAt;
	private String deletedBy;
	private Date deletedAt;
	private Status status;
	private String maxNumberOfInstances;
	private String insightsProviderOrgId;
}
