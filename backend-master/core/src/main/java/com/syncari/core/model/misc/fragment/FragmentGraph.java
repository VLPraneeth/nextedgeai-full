package com.syncari.core.model.misc.fragment;

import com.syncari.core.model.Edge;
import com.syncari.core.model.Layout;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.util.MappingNodeType;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;
import static com.syncari.core.utils.ValidationUtils.validateCondition;

@Data
@Accessors(chain = true)
public class FragmentGraph {

    List<FragmentNode> nodes = new ArrayList<>();
    List<FragmentEdge> edges = new ArrayList<>();
    List<Layout> layouts = new ArrayList<>();

    public void validate() {

        validateCondition(nodes.isEmpty(), i18n("fragment_no_nodes"));
        validateCondition(nodes.size() == 1 && isCoreNode(nodes.get(0)), i18n("fragment_only_core_node"));
        edges.forEach(e->e.validate());
        nodes.forEach(n->n.validate());

        List<String> nodeTemplateIds = nodes.stream().map(n -> n.getTemplateId()).collect(Collectors.toList());
        Map<String, FragmentNode> nodeMap = nodes.stream().collect(Collectors.toMap(n -> n.getTemplateId(), n -> n));

        Map<String, List<FragmentEdge>> inboundEdges = nodes.stream().collect(Collectors.toMap(n -> n.getTemplateId(), n -> new ArrayList<>()));
        Map<String, List<FragmentEdge>> outboundEdges = nodes.stream().collect(Collectors.toMap(n -> n.getTemplateId(), n -> new ArrayList<>()));
        // validate each edge's source/destination node references from nodes list
        for (FragmentEdge edge : edges) {
            // ignore back edges
            if (isBackEdge(edge, nodeMap)) {
                continue;
            }
            validateCondition(!nodeTemplateIds.contains(edge.getDestinationStage().getTemplateId()),
                    i18n("fragment_edge_not_connected_to_node", "Destination"));
            validateCondition(!nodeTemplateIds.contains(edge.getSourceStage().getTemplateId()),
                    i18n("fragment_edge_not_connected_to_node", "Source"));
            List<FragmentEdge> inbound = inboundEdges.getOrDefault(edge.getDestinationStage().getTemplateId(), new ArrayList<>());
            List<FragmentEdge> outbound = outboundEdges.getOrDefault(edge.getSourceStage().getTemplateId(), new ArrayList<>());

            // if an edge exist already connecting two nodes - raise an error
            validateCondition(inbound.contains(edge) || outbound.contains(edge),
                    i18n("fragment_graph_duplicate_edges", edge.getSourceStage().getName(), edge.getDestinationStage().getName()));
            inbound.add(edge);
            outbound.add(edge);
            inboundEdges.put(edge.getDestinationStage().getTemplateId(), inbound);
            outboundEdges.put(edge.getSourceStage().getTemplateId(), outbound);
        }
        // get all roots (nodes with no incoming edges) and make a BFS
        List<FragmentNode> sources = nodes.stream().filter(n -> !inboundEdges.containsKey(n.getTemplateId())
                || inboundEdges.get(n.getTemplateId()).isEmpty())
                .collect(Collectors.toList());
        validateCondition(sources.isEmpty(), i18n("fragment_graph_cyclic_references"));

        // check if there are cycles in fragment graph
        sources.forEach(s -> {
            // validate if source is a dangling node - i.e. there are no outbound edges
            validateCondition(sources.size() > 1 && outboundEdges.get(s.getTemplateId()).isEmpty(),
                    i18n("fragment_dangling_node", s.getName()));
            // dfs traversal starting with source node to identify cycles
            validateCondition(hasCycles(outboundEdges, new HashSet<>(), s.getTemplateId()), i18n("fragment_graph_cyclic_references"));
        });

        // check if fragment graph is connected : if graph is not connected there will be more than 2 sources
        // pick any source - traverse outbound to reach sinks - traverse inbound from sinks to source back
        // if all nodes are not traversed by the above traversal - graph is not connected
        Queue<String> srcToSinkQueue = new ArrayDeque<>();
        Queue<String> sinkToSrcQueue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        srcToSinkQueue.offer(sources.get(0).getTemplateId());
        while (!srcToSinkQueue.isEmpty()) {
            var currentNodeId = srcToSinkQueue.poll();
            visited.add(currentNodeId);
            var outbound = outboundEdges.get(currentNodeId);
            if(outbound.isEmpty()){
                // add the sink node to queue for reverse traversal
                sinkToSrcQueue.offer(currentNodeId);
            }else {
                outbound.forEach(edge -> srcToSinkQueue.offer(edge.getDestinationStage().getTemplateId()));
            }
        }

        while (!sinkToSrcQueue.isEmpty()) {
            var currentNodeId = sinkToSrcQueue.poll();
            visited.add(currentNodeId);
            var inbound = inboundEdges.get(currentNodeId);
            inbound.forEach(edge -> sinkToSrcQueue.offer(edge.getSourceStage().getTemplateId()));
        }
        // if all nodes are not in visited, graph is not connected
        validateCondition(visited.size() != nodes.size(), i18n("fragment_graph_not_connected"));
    }

    private boolean isBackEdge(FragmentEdge e, Map<String, FragmentNode> nodeMap) {

        var sourceNode = nodeMap.get(e.getSourceStage().getTemplateId());
        var destinationNode = nodeMap.get(e.getDestinationStage().getTemplateId());

        boolean loopEnd = Optional.ofNullable(sourceNode.getConfiguration().getConfigMap()).map(map -> (boolean)map.getOrDefault("loopEnd", false)).orElse(false);
        boolean loopStart = Optional.ofNullable(destinationNode.getConfiguration().getConfigMap()).map(map -> (boolean)map.getOrDefault("loopStart", false)).orElse(false);
        return loopStart && loopEnd;
    }

    private boolean hasCycles(Map<String, List<FragmentEdge>> outboundEdges, Set<String> visited, String currentNodeId){
        if(visited.contains(currentNodeId)){
            return true;
        }
        visited.add(currentNodeId);
        for (FragmentEdge edge : outboundEdges.get(currentNodeId)) {
            if (hasCycles(outboundEdges, visited, edge.getDestinationStage().getTemplateId())) {
                return true;
            }
        }
        visited.remove(currentNodeId);
        return false;
    }

    private boolean isCoreNode(FragmentNode node){
        return MappingNodeType.CORE_ATTRIBUTE.equals(node.getType()) || MappingNodeType.CORE_ENTITY.equals(node.getType());
    }
}
