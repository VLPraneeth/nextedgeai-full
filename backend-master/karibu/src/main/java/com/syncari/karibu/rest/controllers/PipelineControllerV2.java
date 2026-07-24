package com.syncari.karibu.rest.controllers;

import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.util.Scope;
import com.syncari.core.service.*;
import com.syncari.karibu.rest.config.KaribuConstants;
import com.syncari.karibu.rest.exceptions.BadRequestException;
import com.syncari.karibu.rest.exceptions.NotFoundException;
import com.syncari.karibu.rest.response.PipelineResponse;
import com.syncari.karibu.rest.response.ValidListResponse;
import com.syncari.karibu.rest.response.ValidResponse;
import com.syncari.karibu.rest.util.PipelineUtils;
import com.syncari.karibu.rest.util.ResponseUtils;
import com.syncari.restutils.data.MappingGraphDTO;
import com.syncari.restutils.transformers.GraphTransformer;
import com.syncari.restutils.utils.ApiUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.syncari.core.security.Permissions.READ_STUDIO;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@RestController
@RequestMapping(value = "/api/v2/pipelines")
public class PipelineControllerV2 {

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
                    pipelineId, limit+1);

            List<PipelineResponse> pipelineResponse = new ArrayList<>();
            MappingGraphDTO graphDTO = new MappingGraphDTO();
            for (MappingGraph mg : graphs) {
                log.info("Graph {} with id {} entityId {} versionId {} ", mg.getName(), mg.getId(), mg.getTargetId(),
                        mg.getVersionInfo() != null ? mg.getVersionInfo().getId() : "Not a version");
                graphDTO = graphTransformer.fillDraft(mg,true,true);
                pipelineResponse.add(pipelineUtils.cleanMappingGraphDTO(graphDTO));
            }

            ValidListResponse response = responseUtils.convertDTOToResponse(pipelineResponse, limit);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (IllegalArgumentException iae){
            throw new BadRequestException(i18n("invalid_cursor_token", cursorToken));
        } catch (Exception e) {
            ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/{pipelineId}")
    public ResponseEntity<?> getPipelineByIdV2(@PathVariable String pipelineId) {
        try {
            MappingGraph graph = mappingGraphService.retrieve(pipelineId).orElseThrow(()->
                    new NotFoundException(i18n("mapping_graph_not_found", pipelineId)));

            if(!graph.getScope().equals(Scope.ENTITY))
                throw new NotFoundException(i18n("mapping_graph_not_found", pipelineId));

            MappingGraphDTO graphDto = graphTransformer.fillDraft(graph,true,true);

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
}
