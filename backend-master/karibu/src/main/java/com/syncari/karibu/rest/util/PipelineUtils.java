package com.syncari.karibu.rest.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.util.ArrayMap;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.ActionDefinition;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.service.BrandService;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.SchemaService;
import com.syncari.karibu.rest.exceptions.BadRequestException;
import com.syncari.karibu.rest.request.PipelineRequest;
import com.syncari.karibu.rest.request.PipelineResyncRequest;
import com.syncari.restutils.data.*;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.util.Scope;
import com.syncari.core.service.MappingGraphService;
import com.syncari.karibu.rest.exceptions.NotFoundException;
import com.syncari.karibu.rest.response.ActionResponse;
import com.syncari.karibu.rest.response.FunctionResponse;
import com.syncari.karibu.rest.response.PipelineResponse;
import com.syncari.restutils.transformers.GraphTransformer;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.syncari.utils.I18n.i18n;

@Component
public class PipelineUtils {

    @Autowired
    MappingGraphService mappingGraphService;

    @Autowired
    GraphTransformer graphTransformer;

    @Autowired
    SchemaService schemaService;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    BrandService brandService;

    ObjectMapper mapper = new ObjectMapper();

    public PipelineResponse cleanMappingGraphDTO(MappingGraphDTO graphDTO) {
        PipelineResponse response = new PipelineResponse();

        response.setId(graphDTO.getId());
        if (graphDTO.getScope().equals(Scope.ENTITY))
            response.setEntityId(graphDTO.getTargetId());
        if (graphDTO.getScope().equals(Scope.ATTRIBUTE))
            response.setFieldId(graphDTO.getTargetId());
        if(graphDTO.getParentId() != null)
            response.setParentId(graphDTO.getParentId());
        response.setScope(graphDTO.getScope().equals(Scope.ATTRIBUTE) ? "FIELD" : graphDTO.getScope().name());
        response.setName(graphDTO.getName());
        response.setCreatedBy(graphDTO.getCreatedBy());
        response.setCreatedAt(graphDTO.getCreatedAt());
        response.setUpdatedBy(graphDTO.getUpdatedBy());
        response.setUpdatedAt(graphDTO.getUpdatedAt());
        if(graphDTO.getLastSyncedTime() != null)
            response.setLastSyncTime(graphDTO.getLastSyncedTime().toString());
        if(graphDTO.getSyncStatus() != null)
            response.setSyncStatus(graphDTO.getSyncStatus().name());
        response.setDraftStatus((graphDTO.getDraftStatus().name().equals("NEW") ? "DRAFT" : graphDTO.getDraftStatus().name()));

        response.setNodes(getNodes(graphDTO.getNodes()));
        response.setEdges(getEdges(graphDTO.getEdges()));

        return response;
    }

    private List<Map<String, Object>> getNodes(List<MappingNodeDTO> graphNodes) {
        List<Map<String, Object>> nodes = new ArrayList<>();

        for (MappingNodeDTO mappingNode : graphNodes) {
            Map<String, Object> node = new ArrayMap<>() {};
            node.put("id", mappingNode.getId());
            node.put("name", mappingNode.getName());
            node.put("label", mappingNode.getLabel());
            node.put("subLabel", mappingNode.getSubLabel());
            if(brandService.isEnabled()) {
                node.put("iconPath", mappingNode.getIconPath());
                node.put("backgroundColor", mappingNode.getBackgroundColor());
            }
            Map<String, Object> configuration = mappingNode.getConfiguration();
            if (Stream.of(MappingNodeType.ENTITY_SINK.name(), MappingNodeType.ENTITY_SOURCE.name(), MappingNodeType.CORE_ENTITY.name())
                    .anyMatch(mappingNode.getNodeType().name()::equalsIgnoreCase)) {
                configuration.put("synapseId", configuration.get("connectorId"));
                configuration.put("entityId", configuration.get("entityDefinition"));
            }
            if (Stream.of(MappingNodeType.ATTRIBUTE_SINK.name(), MappingNodeType.ATTRIBUTE_SOURCE.name(), MappingNodeType.CORE_ATTRIBUTE.name())
                    .anyMatch(mappingNode.getNodeType().name()::equalsIgnoreCase)) {
                if (configuration.get("connectorId") != null)
                    configuration.put("synapseId", configuration.get("connectorId"));
                configuration.put("fieldId", configuration.get("attributeDefinition"));
            }
            if (mappingNode.getNodeType().equals(MappingNodeType.ACTION))
                configuration.put("actionId", configuration.get("configId"));
            if (mappingNode.getNodeType().equals(MappingNodeType.FUNCTION))
                configuration.put("functionId", configuration.get("definition"));
            configuration.remove("entityDefinition");
            configuration.remove("attributeDefinition");
            configuration.remove("connectorId");
            configuration.remove("configId");
            configuration.remove("definition");
            configuration.remove("targetId");
            if (null != configuration.get("predicate")) {
                Map<String, Object> predicate = (Map<String, Object>) configuration.get("predicate");
                ArrayList<Map<String, Object>> predicates = (ArrayList<Map<String, Object>>) predicate.get("predicates");
                ArrayList<Map<String, Object>> newPredicates = new ArrayList<>();
                for (Map<String, Object> predicatesMap : predicates) {
                    newPredicates.add(predicatesMap);
                }

                predicate.put("predicates", newPredicates);
                configuration.put("predicate", predicate);
            }
            node.put("configuration", configuration);
            node.put("nodeType", mappingNode.getNodeType());
            node.put("location", mappingNode.getLocation());

            nodes.add(node);
        }

        return nodes;
    }

    private List<Map<String, Object>> getEdges(List<EdgeDTO> graphEdges) {
        List<Map<String, Object>> edges = new ArrayList<>();

        for (EdgeDTO mappingEdge : graphEdges) {
            Map<String, Object> edge = new ArrayMap<>() {};
            edge.put("id", mappingEdge.getId());
            Map<String, Object> source = new ArrayMap<>();
            source.put("nodeId", mappingEdge.getSource().getNodeId());
            source.put("datatype", mappingEdge.getSource().getPort().getDatatype());
            source.put("anchor", mappingEdge.getSource().getAnchor());
            edge.put("source", source);
            Map<String, Object> destination = new ArrayMap<>();
            destination.put("nodeId", mappingEdge.getDestination().getNodeId());
            destination.put("datatype", mappingEdge.getDestination().getPort().getDatatype());
            destination.put("anchor", mappingEdge.getDestination().getAnchor());
            edge.put("destination", destination);

            edges.add(edge);
        }
        return edges;
    }

    public MappingGraphDTO convertPipelineUpdateRequest(String pipelineId, PipelineRequest pipelineRequest, Scope scope) {
        MappingGraph entityGraph = mappingGraphService.retrieve(pipelineId).orElseThrow(() ->
                new NotFoundException(i18n(((scope.equals(Scope.ENTITY)) ? "mapping_graph_not_found" : "mapping_field_graph_not_found"), pipelineId)));

        if (scope.equals(Scope.ENTITY) && !entityGraph.getScope().equals(Scope.ENTITY)) {
            throw new NotFoundException(i18n(((scope.equals(Scope.ENTITY)) ? "mapping_graph_not_found" : "mapping_field_graph_not_found"), pipelineId));
        }

        if (scope.equals(Scope.ATTRIBUTE) && !entityGraph.getScope().equals(Scope.ATTRIBUTE)) {
            throw new NotFoundException(i18n(((scope.equals(Scope.ENTITY)) ? "mapping_graph_not_found" : "mapping_field_graph_not_found"), pipelineId));
        }

        MappingGraphDTO mappingGraphDTO = graphTransformer.toMappingGraphDTO(entityGraph);

        mappingGraphDTO.setNodes(getNodesFromRequest(pipelineRequest));
        mappingGraphDTO.setEdges(getEdgesFromRequest(pipelineRequest));

        return mappingGraphDTO;
    }


    public MappingGraphDTO convertPipelineCreateRequest(PipelineRequest pipelineRequest, Scope scope) {

        MappingGraphDTO mappingGraphDTO = new MappingGraphDTO();

        if (scope.equals(Scope.ENTITY)) {
            EntityDefinition entityDefinition = schemaService.findEntity(pipelineRequest.getEntityId()).orElseThrow(() ->
                    new NotFoundException(i18n("entity_not_found", pipelineRequest.getEntityId())));
            mappingGraphDTO.setName(entityDefinition.getDisplayName());
        }

        if (scope.equals(Scope.ATTRIBUTE)) {
            AttributeDefinition attributeDefinition = schemaService.findAttribute(pipelineRequest.getFieldId()).orElseThrow(() ->
                    new NotFoundException(i18n("field_not_found", pipelineRequest.getFieldId())));
            mappingGraphDTO.setName(attributeDefinition.getDisplayName());
        }

        mappingGraphDTO.setId(ObjectId.get().toString());
        mappingGraphDTO.setTargetId((scope.equals(Scope.ENTITY) ? pipelineRequest.getEntityId() : pipelineRequest.getFieldId()));
        mappingGraphDTO.setScope(scope);
        mappingGraphDTO.setParentId(pipelineRequest.getParentId());

        mappingGraphDTO.setNodes(getNodesFromRequest(pipelineRequest));
        mappingGraphDTO.setEdges(getEdgesFromRequest(pipelineRequest));

        return mappingGraphDTO;
    }

    private List<MappingNodeDTO> getNodesFromRequest(PipelineRequest pipelineRequest) {
        List<MappingNodeDTO> mappingNodeDTOS = new ArrayList<>();
        Connector syncariConnector = connectorService.getSyncariConnector();
        List<EntityDefinition> syncariEntities = schemaService.getEntities(syncariConnector.getId(), false)
                .stream()
                .filter(e -> e.isApproved())
                .collect(Collectors.toList());
        for (Map<String, Object> node : pipelineRequest.getNodes()) {
            MappingNodeDTO mappingNodeDTO = mapper.convertValue(node, MappingNodeDTO.class);
            Map<String, Object> configuration = mappingNodeDTO.getConfiguration();
            switch(mappingNodeDTO.getNodeType()){
                case CORE_ENTITY:
                    configuration.put("entityDefinition", configuration.get("entityId"));
                    break;
                case ENTITY_SOURCE:
                case ENTITY_SINK:
                    var externalEntityId = configuration.get("entityId");
                    // source and sink node cannot be syncari entity
                    if(externalEntityId == null
                            || StringUtils.isBlank(externalEntityId.toString())
                            || syncariEntities.stream().anyMatch(e -> e.getId().equals(externalEntityId.toString()))){
                        throw new RuntimeException(i18n("invalid_node", mappingNodeDTO.getLabel(), pipelineRequest.getName()));
                    }
                    configuration.put("entityDefinition", externalEntityId);
                    break;

                case CORE_ATTRIBUTE:
                    if (configuration.get("synapseId") != null)
                        configuration.put("connectorId", configuration.get("synapseId"));
                    configuration.put("attributeDefinition", configuration.get("fieldId"));
                    break;
                case ATTRIBUTE_SOURCE:
                case ATTRIBUTE_SINK:
                    var externalFieldId = configuration.get("fieldId");
                    if(externalFieldId == null
                            || StringUtils.isBlank(externalFieldId.toString())){
                        throw new RuntimeException(i18n("invalid_node", mappingNodeDTO.getLabel(), pipelineRequest.getName()));
                    }
                    AttributeDefinition attrib = schemaService.getAttribute(externalFieldId.toString());
                    // source and sink node cannot be syncari field
                    if(syncariEntities.stream().anyMatch(e -> e.getId().equals(attrib.getEntityId()))){
                        throw new RuntimeException(i18n("invalid_node", mappingNodeDTO.getLabel(), pipelineRequest.getName()));
                    }
                    if (configuration.get("synapseId") != null)
                        configuration.put("connectorId", configuration.get("synapseId"));
                    configuration.put("attributeDefinition", externalFieldId);
                    break;
                case FUNCTION:
                    configuration.put("definition", configuration.get("functionId"));
                    break;
                case ACTION:
                    configuration.put("configId", configuration.get("actionId"));
                    break;
            }

            configuration.remove("synapseId");
            configuration.remove("entityId");
            configuration.remove("actionId");
            configuration.remove("functionId");

            mappingNodeDTOS.add(mappingNodeDTO);
        }
        return mappingNodeDTOS;
    }

    private List<EdgeDTO> getEdgesFromRequest(PipelineRequest pipelineRequest) {
        List<EdgeDTO> edgeDTOS = new ArrayList<>();
        for (Map<String, Object> edge : pipelineRequest.getEdges()) {
            EdgeDTO edgeDTO = new EdgeDTO();
            edgeDTO.setId((edge.containsKey("id")) ? edge.get("id").toString() : ObjectId.get().toString());

            Map<String, Object> source = (Map<String, Object>) edge.get("source");
            PortDTO sourcePortDTO = new PortDTO();
            sourcePortDTO.setDatatype(source.get("datatype").toString());
            sourcePortDTO.setPortType(PortType.OUTPUT);
            sourcePortDTO.setMaxConnections(2147483647);
            NodeRef sourceNodeRef = new NodeRef(source.get("nodeId").toString(), sourcePortDTO, source.get("anchor").toString());
            edgeDTO.setSource(sourceNodeRef);

            Map<String, Object> destination = (Map<String, Object>) edge.get("destination");
            PortDTO destinationPortDTO = new PortDTO();
            destinationPortDTO.setDatatype(destination.get("datatype").toString());
            destinationPortDTO.setPortType(PortType.INPUT);
            destinationPortDTO.setMaxConnections(1);
            NodeRef destinationNodeRef = new NodeRef(destination.get("nodeId").toString(), destinationPortDTO, destination.get("anchor").toString());
            edgeDTO.setDestination(destinationNodeRef);

            edgeDTOS.add(edgeDTO);
        }
        return edgeDTOS;
    }

    public MappingGraph validateEntityFieldPipelineIds(String pipelineId, String fieldPipelineId) {
        MappingGraph entityGraph = mappingGraphService.retrieve(pipelineId).orElseThrow(()->
                new NotFoundException(i18n("mapping_graph_not_found", pipelineId)));

        MappingGraph fg = mappingGraphService.retrieve(fieldPipelineId).orElseThrow(() ->
                new NotFoundException(i18n("mapping_field_graph_not_found", fieldPipelineId)));

        List<MappingGraph> fieldMappings = mappingGraphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId());

        MappingGraph fieldGraph = new MappingGraph();
        for (MappingGraph mg : fieldMappings) {
            if (mg.getId().equals(fieldPipelineId)) {
                fieldGraph = mg;
                break;
            }
        }
        if (fieldGraph.getTargetId() == null || fieldGraph.getDraftStatus().equals(DraftStatus.ARCHIVED))
            throw new NotFoundException(i18n("field_mapping_graph_not_found", fieldPipelineId, pipelineId));

        return fieldGraph;
    }

    public void validateUpdateEntityPipeline(String pipelineId) {
        MappingGraph entityGraph = mappingGraphService.retrieve(pipelineId).orElseThrow(() ->
                new NotFoundException(i18n("mapping_graph_not_found", pipelineId)));

        if (entityGraph.getDraftStatus().equals(DraftStatus.ARCHIVED) || entityGraph.getScope().equals(Scope.ATTRIBUTE))
            throw new NotFoundException(i18n("mapping_graph_not_found", pipelineId));

        if(!entityGraph.getDraftStatus().equals(DraftStatus.NEW))
            throw new RuntimeException(i18n("unable_update_approved_pipeline", pipelineId));

        Optional<MappingGraph> draftEntityGraph = mappingGraphService.retrieveDraftEntityGraph(entityGraph.getTargetId());
    }

    public void validateUpdateFieldPipeline(String fieldPipelineId) {
        MappingGraph fieldGraph = mappingGraphService.retrieve(fieldPipelineId).orElseThrow(() ->
                new NotFoundException(i18n("mapping_field_graph_not_found", fieldPipelineId)));

        if(!fieldGraph.getDraftStatus().equals(DraftStatus.NEW)) {

            if (fieldGraph.getDraftStatus().equals(DraftStatus.ARCHIVED) || fieldGraph.getScope().equals(Scope.ENTITY))
                throw new NotFoundException(i18n("mapping_field_graph_not_found", fieldPipelineId));

            Optional<MappingGraph> draftFieldGraph = mappingGraphService.retrieveDraftAttributeGraph(fieldGraph.getTargetId());

            if (draftFieldGraph.isPresent())
                throw new SyncariValidationException(i18n("field_pipeline_has_draft", fieldPipelineId, draftFieldGraph.get().getId()));

            MappingGraph newDraftFieldGraph = graphTransformer.createEntityPipelineDraft(graphTransformer.fillDraft(fieldGraph));

            throw new RuntimeException(i18n("unable_update_approved_field_pipeline", fieldPipelineId, newDraftFieldGraph.getId()));
        }
    }

    public PipelineRequest validateNodes(String pipelineId, PipelineRequest pipelineRequest) {
        // keep track of valid node ids and new node ids
        List<String> validNodes = new ArrayList<>();
        LinkedHashMap<String, String> newNodeIds = new LinkedHashMap<String, String>();
        List<Map<String, Object>> newNodes = new ArrayList<>();
        List<Map<String, Object>> newEdges = new ArrayList<>();

        // validate nodes
        List<Map<String, Object>> nodes = pipelineRequest.getNodes();
        for (Map<String, Object> node : nodes) {
            if (!node.get("id").toString().startsWith("New nodeId:")) {
                Optional<MappingNode> mappingNode = mappingGraphService.findNode(node.get("id").toString());
                if(mappingNode.isEmpty() || !mappingNode.get().getMappingGraphId().equals(pipelineId))
                    throw new RuntimeException(i18n("invalid_node_id", node.get("id")));
            } else {
                ObjectId id = ObjectId.get();
                newNodeIds.put(node.get("id").toString(), id.toString());
                node.remove("id");
                node.put("id", id.toString());
            }
            newNodes.add(node);
            validNodes.add(node.get("id").toString());
        }
        // validate edges
        List<Map<String, Object>> edges = pipelineRequest.getEdges();
        for (Map<String, Object> edge : edges) {
            Map<String, Object> source = (Map<String, Object>) edge.get("source");
            if(source.get("nodeId").toString().startsWith("New nodeId")){
                String newSourceNodeId = newNodeIds.get(source.get("nodeId").toString());
                source.remove("nodeId");
                source.put("nodeId", newSourceNodeId);
                edge.remove("source");
                edge.put("source", source);
            }
            if(!validNodes.contains(source.get("nodeId")))
                throw new RuntimeException(i18n("invalid_node_id", source.get("nodeId")));
            Map<String, Object> destination = (Map<String, Object>) edge.get("destination");
            if(destination.get("nodeId").toString().startsWith("New nodeId")){
                String newDestinationNodeId = newNodeIds.get(destination.get("nodeId").toString());
                destination.remove("nodeId");
                destination.put("nodeId", newDestinationNodeId);
                edge.remove("destination");
                edge.put("destination", destination);
            }
            if(!validNodes.contains(destination.get("nodeId")))
                throw new RuntimeException(i18n("invalid_node_id", destination.get("nodeId")));
            newEdges.add(edge);
        }
        pipelineRequest.setNodes(newNodes);
        pipelineRequest.setEdges(newEdges);

        return pipelineRequest;
    }

    public MappingGraph getAndValidatePipelineForSync(String entityPipelineId) throws Exception {
        MappingGraph graph = mappingGraphService.retrieve(entityPipelineId)
                .orElseThrow(() -> new NotFoundException(MappingGraph.class, "Id", entityPipelineId));

        if(!graph.getScope().equals(Scope.ENTITY))
            throw new Exception(i18n("mapping_graph_not_entity", graph.getId()));

        if(graph.getDraftStatus().equals(DraftStatus.NEW))
            throw new Exception(i18n("mapping_graph_not_approved", graph.getId()));

        return graph;
    }

    public List<String> getResyncEntityIds (MappingGraph graph, PipelineResyncRequest pipelineResyncRequest){
        List<String> graphEntityIds = new ArrayList<>();
        for (MappingNode mappingNode : graph.getNodes()) {
            if(mappingNode.getType().equals(MappingNodeType.ENTITY_SOURCE)){
                graphEntityIds.add(mappingNode.getEntityDefinitionId().get());
            }
        }

        if(null == pipelineResyncRequest.getEntityIds() || pipelineResyncRequest.getEntityIds().isEmpty()) {
            return graphEntityIds;
        } else {
            for (String requestEntityId : pipelineResyncRequest.getEntityIds()) {
                if(!graphEntityIds.contains(requestEntityId))
                    throw new BadRequestException(i18n("invalid_resync_entity_id", requestEntityId, graph.getId()));
            }
            return pipelineResyncRequest.getEntityIds();
        }
    }
    
    public List<FunctionResponse> getFunction(List<FunctionDefinition> functions) {
        List<FunctionResponse> response = new ArrayList<>();

        for (FunctionDefinition function : functions) {
			FunctionResponse resp = new FunctionResponse().setApiName(function.getName())
					.setDisplayName(function.getDisplayName()).setDescription(function.getDescription())
					.setHelpSummary(function.getHelpSummary()).setScope(function.getScope()).setOutputType(function.getOutputType())
					.setType(function.getType()).setConfiguration(function.getConfiguration());
			resp.setId(function.getId());
        	response.add(resp);
        }
        return response;
    }
    
    public List<ActionResponse> getAction(List<ActionDefinition> actions) {
        List<ActionResponse> response = new ArrayList<>();

        for (ActionDefinition action : actions) {
        	ActionResponse resp = new ActionResponse().setApiName(action.getName())
                    .setLlmHint(action.getLlmHint())
					.setDisplayName(action.getDisplayName()).setDescription(action.getDescription())
					.setHelpSummary(action.getHelpSummary()).setScope(action.getScope()).setOutputType(action.getOutputType())
					.setConfiguration(action.getConfiguration()).setType(action.getType());
			resp.setId(action.getId());
        	response.add(resp);
        }
        return response;
    }

    public List<Map<String, String>> getValidationErrorResponse(List<ValidationError> validationErrors) {
        List<Map<String, String>> validationErrorResponse = new ArrayList<>();
        validationErrors.forEach(e -> {
            Map<String, String> validationError = new HashMap<>();
            validationError.put("errorCode", e.getErrorCode());
            validationError.put("errorMessage", e.getMessage());
            if (e.getNodeId() != null)
                validationError.put("nodeId", e.getNodeId());
            if (e.getTargetId() != null)
                validationError.put("targetId", e.getTargetId());

            validationErrorResponse.add(validationError);
        });

        return validationErrorResponse;
    }
}
