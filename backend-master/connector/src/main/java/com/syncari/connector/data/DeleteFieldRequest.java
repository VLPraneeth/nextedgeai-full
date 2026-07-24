package com.syncari.connector.data;

import com.syncari.connector.ConnectorInfo;

import lombok.Data;

@Data
public class DeleteFieldRequest {
	String entityName;
	String fieldName;
	String externalFieldId;
	private ConnectorInfo connector;

	public DeleteFieldRequest(ConnectorInfo connector, String entityName, String fieldName) {
		this.connector = connector;
		this.entityName = entityName;
		this.fieldName = fieldName;
	}

}
