package com.syncari.api.rest.controllers.data;

import lombok.Data;

import java.util.Map;

@Data
public class ComponentDataRequest {

    String componentName;
    String componentType;
    String configName;
    String configType;
    Map<String, Object> inputs;
}
