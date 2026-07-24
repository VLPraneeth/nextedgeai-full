package com.syncari.analytics.service.data;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SchemaReport {
	private String entityName;
	private int fieldCount;
	private int mappedFieldCount;
	private long totalRecords;
}
