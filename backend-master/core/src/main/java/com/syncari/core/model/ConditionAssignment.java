package com.syncari.core.model;


import java.util.ArrayList;
import java.util.List;

import com.syncari.core.model.RuleDefinition.Impact;
import com.syncari.core.model.RuleDefinition.RuleType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode
public class ConditionAssignment implements SyncariComparable<ConditionAssignment> {
    public String name;
    public String ruleName;
    public boolean conditionMatches;
    public Impact impact;
    public RuleType type;
    private List<String> conditionValues = new ArrayList<String>();
}

