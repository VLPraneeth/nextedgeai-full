package com.syncari.connector.data;

import com.syncari.connector.ConnectorInfo;
import lombok.Data;

import java.util.List;

@Data
public class CreateFieldsRequest {
    String entityName;
    List<AttributeSchema> schemas;
    private ConnectorInfo connector;

    public CreateFieldsRequest(String entityName, ConnectorInfo connector, List<AttributeSchema> schemas) {
        this.entityName = entityName;
        this.connector = connector;
        this.schemas = schemas;
    }
}
