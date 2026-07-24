package com.syncari.core.model.misc;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Source {
    private String connectorId;
    private String entityDefinitionId;
    private String connectorName;
    private String externalId;
    private long lastModified;
}