package com.syncari.api.rest.controllers.data.insights;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
public class TimeGrainProjectionDTO {

    private DatasetConfigDTO datasetConfig;
    private DatasetFieldDTO datasetFieldForProjection;

}
