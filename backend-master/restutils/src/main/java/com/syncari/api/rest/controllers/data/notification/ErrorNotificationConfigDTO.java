package com.syncari.api.rest.controllers.data.notification;

import java.util.Date;
import java.util.List;
import java.util.Map;

import com.syncari.core.model.ErrorNotificationFrequency;
import com.syncari.core.model.misc.ErrorNotificationChannelType;
import com.syncari.core.model.misc.ErrorNotificationConfigStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class ErrorNotificationConfigDTO {

	private String id;
	private ErrorNotificationChannelType type;
	private String name;
	private String description;
	private ErrorNotificationConfigStatus status;
	private String statusMessage;
	private Date firstErrorOccured;
	private Date lastErrorOccured;
	private Integer retries;
	private List<String> notificationTypes;
	private ErrorNotificationFrequency cadence;
	private Map<String, Object> configuration;

	public ErrorNotificationConfigDTO() {
	}


}
