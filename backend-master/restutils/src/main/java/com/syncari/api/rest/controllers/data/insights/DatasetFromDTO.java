package com.syncari.api.rest.controllers.data.insights;

import com.syncari.core.insights.DatasourceType;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DatasetFromDTO {
    String apiName;
    String displayName;
    String datasetId;
    String description;
    String alias;
    DatasourceType datasetType;
}
