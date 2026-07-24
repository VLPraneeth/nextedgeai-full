package com.syncari.core.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.data.annotation.Transient;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode
public class RuleAssignment extends UUIDAuditModel implements SyncariComparable<RuleAssignment> {
    public String name;
    public String entityApiName;
    public String fieldApiName;
    //RuleName to weight map
    @Transient
    @Deprecated
    private Map<String, Integer> rules = new LinkedHashMap<>();
    public boolean seeded;
    public String scope;
    public boolean modified;
    public boolean disabled;
    public Set<String> selectedFields = new TreeSet<>();
    public Set<ConditionAssignment> conditions = new TreeSet<>();
}

