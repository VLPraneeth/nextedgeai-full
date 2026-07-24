package com.syncari.api.rest.controllers.data.insights;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class DatasetFunctionDTO {

    private String name;
    private String displayName;
    private String description;
    private String dataType;
    private boolean aggregate;
    private List<String> functionInputDataTypes;
}
