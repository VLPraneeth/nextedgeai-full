package com.syncari.analytics.model;

import javax.validation.constraints.NotNull;

import org.springframework.data.mongodb.core.mapping.Document;

import com.syncari.core.model.UUIDAuditModel;

import lombok.Data;

@Data
@Document
public class QueryCache extends UUIDAuditModel {
	@NotNull(message = "Key is required")
	String key;
	@NotNull(message = "Value is required")
	Object value;
	
	public QueryCache() {}
	
	public QueryCache(String key, Object value) {
		this.key = key;
		this.value = value;
	}
}
