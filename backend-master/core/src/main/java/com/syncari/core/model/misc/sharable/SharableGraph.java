package com.syncari.core.model.misc.sharable;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Edge;
import com.syncari.core.model.Layout;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.PipelineSettings;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.syncari.core.model.util.MappingNodeType.*;
import static com.syncari.core.model.util.MappingNodeType.ATTRIBUTE_SOURCE;

@Data
@Accessors(chain = true)
public class SharableGraph {

    private String targetId;
    private Scope scope;
    private String name;

    List<SharableNode> nodes = new ArrayList<>();
    List<SharableEdge> edges = new ArrayList<>();
    List<Layout> layouts = new ArrayList<>();

    private PipelineSettings settings;

    public SharableNode getCoreNode(){
        return nodes.stream().filter(node->node.getConfiguration().getNodeType() == CORE_ENTITY || node.getConfiguration().getNodeType() == CORE_ATTRIBUTE)
                .findFirst()
                .orElseThrow(()-> new SyncariValidationException("Did not find a core node in the sharable graph"));
    }

    public Stream<SharableNode> getSources() {
        return getNodesByType(getSourceType());
    }

    public MappingNodeType getSourceType() {
        return getScope().equals(Scope.ENTITY) ? ENTITY_SOURCE : ATTRIBUTE_SOURCE;
    }

    public Stream<SharableNode> getNodesByType(MappingNodeType sourceType) {
        return nodes.stream().filter(stage -> stage.getConfiguration().getNodeType().equals(sourceType));
    }

    public List<SharableEdge> getOutboundEdges(SharableNode node) {
        Map<String, List<SharableEdge>> outboundEdges = new HashMap<>();
        for (SharableEdge edge : getEdges()) {
            if(edge.getSourceStageId()!=null) {
                List<SharableEdge> outbound = outboundEdges.getOrDefault(edge.getSourceStageId(), new ArrayList<>());
                if (edge.getSourceStageId().equals(node.getId())) {
                    outbound.add(edge);
                }
                outboundEdges.put(edge.getSourceStageId(), outbound);
            }
        }
        return outboundEdges.getOrDefault(node.getId(), List.of());
    }

    public List<SharableEdge> getInboundEdges(SharableNode node) {
        Map<String, List<SharableEdge>> inboundEdges = new HashMap<>();
        for (SharableEdge edge : getEdges()) {
            if(edge.getDestinationStageId()!=null) {
                List<SharableEdge> inbound = inboundEdges.getOrDefault(edge.getDestinationStageId(), new ArrayList<>());
                if (edge.getDestinationStageId().equals(node.getId())) {
                    inbound.add(edge);
                }
                inboundEdges.put(edge.getDestinationStageId(), inbound);
            }
        }
        return inboundEdges.getOrDefault(node.getId(),List.of());
    }

    public Optional<SharableNode> getNode(String currentNodeId) {
        if(currentNodeId==null) return Optional.empty();
        return getNodes().stream().filter(n -> currentNodeId.equals(n.getId())).findFirst();
    }

    public List<SharableNode> getPreviousNodes(SharableNode target) {
        return getInboundEdges(target).stream().flatMap(edge -> getNode(edge.getSourceStageId()).stream()).collect(Collectors.toList());
    }
    public List<SharableNode> getNextNodes(SharableNode target) {
        return getOutboundEdges(target).stream().flatMap(edge -> getNode(edge.getDestinationStageId()).stream()).collect(Collectors.toList());
    }
}
