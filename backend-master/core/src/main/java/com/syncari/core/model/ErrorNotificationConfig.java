package com.syncari.core.model;

import java.util.Date;
import java.util.List;
import java.util.Map;

import com.syncari.core.model.misc.ErrorNotificationChannelType;
import com.syncari.core.model.misc.ErrorNotificationConfigStatus;

import lombok.Data;
import lombok.experimental.Accessors;


@Data
@Accessors(chain = true)
public abstract class ErrorNotificationConfig extends UUIDAuditModel {
	private String name;
	private String description;
	private ErrorNotificationConfigStatus status;
	private List<String> notificationTypes;
	private ErrorNotificationFrequency cadence;
	private Date lastNotificationTimestamp;
	private String lastError;
	private Date firstErrorTimestamp;
	private Date lastErrorTimestamp;
	private Integer retries;
	private boolean processing;
	
	public  abstract ErrorNotificationChannelType getType();
	public abstract void loadConfig(Map<String, Object> config);
	public abstract Map<String, Object> getConfig();
	public abstract void validate();
	public void copyFrom(ErrorNotificationConfig config) {
		this.setId(config.getId());
		this.setCadence(config.getCadence());
		this.setDescription(config.getDescription());
		this.setName(config.getName());
		this.setNotificationTypes(config.getNotificationTypes());
		this.setStatus(config.getStatus());
		this.loadConfig(config.getConfig());
	}
}

