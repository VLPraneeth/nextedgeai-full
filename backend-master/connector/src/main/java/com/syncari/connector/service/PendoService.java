package com.syncari.connector.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.config.ProxyConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.DefaultCursorBasedIterator;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.rest.PendoRestClient;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.connector.service.def.*;
import com.syncari.connector.service.seed.PendoSeed;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jooq.lambda.function.Function3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.connector.service.query.PendoQueries.*;

@Slf4j
@Component(Constants.PENDO)
public class PendoService implements CommonDataService, MetadataService, SynapseInfoService, AuthenticationService, RestClientService {
    @Autowired
    Transformer transformer;
    @Autowired
    ObjectMapper mapper;

    public static final String PENDO_URL = "https://app.pendo.io/";
    public static final String PENDO_METADATA_END_POINT = "api/v1/metadata/schema/%s";
    public static final String PENDO_METADATA_SETVALUE_END_POINT = "api/v1/metadata/%s/%s/value";
    public static final String PENDO_AGGREGATION_END_POINT = "api/v1/aggregation";
    public static final String PENDO_GET_BY_IDS = "api/v1/%s%s";
    public static final Integer MAX_BATCH_RECORDS = 500;
    public static final Integer PENDO_30_MIN_CLOCK_SKEW_IN_SEC = 30 * 60;
    private final int WAIT_TIMEOUT_MILLIS = 300000;

    private static final Map<String, String> idNameMap = Map.of("account", "accountId", "visitor", "visitorId");

    private static final Map<HttpStatus, String> errorCodeMap = Map.of(HttpStatus.BAD_REQUEST, "The format is unacceptable due to malformed JSON or missing field mappings.",
    HttpStatus.REQUEST_TIMEOUT, "The call took too long and timed out.");

    @Override
    public SyncariEntityDataRestClient getRestClient() {
        return new PendoRestClient();
    }
    
    @Override
    public SyncariEntityDataRestClient getRestClient(ProxyConfig proxy) {
        return new PendoRestClient(proxy);
    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getApiKey());
    }

    @Override
    public List<AuthField> getConfigureFields() {
        return List.of(ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public String getCategory() {
        return "Accounting";
    }

    @Override
    public String getName() {
        return Constants.PENDO;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/pendo.svg")
                .setDisplayName("Pendo")
                .setBackgroundColor("#FFF6F9")
                .setHelpUrl(helpArticlesBaseUrl + "/13957168631828-Pendo-Synapse-Setup");
    }

    @Override
    public int clockSkewTolerance(ConnectorInfo connectorInfo) {
        return PENDO_30_MIN_CLOCK_SKEW_IN_SEC;
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse result = new TestConnectionResponse();
        try {
            DescribeAllRequest request = new DescribeAllRequest(config, List.of());
            describeAll(request);
        } catch (NonRetriableException e) {
            try {
                result.setCode(e.getErrorCode());
                result.setErrors(List.of(e.getMessage()));
                result.setMessage(e.getMessage());
            } catch (Exception e2) {
                e2.printStackTrace();
                result.setErrors(List.of("Unknown Error"));
                result.setMessage("Unknown Error");
                result.setCode(HttpStatus.UNAUTHORIZED.name());
            }
        }
        return result;
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        WatermarkInfo watermark = request.getWatermark();

        Function3<WatermarkInfo, Integer, String, DataWithCursor> generator = (wm, pageSize, lastProcessedWM) -> {

            long currentStart = wm.getStart();

            if (StringUtils.isNotBlank(lastProcessedWM)) {
                currentStart = Long.parseLong(lastProcessedWM);
            }
            if (currentStart < wm.getStart()) {
                currentStart = wm.getStart();
            }

            if(request.getEntityName().equalsIgnoreCase("visitorRaw")) {
                return fetchDataWithFixedWindow(currentStart, wm.getEnd(), request, lastProcessedWM, new ArrayList<>());
            } else {
                return fetchDataWithRetryHelper(currentStart, wm.getEnd(), request, lastProcessedWM, new ArrayList<>());
            }
        };

        DefaultCursorBasedIterator iterator = new DefaultCursorBasedIterator(watermark,
                watermark.getChangeStream(),
                watermark.getOffset(),
                generator, new ArrayList<>(),
                MAX_BATCH_RECORDS, watermark.getLimit(), true);
        return new FetchResponse(request.getWatermark(), iterator);
    }

    private DataWithCursor fetchDataWithFixedWindow(long currentStart, long currentEnd, SyncRequest request, String lastProcessedWM, List<EntityData> accumulatedResults) {
        long maxRecords = 10000;
        long interval = Duration.ofMinutes(60).toMillis();
        long maxEnd = Math.min(currentStart + interval, currentEnd);
        String finalWatermark = lastProcessedWM;

        while (currentStart < currentEnd && accumulatedResults.size() < maxRecords) {
            DataWithCursor dataWithCursor = fetchDataForWindowWithRetry(currentStart, maxEnd, request, lastProcessedWM, accumulatedResults);

            List<EntityData> fetchedData = dataWithCursor.getData();

            if (!fetchedData.isEmpty()) {
                accumulatedResults.addAll(fetchedData);

                finalWatermark = String.valueOf(fetchedData.get(fetchedData.size() - 1).getLastModified());

                if (accumulatedResults.size() >= maxRecords) {
                    break;
                }
            }

            currentStart = maxEnd + 1;
            maxEnd = Math.min(currentStart + interval, currentEnd);
        }

        String nextProcessedWM = currentStart >= currentEnd ? "" : finalWatermark;

        return new DataWithCursor(lastProcessedWM, nextProcessedWM, accumulatedResults);
    }

    private DataWithCursor fetchDataForWindowWithRetry(long start, long end, SyncRequest request, String lastProcessedWM, List<EntityData> accumulatedResults) {
        try {
            return fetchDataForWindow(start, end, request, lastProcessedWM);

        } catch (RetriableException e) {
            long interval = end - start;

            if (interval > Duration.ofMinutes(1).toMillis()) {
                // Split the interval in half and recursively retry each half
                long midPoint = start + interval / 2;

                DataWithCursor firstHalf = fetchDataForWindowWithRetry(start, midPoint, request, lastProcessedWM, accumulatedResults);
                DataWithCursor secondHalf = fetchDataForWindowWithRetry(midPoint + 1, end, request, firstHalf.getNextPageURL(), firstHalf.getData());

                // Combine results from both halves
                accumulatedResults.addAll(secondHalf.getData());
                return new DataWithCursor(lastProcessedWM, secondHalf.getNextPageURL(), accumulatedResults);

            } else {
                throw new RuntimeException(String.format("Failed to fetch data for interval %d - %d", start, end));
            }
        }
    }

    private DataWithCursor fetchDataForWindow(long start, long end, SyncRequest request, String lastProcessedWM) {
        String requestBody = String.format(PENDO_VISITOR_RAW_GET_BY_WATERMARK_REQ,
                convertEpochToDateFunction(start),
                PendoSeed.objWaterMark.getOrDefault(request.getEntityName(), "lastUpdatedAt"), // startDate Field
                start,
                PendoSeed.objWaterMark.getOrDefault(request.getEntityName(), "lastUpdatedAt"), // endDate Field
                end + 1,
                PendoSeed.objWaterMark.getOrDefault(request.getEntityName(), "lastUpdatedAt") // sort Field
        );
        log.info("Aggregation query - {}", requestBody);

        try {
            ResponseEntity<String> response = getClient().postRaw(PENDO_URL + PENDO_AGGREGATION_END_POINT, requestBody, request.getConnector().getAuthConfig());
            ReadContext context = JsonPath.parse(response.getBody());
            List<Map> rows = context.read("results");

            List<EntityData> result = new ArrayList<>();
            for (Map map : rows) {
                EntityData e = extractRow(request, map);
                if (e != null) {
                    result.add(e);
                }
            }

            // Determine last watermark directly from the last entry in the result (as data is sorted)
            String nextProcessedWM = result.isEmpty() ? lastProcessedWM : String.valueOf(result.get(result.size() - 1).getLastModified());

            return new DataWithCursor(lastProcessedWM, nextProcessedWM, result);

        } catch (Exception e) {
            if (e instanceof RetriableException) {
                throw new RetriableException(((RetriableException) e).getErrorCode(), e.getMessage(), ((RetriableException) e).getStatusCode());
            } else {
                throw new RuntimeException("Failed to fetch data: " + e.getMessage());
            }
        }
    }

    public static String convertEpochToDateFunction(long epochMillis) {
        Instant instant = Instant.ofEpochMilli(epochMillis);

        ZonedDateTime zdt = instant.atZone(ZoneOffset.UTC);

        int year = zdt.getYear();
        int month = zdt.getMonthValue();
        int day = zdt.getDayOfMonth();

        return String.format("date(%d,%d,%d)", year, month, day);
    }

    private DataWithCursor fetchDataWithRetryHelper(long currentStart, long currentEnd, SyncRequest request, String lastProcessedWM, List<EntityData> accumulatedResults) {
        if (currentStart >= currentEnd) {
            throw new RuntimeException("Failed to fetch data within the given time range.");
        }

        String entityName = request.getEntityName().equalsIgnoreCase("nps") ? "pollsSeenEver" : request.getEntitySchema().getPluralName();

        String requestBody = String.format(PENDO_GET_BY_WATERMARK_REQ,
                entityName,
                PendoSeed.objWaterMark.getOrDefault(request.getEntityName(), "lastUpdatedAt"), // startDate Field
                currentStart,
                PendoSeed.objWaterMark.getOrDefault(request.getEntityName(), "lastUpdatedAt"), // endDate Field
                currentEnd + 1,
                PendoSeed.objWaterMark.getOrDefault(request.getEntityName(), "lastUpdatedAt"), // sort Field
                MAX_BATCH_RECORDS
        );
        log.info("Aggregation query - {}", requestBody);

        try {
            ResponseEntity<String> response = getClient().postWithBackOff(PENDO_URL + PENDO_AGGREGATION_END_POINT, requestBody, request.getConnector().getAuthConfig());
            ReadContext context = JsonPath.parse(response.getBody());
            List<Map> rows = context.read("results");

            long lastWatermarkValue = -1L;
            List<EntityData> result = new ArrayList<>();

            for (Map map : rows) {
                EntityData e = extractRow(request, map);
                if (e != null) {
                    result.add(e);
                    lastWatermarkValue = e.getLastModified();
                }
            }

            if (currentStart == lastWatermarkValue) {
                lastWatermarkValue++;
            }
            String nextProcessedWM = String.valueOf(lastWatermarkValue);

            if (result.size() < MAX_BATCH_RECORDS || (result.size() == MAX_BATCH_RECORDS && currentStart == currentEnd)) {
                nextProcessedWM = "";
            }

            accumulatedResults.addAll(result);
            return new DataWithCursor(lastProcessedWM, nextProcessedWM, accumulatedResults);

        } catch (Exception e) {
            if (e instanceof RetriableException) {
                long midPoint = (currentStart + currentEnd) / 2;

                // First half
                DataWithCursor firstHalf = fetchDataWithRetryHelper(currentStart, midPoint, request, lastProcessedWM, accumulatedResults);
                // Second half
                DataWithCursor secondHalf = fetchDataWithRetryHelper(midPoint + 1, currentEnd, request, lastProcessedWM, firstHalf.getData());

                return new DataWithCursor(lastProcessedWM, secondHalf.getNextPageURL(), secondHalf.getData());
            } else {
                throw new RuntimeException(e.getMessage());
            }
        }
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        List<EntityData> records = new ArrayList<>();
        String entityName = request.getEntityName().equalsIgnoreCase("visitorRaw") ? "visitor" : request.getEntityName();;
        String url = String.format(PENDO_URL + PENDO_GET_BY_IDS, entityName, List.of("account", "visitor").contains(entityName) ? "/" :"?id=")+"%s";

        if(request.getEntityName().equalsIgnoreCase("nps")) {
            url = PENDO_URL + PENDO_AGGREGATION_END_POINT;
        }
        for(Map.Entry<String, List<EntityData>> entry : request.getData().entrySet()){
            for(EntityData ed: entry.getValue()) {
                String getByIdUrl = String.format(url, ed.getId());
                getById(getByIdUrl, request, ed.getId()).ifPresent(record -> {
                    records.add(record);
                });

            };
        };
        return records;
    }

    protected Optional<EntityData> getById(String url,  SyncRequest request, String id) {
        try {
            Optional<ResponseEntity<String>> response = Optional.empty();
            if(request.getEntityName().equalsIgnoreCase("nps")) {
                String[] parts = id.split("-");
                if(parts.length == 2) {
                    String timestamp = parts[1];
                    String requestBody = String.format(PENDO_GET_NPS_BY_ID_REQ, timestamp);
                    response = Optional.of(getClient().postRaw(PENDO_URL + PENDO_AGGREGATION_END_POINT, requestBody, request.getConnector().getAuthConfig()));
                }
            } else{
               response =  Optional.of(getClient().getResponse(url, request.getConnector().getAuthConfig()));
            }
            if(response.isEmpty()) return Optional.empty();
            ReadContext context = JsonPath.parse(response.get().getBody());
            Map row;
            if(List.of("account", "visitor").contains(request.getEntityName())){
                row = context.json();
            }else if(request.getEntityName().equalsIgnoreCase("nps")) {
                List<Map> rows = context.read("results");
                if(rows.isEmpty()) return Optional.empty();
                row = rows.get(0);
            } else {
                ArrayList<Map> rowList = context.json();
                if(rowList.isEmpty()) return Optional.empty();
                row = rowList.get(0);
            }
            return Optional.of(extractRow(request, row));
        } catch (NonRetriableException | RetriableException e) {
            if(ErrorCodes.BAD_ENDPOINT.name().equals(e.getErrorCode()) || ErrorCodes.DATA_NOT_FOUND.name().equals(e.getErrorCode())){
                log.info("Skipping {} record corresponding to url {} with error {}", request.getEntityName(), url, e.getErrorCode());
            }else {
                throw e;
            }
        }
        return Optional.empty();
    }

    private EntityData extractRow(SyncRequest request, Map<String, Object> row){
        if(List.of("account", "visitor", "visitorRaw").contains(request.getEntityName())){
            return extractAccountOrVisitor(request, row);
        }
        EntityData data = new EntityData(request.getEntityName());
        if(request.getEntityName().equalsIgnoreCase("nps")) {
            data.setId(row.get("pollId") + "-" + row.get("time").toString());
        } else {
            data.setId(row.get("id").toString());
        }
        data.setConnectorId(request.getConnector().getId());
        row.forEach((attrName, attrValue) -> {
            Object value = attrValue;
            if (List.of("createdByUser", "lastUpdatedByUser").contains(attrName)){
                value = ((Map)attrValue).get("username");
            }
            Optional<AttributeSchema> attributeOptional = request.getEntitySchema().getField(attrName);
            if (attributeOptional.isPresent() && !attributeOptional.get().isIdField()) {
                data.addValue(attrName, value);
                if (attributeOptional.get().isWatermarkField()){
                    data.setLastModified(Long.parseLong(value.toString()));
                }
                if (attributeOptional.get().isCreatedAtField()){
                    data.setCreatedAt(Long.parseLong(value.toString()));
                }
            }

        });
        return data;
    }

    private EntityData extractAccountOrVisitor(SyncRequest request, Map<String, Object> row){
        String entityName = request.getEntityName().equalsIgnoreCase("visitorRaw") ? "visitor" : request.getEntityName();
        EntityData data = new EntityData(entityName);
        data.setConnectorId(request.getConnector().getId());

        Map<String, Map>metaDataMap = (Map)row.get("metadata");

        metaDataMap.forEach((groupName,attributeMap) ->{

            attributeMap.forEach((attrName, value) -> {
                String apiName = groupName + "_" + attrName;
                if (StringUtils.isNotBlank(apiName)){
                    if ("auto_id".equals(apiName)){
                        data.setId(value.toString());
                        return;
                    }
                    Optional<AttributeSchema> attributeOptional = request.getEntitySchema().getField((String)apiName);
                    if (attributeOptional.isPresent()){
                        data.addValue(apiName, value);
                        if (attributeOptional.get().isWatermarkField()){
                            data.setLastModified(Long.parseLong(value.toString()));
                        }
                        if (attributeOptional.get().isCreatedAtField()){
                            data.setCreatedAt(Long.parseLong(value.toString()));
                        }
                    }
                }
            });

        });
        return data;
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        if(List.of("account", "visitor").contains(request.getEntityName())) {
            return createOrUpdate(request, true);
        } else {
            throw new RuntimeException("create not supported for pendo");
        }
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        if(List.of("account", "visitor").contains(request.getEntityName())) {
            return createOrUpdate(request, false);
        } else {
            throw new RuntimeException("update not supported for this pendo object");
        }
    }

    private SyncResponse createOrUpdate(SyncRequest request, boolean create) {
        SyncResponse syncResponse = new SyncResponse();
        List<List<EntityData>> partitions = Lists.partition(request.getData().get(request.getConnector().getId()), MAX_BATCH_RECORDS);
        partitions.forEach(partition -> {
            Map<String, Map<String, Map<String, Object>>> groupMap = new HashMap<>();
            partition.forEach(entityData -> {
                Map<String, Object> valueMap = entityData.getValues();
                valueMap.forEach((key, value) -> {
                    if(key.contains("_")) {
                        String[] parts = key.split("_");
                        if(parts.length >= 2) {
                            String groupName = parts[0];
                            if(PendoSeed.editableMetadataGroups.contains(groupName)) {
                                String customField = Arrays.stream(parts).skip(1).collect(Collectors.joining("_"));
                                if (groupMap.containsKey(groupName)) {
                                    if (entityData.getId() == null) entityData.setId(entityData.getSyncariEntityId());
                                    if(!groupMap.get(groupName).containsKey(entityData.getId())) {
                                        Map<String, Object> fieldMap = new HashMap<>();
                                        fieldMap.put(customField, value);
                                        groupMap.get(groupName).put(entityData.getId(), fieldMap);
                                    }
                                    groupMap.get(groupName).get(entityData.getId()).put(customField, value);
                                } else {
                                    if (entityData.getId() == null) entityData.setId(entityData.getSyncariEntityId());
                                    Map<String, Map<String, Object>> idMap = new HashMap<>();
                                    Map<String, Object> fieldMap = new HashMap<>();
                                    fieldMap.put(customField, value);
                                    idMap.put(entityData.getId(), fieldMap);
                                    groupMap.put(groupName, idMap);
                                }
                            }
                        }
                    }
                });
            });
            String entityName = request.getEntityName().equalsIgnoreCase("visitorRaw") ? "visitor" : request.getEntityName();
            groupMap.forEach((key, value) -> {
                String url = String.format(PENDO_URL + PENDO_METADATA_SETVALUE_END_POINT, entityName, key);
                if(create) {
                    url = url + "?create=true";
                }
                List<Map<String, Object>> payload = convertToPayload(value, request.getEntityName());
                PendoRestClient restClient = getClient();
                try {
                    ResponseEntity<String> response = restClient.postRaw(url, mapper.writeValueAsString(payload), request.getConnector().getAuthConfig());
                    if(response.getStatusCode().is2xxSuccessful()) {
                        partition.forEach(entityData -> {
                            syncResponse.getResults().add(new Result(true, entityData.getId(), entityData.getSyncariEntityId()));
                        });
                    } else {
                        partition.forEach(entityData -> {
                            syncResponse.getResults().add(new Result(false, entityData.getId(), entityData.getSyncariEntityId())
                                    .addError(errorCodeMap.getOrDefault(response.getStatusCode(), response.getStatusCode().getReasonPhrase())));
                        });
                    }
                } catch (JsonProcessingException e) {
                    partition.forEach(entityData -> {
                        syncResponse.getResults().add(new Result(false, entityData.getId(), entityData.getSyncariEntityId())
                                .addError(e.getMessage()));
                    });
                }
            });
        });
        return syncResponse;
    }

    private List<Map<String, Object>> convertToPayload(Map<String, Map<String, Object>> value, String entityName) {
        List<Map<String, Object>> payload = new ArrayList<>();
        value.forEach((id, fieldMap) -> {
            Map<String, Object> map = new HashMap<>();
            map.put(idNameMap.get(entityName), id);
            map.put("values", fieldMap);
            payload.add(map);
        });
        return payload;
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        throw new RuntimeException("delete not supported for pendo");
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        if (!PendoSeed.objPluralMap.entrySet().contains(request.getEntity())){
            Optional.empty();
        }
        ConnectorInfo connectorInfo = request.getConnector();
        return Optional.ofNullable(toEntitySchema(request.getEntity(), connectorInfo));
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        Set<String> entities = PendoSeed.objPluralMap.keySet();
        List<EntitySchema> schemaList = new ArrayList<>();
        entities.forEach(e -> {
            EntitySchema entity = toEntitySchema(e, request.getConnector());
            schemaList.add(entity);
        });
        return schemaList;
    }

    private EntitySchema toEntitySchema(String entityName, ConnectorInfo connector) {
        EntitySchema schema = PendoSeed.getSeedEntitySchema(entityName);

        if(List.of("account", "visitor", "visitorRaw").contains(entityName)){
            // Get the columns from metadata and append
            String entityToQuery = entityName.equalsIgnoreCase("visitorRaw") ? "visitor" : entityName;
            String describeEndpoint = String.format(PENDO_URL + PENDO_METADATA_END_POINT, entityToQuery);
            ResponseEntity<String> response = getClient().getResponse(describeEndpoint, connector.getAuthConfig());
            Map<String, Map> responseMap = JsonPath.parse(response.getBody()).json();
            for(Map.Entry<String, Map> groupEntry : responseMap.entrySet()){
                String groupName = groupEntry.getKey();
                if("auto".equals(groupName)){
                    continue;
                }
                Map<String, Map> attributeMap = groupEntry.getValue();

                for(Map.Entry<String, Map> attributeEntry : attributeMap.entrySet()){
                    String name  = groupName + "_" +attributeEntry.getKey();
                    Map<String, String> attributeProperties = attributeEntry.getValue();

                    String displayName = attributeProperties.get("DisplayName");
                    if (StringUtils.isBlank(displayName)){
                        displayName = StringUtils.capitalize(attributeEntry.getKey());
                    }
                    if (StringUtils.isNotBlank(groupName)){
                        displayName = StringUtils.capitalize(groupName) + " " + displayName;
                    }

                    AttributeSchema attributeSchema = new AttributeSchema(name, attributeProperties.get("Type") == null || attributeProperties.get("Type").isBlank() ? "string" : "date".equals(attributeProperties.get("Type")) || "time".equals(attributeProperties.get("Type"))? "datetime" : attributeProperties.get("Type"))
                            .setDisplayName(displayName)
                            .setUpdateable(PendoSeed.editableMetadataGroups.contains(groupName));
                    schema.addField(attributeSchema);
                }
            }
        }

        return schema;
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        throw new RuntimeException(this.getUIMetadata().getDisplayName() + " does not support create field");
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new RuntimeException(this.getUIMetadata().getDisplayName() + " does not support delete field");
    }

    private JsonParserConfig getSingleJsonConfig() {
        return new JsonParserConfig(null, null, null, "id", true, null);
    }

    public PendoRestClient getClient() {
        return new PendoRestClient(getSingleJsonConfig(), mapper);
    }

    SyncariEntityDataRestClient getClient(JsonParserConfig config) {
        return new SyncariEntityDataRestClient(config,mapper);
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

    @Override
    public boolean isSink() {
        return true;
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19206907606292";
    }
}
