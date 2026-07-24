package com.syncari.core.model;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain=true)
public class SchemaMapping extends UUIDAuditModel {
    String syncariId;
    String synapseObjectId;
    String connectorId;
    String scope; 
}
