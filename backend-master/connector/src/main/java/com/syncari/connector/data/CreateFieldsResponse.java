package com.syncari.connector.data;

import com.syncari.connector.ConnectorInfo;
import lombok.Data;

import java.util.List;

@Data
public class CreateFieldsResponse {
    String entityName;
    private ConnectorInfo connector;
    List<AttributeSchema> schemas;

    public CreateFieldsResponse(String entityName, ConnectorInfo connector, List<AttributeSchema> schemas) {
        this.entityName = entityName;
        this.connector = connector;
        this.schemas = schemas;
    }
}
