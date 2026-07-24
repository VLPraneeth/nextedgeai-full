package com.syncari.api.rest.controllers.data.insights;

import com.syncari.core.model.insights.AggFunctions;
import lombok.Data;

import java.util.List;

@Data
public class ProjectionDTO {

    String aliasName;
    List<DatasetFieldDTO> datasetFields;
    List<ProjectionDTO> innerProjections;
    AggFunctions aggFunctions;
    String dataType;
    String apiName;
}
