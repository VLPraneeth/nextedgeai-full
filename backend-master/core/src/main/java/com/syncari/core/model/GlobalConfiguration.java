package com.syncari.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GlobalConfiguration extends UUIDAuditModel {

	public static final String SYNC_INTERVAL_SECONDS="syncIntervalSeconds";
	String key;
	Object value;

	public GlobalConfiguration() {
	}

	public <T> T cast(){
		return (T)value;
	}

}
