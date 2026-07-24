package com.syncari.api.core.util;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.core.functions.FunctionConstants;
import com.syncari.core.model.Edge;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.SchemaService;

@Component
public class NodeHelper {
    private static Set<MappingNodeType> TERMINAL_NODES = Set.of(MappingNodeType.ATTRIBUTE_SOURCE,
            MappingNodeType.ENTITY_SOURCE, MappingNodeType.CORE_ATTRIBUTE, MappingNodeType.CORE_ENTITY);
    @Autowired
    SchemaService schemaService;
    @Autowired
    MappingGraphService graphService;
    
    /**
     * Run BFS starting at current node, all the way to sources and collect them all
     *
     * @param current
     * @return
     */
    public Set<MappingNode> findConnectedSources(MappingNode current, MappingGraph graph) {
        Queue<MappingNode> unvisited = new ArrayDeque<>();
        Set<MappingNode> sources = new HashSet<>();
        if(current == null || graph == null) return sources;
        Set<String> visited = new HashSet<>();
        unvisited.add(current);
        while (!unvisited.isEmpty()) {
            var node = unvisited.poll();
            if(!visited.contains(node.getId())) {
                List<Edge> edges = graph.getInboundEdges(node);
                // if we hit one terminal node, we stop the traversal.
                if (TERMINAL_NODES.contains(node.getType())) {
                    sources.add(node);
                } else {
                    for (Edge edge : edges) {
                        unvisited.offer(edge.getSourceStage());
                    }
                }
                visited.add(node.getId());
            }
        }
        return sources;
    }
    
    public Set<MappingNode> findConnectedLookup(MappingNode current, MappingGraph graph) {
        boolean isSinkSide = graph.isSinkSide(current);
        Queue<MappingNode> unvisited = new ArrayDeque<>();
        Set<MappingNode> lookUps = new HashSet<>();
        if(current == null || graph == null) return lookUps;
        Set<String> visited = new HashSet<>();
        unvisited.add(current);
        while (!unvisited.isEmpty()) {
            var node = unvisited.poll();
            if(isSinkSide && node.isCoreNode()) break;
            if(!visited.contains(node.getId())) {
                List<Edge> edges = graph.getInboundEdges(node);
                if (MappingNodeType.FUNCTION == node.getType()
                        && "advancedLookUpSyncariRecord".equalsIgnoreCase(node.getApiName())
                        && !node.getId().equalsIgnoreCase(current.getId())) {
                    lookUps.add(node);
                } else {
                    for (Edge edge : edges) {
                        unvisited.offer(edge.getSourceStage());
                    }
                }
                visited.add(node.getId());
            }
        }
        return lookUps;
    }
    
	public Set<MappingNode> findConnectedSetValues(MappingNode current, MappingGraph graph) {
		Set<MappingNode> temps = new HashSet<>();
		if (current == null)
			return temps;
		if (graph.getScope() == Scope.ATTRIBUTE) {
			String targetAttrId = graph.getTargetId();
        	var attribute = schemaService.getAttribute(targetAttrId);
        	var entityId = attribute.getEntityId();
        	var entityMappingGraph = graphService.retrieveDraftEntityGraph(entityId);
			if (!graph.isSinkSide(current)) { // FP source
				temps.addAll(findConnectedSetValueNodes(current, graph, Set.of(current)));
				entityMappingGraph.ifPresent(gra -> {
					gra.getNodesByType(MappingNodeType.CORE_ENTITY).forEach(coreEntity -> {
        				temps.addAll(findConnectedSetValueNodes(coreEntity, gra, Set.of(coreEntity)));
        			});
	        	});
			} else { // FP destination
				temps.addAll(findConnectedSetValueNodes(current, graph, Set.of(current)));
				entityMappingGraph.ifPresent(gra -> {
					gra.getNodesByType(MappingNodeType.ENTITY_SINK).forEach(sink -> {
        				temps.addAll(findConnectedSetValueNodes(sink, gra, Set.of(sink)));
        			});
					var attributeGraphsLite = graphService.retrieveAttributeGraphsLiteForEntityGraph(gra.getId());
					var attributeGraphs = graphService.populateGraphsWithoutLayout(attributeGraphsLite);
					attributeGraphs.forEach(ag -> {
						if(!StringUtils.equals(graph.getId(), ag.getId())) {
							ag.getNodesByType(MappingNodeType.CORE_ATTRIBUTE).forEach(attribNode -> {
								temps.addAll(findConnectedSetValueNodes(attribNode, ag, Set.of(attribNode)));
							});
						}
	        		});
	        	});
				
			}
		} else if (graph.getScope() == Scope.ENTITY) {
			if (!graph.isSinkSide(current)) { // EP source
				temps.addAll(findConnectedSetValueNodes(current, graph, Set.of(current)));
			} else { // EP destination
				temps.addAll(findConnectedSetValueNodes(current, graph, Set.of(current)));
                var attributeGraphsLite = graphService.retrieveAttributeGraphsLiteForEntityGraph(graph.getId());
                var attributeGraphs = graphService.populateGraphsWithoutLayout(attributeGraphsLite);
	        	if(attributeGraphs != null) {
	        		attributeGraphs.forEach(ag -> {
	        			ag.getNodesByType(MappingNodeType.CORE_ATTRIBUTE).forEach(attribNode -> {
	        				temps.addAll(findConnectedSetValueNodes(attribNode, ag, Set.of(attribNode)));
	        			});
	        		});
	        	}
			}
		}
		return temps;
	}
    
    public Set<MappingNode> findConnectedSetValueNodes(MappingNode current, MappingGraph graph, Set<MappingNode> terminalNodes) {
    	Queue<MappingNode> unvisited = new ArrayDeque<>();
    	Set<MappingNode> temps = new HashSet<>();
    	Set<String> visited = new HashSet<>();
    	unvisited.addAll(terminalNodes);
        while (!unvisited.isEmpty()) {
            var node = unvisited.poll();
            if(!visited.contains(node.getId())) {
                List<Edge> edges = graph.getInboundEdges(node);
                var setValueFieldMap = (Map) node.getConfig("setValueField");
                if (MappingNodeType.FUNCTION == node.getType()
                        && (FunctionConstants.SET_VALUE_ON_ENTITY.equalsIgnoreCase(node.getApiName()) 
                        		|| FunctionConstants.SET_VALUE.equalsIgnoreCase(node.getApiName()))
                        && setValueFieldMap != null 
                        && "temporary".equals(setValueFieldMap.get("type"))
                        && (current == null 
                        	|| !node.getId().equalsIgnoreCase(current.getId()))) {
                	temps.add(node);
                }
                for (Edge edge : edges) {
                	unvisited.offer(edge.getSourceStage());
                }
                visited.add(node.getId());
            }
        }
        return temps;
    }
    
    
    public Stream<EntityDefinition> findConnectedEntities(Set<MappingNode> sources) {
        return sources
                        .stream().map(
                                source -> source.getType() == MappingNodeType.ATTRIBUTE_SOURCE
                                        || source.getType() == MappingNodeType.CORE_ATTRIBUTE
                                                ? schemaService
                                                        .getEntity(schemaService
                                                                .getAttribute(source.getConfiguration().getConfigMap()
                                                                        .get("attributeDefinition").toString())
                                                                .getEntityId())
                                                : schemaService.getEntity(source.getConfiguration().getConfigMap()
                                                        .get("entityDefinition").toString()));
    }
}
