package com.syncari.core.model;

import org.springframework.data.mongodb.core.mapping.Document;

import com.syncari.core.model.misc.Watermark;

import lombok.Data;

@Data
@Document
public class SyncDetail extends UUIDAuditModel {
	private String externalEntityId;
	// Syncari entity name
	private String entityName;
	private Watermark watermark;
	private int batchSize;
	private long nextSyncAt;
	private long startTime;
	private long endTime;
	private boolean onGoingSync;
	private boolean forceSchedule; // force the schdule to be used
	
	public SyncDetail() {}
	
	public SyncDetail(String externalEntityId, String entityName, Watermark watermark) {
		this.externalEntityId = externalEntityId;
		this.entityName = entityName;
		this.watermark = watermark;
	}

	public SyncDetail(String externalEntityId, String entityName, Watermark watermark, long startTime, long endTime, boolean onGoingSync) {
		this.externalEntityId = externalEntityId;
		this.entityName = entityName;
		this.watermark = watermark;
		this.startTime = startTime;
		this.endTime = endTime;
		this.onGoingSync = onGoingSync;
	}

}
