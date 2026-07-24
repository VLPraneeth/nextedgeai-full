package com.syncari.connector.freshsales;

import static java.lang.String.format;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.api.client.util.ArrayMap;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.syncari.connector.*;
import com.syncari.connector.data.*;
import com.syncari.connector.exception.RetriableException;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jooq.lambda.function.Function3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.iterator.DefaultDataIterator;
import com.syncari.connector.data.iterator.Offset;
import com.syncari.connector.data.iterator.Offset.OffsetType;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.QuotaExceededException;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.connector.service.Transformer;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.utils.DateUtil;
import com.syncari.utils.Pair;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component(Constants.FRESHSALES)
public class FreshsalesService implements CommonDataService, MetadataService, SynapseInfoService, AuthenticationService {
    private static final String GROUP_FIELD = "group_field";
    private static final String CUSTOM_FIELD = "custom_field";
    @Autowired
    Transformer transformer;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    DateUtil dateUtil;
    private static final Map<String,String> describeMap = Map.of(
            FreshsalesSeed.CONTACT, "api/settings/contacts/fields", FreshsalesSeed.ACCOUNT, "api/settings/sales_accounts/fields",
            FreshsalesSeed.DEAL, "api/settings/deals/fields",FreshsalesSeed.LEAD,"api/settings/leads/fields");
    private static final Map<String,String> crudMap = Map.of(
            FreshsalesSeed.CONTACT, "api/contacts", FreshsalesSeed.ACCOUNT, "api/sales_accounts",FreshsalesSeed.LEAD,"api/leads",
            FreshsalesSeed.DEAL, "api/deals", FreshsalesSeed.NOTE, "api/notes");
    private static final Map<String,String> suffix = Map.of(
            FreshsalesSeed.CONTACT, "?include=sales_accounts,owner,campaign,contact_status,creater,updater,source,tasks,appointments,notes,deals,territory",
            FreshsalesSeed.ACCOUNT, "?include=sales_accounts,owner,creater,updater,territory,business_type,tasks,appointments,contacts,deals,industry_type,child_sales_accounts",
            FreshsalesSeed.DEAL, "?include=sales_account,owner,campaign,creater,updater,source,contacts,sales_account,deal_stage,deal_type,deal_reason,deal_payment_status,deal_product,currency,probability",
            FreshsalesSeed.LEAD, "?include=sales_accounts,owner,campaign,creater,updater,source,lead_stage,lead_reason,territory,tasks,appointments,notes");
    private static final Map<String,String> postMap = Map.of(
            FreshsalesSeed.CONTACT, "api/filtered_search/contact?per_page=%s&page=%s&include=sales_accounts,owner,campaign,contact_status,creater,updater,source,tasks,appointments,notes,deals,territory",
            FreshsalesSeed.ACCOUNT, "api/filtered_search/sales_account?per_page=%s&page=%s&include=sales_accounts,owner,creater,updater,territory,business_type,tasks,appointments,contacts,deals,industry_type,child_sales_accounts",
            FreshsalesSeed.DEAL, "api/filtered_search/deal?per_page=%s&page=%s&include=sales_account,owner,campaign,creater,updater,source,contacts,sales_account,deal_stage,deal_type,deal_reason,deal_payment_status,deal_product,currency,probability",
            FreshsalesSeed.LEAD, "api/filtered_search/lead?per_page=%s&page=%s&include=sales_accounts,owner,campaign,creater,updater,source,lead_stage,lead_reason,territory,tasks,appointments,notes");
    private static final int DEFAULT_PAGE_SIZE=10;
    private static final Map<String, List<String>> multivalued = Map.of(
            FreshsalesSeed.CONTACT, List.of("emails", "phone_numbers", "sales_accounts"),
            FreshsalesSeed.LEAD, List.of("emails", "phone_numbers", "sales_accounts")
    );
    private static final Map<String, Map<String, String>> reference = Map.of(
            FreshsalesSeed.CONTACT, Map.of("sales_accounts", "sales_account"),
            FreshsalesSeed.DEAL, Map.of("contacts", "contact_ids")
            );
    private static final Map<String, Set<String>> referenceFields = Map.of(
        FreshsalesSeed.DEAL, Set.of("sales_account_id")
    );
    private static final Map<String, Map<String, Set<String>>> nested = Map.of(
            FreshsalesSeed.LEAD, Map.of("company", Set.of("name", "company_address", "company_city", "company_state", "company_zipcode", "company_country", "number_of_employees",
                    "annual_revenue", "website", "phone", "industry_type_id", "industry_type", "business_type_id", "business_type"
                    ))
            );
    private static final Map<String, List<String>> nestedFieldsExclusion = Map.of(
            "deal", List.of("name")
            );

    private static final List<String> REFERENCES_AS_DROPDOWN = List.of("owner_id", "creater_id", "updater_id");

    private static final String OUT_OF_ORDER_RECORDS_ERROR = "OUT_OF_ORDER_RECORDS";

    private static final Set<String> LEAD_DUPLICATE_KEYS = Set.of("zipcode", "country", "address", "city", "name", "state");


    private List<EntityData> fetchUsers(ConnectorInfo connector) {
        String url =getHost(connector) +"api/selector/owners";
        SyncariEntityDataRestClient restClient = getClient();
        AuthConfig authConfig = connector.getAuthConfig();
        Map<String, EntityData> recordMap = new HashMap<>();
        ResponseEntity<String> response = restClient.getResponse(getAuthHeaders(authConfig),url, authConfig);
        ReadContext responseBody = JsonPath.parse(response.getBody());
        List<Map> rows = responseBody.read("users");
        Instant now = Instant.now();
        rows.forEach(row->{
            String id = getValue(row, "id");
            EntityData data = new EntityData("user").setConnectorId(connector.getId()).setId(id);
            Object updatedAt = row.get("updated_at");
            data.setLastModified(updatedAt==null? now.toEpochMilli() : dateUtil.toEpochMilli(updatedAt.toString()));
            data.setCreatedAt(data.getLastModified());
            data.addValue("name",getValue(row,"display_name"));
            data.addValue("id",id);
            data.addValue("email",getValue(row,"email"));
            data.addValue("is_active",Boolean.valueOf(row.get("is_active").toString()));
            data.addValue("work_number",getValue(row,"work_number"));
            data.addValue("mobile_number",getValue(row,"mobile_number"));
            recordMap.put(id, data);
        });
        List<EntityData> sortedByLastModified =new ArrayList<>(recordMap.values());
        sortedByLastModified.sort(Comparator.comparingLong(EntityData::getLastModified));
        return sortedByLastModified;
    }

    private String getValue(Map row, String key) {
        return row.get(key)!=null ? row.get(key).toString() : null;
    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getApiKey());
    }

    @Override
    public List<AuthField> getConfigureFields() {
        return List.of(ConnectorHelper.getEndpointField(), ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19200158683028";
    }

    @Override
    public String getCategory() {
        return "CRM";
    }

    @Override
    public String getName() {
        return Constants.FRESHSALES;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/freshsales.svg")
                .setDisplayName("Freshsales")
                .setBackgroundColor("#FFF9EE")
                .setHelpUrl(helpArticlesBaseUrl + "/360056874791-Freshales-Setup");
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        if(FreshsalesSeed.USER.equalsIgnoreCase(request.getEntityName())){
            return fetchUsers(request);
        }
        return new FetchResponse(request.getWatermark(), getIterator(request, new ValueHolder<>("")));
    }


    private FetchResponse fetchUsers(SyncRequest request) {
        return new FetchResponse(request.getWatermark(),
                new ListBasedIterator(fetchUsers(request.getConnector()),request.getWatermark()));
    }

    @Override
    public FetchResponse getDeletedByWatermark(SyncRequest request) {
        if(FreshsalesSeed.USER.equalsIgnoreCase(request.getEntityName())){
            return null;
        }
        return new FetchResponse(request.getWatermark(), getDeletedIterator(request, new ValueHolder<>("")));
    }


    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        SyncariEntityDataRestClient restClient = getClient();
        AuthConfig authConfig = request.getConnector().getAuthConfig();
        return Optional.ofNullable(describeEntity(request.getConnector(), getHost(request.getConnector()),restClient,authConfig,request.getEntity()));
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        String host = getHost(request.getConnector());
        List<String> entities = isFreshSales(host) ? FreshsalesSeed.FRESHSALES_SEED_ENTITIES :FreshsalesSeed.SEED_ENTITIES ;
        SyncariEntityDataRestClient restClient = getClient();
        AuthConfig authConfig = request.getConnector().getAuthConfig();
        List<EntitySchema> schemaList = new ArrayList<>();
        entities.forEach(e -> {
            EntitySchema entity = describeEntity(request.getConnector(), host, restClient, authConfig, e);
            schemaList.add(entity);
        });
        return schemaList;
    }

    protected EntitySchema describeEntity(ConnectorInfo connector, String host, SyncariEntityDataRestClient restClient, 
            AuthConfig authConfig, String entityName) {
        EntitySchema entity = FreshsalesSeed.getSeedEntitySchema(entityName);
        if(FreshsalesSeed.hasOnlySeededAttributes(entityName)){
            return entity;
        }
        try {
            String url = host + describeMap.get(entityName);
            ResponseEntity<String> response = restClient.getResponse(getAuthHeaders(authConfig), url, authConfig);
            ReadContext responseBody = JsonPath.parse(response.getBody());
            List<Map> rows = responseBody.read("fields");

            for (Map map : rows) {
                String apiName = map.get("name").toString();
                String displayName = map.get("label").toString();
                AttributeSchema attr = new AttributeSchema();
                if(entityName.equalsIgnoreCase(FreshsalesSeed.LEAD)) {
                    if(apiName.equalsIgnoreCase("name")) {
                        displayName = "Company Name";
                    } else {
                        apiName = getSyncariApiName(apiName, displayName, null);
                    }
                }
                attr.setApiName(apiName);
                attr.setDisplayName(displayName);
                String datatype = getValue(map, "type");
                // default flag is set to true for standard fields and false for custom fields
                boolean defaultField = Boolean.parseBoolean(map.get("default").toString());
                attr.setCustom(!defaultField);
                if(GROUP_FIELD.equalsIgnoreCase(datatype)) {
                    if(reference.containsKey(entityName) && reference.get(entityName).containsKey(attr.getApiName())) {
                        attr.setDataType("reference");
                        attr.setReferenceTo(reference.get(entityName).get(attr.getApiName()));
                        attr.setReferenceTargetField("id");
                    } else {
                        attr.setDataType("string");
                    }
                    attr.setMultiValueField(true);
                } else if("dropdown".equalsIgnoreCase(datatype)) {
                    if (REFERENCES_AS_DROPDOWN.contains(attr.getApiName().toLowerCase())) {
                        attr.setDataType("reference");
                        attr.setReferenceTo(FreshsalesSeed.USER);
                        attr.setReferenceTargetField("id");
                    } else if (map.containsKey("choices")) {
                        attr.setDataType("picklist");
                        List<String> pickListValues = new ArrayList();
                        List<Map<String, Object>> plValues = (List) map.get("choices");
                        plValues.forEach(x -> pickListValues.add(x.get("id").toString()));
                        attr.setPicklistValues(pickListValues);
                    }
                } else if ("auto_complete".equalsIgnoreCase(datatype)){
                    if(referenceFields.containsKey(entity.getApiName()) &&
                        referenceFields.get(entity.getApiName()).contains(attr.getApiName())) {
                        attr.setDataType("reference");
                        attr.setReferenceTo("sales_account");
                        attr.setReferenceTargetField("id");
                    }else if (getValue(map,"auto_suggest_url")!=null) {
                        String autoSuggestUrl = getValue(map, "auto_suggest_url");
                        if (autoSuggestUrl.contains("contacts_full_details") || autoSuggestUrl.contains("include=contact")) {
                            attr.setDataType("reference");
                            attr.setReferenceTo("contact");
                            attr.setReferenceTargetField("id");
                            if(reference.containsKey(entityName) && reference.get(entityName).containsKey(attr.getApiName())) {
                                attr.setApiName(reference.get(entityName).get(attr.getApiName()));
                            }
                        } else if (autoSuggestUrl.contains("sales_accounts_full_details") || autoSuggestUrl.contains("include=sales_account")) {
                            attr.setDataType("reference");
                            attr.setReferenceTo("sales_account");
                            attr.setReferenceTargetField("id");
                        } else if (autoSuggestUrl.contains("include=user")) {
                            attr.setDataType("reference");
                            attr.setReferenceTo("user");
                            attr.setReferenceTargetField("id");
                        } else{
                            attr.setDataType(datatype);
                        }
                    }else{
                        attr.setDataType(datatype);
                    }
                } else {
                    attr.setDataType(datatype);
                }
                if("true".equals(getValue(map,"multiple")) || "multi_select_dropdown".equalsIgnoreCase(datatype)){
                    attr.setMultiValueField(true);
                }
                attr.setNillable(!(Boolean)map.get("required"));
                if(!entity.hasField(attr.getApiName())) {
                    entity.addField(attr);
                }
            }
        } catch (Exception e) {
            log.error(ExceptionUtils.getStackTrace(e));
            handleException(e, connector);
        }
        return entity;
    }

    private String getSyncariApiName(String apiName, String displayName, String parent) {
        if(parent != null && parent.equalsIgnoreCase(CUSTOM_FIELD)) return apiName;
        if(LEAD_DUPLICATE_KEYS.contains(apiName) && !apiName.equalsIgnoreCase("name") && ((displayName != null && displayName.contains("Company")) ||
                (parent != null && parent.equalsIgnoreCase("company") && LEAD_DUPLICATE_KEYS.contains(apiName)))) {
            return "company_" + apiName;
        }
        return apiName;
    }

    private boolean isFreshSales(String host) {
        return host.replace("/","").endsWith("freshsales.io");
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse result = new TestConnectionResponse();
        try {
            DescribeAllRequest request = new DescribeAllRequest(config, List.of());
            describeAll(request);
        } catch (Exception e) {
            try {
                String exError = e.getMessage();
                int startingIndex = exError.indexOf("{")-1;
                int closingIndex = exError.indexOf("}")+1;
                String errorMessage = exError.substring(startingIndex + 1, closingIndex);
                JsonNode node = mapper.readValue(errorMessage, JsonNode.class);
                result.setMessage(node.get("message").asText());
                result.setCode(HttpStatus.UNAUTHORIZED.name());
            } catch (Exception e2){
                e2.printStackTrace();
                result.setMessage("Unknown Error");
                result.setCode(HttpStatus.UNAUTHORIZED.name());
            }
        }
        return result;
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        throw new RuntimeException("Freshsales does not support create field");
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new RuntimeException("Freshsales does not support delete field");
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        List<EntityData> records = new ArrayList<>();
        String host = getHost(request.getConnector());
        request.getData().forEach((connectorId, ids)->{
            ids.forEach(id -> {
                // All freshsales ids are numeric, we can avoid one API call by checking for legit ID.
                if (!StringUtils.isNumeric(id.getId())) {
                    throw new NonRetriableException(ErrorCodes.BAD_REQUEST,
                        String.format("Expecting numeric value for id, received a non-numeric (or) null value: %s", id.getId()),
                        HttpStatus.BAD_REQUEST.toString());
                }
                if (FreshsalesSeed.USER.equalsIgnoreCase(request.getEntityName())) {
                    List<EntityData> recs = fetchUsers(request.getConnector()).stream()
                        .filter(x -> x.getId().equalsIgnoreCase(id.getId())).collect(Collectors.toList());
                    records.addAll(recs);
                } else {
                    String url = host + crudMap.get(request.getEntityName()) + "/" + id.getId();
                    getById(url, request).ifPresent(record -> {
                        records.add(record);
                    });
                }
            });
        });
        return records;
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        SyncResponse response = new SyncResponse();
        List<Result> results = new ArrayList<>();
        SyncariEntityDataRestClient restClient = getClient();
        AuthConfig authConfig = request.getConnector().getAuthConfig();
        request.getData().get(request.getConnector().getId()).stream().forEach(e -> {
            try {
                Map map = createPayload(e, request.getEntitySchema());
                map = updateMultivalue(e, map);
                ResponseEntity<String> resp = restClient.postRaw(getAuthHeaders(authConfig),
                        getHost(request.getConnector()) + crudMap.get(request.getEntityName()), mapper.writeValueAsString(map),
                        authConfig);
                Map row = JsonPath.parse(resp.getBody()).read(request.getEntityName().toLowerCase());
                Result result = new Result(true, row.get("id").toString(), e.getSyncariEntityId());
                result.setSyncariId(e.getSyncariEntityId());
                results.add(result);
            } catch (Exception e1) {
                if (e1 instanceof NonRetriableException && 
                    ErrorCodes.TOO_MANY_REQUESTS.toString().equals(((NonRetriableException) e1).getErrorCode())) {
                    handleException(e1, request.getConnector());
                }
                log.error(ExceptionUtils.getStackTrace(e1));
                Result result = new Result(false, null, e.getSyncariEntityId());
                result.addError(e1.getMessage());
                results.add(result);
                response.setSuccess(false);
            }
        });
        response.setResults(results);
        return response;
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        SyncResponse response = new SyncResponse();
        List<Result> results = new ArrayList<>();
        SyncariEntityDataRestClient restClient = getClient();
        request.getData().get(request.getConnector().getId()).stream().forEach(e -> {
            try {
                Map map = createPayload(e, request.getEntitySchema());
                map = updateMultivalue(e, map);
                AuthConfig authConfig = request.getConnector().getAuthConfig();
                restClient.put(getAuthHeaders(authConfig), getPostUrl(request, e), mapper.writeValueAsString(map),
                        authConfig);
                results.add(new Result(true, e.getId(), e.getSyncariEntityId()));
            } catch (NonRetriableException | JsonProcessingException e1) {
                if (e1 instanceof NonRetriableException && 
                    ErrorCodes.TOO_MANY_REQUESTS.toString().equals(((NonRetriableException) e1).getErrorCode())) {
                    handleException(e1, request.getConnector());
                }
                log.error(ExceptionUtils.getStackTrace(e1));
                Result result = new Result(false, null, e.getSyncariEntityId()).setId(e.getId());
                result.addError(e1.getMessage());
                results.add(result);
                response.setSuccess(false);
            }
        });
        response.setResults(results);
        return response;
    }

    // custom fields need to be aggregated and added as separate map inside the payload for create and update
    public Map createPayload(EntityData data, EntitySchema schema){
        String entityName = schema.getApiName();
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> customFields = new HashMap<>();
        data.getValues().forEach((k, v) -> {
           // Special processing for specific non custom multi-valued fields entities lead, contact except for the fields in  multivalued
           if(multivalued.containsKey(schema.getApiName()) && !multivalued.get(schema.getApiName()).contains(k)) {
                if(schema.hasField(k)
                        && schema.getField(k).get().isMultiValueField()
                        && !schema.getField(k).filter(AttributeSchema::isCustom).isPresent()
                        && v instanceof List) {
                    List<String> values = (List<String>) v;
                    v = String.join(";", values);
                }
            }
           // Value processing for custom multivalued fields
            if(schema.getField(k).filter(AttributeSchema::isCustom).isPresent()){
                if(schema.getField(k).get().isMultiValueField()) {
                    if (v instanceof List){
                        List<String> values = (List<String>) v;
                        v = String.join(";", values);
                    } else {
                        String valuesString = v.toString();
                        List<String> values = new ArrayList<String>(Arrays.asList(valuesString.substring(1, valuesString.length() - 1).split(",")));
                        v = String.join(";", values);
                    }
                }
                customFields.put(k, v);
            } else {
                if(nested.containsKey(entityName)) {
                    Map<String, Set<String>> map = nested.get(entityName);
                    Object value = v;
                    getNestParentName(map, k).ifPresentOrElse(parent -> {
                        Map<String, Object> nestedPayload = (Map<String, Object>) payload.getOrDefault(parent, new HashMap<>());
                        nestedPayload.put(sanitizeApiName(parent, k), value);
                        payload.put(parent, nestedPayload);
                    }, () -> payload.put(k, value));
                } else {
                    payload.put(k, v);
                }
            }
        });

        payload.put(CUSTOM_FIELD, customFields);
        return payload;
    }

    private String sanitizeApiName(String parent, String k) {
        if(k.startsWith("company_")) {
            return k.substring(8, k.length());
        }
        return k;
    }

    private Optional<String> getNestParentName(Map<String, Set<String>> map, String k) {
        return map.entrySet()
                .stream()
                .filter(entry -> entry.getValue().contains(k))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        SyncResponse response = new SyncResponse();
        List<Result> results = new ArrayList<>();
        SyncariEntityDataRestClient restClient = getClient();
        request.getData().get(request.getConnector().getId()).stream().forEach(e -> {
            AuthConfig authConfig = request.getConnector().getAuthConfig();
            try {
                restClient.delete(getAuthHeaders(authConfig), getPostUrl(request, e), authConfig);
                results.add(new Result(true, e.getId(), e.getSyncariEntityId()));
            } catch (NonRetriableException e1) {
                if (e1 instanceof NonRetriableException && 
                    ErrorCodes.TOO_MANY_REQUESTS.toString().equals(((NonRetriableException) e1).getErrorCode())) {
                    handleException(e1, request.getConnector());
                }
                log.error(ExceptionUtils.getStackTrace(e1));
                Result result = new Result(false, null, e.getSyncariEntityId()).setId(e.getId());
                result.addError(e1.getMessage());
                results.add(result);
                response.setSuccess(false);
            }
        });
        response.setResults(results);
        return response;
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new RuntimeException("createObject not supported in Freshsales yet");
    }

    @Override
    public void deleteObject(DeleteObjectRequest request) {

    }

    @Override
    public Map<String, String> getEntityMappings() {
        return new HashMap<>();
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    private JsonParserConfig getSingleJsonConfig(String plural) {
        return new JsonParserConfig(null, null, null, StringUtils.capitalize("Id"), true, null);
    }

    protected Optional<EntityData> getById(String url, SyncRequest request) {
        SyncariEntityDataRestClient restClient = getClient();
        try {
            String suffixVal = suffix.containsKey(request.getEntityName()) ? suffix.get(request.getEntityName()) : "";
            AuthConfig authConfig = request.getConnector().getAuthConfig();
            ResponseEntity<String> response = restClient.getResponse(getAuthHeaders(authConfig), url+suffixVal, authConfig);
            ReadContext context = JsonPath.parse(response.getBody());
            Map row = context.read(request.getEntityName());
            return Optional.of(extractRow(request, row));
        } catch (NonRetriableException | RetriableException e) {
            log.error(e.getMessage(), e);
            // handle a single getById 'NotFound' failure and skip it.
            if(ErrorCodes.BAD_ENDPOINT.name().equals(e.getErrorCode())){
                log.info("Skipping {} record corresponding to url {}", request.getEntityName(), url);
            }else {
                handleException(e, request.getConnector());
            }
        }
        return Optional.empty();
    }

    private EntityData extractRow(SyncRequest request, Map row) {
        EntityData data = new EntityData(request.getEntityName());
        data.setId(row.get("id").toString());
        data.setConnectorId(request.getConnector().getId());
        data.setLastModified(dateUtil.toEpochMilli(row.get("updated_at").toString()));
        data.setCreatedAt(dateUtil.toEpochMilli(row.get("created_at").toString()));
        if(row.containsKey("is_deleted")) {
            data.setDeleted(Boolean.parseBoolean(row.get("is_deleted").toString()));
        }
        row.forEach((k, v) -> {
            if (multivalued.containsKey(request.getEntityName().toLowerCase())
                    && multivalued.get(request.getEntityName().toLowerCase()).contains(k)) {
                List<Map> values = (List<Map>) v;
                List finalList = new ArrayList();
                values.forEach(value -> {
                    Map map = (Map) value;
                    if(map.containsKey("value")) {
                        finalList.add(map.get("value"));
                    } else if(map.containsKey("id")) {
                        finalList.add(map.get("id"));
                    }
                });
                data.addValue(k.toString(), finalList);
            } else if ((CUSTOM_FIELD.equalsIgnoreCase(k.toString()) || nested.containsKey(request.getEntityName().toLowerCase()))
                    && v instanceof Map) {
                Map values = (Map)v;
                values.forEach((key, val) -> {
                    // The company/deal data is flattened on lead. Skip the id field and add everything else
                    if (!"id".equalsIgnoreCase(key.toString())
                            && !(nestedFieldsExclusion.containsKey(k.toString())
                                    && nestedFieldsExclusion.get(k.toString())
                                            .contains(key.toString()))) {
                        data.addValue(getSyncariApiName(key.toString(), null, (String) k), handleValue(request.getEntitySchema(), key.toString(), val));
                    }
                });
            } else {
                if(v instanceof Map && ((Map)v).containsKey("id")) {
                    data.addValue(k.toString(), ((Map)v).get("id"));
                } else {
                    data.addValue(k.toString(), handleValue(request.getEntitySchema(), k.toString(), v));
                }
            }
        });
        // Freshsales has the concept of primary account for each contact. The API always returns the primary account in "sales_accounts" list.
        if (FreshsalesSeed.CONTACT.equalsIgnoreCase(request.getEntityName()) && row.containsKey("sales_accounts")) {
            List salesAccounts = (List) row.get("sales_accounts");
            if (!salesAccounts.isEmpty()) data.addValue("sales_account_id", ((Map)salesAccounts.get(0)).get("id"));
        }
        return data;
    }

    protected Object handleValue(EntitySchema schema, String key, Object value) {
        return schema.getField(key).map(field -> {
            if (field.getDataType().equalsIgnoreCase("date") && value != null) {
                // discard timezone from incoming and use date as it is
                try {
                    return LocalDate.parse(value.toString(), DateTimeFormatter.ISO_OFFSET_DATE_TIME).toString();
                } catch (DateTimeParseException ex) {
                    // try simple date format
                    try {
                        return LocalDate.parse(value.toString()).toString();
                    } catch (DateTimeParseException e) {
                        return value;
                    }
                }
            } else {
                if(field.isMultiValueField() && value instanceof String) {
                    return Arrays.asList(((String) value).split(";"));
                }
                return value;
            }
        }).orElse(value);
    }

    private Map updateMultivalue(EntityData e, Map map) {
        if ((e != null || e.getName() != null) &&  multivalued.containsKey(e.getName())) {
            for (String field : multivalued.get(e.getName())) {
                List<Multivalued> newValues = new ArrayList<>();
                if (map.containsKey(field)) {
                    String valuesString = map.get(field).toString();
                    List<String> values = new ArrayList<String>(Arrays.asList(valuesString.substring(1, valuesString.length() - 1).split(",")));
                    int counter = 0;
                    for (String value : values) {
                        newValues.add(getMultivalued(field, value, (counter == 0) ? true : false));
                        counter += 1;
                    }
                    map.remove(field);
                    map.put(field, newValues);
                }
            }
        }
        return map;
    }

    private Multivalued getMultivalued(String field, String fieldValue, boolean isPrimary) {
        String id = field.equals("sales_accounts") ? fieldValue : null;
        String value = !field.equals("sales_accounts") ? fieldValue : null;
        return new Multivalued(id, value, isPrimary);
    }

    private EntityData extractId(SyncRequest request, Map row) {
        EntityData data = new EntityData(request.getEntityName());
        data.setId(row.get("id").toString());
        data.setConnectorId(request.getConnector().getId());
        return data;
    }

    private String getHost(ConnectorInfo info) {
        if (info.getEndpoint().endsWith("/"))
            return info.getEndpoint();
        return info.getEndpoint() + "/";
    }

    private JsonParserConfig getSingleJsonConfig() {
        return new JsonParserConfig(null, null, null, StringUtils.capitalize("Id"), true, null);
    }

    private String getPostUrl(SyncRequest request, EntityData e) {
        return getHost(request.getConnector()) + crudMap.get(request.getEntityName())+"/"+e.getId();
    }

    private HttpHeaders getAuthHeaders(AuthConfig authConf) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        headers.set("Authorization", "Token token=" + authConf.getAccessToken());
        return headers;
    }

    public SyncariEntityDataRestClient getClient() {
        return new SyncariEntityDataRestClient(getSingleJsonConfig(), mapper);
    }

    private FreshsalesIterator getIterator(SyncRequest request, ValueHolder<String> lastOffset) {
        Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator = (wm, pageSize, offset) -> {
            if (offset != 0 && lastOffset.get() == null)
                return Pair.of(0L, new ArrayList<EntityData>().stream());
            AuthConfig authConfig = request.getConnector().getAuthConfig();
            String path = format(getHost(request.getConnector()) + postMap.get(request.getEntityName()), pageSize, offset == 0 ? 1 : offset);
            List<EntityData> result = new ArrayList<>();
            String wmStr = dateUtil.format(wm.getStart() == 0 ? Instant.EPOCH.toEpochMilli() : wm.getStart(),DateUtil.dateFormat);
            String wmEndStr = dateUtil.format(wm.getEnd() == 0 ? Instant.now().toEpochMilli() : wm.getEnd(), DateUtil.dateFormat);
            String host = getHost(request.getConnector());
            try {
                Map map = Map.of(
                        "filter_rule", List.of(
                                Map.of("attribute", "updated_at", "operator", "is_after", "value", wmStr),
                                Map.of("attribute", "updated_at", "operator", "is_before", "value", wmEndStr)
                        ),
                        "sort", "updated_at",
                        "sort_type", "asc",
                        "detail", true
                );
                log.info("Post Body {} ", mapper.writeValueAsString(map));
                ResponseEntity<String> res = getClient().postRaw(getAuthHeaders(authConfig), path,
                        mapper.writeValueAsString(map), request.getConnector().getAuthConfig());
                List rows = JsonPath.parse(res.getBody()).read(request.getEntityName()+"s");
                if (rows != null && rows.size() > 0) {
                    boolean unorderedResults = false;
                    long previousUpdatedAt = 0;
                    Map<String, Long> updatedAtById = new LinkedHashMap<>();
                    for (int i = 0; i < rows.size(); i++) {
                        EntityData e = extractRow(request, (Map) rows.get(i));

                        result.add(e);
                        if (previousUpdatedAt == 0) {
                            previousUpdatedAt = e.getLastModified();
                        } else {
                            if (previousUpdatedAt > e.getLastModified()) unorderedResults = true;
                            previousUpdatedAt = e.getLastModified();
                        }
                        updatedAtById.put(e.getId(), Long.valueOf(e.getLastModified()));

                    }
                    // This is not supposed to happen, log it so we can debug.
                    if (unorderedResults) {
                        log.error("Found unordered batch {}", updatedAtById);
                        // sort the result
                        result = result.stream().sorted(Comparator.comparingLong(EntityData::getLastModified)).collect(Collectors.toList());
                    }
                }
            } catch (QuotaExceededException | RetriableException e) {
                handleException(e, request.getConnector());
            } catch (Exception e) {
                log.error(ExceptionUtils.getStackTrace(e));
                handleException(e, request.getConnector());
            }
            Response response = new Response(String.valueOf(offset+1), result);
            lastOffset.set(response.getOffset());
            return Pair.of(Long.valueOf(response.getRecords().size()), response.getRecords().stream());
        };
        int pageSize = request.getPageSize() == 0 ? DEFAULT_PAGE_SIZE : Math.min(request.getPageSize(), DEFAULT_PAGE_SIZE);

        FreshsalesIterator iterator = new FreshsalesIterator(request.getWatermark(),
                request.getWatermark().getOffset(), generator, new ArrayList<>(),
                request.getEntitySchema().getWatermarkField(), pageSize, request.getWatermark().getLimit());
        return iterator;
    }


    private FreshsalesIterator getDeletedIterator(SyncRequest request, ValueHolder<String> lastOffset) {
        Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator = (wm, pageSize, offset) -> {
            if (offset != 0 && lastOffset.get() == null)
                return Pair.of(0L, new ArrayList<EntityData>().stream());
            AuthConfig authConfig = request.getConnector().getAuthConfig();
            String path = format(getHost(request.getConnector()) + postMap.get(request.getEntityName()), pageSize, offset == 0 ? 1 : offset);
            List<EntityData> result = new ArrayList<>();
            String wmStr = dateUtil.format(wm.getStart() == 0 ? Instant.EPOCH.toEpochMilli() : wm.getStart(),DateUtil.dateFormat);
            String wmEndStr = dateUtil.format(wm.getEnd() == 0 ? Instant.now().toEpochMilli() : wm.getEnd(), DateUtil.dateFormat);
            String host = getHost(request.getConnector());
            try {
                Map map = Map.of(
                        "filter_rule", List.of(
                                Map.of("attribute", "deleted", "operator", "is", "value", 1),
                                Map.of("attribute", "updated_at", "operator", "is_after", "value", wmStr),
                                Map.of("attribute", "updated_at", "operator", "is_before", "value", wmEndStr)
                        ),
                        "sort", "updated_at",
                        "sort_type", "asc",
                        "detail", true
                );
                log.info("Post Body {} ", mapper.writeValueAsString(map));
                ResponseEntity<String> res = getClient().postRaw(getAuthHeaders(authConfig), path,
                        mapper.writeValueAsString(map), request.getConnector().getAuthConfig());
                List rows = JsonPath.parse(res.getBody()).read(request.getEntityName()+"s");
                if (rows != null && rows.size() > 0) {
                    boolean unorderedResults = false;
                    long previousUpdatedAt = 0;
                    Map<String, Long> updatedAtById = new LinkedHashMap<>();
                    for (int i = 0; i < rows.size(); i++) {
                        EntityData e = extractRow(request, (Map) rows.get(i));

                        result.add(e);
                        if (previousUpdatedAt == 0) {
                            previousUpdatedAt = e.getLastModified();
                        } else {
                            if (previousUpdatedAt > e.getLastModified()) unorderedResults = true;
                            previousUpdatedAt = e.getLastModified();
                        }
                        updatedAtById.put(e.getId(), Long.valueOf(e.getLastModified()));

                    }
                    // This is not supposed to happen, log it so we can debug.
                    if (unorderedResults) {
                        log.error("Found unordered batch {}", updatedAtById);
                        // sort the result
                        result = result.stream().sorted(Comparator.comparingLong(EntityData::getLastModified)).collect(Collectors.toList());
                    }
                }
            } catch (QuotaExceededException | RetriableException e) {
                handleException(e, request.getConnector());
            } catch (Exception e) {
                log.error(ExceptionUtils.getStackTrace(e));
                handleException(e, request.getConnector());
            }
            Response response = new Response(String.valueOf(offset+1), result);
            lastOffset.set(response.getOffset());
            return Pair.of(Long.valueOf(response.getRecords().size()), response.getRecords().stream());
        };
        int pageSize = request.getPageSize() == 0 ? DEFAULT_PAGE_SIZE : Math.min(request.getPageSize(), DEFAULT_PAGE_SIZE);

        FreshsalesIterator iterator = new FreshsalesIterator(request.getWatermark(),
                request.getWatermark().getOffset(), generator, new ArrayList<>(),
                request.getEntitySchema().getWatermarkField(), pageSize, request.getWatermark().getLimit());
        return iterator;
    }



    private void handleException(Exception e, ConnectorInfo connector) {
        // handle quota exceeded exception
        if (e instanceof RetriableException && ErrorCodes.TOO_MANY_REQUESTS.name().equals(((RetriableException) e).getErrorCode())) {
            throw new QuotaExceededException(ErrorCodes.TOO_MANY_REQUESTS.name(),
                    ErrorCodes.TOO_MANY_REQUESTS.name(), ErrorCodes.TOO_MANY_REQUESTS.name(),
                    connector.getId(), DateUtil.getSecondsToNextHour());
        }
        throw new RuntimeException(e);
    }
}

@Data
@AllArgsConstructor
class Multivalued implements Serializable {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String id;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String value;
    @JsonProperty("is_primary")
    private boolean isPrimary;
}

class FreshsalesIterator extends DefaultDataIterator {

    // SYN-4493 Customers can have the minimum tier for pulling data from freshsales. Example. 1000/hour
    // The framework has a default of 2K/sync cycle. Here we reduce it to 400 to make progress for each sync cycle.
    // 400 in order to accomodate at least 2 batches (800 records) including schema calls and getRecords/getByIds calls.
    public static final int MAX_RECORDS_PER_ENTITY_PER_SYNC_CYCLE = 400;

    public FreshsalesIterator(WatermarkInfo baseWatermark, long offset, Function3<WatermarkInfo, Integer, Long,
            Pair<Long, Stream<EntityData>>> generator, List<EntityData> data, AttributeSchema watermarkField,int pageSize, int maxRecords) {
        super(baseWatermark, offset, generator, data, watermarkField,pageSize,maxRecords);
    }

    @Override
    protected boolean isLastPage() {
        // Freshsales can return less number of records/page within a page. We cannot rely on that to end pagination.
        // Here we look for data to be zero within a page to ensure we keep moving when that discrepency happens.
        // Note, it is OK to process same records in two pages, since we order by updated_at and reprocessing it not an issue.
        return data.size() == 0;
    }

    protected long nextOffset(Pair<Long, Stream<EntityData>> results, List<EntityData> data) {
        // if no data is retrieved meaning the window is exhausted - reset the offset
        if(data.isEmpty()) return 0;
        return (offset == 0 ? 1 : offset) + 1; // increment the offset
    }

    @Override
    public long getLastOffset() {
        return offset;
    }

    @Override
    public Offset getOffsetInfo() {
        return new Offset(OffsetType.PAGE_NUMBER, getEffectivePageSize());
    }

    @Override
    public int getMaxRecordsPerEntitySyncCycle() {
        return MAX_RECORDS_PER_ENTITY_PER_SYNC_CYCLE;
    }
}