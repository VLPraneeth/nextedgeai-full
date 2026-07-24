package com.syncari.api.rest.controllers.data.insights;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DatasetGroupByTimeGrainOptionsDTO {
    private String name;
    private String displayName;
    private String description;
}
