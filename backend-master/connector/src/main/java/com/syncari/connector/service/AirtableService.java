package com.syncari.connector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.DefaultDataIterator;
import com.syncari.connector.exception.ConnectorException;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.connector.service.def.*;
import com.syncari.utils.DateUtil;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jooq.lambda.function.Function3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

@Slf4j
@Component(Constants.AIRTABLE)
public class AirtableService implements CommonDataService, MetadataService, SynapseInfoService, AuthenticationService, OauthAuthenticationService {
    private static final int LONG_TEXT = 32000;
    private static final int DEFAULT_WRITE_BATCH_SIZE = 10;
    public static final String AIRTABLE_INTERNAL_ID = "_airtable_internal_id";
    @Autowired
    Transformer transformer;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    DateUtil dateUtil;
    @Autowired
    DefaultAuthTokenHandler tokenHandler;
    private static final String HOST = "https://api.airtable.com";
    private static final String QUERY_PATH = HOST + "/v0/%s/%s?filterByFormula={condition}&pageSize=%s&sort[0][field]=%s%s";
    private static final String WRITE_PATH = HOST + "/v0/%s/%s";
    private static final String DELETE_PATH = HOST + "/v0/%s/%s/%s";
    ///v0/<baseId>/<table display name>/<recordId>
    private static final String ID_QUERY_PATH = HOST + "/v0/%s/%s/%s";
    private static final String QUERY = "IS_AFTER({%s},'%s')";
    private static final String DESCRIBE_OAUTH = HOST + "/v0/meta/bases/%s/tables";

    private static final String OAUTH_URL = "https://airtable.com/oauth2/v1/token";
    private static final String OAUTH_HOST = "https://airtable.com";
    private static final String OAUTH_REDIRECT_URI = "/oauth2/v1/authorize?client_id={{client_id}}&redirect_uri={{redirect_uri}}" +
            "&response_type=code&state={{state}}&scope=schema.bases:read data.records:read data.records:write&code_challenge_method=S256&" +
            "code_challenge={{code_challenge}}";

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getApiKey(), ConnectorHelper.getAccessTokenOauthType());
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19203185811988";
    }

    @Override
    public List<AuthField> getConfigureFields() {
        AuthField baseId = new AuthField();
        baseId.setDataType("text");
        baseId.setName("baseId");
        baseId.setLabel("Base Id");
        baseId.setHelpSummary("The id of the Base which holds the metadata");
        return List.of(baseId, ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public List<Capability> getCapabilities() {
        var capabilities = new ArrayList<Capability>();
        capabilities.add(Capability.schemaEditInSyncari);
        capabilities.add(Capability.userEditableId);
        capabilities.add(Capability.userEditableWm);
        return capabilities;
    }
    
    @Override
    public String getCategory() {
        return "Productivity";
    }
    
    @Override
    public String getName() {
        return Constants.AIRTABLE;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/airtable.svg")
                .setDisplayName("Airtable")
                .setBackgroundColor("#F9F9F9")
                .setHelpUrl(helpArticlesBaseUrl + "/360052753052-Airtable-Setup");
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        ValueHolder<String> lastOffset = new ValueHolder<>();
        Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator = (wm, pageSize,
                offset) -> {
            if(offset != 0 && lastOffset.get() == null) return Pair.of(0L, new ArrayList<EntityData>().stream());
            String wmField = request.getEntitySchema().getWatermarkField().getDisplayName();
            String wmDate = dateUtil.formatDate(Instant.ofEpochMilli(wm.getStart()), DateUtil.dateFormatMillis);
            String condition = format(QUERY, wmField, wmDate);
            String offsetPart = lastOffset.get()!=null ? "&offset="+lastOffset.get() : "";
            String path = format(QUERY_PATH, getBaseId(request.getConnector()), request.getEntitySchema().getDisplayName(), pageSize, wmField, offsetPart);
            Response response = get(path, request, condition);
            lastOffset.set(response.getOffset());
            return Pair.of(Long.valueOf(response.getRecords().size()), response.getRecords().stream());
        };

        int pageSize = request.getPageSize() == 0 || request.getPageSize() > 100 ? 100 : request.getPageSize();
        DefaultDataIterator iterator = new DefaultDataIterator(request.getWatermark(),
                request.getWatermark().getOffset(), generator, new ArrayList<>(),
                request.getEntitySchema().getWatermarkField(), pageSize, request.getWatermark().getLimit());
        return new FetchResponse(request.getWatermark(), iterator);
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        DescribeAllRequest req = new DescribeAllRequest(request.getConnector(), List.of());
        return describeAll(req).stream().filter(e -> e.getApiName().equalsIgnoreCase(request.getEntity())).findFirst();
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        List<EntitySchema> schemaList = new ArrayList<>();
        SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient(getSingleJsonConfig(""), mapper);
        AuthConfig authConfig = request.getConnector().getAuthConfig();
        String authType = request.getConnector().getMetaConfig().getOrDefault("authType", AuthType.ApiKey).toString();
        String describeURL = DESCRIBE_OAUTH;
        String url = String.format(describeURL, getBaseId(request.getConnector()));
        ResponseEntity<String> response = restClient.getResponse(url, authConfig);
        ReadContext responseBody = JsonPath.parse(response.getBody());
        List<Map> rows = responseBody.read("tables");
        List<String> skipList = List.of("button");
        for (Map map : rows) {
            EntitySchema entitySchema = new EntitySchema(map.get("id").toString(), map.get("name").toString());
            List<Map> fields = (List<Map>) map.get("fields");
            for (Map field : fields) {
                String dataType = field.get("type").toString();
                String referenceTo = getReferenceTo(field);
                String referenceTargetField = referenceTo==null?null: AIRTABLE_INTERNAL_ID;
                if(skipList.contains(dataType)) {
                    continue;
                }
                AttributeSchema attr = new AttributeSchema();
                if("multilinetext".equalsIgnoreCase(dataType) || "singlelinetext".equalsIgnoreCase(dataType) || "lookup".equalsIgnoreCase(dataType)) {
                    attr.setLength(LONG_TEXT);
                }
                if("lookup".equalsIgnoreCase(dataType)) {
                    dataType = "string";
                }else if("formula".equalsIgnoreCase(dataType)) {
                    // The formula value could be string or number or date
                    dataType = "object";
                }else if("multipleRecordLinks".equalsIgnoreCase(dataType)) {
                    dataType = "reference";
                    //We cannot distinguish b/w multi-valued record links vs single ones
                    attr.setMultiValueField(true);
                }else if("multipleSelects".equalsIgnoreCase(dataType)) {
                    dataType = "string";
                    attr.setMultiValueField(true);
                }
                attr.setApiName(field.get("id").toString());
                attr.setDataType(dataType);
                attr.setReferenceTargetField(referenceTargetField);
                attr.setReferenceTo(referenceTo);
                String displayName = field.get("name").toString();
                attr.setDisplayName(displayName);
                entitySchema.addField(attr);
                if("lastModifiedTime".equalsIgnoreCase(dataType)) {
                    attr.setWatermarkField(true);
                    attr.setUpdateable(false);
                    attr.setDataType("datetime");
                }
                if("id".equalsIgnoreCase(displayName)) {
                    attr.setIdField(true);
                    attr.setUpdateable(false);
                }
            }
            AttributeSchema idField = new AttributeSchema(AIRTABLE_INTERNAL_ID, "string").setDisplayName("Record Id")
                    .setIdField(true).setSystem(true).setStatus(Status.ACTIVE).setUpdateable(false).setNillable(false);
            entitySchema.addField(idField);
            schemaList.add(entitySchema);
        }
        return schemaList;
    }
    
    private String getReferenceTo(Map field) {
        Object linkedTableId = null;
        if(field.containsKey("options")){
            linkedTableId = ((Map) field.getOrDefault("options",Map.of())).get("linkedTableId");
        } else if (field.containsKey("config")) {
            Map config = (Map) field.get("config");
            if(config.containsKey("options")) {
                linkedTableId = ((Map) config.getOrDefault("options", Map.of())).get("linkedTableId");
            }
        }
        return linkedTableId==null? null: linkedTableId.toString();

    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse result = new TestConnectionResponse();
        try {
            if(!config.getMetaConfig().containsKey("baseId") || StringUtils.isBlank(config.getMetaConfig().get("baseId").toString())) {
                throw new RuntimeException("Base id is required");
            }
            DescribeAllRequest request = new DescribeAllRequest(config, List.of());
            describeAll(request);
        } catch (ConnectorException e) {
            if(ErrorCodes.BAD_ENDPOINT.name().equalsIgnoreCase(e.getErrorCode())) {
                result.setMessage(format(i18n("airtable_base_not_found"), config.getMetaConfig().get("baseId").toString()));
                result.setCode(HttpStatus.NOT_FOUND.name());
                return result;
            }
            result.setMessage(e.getMessage());
            result.setCode(HttpStatus.UNAUTHORIZED.name());
        } catch (Exception e) {
            result.setMessage(e.getMessage());
            result.setCode(HttpStatus.UNAUTHORIZED.name());
        }
        return result;
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        throw new RuntimeException("Airtable does not support create field");
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new RuntimeException("Airtable does not support delete field");
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        String entityName = request.getEntitySchema().getDisplayName();
        List<EntityData> records = new ArrayList<>();
        request.getData().forEach((connectorId, ids)->{
            ids.forEach(id->{
                String url = format(ID_QUERY_PATH, getBaseId(request.getConnector()), entityName, id.getId());
                getById(url,request).ifPresent(record->{
                    records.add(record);
                });
            });
        });
        return records;
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        SyncResponse response = new SyncResponse();
        if(request.getEntitySchema() == null) throw new RuntimeException("Entity Schema cannot be empty");
        List<Result> results = new ArrayList<>();
        SyncariEntityDataRestClient restClient = getClient();
        AuthConfig authConfig = request.getConnector().getAuthConfig();

        List<List<EntityData>> partitions = Lists.partition(request.getData().get(request.getConnector().getId()), DEFAULT_WRITE_BATCH_SIZE);
        for (List<EntityData> list: partitions) {
            List<Map> records = new ArrayList<>();
            list.stream().forEach(e -> {
                records.add(Map.of("fields", transform(e.getValues(), request.getEntitySchema())));
            });
            try {
                ResponseEntity<String> resp = restClient.postRaw(restClient.getHeaders(authConfig),
                        String.format(WRITE_PATH, getBaseId(request.getConnector()),
                                request.getEntitySchema().getDisplayName()),
                        mapper.writeValueAsString(Map.of("records", records)), authConfig);
                extractResults(results, list, resp);
            } catch (Exception e1) {
                log.error(ExceptionUtils.getStackTrace(e1));
                Result result = new Result(false, null);
                result.addError(e1.getMessage());
                results.add(result);
            }
        }
        response.setResults(results);
        return response;
    }
    
    @Override
    public SyncResponse update(SyncRequest request) {
        SyncResponse response = new SyncResponse();
        if(request.getEntitySchema() == null) throw new RuntimeException("Entity Schema cannot be empty");
        List<Result> results = new ArrayList<>();
        SyncariEntityDataRestClient restClient = getClient();
        AuthConfig authConfig = request.getConnector().getAuthConfig();
        List<List<EntityData>> partitions = Lists.partition(request.getData().get(request.getConnector().getId()), DEFAULT_WRITE_BATCH_SIZE);
        for (List<EntityData> list: partitions) {
            List<Map> records = new ArrayList<>();
            list.stream().forEach(e -> {
                records.add(Map.of("id", e.getId(), "fields", transform(e.getValues(), request.getEntitySchema())));
            });
            try {
                ResponseEntity<String> resp = restClient.patch(restClient.getHeaders(authConfig),
                        String.format(WRITE_PATH, getBaseId(request.getConnector()),
                                request.getEntitySchema().getDisplayName()),
                        mapper.writeValueAsString(Map.of("records", records)), authConfig);
                extractResults(results, list, resp);
            } catch (Exception e1) {
                log.error(ExceptionUtils.getStackTrace(e1));
                Result result = new Result(false, null);
                result.addError(e1.getMessage());
                results.add(result);
            }
        }
        response.setResults(results);
        return response;
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        SyncResponse response = new SyncResponse();
        List<Result> results = new ArrayList<>();
        SyncariEntityDataRestClient restClient = getClient();
        AuthConfig authConfig = request.getConnector().getAuthConfig();
        List<EntityData> list = request.getData().get(request.getConnector().getId());
        list.stream().forEach(l -> {
            try {
                restClient.delete(restClient.getHeaders(authConfig), String.format(DELETE_PATH,
                        getBaseId(request.getConnector()), request.getEntitySchema().getDisplayName(), l.getId()),
                        authConfig);
                Result result = new Result(true, l.getId(), l.getSyncariEntityId());
                results.add(result);
            } catch (Exception e1) {
                log.error(ExceptionUtils.getStackTrace(e1));
                Result result = new Result(false, null);
                result.addError(e1.getMessage());
                results.add(result);
            }
        });
        response.setResults(results);
        return response;
    }
    
    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new RuntimeException("createObject not supported in airtable yet");
    }
    
    @Override
    public void deleteObject(DeleteObjectRequest request) {
        throw new RuntimeException("deleteObject not supported in airtable yet");
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
    
    private Response get(String url, SyncRequest request,Object...uriArgs) {
        List<EntityData> result = new ArrayList<>();
        SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient(getSingleJsonConfig(""), mapper);
        ResponseEntity<String> sheetResponse = restClient.getResponse(url, request.getConnector().getAuthConfig(),uriArgs);
        ReadContext sheetCtx = JsonPath.parse(sheetResponse.getBody());
        List rows = sheetCtx.read("records");
        String offset = null;
        try {
            offset = sheetCtx.read("offset");
        } catch (Exception e) {
        }
        
        List<Pair<String, String>> headerRows = request.getEntitySchema().getAttributes().stream()
                .map(m -> Pair.of(m.getApiName(), m.getDisplayName())).collect(Collectors.toList());
        if (rows != null && rows.size() > 0) {
            for (int i = 0; i < rows.size(); i++) {
                Map row = (Map) rows.get(i);
                EntityData data = createRecord(request, headerRows, row);
                result.add(data);
            }
        }
        return new Response(offset, result);
    }
    private Optional<EntityData> getById(String url, SyncRequest request) {
        SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient(getSingleJsonConfig(""), mapper);
        try {
            ResponseEntity<String> sheetResponse = restClient.getResponse(url, request.getConnector().getAuthConfig());
            ReadContext sheetCtx = JsonPath.parse(sheetResponse.getBody());
            Map row = sheetCtx.read("$");
            List<Pair<String, String>> headerRows = request.getEntitySchema().getAttributes().stream()
                    .map(m -> Pair.of(m.getApiName(), m.getDisplayName())).collect(Collectors.toList());
            if (row != null && !row.isEmpty()) {
                return Optional.ofNullable(createRecord(request, headerRows, row));
            }
        }catch(NonRetriableException e){
            log.error("Could not find airtable record with URL "+url,e);
        }
        return Optional.empty();
    }

    private EntityData createRecord(SyncRequest request, List<Pair<String, String>> headerRows, Map row) {
        EntityData data = new EntityData(request.getEntityName());
        data.setId(row.get("id").toString());
        if(request.getEntitySchema().hasIdField()) {
            data.addValue(request.getEntitySchema().getIdField().getApiName(), data.getId());
        }
        data.setConnectorId(request.getConnector().getId());
        Map fields = (Map) row.get("fields");
        AttributeSchema watermarkField = request.getEntitySchema().getWatermarkField();
        for (int j = 0; j < headerRows.size(); j++) {
            String key = headerRows.get(j).y;
            String apiName = headerRows.get(j).x;
            Object value = fields.get(key);
            if(watermarkField.getDisplayName().equalsIgnoreCase(key)) {
                data.setLastModified(value == null || StringUtils.isBlank(value.toString())
                        ? ZonedDateTime.now().toInstant().toEpochMilli()
                        : dateUtil.toEpochMilli(value.toString()));
            } else {
                request.getEntitySchema().getField(apiName).ifPresent(field->{
                    //flatten the list to comma separated value for all non-reference fields
                    if(value instanceof List && !field.isMultiValueField()){
                        data.addValue(apiName, List.class.cast(value).stream().filter(v -> v != null).reduce((v1,v2)->v1+","+v2).orElse(null));
                    }else{
                        data.addValue(apiName, value);
                    }
                });
            }
        }
        data.setCreatedAt(dateUtil.toEpochMilli(row.get("createdTime").toString()));
        return data;
    }

    private String getBaseId(ConnectorInfo info) {
        return info.getMetaConfig().get("baseId").toString();
    }
    
    public SyncariEntityDataRestClient getClient() {
        return new SyncariEntityDataRestClient(getSingleJsonConfig(), mapper);
    }
    
    private JsonParserConfig getSingleJsonConfig() {
        return new JsonParserConfig(null, null, null, StringUtils.capitalize("Id"), true, null);
    }
    
    private Map<String, Object> transform(Map<String, Object> values, EntitySchema schema) {
        Map results = new HashMap<>();
        values.forEach((k, v) -> {
            Optional<AttributeSchema> field = schema.getField(k);
            if(field.isPresent()) {
                results.put(field.get().getDisplayName(), v); 
            } else {
                log.warn("Field %s not found", k);
            }
        });
        return results;
    }
    
    private void extractResults(List<Result> results, List<EntityData> list, ResponseEntity<String> resp) {
        List rows = JsonPath.parse(resp.getBody()).read("records");
        int i = 0;
        for (Object r : rows) {
            Result result = new Result(true, ((Map)r).get("id").toString(), list.get(i).getSyncariEntityId());
            results.add(result);
            i++;
        }
    }

    @Override
    public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
        String encodedCreds = getEncodedCreds(oAuthRequest.getConfig().getClientId(), oAuthRequest.getConfig().getClientSecret());
        Map<String, String> map = Map.of(DefaultAuthTokenHandler.GRANT_TYPE, "authorization_code",
                DefaultAuthTokenHandler.CODE, oAuthRequest.getCode(),
                DefaultAuthTokenHandler.REDIRECT_URI, oAuthRequest.getRedirectUri(),
                DefaultAuthTokenHandler.CODE_VERIFIER, oAuthRequest.getConfig().getCodeVerifier());

        Map<String, String> headersMap = Map.of("Authorization", "Basic " + encodedCreds);

        return tokenHandler.getAccessToken(OAUTH_URL, map, headersMap);
    }

    private String getEncodedCreds(String clientId, String clientSecret) {
        String creds = clientId + ":" + clientSecret;
        byte[] bytes = new byte[0];
        try {
            bytes = creds.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return Base64.encodeBase64String(bytes);
    }

    @Override
    public AuthConfig refreshToken(ConnectorInfo connector) {
        String encodedCreds = getEncodedCreds(connector.getAuthConfig().getClientId(), connector.getAuthConfig().getClientSecret());
        AuthConfig config = connector.getAuthConfig();
        Map<String, String> map = Map.of(DefaultAuthTokenHandler.GRANT_TYPE, DefaultAuthTokenHandler.REFRESH_TOKEN,
                DefaultAuthTokenHandler.REFRESH_TOKEN, config.getRefreshToken(),
                DefaultAuthTokenHandler.CLIENT_ID, config.getClientId(),
                DefaultAuthTokenHandler.CLIENT_SECRET, config.getClientSecret());

        Map<String, String> headersMap = Map.of("Authorization", "Basic " + encodedCreds);

        return tokenHandler.refreshToken(config, OAUTH_URL, map, headersMap);
    }

    @Override
    public String getOAuthUri(ConnectorInfo connector) {
        return OAUTH_REDIRECT_URI;
    }

    @Override
    public String getAuthHost(AuthConfig config) {
        return OAUTH_HOST;
    }

    @Override
    public String getCodeVerifier() {
        SecureRandom sr = new SecureRandom();
        byte[] code = new byte[32];
        sr.nextBytes(code);
        return Base64.encodeBase64URLSafeString(code);
    }

    @Override
    public String getCodeChallenge(String verifier) {
        byte[] bytes = new byte[0];
        try {
            bytes = verifier.getBytes("US-ASCII");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        md.update(bytes, 0, bytes.length);
        byte[] digest = md.digest();
        return Base64.encodeBase64URLSafeString(digest);
    }
}
