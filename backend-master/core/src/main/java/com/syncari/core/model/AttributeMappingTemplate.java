package com.syncari.core.model;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AttributeMappingTemplate {
    String syncariEntityName;
    String externalEntityName;
    String syncariAttributeName;
    String externalAttributeName;
    String connectorMetadataId;
}
