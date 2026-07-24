package com.syncari.core.model.misc;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode
public class ExternalValue {
    private String fieldId;
    private String apiName;
    private String displayName;
    private String dataType;
    private String connectorId;
    private String connectorName;
    private Object value;
}
