package com.syncari.core.model.misc;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import lombok.Data;

@Data
public class DataStudioPreference {
    private Set<String> filterIds = new LinkedHashSet<String>();
    @Deprecated
    private Map<String, Set<String>> selectedColumns = new LinkedHashMap<String, Set<String>>();
    private Map<String, Set<Map<String, Object>>> allColumns = new LinkedHashMap<>();
    int pageSize;
}
