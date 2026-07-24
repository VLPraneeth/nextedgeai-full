package com.syncari.connector.custom;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.gson.GsonFactory;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.Offset.OffsetType;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.service.def.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jooq.lambda.function.Function1;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component(Constants.CUSTOM)
public class CustomService implements OauthAuthenticationService, MetadataService, SynapseInfoService, WebhookService, CommonDataService, HttpService {

    private static final int API_MAX_PAGESIZE = 100;
    // Some external systems are updating records one by one, in which case, the CRUD calls to cloud function can timeout.
    // TODO: this can be driven by the custom synapse itself to let know the framework about the batch size.
    private static final int API_MAX_CRUD_SIZE = 20;
    private static final int SESSION_DURATION = 5; //MINUTES
    private static final int _10_MIN_IN_MILLI = 600000;
    public static final String WEBHOOK_IDENTIFIER = "webhookIdentifier";

    public static final String OAUTH_HOST_STR = "OAUTH_HOST";
    public static final String OAUTH_URI_STR = "OAUTH_URI";
    public static final String OAUTH_TOKEN_URL_STR = "OAUTH_TOKEN_URL";
    public static final String OAUTH_TOKEN_SUFFIX_STR = "OAUTH_TOKEN_SUFFIX";
    public static final String SUPPORT_SIMPLE_OAUTH_REFRESH_STR = "SUPPORT_SIMPLE_OAUTH_REFRESH";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    DefaultAuthTokenHandler tokenHandler;

    @PostConstruct
    ObjectMapper getConfiguredMapper(){
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return objectMapper;
    }

    LoadingCache<CloudFunctionInfo, SynapseInfo> synapseInfoCache = CacheBuilder.newBuilder()
            .maximumSize(100000).expireAfterAccess(SESSION_DURATION, TimeUnit.MINUTES).build(
                    new CacheLoader<>() {
                        @Override
                        public SynapseInfo load(CloudFunctionInfo cfInfo) throws Exception {
                            return ConnectorHelper.backoffAndThrowOriginalException(() -> internalAbout(cfInfo),1000, 5000, 2, Optional.empty());
                        }
                    }
            );

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse response = new TestConnectionResponse();
        try {
            Map<String, Object> payload = new HashMap<>();
            CustomSynapseResponse resp = makeCustomSynapseCall(config, RequestType.TEST, payload);
            Connection respConn = (Connection) resp.unpack();
            response.setAuthConfig(respConn.getAuthConfig());
            response.setMetaConfig(respConn.getMetaConfig());
        } catch (Exception e) {
            log.error("Encountered exception during custom synapse testConnection {}", e.getMessage(), e);
            response.setCode(ConnectorErrorCodes.CONNECTION_ERROR);
            List<String> errors = new ArrayList<>();
            errors.add(e.getMessage());
            response.setErrors(errors);
            response.setMessage(TestConnectionResponse.AUTH_FAILED_MESSAGE +  " Please verify the credentials and retry.");
            // TODO: We need to get the actual failure from cloud functions.
            //handleAuthenticationErrorMessage(response, e);
        }
        return response;
    }

    @Override
    public SynapseInfo about(CloudFunctionInfo cfInfo) {
        return synapseInfoCache.getUnchecked(cfInfo);
    }

    public  SynapseInfo internalAbout(CloudFunctionInfo cfInfo) {
        Map<String, Object> payload = new HashMap<>();
        CustomSynapseResponse resp = makeCustomSynapseCfInfoCall(cfInfo, RequestType.SYNAPSE_INFO, payload);
        SynapseInfo synapseInfo = new SynapseInfo();
        try {
            synapseInfo = (SynapseInfo) resp.unpack();
        } catch(Exception e) {
            log.error("Failed to load synapse metadata from cloud function. restoring defaults.", e);
        }
        if(synapseInfo.getApiMaxCrudSize() == null) {
            synapseInfo.setApiMaxCrudSize(API_MAX_CRUD_SIZE);
        }
        synapseInfo.setType(ConnectorType.Synapse);
        if(synapseInfo.getMetadata().getHelpUrl() == null || synapseInfo.getMetadata().getHelpUrl().isBlank()) {
            synapseInfo.getMetadata().setHelpUrl(getUIMetadata().getHelpUrl());
        }
        synapseInfo.getMetadata().setBackgroundColor(getUIMetadata().getBackgroundColor());
        // TODO: We need to fix these.
        synapseInfo.getMetadata().setIconPath(getUIMetadata().getIconPath());
        synapseInfo.getConfiguredFields().add(ConnectorHelper.getSupportedAuthPicker());
        return synapseInfo;
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest describeRequest) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("entities", List.of(describeRequest.getEntity()));
        CustomSynapseResponse resp = makeCustomSynapseCall(describeRequest.getConnector(), RequestType.DESCRIBE, payload);
        List<EntitySchema> schemas = (List<EntitySchema>) resp.unpack();
        if(schemas.isEmpty()) {
            throw new RuntimeException("Empty response from custom synapse for " + describeRequest.getEntity() + " describe request");
        }
        return Optional.of(schemas.get(0));
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest describeAllRequest) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("entities", describeAllRequest.getEntities());
        CustomSynapseResponse resp = makeCustomSynapseCall(describeAllRequest.getConnector(), RequestType.DESCRIBE, payload);
        return (List<EntitySchema>) resp.unpack();
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        // TODO Auto-generated method stub

    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(new AuthMetadata(AuthType.SimpleOAuth, List.of(), "Simple OAuth", ""));
    }

    @Override
    public List<AuthField> getConfigureFields() {
        return List.of(ConnectorHelper.getEndpointField(), ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public Map<String, String> getEntityMappings() {
        return Map.of();
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    @Override
    public String getName() {
        return Constants.CUSTOM;
    }

    @Override
    public String getCategory() {
        return "Custom Synapse";
    }

    @Override
    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/custom-synapse-default.svg")
                .setDisplayName("Custom")
                .setBackgroundColor("#EFF2F6")
                // Help url for custom synapse is a section.
                .setHelpUrl(helpSectionsBaseUrl + "/4578749288980-Custom-Synapse");
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "";
    }

    private Map<String, String> getOAuthInfoMap(CloudFunctionInfo cloudFunctionInfo){
        SynapseInfo synapseInfo = this.about(cloudFunctionInfo);
        Map<String, String> oAuthInfoMap = synapseInfo.getOauthInfo();
        return oAuthInfoMap;
    }

    private String getOAuthTokenUrl(Map<String, String> oAuthInfoMap, String endpoint){
        String oAuthTokenUrl = oAuthInfoMap.get(OAUTH_TOKEN_URL_STR);
        String oAuthTokenSuffix = oAuthInfoMap.get(OAUTH_TOKEN_SUFFIX_STR);
        if (StringUtils.isBlank(oAuthTokenUrl)){
            if (StringUtils.isNotBlank(oAuthTokenSuffix)) {
                oAuthTokenUrl = StringUtils.stripEnd(endpoint,"/" )+ "/" + StringUtils.stripStart(oAuthTokenSuffix, "/");
            } else {
                oAuthTokenUrl = endpoint;
            }
        }
        return oAuthTokenUrl;
    }

    @Override
    public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
        Map<String, String> oAuthInfoMap =getOAuthInfoMap(oAuthRequest.getCloudFunctionInfo());
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", oAuthRequest.getCode());
        payload.put("clientId", oAuthRequest.getConfig().getClientId());
        payload.put("clientSecret", oAuthRequest.getConfig().getClientSecret());
        payload.put("endpoint", oAuthRequest.getEndpoint());
        payload.put("redirectUri", oAuthRequest.getRedirectUri());
        payload.put("authConfig", oAuthRequest.getConfig());
        CustomSynapseResponse resp =  makeCustomSynapseCfInfoCall(oAuthRequest.getCloudFunctionInfo(), RequestType.GET_ACCESS_TOKEN, payload);
        Map<String, String> authDataMap = (Map<String, String>) resp.unpack();
        Optional<AuthConfig> authConfigOpt = checkSynapseTokenResponse(authDataMap);
        if (authConfigOpt.isPresent()) return authConfigOpt.get();
        AuthConfig authConfig = tokenHandler.getAccessToken( getOAuthTokenUrl(oAuthInfoMap, oAuthRequest.getEndpoint()), authDataMap);
        if(Boolean.valueOf(oAuthInfoMap.get(SUPPORT_SIMPLE_OAUTH_REFRESH_STR)) && StringUtils.isBlank(authConfig.getRefreshToken())){
            authConfig.setRefreshToken(authConfig.getAccessToken());
            authConfig.setLastRefreshed(Instant.now());
        }
        return authConfig;
    }

    @Override
    public AuthConfig refreshToken(ConnectorInfo connector) {
        Map<String, String> oAuthInfoMap =getOAuthInfoMap(connector.getCloudFunctionInfo());
        Map<String, Object> payload = new HashMap<>();
        CustomSynapseResponse resp = makeCustomSynapseCall(connector, RequestType.REFRESH_TOKEN, payload);
        Map<String, String> authDataMap = (Map<String, String>) resp.unpack();
        Optional<AuthConfig> authConfigOpt = checkSynapseTokenResponse(authDataMap);
        if (authConfigOpt.isPresent()) return authConfigOpt.get();
        AuthConfig authConfig = tokenHandler.refreshToken( connector.getAuthConfig(), getOAuthTokenUrl(oAuthInfoMap, connector.getEndpoint()), authDataMap);
        if(Boolean.valueOf(oAuthInfoMap.get(SUPPORT_SIMPLE_OAUTH_REFRESH_STR)) && StringUtils.isBlank(authConfig.getRefreshToken())){
            authConfig.setRefreshToken(authConfig.getAccessToken());
            authConfig.setLastRefreshed(Instant.now());
        }
        return authConfig;
    }

    private Optional<AuthConfig> checkSynapseTokenResponse(Map<String, String> authDataMap) {
        if(authDataMap != null && authDataMap.containsKey("access_token") && authDataMap.get("access_token") != null &&
                authDataMap.containsKey("refresh_token") && authDataMap.get("refresh_token") != null &&
                authDataMap.containsKey("expires_in") && authDataMap.get("expires_in") != null) {
            log.debug("Using authconfig tokens from custom synapse");
            AuthConfig authConfig = new AuthConfig();
            authConfig.setAccessToken(authDataMap.get("access_token"));
            authConfig.setRefreshToken(authDataMap.get("refresh_token"));
            authConfig.setExpiresIn(String.valueOf(authDataMap.get("expires_in")));
            authConfig.setLastRefreshed(Instant.now());
            return Optional.of(authConfig);
        }
        return Optional.empty();
    }

    @Override
    public String getOAuthUri(ConnectorInfo connector) {
        Map<String, String> oAuthinfo =getOAuthInfoMap(connector.getCloudFunctionInfo());
        return oAuthinfo.getOrDefault(OAUTH_URI_STR,"");
    }

    public String getAuthHost( AuthConfig config, CloudFunctionInfo cfInfo) {
        Map<String, String> oAuthinfo =getOAuthInfoMap(cfInfo);
        return oAuthinfo.getOrDefault(OAUTH_HOST_STR, getAuthHost(config));
    }

    @Override
    public String getIdentifier(ConnectorInfo config) {
        return config.getMetaConfig().getOrDefault(WEBHOOK_IDENTIFIER, "").toString();
    }

    @Override
    public String getEndpoint() {
        return "";
    }

    @Override
    public String extractIdentifier(WebhookRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("body", request.getBody());
        payload.put("headers", request.getHeaders());
        payload.put("params", request.getParams());
        CustomSynapseResponse resp = makeCustomSynapseCfInfoCall(request.getCloudFunctionInfo(), RequestType.EXTRACT_WEBHOOK_IDENTIFIER, payload);
        return (String) resp.unpack();
    }

    @Override
    public List<EventData> parseEventData(WebhookRequest request) {
        List<EventData> eventDataList = new ArrayList<>();
        Map<String, Object> payload = new HashMap<>();
        payload.put("body", request.getBody());
        payload.put("headers", request.getHeaders());
        payload.put("params", request.getParams());
        CustomSynapseResponse resp = makeCustomSynapseCall(request.getConfig(), RequestType.PROCESS_WEBHOOK, payload);
        List<EntityData> edList = (List<EntityData>) resp.unpack();
        log.debug("Connector for parsing event data - " + request.getConfig().toString());
        String connectorId = request.getConfig().getId();
        edList.forEach(ed -> {
            EventData eventData = new EventData();
            ed.setConnectorId(connectorId);
            eventData.setConnectorId(connectorId);
            eventData.setData(ed);
            eventData.setOperation(Operation.update);
            eventDataList.add(eventData);
        });
        log.debug("Returned event data" + eventDataList.toString());
        return eventDataList;
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {

        int pageSize = request.getPageSize() > 0 ? request.getPageSize() : API_MAX_PAGESIZE;
        Function1<WatermarkInfo, ReadResponse> generator = (wm) -> {
            Map<String, Object> payload = new HashMap<>();
            payload.put("entity", request.getEntitySchema());
            payload.put("entityWithMappedFields", request.getEntitySchemaWithMappedFields());
            payload.put("watermark", ReadResponse.fromWatermarkInfo(wm));
            CustomSynapseResponse resp = makeCustomSynapseCall(request.getConnector(), RequestType.READ, payload);
            return (ReadResponse) resp.unpack();
        };

        ReadResponse readResponse = new ReadResponse(ReadResponse.fromWatermarkInfo(request.getWatermark()), new ArrayList<>(), OffsetType.NONE);
        CustomListBasedIterator iterator = new CustomListBasedIterator(request.getWatermark(), generator, readResponse, 
            request.getWatermark().getLimit());
        
        return new FetchResponse(request.getWatermark(), iterator);
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {

        List<EntityData> entityList = request.getData().get(request.getConnector().getId());
        List<List<EntityData>> partitions = Lists.partition(entityList, API_MAX_CRUD_SIZE);

        List<EntityData> returnList = new ArrayList<>();
        for (List<EntityData> partition : partitions) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("entity", request.getEntitySchema());
            payload.put("entityWithMappedFields", request.getEntitySchemaWithMappedFields());
            if (request.getWatermark() != null) {
                payload.put("watermark", ReadResponse.fromWatermarkInfo(request.getWatermark()));
            }
            payload.put("data", partition);
            CustomSynapseResponse resp = makeCustomSynapseCall(request.getConnector(), RequestType.GET_BY_ID, payload);
            returnList.addAll((List<EntityData>) resp.unpack());
        }
        return returnList;
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        Integer apiMaxCrudSize = synapseInfoCache.getUnchecked(request.getConnector().getCloudFunctionInfo()).getApiMaxCrudSize();
        List<EntityData> entityList = request.getData().get(request.getConnector().getId());
        List<List<EntityData>> partitions = Lists.partition(entityList, apiMaxCrudSize != null ? apiMaxCrudSize : API_MAX_CRUD_SIZE);
        SyncResponse response = new SyncResponse();

        for (List<EntityData> partition : partitions) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("entity", request.getEntitySchema());
            if (request.getWatermark() != null) {
                payload.put("watermark", ReadResponse.fromWatermarkInfo(request.getWatermark()));
            }
            payload.put("data", partition);
            CustomSynapseResponse resp = makeCustomSynapseCall(request.getConnector(), RequestType.CREATE, payload);
            try {
                List<Result> created = (List<Result>) resp.unpack();

                for (Result res : created) {
                    response.getResults().add(res);
                }
            } catch (NonRetriableException e) {
                handleError(partition, e, response, Operation.create);
            }
        }
        return response;
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        Integer apiMaxCrudSize = synapseInfoCache.getUnchecked(request.getConnector().getCloudFunctionInfo()).getApiMaxCrudSize();
        List<EntityData> entityList = request.getData().get(request.getConnector().getId());
        List<List<EntityData>> partitions = Lists.partition(entityList, apiMaxCrudSize != null ? apiMaxCrudSize : API_MAX_CRUD_SIZE);
        SyncResponse response = new SyncResponse();

        for (List<EntityData> partition : partitions) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("entity", request.getEntitySchema());
            if (request.getWatermark() != null) {
                payload.put("watermark", ReadResponse.fromWatermarkInfo(request.getWatermark()));
            }
            payload.put("data", partition);
            CustomSynapseResponse resp = makeCustomSynapseCall(request.getConnector(), RequestType.UPDATE, payload);
            try {
                List<Result> updated = (List<Result>) resp.unpack();
                for (Result res : updated) {
                    response.getResults().add(res);
                }
            } catch (NonRetriableException e) {
                handleError(partition, e, response, Operation.update);
            }
        }

        return response;
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        Integer apiMaxCrudSize = synapseInfoCache.getUnchecked(request.getConnector().getCloudFunctionInfo()).getApiMaxCrudSize();
        List<EntityData> entityList = request.getData().get(request.getConnector().getId());
        List<List<EntityData>> partitions = Lists.partition(entityList, apiMaxCrudSize != null ? apiMaxCrudSize : API_MAX_CRUD_SIZE);
        SyncResponse response = new SyncResponse();

        for (List<EntityData> partition : partitions) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("entity", request.getEntitySchema());
            if (request.getWatermark() != null) {
                payload.put("watermark", ReadResponse.fromWatermarkInfo(request.getWatermark()));
            }
            payload.put("data", partition);
            CustomSynapseResponse resp = makeCustomSynapseCall(request.getConnector(), RequestType.DELETE, payload);
            try {
                List<Result> deleted = (List<Result>) resp.unpack();
                for (Result res : deleted) {
                    response.getResults().add(res);
                }
            } catch (NonRetriableException e) {
                handleError(partition, e, response, Operation.delete);
            }
        }
        return response;
    }

    private static void handleError(List<EntityData> partition, NonRetriableException e, SyncResponse response, Operation operation) {
        partition.forEach(ed -> {
            boolean success = operation == Operation.delete && e.getStatusCode().equalsIgnoreCase("404");
            Result result = new Result(success, ed.getId(), ed.getSyncariEntityId());
            result.addError(e.getMessage());
            response.getResults().add(result);
        });
    }

    private CustomSynapseResponse makeCustomSynapseCfInfoCall(CloudFunctionInfo cfInfo, RequestType type, Map<String, Object> payload) {
        Connection connection = new Connection();
        connection.setName(cfInfo.getCustSynapseIdentifier());
        AuthConfig authConfig = new AuthConfig();
        authConfig.setEndpoint("https://dummyendpoint");
        connection.setAuthConfig(authConfig);
        CustomSynapseRequest synReq = new CustomSynapseRequest(type, connection, payload, cfInfo.getHost(), cfInfo.getSyncariId());
        String payloadData = getStringifyData(synReq);
        Optional<HttpRequest> cloudFunctionRequest = constructRequest(cfInfo, payloadData);
        return makeCall(cloudFunctionRequest, synReq);

    }

    private String getStringifyData(Object synReq) {
        String payloadData = null;
        try {
            payloadData = objectMapper.writeValueAsString(synReq);
        } catch (JsonProcessingException e) {
            log.error("Error in Serializing CustomSynapseRequest", e);
            throw new RuntimeException(e);
        }
        return payloadData;
    }

    private CustomSynapseResponse makeCustomSynapseCall(ConnectorInfo connector, RequestType type, Map<String, Object> payload) {
        CustomSynapseRequest synReq = new CustomSynapseRequest(type, Connection.fromConnectorInfo(connector), payload,
                connector.getCloudFunctionInfo().getHost(), connector.getCloudFunctionInfo().getSyncariId());
        String payloadData = getStringifyData(synReq);
        Optional<HttpRequest> cloudFunctionRequest = constructRequest(connector.getCloudFunctionInfo(), payloadData);
        return makeCall(cloudFunctionRequest, synReq);
    }

    private CustomSynapseResponse makeCall(Optional<HttpRequest> cloudFunctionRequest, CustomSynapseRequest synReq) {
        CustomSynapseResponse response = new CustomSynapseResponse();
        try {
            if (cloudFunctionRequest.isPresent()) {
                log.debug("Cloud function request type {}", synReq.getType());
                if(synReq instanceof CustomSynapseRequest){
                    log.debug("Cloud function request payload {}",  ((CustomSynapseRequest) synReq).getBody());
                }

                String s = cloudFunctionRequest.get().execute().parseAsString();
                // This is an issue in GCP cloud functions. When the cloud function is not found or not accessible,
                // the response is a lengthy sign one page which is not pretty.
                if (StringUtils.isNotEmpty(s) && s.startsWith("<!DOCTYPE html>")) {
                    String errorMessage = String.format("Custom Synapse %s not found", cloudFunctionRequest.get().getUrl());
                    throw new RuntimeException(errorMessage);
                }
                // TODO: This should be modified to debug level once we have hardened custom synapses.
                log.debug("Cloud function raw response {}", s);
                response.setType(synReq.getType());
                response.setResponse(s);
            }
        } catch (Exception e) {
            String msg = "Failed to call custom synapse execution due to " + e.getMessage();
            log.error(msg);
            log.debug(ExceptionUtils.getStackTrace(e));
            // TODO For now change the type to RetriableException. Ideally we handle Retriable and NonRetriable Exception cases correctly
            // Since the pipeline is paused for NonRetriable, it ended up pausing transient cases like read timeout
            throw new RetriableException(ErrorCodes.BAD_REQUEST.name(), msg, HttpStatus.BAD_REQUEST.toString(), e);
        }
        return response;
    }

    private Optional<HttpRequest> constructRequest(CloudFunctionInfo cfInfo, String payload) {
        String url = cfInfo.getCloudFunctionEndpoint() + cfInfo.getCustSynapseIdentifier();

        try {
            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(new ByteArrayInputStream(Base64.getDecoder().decode(cfInfo.getExecutorCredentialsKey())))
                    .createScoped(Collections.singleton("https://www.googleapis.com/auth/cloud-platform"));
            if (!(credentials instanceof IdTokenProvider)) {
                throw new IllegalArgumentException("Credentials are not an instance of IdTokenProvider.");
            }
            IdTokenCredentials tokenCredential = null;
            try {
                tokenCredential = IdTokenCredentials.newBuilder()
                        .setIdTokenProvider((IdTokenProvider) credentials)
                        .setTargetAudience(url)
                        .build();
            } catch (Exception e) {
                // Retry after 30 secs
                Thread.sleep(30000);
                tokenCredential = IdTokenCredentials.newBuilder()
                        .setIdTokenProvider((IdTokenProvider) credentials)
                        .setTargetAudience(url)
                        .build();
            }

            GenericUrl genericUrl = new GenericUrl(url);
            HttpCredentialsAdapter adapter = new HttpCredentialsAdapter(tokenCredential);
            HttpTransport transport = new NetHttpTransport();
            HttpContent httpContent = ByteArrayContent.fromString("application/json", payload);
            HttpRequest httpRequest = transport.createRequestFactory(adapter).buildPostRequest(genericUrl, httpContent);
            httpRequest.setParser(new JsonObjectParser(new GsonFactory()));
            httpRequest.setReadTimeout(_10_MIN_IN_MILLI);
            httpRequest.setConnectTimeout(_10_MIN_IN_MILLI);
            httpRequest.setWriteTimeout(_10_MIN_IN_MILLI);
            return Optional.of(httpRequest);
        } catch (Exception e) {
            log.error("Failed to construct custom synapse request, {}", e.getMessage());
            log.debug(ExceptionUtils.getStackTrace(e));
            if(e.getMessage().contains("Error getting id token for service account")) {
                throw new RetriableException(ErrorCodes.BAD_REQUEST.name(), "Failed to construct custom synapse request", HttpStatus.BAD_REQUEST.toString(), e);
            }
            throw new NonRetriableException(ErrorCodes.BAD_REQUEST, "Failed to construct custom synapse request", HttpStatus.BAD_REQUEST.toString(), e);
        }
    }

    public ResponseEntity<String> doPost(ConnectorInfo connectorInfo, String url, String body, String method){
        return getResponseFromCustomSynapse(connectorInfo, url, body, method, RequestType.HTTP_POST);
    }

    public ResponseEntity<String> doPut(ConnectorInfo connectorInfo, String url, String body, String method){
        return getResponseFromCustomSynapse(connectorInfo, url, body, method, RequestType.HTTP_PUT);
    }

    public ResponseEntity<String> doDelete(ConnectorInfo connectorInfo, String url, String body, String method){
        return getResponseFromCustomSynapse(connectorInfo, url, body, method, RequestType.HTTP_DELETE);
    }

    private ResponseEntity<String> getResponseFromCustomSynapse(ConnectorInfo connectorInfo, String url, String body, String method, RequestType requestType) {

        //Raw Http Details need to be set in payload object
        Map<String,Object> payload = new HashMap<>();
        payload.put("url",url);
        payload.put("body",body);
        payload.put("method",method);

        CustomSynapseResponse response = makeCustomSynapseCall(connectorInfo,requestType,payload);

        HashMap<String,Object> result = (HashMap<String, Object>) response.unpack();
        return new ResponseEntity<>(result.containsKey("body") ? result.get("body").toString() : StringUtils.EMPTY , HttpStatus.resolve((Integer) result.get("status_code")));

    }

    public Map<String, String> getHeaders(ConnectorInfo connectorInfo) {
        try {
            Map<String, Object> payload = new HashMap<>();
            CustomSynapseResponse resp = makeCustomSynapseCall(connectorInfo, RequestType.GET_HEADERS, payload);
            return (Map<String, String>) resp.unpack();
        } catch (Exception e) {
            log.error("Encountered exception during custom synapse getHeaders {}, stack trace {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw e;
        }
    }

    @Override
    public List<EntityData> search(SearchRequest searchRequest) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("query", searchRequest.getQuery());
            payload.put("params", searchRequest.getParams());
            CustomSynapseResponse resp = makeCustomSynapseCall(searchRequest.getConnector(), RequestType.SEARCH, payload);
            return (List<EntityData>) resp.unpack();
        } catch (Exception e) {
            log.error("Encountered exception during custom synapse search {}, stack trace {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw e;
        }
    }

}