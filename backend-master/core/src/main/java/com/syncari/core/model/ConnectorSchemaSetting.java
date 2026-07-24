package com.syncari.core.model;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

@Data
public class ConnectorSchemaSetting extends  UUIDAuditModel {
    @NotNull(message = "Connector id is required")
    private String fromConnectorId;

    private String toConnectorId;
    
    @NotNull(message = "External Source Entity is required")
    private String fromEntityId;

    @NotNull(message = "Syncari Entity is required")
    private String syncariEntityId;

    private List<String> toEntityIds = new ArrayList<>();

    private boolean autoPublish;

}
