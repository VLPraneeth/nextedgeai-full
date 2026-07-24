package com.syncari.core.model.misc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Data
@Accessors(chain = true)
@AllArgsConstructor
public class EntitySyncStatusMetric {

	private String connectorId;
	private String connectorName;
	private String connectorEntityName;
    private Instant lastProcessed;
	private Float duration;
	private Integer totalProcessedRecordsCount = 0;
	private Integer skippedCount = 0;
	private Integer readCount = 0;
	private Integer deletedCount = 0;
	private Integer createdCount = 0;
	private Integer mergedCount = 0;
	private Integer updatedCount = 0;
	private ChronoUnit durationUnit = ChronoUnit.MILLIS;

	public EntitySyncStatusMetric(){}

	public EntitySyncStatusMetric(String connectorId,String connectorName,String connectorEntityName,  Instant lastProcessed,
								  Float duration,Integer totalProcessedRecordsCount, Integer skippedCount, Integer readCount){
		this.connectorId  = connectorId;
		this.connectorName = connectorName;
		this.connectorEntityName = connectorEntityName;
		this.lastProcessed = lastProcessed;
		this.duration = duration;
		this.totalProcessedRecordsCount = totalProcessedRecordsCount;
		this.skippedCount = skippedCount;
		this.readCount = readCount;
	}

	public EntitySyncStatusMetric(String connectorId,String connectorName,String connectorEntityName,  Instant lastProcessed,
								  Float duration,Integer totalProcessedRecordsCount, Integer updatedCount, Integer removedCount, Integer createdCount){
		this.connectorId = connectorId;
		this.connectorName = connectorName;
		this.connectorEntityName = connectorEntityName;
		this.lastProcessed = lastProcessed;
		this.duration = duration;
		this.updatedCount = updatedCount;
		this.deletedCount = removedCount;
		this.totalProcessedRecordsCount = totalProcessedRecordsCount;
		this.createdCount = createdCount;
	}
}
