package com.syncari.restutils.data;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;

@Accessors(chain = true)
@Getter
@Setter

public class AdditionalNodeConfigRequest {
    private String currentNodeId;
    private String configName;
    private MappingGraphDTO graph;
    private Map<String, Object> currentConfiguration = new HashMap<>();
    private Map<String, String> additionalConfigParams = new HashMap<>();

    public String getConfigLoaderType() {
        return additionalConfigParams.get("configLoaderType");
    }

    public AdditionalNodeConfigResponse createBaseResponse() {
        return new AdditionalNodeConfigResponse()
                .setConfigName(configName)
                .setCurrentNodeId(currentNodeId)
                .setCurrentConfiguration(currentConfiguration);
    }

}
