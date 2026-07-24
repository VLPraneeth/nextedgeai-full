package com.syncari.api.rest.controllers;

import static com.syncari.connector.Operation.isEqual;
import static com.syncari.connector.Operation.merge;
import static com.syncari.connector.Operation.merge_report_only;
import static com.syncari.connector.Operation.merge_skip;
import static com.syncari.core.security.Permissions.READ_CONNECTOR;
import static com.syncari.core.security.Permissions.TEST_CONNECTION;
import static com.syncari.core.security.Permissions.VIEW_TRANSACTIONS;
import static com.syncari.core.security.Permissions.WRITE_CONNECTOR;
import static com.syncari.utils.I18n.i18n;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.api.core.util.ConnectorMetadataTransformer;
import com.syncari.api.core.util.ObjectTransformer;
import com.syncari.api.rest.controllers.TransactionLogController.QueryCSVInputStream;
import com.syncari.api.rest.controllers.data.ConnectorMetadataDTO;
import com.syncari.api.rest.controllers.data.ConnectorRequest;
import com.syncari.api.rest.controllers.data.ConnectorResponse;
import com.syncari.api.rest.controllers.data.WebhookReceiverResponse;
import com.syncari.api.rest.controllers.data.studio.DataQueryResponse;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Message;
import com.syncari.core.event.Publisher;
import com.syncari.core.event.store.model.WebhookReceiverLog;
import com.syncari.core.event.store.repo.WebhookReceiverLogRepo;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.file.GCSFileManager;
import com.syncari.core.model.Connector;
import com.syncari.core.model.ConnectorSchemaSetting;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.Event;
import com.syncari.core.model.TransactionLog;
import com.syncari.core.model.misc.ConnectorStatus;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.pagination.PageDirection;
import com.syncari.core.service.ConnectorService;
import com.syncari.restutils.data.DataQueryMetadata;
import com.syncari.restutils.data.EntityRecord;
import com.syncari.utils.DateUtil;
import com.syncari.utils.Timer;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/connector")
public class ConnectorController {

    @Autowired
    ConnectorService connectorService;
    @Autowired
    ObjectTransformer transformer;
    @Autowired
    Publisher publisher;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    GCSFileManager gcsFileManager;
    @Autowired
    AppConfig config;
    @Autowired
    ConnectorMetadataTransformer connectorMetadataTransformer;
    @Autowired
    WebhookReceiverLogRepo whLogRepo;


    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST)
    public ConnectorResponse save(@RequestBody ConnectorRequest connector) {
        Connector toBeSaved = transformer.toConnector(connector);
        Connector saved = connectorService.save(toBeSaved, connector.isBootstrapWithSyncari());
        connectorService.createWebhookConfig(saved);
        return transformer.toConnectorResponse(saved, connectorService.getSetting(saved.getId()));
    }

    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST, value = "/{connectorId}")
    public ConnectorResponse edit(@PathVariable String connectorId, @RequestBody ConnectorRequest connector) {
        if (StringUtils.isBlank(connectorId))
            throw new RuntimeException("Connector id cannot be null for edits");

        Connector c = transformer.toConnector(connector);
        c.setId(connectorId);
        Optional<Connector> existingConnector = connectorService.find(connectorId);
        if(existingConnector.isPresent()){
            c = checkStarsAndUpdateFields(c, existingConnector);
        }
        c = connectorService.save(c);
        connectorService.createWebhookConfig(c);
        return transformer.toConnectorResponse(c, connectorService.getSetting(c.getId()));
    }

    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST, value = "/setting/{connectorId}")
    public List<ConnectorSchemaSetting> saveSetting(@PathVariable String connectorId, @RequestBody List<ConnectorSchemaSetting> settings) {
        List<ConnectorSchemaSetting> saved = new ArrayList<>();
        connectorService.deleteSetting(connectorId);
        if(settings == null || settings.isEmpty()) {
            log.warn("Deleting setting for connector {} as it is empty", connectorId);
            return saved;
        }
        for(ConnectorSchemaSetting setting: settings) {
            if (setting == null || StringUtils.isBlank(setting.getFromEntityId()))
                throw new RuntimeException("Please select Entity from which the sync needs to be done.");
            if (setting == null || StringUtils.isBlank(setting.getFromConnectorId()))
                throw new RuntimeException("Please select Synapse to which the sync needs to be done.");
            if (setting.getToEntityIds() != null && !setting.getToEntityIds().isEmpty()) {
                saved.add(connectorService.upsertSetting(setting));
            }
        }
        return saved;
    }

    //@Secured(READ_CONNECTOR)
    @PreAuthorize("hasAnyAuthority('READ_CONNECTOR', 'READ_DATA_STUDIO')")
    @RequestMapping(method = RequestMethod.GET)
    public List<ConnectorResponse> list() {
        List<ConnectorResponse> response = new ArrayList<>();
        List<Connector> persisted = connectorService.list();
        List<Connector> connectors = new ArrayList<>();
        for (Connector connector : persisted) {
            connectors.add(connector);
        }
        // add syncari connector to list
        Connector syncariConnector = connectorService.getSyncariConnector();
        Connector conn = syncariConnector.makeCopy();
        conn.setId(syncariConnector.getId());
        conn.setName("NextEdge AI");
        connectors.add(conn);
        for (Connector connector : connectors) {
            response.add(transformer.toConnectorResponse(connector, connectorService.getSetting(connector.getId())));
        }
        return response;
    }



    private Connector checkStarsAndUpdateFields(Connector requestConnector, Optional<Connector> existingConnectorOpt){
        Connector result = new Connector(requestConnector.getName(), requestConnector.getMetadataId(), requestConnector.getEndpoint());
        Map<String, Object> webhookMetaConfig = new HashMap<>();
        existingConnectorOpt.ifPresent(existingConnector -> {
            AuthConfig existingAuthConfig = existingConnector.getAuthConfig();
            AuthConfig requestAuthConfig = requestConnector.getAuthConfig();
            if (requestConnector.getAuthConfig() != null) {
                if (StringUtils.isNotBlank(requestAuthConfig.getPassword()) && (!"*****".equals(requestAuthConfig.getPassword()))) {
                    existingAuthConfig.setPassword(requestAuthConfig.getPassword());
                }
                if (StringUtils.isNotBlank(requestAuthConfig.getClientSecret()) && (!"*****".equals(requestAuthConfig.getClientSecret()))) {
                    existingAuthConfig.setClientSecret(requestAuthConfig.getClientSecret());
                }
                if (StringUtils.isNotBlank(requestAuthConfig.getToken()) && (!"*****".equals(requestAuthConfig.getToken()))) {
                    existingAuthConfig.setToken(requestAuthConfig.getToken());
                }
                if (StringUtils.isNotBlank(requestAuthConfig.getAccessToken()) && (!"*****".equals(requestAuthConfig.getAccessToken()))) {
                    existingAuthConfig.setAccessToken(requestAuthConfig.getAccessToken());
                }
                if (StringUtils.isNotBlank(requestAuthConfig.getRefreshToken()) && (!"*****".equals(requestAuthConfig.getRefreshToken()))) {
                    existingAuthConfig.setRefreshToken(requestAuthConfig.getRefreshToken());
                }
                if (StringUtils.isNotBlank(requestAuthConfig.getClientId()) && (!"*****".equals(requestAuthConfig.getClientId()))) {
                    existingAuthConfig.setClientId(requestAuthConfig.getClientId());
                }
                if (StringUtils.isNotBlank(requestAuthConfig.getConsumerSecret()) && (!"*****".equals(requestAuthConfig.getConsumerSecret()))) {
                    existingAuthConfig.setConsumerSecret(requestAuthConfig.getConsumerSecret());
                }
                if (StringUtils.isNotBlank(requestAuthConfig.getConsumerKey()) && (!"*****".equals(requestAuthConfig.getConsumerKey()))) {
                    existingAuthConfig.setConsumerKey(requestAuthConfig.getConsumerKey());
                }
                if (StringUtils.isNotBlank(requestAuthConfig.getTokenSecret()) && (!"*****".equals(requestAuthConfig.getTokenSecret()))) {
                    existingAuthConfig.setTokenSecret(requestAuthConfig.getTokenSecret());
                }
                if (StringUtils.isNotBlank(requestAuthConfig.getTokenId()) && (!"*****".equals(requestAuthConfig.getTokenId()))) {
                    existingAuthConfig.setTokenId(requestAuthConfig.getTokenId());
                }
                if (requestConnector.getAuthConfig().getAdditionalHeaders()!=null){
                    Map<String, String> updatedAdditionalHeaders = new HashMap<>();
                    Map<String, String> existingAdditionalHeader = existingAuthConfig.getAdditionalHeaders();
                    Map<String, String> requestAdditionalHeaders = requestAuthConfig.getAdditionalHeaders();
                    // Filter new headers or existing headers and set those accordingly
                    List<String> starAdditionalHeaders = requestAdditionalHeaders.keySet().stream().filter(key -> "*****".equals(requestAdditionalHeaders.get(key))).collect(Collectors.toList());
                    List<String> newAdditionalHeaders = requestAdditionalHeaders.keySet().stream().filter(key -> !"*****".equals(requestAdditionalHeaders.get(key))).collect(Collectors.toList());
                    starAdditionalHeaders.forEach(key -> {
                        updatedAdditionalHeaders.put(key, existingAdditionalHeader.get(key));
                    });
                    newAdditionalHeaders.forEach(key -> {
                        updatedAdditionalHeaders.put(key, requestAdditionalHeaders.get(key));
                    });
                    existingAuthConfig.setAdditionalHeaders(updatedAdditionalHeaders);
                }
                if(existingConnector.getMetaConfig().containsKey(ConnectorService.WEBHOOK_ID)
                        && existingConnector.getMetaConfig().containsKey(ConnectorService.WEBHOOK_SIGNING_SECRET)) {
                    webhookMetaConfig.put(ConnectorService.WEBHOOK_ID, existingConnector.getMetaConfig().get(ConnectorService.WEBHOOK_ID));
                    webhookMetaConfig.put(ConnectorService.WEBHOOK_SIGNING_SECRET, existingConnector.getMetaConfig().get(ConnectorService.WEBHOOK_SIGNING_SECRET));
                }
            }
            existingAuthConfig.setUserName(requestAuthConfig.getUserName());
            existingAuthConfig.setEndpoint(requestAuthConfig.getEndpoint());
            existingAuthConfig.setExpiresIn(requestAuthConfig.getConsumerKey());
            existingAuthConfig.setRedirectUri(requestAuthConfig.getRedirectUri());
            existingAuthConfig.setLastRefreshed(requestAuthConfig.getLastRefreshed());
            existingAuthConfig.setSignatureHeader(requestAuthConfig.getSignatureHeader());
            existingAuthConfig.setHashAlgorithm(requestAuthConfig.getHashAlgorithm());
            existingAuthConfig.setApiKeyHeader(requestAuthConfig.getApiKeyHeader());
            existingAuthConfig.setAccessTokenEndpoint(requestAuthConfig.getAccessTokenEndpoint());
            result.setAuthConfig(existingAuthConfig);
        });
        result.setApiConfig(requestConnector.getApiConfig());
        result.setDailyQuota(requestConnector.getDailyQuota());
        result.setAuthType(requestConnector.getAuthType());
        result.setMetaConfig(requestConnector.getMetaConfig());
        result.getMetaConfig().putAll(webhookMetaConfig);
        result.setSetting(requestConnector.getSetting());
        result.setId(requestConnector.getId());
        return result;
    }

    //@Secured(READ_CONNECTOR)
    @PreAuthorize("hasAnyAuthority('READ_CONNECTOR', 'READ_DATA_STUDIO')")
    @RequestMapping(method = RequestMethod.GET, value = "/describe")
    public List<ConnectorMetadataDTO> describe() {
        List<ConnectorMetadataDTO> dtos = new ArrayList<>();
        Timer timer = new Timer(200, "ConnectorController::describe", log);
        connectorService.describe().forEach(x -> {
            dtos.add(connectorMetadataTransformer.toConnectorMetadata(x));
        });
        timer.close();
        return dtos;
    }

    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST, value = "/activate/{connectorId}")
    public void activate(@PathVariable String connectorId, @RequestParam("createMappings") boolean createMappings) {
        Event event = new Event().setType(EventTypes.ACTIVATE_CONNECTOR)
                .setLoggedTime(new Date())
                .setDetails(Map.of("connectorId", connectorId, "createMappings", String.valueOf(createMappings)));
        Message message = new Message(SyncariContext.getInstance().getSyncariId(), event);
        try {
            String eventString = mapper.writeValueAsString(message);
            log.info(String.format("Sending Activate Message: %s", eventString));
            publisher.publishToGenericQueue(eventString);
            connectorService.setStatus(connectorId, ConnectorStatus.ACTIVATING, null, null);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST, value = "/deactivate/{connectorId}")
    public void deactivate(@PathVariable String connectorId) {
        connectorService.deactivate(connectorId);
    }

    @Secured(TEST_CONNECTION)
    @RequestMapping(method = RequestMethod.POST, value = "/test/{connectorId}")
    public TestConnectionResponse testConnection(@PathVariable String connectorId) {
        return connectorService.testConnection(connectorId);
    }

    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.DELETE, value = "/{connectorId}")
    public void delete(@PathVariable String connectorId) {
        connectorService.deleteWebhookConfig(connectorId);
        connectorService.delete(connectorId, false);
    }
    
    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST, value = "/log")
    public WebhookReceiverResponse getWebhookLog(@RequestParam(defaultValue = "") String cursor,
        @RequestParam(defaultValue = "next") String direction,
        @RequestParam(defaultValue = "25") int count,
        @RequestParam(name = "connectorId") String connectorId) {
      Instant end = Instant.now();
      Instant start = end.minus(3, ChronoUnit.DAYS);
      var pageCursor = new PageCursor(cursor, PageDirection.valueOf(direction), count);
      if(StringUtils.isNotBlank(cursor)) {
        pageCursor.setPageNumber(Integer.parseInt(cursor));
      }
      Page<WebhookReceiverLog> page = whLogRepo.query(connectorId, start, end, pageCursor);
      return new WebhookReceiverResponse(page.getRecords(), page.getPageInfo(),
          connectorId);
    }
    
    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/log/download")
    public ResponseEntity<Resource> download(
        @RequestParam(name = "connectorId") String connectorId) {

      Instant end = Instant.now();
      Instant start = end.minus(3, ChronoUnit.DAYS);
      String connectorName =
          connectorService.find(connectorId).map(c -> c.getName()).orElse("webhook");
      InputStreamResource resource =
          new InputStreamResource(new QueryCSVInputStream(start, end, connectorId));
      return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN)
          .header(HttpHeaders.CONTENT_DISPOSITION,
              "attachment; filename=\"" + connectorName + ".csv\"")
          .body(resource);
    }
    
    class QueryCSVInputStream extends InputStream {
      private static final int MAX_RECORDS = 25000;
      private final Instant startDate;
      private final Instant endDate;
      private final String connectorId;
      private  String cursor;
      private ByteArrayInputStream bin;
      private final CSVPrinter csvPrinter;
      private final StringWriter csvBuffer;
      private boolean completed;
      private int count;

      public QueryCSVInputStream(Instant start, Instant end, String connectorId){
          this.startDate = start;
          this.endDate = end;
          this.connectorId = connectorId;
          this.cursor = "0";
          this.count = 0;
          try {
              List<String> headers = List.of("id", "connectorId", "receivedOn", "payload", "headers", "verified", "authenticated");
              csvBuffer = new StringWriter();
              csvPrinter = new CSVPrinter(csvBuffer, CSVFormat.DEFAULT
                      .withHeader(headers.toArray(new String[headers.size()])));
          }catch(IOException ex){
              throw new RuntimeException(ex);
          }
          readPage();
      }

      private void readPage(){
        if(MAX_RECORDS - count <= 0) {
          completed = true;
          return;
        }
        Page<WebhookReceiverLog> page =
            whLogRepo.query(connectorId, startDate, endDate, new PageCursor(cursor,
                PageDirection.next, (MAX_RECORDS - count) >= 1000 ? 1000 : MAX_RECORDS - count).setPageNumber(Integer.parseInt(cursor)));
          List<WebhookReceiverLog> webhookLogs = page.getRecords();
          writeToBuffer(webhookLogs);
          cursor = Integer.toString(page.getPageInfo().getPageNumber() + 1);
      }

      private void writeToBuffer(List<WebhookReceiverLog> webhookLogs) {
        count = count + webhookLogs.size();
        webhookLogs.forEach(rec -> {
          List<Object> record = List.of(rec.getId(), rec.getConnectorId(), rec.getReceivedOn(),
              rec.getPayload(), rec.getHeaders(), rec.getVerified(), rec.getAuthenticated());
              try {
                  csvPrinter.printRecord(record);
              } catch (IOException e) {
                  log.warn("Webhook Log Export: cannot write row " + record,e);
              }
          });
          try {
              if(csvBuffer.getBuffer().length() >0) {
                  bin = new ByteArrayInputStream(csvBuffer.getBuffer().toString().getBytes("utf-8"));
                  csvBuffer.getBuffer().setLength(0);
              }
          } catch (UnsupportedEncodingException e) {
              log.error(e.getMessage(),e);
              throw new SyncariValidationException("Invalid data found while exporting CSV");
          }
      }

      public int read() {
          if(bin==null && !completed){
              readPage();
          }
          if(bin == null){
              completed=true;
              return -1;
          }
          int read = bin.read();
          if (read == -1) {
              bin=null;
              return read();
          }
          return read;
      }
  }
}
