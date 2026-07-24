package com.syncari.karibu.rest.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.api.client.util.ArrayMap;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.util.Scope;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.SchemaService;
import com.syncari.karibu.rest.request.PipelineRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class PipelineTestUtil {

    @Autowired
    ConnectorService connectorService;

    @Autowired
    SchemaService schemaService;

    @Autowired
    MappingGraphService graphService;

    public String getPipelineCreateRequest(String connectorId, String firstNodeId, String edgeFirstNodeId) {
        try {
            PipelineRequest pipelineRequest = new PipelineRequest();

            EntityDefinition marketoEntityDefinition = schemaService.getEntity(connectorId, "lead");
            Connector connector = connectorService.getSyncariConnector();
            EntityDefinition syncariEntityDefinition = schemaService.getEntity(connector.getId(), "lead");

            // delete any existing drafts if any before creating new
            graphService.discardDraftEntityGraph(syncariEntityDefinition.getId());

            pipelineRequest.setEntityId(syncariEntityDefinition.getId());
            pipelineRequest.setNodes(getNodes(marketoEntityDefinition.getId(), syncariEntityDefinition.getId(), connectorId, firstNodeId));
            pipelineRequest.setEdges(getEdges(edgeFirstNodeId));
            pipelineRequest.setScope(Scope.ENTITY.name());

            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
            ObjectWriter ow = mapper.writer().withDefaultPrettyPrinter();
            return ow.writeValueAsString(pipelineRequest);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public List<Map<String, Object>> getNodes(String marketoEntityId, String syncariEntityId, String marketoSynapseId, String firstNodeId) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<String, Object> node1 = new ArrayMap<>() {};
        Map<String, Object> configuration1 = new ArrayMap<>() {};
        Map<String, Object> location1 = new ArrayMap<>() {};

        node1.put("id", firstNodeId);
        node1.put("name", "Lead");
        node1.put("label", "Lead");
        node1.put("subLabel", "Syncari");
        configuration1.put("entityId", syncariEntityId);
        configuration1.put("enableDeduplicate", false);
        configuration1.put("dataAuthorityStrategy", "NONE");
        node1.put("configuration", configuration1);
        node1.put("nodeType", "CORE_ENTITY");
        node1.put("location", location1);
        nodes.add(node1);

        Map<String, Object> node2 = new ArrayMap<>() {};
        Map<String, Object> configuration2 = new ArrayMap<>() {};
        Map<String, Object> location2 = new ArrayMap<>() {};

        node2.put("id", "New nodeId: 2");
        node2.put("name", "Lead");
        node2.put("label", "Sync from Lead");
        node2.put("subLabel", "Marketo");
        configuration2.put("schedule", "");
        configuration2.put("entityId", marketoEntityId);
        configuration2.put("synapseId", marketoSynapseId);
        configuration2.put("sourceParams", null);
        configuration2.put("destinationParams", null);
        node2.put("configuration", configuration2);
        node2.put("nodeType", "ENTITY_SOURCE");
        location2.put("y", "400");
        location2.put("x", "300");
        node2.put("location", location2);
        nodes.add(node2);

        Map<String, Object> node3 = new ArrayMap<>() {};
        Map<String, Object> configuration3 = new ArrayMap<>() {};
        Map<String, Object> location3 = new ArrayMap<>() {};

        node3.put("id", "New nodeId: 3");
        node3.put("name", "Lead");
        node3.put("label", "Sync to Lead");
        node3.put("subLabel", "Marketo");
        configuration3.put("acceptsDeletesFrom", new ArrayList<>());
        configuration3.put("entityId", marketoEntityId);
        configuration3.put("synapseId", marketoSynapseId);
        node3.put("configuration", configuration3);
        node3.put("nodeType", "ENTITY_SINK");
        location3.put("y", "400");
        location3.put("x", "900");
        node3.put("location", location3);
        nodes.add(node3);

        return nodes;
    }

    public List<Map<String, Object>> getEdges(String edgeFirstNodeId) {
        List<Map<String, Object>> edges = new ArrayList<>();
        Map<String, Object> edge1 = new ArrayMap<>() {};
        Map<String, Object> source1 = new ArrayMap<>() {};
        Map<String, Object> destination1 = new ArrayMap<>() {};

        source1.put("nodeId", "New nodeId: 2");
        source1.put("datatype", "object");
        source1.put("anchor", "1");
        destination1.put("nodeId", edgeFirstNodeId);
        destination1.put("datatype", "object");
        destination1.put("anchor", "3");
        edge1.put("source", source1);
        edge1.put("destination", destination1);
        edges.add(edge1);

        Map<String, Object> edge2 = new ArrayMap<>() {};
        Map<String, Object> source2 = new ArrayMap<>() {};
        Map<String, Object> destination2 = new ArrayMap<>() {};

        source2.put("nodeId", edgeFirstNodeId);
        source2.put("datatype", "object");
        source2.put("anchor", "1");
        destination2.put("nodeId", "New nodeId: 3");
        destination2.put("datatype", "object");
        destination2.put("anchor", "3");
        edge2.put("source", source2);
        edge2.put("destination", destination2);
        edges.add(edge2);

        return edges;
    }
}
