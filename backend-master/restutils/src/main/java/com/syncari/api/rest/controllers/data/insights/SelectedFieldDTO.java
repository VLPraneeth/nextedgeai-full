package com.syncari.api.rest.controllers.data.insights;

import com.syncari.core.model.insights.QField;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SelectedFieldDTO {

    String apiName;
    String displayName;
    String dataType;
    String datasetId;
    QField.Type datasetType;
    String alias;
    String datasourceAlias;
}
