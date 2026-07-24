package com.syncari.core.model;

import com.syncari.core.event.store.EventStore;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Map;

@Data
@Accessors(chain=true)
public class PipelineStats {
	private String connectorId;
	private String connectorName;
	private String pipelineId;
	private String batchId;
	private String stageName; //name of function/action/entity as source,sink,core
	private String stageId;
	private String stageType; //function, core,source, sink or action
	private String targetId;
	private String targetType; //ENTITY or attribute
	private Instant occurredAt;

	private long recordsProcessed;
	private long emptyInputCount;
	private long emptyOutputCount;
	private long changeCount;
	private long duplicateCount;
	private long dedupeCount ;
	private long latency;

}
