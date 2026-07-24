package com.syncari.core.model.misc;

import lombok.Data;
import lombok.experimental.Accessors;


@Data
@Accessors(chain = true)
public class ExternalFieldChange {
    String apiName;
    private Object oldValue;
    private Object newValue;
}
