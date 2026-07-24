package com.syncari.analytics.service.data;

import lombok.Data;

@Data
public class MetricOverTime {
	String connectorName;
	long time;
	long count;
	boolean byHour;
	String timeString;
	String entityName;
	
	public MetricOverTime () {}
	
	public MetricOverTime(String connectorName, long time, long count, boolean byHour) {
		this.connectorName = connectorName;
		this.time = time;
		this.count = count;
		this.byHour = byHour;
	}
}
