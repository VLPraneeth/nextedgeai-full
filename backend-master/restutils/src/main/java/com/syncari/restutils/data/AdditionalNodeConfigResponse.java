package com.syncari.restutils.data;

import com.syncari.utils.KeyValue;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Accessors(chain = true)
@Getter
@Setter
public class AdditionalNodeConfigResponse {
    private String currentNodeId;
    private String configName;

    private Map<String, Object> currentConfiguration = new HashMap<>();
    private List<KeyValue> additionalConfigs = new ArrayList<>();

    public AdditionalNodeConfigResponse addConfig(KeyValue config) {
        additionalConfigs.add(config);
        return this;
    }

}
