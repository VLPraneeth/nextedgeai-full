package com.syncari.connector.zoho;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.LocalStorageService;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.OauthAuthenticationService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jooq.lambda.function.Function2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.connector.ConnectorHelper.withRateLimitHandling;

@Slf4j
@Component(Constants.ZOHO)
public class ZohoService implements OauthAuthenticationService, CommonDataService, 
    MetadataService, SynapseInfoService {
    @Autowired
    ObjectMapper mapper;
    @Autowired
    DefaultAuthTokenHandler tokenHandler;
    @Autowired
    DateUtil dateUtil;
    @Autowired
    LocalStorageService dbStorageIterator;

    public static final String OAUTH_URL = "%s/oauth/v2/token";
    public static final String SERVICE_URL = "%s/crm/v2.1/";
    public static final String SERVICE_URL_AUTH_FIELD = "crmServiceURL";

    private static final int API_MAX_PAGESIZE = 100;
    // Zoho APIs do not return ID field in metadata APIs but the record's id is identified by this field.
    private static final String ID_FIELD = "id";

    // METADATA
    private static final String MODULES_URL = "%s/settings/modules";
    private static final String FIELDS_URL = "%s/settings/fields";

    // RECORDS GET
    private static final int MAX_SEARCH_CRITERIA = 10;
    private static final String RECORDS_URL = "%s?per_page=%s&page=%s&sort_by=Modified_Time&sort_order=asc";
    private static final String SEARCH_URL = "%s/search?criteria=%s&per_page=%s&page=%s";
    private static final String DELETED_RECORDS_URL = "%s/deleted?type=all&per_page=%s&page=%s";

    private static final Map<String, Set<String>> READONLY_ATTRIBUTES = Map.of("Products", Set.of("Tax"));

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(new AuthMetadata(AuthType.Oauth,
            List.of(ConnectorHelper.getClientIdField(), ConnectorHelper.getClientSecretField()), "OAuth", ""));
    }

    @Override
    public List<AuthField> getConfigureFields() {
        AuthField crmServiceURL = new AuthField();
        crmServiceURL.setDataType("text");
        crmServiceURL.setName(SERVICE_URL_AUTH_FIELD);
        crmServiceURL.setLabel("Zoho CRM API Domain URL");
        crmServiceURL.setHelpSummary("Example: https://www.zohoapis.com");
        AuthField endpointURL = ConnectorHelper.getEndpointField();
        endpointURL.setHelpSummary("Accounts URL. Example, https://accounts.zoho.com");
        return List.of(endpointURL, crmServiceURL, ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public String getCategory() {
        return "CRM";
    }
    
    @Override
    public String getName() {
        return Constants.ZOHO;
    }

    @Override
    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/zoho-crm.svg")
                .setDisplayName("Zoho CRM")
                .setBackgroundColor("#F5F9FF")
                .setHelpUrl(helpArticlesBaseUrl + "/360060572811-Zoho-CRM-Setup");
    }

    public ZohoRestClient getClient() {
        return new ZohoRestClient(getSingleJsonConfig(""), mapper, dateUtil);
    }
    
    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        return withRateLimitHandling(request.getConnector().getId(), () -> {
            ZohoRestClient restClient = getClient();
            int pageSize = request.getPageSize() > 0 ? request.getPageSize() : API_MAX_PAGESIZE;
            Function2<WatermarkInfo, Integer, ZohoEntityPage> generator = (wm, pageIndex) -> {
                String endPointURL = String.format(SERVICE_URL, getCRMServiceURL(request.getConnector().getMetaConfig()));
                String recordsURL = String.format(RECORDS_URL, request.getEntityName(), String.valueOf(pageSize), pageIndex.toString());
                if("users".equalsIgnoreCase(request.getEntityName())) {
                    recordsURL += "&type=AllUsers";
                }
                return restClient.get(endPointURL + recordsURL, request);
            };
            
            ZohoIterator iterator = new ZohoIterator(request.getWatermark(), generator, new ArrayList<>(), pageSize);
            long pageNumber = (request.getWatermark().getOffset() > 0) ? request.getWatermark().getOffset() : 1;
            iterator.setNextPageNumber(Integer.valueOf((int) pageNumber));

            return new FetchResponse(request.getWatermark(), iterator);
        });
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19175759505940";
    }

    @Override
    public FetchResponse getDeletedByWatermark(SyncRequest request) {
        return withRateLimitHandling(request.getConnector().getId(), () -> {
            // The zoho Deleted Records endpoint '/deleted' does not support sort by, hence we have to pull all records in oneshot 
            // and use this iterator.
            dbStorageIterator.provisionIfNotExists(request, request.getEntityName() + "_" + UUID.randomUUID().toString());
            dbStorageIterator.fetch(request, getDeletedRecordsIterator(request));
            return dbStorageIterator.getByWatermark(request);
        });
    }

    private ZohoIterator getDeletedRecordsIterator(SyncRequest request) {
        ZohoRestClient restClient = getClient();
        int pageSize = request.getPageSize() > 0 ? request.getPageSize() : API_MAX_PAGESIZE;
        Function2<WatermarkInfo, Integer, ZohoEntityPage> generator = (wm, pageIndex) -> {
            String endPointURL = String.format(SERVICE_URL, getCRMServiceURL(request.getConnector().getMetaConfig()));
            String recordsURL = String.format(DELETED_RECORDS_URL, request.getEntityName(), String.valueOf(pageSize), pageIndex.toString());
            return restClient.get(endPointURL + recordsURL, request);
        };
        
        ZohoIterator iterator = new ZohoIterator(request.getWatermark(), generator, new ArrayList<>(), pageSize);
        // We always drain all deleted records/sync cycle, so start with 1 as first page.
        iterator.setNextPageNumber(1);

        return iterator;
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        return withRateLimitHandling(request.getConnector().getId(), () -> {
            List<EntityData> result = new ArrayList<>();
            List<EntityData> data = request.getData().get(request.getConnector().getId());
            var partitioned = Lists.partition(data, MAX_SEARCH_CRITERIA);
            ZohoRestClient restClient = getClient();
            String endPointURL = String.format(SERVICE_URL, getCRMServiceURL(request.getConnector().getMetaConfig()));
            partitioned.forEach(partition -> {
                List<String> ids = partition.stream().map(e -> e.getId()).filter(id -> !StringUtils.isBlank(id)).collect(Collectors.toList());
                String idCriteria = "";
                // Traditional for iterator works well here.
                for (int i = 0; i < ids.size(); i++) {
                    idCriteria += String.format("(Id:equals:%s)", ids.get(i));
                    if (i != ids.size() - 1) idCriteria += "or";
                }
                String searchURL = String.format(SEARCH_URL, request.getEntityName(), idCriteria, MAX_SEARCH_CRITERIA, 1);
                ZohoEntityPage pageResults = restClient.get(endPointURL + searchURL, request);
                result.addAll(pageResults.getData());
            });
            return result;
        });
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0l;
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        return createOrUpdate(request, true);
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        return createOrUpdate(request, false);
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        return withRateLimitHandling(request.getConnector().getId(), () -> {
            List<EntityData> data = request.getData().get(request.getConnector().getId());
            ZohoRestClient restClient = getClient();
            SyncResponse response = new SyncResponse();
            var partitioned = Lists.partition(data, request.getPageSize() > 0 ? request.getPageSize() : MAX_SEARCH_CRITERIA);
            String endPointURL = String.format(SERVICE_URL, getCRMServiceURL(request.getConnector().getMetaConfig())) + request.getEntityName();
            for (List<EntityData> partition: partitioned) {
                List<String> ids = partition.stream().map(e -> e.getId()).filter(id -> !StringUtils.isBlank(id)).collect(Collectors.toList());
                String idsString = "?ids=" + String.join(",", ids);
                restClient.delete(endPointURL + idsString, request.getConnector().getAuthConfig());
                // TODO This is incomplete and we need to capture the response for each object deletion.
            }
            return response;
        });
    }

    private SyncResponse createOrUpdate(SyncRequest request, boolean isCreate) {
        return ConnectorHelper.withBackoffAndErrorHandling(() -> {
            List<EntityData> data = request.getData().get(request.getConnector().getId());
            ZohoRestClient restClient = getClient();
            EntitySchema schema = request.getEntitySchema();
            SyncResponse response = new SyncResponse();
            var partitioned = Lists.partition(data, request.getPageSize() > 0 ? request.getPageSize() : MAX_SEARCH_CRITERIA);
            String endPointURL = String.format(SERVICE_URL, getCRMServiceURL(request.getConnector().getMetaConfig())) + request.getEntityName();
            for (List<EntityData> partition: partitioned) {
                List<Map<String, Object>> payload = new ArrayList<>();
                partition.forEach(x -> {
                    Map<String, Object> values = x.getValues();
                    // For Updates, we need to pass the id value explictly.
                    if (!isCreate) values.put(ID_FIELD, x.getId());
                    payload.add(transformValues(values, schema));
                });
                String payloadString = mapper.writeValueAsString(Map.of("data", payload));
                ResponseEntity<String> resp;
                if (isCreate) {
                    resp = restClient.postRaw(endPointURL, payloadString, request.getConnector().getAuthConfig());
                } else {
                    resp = restClient.patch(endPointURL, payloadString, request.getConnector().getAuthConfig());
                }
                if (((isCreate && resp.getStatusCode() != HttpStatus.CREATED) || (!isCreate && resp.getStatusCode() != HttpStatus.OK)) && resp.getStatusCode() != HttpStatus.MULTI_STATUS) {
                    response.getResults().add(new Result(false, resp.getBody()));
                    continue;
                }
                Map<String, Object> responseValue = mapper.readValue(resp.getBody(), Map.class);
                List<Map<String, Object>> respData = (List<Map<String, Object>>) responseValue.get("data");
                for (int i = 0; i < respData.size(); i++) {
                    Map<String, Object> respObj = respData.get(i);
                    if (!"SUCCESS".equalsIgnoreCase(respObj.get("code").toString())) {
                        Result result = new Result(false, null, partition.get(i).getSyncariEntityId())
                            .addError(respObj.get("details").toString());
                        if (!isCreate) {
                            result.setId(partition.get(i).getId());
                        }
                        response.getResults().add(result);
                    } else if (respObj.containsKey("details")) {
                        Map<String, Object> detailedValues = (Map<String, Object>) respObj.get("details");
                        response.getResults().add(new Result(true, detailedValues.get(schema.getIdField().getApiName()).toString(), 
                                partition.get(i).getSyncariEntityId()));
                    }
                }
            }
            return response;
        });
    }

    private Map<String, Object> transformValues(Map<String, Object> inputMap, EntitySchema schema) {
        for(AttributeSchema attr: schema.getAttributes()) {
            if(inputMap.containsKey(attr.getApiName())) {
                String dataType = attr.getDataType();
                if(dataType.equalsIgnoreCase("date") || dataType.equalsIgnoreCase("datetime")) {
                    inputMap.put(attr.getApiName(), tranformDate(dataType, inputMap.get(attr.getApiName())));
                }
            }
        }
        return inputMap;
    }

    private String tranformDate(String dataType, Object value) {
        String result = "";
        if(value instanceof Date) {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
            Date date = (Date) value;
            result = formatter.format(date);
        }
        if(value instanceof ZonedDateTime) {
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
            ZonedDateTime dateTime = (ZonedDateTime) value;
            result = dateTime.format(dateTimeFormatter);
        }
        if(value instanceof String) {
            ZonedDateTime date = ConnectorHelper.convert(String.valueOf(value));
            if(dataType.equalsIgnoreCase("date")) {
                DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                result = date.format(dateTimeFormatter);
            }
            else if(dataType.equalsIgnoreCase("datetime")){
                DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
                result = date.format(dateTimeFormatter);
            }
        }
        return result;
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        List<EntitySchema> entitySchemas = describeAll(new DescribeAllRequest(request.getConnector(), List.of(request.getEntity())));
        if (entitySchemas.isEmpty()) return Optional.empty();
        return Optional.of(entitySchemas.get(0));
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        return withRateLimitHandling(request.getConnector().getId(), () -> {
            List<EntitySchema> entitySchemas = new ArrayList<>();
            ZohoRestClient restClient = getClient();
            ResponseEntity<String> resp = restClient.getResponse(getRequestURL(request.getConnector(), MODULES_URL), 
                request.getConnector().getAuthConfig());
            if (resp.getStatusCode() != HttpStatus.OK) {
                throw new RuntimeException("Failed describe due to " + resp);
            }
            Map respMap;
            try {
                respMap = mapper.readValue(resp.getBody(), Map.class);
            } catch (JsonProcessingException e1) {
                throw new RuntimeException("Failed to read entities from zoho crm", e1);
            }
            List<Map<String, Object>> rawSchemas = (ArrayList<Map<String, Object>>) respMap.get("modules");
            rawSchemas.forEach(x -> {
                if (((Boolean) x.get("api_supported")) == false || ((Boolean) x.get("visible")) == false ) return;
                EntitySchema entitySchema = toEntitySchema(x);
                if (CollectionUtils.isNotEmpty(request.getEntities()) && 
                    !request.getEntities().contains(entitySchema.getApiName())) return;

                final Optional<String> fieldsResponse = getFieldsResponse(request, entitySchema, restClient);
                if (fieldsResponse.isEmpty()) return;
                try {

                    Map fieldRespMap = mapper.readValue(fieldsResponse.get(), Map.class);
                    List<Map<String, Object>> rawFieldSchemas = (ArrayList<Map<String, Object>>) fieldRespMap.get("fields");
                    rawFieldSchemas.forEach(y -> {
                        if (Set.of("profileimage", "multiselectlookup").contains(y.get("data_type").toString())) return;
                        AttributeSchema attributeSchema = toAttributeSchema(y);
                        if(READONLY_ATTRIBUTES.containsKey(entitySchema.getApiName()) && READONLY_ATTRIBUTES.get(entitySchema.getApiName()).contains(attributeSchema.getApiName())) {
                            attributeSchema.setUpdateable(false);
                        }
                        entitySchema.addField(attributeSchema);
                    });
                    if (!entitySchema.getField(ID_FIELD).isPresent()) {
                        entitySchema.addField(new AttributeSchema("id", "string").setIdField(true).setSystem(true).setDisplayName("Id"));
                    }
                    if (!entitySchema.hasWatermarkField()) {
                        entitySchema.addField(new AttributeSchema("Modified_Time", "datetime").setWatermarkField(true).setSystem(true).setDisplayName("Modified Time"));
                    }
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Failed to get fields for entity {}" + entitySchema.getApiName(), e);
                }
                entitySchemas.add(entitySchema);
            });
            if (CollectionUtils.isEmpty(request.getEntities()) || request.getEntities().contains("users")) {
                EntitySchema userEntity = new EntitySchema("users", "Users");
                userEntity.addField(new AttributeSchema("id", "string").setDisplayName("Id").setNillable(true).setIdField(true));
                userEntity.addField(new AttributeSchema("name", "string").setDisplayName("Name").setNillable(true));
                userEntity.addField(new AttributeSchema("email", "string").setDisplayName("Email").setNillable(false));
                userEntity.addField(new AttributeSchema("job_title", "string").setDisplayName("Job Title").setNillable(true));
                userEntity.addField(new AttributeSchema("is_active", "boolean").setDisplayName("Is Active").setNillable(false));
                userEntity.addField(new AttributeSchema("work_number", "string").setDisplayName("Work Number").setNillable(true));
                userEntity.addField(new AttributeSchema("mobile_number", "string").setDisplayName("Mobile Number").setNillable(true));
                userEntity.addField(new AttributeSchema("modified_time", "datetime").setDisplayName("Modified Time").setNillable(false)
                    .setWatermarkField(true).setSystem(true));
                userEntity.addField(new AttributeSchema("created_time", "datetime").setDisplayName("Created Time").setNillable(false)
                    .setSystem(true));
                userEntity.setReadOnly(true);
                entitySchemas.add(userEntity);
            }
            return entitySchemas;
        });
        
    }

    private Optional<String> getFieldsResponse(DescribeAllRequest request, EntitySchema entitySchema, ZohoRestClient restClient) {
        String fieldsURL = getRequestURL(request.getConnector(), FIELDS_URL) + "?module=" + entitySchema.getApiName();
        try {
            ResponseEntity<String> fieldResp = restClient.getResponse(fieldsURL, request.getConnector().getAuthConfig());
            // certain objects like VISITS are not supported, skip processing those.
            if (fieldResp.getStatusCode() == HttpStatus.NO_CONTENT) {
                return Optional.empty();
            }
            if (fieldResp.getStatusCode() != HttpStatus.OK) {
                log.error("fieldResp received " + fieldResp);
                throw new RuntimeException(String.format("Failed to get fields for entity %s due to %s", entitySchema.getApiName(), fieldResp));
            }
            return Optional.of(fieldResp.getBody());
        } catch (NonRetriableException e) {
            log.error("Failed to retrieve fields from " + fieldsURL, e);
        }
        return Optional.empty();
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        throw new RuntimeException("Zoho CRM synapse does not support create field");
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new RuntimeException("Zoho CRM synapse does not support delete field");
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new RuntimeException("createObject not supported in Zoho CRM synapse yet");
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
    public AuthConfig refreshToken(ConnectorInfo connector) {
        AuthConfig config = connector.getAuthConfig();
        config.setEndpoint(config.getEndpoint());
        Map<String, String> map = Map.of(DefaultAuthTokenHandler.GRANT_TYPE, DefaultAuthTokenHandler.REFRESH_TOKEN,
                DefaultAuthTokenHandler.REFRESH_TOKEN, config.getRefreshToken(),
                DefaultAuthTokenHandler.CLIENT_ID, config.getClientId(), 
                DefaultAuthTokenHandler.CLIENT_SECRET, config.getClientSecret());
        // Zoho token APIs can be flaky, retry for hard failures but with minimum delay.
        return ConnectorHelper.withBackoff(() -> {
            return tokenHandler.refreshToken(config, String.format(OAUTH_URL, config.getEndpoint()), map);
        }, 1000, 2000, 5);
    }

    @Override
    public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
        oAuthRequest.setEndpoint(oAuthRequest.getEndpoint());
        Map<String, String> map = Map.of(DefaultAuthTokenHandler.GRANT_TYPE, "authorization_code", 
                DefaultAuthTokenHandler.CODE, oAuthRequest.getCode(), 
                DefaultAuthTokenHandler.CLIENT_ID, oAuthRequest.getConfig().getClientId(),
                DefaultAuthTokenHandler.CLIENT_SECRET, oAuthRequest.getConfig().getClientSecret(), 
                DefaultAuthTokenHandler.RESOURCE, getCRMServiceURL(oAuthRequest.getMetaConfig()), 
                DefaultAuthTokenHandler.REDIRECT_URI, oAuthRequest.getRedirectUri());

        return tokenHandler.getAccessToken(String.format(OAUTH_URL, oAuthRequest.getEndpoint()), map);
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse response = new TestConnectionResponse();
        try {
            DescribeAllRequest describeAllRequest = new DescribeAllRequest(config, List.of());
            List<EntitySchema> entityDataList = describeAll(describeAllRequest);
            log.info("Data received " + entityDataList);
        } catch (Exception e) {
            log.error("Zoho CRM synapse testConnection failed due to " + e.getMessage());
            handleAuthenticationErrorMessage(response, e);
        }
        return response;
    }

    private JsonParserConfig getSingleJsonConfig(String plural) {
        return new JsonParserConfig(null, null, null, "id", true, null);
    }

    @Override
    public String getOAuthUri(ConnectorInfo connector) {
        return "/oauth/v2/auth?scope=ZohoCRM.modules.ALL,ZohoCRM.settings.ALL,ZohoCRM.users.ALL,ZohoCRM.org.ALL" + 
            "&redirect_uri={{redirect_uri}}&client_id={{client_id}}&response_type=code&access_type=offline";
    }
    
    @Override
    public String getAuthHost(AuthConfig config) {
        return config.getEndpoint();
    }

    private String getRequestURL(ConnectorInfo connector, String endpoint) {
        return String.format(endpoint, String.format(SERVICE_URL, getCRMServiceURL(connector.getMetaConfig())));
    }

    private String getCRMServiceURL(Map<String, Object> metaConfig) {
        if (!metaConfig.containsKey(SERVICE_URL_AUTH_FIELD)) {
            log.error("Zoho CRM URL not configured or empty");
            return "";
        }
        return metaConfig.get(SERVICE_URL_AUTH_FIELD).toString();
    }

    private EntitySchema toEntitySchema(Map<String, Object> schemaRawValue) {
        return new EntitySchema(schemaRawValue.get("api_name").toString())
                .setPluralName(schemaRawValue.get("plural_label").toString())
                .setDisplayName(schemaRawValue.get("singular_label").toString())
                .setDescription(schemaRawValue.get("description") != null ? schemaRawValue.get("description").toString() : "")
                .setReadOnly(!(Boolean) schemaRawValue.get("editable"));
    }

    private AttributeSchema toAttributeSchema(Map<String, Object> fieldRawValue) {
        //log.info("fieldRawValue {} ", fieldRawValue);
        AttributeSchema field = new AttributeSchema(fieldRawValue.get("api_name").toString(), fieldRawValue.get("data_type").toString())
            .setDisplayName(fieldRawValue.get("display_label").toString())
            .setDataType(fieldRawValue.get("data_type").toString())
            .setCustom((Boolean) fieldRawValue.get("custom_field"))
            .setNillable(!(Boolean) fieldRawValue.get("system_mandatory"))
            .setUpdateable(!(Boolean) fieldRawValue.get("read_only"))
            .setLength(fieldRawValue.get("length") != null ? Integer.parseInt(fieldRawValue.get("length").toString()) : 0)
            .setScale(fieldRawValue.get("decimal_place") != null ? Integer.parseInt(fieldRawValue.get("decimal_place").toString()) : 0);

        if (field.getScale() > 0) {
            field.setPrecision(field.getLength());
        }

        switch (field.getApiName()) {
            case "Modified_Time":
                field.setWatermarkField(true);
                field.setUpdatedAtField(true);
                field.setSystem(true);
                break;
            case "Created_Time":
                field.setCreatedAtField(true);
                field.setSystem(true);
                break;
            case "id":
                field.setIdField(true);
                field.setNillable(false);
                field.setUpdateable(false);
                field.setSystem(true);
                break;
            case "Created_By":
            case "Modified_By":
                field.setSystem(true);
                break;
            default:
                break;
        }

        switch (field.getDataType()) {
            case "lookup":
                Map<String, Object> lookup = (LinkedHashMap) fieldRawValue.get("lookup");
                if (lookup.containsKey("module")) {
                    field.setDataType("reference");
                    field.setReferenceTo(lookup.get("module").toString());
                    field.setReferenceTargetField("id");
                } else {
                    field.setDataType("string");
                }
                break;
            case "ownerlookup":
            case "userlookup":
                field.setDataType("reference");
                field.setReferenceTo("users");
                field.setReferenceTargetField("id");
                break;
            case "picklist":
            case "multiselectpicklist":
                List<String> pickListValues = new ArrayList();
                List<Map<String, Object>> plValues = (List) fieldRawValue.get("pick_list_values");
                plValues.forEach(x -> pickListValues.add(x.get("actual_value").toString()));
                field.setPicklistValues(pickListValues);
                if(field.getDataType().equalsIgnoreCase("multiselectpicklist")) field.setMultiValueField(true);
                break;
            case "bigint":
                field.setDataType("integer");
                break;
            case "multiselectlookup":
                log.info("multiselectlookup {} ", fieldRawValue);
                throw new RuntimeException("multiselectlookup data type not yet supported for Zoho.");
            default:
                break;
        }

        if (fieldRawValue.containsKey("view_type")) {
            Map<String, Object> viewType = (LinkedHashMap) fieldRawValue.get("view_type");
            field.setInitializable((Boolean) viewType.get("create"));
            field.setUpdateable((Boolean) viewType.get("edit"));
        }

        return field;
    }
}
