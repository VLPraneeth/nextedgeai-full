package com.syncari.connector.pardot;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import com.darksci.pardot.api.LoginFailedException;
import com.darksci.pardot.api.PardotClient;
import com.darksci.pardot.api.request.customfield.CustomFieldQueryRequest;
import com.darksci.pardot.api.request.login.SsoLoginRequest;
import com.darksci.pardot.api.request.prospect.ProspectDeleteRequest;
import com.darksci.pardot.api.response.Result;
import com.darksci.pardot.api.response.customfield.CustomFieldQueryResponse;
import com.darksci.pardot.api.response.login.SsoLoginResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.SyncResponse;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.utils.DateUtil;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Builder
public class PardotV4Client {
    //String entityName;
    ConnectorInfo connector;
    DateUtil dateUtil;
    PardotClient client;
    ObjectMapper mapper;

    private static final String BASE_URL = "https://pi.pardot.com";
    //private static final String dateFormat = "yyyy-MM-dd HH:mm:ss";
    private static final String dateFormat = "yyyy-MM-dd'T'HH:mm:ssXXX";
    public static final int NO_LIMIT = -1;
    public static final int PAGE_SIZE = 200;
    public static final String CAMPAIGN = "campaigns";
    public static final String PROSPECT = "prospects";
    public static final String PROSPECT_ACCOUNT = "prospect-accounts";
    public static final String LIST = "lists";
    public static final String LIST_MEMBERSHIP = "list-memberships";

    public static final String VISITOR_ACTIVITY = "visitor-activities";
    public static final String VISITOR_PAGE_VIEWS = "visitor-page-views";
    public static final String VISITS = "visits";
    public static final String OPPORTUNITY = "opportunities";

    // TODO: we should have an external name to apiname mapping in the schema level.
    // Also this is ugly from pardot where they expect snake case for the url object name but return
    // the object name with "_" in the results for records. Example `/api/listMembership/version/4`
    // but in the results they send as 
    // "list_membership":{"id":801941,"list_id":2535,"prospect_id":694987,"opted_out":false,....}
    public static final Map<String, String> externalEntityNameMap = new HashMap<>();
    static {
        externalEntityNameMap.put(LIST_MEMBERSHIP, "list_membership");
    }

    public static enum PARDOT_OP {
        CREATE,
        UPDATE,
        DELETE,
        READ,
        UPSERT
    }

    public List<EntitySchema> getSeededEntitySchemas() {
        List<EntitySchema> pardotEntities = new ArrayList<>();
        if (CollectionUtils.isEmpty(pardotEntities)) {
            try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("seed/pardot.json")) {
                ObjectMapper mapper = new ObjectMapper().disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
                pardotEntities = mapper.readValue(inputStream, new TypeReference<List<EntitySchema>>(){});
            } catch (IOException e) {
                log.error("Could not read pardot entities seed", e);
            }
        }
        return pardotEntities;
    }

    public List<AttributeSchema> getProspectCustomFields() {
        List<AttributeSchema> customAttributes = Lists.newArrayList();
        try {
            final CustomFieldQueryResponse.Result response = client.customFieldQuery(new CustomFieldQueryRequest());
            if (CollectionUtils.isNotEmpty(response.getCustomFields())) {
                response.getCustomFields().forEach(
                    x -> customAttributes.add(new AttributeSchema(x.getFieldId(), x.getType()).setCustom(true).setDisplayName(x.getName()))
                );
            }
        } catch (RuntimeException e) {
            log.error("Error describing prospect custom fields ", e);
            handlePardotV4ClientErrors(e);
        }
        return customAttributes;
    }

    public List<EntityData> queryByIds(SyncRequest request) {
        if (CollectionUtils.isEmpty(request.getIds())) {
            log.error("No ids passed for pardotQueryByIds for entity {}.", request.getEntityName());
            return Lists.newArrayList();
        }
        List<EntityData> entities = Lists.newArrayList();
        List<Long> ids = request.getIds().stream().mapToLong(Long::valueOf).boxed().collect(Collectors.toList());
        for (Long id: ids) {
            entities.addAll(doQueryWithId(request, id));
        }
        return entities;
    }

    public List<EntityData> queryByFilter(SyncRequest request, int limit, long offset) {
        return doQueryByFilter(request, limit, offset, false);
    }

    public List<EntityData> queryDeletedByFilter(SyncRequest request, int limit, long offset) {
        return doQueryByFilter(request, limit, offset, true);
    }

    private List<EntityData> doQueryByFilter(SyncRequest request, int limit, long offset, boolean isForDeletedRecords) {
        if (request.getWatermark() != null) {
            if (!request.getWatermark().hasStart()) {
                log.error("No watermark passed for pardotQueryByWatermark for entity {}.", request.getEntityName());
                return Lists.newArrayList();
            }
        }
        if (isForDeletedRecords) {
            return doQueryDeleted(request, limit, offset);
        }
        return doQueryWithLimit(request, limit, offset);
    }

    public SyncResponse create(SyncRequest request) {
        switch (request.getEntityName()) {
            // The batch endpoints do not return the created record. We cannot use it since we want to get the id of the created record.
            //case PROSPECT:
            //    return doBatchPost(request, PARDOT_OP.CREATE);
            case PROSPECT:
            case LIST:
            case LIST_MEMBERSHIP:
                return doPost(request, PARDOT_OP.CREATE);
            default:
                return logAndGetErrorResponse(String.format("Operation %s on %s not yet implemented",
                    PARDOT_OP.CREATE, request.getEntityName()));
        }
    }

    public SyncResponse update(SyncRequest request) {
        switch (request.getEntityName()) {
            // Bulk updates have some limitations on the size of the URI.
            case PROSPECT:
                try {
                    return doBatchPost(request, PARDOT_OP.UPDATE);
                } catch (NonRetriableException exception) {
                    // Try one by one if its a non-retrieable exception.
                    return doPost(request, PARDOT_OP.UPDATE);
                }
            case LIST:
            case LIST_MEMBERSHIP:
                return doPost(request, PARDOT_OP.UPDATE);
            default:
                return logAndGetErrorResponse(String.format("Operation %s on %s not yet implemented",
                    PARDOT_OP.UPDATE, request.getEntityName()));
        }
    }

    public SyncResponse delete(SyncRequest request) {
        switch (request.getEntityName()) {
            case LIST:
            case LIST_MEMBERSHIP:
                return doPost(request, PARDOT_OP.DELETE);
            case PROSPECT:
                SyncResponse response = new SyncResponse();
                List<EntityData> entityList = request.getData().get(connector.getId());
                for (EntityData ed : entityList) {
                    try {
                        final Result<Boolean> dResponse = client.prospectDelete(
                            new ProspectDeleteRequest().withProspectId(Long.parseLong(ed.getId().toString())));
                        if ((null != dResponse) && (dResponse.get() == Boolean.FALSE)) {
                            throw new RuntimeException("Failed to delete prospect with id: " + ed.getValue("id"));
                        }
                        response.getResults().add(new com.syncari.connector.data.Result(true, ed.getId().toString(), ed.getSyncariEntityId()));
                    } catch (RuntimeException e) {
                        String err = String.format("Error processing delete for %s and data %s. Reason: ", 
                            request.getEntityName(), e.getMessage());
                        log.error(err, e);
                        captureRecordError(err, ed, response);
                    }                    
                }
                return response;
            default:
                return logAndGetErrorResponse(
                    String.format("Operation 'Delete' on %s not yet implemented", request.getEntityName()));
        }
    }

    private List<EntityData> doQueryWithLimit(SyncRequest request, int limit, long offset) {
        List<EntityData> entities = doQuery(request, null, limit, offset, false);
        return entities;
    }

    private List<EntityData> doQueryDeleted(SyncRequest request, int limit, long offset) {
        List<EntityData> deletedEntities = new ArrayList<>();
        if ((PROSPECT.equalsIgnoreCase(request.getEntityName()) || LIST_MEMBERSHIP.equalsIgnoreCase(request.getEntityName()))
            && request.getWatermark() != null &&
                request.getWatermark().getEnd() > Instant.EPOCH.toEpochMilli()) {
            // Make a copy so that we dont messup the primary sync's watermark.
            SyncRequest dRequest = request.copy();
            int dOffset = 0;
            List<EntityData> deleted = new ArrayList<>();
            do {
                deleted = doQuery(dRequest, null, PAGE_SIZE, dOffset, true);
                if (deleted.size() > 0) deletedEntities.addAll(deleted);
                dOffset += deleted.size();
            } while (deleted.size() > 0);
            log.info("Found {} deleted records within batch.", dOffset);
        }
        return deletedEntities;
    }

    private List<EntityData> doQueryWithId(SyncRequest request, Long id) {
        return doQuery(request, id, 0, 0, false);
    }

    private List<EntityData> doQuery(SyncRequest request, Long id, int limit, long offset, boolean includeDeleted) {
        List<EntityData> response = new ArrayList<>();
        SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient(getSingleJsonConfig(""), mapper);
        String accessToken = getAccessTokenForDirectAPI(connector.getAuthConfig());
        try {
            AuthConfig authConfig = connector.getAuthConfig();
            authConfig.addHeader("Authorization", "Bearer " + accessToken);
            authConfig.addHeader("Pardot-Business-Unit-Id", 
                connector.getMetaConfig().get(PardotService.BUSINESS_ID_AUTH_FIELD).toString());
            String fields = getQueryFields(request);
            
            String endpointURL = "";
            String query = "";
            String limitStr = "";
            String sortByStr = "";
            String offsetStr = "";
            String deletedStr = "";
            boolean isQueryEndpoint = false;
            if (id != null && id > 0) {
                endpointURL = String.format(BASE_URL + "/api/v5/objects/%s/%s", request.getEntityName(), id);
            } else {
                // TODO: when we support query by additional values, we can expand this into proper query construction
                if (request.getWatermark() != null) {
                    String zoneId = connector.getMetaConfig().getOrDefault(PardotService.TIME_ZONE_ID, "").toString();
                    ZoneId userZoneId = StringUtils.isEmpty(zoneId) ? ZoneId.systemDefault() : ZoneId.of(zoneId);
                    if (request.getWatermark().hasStart()) {
                        if(request.getEntityName().equalsIgnoreCase(VISITOR_PAGE_VIEWS)){
                            query += "&createdAtAfter=" + dateUtil.format(request.getWatermark().getStart(), dateFormat, userZoneId);
                        }else{
                            query += "&updatedAtAfter=" + dateUtil.format(request.getWatermark().getStart(), dateFormat, userZoneId);
                        }
                    }
                    if (request.getWatermark().hasEnd() && request.getWatermark().getStart() != request.getWatermark().getEnd()) {
                        if(request.getEntityName().equalsIgnoreCase(VISITOR_PAGE_VIEWS)){
                            query += "&createdAtBefore=" + dateUtil.format(request.getWatermark().getEnd(), dateFormat, userZoneId);
                        }else{
                            query += "&updatedAtBefore=" + dateUtil.format(request.getWatermark().getEnd(), dateFormat, userZoneId);
                        }
                    }
                    // TODO: LIST_MEMBERSHIP does not support sortby updated_at, we need to use localstorageiterator.
                    if (!LIST_MEMBERSHIP.equalsIgnoreCase(request.getEntityName())) {
                        if(VISITOR_PAGE_VIEWS.equalsIgnoreCase(request.getEntityName())){
                            sortByStr = "&orderBy=createdAt ASC";
                        }else{
                            sortByStr = "&orderBy=updatedAt ASC";
                        }

                    }
                }
                if (limit > 0) limitStr = "&limit=" + limit;
                endpointURL = String.format(BASE_URL + "/api/v5/objects/%s", request.getEntityName());
                isQueryEndpoint = true;
            }
            //if (offset >= 0) offsetStr = "&offset=" + offset;
            if (includeDeleted) deletedStr = "&deleted=true";

            UriComponentsBuilder uriBuilder =
                    UriComponentsBuilder.fromHttpUrl(endpointURL + "?fields={fields}{query}{limit}{sortby}{offset}{deleted}");
            Map<String, String> urlParams = new HashMap<>();
            urlParams.put("fields",fields);
            urlParams.put("query", query);
            urlParams.put("limit", limitStr);
            urlParams.put("offset", offsetStr);
            urlParams.put("sortby", sortByStr);
            urlParams.put("deleted", deletedStr);
            
            log.info(uriBuilder.buildAndExpand(urlParams).toUri().toString());
            ResponseEntity<String> resp = restClient.getResponse(uriBuilder.buildAndExpand(urlParams).toUriString(), authConfig);
            Map<String, Object> respJson = mapper.convertValue(resp, new TypeReference<Map<String, Object>>(){});
            HttpStatus respStatus = HttpStatus.valueOf(Integer.parseInt(respJson.get("status_code_value").toString()));
            // Handle Http errors
            if (respStatus.isError()) {
                throw new RuntimeException(String.format("Error response code %s received for query on %s.", respStatus.toString(),
                    request.getEntityName()));
            }
            Map<String, Object> bodyMap = mapper.readValue(respJson.get("body").toString(), Map.class);
            // Handle API validation errors
            if (bodyMap.containsKey("err")) {
                throw new RuntimeException(String.format("Failed to query records for %s due to '%s'. Please check query parameters", 
                    request.getEntityName(), bodyMap.get("err")));
            }
            List<Map<String, Object>> rawEntities = new ArrayList<>();
            String respEntityName = (externalEntityNameMap.containsKey(request.getEntityName())) ? externalEntityNameMap.get(request.getEntityName())
                : request.getEntityName();
            if (isQueryEndpoint) {

                List<Object> resultList = (List<Object>) bodyMap.get("values");
                if(CollectionUtils.isEmpty(resultList)){
                    return response;
                }
                rawEntities = mapper.convertValue(resultList, new TypeReference<List<Map<String, Object>>>(){});

            } else {
                Map<String,Object> rawEntitiesV5 = new HashMap<>();
                rawEntitiesV5 = mapper.convertValue(bodyMap, new TypeReference<Map<String, Object>>(){});
                response = toEntityDataV5(request, rawEntitiesV5, respEntityName,includeDeleted);
                return response;
            }
            response = toEntityData(request, rawEntities, includeDeleted);
        } catch (JsonProcessingException e) {
            log.error("JSON Processing error on query response body for {} ", request.getEntityName(), e);
            throw new NonRetriableException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), "");
        } catch (RuntimeException e) {
            log.error("Error querying for {} ", request.getEntityName(), e);
            handlePardotV4ClientErrors(e);
        }
        return response;
    }

    private String getQueryFields(SyncRequest request) {
        String fields = "";
        switch (request.getEntityName()){
            case PROSPECT:
            case PROSPECT_ACCOUNT:
            case LIST:
            case LIST_MEMBERSHIP:
                fields = String.join(",",request.getEntitySchema().getAttributes().stream()
                        .filter(a -> !a.isCustom() && !a.getApiName().startsWith("crm"))
                        .filter(a -> !a.getApiName().startsWith("isCrm") )
                        .map(AttributeSchema::getApiName)
                        .collect(Collectors.toList()));
                break;

            default:
                fields = String.join(",",request.getEntitySchema().getAttributes().stream()
                        .filter(a -> !a.isCustom() && !a.getApiName().startsWith("crm"))
                        .filter(a -> !a.getApiName().startsWith("isCrm") && !a.getDataType().equals("object"))
                        .map(AttributeSchema::getApiName)
                        .collect(Collectors.toList()));
                break;
        }

       return fields;
    }

    private SyncResponse doPost(SyncRequest request, PARDOT_OP op) {
        SyncResponse response = new SyncResponse();
        if (MapUtils.isEmpty(request.getData())) {
            String error = String.format("No records passed for %s for entity %s.", op, request.getEntityName());
            log.error(error);
            response.appendError(error);
            return response;
        }

        SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient(getSingleJsonConfig(""), mapper);
        List<EntityData> entityList = request.getData().get(connector.getId());
        String batchAccessToken = getAccessTokenForDirectAPI(connector.getAuthConfig());
        try {
            // These end points are one by one operations.
            for (EntityData partition : entityList) {
                Map<String, Object> record = toProspectBulkPostList(request.getEntitySchema(), List.of(partition), op).get(0);
                if(record.containsKey("score")){
                    log.info("score is "+record.get("score"));
                }
                AuthConfig authConfig = connector.getAuthConfig();
                authConfig.addHeader("Authorization", "Bearer " + batchAccessToken);
                authConfig.addHeader("Pardot-Business-Unit-Id", 
                    connector.getMetaConfig().get(PardotService.BUSINESS_ID_AUTH_FIELD).toString());
                String fields = String.join(",",request.getEntitySchema().getAttributes().stream()
                        .filter(a -> !a.isCustom() && !a.getApiName().startsWith("crm"))
                        .filter(a -> !a.getApiName().startsWith("isCrm"))
                        .map(AttributeSchema::getApiName)
                        .collect(Collectors.toList()));

                URI uri = getConstructedURI(request.getEntitySchema().getApiName(),fields,partition.getId(),op);

                String body = mapper.writeValueAsString(record);

                HttpStatus respStatus=HttpStatus.NOT_FOUND;
                Map<String, Object> respJson = new HashMap<>();
                switch (op){
                    case CREATE:

                        ResponseEntity<String> resp = restClient.postRawURI(uri, body, authConfig);
                        respJson = mapper.convertValue(resp, new TypeReference<Map<String, Object>>(){});
                        respStatus = HttpStatus.valueOf(Integer.parseInt(respJson.get("status_code_value").toString()));
                        break;
                    case DELETE:
                        resp = restClient.delete(uri.toString(), body, authConfig);
                        respJson = mapper.convertValue(resp, new TypeReference<Map<String, Object>>(){});
                        respStatus = HttpStatus.valueOf(Integer.parseInt(respJson.get("status_code_value").toString()));
                        break;
                    case UPDATE:
                        resp = restClient.patch(uri.toString(), body, authConfig);
                        respJson = mapper.convertValue(resp, new TypeReference<Map<String, Object>>(){});
                        respStatus = HttpStatus.valueOf(Integer.parseInt(respJson.get("status_code_value").toString()));
                        break;

                }

                
                if (respStatus.isError()) {
                    throw new RuntimeException(String.format("Error response code %s received for %s on %s.", respStatus.toString(),
                        op, request.getEntityName()));
                }
                if (respStatus != HttpStatus.NO_CONTENT) {
                    Map<String, Object> bodyMap = mapper.readValue(respJson.get("body").toString(), Map.class);
                    // Handle API validation errors
                    if (bodyMap.containsKey("err")) {
                        String err = String.format("Failed to %s records for %s due to '%s'. Please check parameters", 
                            op, request.getEntityName(), bodyMap.get("err"));
                        captureRecordError(err, partition, response);
                    }

                    Map<String, Object> rawEntities = new HashMap<>();
                    String respEntityName = (externalEntityNameMap.containsKey(request.getEntityName())) ? externalEntityNameMap.get(request.getEntityName())
                        : request.getEntityName();

                    rawEntities = mapper.convertValue(bodyMap, new TypeReference<Map<String, Object>>(){});

                    List<EntityData> createdRecord = toEntityDataV5(request, rawEntities, respEntityName,false);
                    com.syncari.connector.data.Result result = 
                        new com.syncari.connector.data.Result(true, createdRecord.get(0).getId(), partition.getSyncariEntityId());
                    response.getResults().add(result);
                }
            }
        } catch (JsonProcessingException e) {
            log.error("Error processing {} due to {}", op, e.getMessage(), e);
            handlePardotV4ClientErrors(e);
        } catch (RuntimeException e) {
            log.error("Error processing {} due to {}", op, e.getMessage(), e);
            handlePardotV4ClientErrors(e);
        }
        return response;
    }

    private URI getConstructedURI(String apiName, String fields, String partitionId, PARDOT_OP op) {
        URI uri = null;
        switch (op){
            case CREATE:
                UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(
                        getEndpoint(apiName, op) + "?fields={fields}");
                Map<String, String> urlParams = new HashMap<>();
                urlParams.put("fields",fields);
                uri = uriBuilder.buildAndExpand(urlParams).toUri();
                break;
            case UPDATE:
                uriBuilder = UriComponentsBuilder.fromHttpUrl(
                        getEndpoint(apiName, op) + "?fields={fields}");
                urlParams = new HashMap<>();
                urlParams.put("fields",fields);
                uri = uriBuilder.buildAndExpand(Map.of("id", Integer.valueOf(partitionId), "fields",urlParams.get("fields"))).toUri();
                break;
            case DELETE:
                 uriBuilder = UriComponentsBuilder.fromHttpUrl(
                        getEndpoint(apiName, op));
                 uri = uriBuilder.buildAndExpand(Map.of("id", partitionId)).toUri();
                 break;

        }
        return uri;
    }

    private SyncResponse doBatchPost(SyncRequest request, PARDOT_OP op) {
        SyncResponse response = new SyncResponse();
        if (MapUtils.isEmpty(request.getData())) {
            String error = String.format("No records passed for %s for entity %s.", op, request.getEntityName());
            log.error(error);
            response.appendError(error);
            return response;
        }
        SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient(getSingleJsonConfig(""), mapper);
        List<EntityData> entityList = request.getData().get(connector.getId());
        String batchAccessToken = getAccessTokenForDirectAPI(connector.getAuthConfig());
        // 50 is the limit for this API.
        List<List<EntityData>> partitions = Lists.partition(entityList, 50);
        try {
            for (List<EntityData> partition : partitions) {
                List<Map<String, Object>> jsonRecords = toProspectBulkPostList(request.getEntitySchema(), partition, op);
                String json = "{ \"prospects\":" + mapper.writeValueAsString(jsonRecords) + " }";
                AuthConfig authConfig = connector.getAuthConfig();
                authConfig.addHeader("Authorization", "Bearer " + batchAccessToken);
                authConfig.addHeader("Pardot-Business-Unit-Id", 
                    connector.getMetaConfig().get(PardotService.BUSINESS_ID_AUTH_FIELD).toString());
                UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(getBatchEndpoint(op) + "?format=json");
                MultiValueMap<String, String> bodyParams = new LinkedMultiValueMap<String, String>();
                bodyParams.add("prospects", json);
                ResponseEntity<String> resp = restClient.postFormDataURI(uriBuilder.build().toUri(), bodyParams, authConfig);
                Map<String, Object> respJson = mapper.convertValue(resp, new TypeReference<Map<String, Object>>(){});
                HttpStatus respStatus = HttpStatus.valueOf(Integer.parseInt(respJson.get("status_code_value").toString()));
                
                if (respStatus.isError()) {
                    throw new RuntimeException(String.format("Error response code %s received for %s on %s.", respStatus.toString(),
                        op, request.getEntityName()));
                }
                Map<String, Object> errors = getResponseErrors(respJson.get("body"));
                List<Integer> errorKeys = new ArrayList<>(); 
                if (MapUtils.isNotEmpty(errors)) {
                    errors.forEach((k, v) -> {
                        int index = Integer.parseInt(k);
                        errorKeys.add(index);
                        if (index <= partition.size()) {
                            com.syncari.connector.data.Result result = new com.syncari.connector.data.Result(
                                false, partition.get(index).getId(), partition.get(index).getSyncariEntityId()).addError(v.toString());
                            response.getResults().add(result);
                        }
                    });
                }
                for (int i = 0; i < partition.size(); i++) {
                    if (!errorKeys.contains(i)) {
                        com.syncari.connector.data.Result result = new com.syncari.connector.data.Result(
                            true, partition.get(i).getId(), partition.get(i).getSyncariEntityId());
                        response.getResults().add(result);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing {} due to {}", op, e.getMessage(), e);
            handlePardotV4ClientErrors(e);
        }
        return response;
    }

    private Map<String, Object> getResponseErrors(Object respBody) throws JsonProcessingException {
        Map<String, Object> bodyMap = mapper.readValue(respBody.toString(), Map.class);
        return mapper.convertValue(bodyMap.get("errors"), new TypeReference<Map<String, Object>>(){});
    }

    private String getEndpoint(String entity, PARDOT_OP op) {
        String endPoint = BASE_URL + "/api/v5/objects/" + entity;
        if (PARDOT_OP.CREATE == op) {
            return endPoint;
        } else if (PARDOT_OP.UPDATE == op) {
            return endPoint+="/{id}";
        } else if (PARDOT_OP.DELETE == op) {
            return endPoint+="/{id}";
        }
        return endPoint += "/{id}";
    }

    private String getBatchEndpoint(PARDOT_OP op) {
        String endPoint = BASE_URL;
        if (PARDOT_OP.CREATE == op) {
            return endPoint += "/api/prospect/version/4/do/batchCreate";
        } else if (PARDOT_OP.UPDATE == op) {
            return endPoint += "/api/prospect/version/4/do/batchUpdate";
        }
        return endPoint += "/api/prospect/version/4/do/batchUpsert";
    }

    private String getAccessTokenForDirectAPI(AuthConfig authConfig) {
        final SsoLoginRequest loginRequest = new SsoLoginRequest()
            .withUsername(authConfig.getUserName())
            .withPassword(authConfig.getPassword())
            .withClientId(authConfig.getClientId())
            .withClientSecret(authConfig.getClientSecret());
        final SsoLoginResponse response = client.login(loginRequest);
        return response.getAccessToken();
    }

    private JsonParserConfig getSingleJsonConfig(String plural) {
        return new JsonParserConfig(null, null, null, "id", true, null);
    }
    
    public List<EntityData> toEntityData(SyncRequest request, List<Map<String, Object>> records, boolean hasDeleted) {
        List<EntityData> entities = Lists.newArrayList();
        if (CollectionUtils.isEmpty(records)) return entities;
        EntitySchema es = request.getEntitySchema();
        long defaultUpdatedAt = (request.getWatermark() != null) ? request.getWatermark().getEnd() : Instant.now().toEpochMilli();
        for (Map<String, Object> record: records) {
            EntityData entityData = new EntityData(es.getApiName());
            if (es.getIdField() == null || !record.containsKey(es.getIdField().getApiName())) {
                log.error("record {} does not contain a valid id and will be skipped", record);
                continue;
            }
            entityData.setConnectorId(connector.getId());
            entityData.setId(record.get(es.getIdField().getApiName()).toString());

            if (record.get("updatedAt") != null) {
                entityData.setLastModified(convertToZonedDateTime(record.get("updatedAt")).toInstant().toEpochMilli());
			} else {
                entityData.setLastModified(defaultUpdatedAt);
            }

			if (record.get("createdAt") != null) {
                entityData.setCreatedAt(convertToZonedDateTime(record.get("createdAt")).toInstant().toEpochMilli());
            } else {
                entityData.setCreatedAt(defaultUpdatedAt);
            }

            // If we are processing just deleted records, mark them as deleted.
            if (hasDeleted) entityData.setDeleted(true);
            
            for (AttributeSchema attr: es.getAttributes()) {
                if (record.containsKey(attr.getApiName())) {
                    Object value = "";
                    // Pardot APIs can return values as String or as LinkedHashMap with a single k->v as "value" -> <Value>
                    if (record.get(attr.getApiName()) instanceof Map) {
                        Map<String, String> mapValue = (LinkedHashMap) record.get(attr.getApiName());
                        value = mapValue.containsKey("value") ? mapValue.get("value") : mapValue;
                    } else {
                        value = record.get(attr.getApiName());
                    }
                    if ("datetime".equalsIgnoreCase(attr.getDataType())) {
				        entityData.addValue(attr.getApiName(), convertToZonedDateTime(value));
                    } else {
                        entityData.addValue(attr.getApiName(), value);
                    }
                }
            }
            entities.add(entityData);
        }
        return entities;
    }

    public List<EntityData> toEntityDataV5(SyncRequest request, Map<String, Object> record, String entityName, boolean hasDeleted) {
        List<EntityData> entities = Lists.newArrayList();
        if (MapUtils.isEmpty(record)) return entities;
        EntitySchema es = request.getEntitySchema();
        long defaultUpdatedAt = (request.getWatermark() != null) ? request.getWatermark().getEnd() : Instant.now().toEpochMilli();


            EntityData entityData = new EntityData(entityName);
            if (es.getIdField() == null || !record.containsKey(es.getIdField().getApiName())) {
                log.error("record {} does not contain a valid id and will be skipped", record);
            }
            entityData.setConnectorId(connector.getId());
            entityData.setId((String.valueOf(record.get(es.getIdField().getApiName()))));

            if (record.get("updatedAt") != null) {
                entityData.setLastModified(convertToZonedDateTime(record.get("updatedAt")).toInstant().toEpochMilli());
            } else {
                entityData.setLastModified(defaultUpdatedAt);
            }

            if (record.get("createdAt") != null) {
                entityData.setCreatedAt(convertToZonedDateTime(record.get("createdAt")).toInstant().toEpochMilli());
            } else {
                entityData.setCreatedAt(defaultUpdatedAt);
            }

            // If we are processing just deleted records, mark them as deleted.
            if (hasDeleted) entityData.setDeleted(true);

            for (AttributeSchema attr: es.getAttributes()) {
                if (record.containsKey(attr.getApiName())) {
                    Object value = "";
                    // Pardot APIs can return values as String or as LinkedHashMap with a single k->v as "value" -> <Value>
                    if (record.get(attr.getApiName()) instanceof Map) {
                        Map<String, String> mapValue = (LinkedHashMap) record.get(attr.getApiName());
                        value = mapValue.containsKey("value") ? mapValue.get("value") : mapValue;
                    } else {
                        value = record.get(attr.getApiName());
                    }
                    if ("datetime".equalsIgnoreCase(attr.getDataType())) {
                        entityData.addValue(attr.getApiName(), convertToZonedDateTime(value));
                    } else {
                        entityData.addValue(attr.getApiName(), value);
                    }
                }
            }
            entities.add(entityData);
        return entities;
    }

    private void handlePardotV4ClientErrors(Exception e) {
        if (e instanceof LoginFailedException) {
            throw new RetriableException(ErrorCodes.CONNECTION_ERROR, e.getMessage(), String.valueOf(((LoginFailedException) e).getErrorCode()));
        }
        throw new NonRetriableException(ErrorCodes.API_ERROR, e.getMessage(), ErrorCodes.API_ERROR.name());
    }

    private void captureRecordError(String errMessage, EntityData ed, SyncResponse response) {
        com.syncari.connector.data.Result result = 
            new com.syncari.connector.data.Result(false, ed.getId().toString(), ed.getSyncariEntityId()).addError(errMessage);
        response.setSuccess(false);
        response.getResults().add(result);
    }

    private SyncResponse logAndGetErrorResponse(String message) {
        log.error(message);
        SyncResponse errResponse = new SyncResponse(false);
        errResponse.appendError(new RuntimeException(message));
        return errResponse;
    }

    private List<Map<String, Object>> toProspectBulkPostList(EntitySchema es, List<EntityData> entities,
        PARDOT_OP op) {
        List<Map<String, Object>> recordList = Lists.newArrayList();
        for (EntityData ed: entities) {
            Map<String, Object> edMap = new HashMap<>();
            for (AttributeSchema attr: es.getAttributes()) {
                if (PARDOT_OP.CREATE == op && !attr.isInitializable() || attr.isIdField()) {
                    continue;
                }

                if (PARDOT_OP.UPDATE == op && !attr.isUpdateable()) {
                    continue;
                }
                if(attr.isCustom() || attr.getApiName().equalsIgnoreCase("password")){
                    continue;
                }
                edMap.put(attr.getApiName(), ed.getValues().get(attr.getApiName()));
            }
            
            recordList.add(edMap);
        }
        return recordList;
    }

    public <T> Optional<T> toPardotEntity(EntityData entityData) {
        return Optional.empty();
    }

    private ZonedDateTime convertToZonedDateTime(Object date) {
        if (date == null) return null;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateFormat);
        LocalDateTime ltc = LocalDateTime.parse(date.toString(), formatter);
        String zoneId = connector.getMetaConfig().getOrDefault(PardotService.TIME_ZONE_ID, "").toString();
        return ltc.atZone(StringUtils.isEmpty(zoneId) ? ZoneId.systemDefault() : ZoneId.of(zoneId));
    }
}
