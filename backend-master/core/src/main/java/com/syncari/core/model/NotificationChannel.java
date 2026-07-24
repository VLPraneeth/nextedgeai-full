package com.syncari.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Deprecated
@Data
@AllArgsConstructor
@Builder
public class NotificationChannel extends UUIDAuditModel {

	private String type;
	private String label;
	private String configurationType;

	public NotificationChannel() {
	}


}
