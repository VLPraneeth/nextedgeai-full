package com.syncari.connector.data;

import com.syncari.connector.ConnectorInfo;

import lombok.Data;

@Data
public class UpdateObjectRequest {
    EntitySchema schema;
    String oldName;
    String newName;
    private ConnectorInfo connector;

    public UpdateObjectRequest(ConnectorInfo connector, EntitySchema schema) {
        this.connector = connector;
        this.schema = schema;
    }

}
