package com.syncari.core.model;

import com.syncari.core.datatype.Datatype;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
@EqualsAndHashCode
public class FunctionConfiguration implements Serializable {
    private String name;
    private String label;
    private String helpSummary;
    private String llmHint;
    private Datatype datatype;
    private Object defaultValue;
    private String helpText;
    //used for composite configurations
    List<FunctionConfiguration> configuration = new ArrayList<>();
    private Map<String, Object> additionalProperties = new HashMap<>();
    private boolean required;
    private boolean isMultiValuedVariable;
    private boolean readOnly = false;
}

