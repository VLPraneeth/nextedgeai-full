package com.syncari.core.model.misc;

import java.time.Instant;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SyncLog {
	public static final String DIRECTION_INBOUND="inbound";
	public static final String DIRECTION_OUTBOUND="outbound";
	public static final String SYNC_MODE_INITIAL="initial";
	public static final String SYNC_MODE_INCREMENTAL="incremental";
	String connectorId;
	String connectorName;
	String batchId;
	String direction;
	int recordCount;
	int latency;
	String graphId;
	String graphName;
	String errorCode;
	String errorDescription;
	String entityId;
	String entityName;
	String operation;
	String syncMode;
	Instant occuredTime;
	List<String> failedRecords;
	String failedWinningRecord;
}
