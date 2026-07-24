package com.syncari.core.model;

import lombok.Getter;

@Getter
public enum ErrorNotificationFrequency {
	IMMEDIATE ("Real-time — as events occur"), HOURLY ("Hourly — every hour from now"), DAILY("Daily — every 24 hours from now"), WEEKLY("Weekly — every 7 days from now"), MONTHLY("Monthly — same day each month from now");
	
	private String label;
	
	ErrorNotificationFrequency(String label) {
		this.label = label;
	}
}
