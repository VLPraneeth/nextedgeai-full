package com.syncari.api.rest.controllers.data.insights;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DatasetSampleDataDTO {

    private String columnDisplayName;
    private Object value;
}
