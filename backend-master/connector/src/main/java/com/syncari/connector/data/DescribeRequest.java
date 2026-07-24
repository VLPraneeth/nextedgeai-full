package com.syncari.connector.data;

import com.syncari.connector.ConnectorInfo;

import lombok.Data;

import java.util.Optional;

@Data
public class DescribeRequest {
	String entity;
	private ConnectorInfo connector;
	boolean forceRefresh;
	Optional<EntitySchema> existingSchema;

	public DescribeRequest(ConnectorInfo connector, String entity) {
		this(connector, entity,false);
	}
	public DescribeRequest(ConnectorInfo connector, String entity, boolean forceRefresh) {
		this.connector = connector;
		this.entity = entity;
		this.forceRefresh = forceRefresh;
	}

	public DescribeRequest(ConnectorInfo connector, String entity, Optional<EntitySchema> existingSchema) {
		this.connector = connector;
		this.entity = entity;
		this.existingSchema = existingSchema;
	}

}
