package com.syncari.api.rest.controllers.data.insights;

import com.syncari.core.model.insights.dataset.VariableValue;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

@Data
@Accessors(chain = true)
public class VariableValueDTO {
    String datasetName;
    Object defaultValue;
    Map<String, Object> additionalParamForDefaultVal;
    VariableValue.VariableType defaultValueType = VariableValue.VariableType.LITERAL;
    String datasetId;
    String datatype = "text";
}
