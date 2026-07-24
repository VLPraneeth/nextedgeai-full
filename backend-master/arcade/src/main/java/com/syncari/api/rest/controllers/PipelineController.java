package com.syncari.api.rest.controllers;

import com.syncari.analytics.service.AnalyticsService;
import com.syncari.api.core.util.MappingTransformer;
import com.syncari.api.rest.controllers.data.*;
import com.syncari.api.rest.controllers.exceptions.BadRequestException;
import com.syncari.api.rest.controllers.exceptions.ResourceNotFoundException;
import com.syncari.connector.service.Transformer;
import com.syncari.core.SyncariContext;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.event.store.model.NodeAudit;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.mapper.MapperType;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.FieldMapping;
import com.syncari.core.model.misc.*;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.pagination.PageDirection;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.SyncDirection;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.model.versioning.ActionType;
import com.syncari.core.model.versioning.Diff;
import com.syncari.core.model.versioning.Version;
import com.syncari.core.quickstart.QuickStartRunService;
import com.syncari.core.schema.ClonePipelineEntityDef;
import com.syncari.core.service.*;
import com.syncari.restutils.data.*;
import com.syncari.restutils.transformers.GraphTransformer;
import com.syncari.utils.DateUtil;
import com.syncari.utils.I18n;
import com.syncari.utils.KeyValue;
import lombok.Data;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.Serializable;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.READ_STUDIO;
import static com.syncari.core.security.Permissions.WRITE_STUDIO;
import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/pipeline")
@Setter
public class PipelineController {
    public static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss z";
    @Autowired
    private MappingGraphService mappingGraphService;
    @Autowired
    private PipelineTestService pipelineTestService;
    @Autowired
    private LayoutService layoutService;
    @Autowired
    private StreamService streamService;
    @Autowired
    DateUtil dateUtil;
    @Autowired
    ResyncService resyncService;
    @Autowired
    private SchemaService schemaService;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    AnalyticsService analyticsService;

    @Autowired
    SyncStatusService syncStatusService;

    @Autowired
    MappingTransformer mappingTransformer;

    @Autowired
    QuickStartRunService qsRunService;

    @Autowired
    HttpServletResponse response;

    @Autowired
    GraphTransformer graphTransformer;
    @Autowired
    PipelineDocumentationService documentationService;
    @Autowired
    PipelineNodeAuditService pipelineNodeAuditService;
    @Autowired
    private Transformer transformer;

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline/{syncariEntityId}")
    public MappingGraphDTO getEntityPipeline(@PathVariable String syncariEntityId) {
        var graph = mappingGraphService.retrieveEntityGraph(syncariEntityId)
                .orElseThrow(() -> new RuntimeException(String.format("Entity pipeline for syncariEntityId %s not found", syncariEntityId)));
        return graphTransformer.fillDraft(graph);
    }


    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/entityPipeline/{syncariEntityId}/clone")
    public MappingGraphDTO cloneEntityPipeline(@PathVariable String syncariEntityId, @RequestBody ClonePipelineEntityDef entityDef) {
        MappingGraph clonedGraph = mappingGraphService.cloneEntityGraph(syncariEntityId, entityDef);
        return graphTransformer.fillDraft(clonedGraph);
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline/{syncariEntityId}/{draftStatus}")
    public MappingGraphDTO getEntityPipelineWithDraftStatus(@PathVariable String syncariEntityId, @PathVariable String draftStatus) {

        List<MappingGraph> graphs = mappingGraphService.retrieveEntityGraphsLite(syncariEntityId);

        Optional<MappingGraph> approvedLite = graphs.stream().filter(g -> g.isApproved()).findFirst();
        Optional<MappingGraph> draftLite = graphs.stream().filter(g -> g.isDraft()).findFirst();
        validateCondition(approvedLite.isEmpty() && draftLite.isEmpty(), i18n("no_entity_pipeline_found", syncariEntityId));

        // if approved does not exists then only load draft regardless of what draftStatus is asked
        if (approvedLite.isEmpty()) {
            // load only draft
            var draft = mappingGraphService.retrieve(draftLite.get().getId());
            return graphTransformer.fillDraft(draft.get(), false, false);
        }

        // load nodes and edges for pipeline with specified draft status
        if (DraftStatus.APPROVED.name().equals(draftStatus)) {
            var approvedGraph = mappingGraphService.retrieve(approvedLite.get().getId())
                    .orElseThrow(() -> new RuntimeException(String.format("Pipeline with id %s not found", approvedLite.get().getId())));
            return graphTransformer.fillDraft(approvedGraph, false, false);
        }
        // if draft doesn't exists then load full approvedGraph
        var approvedGraph = draftLite.isPresent() ? approvedLite.get()
                : mappingGraphService.retrieve(approvedLite.get().getId())
                .orElseThrow(() -> new RuntimeException(String.format("Pipeline with id %s not found", approvedLite.get().getId())));
        return graphTransformer.fillDraft(approvedGraph, true, false);

    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline/{syncariEntityId}/generateDocumentation/{draftStatus}")
    public Map<String, String> generatePipelineDocs(@PathVariable String syncariEntityId, @PathVariable String draftStatus) {
        DraftStatus status = StringUtils.isBlank(draftStatus) ? DraftStatus.NEW : DraftStatus.valueOf(draftStatus);
        final Documentation documentation = documentationService.generateDocumentation(syncariEntityId, status);
        return Map.of("syncariEntityId", syncariEntityId, "version", draftStatus, "content", documentation.toBase64());
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/entityPipeline/{syncariEntityId}/documentation/{draftStatus}")
    public void saveDocs(@RequestBody Documentation documentation, @PathVariable String syncariEntityId, @PathVariable String draftStatus) {
        DraftStatus status = StringUtils.isBlank(draftStatus) ? DraftStatus.NEW : DraftStatus.valueOf(draftStatus);
        final Optional<MappingGraph> mappingGraph = mappingGraphService.retrieveEntityGraph(syncariEntityId, status);
        mappingGraph.ifPresent(g -> {
            g.setDocumentation(new Documentation().setContent(decode(documentation.getContent())));
            mappingGraphService.saveGraph(g);
        });
    }

    @SneakyThrows
    private static String decode(String documentation) {
        return new String(Base64.getDecoder().decode(documentation), "UTF-8");
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline/{syncariEntityId}/documentation/{draftStatus}")
    public Map findDocs(@PathVariable String syncariEntityId, @PathVariable String draftStatus) {
        DraftStatus status = StringUtils.isBlank(draftStatus) ? DraftStatus.NEW : DraftStatus.valueOf(draftStatus);
        final Optional<MappingGraph> mappingGraph = mappingGraphService.retrieveEntityGraph(syncariEntityId, status);
        final Documentation documentation = mappingGraph.flatMap(g -> Optional.ofNullable(g.getDocumentation())).orElse(new Documentation());
        return Map.of("syncariEntityId", syncariEntityId, "version", draftStatus, "content", documentation.toBase64());
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/createEntityPipeline/{syncariEntityId}")
    public MappingGraphDTO getOrCreateEntityPipeline(@PathVariable String syncariEntityId) {
        var graph = mappingGraphService.retrieveEntityGraph(syncariEntityId)
                .orElseGet(() -> mappingGraphService.createDefaultEntityGraph(syncariEntityId));
        if (graph.isApproved()) {
            createVersionIfNotExists(syncariEntityId, graph.getDraftStatus());
            graph = mappingGraphService.createDraftFor(graph);
        }
        return graphTransformer.fillDraft(graph);
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline/{syncariEntityId}/validate")
    public KeyValue validateEntityGraph(@PathVariable String syncariEntityId) {

        var graph = mappingGraphService.retrieveDraftEntityGraph(syncariEntityId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format("A draft Entity Pipeline for entity with id %s not found", syncariEntityId)));

        mappingGraphService.validateGraph(graph.getId());
        return new KeyValue("status", "success");
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/entityPipeline/{syncariEntityId}/validate")
    public ResponseEntity<KeyValue> validateCurrentEntityGraph(@PathVariable String syncariEntityId, @RequestBody MappingGraphDTO graph) {
        return validateGraph(graph);
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/fieldPipeline/{syncariFieldId}/validate")
    public ResponseEntity<KeyValue> validateCurrentFieldGraph(@PathVariable String syncariFieldId, @RequestBody MappingGraphDTO graph) {
        return validateGraph(graph);
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/fieldPipeline/{syncariFieldId}/validate")
    public KeyValue validateFieldGraph(@PathVariable String syncariFieldId) {
        var graph = mappingGraphService.retrieveDraftAttributeGraph(syncariFieldId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format("A draft Field Pipeline for field with id %s not found", syncariFieldId)));

        mappingGraphService.validateGraph(graph.getId(), false);
        return new KeyValue("status", "success");
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/search/{text}")
    public List<MappingGraphDTO> search(@PathVariable String text) {
        List<MappingGraph> list = mappingGraphService.search(text);
        List<MappingGraphDTO> results = new ArrayList<>();
        Map<String, FunctionDefinition> functions = schemaService.getFunctions(Scope.ENTITY).stream().collect(Collectors.toMap(f -> f.getId(), f -> f));
        functions.putAll(schemaService.getFunctions(Scope.ATTRIBUTE).stream().collect(Collectors.toMap(f -> f.getId(), f -> f)));
        Map<String, ActionDefinition> actions = schemaService.getActions().stream().collect(Collectors.toMap(a -> a.getId(), a -> a));
        list.forEach(graph -> {
            MappingGraphDTO graphDTO = graphTransformer.toMappingGraphDTO(graph);
            graphDTO.getNodes().forEach(node -> {
                Map<String, Object> config = node.getConfiguration();
                Object key = config.getOrDefault("configId", "");
                if (node.getNodeType() == MappingNodeType.FUNCTION && functions.containsKey(key)) {
                    node.setIconPath(functions.get(key).getIconPath());
                } else if (node.getNodeType() == MappingNodeType.ACTION && actions.containsKey(key)) {
                    node.setIconPath(actions.get(key).getIconPath());
                } else {
                    node.setIconPath("/assets/icons/syncari-icon-blue.svg");
                }
            });
            graphDTO.getGroups().forEach(group -> {
                group.setIconPath("/assets/icons/groups/group_gray.svg");
            });
            results.add(graphDTO);
        });
        return results;
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline/{syncariEntityId}/fieldDraftSummary")
    public List<KeyValue> retrieveDraftPipelineSummary(@PathVariable String syncariEntityId) {
        Optional<MappingGraph> approvedGraph = mappingGraphService.retrieveApprovedEntityGraph(syncariEntityId);
        Optional<MappingGraph> mappingGraph = mappingGraphService.retrieveDraftEntityGraph(syncariEntityId);
        List<MappingGraph> approvedAttribs = approvedGraph
                .map(graph -> mappingGraphService.retrieveApprovedAttributeGraphs(graph.getId()))
                .orElse(Collections.emptyList());
        List<MappingGraph> draftAttributes = mappingGraph
                .map(graph -> mappingGraphService.retrieveDraftAttributeGraphs(graph.getId()))
                .orElse(Collections.emptyList());

        Set<String> fieldIds = new HashSet<String>(approvedAttribs.stream().map(g -> g.getTargetId()).collect(Collectors.toSet()));
        fieldIds.addAll(draftAttributes.stream().map(g -> g.getTargetId()).collect(Collectors.toSet()));

        return fieldIds.stream()
                .map(fieldId -> tofieldGraphSummary(
                        draftAttributes.stream().filter(g -> fieldId.equals(g.getTargetId())).findFirst(),
                        approvedAttribs.stream().filter(g -> fieldId.equals(g.getTargetId())).findFirst()))
                .collect(Collectors.toList());
    }

    private KeyValue tofieldGraphSummary(Optional<MappingGraph> draftGraph, Optional<MappingGraph> approvedGraph) {
        var fieldGraph = draftGraph.orElse(approvedGraph.orElse(null));
        return new KeyValue("name", fieldGraph.getName())
                .set("id", fieldGraph.getTargetId())
                .set("updatedAt", fieldGraph.getUpdatedAt())
                .set("draftStatus", fieldGraph.getDraftStatus())
                .set("hasChanges", fieldGraph.isChanged())
                .set("ready", fieldGraph.isReady())
                .set("isDeleted", (draftGraph.isPresent() && draftGraph.get().isDeleted()) || (approvedGraph.isPresent() && draftGraph.isEmpty()));
    }


    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/entityPipeline/{syncariEntityId}")
    public Object createEntityPipeline(@PathVariable String syncariEntityId, @RequestBody MappingGraphDTO graph) {
        List<ValidationError> errors = new ArrayList<>();
        createVersionIfNotExists(syncariEntityId, graph.getDraftStatus());
        var newGraph = graphTransformer.createEntityPipelineDraft(graph, errors);
        if (errors != null && !errors.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(KeyValue.of("status", "400", "validationErrors", errors));
        }
        return graphTransformer.toMappingGraphDTO(newGraph);
    }

    /**
     * Only patches settings right now
     *
     * @param syncariEntityId
     * @param graph
     * @return
     */
    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.PATCH, value = "/entityPipeline/{syncariEntityId}")
    public void updateEntityPipeline(@PathVariable String syncariEntityId, @RequestBody MappingGraphDTO graphDTO) {
        mappingGraphService.retrieveEntityGraphLite(syncariEntityId, DraftStatus.NEW)
                .ifPresentOrElse(m -> {
                    final MappingGraph mappingGraph = graphTransformer.toMappingGraph(graphDTO);
                    if (mappingGraph.getSettings() != null) {
                        m.setSettings(mappingGraph.getSettings());
                        mappingGraphService.saveGraph(m);
                    }
                }, () -> {
                    throw new NotFoundException(i18n("settings_draft_not_found", syncariEntityId));
                });
    }

    private void createVersionIfNotExists(String syncariEntityId, DraftStatus graphDraftStatus) {
        if (graphDraftStatus == DraftStatus.APPROVED
                && !mappingGraphService.hasVersions(syncariEntityId)) {
            var approvedGraph = mappingGraphService.retrieveApprovedEntityGraph(syncariEntityId);
            if (approvedGraph.isPresent()) {
                log.info("Creating version for approved graph {} since there is no versions available",
                        approvedGraph.get().getId());
                var v = graphTransformer.fromVersionRequest(
                        MappingGraphVersionRequestDTO.builder().name(i18n("published_version_name"))
                                .summary(i18n("published_version_summary")).build(),
                        ActionType.Published);
                mappingGraphService.createVersion(approvedGraph.get(), v);
            }
        }
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/approveEntityPipeline/{syncariEntityId}")
    public void approveEntityPipeline(@PathVariable String syncariEntityId, @RequestBody PublishOptions options) {
        var mappingGraph = mappingGraphService.retrieveEntityGraph(syncariEntityId);
        var graphToApprove = mappingGraph.flatMap(g -> g.isDraft() ? Optional.of(g) : mappingGraphService.findDraft(g))
                .orElseThrow(() -> new ResourceNotFoundException(String.format("A draft Entity Pipeline for entity with id %s not found", syncariEntityId)));
        Set<MappingGraph> readyAttributeGraphs = options.isReadyOnly() ?
                mappingGraphService.retrieveDraftAttributeGraphs(graphToApprove.getId())
                        .stream().filter((g) -> g.isReady()).collect(Collectors.toSet())
                : new HashSet<>();

        mappingGraphService.validateGraph(graphToApprove.getId(), options.isReadyOnly());

        log.info("Approving {} draft for {} with processAll {}", syncariEntityId, SyncariContext.getSyncariId(), options.isProcessAll());
        mappingGraphService.approveDraft(graphToApprove, options.isProcessAll(), options.isReadyOnly(),
                graphTransformer.fromVersionRequest(options.getVersionInfo(), ActionType.Published));
        if (options.isReadyOnly()) {
            graphTransformer.createEntityPipelineDraft(options.getGraph());
        }
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "entityPipeline/resyncEntity/{syncariEntityId}")
    public String resyncEntitySource(@PathVariable String syncariEntityId, @RequestBody ResyncRequest resyncRequest) {

        if (resyncRequest.getSynapseEntityIds().isEmpty()) {
            throw new RuntimeException("No entity source selected for resync");
        }

        Instant startTime = StringUtils.isBlank(resyncRequest.getFromDate()) ? Instant.ofEpochMilli(0) : Instant.parse(resyncRequest.getFromDate());
        Instant endTime = StringUtils.isBlank(resyncRequest.getToDate()) ? Instant.now() : Instant.now().isBefore(Instant.parse(resyncRequest.getToDate())) ? Instant.now() : Instant.parse(resyncRequest.getToDate());

        if (startTime.toEpochMilli() > endTime.toEpochMilli()) {
            throw new RuntimeException("Start time should be less than End time");
        }
        resyncService.createResyncRequest(syncariEntityId, resyncRequest.getSynapseEntityIds(), startTime, endTime);
        return "success";

    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/discardEntityPipeline/{syncariEntityId}")
    public void discardEntityPipelineDraft(@PathVariable String syncariEntityId, @RequestBody MappingGraphVersionRequestWrapper req) {
        mappingGraphService.discardDraftEntityGraph(syncariEntityId, graphTransformer.fromVersionRequest(req != null ? req.getVersionInfo() : null, ActionType.Deleted));
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/deleteEntityPipeline/{syncariEntityId}")
    public void deleteEntityPipeline(@PathVariable String syncariEntityId, @RequestBody MappingGraphVersionRequestWrapper req) {
        mappingGraphService.deleteApprovedEntityGraph(syncariEntityId, graphTransformer.fromVersionRequest(req != null ? req.getVersionInfo() : null, ActionType.Deleted));
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/cancelResyncEntity/{syncariEntityId}")
    public void cancelResyncEntity(@PathVariable String syncariEntityId) {
        mappingGraphService.cancelResync(syncariEntityId);
    }


    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/approveFieldPipeline/{syncariFieldId}")
    public void approveFieldPipeline(@PathVariable String syncariFieldId) {
        var mappingGraph = mappingGraphService.retrieveAttributeGraph(syncariFieldId);
        var graphToApprove = mappingGraph.flatMap(g -> g.isDraft() ? Optional.of(g) : mappingGraphService.findDraft(g))
                .orElseThrow(() -> new ResourceNotFoundException(String.format("A draft Field Pipeline for field with id %s not found", syncariFieldId)));
        mappingGraphService.validateGraph(graphToApprove.getId());
        mappingGraphService.approveDraft(graphToApprove);

    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/discardFieldPipeline/{syncariFieldId}")
    public void discardFieldPipelineDraft(@PathVariable String syncariFieldId) {
        mappingGraphService.discardDraftFieldGraph(syncariFieldId);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/deleteFieldPipeline/{syncariFieldId}")
    public void deleteFieldPipeline(@PathVariable String syncariFieldId) {
        mappingGraphService.deleteApprovedFieldgraph(syncariFieldId);

    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/fieldPipeline/{syncariFieldId}")
    public Object createFieldPipeline(@PathVariable String syncariFieldId, @RequestBody MappingGraphDTO graph) {
        var incomingGraph = graph.hasDraft() ? graph.getDraft() : graph;
        List<ValidationError> errors = new ArrayList<>();
        var mappedIncomingGraph = graphTransformer.toMappingGraph(incomingGraph, errors);
        if (errors != null && !errors.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(KeyValue.of("status", "400", "validationErrors", errors));
        }
        MappingGraph newGraph = mappingGraphService.upsertAttributeGraph(mappedIncomingGraph);
        graphTransformer.updateLayout(incomingGraph);
        return graphTransformer.toMappingGraphDTO(newGraph);

    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/{syncariEntityId}/mapping")
    public FieldMappingResponse createFieldMappings(@PathVariable String syncariEntityId, @RequestBody List<FieldMappingDTO> mappings) {
        Optional<MappingGraph> existingDraft = mappingGraphService.retrieveDraftEntityGraph(syncariEntityId);
        List<FieldMapping> newMappings = mappingGraphService.createFieldMappings(syncariEntityId,
                mappingTransformer.toMappingFields(mappings, syncariEntityId));
        FieldMappingResponse response = mappingTransformer.toFieldMappingResponse(newMappings);
        if (existingDraft.isPresent()) {
            var draftAfterMapping = mappingGraphService.retrieveDraftEntityGraph(syncariEntityId);
            draftAfterMapping.ifPresent(d -> {
                response.setEntityDraftUpdated(d.getNodes().size() != existingDraft.get().getNodes().size());
            });
        } else {
            response.setNewEntityDraft(true);
            response.setEntityDraftUpdated(true);
        }
        return response;
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.PUT, value = "/{syncariEntityId}/mapping")
    public FieldMappingResponse updateFieldMappings(@PathVariable String syncariEntityId, @RequestBody List<UpdateFieldMappingDTO> mappings) {

        Optional<MappingGraph> existingDraft = mappingGraphService.retrieveDraftEntityGraph(syncariEntityId);
        List<UpdateFieldMappingRequest> updateRequest = mappings.stream()
                .map(m -> mappingTransformer.toUpdateFieldMappingRequest(m, syncariEntityId))
                .collect(Collectors.toList());

        List<FieldMapping> updatedMappings = mappingGraphService.updateFieldMappings(syncariEntityId, updateRequest);
        FieldMappingResponse response = mappingTransformer.toFieldMappingResponse(updatedMappings);
        if (existingDraft.isPresent()) {
            var draftAfterMapping = mappingGraphService.retrieveDraftEntityGraph(syncariEntityId);
            draftAfterMapping.ifPresent(d -> {
                response.setEntityDraftUpdated(d.getNodes().size() != existingDraft.get().getNodes().size());
            });
        } else {
            response.setNewEntityDraft(true);
            response.setEntityDraftUpdated(true);
        }
        return response;
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/{syncariEntityId}/mapping/bulkDelete")
    public FieldMappingResponse deleteFieldMappings(@PathVariable String syncariEntityId, @RequestBody List<FieldMappingDTO> mappings) {
        Optional<MappingGraph> existingDraft = mappingGraphService.retrieveDraftEntityGraph(syncariEntityId);
        List<FieldMapping> newMappings = mappingGraphService.deleteFieldMappings(syncariEntityId,
                mappingTransformer.toMappingFields(mappings, syncariEntityId));
        FieldMappingResponse response = mappingTransformer.toFieldMappingResponse(newMappings);
        response.setNewEntityDraft(existingDraft.isEmpty());
        return response;
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/{syncariEntityId}/mapping")
    public List<FieldMappingDTO> getFieldMappings(@PathVariable String syncariEntityId) {
        EntityDefinition syncariEntity = schemaService.getEntity(syncariEntityId);

        List<Connector> connectors = connectorService.list();
        Connector syncariConnector = connectorService.getSyncariConnector();
        if (syncariConnector != null) {
            syncariConnector.setName("Syncari");
            connectors.add(syncariConnector);
        }
        Map<String, Connector> connectorMap = connectors.stream().collect(Collectors.toMap(c -> c.getId(), c -> c));

        List<FieldMappingDTO> mappings = new ArrayList<>();
        Optional<MappingGraph> entityGraphMaybe = mappingGraphService.retrieveDraftEntityGraph(syncariEntityId)
                .or(() -> mappingGraphService.retrieveApprovedEntityGraph(syncariEntityId));
        entityGraphMaybe.ifPresent(entityGraph -> {

            Map<String, EntityDefinition> srcEntityMap = entityGraph.getSources()
                    .map(srcNode -> {
                        EntitySourceNodeConfig config = srcNode.getTypedConfiguration();
                        return config.getEntityDefinition();
                    }).collect(Collectors.toMap(e -> e.getId(), e -> e, (e1, e2) -> e1));

            Map<String, EntityDefinition> sinkEntityMap = entityGraph.getSinks()
                    .map(srcNode -> {
                        EntitySinkNodeConfig config = srcNode.getTypedConfiguration();
                        return config.getEntityDefinition();
                    }).collect(Collectors.toMap(e -> e.getId(), e -> e, (e1, e2) -> e1));

            List<MappingGraph> attributeGraphs = mappingGraphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId());
            attributeGraphs.forEach(graph -> {
                try {
                    AttributeDefinition syncariField = syncariEntity.getAttribute(graph.getTargetId());
                    Map<String, AttributeDefinition> srcAttribMap = graph.getSources()
                            .map(srcNode -> {
                                AttributeSourceNodeConfig config = srcNode.getTypedConfiguration();
                                return config.getAttributeDefinition();
                            }).collect(Collectors.toMap(a -> a.getId(), a -> a, (a1, a2) -> a1));

                    Map<String, AttributeDefinition> sinkAttribMap = graph.getSinks()
                            .map(srcNode -> {
                                AttributeSinkNodeConfig config = srcNode.getTypedConfiguration();
                                return config.getAttributeDefinition();
                            }).collect(Collectors.toMap(a -> a.getId(), a -> a, (a1, a2) -> a1));

                    srcAttribMap.forEach((attribId, attrib) -> {
                        EntityDefinition synapseEntity = srcEntityMap.get(attrib.getEntityId());
                        Connector conn = connectorMap.get(synapseEntity.getConnectorId());
                        var mapping = buildFieldMapping(conn, synapseEntity, syncariEntity, attrib, syncariField,
                                sinkAttribMap.containsKey(attribId) ? SyncDirection.BIDI : SyncDirection.INBOUND);
                        mappings.add(mapping);
                    });

                    sinkAttribMap.forEach((attribId, attrib) -> {
                        // if it was part of source the field mapping is already added
                        if (!srcAttribMap.containsKey(attribId)) {
                            EntityDefinition synapseEntity = sinkEntityMap.get(attrib.getEntityId());
                            Connector conn = connectorMap.get(synapseEntity.getConnectorId());
                            var mapping = buildFieldMapping(conn, synapseEntity, syncariEntity, attrib, syncariField, SyncDirection.OUTBOUND);
                            mappings.add(mapping);
                        }
                    });
                } catch (Exception e) {
                    // if there are any errors in fetching field mapping of a pipeline - skip it and log error instead of failing entire api call
                    log.error(String.format("Error in fetching field mapping for graph %s with id %s", graph.getName(), graph.getId()), e);
                }
            });

        });
        Collections.sort(mappings, Comparator.comparing(FieldMappingDTO::getSyncariFieldDisplayName));
        return mappings;
    }

    private FieldMappingDTO buildFieldMapping(Connector connector, EntityDefinition synapseEntity, EntityDefinition syncariEntity,
                                              AttributeDefinition synapseField, AttributeDefinition syncariField, SyncDirection direction) {
        FieldMappingDTO mapping = new FieldMappingDTO()
                .setId(ObjectId.get().toHexString()).setSyncariEntityId(syncariEntity.getId())
                .setSynapseId(connector.getId()).setSynapseName(connector.getName())
                .setSyncariFieldId(syncariField.getId()).setSyncariFieldApiName(syncariField.getApiName())
                .setSyncariFieldDisplayName(syncariField.getDisplayName())
                .setSyncariFieldDatatype(syncariField.getDataType().getName())
                .setSynapseEntityId(synapseEntity.getId()).setSynapseEntityApiName(synapseEntity.getApiName())
                .setSynapseEntityDisplayName(synapseEntity.getDisplayName())
                .setSynapseFieldId(synapseField.getId()).setSynapseFieldApiName(synapseField.getApiName())
                .setSynapseFieldDisplayName(synapseField.getDisplayName())
                .setSynapseFieldDatatype(synapseField.getDataType().getName())
                .setDirections(mappingTransformer.retrieveMappingDirections(direction));
        return mapping;
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/fieldPipeline/{syncariFieldId}")
    public MappingGraphDTO getFieldPipeline(@PathVariable String syncariFieldId) {
        MappingGraph graph = mappingGraphService.retrieveAttributeGraph(syncariFieldId)
                .orElseThrow(() -> new ResourceNotFoundException(I18n.i18n("fp_not_found")));
        return graphTransformer.fillDraft(graph);
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/fieldPipeline/{syncariFieldId}/{draftStatus}")
    public MappingGraphDTO getFieldPipelineWithDraftStatus(@PathVariable String syncariFieldId, @PathVariable String draftStatus) {

        List<MappingGraph> graphs = mappingGraphService.retrieveAttributeGraphsLite(syncariFieldId);

        Optional<MappingGraph> approvedLite = graphs.stream().filter(g -> g.isApproved()).findFirst();
        Optional<MappingGraph> draftLite = graphs.stream().filter(g -> g.isDraft()).findFirst();
        validateCondition(approvedLite.isEmpty() && draftLite.isEmpty(), i18n("no_field_pipeline_found", syncariFieldId));

        // if approved does not exists then only load draft regardless of what draftStatus is asked by user
        if (approvedLite.isEmpty()) {
            // load only draft
            var draft = mappingGraphService.retrieve(draftLite.get().getId());
            return graphTransformer.fillDraft(draft.get(), false, false);
        }

        // load nodes and edges for pipeline with specified draft status
        if (DraftStatus.APPROVED.name().equals(draftStatus)) {
            var approvedGraph = mappingGraphService.retrieve(approvedLite.get().getId())
                    .orElseThrow(() -> new RuntimeException(String.format("Pipeline with id %s not found", approvedLite.get().getId())));
            return graphTransformer.fillDraft(approvedGraph, false, false);
        }
        // if draft doesn't exists then load full approvedGraph
        var approvedGraph = draftLite.isPresent() ? approvedLite.get()
                : mappingGraphService.retrieve(approvedLite.get().getId())
                .orElseThrow(() -> new RuntimeException(String.format("Pipeline with id %s not found", approvedLite.get().getId())));
        return graphTransformer.fillDraft(approvedGraph, true, false);

    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/createFieldPipeline/{syncariFieldId}")
    public MappingGraphDTO getOrCreateFieldPipeline(@PathVariable String syncariFieldId) {
        return graphTransformer.fillDraft(createAttributeDraftIfNeeded(syncariFieldId));
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/markFieldPipelineReady/{syncariFieldId}")
    public MappingGraphDTO markFieldPipelineReady(@PathVariable String syncariFieldId) {
        boolean deleted = isDeleted(syncariFieldId);
        MappingGraph graph = createAttributeDraftIfNeeded(syncariFieldId);
        graph.setReady(true);
        graph.setDeleted(deleted);
        mappingGraphService.upsertAttributeGraph(graph);
        return graphTransformer.fillDraft(graph);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/markFieldPipelineNotReady/{syncariFieldId}")
    public MappingGraphDTO markFieldPipelineNotReady(@PathVariable String syncariFieldId) {
        if (isDeleted(syncariFieldId)) {
            mappingGraphService.discardDraftFieldGraph(syncariFieldId);
            return graphTransformer.toMappingGraphDTO(
                    mappingGraphService.retrieveAttributeGraph(syncariFieldId).orElse(new MappingGraph()));
        } else {
            MappingGraph graph = createAttributeDraftIfNeeded(syncariFieldId);
            graph.setReady(false);
            mappingGraphService.upsertAttributeGraph(graph);
            return graphTransformer.fillDraft(graph);
        }
    }

    private boolean isDeleted(String syncariFieldId) {
        var approved = mappingGraphService.retrieveApprovedAttributeGraphLite(syncariFieldId);
        var draft = mappingGraphService.retrieveDraftAttributeGraphLite(syncariFieldId);
        return (draft.isPresent() && draft.get().isDeleted()) || (approved.isPresent() && draft.isEmpty());
    }

    private void createEPDraftIfNeeded(String syncariFieldId) {
        AttributeDefinition activeAttribute = schemaService.getActiveAttribute(syncariFieldId)
                .orElseThrow(() -> new ResourceNotFoundException(I18n.i18n("field_not_found")));
        String entityId = activeAttribute.getEntityId();
        Optional<MappingGraph> draftEntityGraph = mappingGraphService.retrieveDraftEntityGraph(entityId);
        draftEntityGraph.ifPresentOrElse(g -> {
            //do nothing if present
        }, () -> {
            //create a draft if no draft found present
            Optional<MappingGraph> approved = mappingGraphService.retrieveApprovedEntityGraph(entityId);
            approved.ifPresentOrElse(
                    //create a draft from approved if approved is available
                    a -> {
                        mappingGraphService.createDraftFor(a);
                    },
                    //create a brand new graph
                    () -> mappingGraphService.createDefaultEntityGraph(entityId)
            );
        });
    }

    private void discardDraft(MappingGraph mappingGraph) {
        var maybeDraft = mappingGraph.isDraft() ? Optional.of(mappingGraph) : mappingGraphService.findDraft(mappingGraph);
        maybeDraft.stream().forEach(draft -> {
                    mappingGraphService.discardDraft(draft);
                    List<String> nodeIds = draft.getNodes().stream().map(MappingNode::getId).collect(Collectors.toList());
                    List<String> edgeIds = draft.getEdges().stream().map(Edge::getId).collect(Collectors.toList());
                    layoutService.deleteEdgeLayouts(edgeIds);
                    layoutService.deleteNodeLayouts(nodeIds);

                }
        );
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/deactivateFieldPipeline/{syncariFieldId}")
    public void deactivateFieldPipeline(@PathVariable String syncariFieldId) {
        try {
            mappingGraphService.deactivateFieldGraph(syncariFieldId);
        } catch (SyncariValidationException ex) {
            throw new BadRequestException(ex);
        }
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/deactivateEntityPipeline/{syncariEntityId}")
    public void deactivateEntityPipeline(@PathVariable String syncariEntityId) {
        try {
            mappingGraphService.deactivateEntityGraph(syncariEntityId);
        } catch (SyncariValidationException ex) {
            throw new BadRequestException(ex);
        }
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/entityPipeline/stop/{syncariEntityId}")
    public void stop(@PathVariable String syncariEntityId) {
        mappingGraphService.pauseStream(syncariEntityId);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/entityPipeline/start/{syncariEntityId}")
    public void start(@PathVariable String syncariEntityId) {
        mappingGraphService.restart(syncariEntityId);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/entityPipeline/test/{syncariEntityId}")
    public String testPipeline(@PathVariable String syncariEntityId, @RequestBody TestPipelineDTO filter) {
        Instant startTime = null;
        Instant endTime = null;
        Map<String, List<String>> recordIds = filter.getRecordIds();
        Map<String, PipelineTestWebhook> webhook = filter.getWebhook();

        try {
            // are we testing the pipeline by ID? or Date Range? or Webhook payload?
            if (MapUtils.isEmpty(recordIds) && MapUtils.isEmpty(webhook)) {
                if (StringUtils.isBlank(filter.getStart()) || StringUtils.isBlank(filter.getEnd())) {
                    throw new SyncariValidationException(I18n.i18n("test_time_required"));
                }
                if (StringUtils.isBlank(filter.getLimit())) {
                    throw new SyncariValidationException(I18n.i18n("test_limit_required"));
                }
                if (!StringUtils.isBlank(filter.getLimit()) && Long.parseLong(filter.getLimit()) > 100) {
                    throw new SyncariValidationException(I18n.i18n("test_limit"));
                }
                startTime = dateUtil.toInstant(filter.getStart(), "UTC");
                endTime = dateUtil.toInstant(filter.getEnd(), "UTC");
                if (startTime.toEpochMilli() > endTime.toEpochMilli()) {
                    throw new SyncariValidationException(I18n.i18n("test_time_invalid"));
                }
            }

            mappingGraphService.testEntityGraph(syncariEntityId, startTime, endTime,
                    filter.getLimit() == null ? 100 : Long.parseLong(filter.getLimit()), recordIds, webhook);
            return "success";
        } catch (SyncariValidationException ex) {
            throw new BadRequestException(ex.getMessage());
        }
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/reposition/{mappingGraphId}")
    public void reposition(@PathVariable String mappingGraphId) {
        mappingGraphService.reposition(mappingGraphId);
    }

    private MappingGraph createAttributeDraftIfNeeded(String syncariFieldId) {
        createEPDraftIfNeeded(syncariFieldId);
        MappingGraph graph = mappingGraphService.retrieveDraftAttributeGraph(syncariFieldId)
                .orElseGet(() ->
                        mappingGraphService.retrieveApprovedAttributeGraph(syncariFieldId)
                                //if approved graph exists, create a draft from it
                                .map(approved -> mappingGraphService.createDraftFor(approved))
                                //otherwise, create a default graph
                                .orElseGet(() -> mappingGraphService.createDefaultAttributeGraph(syncariFieldId))
                );
        return graph;
    }

    private ResponseEntity<KeyValue> validateGraph(@RequestBody MappingGraphDTO graph) {
        List<ValidationError> errors = new ArrayList<>();
        try {
            var incomingGraph = graphTransformer.toMappingGraph(graph.hasDraft() ? graph.getDraft() : graph, errors);
            errors.stream().forEach(e -> e.setTargetId(graph.getId()));
            EntityDefinition coreEntity = mappingGraphService.getCoreEntity(incomingGraph);
            Map<String, EntityDefinition> sourceEntitiesMap = mappingGraphService.getConnectedSourceEntityMap(incomingGraph);
            var validationErrors = mappingGraphService.validateGraphWithoutException(incomingGraph, coreEntity, sourceEntitiesMap, new HashMap<String, Object>());
            if (validationErrors != null) {
                errors.addAll(validationErrors);
            }
        } catch (Exception e) {
            log.error("Validation error ", e);
            errors.add(ValidationError.globalError().withTargetId(graph.getTargetId()).withMessage(e.getMessage()));
        }
        ResponseEntity<KeyValue> res = null;
        if (errors == null || errors.isEmpty()) {
            res = ResponseEntity.ok(new KeyValue("status", "success"));
        } else {
            res = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(KeyValue.of("status", "400", "validationErrors", errors));
        }
        return res;
    }

    @Deprecated
    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline/status")
    public List<StreamInfo> getAllEntityPipelineStreamStatus() {
        List<StreamInfo> infos = syncStatusService.getAllPipelineStreamStatus();
        return infos;
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline/details")
    public List<EntityPipelineDetailsDTO> getAllEntityPipelineDetailedStatus() {
        return syncStatusService.getAllPipelineStatusDetails().stream().map(detail -> {
            var resyncDetails = getEntityPipelineResyncStatus(detail.getSyncariEntityId());
            return new EntityPipelineDetailsDTO(detail, resyncDetails);
        }).collect(Collectors.toList());
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline/details/transactions")
    public List<EntityPipelineDetailsTransaction> getAllEntityPipelineDetailedStatusTransactions() {
        return syncStatusService.getAllPipelineStatusDetailsTransactions();
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline/details/syncMetric")
    public List<EntityPipelineSyncMetric> getAllEntityPipelineDetailedSyncMetric() {
        return syncStatusService.getAllPipelineStatusDetailsSyncMetric();
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline/status/{syncariEntityId}")
    public StreamInfo getEntityPipelineStreamStatus(@PathVariable String syncariEntityId) {
        StreamInfo streamInfo = syncStatusService.getEntityPipelineStreamStatus(syncariEntityId);
        List<SyncError> errors = analyticsService.getLatestSyncErrorsForEntityPipeline(syncariEntityId);
        streamInfo.setErrorCount(errors.size());
        return streamInfo;
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline/syncMetric/{syncariEntityId}")
    public SyncMetric getEntityPipelineSyncMetric(@PathVariable String syncariEntityId) {
        var graph = mappingGraphService.retrieveEntityGraph(syncariEntityId);
        if (!graph.isPresent()) {
            throw new SyncariValidationException(I18n.i18n("graph_does_not_exist"));
        }
        ;

        SyncMetric syncMetric = syncStatusService.getEntityPipelineSyncMetric(syncariEntityId, true);
        return syncMetric;
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline/errorSummary/{syncariEntityId}")
    public PipelineErrorSummary getEntityPipelineErrorSummary(@PathVariable String syncariEntityId) {
        var graph = mappingGraphService.retrieveEntityGraph(syncariEntityId);
        if (!graph.isPresent()) {
            throw new SyncariValidationException(I18n.i18n("graph_does_not_exist"));
        }

        PipelineErrorSummary errorSummary = syncStatusService.getEntityPipelineErrorSummary(syncariEntityId);
        return errorSummary;
    }


    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline/resyncStatus/{syncariEntityId}")
    public ResyncDetailDTO getEntityPipelineResyncStatus(@PathVariable String syncariEntityId) {
        var graph = mappingGraphService.retrieveEntityGraph(syncariEntityId);
        if (!graph.isPresent()) return null;

        Optional<SyncStream> stream = streamService.findStream(graph.get().getId());
        if (!stream.isPresent()) return null;

        List<String> existingSourcesDefIds = graph.get().getSources().map(s -> s.getConfiguration()
                .getConfigMap().get("entityDefinition").toString()).collect(Collectors.toList());

        Optional<ResyncDetail> resync = resyncService.findLatestResyncDetailForEntityOfExistingMappings(graph.get().getTargetId(), existingSourcesDefIds);
        if (!resync.isPresent()) return null;

        ResyncStatus resyncStatus = resync.get().getStatus();
        return new ResyncDetailDTO(resync.get(), resyncStatus, schemaService, syncStatusService, stream.get());
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/entityPipeline/{syncariEntityId}/version")
    public MappingGraphVersionResponseDTO createPipelineVersion(@PathVariable String syncariEntityId, @RequestBody MappingGraphVersionRequestDTO req) {
        var graph = mappingGraphService.retrieveDraftEntityGraph(syncariEntityId);
        if (graph.isEmpty()) {
            graph = mappingGraphService.retrieveApprovedEntityGraph(syncariEntityId);
        }
        if (graph.isPresent()) {
            log.info("Creating version for graph {}", graph.get().getId());
            var v = graphTransformer.fromVersionRequest(req, ActionType.Manual);
            var versionedGraph = mappingGraphService.createVersion(graph.get(), v);
            return graphTransformer.fromVersion(versionedGraph);
        }
        throw new RuntimeException(String.format("Cannot create pipeline version for syncariEntityId %s", syncariEntityId));
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline/{syncariEntityId}/version")
    public List<MappingGraphVersionResponseDTO> getPipelineVersions(@PathVariable String syncariEntityId) {
        var versionedGraphs = mappingGraphService.getVersions(syncariEntityId);
        if (CollectionUtils.isNotEmpty(versionedGraphs)) {
            return versionedGraphs.stream().map(g -> graphTransformer.fromVersion(g)).collect(Collectors.toList());
        }
        return List.of();
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.DELETE, value = "/entityPipeline/{syncariEntityId}/version")
    public void deletePipelineVersions(@PathVariable String syncariEntityId) {
        mappingGraphService.discardAllVersionsEntityGraph(syncariEntityId);
        log.info("Discarded all versions for {}", syncariEntityId);

    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/entityPipeline/{syncariEntityId}/restoreVersion/{versionId}")
    public void restorePipelineVersion(@PathVariable String syncariEntityId, @PathVariable String versionId, @RequestBody MappingGraphRestoreVersionRequestDTO request) {
        //Create a version of draft before restore
        var graph = mappingGraphService.retrieveDraftEntityGraph(syncariEntityId);
        if (graph.isPresent()) {
            String version = "Unknown";
            String versionName = "Unknown";
            var gra = mappingGraphService.getVersionGraphs(versionId).stream().findFirst();
            if (gra.isPresent()) {
                Version v = gra.get().getVersionInfo();
                if (v != null) {
                    version = "" + v.getVersionNumber();
                    versionName = v.getName();
                }
            }
            MappingGraphVersionRequestDTO createReq = MappingGraphVersionRequestDTO.builder()
                    .name(String.format("Created before restore version #%s (%s)", version, versionName))
                    .summary(String.format("This version was created automatically, just before version #%s was restored", version))
                    .build();
            var v = graphTransformer.fromVersionRequest(createReq, ActionType.Restored);
            mappingGraphService.createVersion(graph.get(), v);
        } else {
            log.info("No active drafts available for entity {}. Skipping version creation before restore", syncariEntityId);
        }
        Map<String, String> restoreRes = Map.of();
        if (request.isRestoreAll()) {
            restoreRes = mappingGraphService.restoreEntityDraft(syncariEntityId, versionId);
        } else {
            if (request.isRestoreEntity()) {
                restoreRes = mappingGraphService.restoreEntityDraft(syncariEntityId, versionId, request.getFieldIds());
            } else {
                var draft = mappingGraphService.retrieveDraftEntityGraph(syncariEntityId);
                if (draft.isPresent()) {
                    restoreRes = mappingGraphService.restoreAttributeDraft(versionId, request.getFieldIds());
                } else {
                    restoreRes = mappingGraphService.restoreEntityDraft(syncariEntityId, versionId, request.getFieldIds());
                }
            }
        }

    }


    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline/{syncariEntityId}/version/{versionId1}/diff")
    public List<Diff> getEntityPipelineVersionDiffSingle(@PathVariable String syncariEntityId, @PathVariable String versionId1) {
        return mappingGraphService.diffVersions(syncariEntityId, null, versionId1);
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline/{syncariEntityId}/settingsMetadata")
    public KeyValue getPipelineSettingsMetadata(@PathVariable String syncariEntityId) {
        return new KeyValue("configurations", List.of(
                new KeyValue("name", "nodeLoggingEnabled").set("label", i18n("label_node_logging_enabled")).set("datatype", "boolean").set("helpUrl", PipelineSettings.NODE_LOGGING_HELP_URL),
                new KeyValue("name", "continuousPipeline").set("label", i18n("label_continuous_pipeline")).set("datatype", "boolean"),
                new KeyValue("name", "simpleLoops").set("label", i18n("label_simple_loops")).set("datatype", "boolean"),
                new KeyValue("name", "realtimePipeline").set("label", i18n("label_realtime_pipeline")).set("datatype", "boolean"),
                new KeyValue("name", "realtimeEndpoint").set("label", i18n("label_realtime_endpoint")).set("datatype", "string"),
                new KeyValue("name", "realtimeIpWhitelist").set("label", i18n("label_realtime_ipwhitelist")).set("datatype", "list"),
                new KeyValue("name", "dataQuality").set("label", i18n("label_dfi_v2")).set("datatype", "boolean")
        ));
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/fieldPipeline/{syncariFieldId}/version/{versionId1}/diff")
    public List<Diff> getFieldPipelineVersionDiffSingle(@PathVariable String syncariFieldId, @PathVariable String versionId1) {
        return mappingGraphService.diffVersions(syncariFieldId, null, versionId1);
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline/{syncariEntityId}/version/{versionId1}/diff/{versionId2}")
    public List<Diff> getEntityPipelineVersionDiffs(@PathVariable String syncariEntityId, @PathVariable String versionId1, @PathVariable String versionId2) {
        return mappingGraphService.diffVersions(syncariEntityId, versionId1, versionId2);
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/fieldPipeline/{syncariFieldId}/version/{versionId1}/diff/{versionId2}")
    public List<Diff> getFieldPipelineVersionDiffs(@PathVariable String syncariFieldId, @PathVariable String versionId1, @PathVariable String versionId2) {
        return mappingGraphService.diffVersions(syncariFieldId, versionId1, versionId2);
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline/{syncariEntityId}/version/{versionId1}/pipelines")
    public List<PipelineVersionInfoDTO> getEntityPipelineDetails(@PathVariable String syncariEntityId, @PathVariable String versionId1) {
        return mappingGraphService.getVersionGraphs(versionId1).stream().map(g -> graphTransformer.toPipelineVersionInfo(g)).collect(Collectors.toList());
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline/{syncariEntityId}/version/{versionId1}/versionPipelines/{versionId2}")
    public List<PipelineVersionInfoDTO> getEntityPipelineDetailsDiff(@PathVariable String syncariEntityId, @PathVariable String versionId1, @PathVariable String versionId2) {
        List<PipelineVersionInfoDTO> details = new ArrayList<>();
        mappingGraphService.getVersionGraphs(versionId1, versionId2).entrySet().stream().forEach(e -> {
            e.getValue().forEach(g -> {
                details.add(graphTransformer.toPipelineVersionInfo(g, e.getKey()));
            });
        });

        return details;
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline/{syncariEntityId}/version/{versionId1}/versionPipelines")
    public List<PipelineVersionInfoDTO> getEntityPipelineDetailsDiffSingle(@PathVariable String syncariEntityId, @PathVariable String versionId1) {
        List<PipelineVersionInfoDTO> details = new ArrayList<>();
        mappingGraphService.getVersionGraphsSingle(versionId1).entrySet().stream().forEach(e -> {
            e.getValue().forEach(g -> {
                details.add(graphTransformer.toPipelineVersionInfo(g, e.getKey()));
            });
        });

        return details;
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/nodeAudit")
    public NodeAuditResponse getNodeAudit(@RequestBody NodeAuditRequest request, @RequestParam(defaultValue = "") String cursor,
                                          @RequestParam String direction,
                                          @RequestParam int count) {
        Page<NodeAudit> page = pipelineNodeAuditService.query(
                request.getSyncariEntityId(), request.getSyncariRecordId(),
                dateUtil.toInstant(request.getStart(), "UTC"),
                dateUtil.toInstant(request.getEnd(), "UTC"),
                new PageCursor(cursor, PageDirection.valueOf(direction), count));
        return new NodeAuditResponse(page.getRecords(), page.getPageInfo(), request.getSyncariRecordId());
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/entityPipeline/{syncariEntityId}/automap/{sourceEntityId}/{mapperType}")
    public List<FieldMappingDTO> automap(@PathVariable String syncariEntityId, @PathVariable String sourceEntityId,
                                         @RequestParam(defaultValue = "false") boolean autoCreateUnmappedFields,
                                         @PathVariable String mapperType) {
        final EntityDefinition syncariEntity = schemaService.getEntity(syncariEntityId);
        final EntityDefinition srcEntity = schemaService.getEntity(sourceEntityId);
        final Connector connector = connectorService.findLite(srcEntity.getConnectorId());
        final MapperType mapperTypeEnum = MapperType.fromValue(mapperType);
        final Map<AttributeDefinition, AttributeDefinition> mappings = autoCreateUnmappedFields ?
                mappingGraphService.automapWithCreate(srcEntity, syncariEntity, mapperTypeEnum) : mappingGraphService.automap(srcEntity, syncariEntity, mapperTypeEnum);
        List<FieldMappingDTO> mappingDTOs = new ArrayList<>();
        mappings.forEach((srcField, syncariField) -> {
            final FieldMappingDTO fieldMappingDTO = buildFieldMapping(connector, srcEntity, syncariEntity, srcField, syncariField, SyncDirection.INBOUND);
            if (syncariField.getId() == null) {
                fieldMappingDTO.setCreateNewSyncariField(true);
            }
            mappingDTOs.add(fieldMappingDTO);
        });
        return mappingDTOs;
    }


}

@Data
@Accessors(chain = true)
class PublishOptions implements Serializable {
    private boolean processAll;

    // true to pushlish read only field pipelines, otherwise all
    private boolean readyOnly;

    // We will be expecting a graph from the UI to use as the new draft when
    // the publishAll is false since the not-ready drafts will stay draft and
    // needs a draft EP. Note that createEP draft always expect a draft from the UI.
    private MappingGraphDTO graph;
    private MappingGraphVersionRequestDTO versionInfo;
}
