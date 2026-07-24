package com.syncari.core.model;

import javax.validation.constraints.NotNull;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Cluster extends UUIDAuditModel {
	private ResourceType type;
	@NotNull(message = "Cluster host is required")
	private String host;
	private Integer port;
	private String user;
	private String password;
	private String readOnlyUser;
	private String readOnlyPassword;
	private boolean hasSyncariDb;
	private boolean isProvisionActive;
	private boolean isTrial;

}
