package com.syncari.connector.data;

import com.syncari.connector.ConnectorInfo;

import lombok.Data;

@Data
public class CreateFieldRequest {
	String entityName;
	AttributeSchema schema;
	private ConnectorInfo connector;

	public CreateFieldRequest(String entityName, ConnectorInfo connector, AttributeSchema schema) {
		this.entityName = entityName;
		this.connector = connector;
		this.schema = schema;
	}

}
