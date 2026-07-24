package com.syncari.core.validation;

import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class ValidationContext {

    private MappingNode node;
    private MappingGraph graph;
    private Connector syncariConnector;
    private Map<String, EntityDefinition> sourceEntityMap = new HashMap<>();
    private Map<String, EntityDefinition> sinkEntityMap = new HashMap<>();
    private EntityDefinition coreEntity;
    private List<MappingNode> topoSortedNodes;
    private boolean allowToken;

    Map<String, Object> data = new HashMap<>();

    private ValidationType validationType;

    public enum ValidationType{
        NODE,
        GRAPH,
    }

}