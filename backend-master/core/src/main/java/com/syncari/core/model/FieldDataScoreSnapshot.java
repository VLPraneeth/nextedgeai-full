package com.syncari.core.model;

import java.time.Instant;

import javax.validation.constraints.NotNull;

import com.syncari.utils.DateUtil;

import lombok.Data;

/**
 * Stores the snapshot of average score at field-rule level for every entity
 *
 */
@Data
public class FieldDataScoreSnapshot extends UUIDAuditModel {
    @NotNull(message = "entityDefId is required")
	private String entityDefId;
    @NotNull(message = "fieldName is required")
	private String fieldName;
    @NotNull(message = "ruleId is required")
	private String ruleId;
    @NotNull(message = "ruleName is required")
	private String ruleName;
	private String conditionName;
	private Integer averageScore;
	@NotNull(message = "computedOn is required")
	private Instant computedOn;
	private String computedDay;
	
    public FieldDataScoreSnapshot(String entityDefId, String fieldName, String ruleId, String ruleName, String conditionName, Integer averageScore,
            Instant computedOn) {
        this.entityDefId = entityDefId;
        this.fieldName = fieldName;
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.conditionName = conditionName;
        this.averageScore = averageScore;
        this.computedOn = computedOn;
        setComputedDay(computedOn);
    }
    
    public void setComputedOn(Instant computedOn) {
        this.computedOn = computedOn;
        setComputedDay(computedOn);
    }

    private void setComputedDay(Instant computedOn) {
        computedDay = new DateUtil().formatDate(computedOn, DateUtil.dateOnlyFormat2);
    }

}
