package com.syncari.analytics.service.data;

import lombok.Data;

@Data
public class DataMetrics {
	private String fieldName;
	private long uniqueValues;
	private long min;
	private long max;
	private long missingValues;
	private String mostFrequent;
}
