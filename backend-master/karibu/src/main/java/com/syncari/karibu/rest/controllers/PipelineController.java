package com.syncari.karibu.rest.controllers;

import com.ibm.icu.util.VersionInfo;
import com.syncari.api.core.util.TestTransformer;
import com.syncari.api.rest.controllers.data.TestPipelineDTO;
import com.syncari.api.rest.controllers.data.test.PipelineTestRunResultsDTO;
import com.syncari.core.SyncariContext;
import com.syncari.core.actions.Actions;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import com.syncari.core.model.versioning.ActionType;
import com.syncari.core.model.versioning.Version;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.service.*;
import com.syncari.karibu.rest.config.KaribuConstants;
import com.syncari.karibu.rest.exceptions.BadRequestException;
import com.syncari.karibu.rest.exceptions.NotFoundException;
import com.syncari.karibu.rest.request.PipelineRequest;
import com.syncari.karibu.rest.request.PipelineResyncRequest;
import com.syncari.karibu.rest.response.ErrorType;
import com.syncari.karibu.rest.response.PipelineResponse;
import com.syncari.karibu.rest.response.ValidListResponse;
import com.syncari.karibu.rest.response.ValidResponse;
import com.syncari.karibu.rest.util.PipelineUtils;
import com.syncari.karibu.rest.util.ResponseUtils;
import com.syncari.restutils.data.MappingGraphDTO;
import com.syncari.restutils.transformers.GraphTransformer;
import com.syncari.restutils.utils.ApiUtils;
import com.syncari.utils.DateUtil;
import com.syncari.utils.I18n;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

import static com.syncari.core.security.Permissions.READ_STUDIO;
import static com.syncari.core.security.Permissions.WRITE_STUDIO;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/pipelines")
public class PipelineController {

    @Autowired
    MappingGraphService mappingGraphService;

    @Autowired
    SchemaService schemaService;

    @Autowired
    LayoutService layoutService;

    @Autowired
    StreamService streamService;

    @Autowired
    ResyncService resyncService;

    @Autowired
    PipelineUtils pipelineUtils;

    @Autowired
    ApiUtils apiUtils;

    @Autowired
    GraphTransformer graphTransformer;

    @Autowired
    ResponseUtils responseUtils;

    @Autowired
    DateUtil dateUtil;

    @Autowired
    PipelineTestService pipelineTestService;

    @Autowired
    TestTransformer transformer;
    @Autowired
    Actions actions;

    List statuses = Arrays.asList("approved", "draft");

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<?> listPipelines(@RequestParam(value = "status", defaultValue = "approved") String requestStatus,
                                           @RequestParam(value = "cursorToken", required = false) String cursorToken,
                                           @RequestParam(value = "limit", required = false, defaultValue = KaribuConstants.MAX_LIMIT_STRING) Integer limit) {
        try {
            // validate limit mx value
            if (limit > KaribuConstants.MAX_LIMIT)
                throw new BadRequestException(i18n("limit_max_value_error", limit, KaribuConstants.MAX_LIMIT));

            // validate status
            if (!statuses.contains(requestStatus))
                throw new BadRequestException(i18n("invalid_entity_status", statuses.toString()));

            // convert draft status to new
            String status = requestStatus;
            if (requestStatus.equalsIgnoreCase("draft"))
                status = "NEW";

            // get pipeline id
            String pipelineId = null;
            if (cursorToken != null)
                pipelineId = apiUtils.decodeCursor(cursorToken);

            // get pipelines
            List<MappingGraph> graphs = mappingGraphService.retrieveEntityMappingGraphsByDraftStatus(DraftStatus.valueOf(status.toUpperCase()),
                    pipelineId, limit + 1);

            List<PipelineResponse> pipelineResponse = new ArrayList<>();
            MappingGraphDTO graphDTO = new MappingGraphDTO();
            for (MappingGraph mg : graphs) {
                log.info("Graph {} with id {} entityId {} versionId {} ", mg.getName(), mg.getId(), mg.getTargetId(),
                        mg.getVersionInfo() != null ? mg.getVersionInfo().getId() : "Not a version");
                graphDTO = graphTransformer.fillDraft(mg);
                pipelineResponse.add(pipelineUtils.cleanMappingGraphDTO(graphDTO));
            }

            ValidListResponse response = responseUtils.convertDTOToResponse(pipelineResponse, limit);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (IllegalArgumentException iae) {
            throw new BadRequestException(i18n("invalid_cursor_token", cursorToken));
        } catch (Exception e) {
            ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/{pipelineId}/fieldPipelines")
    public ResponseEntity<?> listFieldPipelines(@PathVariable String pipelineId,
                                                @RequestParam(value = "cursorToken", required = false) String cursorToken,
                                                @RequestParam(value = "limit", required = false, defaultValue = KaribuConstants.MAX_LIMIT_STRING) Integer limit) {
        try {
            MappingGraph entityGraph = mappingGraphService.retrieve(pipelineId).orElseThrow(() ->
                    new NotFoundException(i18n("mapping_graph_not_found", pipelineId)));

            if (!entityGraph.getScope().equals(Scope.ENTITY))
                throw new NotFoundException(i18n("mapping_graph_not_found", pipelineId));

            // get pipeline id
            String fieldPipelineId = null;
            if (cursorToken != null)
                fieldPipelineId = apiUtils.decodeCursor(cursorToken);

            List<MappingGraph> fieldMappings = mappingGraphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId(),
                    fieldPipelineId, limit + 1, true);

            List<PipelineResponse> fieldPipelineResponse = new ArrayList<>();
            MappingGraphDTO fieldGraphDTO = new MappingGraphDTO();
            for (MappingGraph mg : fieldMappings) {
                fieldGraphDTO = graphTransformer.fillDraft(mg);
                fieldPipelineResponse.add(pipelineUtils.cleanMappingGraphDTO(fieldGraphDTO));
            }

            ValidListResponse response = responseUtils.convertDTOToResponse(fieldPipelineResponse, limit);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            // check for approved pipeline not found
            if (StringUtils.contains(e.getMessage(), "not found")) {
                throw new NotFoundException(StringUtils.replace(e.getMessage(), "MappingGraph", "Pipeline"));
            } else {
                // throw conflict error for all other errors
                ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
        }
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/{pipelineId}")
    public ResponseEntity<?> getPipelineById(@PathVariable String pipelineId) {
        try {
            MappingGraph graph = mappingGraphService.retrieve(pipelineId).orElseThrow(() ->
                    new NotFoundException(i18n("mapping_graph_not_found", pipelineId)));

            if (!graph.getScope().equals(Scope.ENTITY))
                throw new NotFoundException(i18n("mapping_graph_not_found", pipelineId));

            MappingGraphDTO graphDto = graphTransformer.fillDraft(graph);

            PipelineResponse pipelineResponse = pipelineUtils.cleanMappingGraphDTO(graphDto);

            ValidResponse response = responseUtils.convertDTOToResponse(pipelineResponse);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            // check for approved pipeline not found
            if (StringUtils.contains(e.getMessage(), "not found")) {
                throw new NotFoundException(StringUtils.replace(e.getMessage(), "MappingGraph", "Pipeline"));
            } else {
                // throw conflict error for all other errors
                ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
        }
    }


    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/{pipelineId}/fieldPipelines/{fieldPipelineId}")
    public ResponseEntity<?> getFieldPipelineById(@PathVariable String pipelineId,
                                                  @PathVariable String fieldPipelineId) {
        try {
            MappingGraph graph = pipelineUtils.validateEntityFieldPipelineIds(pipelineId, fieldPipelineId);

            if (!graph.getScope().equals(Scope.ATTRIBUTE))
                throw new NotFoundException(i18n("mapping_field_graph_not_found", fieldPipelineId));

            MappingGraphDTO graphDto = graphTransformer.fillDraft(graph);

            PipelineResponse pipelineResponse = pipelineUtils.cleanMappingGraphDTO(graphDto);

            ValidResponse response = responseUtils.convertDTOToResponse(pipelineResponse);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            // check for approved pipeline not found
            if (StringUtils.contains(e.getMessage(), "not found")) {
                throw new NotFoundException(StringUtils.replace(e.getMessage(), "MappingGraph", "Pipeline"));
            } else {
                // throw conflict error for all other errors
                ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
        }
    }


    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST)
    public ResponseEntity<ValidResponse> createPipeline(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                                        @RequestBody PipelineRequest pipelineRequest) {
        try {
            pipelineUtils.validateNodes(null, pipelineRequest);

            MappingGraphDTO graphRequestDTO = pipelineUtils.convertPipelineCreateRequest(pipelineRequest, Scope.ENTITY);

            MappingGraphDTO graphDTO = graphTransformer.toMappingGraphDTO(graphTransformer.createEntityPipelineDraft(graphRequestDTO));

            PipelineResponse pipelineResponse = pipelineUtils.cleanMappingGraphDTO(graphDTO);

            ValidResponse response = responseUtils.convertDTOToResponse(pipelineResponse);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (NotFoundException nfe) {
            throw new NotFoundException(StringUtils.replace(nfe.getMessage(), "MappingGraph", "Pipeline"));
        } catch (Exception e) {
            ValidResponse response = responseUtils.populateErrorResponse(StringUtils.replace(e.getMessage(), "graph", "pipeline"));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/{pipelineId}/fieldPipelines")
    public ResponseEntity<ValidResponse> createFieldPipeline(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                                             @PathVariable String pipelineId,
                                                             @RequestBody PipelineRequest fieldPipelineRequest) {
        try {
            pipelineUtils.validateNodes(null, fieldPipelineRequest);

            MappingGraphDTO graphRequestDTO = pipelineUtils.convertPipelineCreateRequest(fieldPipelineRequest, Scope.ATTRIBUTE);

            MappingGraph newGraph = mappingGraphService.upsertAttributeGraph(graphTransformer.toMappingGraph(graphRequestDTO));

            List<Layout> layouts = graphTransformer.extractLayout(graphRequestDTO);
            layoutService.upsert(layouts);

            MappingGraphDTO graphDTO = graphTransformer.toMappingGraphDTO(newGraph);

            PipelineResponse pipelineResponse = pipelineUtils.cleanMappingGraphDTO(graphDTO);

            ValidResponse response = responseUtils.convertDTOToResponse(pipelineResponse);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (NotFoundException nfe) {
            nfe.printStackTrace();
            throw new NotFoundException(StringUtils.replace(nfe.getMessage(), "MappingGraph", "Pipeline"));
        } catch (Exception e) {
            e.printStackTrace();
            ValidResponse response = responseUtils.populateErrorResponse(StringUtils.replace(e.getMessage(), "graph", "pipeline"));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }


    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.PUT, value = "/{pipelineId}")
    public ResponseEntity<ValidResponse> updateEntityPipeline(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                                              @PathVariable String pipelineId,
                                                              @RequestBody PipelineRequest pipelineRequest) {
        try {
            pipelineUtils.validateUpdateEntityPipeline(pipelineId);

            pipelineUtils.validateNodes(pipelineId, pipelineRequest);

            pipelineRequest.setId(pipelineId);

            MappingGraphDTO graphRequestDTO = pipelineUtils.convertPipelineUpdateRequest(pipelineId, pipelineRequest, Scope.ENTITY);

            MappingGraph savedGraph = graphTransformer.createEntityPipelineDraft(graphRequestDTO);

            MappingGraphDTO graphDTO = graphTransformer.toMappingGraphDTO(savedGraph);

            PipelineResponse pipelineResponse = pipelineUtils.cleanMappingGraphDTO(graphDTO);

            ValidResponse response = responseUtils.convertDTOToResponse(pipelineResponse);
            return ResponseEntity.status(HttpStatus.OK).body(response);

        } catch (NotFoundException nfe) {
            nfe.printStackTrace();
            throw new NotFoundException(StringUtils.replace(nfe.getMessage(), "MappingGraph", "Pipeline"));
        } catch (Exception e) {
            e.printStackTrace();
            ValidResponse response = responseUtils.populateErrorResponse(StringUtils.replace(e.getMessage(), "graph", "pipeline"));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }


    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.PUT, value = "/{pipelineId}/fieldPipelines/{fieldPipelineId}")
    public ResponseEntity<ValidResponse> updateFieldPipeline(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                                             @PathVariable String pipelineId,
                                                             @PathVariable String fieldPipelineId,
                                                             @RequestBody PipelineRequest fieldPipelineRequest) {
        try {
            pipelineUtils.validateEntityFieldPipelineIds(pipelineId, fieldPipelineId);

            pipelineUtils.validateUpdateFieldPipeline(fieldPipelineId);

            pipelineUtils.validateNodes(fieldPipelineId, fieldPipelineRequest);

            fieldPipelineRequest.setId(fieldPipelineId);

            MappingGraphDTO graphRequestDTO = pipelineUtils.convertPipelineUpdateRequest(fieldPipelineId, fieldPipelineRequest, Scope.ATTRIBUTE);

            MappingGraph newGraph = mappingGraphService.upsertAttributeGraph(graphTransformer.toMappingGraph(graphRequestDTO));

            List<Layout> layouts = graphTransformer.extractLayout(graphRequestDTO);
            layoutService.upsert(layouts);

            MappingGraphDTO graphDTO = graphTransformer.toMappingGraphDTO(newGraph);

            PipelineResponse pipelineResponse = pipelineUtils.cleanMappingGraphDTO(graphDTO);

            ValidResponse response = responseUtils.convertDTOToResponse(pipelineResponse);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (NotFoundException nfe) {
            nfe.printStackTrace();
            throw new NotFoundException(StringUtils.replace(nfe.getMessage(), "MappingGraph", " Field pipeline"));
        } catch (Exception e) {
            e.printStackTrace();
            ValidResponse response = responseUtils.populateErrorResponse(StringUtils.replace(e.getMessage(), "graph", "field pipeline"));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }


    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/{pipelineId}/publish")
    public ResponseEntity<?> publishPipeline(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                             @PathVariable String pipelineId) {

        try {
            MappingGraph graph = mappingGraphService.retrieve(pipelineId).orElseThrow(() ->
                    new NotFoundException(i18n("mapping_graph_not_found", pipelineId)));

            if (!graph.getScope().equals(Scope.ENTITY))
                throw new NotFoundException(i18n("mapping_graph_not_entity", pipelineId));

            if (graph.getDraftStatus().equals(DraftStatus.APPROVED))
                throw new NotFoundException(i18n("mapping_graph_not_draft", pipelineId));

            if (graph.getDraftStatus().equals(DraftStatus.ARCHIVED))
                throw new NotFoundException(i18n("mapping_graph_not_found", pipelineId));

            // default options
            boolean isProcessAll = false;
            boolean isReadyOnly = false;

            mappingGraphService.validateGraph(graph.getId(), isReadyOnly);

            log.info("Approving {} draft for {} with processAll {}", graph.getTargetId(), SyncariContext.getSyncariId(), isProcessAll);
            String versionName = ("Api publish_" + new Date()) + "_" + System.currentTimeMillis();
            Version publishVersion = graphTransformer.fromVersionRequest(null, ActionType.Published).setName(versionName);
            mappingGraphService.approveDraft(graph, isProcessAll, isReadyOnly,publishVersion);

            MappingGraph publishedGraph = mappingGraphService.retrieveApprovedEntityGraph(graph.getTargetId()).get();

            MappingGraphDTO graphDto = graphTransformer.fillDraft(publishedGraph);

            PipelineResponse pipelineResponse = pipelineUtils.cleanMappingGraphDTO(graphDto);

            ValidResponse response = responseUtils.convertDTOToResponse(pipelineResponse);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            log.error("Exception occurred for pipelineId {} while publishing, stacktrace is {}", pipelineId, ExceptionUtils.getStackTrace(e));
            // check for approved pipeline not found
            if (StringUtils.contains(e.getMessage(), "not found")) {
                throw new NotFoundException(StringUtils.replace(e.getMessage(), "MappingGraph", "Pipeline"));
            } else {
                // throw conflict error for all other errors
                ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
        }
    }


    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/{pipelineId}/createDraft")
    public ResponseEntity<ValidResponse> createEntityPipelineDraft(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                                                   @PathVariable String pipelineId) {
        try {
            MappingGraph entityGraph = mappingGraphService.retrieve(pipelineId).orElseThrow(() ->
                    new NotFoundException(i18n("mapping_graph_not_found", pipelineId)));

            if (entityGraph.getDraftStatus().equals(DraftStatus.ARCHIVED) || entityGraph.getScope().equals(Scope.ATTRIBUTE))
                throw new NotFoundException(i18n("mapping_graph_not_found", pipelineId));

            if (!entityGraph.getDraftStatus().equals(DraftStatus.APPROVED))
                throw new RuntimeException(i18n("create_draft_off_approved_pipeline", pipelineId));

            Optional<MappingGraph> draftEntityGraph = mappingGraphService.retrieveDraftEntityGraph(entityGraph.getTargetId());

            if (draftEntityGraph.isPresent())
                throw new RuntimeException(i18n("pipeline_has_draft", pipelineId, draftEntityGraph.get().getId()));

            MappingGraph newDraftEntityGraph = graphTransformer.createEntityPipelineDraft(graphTransformer.fillDraft(entityGraph));

            PipelineResponse pipelineResponse = pipelineUtils.cleanMappingGraphDTO(graphTransformer.fillDraft(newDraftEntityGraph));

            ValidResponse response = responseUtils.convertDTOToResponse(pipelineResponse);
            return ResponseEntity.status(HttpStatus.OK).body(response);

        } catch (NotFoundException nfe) {
            throw new NotFoundException(StringUtils.replace(nfe.getMessage(), "MappingGraph", "Pipeline"));
        } catch (Exception e) {
            ValidResponse response = responseUtils.populateErrorResponse(StringUtils.replace(e.getMessage(), "graph", "pipeline"));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }


    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.DELETE, value = "/{pipelineId}")
    public ResponseEntity<ValidResponse> deleteEntityPipeline(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                                              @PathVariable String pipelineId) {
        try {
            MappingGraph graph = mappingGraphService.retrieve(pipelineId).orElseThrow(() ->
                    new NotFoundException(i18n("mapping_graph_not_found", pipelineId)));

            if (!graph.getScope().equals(Scope.ENTITY))
                throw new NotFoundException(i18n("mapping_graph_not_found", pipelineId));

            switch (graph.getDraftStatus()) {
                case NEW:
                    mappingGraphService.discardDraftEntityGraph(graph.getTargetId());
                    break;
                case APPROVED:
                    mappingGraphService.deleteApprovedEntityGraph(graph.getTargetId());
                    break;
                case ARCHIVED:
                    throw new NotFoundException(i18n("mapping_graph_not_found", pipelineId));
            }

            PipelineResponse pipelineResponse = pipelineUtils.cleanMappingGraphDTO(graphTransformer.fillDraft(graph));
            pipelineResponse.setDraftStatus("DELETED");
            pipelineResponse.setNodes(new ArrayList<>());
            pipelineResponse.setEdges(new ArrayList<>());

            ValidResponse response = responseUtils.convertDTOToResponse(pipelineResponse);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            // check for approved pipeline not found
            if (StringUtils.contains(e.getMessage(), "not found")) {
                throw new NotFoundException(StringUtils.replace(e.getMessage(), "MappingGraph", "Pipeline"));
            } else {
                // throw conflict error for all other errors
                ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
        }
    }


    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.DELETE, value = "/{pipelineId}/fieldPipelines/{fieldPipelineId}")
    public ResponseEntity<ValidResponse> deleteFieldPipeline(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                                             @PathVariable String pipelineId,
                                                             @PathVariable String fieldPipelineId) {

        try {
            MappingGraph fieldGraph = pipelineUtils.validateEntityFieldPipelineIds(pipelineId, fieldPipelineId);

            switch (fieldGraph.getDraftStatus()) {
                case NEW:
                    mappingGraphService.discardDraftFieldGraph(fieldGraph.getTargetId());
                    break;
                case APPROVED:
                    mappingGraphService.deleteApprovedFieldgraph(fieldGraph.getTargetId());
                    break;
                case ARCHIVED:
                    throw new NotFoundException(i18n("mapping_graph_not_found", pipelineId));
            }

            ValidResponse response = responseUtils.convertDTOToResponse(Map.of("deleteCount", 1));
            return ResponseEntity.status(HttpStatus.OK).body(response);

        } catch (NotFoundException nfe) {
            throw new NotFoundException(StringUtils.replace(nfe.getMessage(), "MappingGraph", "Pipeline"));
        } catch (Exception e) {
            // check for synapse not found
            if (StringUtils.contains(e.getMessage(), "not found")) {
                throw new NotFoundException(e.getMessage());
            } else {
                // throw conflict error for all other errors
                ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
        }
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/{pipelineId}/testResult/{testId}")
    public ResponseEntity<ValidResponse> getTestResult(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                                     @PathVariable String pipelineId,@PathVariable String testId) {
        try {
            //MappingGraph graph = pipelineUtils.getAndValidatePipelineForSync(pipelineId);
            Optional<PipelineTest> pipelineTest = pipelineTestService.getTestByIdAndGraphId(pipelineId, testId);
            if (!pipelineTest.isPresent()) {
                throw new SyncariValidationException(i18n("pipeline_test_not_found"));
            }
            List<TestResult> runResult = pipelineTestService.getEntityPipelineTestResults(pipelineTest.get().getId());
            PipelineTestRunResultsDTO dto =  transformer.toPipelineTestResultDTO(pipelineTest.get(), runResult);
            ValidResponse response = responseUtils.convertDTOToResponse(dto);
            return ResponseEntity.status(HttpStatus.OK).body(response);

        } catch (SyncariValidationException se) {
            throw new BadRequestException(se.getMessage());
        }
        catch (Exception e) {
            ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/{pipelineId}/test")
    public ResponseEntity<ValidResponse> runLiveTest(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                                       @PathVariable String pipelineId,@RequestBody TestPipelineDTO filter) {
        Instant startTime = null;
        Instant endTime = null;
        Map<String, List<String>> recordIds = filter.getRecordIds();
        Map<String, PipelineTestWebhook> webhook = filter.getWebhook();

        try {
            MappingGraph graph = mappingGraphService.retrieve(pipelineId)
                    .orElseThrow(() -> new NotFoundException(MappingGraph.class, "Id", pipelineId));
            String syncariEntityId = graph.getTargetId();
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
            Optional<PipelineTest> pipelineTest = pipelineTestService.getTestByIdAndGraphId(pipelineId, "latest");
            if (pipelineTest.isPresent() && (pipelineTest.get().getStatus().equals(Status.NEW) || pipelineTest.get().getStatus().equals(Status.PROCESSING))){
                throw new SyncariValidationException(I18n.i18n("test_in_progress"));
            }
            String testId = mappingGraphService.testEntityGraph(syncariEntityId, startTime, endTime,
                    filter.getLimit() == null ? 100 : Long.parseLong(filter.getLimit()), recordIds, webhook);

            ValidResponse response = responseUtils.convertDTOToResponse(Map.of("success", "true","testId", testId));
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } catch (SyncariValidationException se){
            throw new BadRequestException(se.getMessage());
        }catch (Exception e) {
            // throw conflict error for all other errors
            ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/{pipelineId}/pause")
    public ResponseEntity<ValidResponse> pausePipeline(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                                       @PathVariable String pipelineId) {

        try {
            MappingGraph graph = pipelineUtils.getAndValidatePipelineForSync(pipelineId);

            SyncStream stream = streamService.findStream(pipelineId).orElseThrow(()->
                    new NotFoundException(i18n("stream_not_found", pipelineId)));

            if(stream.getStatus() == SyncStream.Status.PAUSING || stream.getStatus() == SyncStream.Status.PAUSED)
                throw new Exception(i18n("stream_already_paused", pipelineId));

            mappingGraphService.pauseStream(graph.getTargetId());

            SyncStream syncStream = streamService.findStream(graph.getId())
                    .orElseThrow(() -> new NotFoundException(i18n("sync_not_found", graph.getId())));

            ValidResponse response = responseUtils.convertDTOToResponse(Map.of("syncStatus", syncStream.getStatus()));
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

        } catch (Exception e) {
            // check for approved pipeline not found
            if (StringUtils.contains(e.getMessage(), "not found")) {
                throw new NotFoundException(StringUtils.replace(e.getMessage(), "MappingGraph", "Pipeline"));
            } else {
                // throw conflict error for all other errors
                ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
        }
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/{pipelineId}/resume")
    public ResponseEntity<ValidResponse> resumePipeline(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                                        @PathVariable String pipelineId) {

        try {
            MappingGraph graph = pipelineUtils.getAndValidatePipelineForSync(pipelineId);

            SyncStream stream = streamService.findStream(pipelineId).orElseThrow(()->
                    new NotFoundException(i18n("stream_not_found", pipelineId)));

            if(stream.getStatus() != SyncStream.Status.PAUSED)
                throw new Exception(i18n("stream_not_paused", pipelineId));

            mappingGraphService.restart(graph.getTargetId());

            SyncStream syncStream = streamService.findStream(graph.getId())
                    .orElseThrow(() -> new NotFoundException(i18n("sync_not_found", graph.getId())));

            ValidResponse response = responseUtils.convertDTOToResponse(Map.of("syncStatus", syncStream.getStatus()));
            return ResponseEntity.status(HttpStatus.OK).body(response);

        } catch (Exception e) {
            // check for approved pipeline not found
            if (StringUtils.contains(e.getMessage(), "not found")) {
                throw new NotFoundException(StringUtils.replace(e.getMessage(), "MappingGraph", "Pipeline"));
            } else {
                // throw conflict error for all other errors
                ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
        }
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/{pipelineId}/resync")
    public ResponseEntity<ValidResponse> resyncPipeline(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                                        @PathVariable String pipelineId,
                                                        @RequestBody PipelineResyncRequest pipelineResyncRequest) {

        try {
            MappingGraph graph = mappingGraphService.retrieve(pipelineId).orElseThrow(() ->
                    new NotFoundException(i18n("mapping_graph_not_found", pipelineId)));

            if (!graph.getScope().equals(Scope.ENTITY))
                throw new NotFoundException(i18n("mapping_graph_not_entity", pipelineId));

            if (!graph.getDraftStatus().equals(DraftStatus.APPROVED))
                throw new NotFoundException(i18n("mapping_graph_not_approved", pipelineId));

            List<String> entityIds = pipelineUtils.getResyncEntityIds(graph, pipelineResyncRequest);
            Instant startTime = StringUtils.isBlank(pipelineResyncRequest.getFromDate()) ? Instant.ofEpochMilli(0) : apiUtils.getDateTimeInstant(pipelineResyncRequest.getFromDate());
            Instant endTime = StringUtils.isBlank(pipelineResyncRequest.getToDate()) ? Instant.now() : Instant.now().isBefore(Instant.parse(pipelineResyncRequest.getToDate())) ? Instant.now() : Instant.parse(pipelineResyncRequest.getToDate());

            if (startTime.toEpochMilli() > endTime.toEpochMilli()) {
                throw new RuntimeException("Start time should be less than End time");
            }

            resyncService.createResyncRequest(graph.getTargetId(), entityIds, startTime, endTime);

            ValidResponse response = responseUtils.convertDTOToResponse(Map.of("resyncStatus", "queued"));
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } catch (NotFoundException nfe) {
            throw new NotFoundException(StringUtils.replace(nfe.getMessage(), "MappingGraph", "Pipeline"));
        } catch (BadRequestException bre) {
            throw new BadRequestException(bre.getMessage());
        } catch (DateTimeParseException dtpe) {
            throw new BadRequestException(i18n("bad_date_format", pipelineResyncRequest.getFromDate()));
        } catch (Exception e) {
            // throw conflict error for all other errors
            ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/{pipelineId}/validate")
    public ResponseEntity<ValidResponse> validatePipeline(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                                        @PathVariable String pipelineId) {

        try {
            MappingGraph graph = mappingGraphService.retrieve(pipelineId).orElseThrow(() ->
                    new NotFoundException(i18n("mapping_graph_not_found", pipelineId)));

            if (!graph.getScope().equals(Scope.ENTITY))
                throw new RuntimeException(i18n("mapping_graph_not_entity", pipelineId));

            if (graph.getDraftStatus().equals(DraftStatus.APPROVED))
                throw new RuntimeException(i18n("mapping_graph_not_draft", pipelineId));

            EntityDefinition coreEntity = mappingGraphService.getCoreEntity(graph);
            Map<String, EntityDefinition> sourceEntitiesMap = mappingGraphService.getConnectedSourceEntityMap(graph);
            var validationErrors = mappingGraphService.validateGraphWithoutException(graph, coreEntity, sourceEntitiesMap, new HashMap<String, Object>());

            if (!validationErrors.isEmpty()) {
                List<Map<String, String>> validationErrorResponse = pipelineUtils.getValidationErrorResponse(validationErrors);
                ValidResponse response = responseUtils.populateErrorResponse(new ErrorType(i18n("entity_pipeline_validation_errors", pipelineId),
                        validationErrorResponse, true));
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }

            ValidResponse response = responseUtils.convertDTOToResponse(Map.of("id", pipelineId, "validationStatus", "success"));
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (NotFoundException nfe) {
            throw new NotFoundException(StringUtils.replace(nfe.getMessage(), "MappingGraph", "Pipeline"));
        } catch (BadRequestException bre) {
            throw new BadRequestException(bre.getMessage());
        } catch (Exception e) {
            // throw conflict error for all other errors
            ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/{pipelineId}/fieldPipelines/{fieldPipelineId}/validate")
    public ResponseEntity<?> validateFieldPipeline(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                                   @PathVariable String pipelineId,
                                                   @PathVariable String fieldPipelineId) {
        try {
            MappingGraph graph = pipelineUtils.validateEntityFieldPipelineIds(pipelineId, fieldPipelineId);

            if(!graph.getScope().equals(Scope.ATTRIBUTE))
                throw new NotFoundException(i18n("mapping_field_graph_not_found", fieldPipelineId));

            if (graph.getDraftStatus().equals(DraftStatus.APPROVED))
                throw new RuntimeException(i18n("field_mapping_graph_not_draft", fieldPipelineId));

            EntityDefinition coreEntity = mappingGraphService.getCoreEntity(graph);
            Map<String, EntityDefinition> sourceEntitiesMap = mappingGraphService.getConnectedSourceEntityMap(graph);
            var validationErrors = mappingGraphService.validateGraphWithoutException(graph, coreEntity, sourceEntitiesMap, new HashMap<String, Object>());

            if (!validationErrors.isEmpty()) {
                List<Map<String, String>> validationErrorResponse = pipelineUtils.getValidationErrorResponse(validationErrors);
                ValidResponse response = responseUtils.populateErrorResponse(new ErrorType(i18n("field_pipeline_validation_errors", fieldPipelineId),
                        validationErrorResponse, true));
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }

            ValidResponse response = responseUtils.convertDTOToResponse(Map.of("id", fieldPipelineId, "validationStatus", "success"));
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            // check for approved pipeline not found
            if (StringUtils.contains(e.getMessage(), "not found")) {
                throw new NotFoundException(StringUtils.replace(e.getMessage(), "MappingGraph", "Pipeline"));
            } else {
                // throw conflict error for all other errors
                ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
        }
    }

    @Secured(READ_STUDIO)
	@RequestMapping(method = RequestMethod.GET, value = "/functions")
	public ResponseEntity<?> listFunctions(@RequestParam(value = "scope", required = false) String scope) {
		try {
			if (!StringUtils.isBlank(scope)
					&& !(scope.equalsIgnoreCase(Scope.ENTITY.name()) || scope.equalsIgnoreCase(Scope.ATTRIBUTE.name())))
				throw new BadRequestException(i18n("invalid_scope", scope));

			List<FunctionDefinition> functionResponse = schemaService
					.getFunctions(StringUtils.isBlank(scope) ? Scope.ENTITY : Scope.valueOf(scope.toUpperCase()));
			ValidListResponse response = responseUtils.convertDTOToResponse(pipelineUtils.getFunction(functionResponse),
					false);
			return ResponseEntity.status(HttpStatus.OK).body(response);
		} catch (Exception e) {
			ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
			return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
		}
	}

	@Secured(READ_STUDIO)
	@RequestMapping(method = RequestMethod.GET, value = "/actions")
	public ResponseEntity<?> listActions(@RequestParam(value = "scope", required = false) String scope) {
		try {
			if (!StringUtils.isBlank(scope)
					&& !(scope.equalsIgnoreCase(Scope.ENTITY.name()) || scope.equalsIgnoreCase(Scope.ATTRIBUTE.name())))
				throw new BadRequestException(i18n("invalid_scope", scope));

			List<ActionDefinition> actionResponse = schemaService.getActions();
			ValidListResponse response = responseUtils.convertDTOToResponse(pipelineUtils.getAction(actionResponse),
					false);
			return ResponseEntity.status(HttpStatus.OK).body(response);
		} catch (Exception e) {
			ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
			return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
		}
	}
    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/actions/execute/{name}")
    public ResponseEntity<?> executeAction(@PathVariable String name, @RequestBody Map params) {
        try {
            GenericActionConfig actionConfig = new GenericActionConfig().setConfigMap(params);
            GraphContext graphContext = new GraphContext();
            MappingGraph graph = new MappingGraph().setName(name);
            graph.setId(name);
            graphContext.setGraph(graph);
            MappingNode mappingNode = new MappingNode().setApiName(name).setName(name);
            mappingNode.setId(ObjectId.get().toHexString());
            graphContext.setCurrentNode(mappingNode);
            ActionResult actionResult = actions.dispatch(name, actionConfig, graphContext);
            if(!actionResult.isStatus()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(actionResult.getError().getMessage());
            }
            return ResponseEntity.status(HttpStatus.OK).body(actionResult.getResult());
        } catch (Exception e) {
            log.error(ExceptionUtils.getStackTrace(e));
            ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

}
