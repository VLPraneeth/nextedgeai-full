package com.syncari.analytics.service.data;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApiUsage {
	private String synapseName;
	private String operation;
	private String entityName;
	private long timeTaken;
	private String occurredDate;
}
