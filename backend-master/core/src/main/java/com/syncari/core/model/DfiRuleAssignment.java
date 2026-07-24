package com.syncari.core.model;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;

import com.syncari.core.model.misc.DraftableModel;
import com.syncari.core.model.util.Status;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DfiRuleAssignment extends DraftableModel<DfiRuleAssignment> {
    public String entityId;
    public String entityApiName;
    public boolean initializing;
    public Set<RuleAssignment> rules = new TreeSet<>();

    String processorId;
    Instant checkin;
    Status status;
    String errorMsg;

    @Override
    public DfiRuleAssignment makeCopy() {
        return new DfiRuleAssignment().setEntityId(entityId).setEntityApiName(entityApiName)
            .setInitializing(false).setRules(rules).setStatus(status);
    }

    @Override
    public void copyValuesFrom(DfiRuleAssignment other) {
        setEntityId(other.getEntityId()).setEntityApiName(other.getEntityApiName())
            .setInitializing(other.isInitializing()).setRules(other.getRules()).setStatus(other.getStatus());
    }
}

