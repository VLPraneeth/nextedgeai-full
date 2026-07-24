package com.syncari.core.model.misc;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@Accessors(chain = true)
public class StreamInfo {
	private String syncariEntityId;
	private Status status;
	private Instant lastSyncTime;
	private long lagTimeInSeconds;
	private long errorCount;
	private long warningCount;
	private String errorDetails;

	EntitySyncStatusSummary summary;
	
	public StreamInfo() {}

	public enum Status{

		//Processor has begun processing the underlying stream of entities
		RUNNING,
		// Stream is in resync state. 
		RESYNCING,
		//Pause command has been issued. Stream is still in its previous state
		PAUSING,
		//Paused
		PAUSED,
		//Queued for execution
		QUEUED,
		// Stream is in Error state
		ERROR,
		// Unpublished
		UNPUBLISHED,
		// Running Test
		TEST,
		// Pipeline has errors, but not ready to pause yet. Can transition to either Running/Resyncing or Error state
		RETRYING
	}
}
