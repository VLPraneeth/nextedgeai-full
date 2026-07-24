package com.syncari.core.model;

import java.util.HashMap;
import java.util.Map;

import com.syncari.core.datatype.Datatype;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class RuleConfiguration {
    private String name;
    private String label;
    private String description;
    private String helpSummary;
    private Datatype datatype;
    private String helpText;
    private Map<String, Object> additionalProperties = new HashMap<>();
    private boolean required;
}

