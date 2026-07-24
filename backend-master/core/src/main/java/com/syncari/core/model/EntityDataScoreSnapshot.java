package com.syncari.core.model;

import java.time.Instant;

import javax.validation.constraints.NotNull;

import com.syncari.utils.DateUtil;

import lombok.Data;

@Data
public class EntityDataScoreSnapshot extends UUIDAuditModel {
    @NotNull(message = "entityDefId is required")
	private String entityDefId;
	private int score;
	private int sourceScore;
	@NotNull(message = "computedOn is required")
	private Instant computedOn;
	private String computedDay;
	
	public EntityDataScoreSnapshot() {}
	
    public EntityDataScoreSnapshot(String entityDefId, int score, int sourceScore, Instant computedOn) {
        this.entityDefId = entityDefId;
        this.score = score;
        this.sourceScore = sourceScore;
        this.computedOn = computedOn;
        setComputedDay(computedOn);
    }
	
	public void setComputedOn(Instant computedOn) {
	    this.computedOn = computedOn;
	    setComputedDay(computedOn);
	}

    private void setComputedDay(Instant computedOn) {
        computedDay = new DateUtil().formatDate(computedOn,DateUtil.dateOnlyFormat2);
    }

}
