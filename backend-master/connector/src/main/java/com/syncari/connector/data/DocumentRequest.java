package com.syncari.connector.data;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DocumentRequest {
    private final String entityName;
    private final EntityData fileMetadata;
    private final ConnectorInfo connector;
    private final EntitySchema entitySchema;

    public DocumentRequest(ConnectorInfo connector, EntitySchema entitySchema, EntityData fileMetadata) {
        this.connector = connector;
        this.entitySchema = entitySchema;
        this.entityName = entitySchema.apiName;
        this.fileMetadata = fileMetadata;
    }
}
