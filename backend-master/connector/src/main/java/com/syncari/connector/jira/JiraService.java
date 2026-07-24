package com.syncari.connector.jira;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.DefaultDataIterator;
import com.syncari.connector.data.iterator.JiraDataIterator;
import com.syncari.connector.JiraEntityPage;
import com.syncari.connector.exception.*;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.OauthAuthenticationService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.connector.service.helper.JiraHelper;
import com.syncari.utils.DateUtil;
import com.syncari.utils.Pair;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jooq.lambda.function.Function2;
import org.jooq.lambda.function.Function3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

@Slf4j
@Component(Constants.JIRA)
public class JiraService
        implements OauthAuthenticationService, CommonDataService, MetadataService, SynapseInfoService {
    private static final String SYSTEM = "system";
    public static final String PROJECT_KEY = "projectKey";
    public static final String UPDATED_AT_FORMAT = "yyyy-MM-dd HH:mm";
    public static final String UPDATED_FORMAT = "yyyy-MM-dd\'T\'HH:mm:ss.SSSZ";
    @Autowired
    ObjectMapper mapper;
    @Autowired
    DateUtil dateUtil;
    @Autowired
    DefaultAuthTokenHandler tokenHandler;
    @Autowired
    JiraHelper helper;
    private static String GET_USER_URL = "rest/api/3/users/search?%s";
    private static String GET_CURRENT_USER = "rest/api/3/myself";
    private static String GET_PROJECT = "rest/api/3/project/%s";
    private static String GET_ISSUE = "rest/api/3/issue/%s";
    private static Map<String, String> GET_OBJECT = Map.of(JiraSeed.COMMENT, "rest/api/3/issue/%s/comment",
            JiraSeed.PRIORITY, "rest/api/3/priority",
            JiraSeed.RESOLUTION, "rest/api/3/resolution",
            JiraSeed.COMPONENT, "rest/api/3/project/%s/component",
            JiraSeed.ISSUETYPE, "rest/api/3/issuetype",
            JiraSeed.STATUS, "rest/api/3/status",
            JiraSeed.STATUS_CATEGORY, "rest/api/3/statuscategory");
    private static Map<String, String> GET_OBJECT_BY_ID = Map.of(JiraSeed.COMMENT, "rest/api/3/issue/%s/comment/%s",
            JiraSeed.PRIORITY, "rest/api/3/priority/%s",
            JiraSeed.RESOLUTION, "rest/api/3/resolution/%s",
            JiraSeed.ISSUETYPE, "rest/api/3/issuetype/%s",
            JiraSeed.STATUS, "rest/api/3/status/%s",
            JiraSeed.STATUS_CATEGORY, "rest/api/3/statuscategory/%s");
    private static String GET_ISSUE_FIELDS = "rest/api/3/field";
    private static String GET_ISSUES = "rest/api/3/search/jql?%s&maxResults=50&fields=*all,-comment&jql=project IN (%s) AND updated>'%s' ORDER BY updated ASC";
    private static String CREATE_ISSUE = "rest/api/3/issue";
    private static String MODIFY_ISSUE = "/rest/api/3/issue/%s?notifyUsers=%s";
    private static final Map<String, String> urlMap = Map.of(JiraSeed.ISSUE, GET_ISSUES,
            Constants.USER.toLowerCase(), GET_USER_URL);
    private static final List<String> allowedTypes = List.of("datetime", "string", "number", "date", "float", "option",
            "textarea", "url", JiraSeed.RESOLUTION, JiraSeed.PRIORITY, "project", JiraSeed.USER, "array", "issuetype",
            "reporter","assignee",JiraSeed.STATUS_CATEGORY, JiraSeed.STATUS, "parent");
    private static final List<String> multivalued = List.of("labels");
    private static final List<String> refDatatype = List.of(JiraSeed.RESOLUTION, JiraSeed.PRIORITY, "project",
            JiraSeed.USER, "issuetype", "user", "reporter", "assignee",JiraSeed.STATUS_CATEGORY, JiraSeed.STATUS, "parent");
    private static final List<String> requiredIssueFields = List.of("summary", "issuetype");

    private static final int DEFAULT_RETRY_TIME = 5 *  60;

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return helper.getSupportedAuthTypes();
    }

    @Override
    public List<AuthField> getConfigureFields() {
        AuthField projectKey = new AuthField().setName(PROJECT_KEY).setLabel(i18n("project_key"))
                .setDataType("text").setHelpSummary(i18n("project_key_summary"));
        AuthField notifyUsers = new AuthField().setName("notifyUsers").setLabel(i18n("notifyUsers"))
                .setDataType("text").setHelpSummary(i18n("notifyUsers_summary")).setRequired(false);
        return List.of(ConnectorHelper.getEndpointField(), projectKey, notifyUsers, ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public List<Capability> getCapabilities() {
        return List.of(Capability.create, Capability.update, Capability.delete,
                Capability.getById, Capability.noWatermark, Capability.schemaCreateField,
                Capability.getByWatermark, Capability.schemaEditInSyncari, Capability.userEditableId,
                Capability.userEditableWm);
    }

    @Override
    public String getCategory() {
        return "Customer Success";
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19200815459860";
    }

    @Override
    public String getName() {
        return Constants.JIRA;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/jira.svg")
                .setDisplayName("Jira")
                .setBackgroundColor("#F0F6FF")
                .setHelpUrl(helpArticlesBaseUrl + "/360054741932-Jira-Platform-Setup");
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        String connectorId = request.getConnector().getId();
        
        // For ISSUE entity, use new JiraDataIterator with token-based pagination
        if (JiraSeed.ISSUE.equals(request.getEntityName()) || "request".equals(request.getEntityName())) {
            Function2<WatermarkInfo, String, JiraEntityPage> generator = (wm, pageToken) -> {
                try {
                    ZoneId timeZone = getTimeZone(request.getConnector());
                    String userZoneWmDate = dateUtil.formatDate(Instant.ofEpochMilli(wm.getStart()), UPDATED_AT_FORMAT, timeZone);
                    List<String> projects = getprojectKeys(request.getConnector());
                    String projectFilter = projects.stream()
                            .map(key -> "\"" + key + "\"")
                            .collect(Collectors.joining(","));
                    
                    String tokenPart = pageToken != null ? "&nextPageToken=" + pageToken : "";
                    String path = format(getHost(request.getConnector()) + GET_ISSUES, tokenPart,
                            projectFilter, userZoneWmDate);
                    
                    return getIssuesWithToken(path, request);
                } catch (RetriableException e) {
                    if (ErrorCodes.TOO_MANY_REQUESTS.name().equals(e.getErrorCode())) {
                        throw new QuotaExceededException(e.getErrorCode(), e.getMessage(), e.getStatusCode().toString(), connectorId, getRetryTime(e));
                    }
                    throw e;
                }
            };
            
            JiraDataIterator iterator = new JiraDataIterator(request.getWatermark(), generator);
            return new FetchResponse(request.getWatermark(), iterator);
        }
        
        // For other entities, keep existing logic
        ValueHolder<String> lastOffset = new ValueHolder<>();
        Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator = (wm, pageSize, offset) -> {
            try {
                if (offset != 0 && lastOffset.get() == null)
                    return Pair.of(0L, new ArrayList<EntityData>().stream());
                //WARNING requires user tz to be UTC
                String wmDate = dateUtil.formatDate(Instant.ofEpochMilli(wm.getStart()), UPDATED_AT_FORMAT);
                String path;
                String offsetPart = lastOffset.get() != null ? "&startAt=" + lastOffset.get() : "";
                Response response = null;
                List<String> projects = getprojectKeys(request.getConnector());
                switch (request.getEntityName()) {
                    case JiraSeed.USER:
                        path = format(getHost(request.getConnector()) + urlMap.get(request.getEntityName()), offsetPart);
                        List<EntityData> result = new ArrayList<>();
                        ResponseEntity<String> res = getClient().getResponse(path, request.getConnector().getAuthConfig());
                        try {
                            List rows = mapper.readValue(res.getBody(), List.class);
                            if (rows != null && rows.size() > 0) {
                                for (int i = 0; i < rows.size(); i++) {
                                    Map row = (Map) rows.get(i);
                                    EntityData data = extractUser(request, result, connectorId, row);
                                    result.add(data);
                                }
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e.getMessage());
                        }
                        response = new Response(String.valueOf(offset+result.size()), result);
                        break;
                    case JiraSeed.PRIORITY:
                    case JiraSeed.RESOLUTION:
                    case JiraSeed.COMMENT:
                    case JiraSeed.ISSUETYPE:
                    case JiraSeed.STATUS:
                    case JiraSeed.STATUS_CATEGORY:
                        response = getObjects(getHost(request.getConnector()) + GET_OBJECT.get(request.getEntityName()), request);
                        break;
                    case JiraSeed.COMPONENT:
                        List<EntityData> records = new ArrayList<>();
                        for(String project: projects) {
                            String projectId = getProjectId(request.getConnector(), project);
                            String url = String.format(GET_OBJECT.get(request.getEntityName()), projectId);
                            Response currResponse = getComponents(getHost(request.getConnector()) + url, request);
                            records.addAll(currResponse.getRecords());
                        }
                        response = new Response(null, records);
                        break;

                    default:
                        throw new RuntimeException("Unsupported get for " + request.getEntityName());
                }

                lastOffset.set(response.getOffset());
                return Pair.of(Long.valueOf(response.getRecords().size()), response.getRecords().stream());
            } catch (RetriableException e) {
                if (ErrorCodes.TOO_MANY_REQUESTS.name().equals(e.getErrorCode())) {
                    throw new QuotaExceededException(e.getErrorCode(), e.getMessage(), e.getStatusCode().toString(), connectorId, getRetryTime(e));
                }
                throw e;
            }
        };

        DefaultDataIterator iterator = new DefaultDataIterator(request.getWatermark(),
                request.getWatermark().getOffset(), generator, new ArrayList<>(),
                request.getEntitySchema().getWatermarkField(), 50, request.getWatermark().getLimit());
        return new FetchResponse(request.getWatermark(), iterator);
    }

    private int getRetryTime(RetriableException exception) {
        if (exception.getCause() != null && exception.getCause() instanceof HttpClientErrorException.TooManyRequests) {
            String retryStr = ((HttpClientErrorException.TooManyRequests) exception.getCause()).getResponseHeaders().getFirst("Retry-After");
            if (retryStr != null) {
                try {
                    int retryTime = Integer.parseInt(retryStr);
                    // add a jitter factor of upto 0.5 * retryTime
                    retryTime += (int) (retryTime * (Math.random() * (0.5)));
                    return retryTime;
                } catch (Exception e) {}
            }
        }
        return DEFAULT_RETRY_TIME;
    }

    private ZoneId getTimeZone(ConnectorInfo connectorInfo) {
        ResponseEntity<String> response = getClient().getResponse(getHost(connectorInfo) + GET_CURRENT_USER, connectorInfo.getAuthConfig());
        String timeZone= JsonPath.parse(response.getBody()).read("timeZone");
        return ZoneId.of(timeZone);

    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {

        String connectorId = request.getConnector().getId();
        try {
            List<EntityData> result = new ArrayList<>();
            List<String> ids = request.getIds();
            switch (request.getEntityName().toLowerCase()) {
                case JiraSeed.ISSUE:
                case "request":
                    // Track skipped IDs for monitoring
                    List<String> skippedIds = new ArrayList<>();

                    ids.forEach(id -> {
                        try {
                            String url = format(getHost(request.getConnector()) + GET_ISSUE, id);
                            ResponseEntity<String> response = getClient().getResponse(url, request.getConnector().getAuthConfig());
                            result.add(extractIssue(request, result, connectorId, JsonPath.parse(response.getBody()).json()));

                        } catch (NonRetriableInternalException e) {
                            // Handle 404 for missing/deleted Jira issues
                            if (ErrorCodes.BAD_ENDPOINT.name().equals(e.getErrorCode())) {
                                String errorMsg = "Issue not found";
                                try {
                                    if (e.getMessage() != null && e.getMessage().contains("errorMessages")) {
                                        Map errorMap = mapper.readValue(e.getMessage(), Map.class);
                                        List<String> messages = (List<String>) errorMap.get("errorMessages");
                                        errorMsg = messages != null && !messages.isEmpty() ? messages.get(0) : errorMsg;
                                    }
                                } catch (JsonProcessingException ex) {
                                    // Use default message if JSON parsing fails
                                }

                                log.warn("Issue {} does not exist or is not accessible: {}. Skipping to allow batch progress.",
                                         id, errorMsg);
                                skippedIds.add(id);
                                // Continue to next issue (don't add to result, don't throw)

                            } else {
                                // Re-throw other non-retriable exceptions (auth errors, etc.)
                                throw e;
                            }
                        }
                    });

                    // Log summary after forEach completes
                    if (!skippedIds.isEmpty()) {
                        log.warn("Completed getByIds for entity {}: requested={}, returned={}, skipped={} (IDs: {})",
                                 request.getEntityName(), ids.size(), result.size(), skippedIds.size(), skippedIds);
                    }
                    break;
                case JiraSeed.PRIORITY:
                case JiraSeed.RESOLUTION:
                case JiraSeed.COMMENT:
                case JiraSeed.ISSUETYPE:
                case JiraSeed.STATUS:
                case JiraSeed.STATUS_CATEGORY:
                    ids.forEach(id -> {
                        try {
                            int parsedId = Integer.parseInt(id);
                            Response response = getObject(getHost(request.getConnector()) + String.format(GET_OBJECT_BY_ID.get(request.getEntityName()), parsedId), request);
                            result.addAll(response.getRecords());
                        } catch (NumberFormatException e) {
                            log.error(ExceptionUtils.getStackTrace(e));
                        }
                    });
                    break;
                default:
                    throw new NotSupportedException("getByIds not supported by jira " + request.getEntityName().toLowerCase());
            }
            return result;
        } catch (RetriableException e) {
            if (ErrorCodes.TOO_MANY_REQUESTS.name().equals(e.getErrorCode())) {
                throw new QuotaExceededException(e.getErrorCode(), e.getMessage(), e.getStatusCode().toString(), connectorId, getRetryTime(e));
            }
            throw e;
        }
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        SyncResponse resp = new SyncResponse(true);
        List<Result> results = new ArrayList<>();
        SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient(getSingleJsonConfig(), mapper);
        switch (request.getEntityName()) {
        case "request":
        case JiraSeed.ISSUE:
            List<String> projectKeys = getprojectKeys(request.getConnector());
            if (projectKeys.size() == 1) {
                String projectId = getProjectId(request.getConnector(), projectKeys.get(0));
                request.getData().get(request.getConnector().getId()).stream().forEach(e -> {
                    try {
                        e.remove("projectKey");
                        String asString = getIssuePostBody(request, projectId, e);
                        ResponseEntity<String> response = restClient.postRaw(
                                getHost(request.getConnector()) + CREATE_ISSUE, asString,
                                request.getConnector().getAuthConfig());
                        Result result = new Result(true, JsonPath.parse(response.getBody()).read("id"), e.getSyncariEntityId());
                        results.add(result);
                    } catch (Exception e1) {
                        log.error(ExceptionUtils.getStackTrace(e1));
                        Result result = new Result(false, null, e.getSyncariEntityId());
                        result.addError(e1.getMessage());
                        results.add(result);
                    }
                });
            } else if(projectKeys.size() > 1) {
                List<EntityData> records = request.getData().get(request.getConnector().getId());
                Set<String> recordProjectKeys = records.stream()
                        .map(entity -> entity.getValueAsString("projectKey"))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                Map<String, String> projectKeyToIdMap = recordProjectKeys.stream()
                        .collect(Collectors.toMap(key -> key, key -> getProjectId(request.getConnector(), key)));
                request.getData().get(request.getConnector().getId()).stream().forEach(e -> {
                    try {
                        String projectId = projectKeyToIdMap.get(e.getValueAsString("projectKey"));
                        e.remove("projectKey");
                        if(StringUtils.isNotBlank(projectId)) {
                            String asString = getIssuePostBody(request, projectId, e);
                            ResponseEntity<String> response = restClient.postRaw(
                                    getHost(request.getConnector()) + CREATE_ISSUE, asString,
                                    request.getConnector().getAuthConfig());
                            Result result = new Result(true, JsonPath.parse(response.getBody()).read("id"), e.getSyncariEntityId());
                            results.add(result);
                        } else {
                            Result result = new Result(false, null, e.getSyncariEntityId());
                            result.addError("Project key not provided");
                            results.add(result);
                        }
                    } catch (Exception e1) {
                        log.error(ExceptionUtils.getStackTrace(e1));
                        Result result = new Result(false, null, e.getSyncariEntityId());
                        result.addError(e1.getMessage());
                        results.add(result);
                    }
                });
            }
            break;
        default:
            throw new RuntimeException("create not supported by jira service desk "+request.getEntityName());
        }
        resp.setResults(results);
        return resp;
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        SyncResponse resp = new SyncResponse(true);
        List<Result> results = new ArrayList<>();
        SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient(getSingleJsonConfig(), mapper);
        switch (request.getEntityName()) {
        case "request":
        case JiraSeed.ISSUE:
            request.getData().get(request.getConnector().getId()).stream().forEach(e -> {
                try {
                    String asString = getIssuePostBody(request, null, e);
                    restClient.put(
                            getHost(request.getConnector())
                                    + String.format(MODIFY_ISSUE, e.getId(), getNotifyUsers(request.getConnector())),
                            asString, request.getConnector().getAuthConfig());
                    results.add(new Result(true, e.getId(), e.getSyncariEntityId()));
                } catch (Exception e1) {
                    log.error(ExceptionUtils.getStackTrace(e1));
                    Result result = new Result(false, null, e.getSyncariEntityId());
                    result.addError(e1.getMessage());
                    results.add(result);
                }
            });
            break;
        default:
            throw new RuntimeException("update not supported by jira "+request.getEntityName());
        }
        resp.setResults(results);
        return resp;
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        SyncResponse resp = new SyncResponse(true);
        List<Result> results = new ArrayList<>();
        SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient(getSingleJsonConfig(), mapper);
        switch (request.getEntityName()) {
        case "request":
        case JiraSeed.ISSUE:
            request.getData().get(request.getConnector().getId()).stream().forEach(e -> {
                String path = format(getHost(request.getConnector()) + MODIFY_ISSUE, e.getId(), getNotifyUsers(request.getConnector()));
                restClient.delete(path, request.getConnector().getAuthConfig());
                results.add(new Result(true, e.getId(), e.getSyncariEntityId()));
            });
            break;

        default:
            throw new RuntimeException("delete not supported by jira "+request.getEntityName());
        }
        resp.setResults(results);
        return resp;
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        if (JiraSeed.ISSUE.equalsIgnoreCase(request.getEntity())) {
            return Optional.of(getIssueSchema(request.getConnector()));
        }
        if (JiraSeed.SEED_ENTITIES.contains(request.getEntity())) {
            return Optional.of(JiraSeed.getSeedEntitySchema(request.getEntity()));
        }
        return Optional.empty();
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        List<EntitySchema> allSchemas = new ArrayList<>();
        ConnectorInfo connector = request.getConnector();
        JiraSeed.SEED_ENTITIES.forEach(k -> {
            allSchemas.add(describe(new DescribeRequest(connector, k)).get());
        });
        return allSchemas;
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        throw new RuntimeException(this.getUIMetadata().getDisplayName() + " does not support create field");
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new RuntimeException(this.getUIMetadata().getDisplayName() + " does not support delete field");
    }

    SyncariEntityDataRestClient getClient(JsonParserConfig config) {
        return new SyncariEntityDataRestClient(config, mapper);
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new RuntimeException("createObject not supported in " + this.getUIMetadata().getDisplayName() + " yet");
    }

    @Override
    public Map<String, String> getEntityMappings() {
        return new HashMap<>();
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse response = new TestConnectionResponse();
        List<String> keys;
        try {
            keys = getprojectKeys(config);
        } catch (Exception e) {
            response.setErrors(List.of(e.getMessage()));
            response.setMessage(e.getMessage());
            return response;
        }
        for(String key: keys) {
            try {
            validateProject(config, key);
            } catch (ConnectorException e) {
                String message = format(i18n("invalid_project"), key);
                response.setErrors(List.of(message));
                response.setMessage(message);
            }
        }
        return response;
    }

    @Override
    public AuthConfig refreshToken(ConnectorInfo connector) {
        return helper.refreshToken(connector);
    }

    @Override
    public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
        return helper.getAccessToken(oAuthRequest);
    }

    @Override
    public String getOAuthUri(ConnectorInfo connector) {
        return helper.getOAuthUri();
    }

    private JsonParserConfig getSingleJsonConfig() {
        return new JsonParserConfig(null, null, null, StringUtils.capitalize("Id"), true, null);
    }

    private JiraEntityPage getIssuesWithToken(String url, SyncRequest request) {
        List<EntityData> result = new ArrayList<>();
        ResponseEntity<String> response = getClient().getResponse(url, request.getConnector().getAuthConfig());
        
        
        List rows = null;
        try {
            rows = JsonPath.parse(response.getBody()).read("issues");
        } catch (Exception e) {
            log.error("Failed to parse issues from response: {}", e.getMessage());
            rows = new ArrayList<>();
        }
        
        String connectorId = request.getConnector().getId();

        if (rows != null && rows.size() > 0) {
            for (int i = 0; i < rows.size(); i++) {
                Map row = (Map) rows.get(i);
                try {
                    EntityData issue = extractIssue(request, result, connectorId, row);
                    result.add(issue);
                } catch (Exception e) {
                    log.error("Failed to extract issue {}: {}", row != null ? row.get("key") : "unknown", e.getMessage(), e);
                }
            }
        }
        
        JiraEntityPage page = new JiraEntityPage();
        page.setData(result);
        page.setOffset(result.size()); // Set offset to number of records fetched
        
        try {
            String nextPageToken = JsonPath.parse(response.getBody()).read("nextPageToken");
            page.setNextPageToken(nextPageToken);
        } catch (Exception e) {
            page.setNextPageToken(null);
        }
        
        try {
            Boolean isLast = JsonPath.parse(response.getBody()).read("isLast");
            page.setLast(isLast != null ? isLast : true);
        } catch (Exception e) {
            page.setLast(true);
        }
        
        return page;
    }
    
    private Response getIssues(String url, SyncRequest request) {
        List<EntityData> result = new ArrayList<>();
        ResponseEntity<String> response = getClient().getResponse(url, request.getConnector().getAuthConfig());
        List rows = JsonPath.parse(response.getBody()).read("issues");
        int start = JsonPath.parse(response.getBody()).read("startAt");
        int size = JsonPath.parse(response.getBody()).read("maxResults");
        String connectorId = request.getConnector().getId();

        if (rows != null && rows.size() > 0) {
            for (int i = 0; i < rows.size(); i++) {
                Map row = (Map) rows.get(i);
                result.add(extractIssue(request, result, connectorId, row));
            }
        }
        return new Response(String.valueOf(start + size), result);
    }
    
    private Response getObjects(String url, SyncRequest request) {
        List<EntityData> result = new ArrayList<>();
        ResponseEntity<String> response = getClient().getResponse(url, request.getConnector().getAuthConfig());
        List rows;
        try {
            rows = mapper.readValue(response.getBody(), mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        String connectorId = request.getConnector().getId();
        
        if (rows != null && rows.size() > 0) {
            for (int i = 0; i < rows.size(); i++) {
                Map row = (Map) rows.get(i);
                result.add(extractData(request, connectorId, row));
            }
        }
        return new Response(null, result);
    }
    
    private Response getComponents(String url, SyncRequest request) {
    	List<EntityData> result = new ArrayList<>();
        ResponseEntity<String> response = getClient().getResponse(url, request.getConnector().getAuthConfig());
        Map respBody;
        List rows;
        try {
        	respBody = mapper.readValue(response.getBody(), Map.class);
        	rows = (List) respBody.get("values");
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        String connectorId = request.getConnector().getId();
        
        if (rows != null && rows.size() > 0) {
            for (int i = 0; i < rows.size(); i++) {
                Map row = (Map) rows.get(i);
                row = (Map) row.get("componentBean");
                result.add(extractData(request, connectorId, row));
            }
        }
        return new Response(null, result);
    }
    
	private Response getObject(String url, SyncRequest request) {
		try {
			ResponseEntity<String> response = getClient().getResponse(url, request.getConnector().getAuthConfig());
			String connectorId = request.getConnector().getId();
			Map row = mapper.readValue(response.getBody(), Map.class);
			List<EntityData> list = List.of(extractData(request, connectorId, row));
			return new Response(null, list);
		} catch (NonRetriableException e) {
			try {
				if (e.getMessage() != null && e.getMessage().contains("errorMessages")) {
					Map row = mapper.readValue(e.getMessage(), Map.class);
					List<String> messages = (List<String>) row.get("errorMessages");
					log.info("Jira error messages received {} ", messages);
					throw new NonRetriableException(e.getErrorCode(), messages.get(0), e.getStatusCode().toString(), e);
				}
				throw e;
			} catch (JsonProcessingException ex) {
				log.error("Could not parse the response ", ex);
				throw new RuntimeException(ex.getMessage());
			}
		} catch (Exception e) {
			log.error("Error in getting Jira response ", e);
			throw new RuntimeException(e.getMessage());
		}

	}
    
    private void validateProject(ConnectorInfo info, String key) {
        String id = getProjectId(info, key);
        if(id == null) throw new NonRetriableException("INVALID_PROJECT",String.format("No project could be found with key '%s'",key),"INVALID_PROJECT");
    }

    private String getProjectId(ConnectorInfo info, String key) {
        String url = format(getHost(info) + GET_PROJECT, key);
        ResponseEntity<String> response = getClient().getResponse(url, info.getAuthConfig());
        return JsonPath.parse(response.getBody()).read("id");
    }

    private EntityData extractUser(SyncRequest request, List<EntityData> result, String connectorId, Map row) {
        EntityData data = new EntityData(request.getEntityName());
        data.setId(row.get("accountId").toString());
        data.setConnectorId(connectorId);
        data.addValue("name", row.get("name"));
        data.addValue("displayName", row.get("displayName"));
        data.addValue("active", (Boolean)row.get("active"));
        data.addValue("accountType", row.get("accountType"));
        data.addValue("emailAddress", row.get("emailAddress"));
        return data;
    }

    private EntityData extractData(SyncRequest request, String connectorId, Map row) {
        EntityData data = new EntityData(request.getEntityName());
        data.setId(row.get("id").toString());
        if (row.containsKey("updated") && row.get("updated") != null) {
            data.setLastModified(dateUtil.parse(row.get("updated").toString(), UPDATED_FORMAT).toInstant().toEpochMilli());
        } else {
        	long lastModified = Instant.now().toEpochMilli();
        	if(request.getWatermark() != null && request.getWatermark().getEnd() > 0) {
        		lastModified = request.getWatermark().getEnd();
        	}
        	data.setLastModified(lastModified);
        }
        data.setConnectorId(connectorId);
        row.forEach((k, v) -> {
            if (refDatatype.contains(k.toString().toLowerCase()) && v instanceof Map && ((Map) v).containsKey("id")) {
                data.addValue(k.toString(), ((Map) v).get("id"));
            } else {
                data.addValue(k.toString(), v);
            }
        });
        return data;
    }
    private String extractText(Map content){
        String text = extractTextFromADF(content);
        return StringUtils.isBlank(text)? text: text.strip();
    }
    private String extractTextFromADF(Map content){
        if(content==null){
            return null;
        }
        if("text".equals(content.get("type"))){
            return content.getOrDefault("text","").toString();
        }else if("mention".equals(content.get("type"))){
            Map attributes = (Map) content.getOrDefault("attrs",Map.of());
            return attributes.getOrDefault("text","").toString();
        }else if("emoji".equals(content.get("type"))){
            Map attributes = (Map) content.getOrDefault("attrs",Map.of());
            return attributes.getOrDefault("text","").toString();
        }

        List<Map> contents = (List<Map>) content.getOrDefault("content",List.of());
        String extractedText = contents.stream().map(c -> extractTextFromADF(c)).reduce((t1, t2) -> t1 + " " + t2).orElse("").trim();
        return StringUtils.isBlank(extractedText)?extractedText : (extractedText+"\n");
    }

    private EntityData extractIssue(SyncRequest request, List<EntityData> result, String connectorId, Map row) {
        EntityData data = new EntityData(request.getEntityName());
        data.setId(row.get("id").toString());
        data.addValue("id", row.get("id").toString());
        data.setConnectorId(connectorId);
        String key = row.get("key").toString();
        data.addValue("issuekey", key);
        if(StringUtils.isNotBlank(key)) {
            String[] split = key.split("-");
            if(split.length > 0) {
                data.addValue("projectKey", split[0]);
            }
        }
        Map fields = (Map) row.get("fields");
        
        
        fields.forEach((k, v) -> {
            if (refDatatype.contains(k.toString().toLowerCase()) && v instanceof Map) {
                if ("reporter".equalsIgnoreCase(k.toString()) || "assignee".equalsIgnoreCase(k.toString())) {
                    data.addValue(k.toString(), ((Map) v).get("accountId"));
                } else {
                    data.addValue(k.toString(), ((Map) v).get("id"));
                }
            } else if (v instanceof List) {
                List values = (List) v;
                if (!values.isEmpty()) {
                    if (values.get(0) instanceof Map && (((Map)values.get(0)).containsKey("id") || ((Map)values.get(0)).containsKey("accountId"))) {
                        String idKey = ((Map)values.get(0)).containsKey("id") ? "id" : "accountId";
                        List ids = (List) values.stream().map(val -> (((Map) val).get(idKey)))
                                .collect(Collectors.toList());
                        data.addValue(k.toString(), ids);
                    } else {
                        data.addValue(k.toString(), v);
                    }
                } else {
                    data.addValue(k.toString(), v);
                }
            } else if (request.getEntitySchema().getField(k.toString()).map(f -> f.getDataType().equals("textarea"))
                    .orElse(false)) {
                data.addValue(k.toString(), extractText((Map) v));
            } else if (v instanceof Map && ((Map) v).containsKey("id")) {
            	data.addValue(k.toString(), ((Map) v).get("id"));
            } else {
                data.addValue(k.toString(), v);
            }
            if ("updated".equals(k) && v != null) {
                long parsedTimestamp = dateUtil.parse(v.toString(), UPDATED_FORMAT).toInstant().toEpochMilli();
                data.setLastModified(parsedTimestamp);
            }
            if ("created".equals(k) && v != null) {
                data.setCreatedAt(dateUtil.parse(v.toString(), UPDATED_FORMAT).toInstant().toEpochMilli());
            }
        });
        return data;
    }

    private EntitySchema getIssueSchema(ConnectorInfo info) {
        EntitySchema schema = JiraSeed.getSeedEntitySchema(JiraSeed.ISSUE);
        String path = getHost(info) + GET_ISSUE_FIELDS;
        ResponseEntity<String> response = getClient().getResponse(path, info.getAuthConfig());
        try {
            List rows = mapper.readValue(response.getBody(), List.class);
            extractRows(schema, rows);
        } catch (Exception e1) {
            throw new RuntimeException(e1.getMessage());
        }

        return schema;
    }

    @SneakyThrows
    protected void extractRows(EntitySchema schema, List rows) {
        log.debug("Got {} fields from jira", rows.size());
        if (rows != null && rows.size() > 0) {
            for (int i = 0; i < rows.size(); i++) {
                Map row = (Map) rows.get(i);
                String apiName = row.get("key").toString();
                String displayName = row.get("name").toString();
                Map schemaMap = (Map) row.get("schema");
                String datatype = schemaMap == null ? "string" : schemaMap.get("type").toString();
                String subType = getSubType(schemaMap);
                if (subType.contains("textarea") || subType.equalsIgnoreCase("description")) {
                    datatype = "textarea";
                }
                if (!allowedTypes.contains(datatype.toLowerCase())) continue;
                try {
                    AttributeSchema field = new AttributeSchema(apiName, datatype);
                    field.setDisplayName(displayName);
                    //We want to ignore Syncari defined if it's already present on Jira
                    if ("created_at".equalsIgnoreCase(apiName) && schema.hasField(apiName)) {
                        schema.removeField(apiName);
                    }
                    if (requiredIssueFields.contains(apiName)) {
                        field.setNillable(false);
                    }
                    if (refDatatype.contains(datatype)) {
                        field.setDataType("reference");
                        field.setReferenceTargetField("id");
                        field.setReferenceTo(datatype);
                    } else if (apiName.equals("parent")) {
                        field.setDataType("reference");
                        field.setReferenceTargetField("id");
                        field.setReferenceTo(JiraSeed.ISSUE);
                    } else if ("option".equalsIgnoreCase(datatype)) {
                        field.setDataType("picklist");
                    } else if ("array".equalsIgnoreCase(datatype)) {
                        String items = schemaMap.getOrDefault("items", "").toString();
                        boolean noDatatype = false;
                        if (!StringUtils.isBlank(items) && JiraSeed.SEED_ENTITIES.contains(items)) {
                            field.setDataType("reference");
                            field.setReferenceTargetField("id");
                            field.setReferenceTo(schemaMap.get("items").toString());
                        } else if ("option".equalsIgnoreCase(items)) {
                            field.setDataType("picklist");
                        } else if ("string".equalsIgnoreCase(items)) {
                            field.setDataType("string");
                        } else {
                            noDatatype = true;
                            log.debug("Jira field got datatype {} not supported", row);
                        }
                        if (!noDatatype || multivalued.contains(apiName)) {
                            field.setMultiValueField(true);
                        }
                    }
                    field.setCustom((Boolean) row.get("custom"));
                    if (!schema.hasField(field.getApiName())) {
                        schema.addField(field);
                    }
                } catch (Exception ignored) {
                    log.error("Response {} Error in jira {}", mapper.writeValueAsString(rows) , ExceptionUtils.getStackTrace(ignored));
                }
            }
        }
    }

    private String getSubType(Map schemaMap) {
        return (schemaMap == null || (!schemaMap.containsKey(SYSTEM)
                && !schemaMap.containsKey("custom"))) ? "string"
                        : (schemaMap.containsKey(SYSTEM) ? schemaMap.get(SYSTEM).toString()
                                : schemaMap.get("custom").toString());
    }

    private String getHost(ConnectorInfo info) {
        if (info.getEndpoint().endsWith("/"))
            return info.getEndpoint();
        return info.getEndpoint() + "/";
    }
    
    private boolean getNotifyUsers(ConnectorInfo config) {
        try {
            var notifyUsers = config.getMetaConfig().getOrDefault("notifyUsers", true);
            return Boolean.parseBoolean(notifyUsers.toString());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getAuthHost(AuthConfig config) {
        return helper.getAuthHost(config);
    }

    private List<String> getprojectKeys(ConnectorInfo config) {
        var id = config.getMetaConfig().get(PROJECT_KEY);
        if (id == null || StringUtils.isBlank(id.toString())) {
            throw new RuntimeException(i18n("project_key_required"));
        }
        return Arrays.stream(id.toString().split(","))
                     .map(String::trim).collect(Collectors.toList());
    }
    
    private SyncariEntityDataRestClient getClient() {
        return new SyncariEntityDataRestClient(getSingleJsonConfig(), mapper);
    }
    
    public String getIssuePostBody(SyncRequest request, String projectId, EntityData e)
            throws JsonProcessingException {
    	Map<String, Object> values = new HashMap<>();
    	values.putAll(e.getValues());
		values.forEach((k, v) -> {
			Optional<AttributeSchema> field = request.getEntitySchema() == null ? Optional.empty()
					: request.getEntitySchema().getField(k);
			if ("components".equalsIgnoreCase(k)) {
				List comps = new ArrayList();
				if(v instanceof List) {
					((List)v).stream().forEach(val -> comps.add(Map.of("id", val)));
					values.put(k, comps);
				} else {
					log.warn("Expecting list for components, but found {}", v);
				}
			}
			if (refDatatype.contains(k) || (field.isPresent() && "picklist".equalsIgnoreCase(field.get().getDataType()))) {
                if(v instanceof List) {
                    List list = (List) v;
                    List<Map<String, String>> idMap = new ArrayList<>();
                    list.forEach(elem -> {
                        idMap.add(Map.of("id", elem == null ? "" : elem.toString()));
                    });
                    values.put(k, idMap);
                } else {
                    values.put(k, Map.of("id", v == null ? "" : v.toString()));
                }
			}
			if (field.isPresent() && "textarea".equalsIgnoreCase(field.get().getDataType()) && v != null) {
				values.put(k, new Doc(v.toString()));
			}
            if(field.isPresent() && field.get().isReference() && v instanceof List) {
                List ids = new ArrayList();
                ((List)v).stream().forEach(val -> ids.add(Map.of("id", val)));
                values.put(k, ids);
            }
		});
        if(projectId != null) {
        	values.put("project", Map.of("id", projectId));
        }
        return "{ \"fields\": " + mapper.writeValueAsString(values) + "}";
    }
    
}

@Data
class Doc {
    String type = "doc";
    int version = 1;
    List<Content> content = new ArrayList<>();
    
    public Doc(String text) {
        content.add(new Content(text));
    }
}

@Data
class Content {
    String type = "paragraph";
    List content = new ArrayList<>();

    public Content(String text) {
        content.add(Map.of("text", text, "type", "text"));
    }
}

@Data
class ADFContent {
    String type;
    List<ADFContent> content = new ArrayList<>();

    public String extractText(){
        return content.stream().map(c->c.extractText()).reduce((t1,t2)->t1+" "+t2).orElse("");
    }
}

@Data
class ADFDoc extends ADFContent{
    int version = 1;
}
@Data
class TextContent extends ADFContent{
    String type="text";
    String text;
    public String extractText(){
        return text;
    }
}

