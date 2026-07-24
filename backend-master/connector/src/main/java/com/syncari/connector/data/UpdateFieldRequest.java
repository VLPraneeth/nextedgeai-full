package com.syncari.connector.data;

import com.syncari.connector.ConnectorInfo;

import lombok.Data;

@Data
public class UpdateFieldRequest {
	String entityName;
	AttributeSchema schema;
	String oldName;
	String newName;
	private ConnectorInfo connector;

	public UpdateFieldRequest(String entityName, ConnectorInfo connector, AttributeSchema schema) {
		this.entityName = entityName;
		this.connector = connector;
		this.schema = schema;
	}

}
