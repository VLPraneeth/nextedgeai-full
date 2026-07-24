package com.syncari.core.model.misc;

import lombok.Data;

@Data
public class EntityPipelineDetailsStatus {
	private String entityName;
	private String entityId;
	private String connectorName;
	private String connectorType;
	private String connectorId;
}
