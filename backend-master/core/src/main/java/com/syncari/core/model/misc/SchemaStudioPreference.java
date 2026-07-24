package com.syncari.core.model.misc;

import java.util.LinkedHashSet;
import java.util.Map;
import lombok.Data;

@Data
public class SchemaStudioPreference {
    @Deprecated
    private LinkedHashSet<String> entityColumns = new LinkedHashSet<String>();
    @Deprecated
    private LinkedHashSet<String> fieldColumns = new LinkedHashSet<String>();
    private LinkedHashSet<Map<String, Object>> allEntityColumns = new LinkedHashSet<>();
    private LinkedHashSet<Map<String, Object>> allFieldColumns = new LinkedHashSet<>();
}
