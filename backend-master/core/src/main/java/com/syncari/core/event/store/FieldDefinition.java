package com.syncari.core.event.store;

import com.google.cloud.bigquery.StandardSQLTypeName;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode
@ToString
public class FieldDefinition {
	public final String syncariId;
	public final String tableName;
	public final String fieldName;
	public final boolean required;
	public final StandardSQLTypeName type;

	public FieldDefinition(String syncariId, String tableName, String fieldName, StandardSQLTypeName type, boolean required){
		this.syncariId = syncariId;
		this.tableName = tableName;
		this.fieldName = fieldName;
		this.type = type;
		this.required = required;
	}
}
