package com.syncari.core.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class DedupeConfig implements Serializable {
    private List<String> dedupeFields=new ArrayList<>();
    private WinnerStrategy winnerStrategy=WinnerStrategy.DO_NOTHING;
    private String selectedConnectorId;
    private MergeStrategy mergeStrategy=MergeStrategy.INTELLIGENT_MERGE;
    private boolean enableDeduplicate=false;
    public static DedupeConfig doNothing(){
        return new DedupeConfig();
    }
    public Map<String, Object> getConfigMap(){
        var config =new HashMap<String, Object>();
        config.put("enableDeduplicate", enableDeduplicate);
        if(enableDeduplicate) {
            config.put("dedupeFields", dedupeFields);
            config.put("winnerStrategy", winnerStrategy.name());
            config.put("selectedConnectorId", selectedConnectorId);
            config.put("mergeStrategy", mergeStrategy.name());
        }
        return config;
    }
}

