package com.syncari.connector.jira;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.syncari.connector.data.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jooq.lambda.function.Function3;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.jayway.jsonpath.JsonPath;
import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.DefaultAuthTokenHandler;
import com.syncari.connector.EntityData;
import com.syncari.connector.Response;
import com.syncari.connector.ValueHolder;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.iterator.DefaultDataIterator;
import com.syncari.connector.exception.NotSupportedException;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.OauthAuthenticationService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.connector.service.helper.JiraHelper;
import com.syncari.utils.DateUtil;
import com.syncari.utils.Pair;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component(Constants.JIRA_SERVICE_DESK)
public class JiraServiceDeskService
        implements OauthAuthenticationService, CommonDataService, MetadataService, SynapseInfoService {
    private static final String EMAIL_ADDRESS = "emailAddress";
    private static final List<String> CUSTOMER_FIELDS = List.of(EMAIL_ADDRESS, "displayName");
    @Autowired
    ObjectMapper mapper;
    @Autowired
    DateUtil dateUtil;
    @Autowired
    DefaultAuthTokenHandler tokenHandler;
    @Autowired
    JiraHelper helper;
    @Autowired
    JiraService jiraService;
    private static String GET_ORG_URL = "rest/servicedeskapi/servicedesk/%s/organization?%s";
    private static String GET_SERVICE_DESK_BY_ID = "rest/servicedeskapi/servicedesk/%s";
    private static String GET_ORG_BY_ID_URL = "rest/servicedeskapi/organization/%s";
    private static String CREATE_ORG_URL = "rest/servicedeskapi/organization";
    private static String ADD_REMOVE_CUSTOMER_ORG_URL = "rest/servicedeskapi/organization/%s/user";
    private static String DELETE_ORG_URL = "rest/servicedeskapi/organization/%s";
    private static String GET_CUSTOMER_URL = "rest/servicedeskapi/servicedesk/%s/customer?%s";
    private static String CREATE_CUSTOMER_URL = "rest/servicedeskapi/customer";
    private static String GET_REQUEST_TYPE_URL = "rest/servicedeskapi/requesttype";
    private static String GET_REQUEST_TYPE_FIELDS_URL = "rest/servicedeskapi/servicedesk/%s/requesttype/%s/field";
//    private static String GET_REQUEST_URL = "rest/servicedeskapi/request?requestOwnership=ALL_ORGANIZATIONS&searchTerm=updated>%s order by updated";
    private static final List<String> SEED_ENTITIES = List.of(Constants.ORGANIZATION.toLowerCase(), "customer",
            "request", JiraSeed.ISSUETYPE, JiraSeed.PRIORITY, JiraSeed.RESOLUTION, JiraSeed.STATUS, JiraSeed.STATUS_CATEGORY);
    private static final Map<String, String> urlMap = Map.of(
            Constants.ORGANIZATION.toLowerCase(), GET_ORG_URL, "customer", GET_CUSTOMER_URL);
    Cache<Object, Object> projectIdCache = CacheBuilder.newBuilder().maximumSize(100000).build();

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return helper.getSupportedAuthTypes();
    }

    @Override
    public List<AuthField> getConfigureFields() {
        AuthField serviceDeskId = new AuthField().setName("serviceDeskId").setLabel(i18n("service_desk_id"))
                .setDataType("text").setHelpSummary(i18n("service_desk_id_summary"));
        AuthField cloudId = new AuthField().setName("cloudId").setLabel(i18n("cloud_id"))
                .setDataType("text").setHelpSummary(i18n("cloud_id_summary")).setRequired(false);
        AuthField notifyUsers = new AuthField().setName("notifyUsers").setLabel(i18n("notifyUsers"))
                .setDataType("text").setHelpSummary(i18n("notifyUsers_summary")).setRequired(false);
        return List.of(ConnectorHelper.getEndpointField(), serviceDeskId, cloudId, notifyUsers, ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public String getCategory() {
        return "Customer Success";
    }

    @Override
    public String getName() {
        return Constants.JIRA_SERVICE_DESK;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/jira-service-desk.svg")
                .setDisplayName("Jira Service Desk")
                .setBackgroundColor("#F0F6FF")
                .setHelpUrl(helpArticlesBaseUrl + "/360055166031-Jira-Service-Management-Setup");
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "";
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        if (!urlMap.containsKey(request.getEntityName())) {
            setProjectId(request.getConnector());
            return jiraService.getByWatermark(request);
        }
        ValueHolder<String> lastOffset = new ValueHolder<>();
        Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator = (wm, pageSize, offset) -> {
            if (offset != 0 && lastOffset.get() == null)
                return Pair.of(0L, new ArrayList<EntityData>().stream());
            String path;
            String offsetPart = lastOffset.get() != null ? "start=" + lastOffset.get() +"&" : "";
            switch (request.getEntityName()) {
            // NOTE: These 2 dont honor the updated filter, so they give back all records everytime
            case "organization":
            case "customer":
                path = format(getHost(request.getConnector()) + urlMap.get(request.getEntityName()),
                        getServiceDeskId(request.getConnector()), offsetPart);
                break;

            default:
                throw new RuntimeException("Unsupported get for " + request.getEntityName());
            }
            Response response = get(path, request);
            lastOffset.set(response.getOffset());
            return Pair.of(Long.valueOf(response.getRecords().size()), response.getRecords().stream());
        };

        DefaultDataIterator iterator = new DefaultDataIterator(request.getWatermark(),
                request.getWatermark().getOffset(), generator, new ArrayList<>(),
                request.getEntitySchema().getWatermarkField(), 50, request.getWatermark().getLimit());
        return new FetchResponse(request.getWatermark(), iterator);
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        List<EntityData> results = new ArrayList<>();
        SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient(getSingleJsonConfig(), mapper);
        switch (request.getEntityName()) {
        case "organization":
            request.getData().get(request.getConnector().getId()).stream().forEach(e -> {
                String path = format(getHost(request.getConnector()) + GET_ORG_BY_ID_URL, e.getId());
                try {
                    ResponseEntity<String> response = restClient.getResponse(path, request.getConnector().getAuthConfig());
                    EntityData entityData = extractOrg(request, request.getConnector().getId(),
                            mapper.readValue(response.getBody(), Map.class));
                    addOrgDetails(restClient, request, entityData);
                    results.add(entityData);
                } catch (Exception e1) {
                    log.error(ExceptionUtils.getStackTrace(e1));
                }
            });
            break;
        case "request":
        case JiraSeed.PRIORITY:
        case JiraSeed.RESOLUTION:
        case JiraSeed.COMMENT:
        case JiraSeed.ISSUETYPE:
        case JiraSeed.STATUS:
        case JiraSeed.STATUS_CATEGORY:
            setProjectId(request.getConnector());
            return jiraService.getByIds(request);
        default:
            throw new NotSupportedException("getByIds not supported by jira service desk "+request.getEntityName());
        }
        return results;
    }

    private void addOrgDetails(SyncariEntityDataRestClient restClient, SyncRequest request, EntityData data) {
        if(StringUtils.isNotBlank((String) request.getConnector().getMetaConfig().get("cloudId"))) {
            String url = "https://api.atlassian.com/jsm/csm/cloudid/" + request.getConnector().getMetaConfig().get("cloudId") + "/api/v1/organization/" + data.getId();
            ResponseEntity<String> response = restClient.getResponse(url, request.getConnector().getAuthConfig());
            JSONObject jsonObject = new JSONObject(response.getBody());
            if (jsonObject.has("details") && jsonObject.getJSONArray("details") != null) {
                data.addValue("details", jsonObject.getJSONArray("details").toString());
            }
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
        case "organization":
            request.getData().get(request.getConnector().getId()).stream().forEach(e -> {
                try {
                    validateOrgName(e);
                    String asString = mapper.writeValueAsString(Map.of("name", e.getValueAsString("name")));
                    ResponseEntity<String> response = restClient.postRaw(
                            getHost(request.getConnector()) + CREATE_ORG_URL, asString,
                            request.getConnector().getAuthConfig());
                    Result result = new Result(true, JsonPath.parse(response.getBody()).read("id"), e.getSyncariEntityId());
                    result.setSyncariId(e.getSyncariEntityId());
                    results.add(result);
                } catch (Exception e1) {
                    log.error(ExceptionUtils.getStackTrace(e1));
                    Result result = new Result(false, null, e.getSyncariEntityId());
                    result.addError(e1.getMessage());
                    results.add(result);
                }
            });
            break;
        case "request":
            setProjectId(request.getConnector());
            resp = jiraService.create(request);
            return resp;    
        case "customer":
            Map<String, List<String>> customerIds = new HashMap<>();
            request.getData().get(request.getConnector().getId()).stream().forEach(e -> {
                try {
                    validateCustomerName(e);
                    String asString = getCustomerPostBody(e);
                    ResponseEntity<String> response = restClient.postRaw(
                            getHost(request.getConnector()) + CREATE_CUSTOMER_URL, asString,
                            request.getConnector().getAuthConfig());
                    Result result = new Result(true, JsonPath.parse(response.getBody()).read("accountId"), e.getSyncariEntityId());
                    results.add(result);
                    Object orgs = e.getValue("organizations");
                    if(orgs != null && orgs instanceof List) {
                        ((List) orgs).forEach(o -> {
                            if(o == null || StringUtils.isBlank(o.toString())) return;
                            customerIds.putIfAbsent(o.toString(), new ArrayList<>());
                            customerIds.get(o.toString()).add(result.getId());
                        });
                    }
                } catch (Exception e1) {
                    log.error(ExceptionUtils.getStackTrace(e1));
                    Result result = new Result(false, null, e.getSyncariEntityId());
                    result.addError(e1.getMessage());
                    results.add(result);
                }
            });
            assignCustomersToOrg(customerIds, restClient, request);
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
        switch (request.getEntityName()) {
        case "request":
            setProjectId(request.getConnector());
            resp = jiraService.update(request);
            return resp;
        case "customer":
            setProjectId(request.getConnector());
            resp = jiraService.update(request);
            return resp;

        default:
            request.getData().get(request.getConnector().getId()).stream().forEach(e -> {
                Result result = new Result(false, e.getId(), e.getSyncariEntityId());
                result.addError("Update not supported by Jira service desk");
                results.add(result);
            });
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
        case "organization":
            request.getData().get(request.getConnector().getId()).stream().forEach(e -> {
                String path = format(getHost(request.getConnector()) + DELETE_ORG_URL, e.getId());
                restClient.delete(path, request.getConnector().getAuthConfig());
                results.add(new Result(true, e.getId(), e.getSyncariEntityId()));
            });
            break;
        case "request":
            setProjectId(request.getConnector());
            resp = jiraService.delete(request);
            return resp;

        default:
            throw new RuntimeException("delete not supported by jira service desk "+request.getEntityName());
        }
        resp.setResults(results);
        return resp;
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        if ("request".equalsIgnoreCase(request.getEntity())) {
            return Optional.of(getRequestSchema(request.getConnector()));
        }
        if (SEED_ENTITIES.contains(request.getEntity())) {
            if(JiraSeed.SEED_ENTITIES.contains(request.getEntity())) {
                return Optional.of(JiraSeed.getSeedEntitySchema(request.getEntity()));
            }
            return Optional.of(JiraServiceDeskSeed.getSeedEntitySchema(request.getEntity()));
        }
        return Optional.empty();
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        List<EntitySchema> allSchemas = new ArrayList<>();
        ConnectorInfo connector = request.getConnector();
        SEED_ENTITIES.forEach(k -> {
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
        String projectId = getServiceDeskProjectId(config);
        projectIdCache.put(getProjectCacheKey(config), projectId);
        try {
            DescribeAllRequest request = new DescribeAllRequest(config, entityNames);
            describeAll(request);
        } catch (Exception e) {
            response.setErrors(List.of(e.getMessage()));
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

    private Response get(String url, SyncRequest request) {
        List<EntityData> result = new ArrayList<>();
        request.getConnector().getAuthConfig().addHeader("X-ExperimentalApi", "opt-in");
        SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient(getSingleJsonConfig(), mapper);
        ResponseEntity<String> response = restClient.getResponse(url, request.getConnector().getAuthConfig());
        List rows = JsonPath.parse(response.getBody()).read("values");
        int start = JsonPath.parse(response.getBody()).read("start");
        int size = JsonPath.parse(response.getBody()).read("size");
        String connectorId = request.getConnector().getId();

        if (rows != null && rows.size() > 0) {
            for (int i = 0; i < rows.size(); i++) {
                Map row = (Map) rows.get(i);
                EntityData data = null;
                switch (request.getEntityName()) {
                case "organization":
                    data = extractOrg(request, connectorId, row);
                    addOrgDetails(restClient, request, data);
                    break;
                case "customer":
                    data = extractCustomer(request, result, connectorId, row);
                    // get org for the customer
                    List<String> orgIds = new ArrayList<>();
                    String path = format(getHost(request.getConnector()) + urlMap.get(Constants.ORGANIZATION.toLowerCase()),
                            getServiceDeskId(request.getConnector()), "accountId="+data.getId());
                    ResponseEntity<String> orgs = restClient.getResponse(path, request.getConnector().getAuthConfig());
                    List orgList = JsonPath.parse(orgs.getBody()).read("values");
                    for (int j = 0; j < orgList.size(); j++) {
                        Map r = (Map) orgList.get(j);
                        String orgId = r.get("id").toString();
                        orgIds.add(orgId);
                    }
                    data.addValue("organizations", orgIds);
                    break;

                default:
                    throw new RuntimeException("Unsupported get for " + request.getEntityName());
                }
                result.add(data);
            }
        }
        return new Response(String.valueOf(start + size), result);
    }

    private EntityData extractOrg(SyncRequest request, String connectorId, Map row) {
        EntityData data = new EntityData(request.getEntityName());
        data.setId(row.get("id").toString());
        data.setConnectorId(connectorId);
        data.addValue("name", row.get("name").toString());
        return data;
    }

    private EntityData extractCustomer(SyncRequest request, List<EntityData> result, String connectorId, Map row) {
        EntityData data = new EntityData(request.getEntityName());
        data.setId(row.get("accountId").toString());
        data.setConnectorId(connectorId);
        data.addValue(EMAIL_ADDRESS, row.get(EMAIL_ADDRESS).toString());
        data.addValue("displayName", row.get("displayName").toString());
        data.addValue("active", (Boolean) row.get("active"));
        return data;
    }

    private EntitySchema getRequestSchema(ConnectorInfo info) {
        EntitySchema schema = JiraServiceDeskSeed.getSeedEntitySchema("request");
        SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient(getSingleJsonConfig(), mapper);
        List rows = getRequestTypes(info, restClient);

        if (rows != null && rows.size() > 0) {
            for (int i = 0; i < rows.size(); i++) {
                Map row = (Map) rows.get(i);
                String serviceDeskId = row.get("serviceDeskId").toString();
                String requestTypeId = row.get("id").toString();
                String url = format(getHost(info) + GET_REQUEST_TYPE_FIELDS_URL, serviceDeskId, requestTypeId);
                try {
                    ResponseEntity<String> fieldsResp = restClient.getResponse(url, info.getAuthConfig());
                    List fieldRows = JsonPath.parse(fieldsResp.getBody()).read("requestTypeFields");
                    for (int j = 0; j < fieldRows.size(); j++) {
                        Map f = (Map) fieldRows.get(j);
                        Map<String, Object> jiraSchema = ((Map) f.get("jiraSchema"));
                        String dataType = jiraSchema.get("type").toString();
                        if ("option".equalsIgnoreCase(dataType)) {
                            dataType = "picklist";
                        }
                        String subType = jiraSchema.containsKey("system") ? jiraSchema.get("system").toString() : jiraSchema.containsKey("custom") ? jiraSchema.get("custom").toString() : "";
                        if(subType.contains("textarea") || subType.equalsIgnoreCase("description")) {
                            dataType = "textarea";
                        }
                        AttributeSchema field = new AttributeSchema(f.get("fieldId").toString(), dataType);
                        field.setDisplayName(f.get("name").toString());
                        field.setNillable(!((Boolean) f.get("required")));
                        if (!schema.hasField(field.getApiName())) {
                            schema.addField(field);
                        }
                    }
                } catch (Exception e) {
                    if(!e.getMessage().equalsIgnoreCase("404 NOT_FOUND")) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        // add platform custom fields
        DescribeRequest request = new DescribeRequest(info, "issue");
        setProjectId(request.getConnector());
        Optional<EntitySchema> issue = jiraService.describe(request);
        issue.ifPresent(i -> {
            i.getAttributes().forEach(a -> {
                if (!schema.hasField(a.getApiName())) {
                    schema.addField(a);
                }
            });
        });
        return schema;
    }

    private List getRequestTypes(ConnectorInfo info, SyncariEntityDataRestClient restClient) {
        String path = getHost(info) + GET_REQUEST_TYPE_URL;
        info.getAuthConfig().addHeader("X-ExperimentalApi", "opt-in");
        ResponseEntity<String> response = restClient.getResponse(path, info.getAuthConfig());
        return JsonPath.parse(response.getBody()).read("values");
    }

    private String getHost(ConnectorInfo info) {
        if (info.getEndpoint().endsWith("/"))
            return info.getEndpoint();
        return info.getEndpoint() + "/";
    }

    @Override
    public String getAuthHost(AuthConfig config) {
        return helper.getAuthHost(config);
    }

    private String getServiceDeskId(ConnectorInfo config) {
        var id = config.getMetaConfig().get("serviceDeskId");
        if (id == null || StringUtils.isBlank(id.toString())) {
            throw new RuntimeException(i18n("service_desk_required"));
        }
        return id.toString();
    }
    
    private String getServiceDeskProjectId(ConnectorInfo config) {
        var id = getServiceDeskId(config);
        SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient(getSingleJsonConfig(), mapper);
        String path = getHost(config) + String.format(GET_SERVICE_DESK_BY_ID, id);
        ResponseEntity<String> response = restClient.getResponse(path, config.getAuthConfig());
        return JsonPath.parse(response.getBody()).read(JiraService.PROJECT_KEY);
    }
    
    private void validateOrgName(EntityData e) {
        if(e.getValue("name") == null) throw new RuntimeException("The organization's name should not be empty");
    }
    
    private void validateCustomerName(EntityData e) {
        CUSTOMER_FIELDS.stream().forEach(f -> {
            if(e.getValue(f) == null) throw new RuntimeException("The Customers "+f+" should not be empty");
        });
    }
    private String getCustomerPostBody(EntityData e) throws JsonProcessingException {
        Map<String, String> map = new HashMap<>();
        CUSTOMER_FIELDS.stream().forEach(f -> {
            // https://developer.atlassian.com/cloud/jira/service-desk/rest/api-group-customer/#api-rest-servicedeskapi-customer-post
            // unfortunately the emailAddress field is inconsistent for create and get apis
            if(EMAIL_ADDRESS.equalsIgnoreCase(f)) {
                map.put("email", e.getValueAsString(f));
            } else {
                map.put(f, e.getValueAsString(f));
            }
        });
        return mapper.writeValueAsString(map);
    }
    
    private String getProjectCacheKey(ConnectorInfo config) {
        return config.getInstanceId()+getServiceDeskId(config);
    }
    
    private void setProjectId(ConnectorInfo info) {
        String id = (String) projectIdCache.getIfPresent(getProjectCacheKey(info));
        if(id == null) {
            id = getServiceDeskProjectId(info);
        }
        info.getMetaConfig().put(JiraService.PROJECT_KEY, id);
    }
    
    private void assignCustomersToOrg(Map<String, List<String>> orgToUser, SyncariEntityDataRestClient restClient,
            SyncRequest request) {
        // remove existing and assign customer to org
        orgToUser.forEach((orgId, users) -> {
            try {
                String asString = mapper.writeValueAsString(Map.of("accountIds", users));
                String url = String.format(getHost(request.getConnector()) + ADD_REMOVE_CUSTOMER_ORG_URL, orgId);
                ResponseEntity<String> response = restClient.postRaw(url, asString,
                        request.getConnector().getAuthConfig());
                if (response.getStatusCode() != HttpStatus.NO_CONTENT) {
                    log.error("error assigning customer to org {}", response);
                } else {
                    log.info("Successfully assigned org {} for customers {}", orgId, users);
                }
            } catch (JsonProcessingException e) {
                log.error(ExceptionUtils.getStackTrace(e));
            }
        });
    }
}
