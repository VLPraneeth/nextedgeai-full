package com.syncari.viper.rest.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.syncari.core.DataTransformer;
import com.syncari.core.SyncariContextHandler;
import com.syncari.core.config.AppConfig;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.SyncStream;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.EmailService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.StreamService;
import com.syncari.core.webhook.receiver.WebhookReceiverService;
import com.syncari.viper.GraphRunner;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/pipeline")
public class PipelineController {

    @Autowired
    SyncariContextHandler contextHandler;

    @Autowired
    MappingGraphService graphService;

    @Autowired
    GraphRunner service;

    @Autowired
    WebhookReceiverService webhookService;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    DataTransformer dataTransformer;

    @Autowired
    GraphRunner graphRunner;

    @Autowired
    StreamService streamService;
    
    @Autowired
    @Qualifier("defaultEmailService")
    EmailService emailService;
    
    @Autowired
    AppConfig appConfig;

    private static final String PAUSED_MESSAGE = "{'serviceStatus': 'PAUSED','message':'The pipeline %s serving this endpoint is paused'}";

    private static final String ERROR_MESSAGE = "{'serviceStatus':'ERROR','message':'The pipeline %s serving has an error: %s'}";

    @RequestMapping(method = RequestMethod.GET, value = "/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.status(HttpStatus.OK).body(Map.of("status", "ok"));
    }

    @RequestMapping(method = RequestMethod.POST, value = "/sync2/{instanceId}/{targetEntityId}")
    public CompletableFuture<ResponseEntity<String>> sync2(@PathVariable String instanceId, @PathVariable String targetEntityId, @RequestParam String connectorId,
                                                           @RequestBody String body, HttpServletRequest request) {
        return CompletableFuture.completedFuture(ResponseEntity.ok("TestMessage"));
    }


    @RequestMapping(method = RequestMethod.POST, value = "/sync/{instanceId}/{targetEntityId}")
    public CompletableFuture<ResponseEntity<String>> sync(@PathVariable String instanceId, @PathVariable String targetEntityId, @RequestParam String connectorId,
                                                          @RequestParam String requestId,
                                                          @RequestBody String body, HttpServletRequest request) {
        contextHandler.setContext(instanceId);
        Optional<MappingGraph> graphOpt = graphService.retrieveApprovedEntityGraph(targetEntityId);
        return graphOpt.map(graph -> {
                return connectorService.find(connectorId).map(connector -> {
                        return streamService.findStream(graph.getId()).map(stream -> {
                            if (stream.getStatus() == SyncStream.Status.PAUSED && stream.getErrorDetail() != null && stream.getErrorDetail().isPausedByError()) {
                                return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                        .body(String.format(ERROR_MESSAGE, graph.getName(), stream.getErrorDetail().getMessage())));
                            } else if (stream.getStatus() == SyncStream.Status.PAUSED) {
                                return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                        .body(String.format(PAUSED_MESSAGE, graph.getName())));
                            }
                            try {
                                long startTime = Instant.now().toEpochMilli();
                                log.info("Received Real time pipeline request for RequestId: {} instanceId: {}, targetEntityId: {}, connectorId: {} Name: {}",
                                        requestId, instanceId, targetEntityId, connectorId, graph.getName());
                                return graphRunner.syncPipeline(requestId, graph, connector, body, getHeaders(request))
                                    .thenApply(r -> {
                                        if(StringUtils.isBlank(r.getPayload()) && StringUtils.isNotBlank(connector.getMetadata().getResponseTemplate())) {
                                          log.error(
                                              "Blank realtime response received for RequestId: {} instanceId: {}, targetEntityId: {}, connectorId: {} request body: {}",
                                              requestId, instanceId, targetEntityId, connectorId, body);
                                          try {
                                            String subject = String.format("Blank realtime response received for instanceId: %s, targetEntityId: %s, connectorId: %s", instanceId, targetEntityId, connectorId);
                                            emailService.sendErrorEmail(List.of(), appConfig.getErrorEmail(), subject, body);
                                          } catch (Exception e) {
                                            log.error("Failed to send error email for realtime {}", e.getMessage(), e);
                                          }
                                        }
                                        log.info("Real time pipeline request completed for RequestId: {} instanceId: {}, targetEntityId: {}, connectorId: {} Name {} in {} secs",
                                                requestId, instanceId, targetEntityId, connectorId, graph.getName(), (Instant.now().toEpochMilli() - startTime) / 1000);
                                        return ResponseEntity.status(r.getStatusCode())
                                                             .headers(new HttpHeaders(r.getHeaders()))
                                                             .body(r.getPayload());
                                    });
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        }).orElse(CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(String.format("Invalid Pipeline Id %s or Connector Id %s for instance %s", targetEntityId, connectorId, instanceId))));
                });
        }).flatMap(Function.identity())
                .orElse(CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(String.format("Invalid Pipeline Id %s or Connector Id %s for instance %s", targetEntityId, connectorId, instanceId))));
    }


    private Map<String, Object> getHeaders(HttpServletRequest request) {
        Map<String, Object> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String header = headerNames.nextElement();
                headers.put(header, request.getHeader(header));
            }
        }
        return headers;
    }
}
