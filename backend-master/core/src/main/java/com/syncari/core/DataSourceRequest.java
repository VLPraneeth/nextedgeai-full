package com.syncari.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.syncari.connector.EntityData;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.PipelineTestWebhook;
import com.syncari.core.model.misc.Watermark;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DataSourceRequest {
	Watermark watermark;
	List<EntityDefinition> sourceEntities;
	EntityDefinition syncariEntity;
	Map<String, List<String>> recordIds;
	MappingGraph graph;
	Map<String, Map<String, Object>> sourceParamMap = new HashMap<>();
	Long syncStartTime;
	String syncCycleId;
	Map<String, Map<String, Object>> additionalParamMap = new HashMap<>();
	Map<String, PipelineTestWebhook> webhook;
	EntityData realTimeSourceData;

	public DataSourceRequest addSource(String entityId, Map<String, Object> params) {
		sourceParamMap.put(entityId, params);
		return this;
	}
}
