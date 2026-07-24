package com.syncari.connector.data;

import com.syncari.connector.ConnectorInfo;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DeleteObjectRequest {
	String entityName;
	String datastoreName;
	private ConnectorInfo connector;

	public DeleteObjectRequest(ConnectorInfo connector, String entityName, String datastoreName) {
		this.connector = connector;
		this.entityName = entityName;
		this.datastoreName = datastoreName;
	}

}
