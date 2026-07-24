package com.syncari.core.model.misc;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Destination {
    private String connectorId;
    private String connectorName;
    private String externalId;
    private String details;
    private boolean isSkipped;
    private boolean isError;
}