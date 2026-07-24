package com.syncari.api.rest.controllers.data.insights;

import com.syncari.core.model.insights.QField;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DatasetFieldDTO {

    String apiName;
    String displayName;
    String dataType;
    String datasetId;
    String datastoreName;
    QField.Type datasetType; // TODO: Check we can change it to DatasourceType
    String fieldId;
    String alias;
    String datasourceAlias;
}
