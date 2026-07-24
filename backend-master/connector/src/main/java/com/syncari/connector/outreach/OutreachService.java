package com.syncari.connector.outreach;

import static com.syncari.connector.ConnectorHelper.withBackoffAndErrorHandling;
import static java.lang.String.format;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import com.syncari.connector.data.*;

import com.syncari.connector.exception.NonRetriableException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jooq.lambda.function.Function3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.ConnectorErrorCodes;
import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.DefaultAuthTokenHandler;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.iterator.DefaultCursorBasedIterator;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.OauthAuthenticationService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.utils.DateUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component(Constants.OUTREACH)
public class OutreachService implements OauthAuthenticationService, CommonDataService, MetadataService, SynapseInfoService {
    private static final String ID = "Id";
    @Autowired
    ObjectMapper mapper;
    @Autowired
    DateUtil dateUtil;
    @Autowired
    DefaultAuthTokenHandler tokenHandler;

    // This is the max pagesize supported by outreach pagination. https://api.outreach.io/api/v2/docs#pagination
    public static int API_MAX_PAGESIZE = 200;
    private static int CLOCK_SKEW_IN_SEC = 30;

    private static final String CREATE_DATA = "/api/v2/%s";
    private static final String UPDATE_DATA = "/api/v2/%s/%s";
    private static final String DELETE_DATA = "/api/v2/%s/%s";
    private static final String GET_BY_WATERMARK = "/api/v2/%s?sort=updatedAt&page[size]=%s&filter[updatedAt]=%s..inf";
    private static final String GET_BY_WATERMARK_ROLE = "/api/v2/%s?page[size]=%s";
    private static final String GET_BY_IDS = "/api/v2/%s?filter[id]=%s";
    private static String OAUTH_URL = "https://api.outreach.io/oauth/token";
    private static final Map<String, String> objName = Map.ofEntries(
            Map.entry("account", "accounts"),
            Map.entry("callDisposition", "callDispositions"),
            Map.entry("call", "calls"),
            Map.entry("mailbox", "mailboxes"),
            Map.entry("mailing", "mailings"),
            Map.entry("opportunity", "opportunities"),
            Map.entry("prospect", "prospects"),
            Map.entry("role", "roles"),
            Map.entry("sequenceState", "sequenceStates"),
            Map.entry("sequenceStep", "sequenceSteps"),
            Map.entry("sequence", "sequences"),
            Map.entry("stage", "stages"),
            Map.entry("taskPriority", "taskPriorities"),
            Map.entry("task", "tasks"),
            Map.entry("user", "users")
    );

    private static final Map<String, Map<String, String>> EntityFieldRelationshipMap  = Map.ofEntries(
            Map.entry("task", Map.ofEntries(
                            Map.entry("prospectId", "subject"),
                            Map.entry("accountId", "subject")
                    )
            )
    );
    
    @Override
    public Map<String, String> getEntityMappings() {
        return Map.of(Constants.CONTACT.toLowerCase(), "prospect", Constants.OPPORTUNITY.toLowerCase(), "opportunity",
                Constants.ACCOUNT.toLowerCase(), "account", Constants.TICKET.toLowerCase(), "task");
    }
    
    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return OutreachSeed.getAttributeMappings(entityApiName);
    }
    
    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(new AuthMetadata(AuthType.Oauth,
                List.of(ConnectorHelper.getClientIdField(), ConnectorHelper.getClientSecretField()), "OAuth", ""));
    }

    @Override
    public List<AuthField> getConfigureFields() {
        return List.of(ConnectorHelper.getEndpointField(), ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public String getCategory() {
        return "CRM";
    }
    
    @Override
    public String getName() {
        return Constants.OUTREACH;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/outreach.svg")
                .setDisplayName("Outreach")
                .setBackgroundColor("#F0F2FF")
                .setHelpUrl(helpArticlesBaseUrl + "/360052204612-Outreach-Setup");
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19208576431892";
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        String plural = objName.get(request.getEntityName());

        Function3<WatermarkInfo, Integer, String, DataWithCursor> generator = (wm, pageSize,
                changeStream) -> {
            String url = changeStream;
            // If for first page, changeStream will be empty, in which case, begin the cursor iteration.
            if (StringUtils.isEmpty(url)) {
                String wmDate = dateUtil.formatDate(Instant.ofEpochMilli(wm.getStart()), DateUtil.dateOnlyFormat);
                if (request.getEntityName().equals("role")){
                    url = format(request.getConnector().getEndpoint() + GET_BY_WATERMARK_ROLE, plural, pageSize);
                } else {
                    url = format(request.getConnector().getEndpoint() + GET_BY_WATERMARK, plural, pageSize, wmDate);
                }
            }
            return get(url, request, plural);
        };

        int pgSize = (request.getPageSize() <= 0) ? API_MAX_PAGESIZE : request.getPageSize();

        DefaultCursorBasedIterator iterator = new DefaultCursorBasedIterator(request.getWatermark(),
                request.getWatermark().getChangeStream(),
                request.getWatermark().getOffset(), generator, new ArrayList<>(), pgSize, request.getWatermark().getLimit());
        return new FetchResponse(request.getWatermark(), iterator);
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        String plural = objName.get(request.getEntityName());
        String url = format(request.getConnector().getEndpoint() + GET_BY_IDS, plural, getIds(request));
        return get(url, request, plural).getData();
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
      //TODO
        return 0;
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        String plural = objName.get(request.getEntityName());
        EntitySchema schema = request.getEntitySchema();
        Map<String, AttributeSchema> relationshipKeys = schema.getAttributes().stream()
                .filter(attributeSchema -> attributeSchema.isReference())
                .collect(Collectors.toMap(attributeSchema -> attributeSchema.getApiName(), attributeSchema -> attributeSchema));
        SyncariEntityDataRestClient restClient = getClient(getSingleJsonConfig(request.getEntityName()));
        SyncResponse response = new SyncResponse(true);
        List<EntityData> toBeCreated = request.getData().get(request.getConnector().getId());
        if (toBeCreated == null || toBeCreated.isEmpty()) {
            log.info("Nothing to be created for outreach");
            return response;
        }
        log.info(format("Calling create for outreach with size %s", toBeCreated.size()));
        for (EntityData data : toBeCreated) {
            PostRequest req = new PostRequest();
            Data d = new Data();
            d.setType(request.getEntityName());
            data.getValues().forEach((k, v) -> {
                if(relationshipKeys.containsKey(k)) {
                    if(v != null) {
                        AttributeSchema attributeSchema = relationshipKeys.get(k);
                        RelationshipData relationshipData = new RelationshipData();
                        relationshipData.data.put("type", attributeSchema.getReferenceTo());
                        relationshipData.data.put("id", v);
                        String relationshipApiName = getRelationshipName(request.getEntityName(), k);
                        d.getRelationships().put(relationshipApiName, relationshipData);
                    }
                }
                else {
                    if(!k.equalsIgnoreCase("id")) {
                        d.getAttributes().put(k, getConvertedValue(schema.getField(k).get(),v));
                    }

                }
            });
            req.setData(d);
            try {
                String url = String.format(request.getConnector().getEndpoint() + CREATE_DATA, plural);
                log.debug("Request body: {}", mapper.writeValueAsString(req));
                ResponseEntity<String> resp = withBackoffAndErrorHandling(()->restClient.getTemplate().exchange(url, HttpMethod.POST,
                        new HttpEntity(req, restClient.getHeaders(request.getConnector().getAuthConfig())), String.class));
                Map<String, Object> postResponse = mapper.readValue(resp.getBody(), Map.class);
                Map responseData = (Map<String, Object>)postResponse.get("data");
                log.debug("Response body: {}", resp.getBody());
                response.getResults().add(new Result(true, String.valueOf(responseData.get("id")), data.getSyncariEntityId()));
                log.info("Calling create for outreach complete");
            } catch (NonRetriableException e) {
                if (e.getStatusCode().contains("422 UNPROCESSABLE_ENTITY")){
                    Result error = new Result(false, null, data.getSyncariEntityId());
                    error.addError(e.getMessage());
                    response.getResults().add(error);
                } else {
                    log.error(ExceptionUtils.getStackTrace(e));
                    throw new RuntimeException(e.getMessage());
                }
            } catch (Exception e) {
                log.error(ExceptionUtils.getStackTrace(e));
                throw new RuntimeException(e.getMessage());
            }
        }
        return response;
    }

    private String getRelationshipName(String entityName, String relationShipAPIName){
        return EntityFieldRelationshipMap.getOrDefault(entityName, new HashMap<>()).getOrDefault(relationShipAPIName, relationShipAPIName.substring(0, relationShipAPIName.length() - 2));
    }

    private Object getConvertedValue(AttributeSchema attributeSchema, Object val){
        if (val != null && "datetime".equals(attributeSchema.getDataType())){
            if(val instanceof Instant){
                return dateUtil.formatDate((Instant)val, DateUtil.dateFormatMillis);
            } else if(val instanceof ZonedDateTime){
                return dateUtil.formatDate(((ZonedDateTime) val).toInstant(), DateUtil.dateFormatMillis);
            }
        }
        return val;
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        String plural = objName.get(request.getEntityName());
        EntitySchema schema = request.getEntitySchema();
        Map<String, AttributeSchema> relationshipKeys = schema.getAttributes().stream()
                .filter(attributeSchema -> attributeSchema.isReference())
                .collect(Collectors.toMap(attributeSchema -> attributeSchema.getApiName(), attributeSchema -> attributeSchema));
        SyncariEntityDataRestClient restClient = getClient(getSingleJsonConfig(request.getEntityName()));
        SyncResponse response = new SyncResponse(true);
        List<EntityData> toBeUpdated = request.getData().get(request.getConnector().getId());
        if (toBeUpdated == null || toBeUpdated.isEmpty()) {
            log.info("Nothing to be updated for outreach");
            return response;
        }
        log.info(format("Calling update for outreach with size %s", toBeUpdated.size()));
        for (EntityData data : toBeUpdated) {
            try {
                DataWithIdWrapper req = new DataWithIdWrapper();
                Long recordId = Long.valueOf(data.getId());
                DataWithId d = new DataWithId(recordId, request.getEntityName());
                data.getValues().forEach((k, v) -> {
                    if(relationshipKeys.containsKey(k)) {
                        String relationshipApiName = getRelationshipName(request.getEntityName(), k);
                        AttributeSchema attributeSchema = relationshipKeys.get(k);
                        RelationshipData relationshipData = new RelationshipData();
                        if(v != null) {
                            relationshipData.data.put("type", attributeSchema.getReferenceTo());
                            relationshipData.data.put("id", v);
                        }
                        d.getRelationships().put(relationshipApiName, relationshipData);
                    }
                    else {
                        if(!k.equalsIgnoreCase("id")) {
                            d.getAttributes().put(k, getConvertedValue(schema.getField(k).get(),v));
                        }
                    }
                });
                req.setData(d);
                String body = mapper.writeValueAsString(req);
                log.debug("Request body: {}", body);
                String url = String.format(request.getConnector().getEndpoint() + UPDATE_DATA, plural, recordId);

                ResponseEntity<String> resp = restClient.patch(url,body,request.getConnector().getAuthConfig());

                if(resp.getStatusCodeValue() != 200) {
                    log.error("Response body: {}", resp.getBody());
                    throw new RuntimeException("Error updating outreach data "+resp.getStatusCodeValue()+" "+resp.getBody());
                }
                log.info("Calling update for outreach complete");
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage());
            }
        }
        return response;
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        String plural = objName.get(request.getEntityName());
        SyncariEntityDataRestClient restClient = getClient(getSingleJsonConfig(request.getEntityName()));
        SyncResponse response = new SyncResponse(true);
        List<EntityData> toBeDeleted = request.getData().get(request.getConnector().getId());
        if (toBeDeleted == null || toBeDeleted.isEmpty()) {
            log.info("Nothing to be deleted for outreach");
            return response;
        }
        log.info(format("Calling delete for outreach with size %s", toBeDeleted.size()));
        for (EntityData data : toBeDeleted) {
            try {
                log.debug("Deleting {} with id {}", request.getEntityName(), data.getId());
                String url = String.format(request.getConnector().getEndpoint() + DELETE_DATA, plural, Long.valueOf(data.getId()));
                ResponseEntity<String> resp = withBackoffAndErrorHandling(()->restClient.getTemplate().exchange(url, HttpMethod.DELETE,
                        new HttpEntity(restClient.getHeaders(request.getConnector().getAuthConfig())), String.class));
                if(resp.getStatusCodeValue() != 204) {
                    log.error("Response body: {}", resp.getBody());
                    throw new RuntimeException("Error deleting outreach data "+resp.getStatusCodeValue()+" "+resp.getBody());
                }
                log.info("Calling create for outreach complete");
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage());
            }
        }
        return response;
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        switch (request.getEntity()) {
            case "account":
                return Optional.of(OutreachSeed.getAccountSchema());
            case "callDisposition":
                 return Optional.of(OutreachSeed.getCallDispositionSchema());
            case "call":
                 return Optional.of(OutreachSeed.getCallSchema());
            case "mailbox":
                return Optional.of(OutreachSeed.getMailboxSchema());
            case "mailing":
                return Optional.of(OutreachSeed.getMailingSchema());
            case "opportunity":
                return Optional.of(OutreachSeed.getOpportunitySchema());
            case "prospect":
                return Optional.of(OutreachSeed.getProspectSchema());
            case "role":
                return Optional.of(OutreachSeed.getRoleSchema());
            case "sequenceState":
                return Optional.of(OutreachSeed.getSequenceStateSchema());
            case "sequenceStep":
                return Optional.of(OutreachSeed.getSequenceStepSchema());
            case "sequence":
                return Optional.of(OutreachSeed.getSequenceSchema());
            case "stage":
                return Optional.of(OutreachSeed.getStageSchema());
            case "taskPriority":
                return Optional.of(OutreachSeed.getTaskPrioritySchema());
            case "task":
                return Optional.of(OutreachSeed.getTaskSchema());
            case "user":
                return Optional.of(OutreachSeed.getUserSchema());
            default:
                break;
        }
        return Optional.empty();
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        // Outreach does not have metadata apis, so we rely on our seeded metadata
        // for all entities
        return List.of(
                OutreachSeed.getAccountSchema(),
                OutreachSeed.getCallDispositionSchema(),
                OutreachSeed.getCallSchema(),
                OutreachSeed.getMailboxSchema(),
                OutreachSeed.getMailingSchema(),
                OutreachSeed.getOpportunitySchema(),
                OutreachSeed.getProspectSchema(),
                OutreachSeed.getRoleSchema(),
                OutreachSeed.getSequenceStateSchema(),
                OutreachSeed.getSequenceStepSchema(),
                OutreachSeed.getSequenceSchema(),
                OutreachSeed.getStageSchema(),
                OutreachSeed.getTaskPrioritySchema(),
                OutreachSeed.getTaskSchema(),
                OutreachSeed.getUserSchema()
        );
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        throw new RuntimeException("Outreach does not support create field");
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new RuntimeException("Outreach does not support delete field");
    }

    @Override
    public String getOAuthUri(ConnectorInfo connector) {
        return "/oauth/authorize?client_id={{client_id}}&redirect_uri={{redirect_uri}}&response_type=code&scope=" +
                "accounts.all+" +
                "callDispositions.all+" +
                "calls.all+" +
                "emailAddresses.all+" +
                "mailboxes.read+" +
                "mailings.all+" +
                "opportunities.all+" +
                "prospects.all+" +
                "roles.all+" +
                "stages.all+" +
                "sequenceStates.all+" +
                "sequenceSteps.all+" +
                "sequences.all+" +
                "taskPriorities.read+" +
                "tasks.all+" +
                "users.all+" +
                "webhooks.all";
    }
    
    @Override
    public AuthConfig refreshToken(ConnectorInfo connector) {
        AuthConfig config = connector.getAuthConfig();
        Map<String, String> map = Map.of("grant_type", "refresh_token",
                "refresh_token", config.getRefreshToken(),
                "client_id", config.getClientId(), "client_secret", config.getClientSecret(), 
                "redirect_uri", config.getRedirectUri());
        
        return tokenHandler.refreshToken(config, OAUTH_URL, map);
    }

    @Override
    public int clockSkewTolerance(ConnectorInfo connectorInfo) { return CLOCK_SKEW_IN_SEC; }

    @Override
    public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
        Map<String, String> map = Map.of("grant_type", "authorization_code", "code", oAuthRequest.getCode(), "client_id",
                oAuthRequest.getConfig().getClientId(), "client_secret", oAuthRequest.getConfig().getClientSecret(), 
                "redirect_uri", oAuthRequest.getRedirectUri());

        return tokenHandler.getAccessToken(OAUTH_URL, map);
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new RuntimeException("createObject not supported in outreach yet");
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse response = new TestConnectionResponse();
        try {
            describe(new DescribeRequest(config, "accounts"));
            log.info(format("Successfully authenticated outreach connection for %s", config.getName()));
        } catch (Exception e) {
            response.setCode(ConnectorErrorCodes.CONNECTION_ERROR);
            response.setMessage(e.getMessage());
        }
        return response;
    }

    private JsonParserConfig getBatchJsonConfig(String plural) {
        return new JsonParserConfig("data", "data[{i}]", null, ID, true, "data[{i}].__key__");
    }

    SyncariEntityDataRestClient getClient(JsonParserConfig config) {
        return new OutreachRestClient(config, mapper);
    }

    private JsonParserConfig getSingleJsonConfig(String plural) {
        return new JsonParserConfig(null, null, null, ID, true, null);
    }

    private String getIds(SyncRequest request) {
        List<EntityData> entityList = request.getData().get(request.getConnector().getId());
        return String.join(",", entityList.stream().map(e -> e.getId()).collect(Collectors.toList()));
    }

    private DataWithCursor get(String url, SyncRequest request, String plural) {
        OutreachRestClient restClient = new OutreachRestClient(getBatchJsonConfig(plural), mapper);
        return restClient.getDataWithCursor(url, request.getConnector().getAuthConfig());
    }
}

@lombok.Data
class PostRequest {
    Data data;
}

@lombok.Data
class DataWithIdWrapper {
    DataWithId data;
}

@lombok.Data
class Data {
    String type;
    Map<String, Object> attributes = new HashMap<>();
    Map<String, RelationshipData> relationships = new HashMap<>();
}

@lombok.Data
class RelationshipData {
    Map<String, Object> data= new HashMap<>();
}

@lombok.Data
class DataWithId {
    String type;
    long id;
    Map<String, Object> attributes = new HashMap<>();
    Map<String, RelationshipData> relationships = new HashMap<>();
    
    public DataWithId() {}
    
    public DataWithId(long id, String type) {
        this.id = id;
        this.type = type;
    }
}
