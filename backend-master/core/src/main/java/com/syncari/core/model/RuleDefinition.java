package com.syncari.core.model;

import java.util.HashMap;
import java.util.Map;

import com.syncari.core.model.util.Scope;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class RuleDefinition extends UUIDAuditModel {
    private String name;
    private String label;
    private String description;
    private int weight;
    private Scope scope;
    private boolean seeded;
    private RuleType type;
    private Impact defaultImpact;

    public enum Impact {
        LOW,
        MEDIUM,
        HIGH
    }

    public enum RuleType {
        BOOLEAN,
        STRING,
        INTEGER,
        INT_RANGE,
        DATE_RANGE,
        REGEX,
        LOOKUP,
        FILTER_CONDITION
    }

    public static final Map<Impact, Integer> weightByImpact = new HashMap<>();
    static {
        weightByImpact.put(Impact.HIGH, 0);
        weightByImpact.put(Impact.MEDIUM, 40);
        weightByImpact.put(Impact.LOW, 70);
    }

    public boolean matchesNameAndScope(String name, Scope scope) {
        return name.equalsIgnoreCase(this.name) && scope == this.scope;
    }
}
