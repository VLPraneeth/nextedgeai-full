package com.syncari.core.model.misc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import lombok.Data;

@Data
public class SyncStudioPreference {
    private Map<String, Set<String>> filterSelections = new LinkedHashMap<String, Set<String>>();
    private Map<String, Set<String>> hiddenFields = new LinkedHashMap<String, Set<String>>();
    private Map<String, ArrayList<Number>> pipelineViewports = new LinkedHashMap<String, ArrayList<Number>>();
}
