package com.syncari.core.model.misc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.Data;

@Data
public class ErrorChannelConfiguration {
    private String type;
    private boolean active;
    private Map<String, Set<String>> configuration = new LinkedHashMap<String, Set<String>>();
}
