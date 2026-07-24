package com.syncari.core.validation;

import com.syncari.core.model.*;
import com.syncari.core.model.util.Scope;

import java.util.List;
import java.util.stream.Collectors;

public class GraphValidationUtil {

    public static boolean isAttributeRefFromSourceEntity(String attributeId, ValidationContext validationContext){
        MappingNode node = validationContext.getNode();
        MappingGraph graph = validationContext.getGraph();
        List<MappingNode> connectedSources = graph.getConnectedSourcesWithNode(node).collect(Collectors.toList());
        for(MappingNode src : connectedSources){
            String entityId;
            if(Scope.ENTITY.equals(graph.getScope())){
                EntitySourceNodeConfig srcNodeConfig = src.getTypedConfiguration();
                entityId = srcNodeConfig.getEntityDefinition().getId();
            } else {
                AttributeSourceNodeConfig srcNodeConfig = src.getTypedConfiguration();
                entityId = srcNodeConfig.getAttributeDefinition().getEntityId();
            }

            EntityDefinition srcEntity = validationContext.getSourceEntityMap().get(entityId);
            if(srcEntity != null && srcEntity.getAttributes().stream().anyMatch(a -> a.getId().equals(attributeId))){
                return true;
            }
        }
        return false;
    }

    public static boolean isAttributeRefFromCoreEntity(String attributeId, ValidationContext validationContext){
        return validationContext.getCoreEntity().getAttributes().stream().anyMatch(a -> a.getId().equals(attributeId));
    }

    public static boolean isValidSourceEntityReference(String entityId, ValidationContext validationContext){
        MappingGraph graph = validationContext.getGraph();
        List<EntityDefinition> sources = graph.getSources().map(n -> {
            String srcEntityId;
            if(Scope.ENTITY.equals(graph.getScope())){
                EntitySourceNodeConfig srcNodeConfig = n.getTypedConfiguration();
                srcEntityId = srcNodeConfig.getEntityDefinition().getId();
            } else {
                AttributeSourceNodeConfig srcNodeConfig = n.getTypedConfiguration();
                srcEntityId = srcNodeConfig.getAttributeDefinition().getEntityId();
            }
            return validationContext.getSourceEntityMap().get(srcEntityId);
        }).collect(Collectors.toList());
        var sourceEntityIds = sources.stream().map(s -> s.getId()).collect(Collectors.toList());
        return sourceEntityIds.contains(entityId);
    }

    public static boolean isValidCoreEntityReference(String entityId, ValidationContext validationContext) {
        MappingGraph graph = validationContext.getGraph();
        MappingNode coreNode = graph.getCoreNode();
        String srcEntityId;
        if (Scope.ENTITY.equals(graph.getScope())) {
            CoreEntityNodeConfig coreEntityNodeConfig = coreNode.getTypedConfiguration();
            srcEntityId = coreEntityNodeConfig.getEntityDefinition().getId();
        } else {
            CoreAttributeNodeConfig coreAttributeNodeConfig = coreNode.getTypedConfiguration();
            srcEntityId = coreAttributeNodeConfig.getAttributeDefinition().getEntityId();
        }
        return srcEntityId.equals(entityId);
    }
}
