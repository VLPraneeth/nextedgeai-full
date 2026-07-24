package com.syncari.viper.streams.stages;

import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.pipeline.BatchActionContext;
import com.syncari.core.pipeline.BatchedOperations;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.sync.RecordsBySyncariId;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;

@Data
@Accessors(chain = true)
public class FieldsGraphRequest {
    private String entityName;
    private RecordsBySyncariId records;
    private GraphContext graphContext;
    private Map<AttributeDefinition, MappingGraph> attributeDAGs;
    private EntityDefinition syncariEntityDef;
    private BatchActionContext attributeBatchActionContext;
    private BatchedOperations batchedOperations;
    private HashMap<String, Map<String, String>> resolvedIds;
    private Map<String, EntityDefinition> entityDefCache;
    private Map<String, Connector> connectorCache;
}
