package com.syncari.connector.data;

import com.syncari.connector.ConnectorInfo;

import lombok.Data;

@Data
public class CreateObjectRequest {
	EntitySchema schema;
	private ConnectorInfo connector;

	public CreateObjectRequest(ConnectorInfo connector, EntitySchema schema) {
		this.connector = connector;
		this.schema = schema;
	}

}
