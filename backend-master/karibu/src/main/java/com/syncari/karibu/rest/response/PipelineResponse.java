package com.syncari.karibu.rest.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.api.client.util.ArrayMap;
import com.syncari.core.model.Edge;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.UUIDAuditModel;
import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@ToString(callSuper=true)
public class PipelineResponse extends BaseKaribuResponse {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String entityId;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String fieldId;
    private String parentId;
    private String scope;
    private String lastSyncTime;
    private String syncStatus;
    private String draftStatus;

    private List<Map<String, Object>> nodes;
    private List<Map<String, Object>> edges;


    @Override
    public <k extends KaribuResponse, h extends UUIDAuditModel> Object populate(h object) {
        MappingGraph graphDTO = (MappingGraph) object;
        PipelineResponse response = new PipelineResponse();

        response.setId(graphDTO.getId());
        response.setScope(graphDTO.getScope().name());
        response.setName(graphDTO.getName());
        //response.setSyncStatus();
        //response.setLastSyncTime();
        response.setDraftStatus(graphDTO.getDraftStatus().name());
        response.setCreatedBy(graphDTO.getCreatedBy());
        response.setCreatedAt(graphDTO.getCreatedAt());
        response.setUpdatedBy(graphDTO.getUpdatedBy());
        response.setUpdatedAt(graphDTO.getUpdatedAt());

        response.setNodes(getGraphNodes(graphDTO.getNodes()));
        response.setEdges(getGraphEdges(graphDTO.getEdges()));

        return response;
    }

    private List<Map<String, Object>> getGraphNodes(List<MappingNode> graphNodes) {
        List<Map<String, Object>> nodes = new ArrayList<>();

        for (MappingNode mappingNode : graphNodes) {
            Map<String, Object> node = new ArrayMap<>() {};
            node.put("id", mappingNode.getId());
            node.put("name", mappingNode.getName());
            node.put("apiName", mappingNode.getApiName());
            // node.put("label", apiPipelineUtils.generateLabel(mappingNode));
            // node.put("subLabel", apiPipelineUtils.generateSubLabel(mappingNode));
            Map<String, Object> configuration = mappingNode.getConfiguration().getConfigMap();
            node.put("configuration", configuration);
            node.put("nodeType", mappingNode.getConfiguration().getNodeType().name());

            nodes.add(node);
        }

        return nodes;

    }

    private List<Map<String, Object>> getGraphEdges(List<Edge> graphEdges) {
        List<Map<String, Object>> edges = new ArrayList<>();

        for (Edge mappingEdge : graphEdges) {
            Map<String, Object> edge = new ArrayMap<>() {};
            edge.put("id", mappingEdge.getId());
            Map<String, Object> source = new ArrayMap<>();
            source.put("nodeId", mappingEdge.getSourceStage().getId());
            edge.put("source", source);
            Map<String, Object> destination = new ArrayMap<>();
            destination.put("nodeId", mappingEdge.getDestinationStage().getId());
            edge.put("destination", destination);

            edges.add(edge);
        }

        return edges;

    }

}
