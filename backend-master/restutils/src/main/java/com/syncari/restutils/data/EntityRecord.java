package com.syncari.restutils.data;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class EntityRecord {
    private long syncariTimestamp;
    private long lastModified;
    private String syncariId;
    private Map<String, Object> values = new HashMap<>();
    private Map<String, Object> idMapping = new LinkedHashMap<>();
    private boolean isDeleted;
}
