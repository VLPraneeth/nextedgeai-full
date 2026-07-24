package com.syncari.core.model.misc;

import java.time.Instant;

import lombok.Data;

@Data
public class EntitySyncStatus {
	private String entityName;
	private String entityId;
	private String connectorName;
	private String connectorType;
	private String connectorId;
	private Instant processedUpTo;
	private boolean historicalSync;
}
