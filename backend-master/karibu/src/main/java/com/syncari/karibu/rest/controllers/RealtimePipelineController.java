package com.syncari.karibu.rest.controllers;

import com.syncari.core.SyncariContext;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.PipelineSettings;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.SchemaService;
import com.syncari.karibu.rest.config.ControllerInterceptor;
import com.syncari.karibu.rest.config.KaribuConfig;
import com.syncari.karibu.rest.config.security.SecurityConstants;
import com.syncari.karibu.rest.exceptions.BadRequestException;
import com.syncari.karibu.rest.exceptions.NotFoundException;
import com.syncari.karibu.rest.exceptions.UnauthorizedException;
import com.syncari.karibu.rest.util.IPValidator;
import com.syncari.karibu.rest.util.ViperUtils;
import com.syncari.utils.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.EXECUTE_REALTIME_PIPELINE;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/realtime")
public class RealtimePipelineController {
    @Autowired
    KaribuConfig karibuConfig;

    @Autowired
    ViperUtils viperUtils;

    @Autowired
    MappingGraphService mappingGraphService;

    @Autowired
    SchemaService schemaService;

    @Autowired
    IPValidator ipValidator;

    @Secured(EXECUTE_REALTIME_PIPELINE)
    @RequestMapping(method = RequestMethod.POST, value = "/{suffix}")
    public ResponseEntity<String> executeRealtimePipeline(
            @PathVariable("suffix") String suffix, @RequestBody(required = false) String body, HttpServletRequest request
            ) {
        String instanceId = SyncariContext.getSyncariId();

        String syncariIdInHeader = request.getHeader(SecurityConstants.SYNCARI_ID);
        String requestId = (String) request.getAttribute(ControllerInterceptor.REQUEST_ID);
        if (StringUtils.isEmpty(syncariIdInHeader))
            throw new BadRequestException(i18n("syncariId_mandatory_header"));

        log.debug("Request received for instanceId {}, suffix {}, body {}", instanceId, suffix, body);
        Optional<MappingGraph> mappingGraph = mappingGraphService.getPipelineByEndPointSuffix(suffix);

        if (mappingGraph.isEmpty()) {
            throw new NotFoundException(String.format("Mapping graph not found for endpoint suffix %s", suffix));
        }
        ResponseEntity<String> responseEntity;
        MappingGraph graph = mappingGraph.get();
        PipelineSettings graphPipelineSettings = graph.getSettings();
        if ((null != graphPipelineSettings) && (StringUtils.isNotEmpty(graphPipelineSettings.getRealtimeIpWhitelist()))){
            List<String> ipWhiteLists = Arrays.stream(graphPipelineSettings.getRealtimeIpWhitelist().split("\n")).collect(Collectors.toList());
            String requestIpAddress = request.getHeader("X-FORWARDED-FOR");
            if (requestIpAddress == null) {
                requestIpAddress = request.getRemoteAddr();
            }
            try{
                if (!ipValidator.isClientIpPermitted(requestIpAddress, ipWhiteLists)){
                    throw new AccessDeniedException(String.format("Access Denied"));
                }
            }catch (Exception e){
                log.error("Exception occurred while validation client ip permission, not blocking pipeline",e);
            }
        }

        String targetEntityId = graph.getTargetId();
        log.debug("Graph id is {} with targetEntityId {}", graph, targetEntityId);
        if (graph.getSources().findFirst().isPresent()) {
            MappingNode sourceNode = graph.getSources().findFirst().get();
            if (sourceNode.getEntityDefinitionId().isPresent()) {
                Optional<EntityDefinition> entityDefinition = schemaService.findEntity(sourceNode.getEntityDefinitionId().get());
                if (entityDefinition.isPresent()) {
                    try {
                        responseEntity = viperUtils.callRealtimeSync(targetEntityId, instanceId, entityDefinition.get().getConnectorId(), requestId, body);
                    } catch (RestClientResponseException e) {
                        return new ResponseEntity<>(e.getResponseBodyAsString(), e.getResponseHeaders(), HttpStatus.valueOf(e.getRawStatusCode()));
                    }
                } else {
                    throw new NotFoundException(String.format("Entity not found for endpoint suffix %s", suffix));
                }
            } else {
                throw new NotFoundException(String.format("Mapping node not found for endpoint suffix %s", suffix));
            }
        } else {
            throw new NotFoundException(String.format("Webhook source not found for endpoint suffix %s", suffix));
        }
        return responseEntity;
    }

}