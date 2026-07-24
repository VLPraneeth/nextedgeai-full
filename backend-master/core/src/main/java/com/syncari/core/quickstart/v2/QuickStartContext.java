package com.syncari.core.quickstart.v2;

import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.misc.sharable.SharableGraph;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.SchemaService;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Data
@Accessors(chain = true)
public class QuickStartContext {

    ConnectorService connService;
    SchemaService schemaService;
    public QuickStartContext(){

    }

    public QuickStartContext(ConnectorService connService, SchemaService schemaService){
        this.connService = connService;
        this.schemaService = schemaService;
    }

    QSConfig qsConfig;
    SharableNode currentNode;
    SharableGraph currentPipeline;
    Map<String, String> resolvedNodeMap = new HashMap<>();
    private Map<String, Connector> connectorMap = new HashMap<>();
    private Map<String, EntityDefinition> entityMap = new HashMap<>();
    private Map<String, AttributeDefinition> attributeMap = new HashMap<>();
    MappingNode currentMappingNode;

    public Optional<Connector> getConnector(String connectorId){
        if(connectorMap.isEmpty()) {
            // seed all active connectors
            connService.list().forEach(c -> connectorMap.put(c.getId(), c));
            Connector syncariConnector = connService.getSyncariConnector();
            connectorMap.put(syncariConnector.getId(), syncariConnector);
        }
        return Optional.ofNullable(connectorMap.get(connectorId));
    }

    public Optional<EntityDefinition> getEntity(String entityId){
        if(!entityMap.containsKey(entityId)){
            EntityDefinition retrieved = schemaService.getEntity(entityId);
            entityMap.put(retrieved.getId(), retrieved);
            // also populate the corresponding attributes
            attributeMap.putAll(retrieved.getIdToAttribMap());
        }

        return Optional.ofNullable(entityMap.get(entityId));
    }

    public Optional<AttributeDefinition> getAttribute(String attributeId){
        if(!attributeMap.containsKey(attributeId)){
            Optional<AttributeDefinition> retrieved = schemaService.findAttribute(attributeId);
            retrieved.ifPresent(attr -> attributeMap.put(attr.getId(), attr));
        }
        return Optional.ofNullable(attributeMap.get(attributeId));
    }


}
