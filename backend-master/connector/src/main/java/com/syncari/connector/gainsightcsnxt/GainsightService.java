package com.syncari.connector.gainsightcsnxt;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jooq.lambda.function.Function3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.Status;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.AuthField;
import com.syncari.connector.data.AuthMetadata;
import com.syncari.connector.data.CreateFieldRequest;
import com.syncari.connector.data.CreateObjectRequest;
import com.syncari.connector.data.DataWithOffset;
import com.syncari.connector.data.DeleteFieldRequest;
import com.syncari.connector.data.DescribeAllRequest;
import com.syncari.connector.data.DescribeRequest;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.FetchResponse;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.SyncResponse;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.connector.data.UIMetadata;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.connector.data.iterator.DefaultDataOffsetIterator;
import com.syncari.connector.service.Transformer;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.SynapseInfoService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component(Constants.GAINSIGHTCS)
public class GainsightService implements AuthenticationService, CommonDataService, MetadataService, SynapseInfoService {
    @Autowired
    Transformer transformer;
    @Autowired
    ObjectMapper mapper;

    // Connector level constants
    public static final String SERVICE_ENDPOINT = "endpoint";
    public static final String ACCESS_KEY_HEADER = "accesskey";

    protected static final int API_MAX_PAGESIZE = 100;
    protected static final int CUD_API_MAX_RECORDS = 50;
    
    // Describe object level constants
    private static final String DESC_OBJECTS_POST_URL = "%s/v1/meta/services/objects/";
    private static final String DESC_OBJECTS_POST_BODY = "{\"externalUse\":\"true\",\"excludeMappings\":\"false\",\"sortByLabel\":\"true\"}";
    private static final String DESC_OBJECT_GET_URL = "%s/v1/meta/services/objects/%s/describe?ci=mda&idd=true";
    private static final List<String> NON_SUPPORTED_OBJECTS = List.of("email_logs", "survey_text_analytics", "user_shared_detail");

    // Query constants
    private static final String QUERY_POST_URL = "%s/v1/data/objects/query/";
    private static final String GET_BY_WATERMARK_POST_BODY_TMPL = "{\"select\": [%s], "
        + "\"where\": {\"conditions\":[{\"name\":\"ModifiedDate\",\"alias\":\"A\",\"value\":[\"%s\",\"%s\"],\"operator\":\"BTW\"}],\"expression\":\"A\"},"
        + "\"orderBy\": { \"ModifiedDate\": \"asc\"},"
        + "\"limit\": %d,"
        + "\"offset\": %d }";

    // CRUD APIs URL
    private static final String CRUD_URL = "%s/v1/data/objects/%s";
    private static final String CRUD_ID_URL = "%s/v1/data/objects/%s/%s";
    private static final String USER_CRUD_URL = "%s/v1/users/services";

    // attribute level constants
    public static final String ID_FIELD = "Gsid";

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getApiKey());
    }

    @Override
    public List<AuthField> getConfigureFields() {
        return List.of(ConnectorHelper.getEndpointField(), ConnectorHelper.getSupportedAuthPicker());
    }

    private JsonParserConfig getSingleJsonConfig() {
        return new JsonParserConfig(null, null, null, StringUtils.capitalize("Gsid"), true, null);
    }

    protected GainsightRestClient getClient(AuthConfig config) {
        config.addHeader(ACCESS_KEY_HEADER, config.getAccessToken());
        return new GainsightRestClient(getSingleJsonConfig(), mapper);
    }

    public String getAuthHost(AuthConfig config) {
        return config.getEndpoint();
    }

    private String getRequestURL(ConnectorInfo connector, String url) {
        return String.format(url, connector.getEndpoint());
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse result = new TestConnectionResponse();
        try {
            ResponseEntity<String> data = getClient(config.getAuthConfig()).postRaw(getRequestURL(config, DESC_OBJECTS_POST_URL), 
                DESC_OBJECTS_POST_BODY, config.getAuthConfig());
            log.debug("Data received " + data);
        } catch (Exception e) {
            handleAuthenticationErrorMessage(result, e);
        }
        return result;
    }

    @Override
    public String getCategory() {
        return "Customer Support";
    }
    
    @Override
    public String getName() {
        return Constants.GAINSIGHTCS;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/gainsight.svg")
                .setDisplayName("Gainsight CS NXT")
                .setBackgroundColor("#F2FCFF")
                .setHelpUrl(helpArticlesBaseUrl + "/4406177991828-Gainsight-CS-NXT-Setup");
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        Function3<WatermarkInfo, Integer, Long, DataWithOffset> generator = (wm, pageSize, offset) -> {
            String start = ZonedDateTime.ofInstant(Instant.ofEpochMilli(wm.getStart()), ZoneOffset.UTC).toString();
            String end = ZonedDateTime.ofInstant(Instant.ofEpochMilli(wm.getEnd()), ZoneOffset.UTC).toString();
            String url = getRequestURL(request.getConnector(), QUERY_POST_URL) + request.getEntityName().toLowerCase();
            List<AttributeSchema> attributesToQuery = request.getEntitySchema().getAttributes();
            String fields = "\"" + String.join("\",\"", attributesToQuery.stream().filter(a -> !a.isIdField() && a.getStatus() == Status.ACTIVE)
                .map(a -> a.getApiName()).collect(Collectors.toList())) + 
                "\",\"" + request.getEntitySchema().getIdField().getApiName() + "\"";
            String postBody = String.format(GET_BY_WATERMARK_POST_BODY_TMPL, fields, start, end, pageSize, offset);
            log.info(postBody);
            return post(url, postBody, offset, request);
        };

        int pgSize = (request.getPageSize() <= 0) ? API_MAX_PAGESIZE : request.getPageSize();

        DefaultDataOffsetIterator iterator = new DefaultDataOffsetIterator(request.getWatermark(),
                request.getWatermark().getOffset(), generator, new ArrayList<>(),
                request.getEntitySchema().getWatermarkField(), pgSize, request.getWatermark().getLimit());
        return new FetchResponse(request.getWatermark(), iterator);
    }

    private DataWithOffset post(String url, String postBody, Long prevOffset, SyncRequest request) {
        return getClient(request.getConnector().getAuthConfig())
            .getDataWithOffset(url, postBody, prevOffset, request);
    }

    private DataWithOffset get(String url, SyncRequest request) {
        return getClient(request.getConnector().getAuthConfig()).getData(url, 0l, request);
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        List<EntityData> data = new ArrayList<>();
        request.getIds().forEach(id -> {
            String url = String.format(CRUD_ID_URL, request.getConnector().getEndpoint(), request.getEntityName().toLowerCase(), id);
            data.addAll(get(url, request).getData());
        });
        return data;
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return Instant.EPOCH.toEpochMilli();
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        String url = request.getEntityName().equalsIgnoreCase("gsuser") ? String.format(USER_CRUD_URL, request.getConnector().getEndpoint()) :
                String.format(CRUD_URL, request.getConnector().getEndpoint(), request.getEntityName().toLowerCase());
        return getClient(request.getConnector().getAuthConfig()).postRecords(url, HttpMethod.POST, request);
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        String url = request.getEntityName().equalsIgnoreCase("gsuser") ? String.format(USER_CRUD_URL, request.getConnector().getEndpoint()) :
                String.format(CRUD_URL, request.getConnector().getEndpoint(), request.getEntityName().toLowerCase());
        return getClient(request.getConnector().getAuthConfig()).postRecords(url, HttpMethod.PUT, request);
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        String url = request.getEntityName().equalsIgnoreCase("gsuser") ? USER_CRUD_URL : CRUD_ID_URL;
        return getClient(request.getConnector().getAuthConfig()).deletedRecords(url, request);
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19203455280148";
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        List<EntitySchema> entitySchemas = describeAll(new DescribeAllRequest(request.getConnector(), List.of(request.getEntity())));
        if (entitySchemas.isEmpty()) return Optional.empty();
        return Optional.of(entitySchemas.get(0));
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        ResponseEntity<String> data = getClient(request.getConnector().getAuthConfig()).postRaw(
            getRequestURL(request.getConnector(), DESC_OBJECTS_POST_URL), DESC_OBJECTS_POST_BODY, request.getConnector().getAuthConfig());
        return toEntitySchemas(request, data);
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        throw new RuntimeException(this.getUIMetadata().getDisplayName() + " does not support create field");
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new RuntimeException(this.getUIMetadata().getDisplayName() + " does not support delete field");
    }
    
    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new RuntimeException("createObject not supported in " + this.getUIMetadata().getDisplayName()  + " yet");
    }

    @Override
    public Map<String, String> getEntityMappings() {
        return new HashMap<>();
    }
    
    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    private List<EntitySchema> toEntitySchemas(DescribeAllRequest request, ResponseEntity<String> resp) {
        List<EntitySchema> entitySchemas = new ArrayList<>();
        if (resp.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException(resp.getBody());
        }
        Map respMap;
        try {
            respMap = mapper.readValue(resp.getBody(), Map.class);
        } catch (JsonProcessingException e1) {
            throw new RuntimeException("Failed to read entities.", e1);
        }
        List<Map<String, Object>> rawSchemas = (ArrayList<Map<String, Object>>) respMap.get("data");
        rawSchemas.forEach(x -> {
            if ("SYSTEM".equalsIgnoreCase(x.get("objectType").toString())) return;
            String objectName = x.get("objectName").toString();
            // Some objects are not supported.
            if (NON_SUPPORTED_OBJECTS.contains(objectName.toLowerCase())) return;
            if (CollectionUtils.isNotEmpty(request.getEntities()) && 
                !request.getEntities().contains(objectName)) return;
            
            String fieldsURL = String.format(DESC_OBJECT_GET_URL, request.getConnector().getEndpoint(), objectName);
            ResponseEntity<String> fieldResp = getClient(request.getConnector().getAuthConfig())
                .getResponse(fieldsURL, request.getConnector().getAuthConfig());
            if (fieldResp.getStatusCode() != HttpStatus.OK) {
                log.error("fieldResp received " + fieldResp);
                throw new RuntimeException(String.format("Failed to get fields for entity %s due to %s", objectName, fieldResp));
            }

            Map fieldRespMap;
            try {
                fieldRespMap = mapper.readValue(fieldResp.getBody(), Map.class);
            } catch (JsonProcessingException e1) {
                throw new RuntimeException("Failed to read detailed object response.", e1);
            }
            
            List<Map<String, Object>> datas = (ArrayList<Map<String, Object>>) fieldRespMap.get("data");
            Map<String, Object> entitySchemaWithFields = datas.get(0);
            EntitySchema entitySchema = toEntitySchema(entitySchemaWithFields);
            List<Map<String, Object>> rawFieldSchemas = (ArrayList<Map<String, Object>>) entitySchemaWithFields.get("fields");
            log.debug("raw:\n " +rawFieldSchemas);
            rawFieldSchemas.forEach(y -> {
                entitySchema.addField(toAttributeSchema(y));
            });
            entitySchemas.add(entitySchema);
        });
        return entitySchemas;
    }

    private EntitySchema toEntitySchema(Map<String, Object> schemaRawValue) {
        return new EntitySchema(schemaRawValue.get("objectName").toString())
            .setDisplayName(schemaRawValue.get("label").toString())
            .setPluralName(schemaRawValue.get("labelPlural").toString())
            .setDescription(schemaRawValue.get("description").toString())
            .setReadOnly(!(Boolean) schemaRawValue.get("createable"));
    }

    private AttributeSchema toAttributeSchema(Map<String, Object> fieldRawValue) {
        log.debug("fieldRawValue {} ", fieldRawValue);
        AttributeSchema field = new AttributeSchema(fieldRawValue.get("fieldName").toString(), fieldRawValue.get("dataType").toString().toLowerCase())
            .setDisplayName(fieldRawValue.get("label").toString());
        
        Map<String, Object> fieldMeta = (Map<String, Object>) fieldRawValue.get("meta");
        field.setNillable((Boolean) fieldMeta.get("nillable"));
        field.setLength(fieldMeta.containsKey("length") ? Integer.parseInt(fieldMeta.get("length").toString()) : 0);
        field.setScale(fieldMeta.containsKey("decimalPlaces") ? Integer.parseInt(fieldMeta.get("decimalPlaces").toString()) : 0);

        field.setInitializable(fieldMeta.containsKey("createable") ? (Boolean) fieldMeta.get("createable") : false);
        field.setUpdateable(fieldMeta.containsKey("updateable") ? (Boolean) fieldMeta.get("updateable") : false);
        field.setCalculated(fieldMeta.containsKey("formulaField") ? (Boolean) fieldMeta.get("formulaField") : false);
        if (fieldMeta.containsKey("defaultValue")) {
            field.setDefaultValue(fieldMeta.get("defaultValue").toString());
        }

        if (field.getScale() > 0) {
            field.setPrecision(field.getLength());
        }

        switch (field.getApiName()) {
            case "ModifiedDate":
                field.setWatermarkField(true);
                field.setUpdatedAtField(true);
                field.setNillable(false);
                field.setUpdateable(false);
                field.setSystem(true);
                break;
            case "CreatedDate":
                field.setCreatedAtField(true);
                field.setSystem(true);
                break;
            case ID_FIELD:
                field.setIdField(true);
                field.setNillable(false);
                field.setUnique(true);
                field.setUpdateable(false);
                field.setSystem(true);
                break;
            case "CreatedBy":
            case "ModifiedBy":
                field.setSystem(true);
                break;
            default:
                break;
        }

        switch (field.getDataType()) {
            case "lookup":
                Map<String, Object> lookup = (LinkedHashMap) fieldMeta.get("lookupDetail");
                if (lookup.containsKey("lookupObjects")) {
                    Map<String, Object> lookupRefDetails = ((List<LinkedHashMap>) lookup.get("lookupObjects")).get(0);
                    field.setDataType("reference");
                    field.setReferenceTo(lookupRefDetails.get("objectName").toString());
                    field.setReferenceTargetField(lookup.containsKey("fieldDBName") ? lookup.get("fieldDBName").toString() : ID_FIELD);
                } else {
                    field.setDataType("string");
                }
                break;
            case "multiselectdropdownlist":
            case "picklist":
                List<String> pickListValues = new ArrayList();
                List<Map<String, Object>> plValues = (List) fieldRawValue.get("options");
                plValues.forEach(x -> pickListValues.add(x.get("value").toString()));
                field.setPicklistValues(pickListValues);
                if ("multiselectdropdownlist".equalsIgnoreCase(field.getDataType())) field.setMultiValueField(true);
                break;
            case "sfdcid":
                field.setDataType("string");
            default:
                break;
        }

        return field;
    }
    
}
