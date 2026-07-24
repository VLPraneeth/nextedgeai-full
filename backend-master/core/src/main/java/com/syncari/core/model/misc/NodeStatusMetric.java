package com.syncari.core.model.misc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Data
@Accessors(chain = true)
@AllArgsConstructor
public class NodeStatusMetric {

	private String nodeId;
	private Integer recordCount;
	private Integer skippedCount;
	private Integer readCount;

	public NodeStatusMetric(Integer recordCount) {
		this.recordCount = recordCount;
	}
}
