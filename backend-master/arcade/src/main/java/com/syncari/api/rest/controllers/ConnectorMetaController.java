package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.APPROVE_CUSTOM_SYNAPSE;
import static com.syncari.core.security.Permissions.READ_CONNECTOR;
import static com.syncari.core.security.Permissions.TEST_CONNECTION;
import static com.syncari.core.security.Permissions.WRITE_CONNECTOR;
import static com.syncari.utils.I18n.i18n;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.api.core.util.ConnectorMetadataTransformer;
import com.syncari.api.core.util.ObjectTransformer;
import com.syncari.api.rest.controllers.data.ConnectorMetadataDTO;
import com.syncari.api.rest.controllers.data.ConnectorRequest;
import com.syncari.api.rest.controllers.data.CustomSynapseListResponse;
import com.syncari.api.rest.controllers.data.HttpSourceEntityListResponse;
import com.syncari.api.rest.controllers.data.HttpSourceEntityRequest;
import com.syncari.api.rest.controllers.data.HttpSourceEntityResponse;
import com.syncari.api.rest.controllers.data.HttpSourceEntityTestRequest;
import com.syncari.api.rest.controllers.data.HttpSourceEntityTestResponse;
import com.syncari.api.rest.controllers.data.ServiceCredentialDTO;
import com.syncari.api.rest.controllers.data.ShareConnectorMetaResponse;
import com.syncari.api.rest.controllers.data.WebhookTestRequest;
import com.syncari.api.rest.controllers.data.WebhookTestResponse;
import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorSharingScope;
import com.syncari.connector.ConnectorType;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.AuthMetadata;
import com.syncari.connector.data.AuthType;
import com.syncari.connector.data.HTTPSourceResult;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.connector.data.WebhookReceiverResult;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.core.GlobalConstants;
import com.syncari.core.config.AppConfig;
import com.syncari.core.event.Publisher;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.file.GCSFileManager;
import com.syncari.core.functions.FunctionsSeed;
import com.syncari.core.http.source.HttpSourceMetadataDTO;
import com.syncari.core.http.source.HttpSourcesService;
import com.syncari.core.model.Connector;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.core.model.misc.ServiceType;
import com.syncari.core.service.ConnectorMetadataService;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.CustomSecurityScannerService;
import com.syncari.core.service.DataServiceFactory;
import com.syncari.core.service.SchemaService;
import com.syncari.core.share.ShareConnectorMetaRequest;
import com.syncari.core.utils.CustomSynapseDraftIssue;
import com.syncari.core.utils.CustomSynapseDraftIssueSeverity;
import com.syncari.core.utils.CustomSynapseWhitelistedErrors;
import com.syncari.core.webhook.receiver.WebhookReceiverMetadataDTO;
import com.syncari.core.webhook.receiver.WebhookReceiverService;
import com.syncari.utils.KeyValue;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONObject;


@Slf4j
@RestController
@RequestMapping(value = "/api/v1/connectormeta")
public class ConnectorMetaController {

    private static final List<String> allowedCustomSynapseExt = List.of("py", "txt");
    private static final List<String> allowedCustomSynapseContentType = List.of("text/plain", "text/x-python-script", "application/octet-stream", "text/x-python");
    // External vendor documentation is intentionally disabled in the private white-label build.
    private static final String articleBaseUrl = "";

    @Autowired
    ConnectorService connectorService;
    @Autowired
    ConnectorMetadataService connectorMetadataService;
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
    DataServiceFactory dataServiceFactory;
    @Autowired
    AppConfig appConfig;
    @Autowired
    SchemaService schemaService;
    @Autowired
    CustomSecurityScannerService securityScannerService;
    @Autowired
    HttpSourcesService httpSourcesService;
    @Autowired
    WebhookReceiverService webhookReceiverService;

    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET)
    public List<ConnectorMetadataDTO> list() {
        List<ConnectorMetadataDTO> dtos = new ArrayList<>();
        connectorMetadataService.listAllCustomSynapses().forEach(x -> {
            dtos.add(connectorMetadataTransformer.toConnectorMetadata(x));
        });
        return dtos;
    }

    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST, value = "/samplesynapse")
    public ResponseEntity<Resource> getSampleSynapse() {
        InputStream resource = gcsFileManager.readFile("syncari_sample_custom_synapse/custom_synapse.zip");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=custom_synapse.zip")
                .body(new InputStreamResource(resource));
    }

    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/capabilities/{metaId}")
    public String getCapabilities(@PathVariable("metaId") String metaId) {
        Optional<ConnectorMetadata> meta = connectorMetadataService.findById(metaId);
        String articleId = dataServiceFactory.getSynapseService(meta.get()).getCapabilitiesArticleId();
        if(StringUtils.isBlank(articleId) || StringUtils.isBlank(articleBaseUrl)) return "";
        SyncariEntityDataRestClient client = new SyncariEntityDataRestClient();
        RestTemplate restTemplate = client.getTemplate();
        HttpEntity httpEntity = new HttpEntity(client.getHeaders(new AuthConfig()));
        String response = restTemplate.exchange(String.format(articleBaseUrl, articleId), HttpMethod.GET, httpEntity, String.class).getBody();
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            Map result = objectMapper.readValue(response, Map.class);
            return ((Map)result.get("article")).get("body").toString();
        } catch (JsonProcessingException e) {
            log.error(ExceptionUtils.getStackTrace(e));
            throw new RuntimeException(e);
        }
    }

    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/mappings/{metaId}")
    public List<Mappings> getDefaultMappings(@PathVariable("metaId") String metaId) {
        Optional<ConnectorMetadata> meta = connectorMetadataService.findById(metaId);
        SynapseInfoService infoService = dataServiceFactory.getSynapseService(meta.get());
        List<Mappings> mappings = new ArrayList<>();
        infoService.getEntityMappings().forEach((k,v) -> {
            String direction = schemaService.getSyncariEntityByName(k).get().isReadOnly()? "Source" : "Bi Directional";
            mappings.add(new Mappings().setSyncariEntity(k).setExternalEntity(v)
                    .setAttributeMapping(infoService.getAttributeMappings(k)).setDirection(direction));
        });
        return mappings;
    }

    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/servicecredentials")
    public List<ServiceCredentialDTO> listEnrichment() {
        List<ServiceCredentialDTO> dtos = new ArrayList<>();
        List<ConnectorMetadata> byType = connectorMetadataService.findByType(ConnectorType.Enrich.name());
        byType.forEach(c -> {
            ServiceCredentialDTO dto = new ServiceCredentialDTO(c);
            dto.setSupportedAuthType(getAuthMeta(dto.getName()));
        });
        return dtos;
    }

    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST)
    public ResponseEntity create(@RequestParam("connectorMetaName") String connectorMetaName,
            @RequestParam("connectorMetaDisplayName") String connectorMetaDisplayName,
            @RequestParam("synapseFile") MultipartFile synapseFile,
            @RequestParam("requirementsFile") MultipartFile requirementsFile,
            @RequestParam(required = false) MultipartFile iconFile) {
        try {
            validateFiles(synapseFile, requirementsFile);
            return ResponseEntity.ok(
                    connectorMetadataTransformer.toConnectorMetadata(connectorMetadataService.createDraft(connectorMetaName, connectorMetaDisplayName, synapseFile, requirementsFile, iconFile)));
        } catch (Exception e) {
            log.error(e.getMessage());
            JSONObject errResp = new JSONObject();
            errResp.put("message", e.getMessage());
            return new ResponseEntity(errResp, HttpStatus.BAD_REQUEST);
        }
    }

    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/{id}/icon")
    public ResponseEntity<StreamingResponseBody> getCustomSynapseIcon(@PathVariable String id) {
        HttpHeaders headers = new HttpHeaders();

        ConnectorMetadata connectorMetadata = connectorMetadataService.findById(id).get();
        String iconPath = connectorMetadata.getIconUri();
        if (iconPath == null) {
            iconPath = ConnectorMetadataService.CUSTOM_SYNAPSE_DEFAULT_ICON;
        }

        var extensionParts = iconPath.split("\\.");
        var extension = extensionParts.length > 0 ? extensionParts[extensionParts.length - 1] : "png";
        var mediaType = GlobalConstants.PHOTO_MEDIA_TYPE_MAP.getOrDefault(extension.toLowerCase(), MediaType.IMAGE_PNG);
        if(connectorMetadata.getIconUriContentType() != null) {
          mediaType = MediaType.parseMediaType(connectorMetadata.getIconUriContentType());
        }
        headers.setContentType(mediaType);
        var iconStream = connectorMetadataService.getCustomSynapseIcon(iconPath);
        StreamingResponseBody stream = outputStream -> iconStream.transferTo(outputStream);

        return new ResponseEntity<>(stream, headers, HttpStatus.OK);
    }

    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/{id}/draft")
    public ResponseEntity getDraft(@PathVariable String id) {
        return ResponseEntity.ok(connectorMetadataTransformer.toConnectorMetadata(connectorMetadataService.findDraft(id).get()));
    }
    
    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/{id}")
    public ConnectorMetadataDTO describeById(@PathVariable String id) {
        return connectorMetadataTransformer.toConnectorMetadata(connectorService.describeById(id));
    }

    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST, value = "/{id}/createDraft")
    public ResponseEntity createDraft(@PathVariable String id) throws IOException  {
        return ResponseEntity.ok(connectorMetadataTransformer.toConnectorMetadata(connectorMetadataService.createDraftFor(id)));
    }

    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.PUT, value = "/{id}/updateDraft")
    public ResponseEntity updateDraft(@PathVariable String id,
            @RequestParam("connectorMetaDisplayName") String connectorMetaDisplayName,
            @RequestParam(required = false) MultipartFile synapseFile,
            @RequestParam(required = false) MultipartFile requirementsFile,
            @RequestParam(required = false) MultipartFile iconFile,
            @RequestParam(required = false) boolean publishToGlobal) {
        try {
            if (synapseFile != null) {
                validateFiles(synapseFile, requirementsFile);    
            }
            connectorMetadataService.validateSourceInstance(id);
            return ResponseEntity.ok(
                    connectorMetadataTransformer.toConnectorMetadata(connectorMetadataService.updateDraft(id, connectorMetaDisplayName, synapseFile, requirementsFile, iconFile, publishToGlobal)));
        } catch (Exception e) {
            log.error(e.getMessage());
            JSONObject errResp = new JSONObject();
            errResp.put("message", e.getMessage());
            return new ResponseEntity(errResp, HttpStatus.BAD_REQUEST);
        }
    }

    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST, value = "/{id}/discardDraft")
    public void discardDraft(@PathVariable String id) throws IOException  {
        if(connectorService.isInUse(id)) {
            throw new SyncariValidationException(i18n("connector_in_use"));
        }
        connectorMetadataService.validateSourceInstance(id);
        connectorMetadataService.discardDraft(id);
    }

    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST, value = "/{id}/submitForApproval")
    public ResponseEntity submitForApproval(@PathVariable String id) throws IOException  {
        return ResponseEntity.ok(connectorMetadataTransformer.toConnectorMetadata(connectorMetadataService.submitForApproval(id)));
    }

    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST, value = "/{id}/withdrawApproval")
    public ResponseEntity withdrawApproval(@PathVariable String id) throws IOException  {
        return ResponseEntity.ok(connectorMetadataTransformer.toConnectorMetadata(connectorMetadataService.withdrawApproval(id)));
    }

    @Secured(APPROVE_CUSTOM_SYNAPSE)
    @RequestMapping(method = RequestMethod.POST, value = "/{id}/approve")
    public ResponseEntity approve(@PathVariable String id) throws IOException  {
        // TODO: This should be only allowed for Super admin (Syncari users)
        return ResponseEntity.ok(connectorMetadataTransformer.toConnectorMetadata(connectorMetadataService.approve(id)));
    }

    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/{id}/status")
    public ResponseEntity getDeploymentStatus(@PathVariable String id) {
        try {
            return ResponseEntity.ok(connectorMetadataService.getCustomConnectorMetadataStatus(id));
        } catch (Exception e) {
            log.error(e.getMessage());
            JSONObject errResp = new JSONObject();
            errResp.put("message", e.getMessage());
            return new ResponseEntity(errResp, HttpStatus.BAD_REQUEST);
        }
    }

    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.DELETE, value = "/{id}")
    public void delete(@PathVariable String id) {
        // check if any instance in the org is using the synapse
        if(connectorService.isInUse(id)) {
            throw new SyncariValidationException(i18n("connector_in_use"));
        }
        connectorMetadataService.validateSourceInstance(id);
        connectorMetadataService.delete(id);
    }

    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/{id}/downloadFiles")
    public ResponseEntity<Resource> downloadCustomSynapseFiles(@PathVariable String id) {
        ConnectorMetadata draft = connectorMetadataService.findDraft(id).get();
        InputStreamResource resource = new InputStreamResource(connectorMetadataService.getCustomSynapseFiles(id));
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + draft.getName() + ".zip" + "\"")
                .body(resource);
    }

    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/{id}/downloadErrorLog")
    public ResponseEntity<Resource> downloadCustomSynapseErrorLog(@PathVariable String id) {
        ConnectorMetadata draft = connectorMetadataService.findDraft(id).get();
        InputStreamResource resource = new InputStreamResource(connectorMetadataService.getCustomSynapseErrorLog(id));
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("text/plain"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + draft.getName() + ".txt" + "\"")
                .body(resource);
    }

    @Secured(TEST_CONNECTION)
    @RequestMapping(method = RequestMethod.POST, value = "/test")
    public TestConnectionResponse testMetaConnection(@RequestBody ConnectorRequest connector) {
        Connector c = transformer.toConnector(connector);
        return connectorMetadataService.testConnection(c);
    }
    
    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/customSynapses/all")
    public List<CustomSynapseListResponse> customSynapsesAll() {
		return connectorMetadataService
				.listAllCustomSynapses().stream().map(
						meta -> transformer.toCustomSynapseListResponse(meta,
								connectorMetadataService.findSharedItemByConnectorMetaData(meta)
										.map(s -> s.isPublishedToMarketplace()).orElse(false)))
				.collect(Collectors.toList());
    }
    
    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST, value = "/httpsource/test")
    public HttpSourceEntityTestResponse test(@RequestBody HttpSourceEntityTestRequest testReq) {
        if(testReq.getBody() != null) {
          testReq.setBody(StringEscapeUtils.unescapeJson(testReq.getBody()).trim().replaceAll("^\"|\"$", ""));
        }
		HTTPSourceResult httpResponse = connectorMetadataService.testHttpSource(testReq.getMetadataId(),
				testReq.getAuthType(), testReq.getAuthConfig(), transformer.toHttpSourceConfig(testReq),
				testReq.getVariableValues());
		// update request headers
        HttpHeaders requestHeaders = httpResponse.getRequestHeaders();
        testReq.setHeaders(requestHeaders.toSingleValueMap());
        return transformer.toHttpSourceEntityTestResponse(testReq, httpResponse);
    }
    
    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/httpsource/supportedAuthTypes")
    public List<AuthMetadata> supportedAuthTypes() {
		return httpSourcesService.getSupportedAuthTypes();
    }
    
    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/httpsource/supportedDataTypes")
    public List<Map<String, String>> supportedDataTypes() {
		return FunctionsSeed.getSupportedDataTypes();
    }
    
    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/httpsource/pagination")
    public List<KeyValue> paginationTypes() {
    	return connectorMetadataService.getPaginationTypes();
    }
    
    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST, value = "/httpsource")
    public ResponseEntity createHttpSource(
    		@RequestParam("name") String name,
            @RequestParam("displayName") String displayName,
            @RequestParam("authType") AuthType authType,
            @RequestParam("authConfig") String authConfig,
            @RequestParam(value = "variables", required = false) String variables,
            @RequestParam(value = "variableValues", required = false) String variableValues,
            @RequestParam(value = "method", required = false) String method,
            @RequestParam(value = "endpoint", required = false) String endpoint,
            @RequestParam(value = "headers", required = false) String headers,
            @RequestParam(value ="body", required = false) String body,
            @RequestParam(name = "iconFile", required = false) MultipartFile iconFile) {
        try {
        	var req = new HttpSourceMetadataDTO()
        			.setName(name)
        			.setDisplayName(displayName)
        			.setAuthType(authType)
        			.setAuthConfig(authConfig != null ? mapper.readValue(authConfig, AuthConfig.class) : null)
        			.setVariables(variables != null ? mapper.readValue(variables, new TypeReference<List<KeyValue>>() {}) : List.of())
        			.setVariableValues(variableValues != null ? mapper.readValue(variableValues, new TypeReference<List<KeyValue>>() {}) : List.of())
        			.setMethod(method)
        			.setEndpoint(endpoint)
        			.setHeaders(headers != null ? mapper.readValue(headers, new TypeReference<Map<String, String>>() {}) : Map.of())
        			.setBody(body != null ? StringEscapeUtils.unescapeJson(body).trim().replaceAll("^\"|\"$", "") : body)
        			.setIcon(iconFile);
            return ResponseEntity.ok(
                    connectorMetadataTransformer.toConnectorMetadata(connectorMetadataService.createHttpSourceDraft(req)));
        } catch (Exception e) {
            log.error(e.getMessage());
            JSONObject errResp = new JSONObject();
            errResp.put("message", e.getMessage());
            return new ResponseEntity(errResp, HttpStatus.BAD_REQUEST);
        }
    }
    
    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.PUT, value = "/httpsource/{id}/updateDraft")
    public ResponseEntity updateHttpSource(@PathVariable String id,
    		@RequestParam("name") String name,
            @RequestParam("displayName") String displayName,
            @RequestParam("authType") AuthType authType,
            @RequestParam("authConfig") String authConfig,
            @RequestParam(value = "variables", required = false) String variables,
            @RequestParam(value = "variableValues", required = false) String variableValues,
            @RequestParam(value = "method", required = false) String method,
            @RequestParam(value = "endpoint", required = false) String endpoint,
            @RequestParam(value = "headers", required = false) String headers,
            @RequestParam(value ="body", required = false) String body,
            @RequestParam(name = "iconFile", required = false) MultipartFile iconFile) {
        try {
        	var req = new HttpSourceMetadataDTO()
        			.setName(name)
        			.setDisplayName(displayName)
        			.setAuthType(authType)
        			.setAuthConfig(authConfig != null ? mapper.readValue(authConfig, AuthConfig.class) : null)
        			.setVariables(variables != null ? mapper.readValue(variables, new TypeReference<List<KeyValue>>() {}) : List.of())
        			.setVariableValues(variableValues != null ? mapper.readValue(variableValues, new TypeReference<List<KeyValue>>() {}) : List.of())
        			.setMethod(method)
        			.setEndpoint(endpoint)
        			.setHeaders(headers != null ? mapper.readValue(headers, new TypeReference<Map<String, String>>() {}) : Map.of())
					.setBody(body != null ? StringEscapeUtils.unescapeJson(body).trim().replaceAll("^\"|\"$", "") : body)
        			.setIcon(iconFile);
        	return ResponseEntity.ok(
                    connectorMetadataTransformer.toConnectorMetadata(connectorMetadataService.updateHttpSourceDraft(id, req)));
        } catch (Exception e) {
            log.error(e.getMessage());
            JSONObject errResp = new JSONObject();
            errResp.put("message", e.getMessage());
            return new ResponseEntity(errResp, HttpStatus.BAD_REQUEST);
        }
    }

    private void validateFiles(MultipartFile synapseFile, MultipartFile requirementsFile) {
        validateSynapseFile(synapseFile);
        validateSynapseFile(requirementsFile);
    }
    
    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST, value = "/httpsource/{id}/createDraft")
    public ResponseEntity createHttpSourceDraft(@PathVariable String id) throws IOException  {
        return ResponseEntity.ok(connectorMetadataTransformer.toConnectorMetadata(connectorMetadataService.createHttpSourceDraftFor(id)));
    }
    
    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST, value = "/httpsource/{id}/discardDraft")
    public void discardHttpSourceDraft(@PathVariable String id) throws IOException  {
        if(connectorService.isInUse(id)) {
            throw new SyncariValidationException(i18n("connector_in_use"));
        }
        connectorMetadataService.discardDraft(id);
    }
    
    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST, value = "/httpsource/{id}/approve")
    public ResponseEntity approveHttpSource(@PathVariable String id) throws IOException  {
        return ResponseEntity.ok(connectorMetadataTransformer.toConnectorMetadata(connectorMetadataService.approveHttpSource(id)));
    }
    
    
    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST, value = "/httpsource/schema/generate")
    public String generateSchema(@RequestBody String data) {
    	return connectorMetadataService.generateSchema("JSON", data);
    }
    
    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST, value = "/httpsource/{metaId}/entity")
    public HttpSourceEntityResponse createHttSource(@PathVariable String metaId, @RequestBody HttpSourceEntityRequest entityReq) {
        if (StringUtils.isBlank(metaId))
            throw new RuntimeException("Connector meta id cannot be null");
        var response = connectorMetadataService.saveHttpSource(transformer.toHttpSourceConfig(entityReq, null), metaId);
        return transformer.toHttpSourceEntityResponse(response, metaId);
    }
    
    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.PUT, value = "/httpsource/{metaId}/entity/{id}")
    public HttpSourceEntityResponse updateHttSource(@PathVariable String metaId, @PathVariable String id, @RequestBody HttpSourceEntityRequest entityReq) {
        if (StringUtils.isBlank(metaId))
            throw new RuntimeException("Connector meta id cannot be null");
        if (StringUtils.isBlank(id))
            throw new RuntimeException("id cannot be null for edits");
        var response = connectorMetadataService.saveHttpSource(transformer.toHttpSourceConfig(entityReq, id), metaId);
        return transformer.toHttpSourceEntityResponse(response, metaId);
    }
    
    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/httpsource/{metaId}/entity/{id}")
    public HttpSourceEntityResponse getHttSource(@PathVariable String metaId, @PathVariable String id) {
        if (StringUtils.isBlank(metaId))
            throw new RuntimeException("Connector id cannot be null");
        if (StringUtils.isBlank(id))
            throw new RuntimeException("id cannot be null");
        var response = connectorMetadataService.findHttpSource(metaId, id);
        return transformer.toHttpSourceEntityResponse(response, metaId);
    }
    
    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/httpsource/{metaId}/entity")
    public List<HttpSourceEntityListResponse> listHttSource(@PathVariable String metaId) {
        if (StringUtils.isBlank(metaId))
            throw new RuntimeException("Connector meta id cannot be null");
        return connectorMetadataService.findAllHttpSource(metaId).stream().map(src -> transformer.toHttpSourceEntityListResponse(src, metaId)).collect(Collectors.toList());
    }
    
    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.DELETE, value = "/httpsource/{metaId}/entity/{id}")
    public void deleteHttpSourceEntity(@PathVariable String metaId, @PathVariable String id) {
        if (StringUtils.isBlank(metaId))
            throw new RuntimeException("Connector id cannot be null");
        if (StringUtils.isBlank(id))
            throw new RuntimeException("id cannot be null");
        connectorMetadataService.deleteHttpSourceEntity(metaId, id);
    }

    private void validateSynapseFile(MultipartFile file) {
        if (file == null)
            return;
        String[] parts = file.getOriginalFilename().split("[.]");
        if (parts.length <= 1 || !allowedCustomSynapseExt.contains(parts[parts.length - 1])) {
            throw new SyncariValidationException(i18n("unsupported_file_ext"));
        }
        if (!allowedCustomSynapseContentType.contains(file.getContentType())) {
            throw new SyncariValidationException(
                    String.format(i18n("unsupported_content_type"), file.getContentType()));
        }
        List<CustomSynapseDraftIssue> issues = securityScannerService.scan(file);
        List<CustomSynapseDraftIssue> highPriorityIssues = issues.stream().filter(issue -> (issue.getIssue_severity() == CustomSynapseDraftIssueSeverity.HIGH
                || issue.getIssue_severity() == CustomSynapseDraftIssueSeverity.MEDIUM) && !CustomSynapseWhitelistedErrors.WHITELISTED_SECURITY_ERRORS.contains(issue.getIssue_text())).collect(Collectors.toList());
        if(!highPriorityIssues.isEmpty()) {
            String issuesString = highPriorityIssues.stream().map(issue ->
                    String.format("Issue - %s, Severity - %s, Line - %s", issue.getIssue_text(), issue.getIssue_severity(), issue.getLine_number())).collect(Collectors.joining("\n"));
            throw new SyncariValidationException(issuesString);
        }
    }

    private AuthMetadata getAuthMeta(String name) {
        if (ServiceType.Slack.name().equalsIgnoreCase(name) || ServiceType.Insideview.name().equalsIgnoreCase(name)) {
            return ConnectorHelper.getSimpleOAuthType();
        } else if (ServiceType.Zoominfo.name().equalsIgnoreCase(name)) {
            return ConnectorHelper.getUserPwd();
        }
        return ConnectorHelper.getApiKey();
    }
    
    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST, value = "/webhook")
    public ResponseEntity createWebhookReceiver(
            @RequestParam("name") String name,
            @RequestParam("displayName") String displayName,
            @RequestParam("authType") AuthType authType,
            @RequestParam(value ="schema", required = false) String schema,
            @RequestParam(value = "recordSelector", required = false) String recordSelector,
            @RequestParam(value = "idSelector", required = false) String idSelector,
            @RequestParam(value ="responseCode", required = false) Integer responseCode,
            @RequestParam(value ="responseTemplate", required = false) String responseTemplate,
            @RequestParam(name = "iconFile", required = false) MultipartFile iconFile) {
        try {
            var req = new WebhookReceiverMetadataDTO()
                    .setName(name)
                    .setDisplayName(displayName)
                    .setAuthType(authType)
                    .setSchema(schema != null ? StringEscapeUtils.unescapeJson(schema).trim().replaceAll("^\"|\"$", "") : schema)
                    .setRecordSelector(recordSelector)
                    .setIdSelector(idSelector)
                    .setIcon(iconFile)
                    .setResponseCode(responseCode)
                    .setResponseTemplate(responseTemplate != null ? StringEscapeUtils.unescapeJson(responseTemplate).trim().replaceAll("^\"|\"$", "") : responseTemplate);
            return ResponseEntity.ok(
                    connectorMetadataTransformer.toConnectorMetadata(connectorMetadataService.createWebhookReceiverDraft(req)));
        } catch (Exception e) {
            log.error(e.getMessage());
            JSONObject errResp = new JSONObject();
            errResp.put("message", e.getMessage());
            return new ResponseEntity(errResp, HttpStatus.BAD_REQUEST);
        }
    }
    
    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.PUT, value = "/webhook/{id}/updateDraft")
    public ResponseEntity updateWebhookReceiver(@PathVariable String id,
        @RequestParam("name") String name,
        @RequestParam("displayName") String displayName,
        @RequestParam("authType") AuthType authType,
        @RequestParam(value ="schema", required = false) String schema,
        @RequestParam(value = "recordSelector", required = false) String recordSelector,
        @RequestParam(value = "idSelector", required = false) String idSelector,
        @RequestParam(value ="responseCode", required = false) Integer responseCode,
        @RequestParam(value ="responseTemplate", required = false) String responseTemplate,
        @RequestParam(name = "iconFile", required = false) MultipartFile iconFile) {
        try {
          var req = new WebhookReceiverMetadataDTO()
              .setName(name)
              .setDisplayName(displayName)
              .setAuthType(authType)
              .setSchema(schema != null ? StringEscapeUtils.unescapeJson(schema).trim().replaceAll("^\"|\"$", "") : schema)
              .setRecordSelector(recordSelector)
              .setIdSelector(idSelector)
              .setIcon(iconFile)
              .setResponseCode(responseCode)
              .setResponseTemplate(responseTemplate != null ? StringEscapeUtils.unescapeJson(responseTemplate).trim().replaceAll("^\"|\"$", "") : responseTemplate);
            return ResponseEntity.ok(
                    connectorMetadataTransformer.toConnectorMetadata(connectorMetadataService.updateWebhookReceiverDraft(id, req)));
        } catch (Exception e) {
            log.error(e.getMessage());
            JSONObject errResp = new JSONObject();
            errResp.put("message", e.getMessage());
            return new ResponseEntity(errResp, HttpStatus.BAD_REQUEST);
        }
    }
    
    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST, value = "/webhook/{id}/createDraft")
    public ResponseEntity createWebhookReceiverDraft(@PathVariable String id) throws IOException  {
        return ResponseEntity.ok(connectorMetadataTransformer.toConnectorMetadata(connectorMetadataService.createWebhookReceiverDraftFor(id)));
    }
    
    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST, value = "/webhook/{id}/discardDraft")
    public void discardWebhookReceiverDraft(@PathVariable String id) throws IOException  {
        if(connectorService.isInUse(id)) {
            throw new SyncariValidationException(i18n("connector_in_use"));
        }
        connectorMetadataService.discardDraft(id);
    }
    
    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST, value = "/webhook/{id}/approve")
    public ResponseEntity approveWebhookReceiver(@PathVariable String id) throws IOException  {
        return ResponseEntity.ok(connectorMetadataTransformer.toConnectorMetadata(connectorMetadataService.approveWebHookReceiver(id)));
    }
    
    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/webhook/supportedAuthTypes")
    public List<AuthMetadata> whSupportedAuthTypes() {
        return webhookReceiverService.getSupportedAuthTypes();
    }
    
    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST, value = "/webhook/test")
    public WebhookTestResponse webhookTest(@RequestBody WebhookTestRequest testReq) {
      WebhookReceiverResult webhookResponse =
          connectorMetadataService.testWebhookReceiver(testReq.getAuthType(),
              testReq.getAuthConfig(), transformer.toWebhookConfig(testReq), testReq.getBody(), testReq.getHeaders());
        return transformer.toWebhookTestResponse(testReq, webhookResponse);
    }
    
    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/webhook/endpoint")
    public Map<String, String> whgenerateEndpoint() {
        return Map.of("endpoint", webhookReceiverService.getEndpoint());
    }
    
    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/webhook/httpcodes")
    public List<KeyValue> whHttpcodes() {
        return List.of(
            new KeyValue("name", "200 OK", "value", 200),
            new KeyValue("name", "201 Created", "value", 201),
            new KeyValue("name", "202 Accepted", "value", 202),
            new KeyValue("name", "203 Non-Authoritative Information", "value", 203),
            new KeyValue("name", "204 No Content", "value", 204),
            new KeyValue("name", "205 Reset Content", "value", 205),
            new KeyValue("name", "206 Partial Content", "value", 206),
            new KeyValue("name", "207 Multi-Status", "value", 207),
            new KeyValue("name", "208 Already Reported", "value", 208),
            new KeyValue("name", "226 IM Used", "value", 226)
        );
    }
    
    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/sharing/scope")
    public List<KeyValue> sharingScope() {
      List<KeyValue> list = new ArrayList<>();
        for( ConnectorSharingScope scope : ConnectorSharingScope.values()) {
          list.add(new KeyValue("id", scope.name(), "name",
              i18n("connector_sharing_scope_name_" + scope.name().toLowerCase()), "helpText",
              i18n("connector_sharing_scope_help_" + scope.name().toLowerCase())));
        }
        return list;
    }
    
    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST, value = "/{id}/share")
    public ShareConnectorMetaResponse shareConnector(@PathVariable String id, @RequestBody ShareConnectorMetaRequest shareReq) {
      var meta = connectorMetadataService.shareHttpOrWebhookSynapse(id, shareReq);
      return new ShareConnectorMetaResponse().setScope(shareReq.getScope())
          .setInstances(shareReq.getInstances());
    }
    
    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/{id}/share")
    public ShareConnectorMetaResponse shareConnector(@PathVariable String id) {
      var metaOpt = connectorMetadataService.findById(id);
      if(metaOpt.isEmpty()) {
        return null;
      }
      var meta = metaOpt.get();
      var req = connectorMetadataService.detectSharingScope(meta);
      return new ShareConnectorMetaResponse().setScope(req.getScope())
          .setInstances(req.getInstances());
    }
}
@Data
@Accessors(chain = true)
class Mappings {
    String syncariEntity;
    String externalEntity;
    Map<String, String> attributeMapping;
    String direction;
}
