package com.syncari.api.rest.controllers.data.insights;

import com.syncari.core.datatype.Datatype;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class VariableDTO {
    private String apiName;
    private String displayName;
    private String datatype;
    private String helpText;
    private String datasetId;
    private boolean isRequired;
    private boolean updatable;
    private boolean isMultiValueField;
    private VariableValueDTO variableDefaultValue;
}

