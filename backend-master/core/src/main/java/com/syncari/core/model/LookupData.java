package com.syncari.core.model;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;

@ToString
@Data
@Accessors(chain = true)
public class LookupData {

    private String lookupEntityName;
    private Map<String, Object> values = new HashMap<>();

    public Object getValue(String key) {
        return this.values.get(key);
    }

    public LookupData addValue(String key, Object value) {
        this.values.put(key, value);
        return this;
    }

    public String getValueAsString(String key) {
        return this.values.get(key) == null ? null : this.getValue(key).toString();
    }

}
