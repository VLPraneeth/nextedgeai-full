package com.syncari.connector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.DefaultCursorBasedIterator;
import com.syncari.connector.data.iterator.DefaultDataIterator;
import com.syncari.connector.data.iterator.Offset;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.QuotaExceededException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.connector.service.seed.EloquaSeed;
import com.syncari.utils.DateUtil;
import com.syncari.utils.Pair;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jooq.lambda.function.Function3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;


@Slf4j
@Component(Constants.ELOQUA)
public class EloquaService implements AuthenticationService, CommonDataService, MetadataService, SynapseInfoService {

    @Autowired
    Transformer transformer;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    DateUtil dateUtil;

    private static final Map<String, String> WATERMARK_FIELD_MAP = Map.of(Constants.CONTACT.toLowerCase(), "C_DateModified", Constants.ACCOUNT.toLowerCase(), "M_DateModified");

    private static final int DEFAULT_PAGE_SIZE=100;

    private static final String DEFAULT_TIME_ZONE_ID = "America/New_York";

    public static final String UPDATED_AT_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private static final String ORACLE_ELOQUA_SYNAPSE_SETUP_ARTICLE = "/13537382451092-Oracle-Eloqua-Synapse-Setup";

    @Data
    @EqualsAndHashCode
    public static class PicklistOptionCacheKey {
        ConnectorInfo info;
        String optionListId;

        public PicklistOptionCacheKey(ConnectorInfo info, String optionListId) {
            this.info = info;
            this.optionListId = optionListId;
        }
    }

    LoadingCache<PicklistOptionCacheKey, List<String> > picklistOptionCache = CacheBuilder.newBuilder().maximumSize(1000).expireAfterWrite(3l, TimeUnit.HOURS)
            .build(new CacheLoader<>() {
                @Override
                public List<String> load(PicklistOptionCacheKey key) {
                    return getPicklistValues(key.optionListId, key.info);
                }
            });

    LoadingCache<AuthConfig, String > eloquaEnpointCache = CacheBuilder.newBuilder().maximumSize(1000).expireAfterWrite(3l, TimeUnit.HOURS)
            .build(new CacheLoader<>() {
                @Override
                public String load(AuthConfig key) {
                    return getEloquaEndpoint(key);
                }
            });

    private static final Map<String, HashBiMap> ENTITY_STANDARD_ATTR_APINAME_FIELDNAME_MAP = Map.of(
            Constants.CONTACT.toLowerCase(), HashBiMap.create(Map.ofEntries(
                    Map.entry("C_Company", "accountName"),
                    Map.entry("C_Address1", "address1"),
                    Map.entry("C_Address2", "address2"),
                    Map.entry("C_Address3", "address3"),
                    Map.entry("C_BusPhone", "businessPhone"),
                    Map.entry("C_City", "city"),
                    Map.entry("C_Country", "country"),
                    Map.entry("C_DateCreated", "createdAt"),
                    Map.entry("C_EmailAddress", "emailAddress"),
                    Map.entry("C_Fax", "fax"),
                    Map.entry("C_FirstName", "firstName"),
                    Map.entry("C_LastName", "lastName"),
                    Map.entry("C_MobilePhone", "mobilePhone"),
                    Map.entry("C_Zip_Postal", "postalCode"),
                    Map.entry("C_State_Prov", "province"),
                    Map.entry("C_Salesperson", "salesPerson"),
                    Map.entry("C_Title", "title"),
                    Map.entry("C_DateModified", "updatedAt"),
                    Map.entry("id", "id")
                    )),
            Constants.ACCOUNT.toLowerCase(), HashBiMap.create(Map.ofEntries(
                    Map.entry("M_Address1", "address1"),
                    Map.entry("M_Address2", "address2"),
                    Map.entry("M_Address3", "address3"),
                    Map.entry("M_BusPhone", "businessPhone"),
                    Map.entry("M_City", "city"),
                    Map.entry("M_Country", "country"),
                    Map.entry("M_DateCreated", "createdAt"),
                    Map.entry("M_CompanyName", "name"),
                    Map.entry("M_Zip_Postal", "postalCode"),
                    Map.entry("M_State_Prov", "province"),
                    Map.entry("M_DateModified", "updatedAt")
                    ))
            );

    private static final Set<String> CUSTOM_OBJECT_STANDARD_ATTR_SET = Set.of(
            "customObjectRecordStatus", "accountId", "contactId"
    );

    public static final String GET_ENTITY_FIELDS_ENDPOINT = "/api/REST/2.0/assets/%s/fields?depth=complete";
    public static final String GET_CUSTOM_OBJECTS_ENDPOINT = "/api/REST/2.0/assets/customObjects?depth=complete&count=1000&page=%s";
    public static final String GET_CUSTOM_OBJECTS_BY_ID_ENDPOINT = "/api/REST/2.0/assets/customObject/%s?depth=complete";
    public static final String GET_OPTION_LIST_VALUES_ENDPOINT = "/api/REST/2.0/assets/optionList/%s?depth=complete";
    public static final String GET_ACCOUNTS_BY_WATERMARK_ENDPOINT = "/api/REST/2.0/data/%s?search=%s>='%s'%s<'%s'%s&orderBy=%s&depth=complete&count=%s&page=%s";
    public static final String GET_ENTITIES_BY_WATERMARK_ENDPOINT = "/api/REST/2.0/data/%s?search=%s>='%s'%s<'%s'%s%s&orderBy=%s&depth=complete&count=%s";
    public static final String GET_CUSTOM_ENTITIES_BY_WATERMARK_ENDPOINT = "/api/REST/2.0/data/customObject/%s/instances?search=%s>='%s'%s<'%s'%s%s&orderBy=%s&depth=complete&count=%s";
    public static final String ENTITY_BY_ID_ENDPOINT = "/api/REST/2.0/data/%s/%s";
    public static final String CUSTOM_OBJECT_BY_ID_ENDPOINT = "/api/REST/2.0/data/customObject/%s/instance/%s";
    public static final String CREATE_ENTITY_ENDPOINT = "/api/REST/2.0/data/%s";
    public static final String CREATE_CUSTOM_OBJECT_ENDPOINT = "/api/REST/2.0/data/customObject/%s/instance";

    public static final String ELOQUA_DISCOVERY_ENDPOINT = "https://login.eloqua.com/id";

    public static final String CUSTOM_OBJECT_PREFIX = "customObject-";

    // Thread-safe counter to track the total number of threads
    private final AtomicInteger threadCountTracker = new AtomicInteger(0);

    private static final int MAX_THREAD_LIMIT = 12;

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getUserPwd());
    }

    @Override
    public List<AuthField> getConfigureFields() {
        return List.of(ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19189161401876";
    }

    private String getEloquaEndpoint(AuthConfig authConfig){
        ResponseEntity<String> response = getClient().getResponse(ELOQUA_DISCOVERY_ENDPOINT, authConfig);
        if (response.getBody() == null || response.getBody().contains("Not authenticated.")){
            log.error("Not Authenticated. Check your credentials");
            throw new NonRetriableException(ErrorCodes.ACCESS_DENIED, "{\"message\":\"Authentication failed.\"}", "401");
        }
        ReadContext context = JsonPath.parse(response.getBody());
        Map rows = context.read("urls");
        return (String)rows.get("base");
    }

    @Override
    public String getCategory() {
        return "Marketing";
    }

    @Override
    public String getName() {
        return Constants.ELOQUA;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/eloqua.svg")
                .setDisplayName("Eloqua")
                .setBackgroundColor("#FFF9FA")
                .setHelpUrl(helpArticlesBaseUrl + ORACLE_ELOQUA_SYNAPSE_SETUP_ARTICLE);
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        log.debug("Watermark : {}, Entity Name: {} SourceParams: {}", request.getWatermark(), request.getEntityName(), request.getSourceParams());
        if(Constants.ACCOUNT.equalsIgnoreCase(request.getEntityName())){
            return new FetchResponse(request.getWatermark(), getAccountIterator(request, new ValueHolder<>("")));
        }
        return new FetchResponse(request.getWatermark(), getIterator(request));
    }

    private DefaultCursorBasedIterator getIterator(SyncRequest request) {
        BiMap<String, String> objectFieldIdMap = getObjectFieldIdMap(request, request.getConnector());
        WatermarkInfo watermark = request.getWatermark();
        String queryPredicate = getOptimizedPredicateString(request);
        Function3<WatermarkInfo, Integer, String, DataWithCursor> generator = (wm, pageSize, prevCycleLastProcessedRecordInfo) -> {

            // Make sure start/end are always in seconds ignoring any milli seconds
            long wmStart = (wm.getStart() / 1000l) * 1000l;

            long currentWatermarkStart = wmStart;
            String prevCycleLastProcessedRecordId = null;
            String nextCycleLastProcessedRecordInfo = "";

            // Get whether its same the start date and if so get the previous last processed id
            if(StringUtils.isNotBlank(prevCycleLastProcessedRecordInfo)){
                String[] prevCycleLastProcesed = prevCycleLastProcessedRecordInfo.split(":");
                currentWatermarkStart = Long.parseLong(prevCycleLastProcesed[0]);
                if (prevCycleLastProcesed.length == 2){
                    prevCycleLastProcessedRecordId = prevCycleLastProcesed[1];
                }
            }

            if (currentWatermarkStart < wmStart){
                currentWatermarkStart = wmStart;
                prevCycleLastProcessedRecordId = null;
            }

            List<EntityData> result = new ArrayList<>();

            String wmStartStr = dateUtil.formatDate(Instant.ofEpochMilli(currentWatermarkStart), UPDATED_AT_FORMAT, ZoneId.of(DEFAULT_TIME_ZONE_ID));
            String wmEndStr = dateUtil.formatDate(wm.getEnd() == 0 ? Instant.now() : Instant.ofEpochMilli(wm.getEnd()), UPDATED_AT_FORMAT, ZoneId.of(DEFAULT_TIME_ZONE_ID));

            String wmField = request.getEntitySchema().getWatermarkField().getApiName();

            String idField = request.getEntitySchema().getIdField().getApiName();
            try{
                String url = request.getEntitySchema().isCustom() ? GET_CUSTOM_ENTITIES_BY_WATERMARK_ENDPOINT : GET_ENTITIES_BY_WATERMARK_ENDPOINT;
                String entity = request.getEntitySchema().isCustom() ? extractCustomObjectId(request.getEntityName()) : request.getEntityName().toLowerCase();

                String orderBy = wmField + "," + idField;

                // Add Id filter if prev record Id is not null
                String idFilter = "";
                if (StringUtils.isNotBlank(prevCycleLastProcessedRecordId) && !prevCycleLastProcessedRecordId.equalsIgnoreCase("null")){
                    idFilter = idField + ">'" + prevCycleLastProcessedRecordId + "'";
                }

                String getByWatermarkURL = eloquaEnpointCache.getUnchecked(request.getConnector().getAuthConfig()) + String.format(url,
                        request.getEntitySchema().isCustom() ? entity: EloquaSeed.objPluralMap.get(entity), wmField, wmStartStr, wmField, wmEndStr, idFilter, queryPredicate, orderBy, pageSize);

                ResponseEntity<String> response = getClient().getResponse(getByWatermarkURL, request.getConnector().getAuthConfig());
                ReadContext context = JsonPath.parse(response.getBody());
                List<Map> rows = context.read("elements");

                Long nextWatermarkStart = currentWatermarkStart;
                String currentCycleLastProcessedRecordId = null;

                // Loop and mark lastmodified and last processed record id
                for (Map map : rows) {
                    EntityData e = extractRow(request, objectFieldIdMap, map);
                    if (e != null){
                        result.add(e);
                        nextWatermarkStart = e.getLastModified();
                        currentCycleLastProcessedRecordId = e.getId();
                    }
                }

                // Previous processed record is not empty means we are processing the same timestamp
                // If the new watermark is greater than prevwatermark - we processed all the records in the current timestamo,
                // but might have missed some records in the next timestamp
                // If the result size is also less than pagesize means we processed all records in the current timestamp
                // In both situations we just need to increment our watermark by 1sec as eloqua works on second interval,
                // instead respecting the date on the last processed record to make sure we process the missing records after the curr watermarkstartdate
                if (StringUtils.isNotEmpty(prevCycleLastProcessedRecordId)){
                    if (currentWatermarkStart != nextWatermarkStart || result.size() < pageSize){
                        nextWatermarkStart = currentWatermarkStart + 1000l;
                    }
                }

                // Start the nextWatermark Start with the last processed record or based on the codition in the previous check
                // If the last processed record in the currrent iteration has the same timestamp as the old meaning we are processing the same start watermark,
                // add the last processed recordinfo into the changestream else just the timestamp
                nextCycleLastProcessedRecordInfo = String.valueOf(nextWatermarkStart);
                if (currentWatermarkStart == nextWatermarkStart && StringUtils.isNotBlank(currentCycleLastProcessedRecordId)){
                    nextCycleLastProcessedRecordInfo = nextCycleLastProcessedRecordInfo + ":" + currentCycleLastProcessedRecordId;
                }

                // If the prevCycleLastProcessedRecordId record Id is not present meaning we are in processing records with same timestamp
                // and the result size is less than page size or nextwatermark is equal to watermark end - we drained all the records
                // reset the stream
                if(StringUtils.isEmpty(prevCycleLastProcessedRecordId) && (result.size() < pageSize || nextWatermarkStart >= wm.getEnd())){
                    nextCycleLastProcessedRecordInfo = null;
                }

            }catch (QuotaExceededException | RetriableException e) {
                handleException(e, request.getConnector());
            } catch (Exception e) {
                log.error(ExceptionUtils.getStackTrace(e));
                handleException(e, request.getConnector());
            }

            return new DataWithCursor(prevCycleLastProcessedRecordInfo, nextCycleLastProcessedRecordInfo, result);

        };

        int pageSize = request.getPageSize() == 0 ? DEFAULT_PAGE_SIZE : Math.min(request.getPageSize(), DEFAULT_PAGE_SIZE);

        DefaultCursorBasedIterator iterator = new DefaultCursorBasedIterator(watermark,
                watermark.getChangeStream(),
                watermark.getOffset(),
                generator, new ArrayList<>(),
                pageSize, watermark.getLimit(), true);

        return iterator;
    }

    private EloquaIterator getAccountIterator(SyncRequest request, ValueHolder<String> lastOffset) {
        BiMap<String, String> objectFieldIdMap = getObjectFieldIdMap(request, request.getConnector());
        String queryPredicate = getOptimizedPredicateString(request);
        Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator = (wm, pageSize, offset) -> {
            if (offset != 0 && lastOffset.get() == null)
                return Pair.of(0L, new ArrayList<EntityData>().stream());
            List<EntityData> result = new ArrayList<>();

            String wmStartStr = dateUtil.formatDate(wm.getStart() == 0 ? Instant.EPOCH : Instant.ofEpochMilli(wm.getStart()), UPDATED_AT_FORMAT, ZoneId.of(DEFAULT_TIME_ZONE_ID));
            String wmEndStr = dateUtil.formatDate(wm.getEnd() == 0 ? Instant.now() : Instant.ofEpochMilli(wm.getEnd()), UPDATED_AT_FORMAT, ZoneId.of(DEFAULT_TIME_ZONE_ID));

            String wmField = request.getEntitySchema().getWatermarkField().getApiName();

            try {
                String url = GET_ACCOUNTS_BY_WATERMARK_ENDPOINT;
                String entity = request.getEntityName().toLowerCase();
                String getByWatermarkURL = eloquaEnpointCache.getUnchecked(request.getConnector().getAuthConfig()) + String.format(url,
                        EloquaSeed.objPluralMap.get(entity), wmField, wmStartStr, wmField, wmEndStr, queryPredicate, wmField, pageSize, offset == 0 ? 1 : offset);
                ResponseEntity<String> response = getClient().getResponse(getByWatermarkURL, request.getConnector().getAuthConfig());
                ReadContext context = JsonPath.parse(response.getBody());
                List<Map> rows = context.read("elements");

                for (Map map : rows) {
                    EntityData e = extractRow(request, objectFieldIdMap, map);
                    if (e != null){
                        result.add(e);
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

        EloquaIterator iterator = new EloquaIterator(request.getWatermark(),
                request.getWatermark().getOffset(), generator, new ArrayList<>(),
                request.getEntitySchema().getWatermarkField(), pageSize, request.getWatermark().getLimit());
        return iterator;
    }

    private EntityData extractRow(SyncRequest request, BiMap<String, String> objectFieldIdMap, Map row) {
        EntityData data = new EntityData(request.getEntityName());
        data.setId(row.get("id").toString());
        data.setConnectorId(request.getConnector().getId());
        data.setLastModified(Long.parseLong(row.get("updatedAt").toString())*1000);
        data.setCreatedAt(Long.parseLong(row.get("createdAt").toString())*1000);
        if(request.getEntitySchema().isCustom()) {
            CUSTOM_OBJECT_STANDARD_ATTR_SET.forEach(apiName -> {
                data.addValue(apiName, row.get(apiName));
            });
        }

        BiMap<String, String> standardFieldAPIMap = ENTITY_STANDARD_ATTR_APINAME_FIELDNAME_MAP.getOrDefault(request.getEntityName().toLowerCase(), HashBiMap.create()).inverse();

        row.forEach((k, v) -> {
            if ("fieldValues".equals(k)){
                List<Map> fieldValues =  (List)row.get("fieldValues");
                fieldValues.forEach((f) -> {
                    String apiName = objectFieldIdMap.get(f.get("id"));
                    if (StringUtils.isNotBlank(apiName) && StringUtils.isBlank(standardFieldAPIMap.get(apiName))  ){
                        AttributeSchema attribute = request.getEntitySchema().getField((String)apiName).get();
                        if (f.get("value") == null){
                            data.addValue(apiName, null);
                        } else {
                            String value = f.get("value").toString();
                            if(StringUtils.isBlank(value) && ("text".equals(attribute.getDataType()) || "text".equals(attribute.getSubDataType()))) {
                                data.addValue(apiName, value);
                            } else if (("datetime".equals(attribute.getDataType()) || "datetime".equals(attribute.getSubDataType())) && StringUtils.isNotBlank(value)) {
                                data.addValue(apiName, Long.parseLong(value) * 1000);
                            } else if (attribute.isMultiValueField()) {
                                data.addValue(apiName, Arrays.asList(value.split("::")));
                            } else {
                                data.addValue(apiName, value);
                            }
                        }
                    }
                });

            } else {
                String apiName = standardFieldAPIMap.get((String)k);
                if (StringUtils.isNotBlank(apiName)){
                    AttributeSchema attribute = request.getEntitySchema().getField(apiName).get();
                    if (v == null){
                        data.addValue(apiName, null);
                    } else {
                        String value = (String)v;
                        if(StringUtils.isBlank(value) && ("text".equals(attribute.getDataType()) || "text".equals(attribute.getSubDataType()))) {
                            data.addValue(apiName, value);
                        } else if ("datetime".equals(attribute.getDataType()) || "datetime".equals(attribute.getSubDataType())) {
                            data.addValue(apiName, Long.parseLong(value) * 1000);
                        } else if (attribute.isMultiValueField()) {
                            data.addValue(apiName, Arrays.asList(value.split("::")));
                        } else {
                            data.addValue(apiName, value);
                        }
                    }
                }
            }
        });
        for (String key : standardFieldAPIMap.values()) {
            if (!data.getValues().containsKey(key)) {
                data.addValue(key, null);
            }
        }
        return data;
    }

    private void handleException(Exception e, ConnectorInfo connector) {
        if (e instanceof RetriableException && ErrorCodes.TOO_MANY_REQUESTS.name().equals(((RetriableException) e).getErrorCode())) {
            if(e.getCause() != null && e.getCause() instanceof RetriableException && ErrorCodes.IO_ERROR.name().equals(((RetriableException)(e.getCause())).getErrorCode())){
                throw new NonRetriableException(ErrorCodes.BAD_REQUEST, ((RetriableException)(e.getCause())).getStatusCode(), ErrorCodes.BAD_REQUEST.name());
            }
            throw new QuotaExceededException(ErrorCodes.TOO_MANY_REQUESTS.name(),
                    ErrorCodes.TOO_MANY_REQUESTS.name(), ErrorCodes.TOO_MANY_REQUESTS.name(),
                    connector.getId(), DateUtil.getSecondsToNextHour());
        }
        if( e instanceof NonRetriableException && ErrorCodes.BAD_REQUEST.name().equals(((NonRetriableException)e).getErrorCode())){
            throw new NonRetriableException(ErrorCodes.BAD_REQUEST, e.getMessage(), ErrorCodes.BAD_REQUEST.name());
        }
        throw new RuntimeException(e);
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        List<EntityData> records = new ArrayList<>();

        BiMap<String, String> objectFieldIdMap = getObjectFieldIdMap(request, request.getConnector());

        request.getData().forEach((connectorId, ids)->{
            ids.forEach(id -> {
                if (!StringUtils.isNumeric(id.getId())) {
                    throw new NonRetriableException(ErrorCodes.BAD_REQUEST,
                            String.format("Expecting numeric value for id, received a non-numeric (or) null value: %s", id.getId()),
                            HttpStatus.BAD_REQUEST.toString());
                }
                String url = request.getEntitySchema().isCustom() ? CUSTOM_OBJECT_BY_ID_ENDPOINT : ENTITY_BY_ID_ENDPOINT;
                String entity = request.getEntitySchema().isCustom() ? extractCustomObjectId(request.getEntityName()) : request.getEntityName().toLowerCase();
                String getByIdUrl = eloquaEnpointCache.getUnchecked(request.getConnector().getAuthConfig()) + String.format(url,
                        entity, id.getId()) + "?depth=complete";
                getById(getByIdUrl, objectFieldIdMap, request).ifPresent(record -> {
                    records.add(record);
                });

            });
        });
        return records;
    }

    protected Optional<EntityData> getById(String url, BiMap<String, String> objectFieldIdMap, SyncRequest request) {
        try {
            ResponseEntity<String> response = getClient().getResponse(url, request.getConnector().getAuthConfig());
            ReadContext context = JsonPath.parse(response.getBody());
            Map row = context.json();
            return Optional.of(extractRow(request, objectFieldIdMap, row));
        } catch (NonRetriableException | RetriableException e) {
            log.error(e.getMessage(), e);
            if(ErrorCodes.BAD_ENDPOINT.name().equals(e.getErrorCode())){
                log.info("Skipping {} record corresponding to url {}", request.getEntityName(), url);
            }else {
                handleException(e, request.getConnector());
            }
        }
        return Optional.empty();
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    public Map<String, Object> createPayload(EntityData data, BiMap<String, String> objectFieldIdMap, EntitySchema schema, boolean includeReadOnly){
        Map<String, Object> payload = new HashMap<>();
        List<Map<String, Object>> fieldValues = new ArrayList<>();
        BiMap<String, String> standardFieldAPIMap = ENTITY_STANDARD_ATTR_APINAME_FIELDNAME_MAP.getOrDefault(schema.getApiName(), HashBiMap.create());
        data.getValues().forEach((k, v) -> {
            Optional<AttributeSchema> attr = schema.getField(k);
            Object value = null;
            if ( attr.isPresent() && (includeReadOnly || attr.get().isUpdateable())){
                if (attr.get().isMultiValueField() && v instanceof List){
                    List<String> values = (List<String>) v;
                    value = String.join("::", values);
                } else if ("datetime".equals(attr.get().getDataType()) || "datetime".equals(attr.get().getSubDataType())){
                    if (v != null && v instanceof  ZonedDateTime){
                        value = ((ZonedDateTime) v).toInstant().getEpochSecond();
                    } else if (v instanceof  Long){
                        value = Instant.ofEpochMilli((Long)v).getEpochSecond();
                    } else if (v instanceof  String){
                        value = DateUtil.convertLocalDateTimeToZonedDateTime((String)v);
                    }
                } else {
                    value = v;
                }

                if (standardFieldAPIMap.containsKey(attr.get().getApiName())){
                    payload.put(standardFieldAPIMap.get(attr.get().getApiName()), value);
                } else if (objectFieldIdMap.containsKey(attr.get().getApiName())){
                    if(value != null){
                        fieldValues.add(Map.of("id", objectFieldIdMap.get(attr.get().getApiName()),"value",value));
                    } else {
                        fieldValues.add(Map.of("id", objectFieldIdMap.get(attr.get().getApiName())));
                    }
                }
                if(schema.isCustom() && CUSTOM_OBJECT_STANDARD_ATTR_SET.contains(attr.get().getApiName())) {
                    payload.put(attr.get().getApiName(), value);
                }

            }
        });
        payload.put("fieldValues", fieldValues);
        return payload;
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        BiMap<String, String> objectFieldIdMap = getObjectFieldIdMap(request, request.getConnector());
        SyncResponse response = new SyncResponse();
        ConcurrentLinkedQueue<Result> results = new ConcurrentLinkedQueue<>();
        Integer threadCount = getThreadCount(request);
        List<List<EntityData>> partitions = partitionIntoNParts(request.getData().get(request.getConnector().getId()), threadCount);
        if(threadCountTracker.get() < MAX_THREAD_LIMIT && request.getConnector().getInternalConfig().containsKey("threadCount")) {
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

            // Increment the thread count tracker by the number of threads created
            threadCountTracker.addAndGet(threadCount);

            // Create a list of CompletableFuture tasks
            List<CompletableFuture<Void>> futures = partitions.stream()
                    .map(partition -> CompletableFuture.runAsync(() -> create(request, partition, objectFieldIdMap, results, response), executorService))
                    .collect(Collectors.toList());

            awaitFuturesAndFinalizeResponse(futures, executorService, threadCount, response, results);

            // Decrement the thread count tracker by the number of threads created
            threadCountTracker.addAndGet(-threadCount);
        } else {
            log.error("Max thread count reached {}. Falling back to single thread mode", threadCountTracker.get());
            create(request, request.getData().get(request.getConnector().getId()), objectFieldIdMap, results, response);
            response.setResults(new ArrayList<>(results));
        }
        return response;
    }

    public List<List<EntityData>> partitionIntoNParts(List<EntityData> list, int n) {
        int totalSize = list.size();
        int partitionSize = totalSize / n;
        int remainder = totalSize % n;

        List<List<EntityData>> partitions = IntStream.range(0, n)
                .mapToObj(i -> {
                    int start = i * partitionSize + Math.min(i, remainder);
                    int end = start + partitionSize + (i < remainder ? 1 : 0);
                    return new ArrayList<>(list.subList(start, end));
                })
                .collect(Collectors.toList());

        return partitions;
    }

    private void awaitFuturesAndFinalizeResponse(List<CompletableFuture<Void>> futures, ExecutorService executorService, Integer threadCount, SyncResponse response, ConcurrentLinkedQueue<Result> results) {
        // Combine all futures and wait for all of them to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        // Join the combined future to wait for completion
        allFutures.join();

        // Shutdown the executor service
        executorService.shutdown();

        response.setResults(new ArrayList<>(results));
    }

    private static Integer getThreadCount(SyncRequest request) {
        return (Integer) request.getConnector().getInternalConfig().getOrDefault("threadCount", 3);
    }

    private void create(SyncRequest request, List<EntityData> partition, BiMap<String, String> objectFieldIdMap, ConcurrentLinkedQueue<Result> results, SyncResponse response) {
        partition.forEach(e -> {
            try {
                if(request.getEntitySchema().isCustom()){
                    validateCustomObjectMappedContactAndAccount(e, request.getConnector());
                }
                String url = request.getEntitySchema().isCustom() ? CREATE_CUSTOM_OBJECT_ENDPOINT : CREATE_ENTITY_ENDPOINT;
                String entity = request.getEntitySchema().isCustom() ? extractCustomObjectId(request.getEntityName()) : request.getEntityName().toLowerCase();
                String createEntityUrl = eloquaEnpointCache.getUnchecked(request.getConnector().getAuthConfig()) + String.format(url, entity);
                Map payload = createPayload(e, objectFieldIdMap.inverse(), request.getEntitySchema(), true);
                ResponseEntity<String> resp = getClient().postRaw(createEntityUrl, mapper.writeValueAsString(payload), request.getConnector().getAuthConfig());
                ReadContext context = JsonPath.parse(resp.getBody());
                Map row = context.json();
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
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        if (request.getEntitySchema().isCustom()){
            return updateCustomObject(request);
        }
        BiMap<String, String> objectFieldIdMap = getObjectFieldIdMap(request, request.getConnector());
        SyncResponse response = new SyncResponse();
        ConcurrentLinkedQueue<Result> results = new ConcurrentLinkedQueue<>();
        Integer threadCount = getThreadCount(request);
        List<List<EntityData>> partitions = partitionIntoNParts(request.getData().get(request.getConnector().getId()), threadCount);
        if(threadCountTracker.get() < MAX_THREAD_LIMIT && request.getConnector().getInternalConfig().containsKey("threadCount")) {
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            // Increment the thread count tracker by the number of threads created
            threadCountTracker.addAndGet(threadCount);

            // Create a list of CompletableFuture tasks
            List<CompletableFuture<Void>> futures = partitions.stream()
                    .map(partition -> CompletableFuture.runAsync(() -> update(request, partition, objectFieldIdMap, results, response), executorService))
                    .collect(Collectors.toList());

            // Combine all futures and wait for all of them to complete
            awaitFuturesAndFinalizeResponse(futures, executorService, threadCount, response, results);

            // Decrement the thread count tracker by the number of threads created
            threadCountTracker.addAndGet(-threadCount);

        } else {
            log.error("Max thread count reached {}. Falling back to single thread mode", threadCountTracker.get());
            update(request, request.getData().get(request.getConnector().getId()), objectFieldIdMap, results, response);
            response.setResults(new ArrayList<>(results));
        }
        return response;
    }

    private void update(SyncRequest request, List<EntityData> partition, BiMap<String, String> objectFieldIdMap, ConcurrentLinkedQueue<Result> results, SyncResponse response) {
        partition.forEach(e -> {
            try {
                String updateEntityUrl = eloquaEnpointCache.getUnchecked(request.getConnector().getAuthConfig()) + String.format(ENTITY_BY_ID_ENDPOINT,
                        request.getEntityName().toLowerCase(), e.getId());
                Optional<EntityData> recdData = getById(updateEntityUrl, objectFieldIdMap, request);
                if (recdData.isPresent()) {
                    EntityData ed = recdData.get();
                    e.getValues().forEach((k, v) -> {
                        ed.addValue(k,v);
                    });
                    Map<String, Object> payload = createPayload(ed, objectFieldIdMap.inverse(), request.getEntitySchema(), false);
                    payload.put("id", e.getId());
                    getClient().put(updateEntityUrl, mapper.writeValueAsString(payload), request.getConnector().getAuthConfig());
                    results.add(new Result(true, e.getId(), e.getSyncariEntityId()));
                } else {
                    Result result = new Result(false, e.getId(), e.getSyncariEntityId());
                    result.addError("Record cant be updated and might have been deleted at the destination");
                    result.setErrorCode(ErrorCodes.DATA_NOT_FOUND.name());
                    results.add(result);
                }
            } catch (Exception e1) {
                if (e1 instanceof NonRetriableException &&
                        ErrorCodes.TOO_MANY_REQUESTS.toString().equals(((NonRetriableException) e1).getErrorCode())) {
                    handleException(e1, request.getConnector());
                }
                log.error(ExceptionUtils.getStackTrace(e1));
                Result result = new Result(false, e.getId(), e.getSyncariEntityId());
                result.addError(e1.getMessage());
                results.add(result);
                response.setSuccess(false);
            }
        });
    }

    private SyncResponse updateCustomObject(SyncRequest request){
        BiMap<String, String> objectFieldIdMap = getObjectFieldIdMap(request, request.getConnector());
        SyncResponse response = new SyncResponse();
        ConcurrentLinkedQueue<Result> results = new ConcurrentLinkedQueue<>();
        Integer threadCount = getThreadCount(request);
        List<List<EntityData>> partitions = partitionIntoNParts(request.getData().get(request.getConnector().getId()), threadCount);
        if(threadCountTracker.get() < MAX_THREAD_LIMIT && request.getConnector().getInternalConfig().containsKey("threadCount")) {
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            // Increment the thread count tracker by the number of threads created
            threadCountTracker.addAndGet(threadCount);

            // Create a list of CompletableFuture tasks
            List<CompletableFuture<Void>> futures = partitions.stream()
                    .map(partition -> CompletableFuture.runAsync(() -> updateCustomObject(request, partition, objectFieldIdMap, results, response), executorService))
                    .collect(Collectors.toList());

            // Combine all futures and wait for all of them to complete
            awaitFuturesAndFinalizeResponse(futures, executorService, threadCount, response, results);

            // Decrement the thread count tracker by the number of threads created
            threadCountTracker.addAndGet(-threadCount);
        } else {
            log.error("Max thread count reached {}. Falling back to single thread mode", threadCountTracker.get());
            updateCustomObject(request, request.getData().get(request.getConnector().getId()), objectFieldIdMap, results, response);
            response.setResults(new ArrayList<>(results));
        }
        return response;
    }

    private void updateCustomObject(SyncRequest request, List<EntityData> partition, BiMap<String, String> objectFieldIdMap, ConcurrentLinkedQueue<Result> results, SyncResponse response) {
        partition.forEach(e -> {
            try {
                String updateEntityUrl = eloquaEnpointCache.getUnchecked(request.getConnector().getAuthConfig()) + String.format(CUSTOM_OBJECT_BY_ID_ENDPOINT,
                        extractCustomObjectId(request.getEntityName()), e.getId());
                validateCustomObjectMappedContactAndAccount(e, request.getConnector());
                Map payload = createPayload(e, objectFieldIdMap.inverse(), request.getEntitySchema(), true);
                payload.put("id", e.getId());
                getClient().put(updateEntityUrl, mapper.writeValueAsString(payload), request.getConnector().getAuthConfig());
                results.add(new Result(true, e.getId(), e.getSyncariEntityId()));
            } catch (Exception ex) {
                if (ex instanceof NonRetriableException &&
                        ErrorCodes.TOO_MANY_REQUESTS.toString().equals(((NonRetriableException) ex).getErrorCode())) {
                    handleException(ex, request.getConnector());
                }
                String errorMessage = ex.getMessage();
                if(ex instanceof NonRetriableException &&
                        ErrorCodes.BAD_ENDPOINT.toString().equals(((NonRetriableException) ex).getErrorCode()) &&
                        "404 NOT_FOUND".equals(ex.getMessage())){
                    errorMessage = String.format("Entity with id %s is not found", e.getId());
                }
                Result result = new Result(false, e.getId(), e.getSyncariEntityId());
                result.addError(errorMessage);
                results.add(result);
                response.setSuccess(false);
            }
        });
    }

    private void validateCustomObjectMappedContactAndAccount(EntityData customObject, ConnectorInfo connector){
        if (customObject.getValue("contactId") != null) {
            validateRelatedObjectById(customObject.getValue("contactId").toString(), Constants.CONTACT, connector);
        } else if (customObject.hasValue("accountId")) {
            validateRelatedObjectById(customObject.getValue("accountId").toString(), Constants.ACCOUNT, connector);
        }
    }

    private void validateRelatedObjectById(String objectId, String objectType, ConnectorInfo connector){
        String getEntityId = eloquaEnpointCache.getUnchecked(connector.getAuthConfig()) + String.format(ENTITY_BY_ID_ENDPOINT, objectType.toLowerCase(), objectId);
        try{
            getClient().getResponse(getEntityId, connector.getAuthConfig());
        } catch (QuotaExceededException | RetriableException e) {
            handleException(e, connector);
        } catch (NonRetriableException ex) {
            if (ErrorCodes.BAD_ENDPOINT.toString().equals(((NonRetriableException) ex).getErrorCode()) &&
                    "404 NOT_FOUND".equals(ex.getMessage())) {
                throw new NonRetriableException(ErrorCodes.DATA_NOT_FOUND,
                        String.format("Mapped %s object with id %s is not found or deleted", objectType.toLowerCase(), objectId),
                        HttpStatus.NOT_FOUND.toString());
            }
            throw ex;
        }
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        SyncResponse response = new SyncResponse();
        ConcurrentLinkedQueue<Result> results = new ConcurrentLinkedQueue<>();

        Integer threadCount = getThreadCount(request);
        List<List<EntityData>> partitions = partitionIntoNParts(request.getData().get(request.getConnector().getId()), threadCount);
        if(threadCountTracker.get() < MAX_THREAD_LIMIT && request.getConnector().getInternalConfig().containsKey("threadCount")) {
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            // Increment the thread count tracker by the number of threads created
            threadCountTracker.addAndGet(threadCount);

            // Create a list of CompletableFuture tasks
            List<CompletableFuture<Void>> futures = partitions.stream()
                    .map(partition -> CompletableFuture.runAsync(() -> delete(request, partition, results, response), executorService))
                    .collect(Collectors.toList());

            // Combine all futures and wait for all of them to complete
            awaitFuturesAndFinalizeResponse(futures, executorService, threadCount, response, results);

            // Decrement the thread count tracker by the number of threads created
            threadCountTracker.addAndGet(-threadCount);
        } else {
            log.error("Max thread count reached {}. Falling back to single thread mode", threadCountTracker.get());
            delete(request, request.getData().get(request.getConnector().getId()), results, response);
            response.setResults(new ArrayList<>(results));
        }
        return response;
    }

    private void delete(SyncRequest request, List<EntityData> partition, ConcurrentLinkedQueue<Result> results, SyncResponse response) {
        partition.forEach(e -> {
            if (!StringUtils.isNumeric(e.getId())) {
                throw new NonRetriableException(ErrorCodes.BAD_REQUEST,
                        String.format("Expecting numeric value for id, received a non-numeric (or) null value: %s", e.getId()),
                        HttpStatus.BAD_REQUEST.toString());
            }
            try {
                String url = request.getEntitySchema().isCustom() ? CUSTOM_OBJECT_BY_ID_ENDPOINT : ENTITY_BY_ID_ENDPOINT;
                String entity = request.getEntitySchema().isCustom() ? extractCustomObjectId(request.getEntityName()) : request.getEntityName().toLowerCase();
                String deleteByIdUrl = eloquaEnpointCache.getUnchecked(request.getConnector().getAuthConfig()) + String.format(url,
                        entity, e.getId());
                getClient().delete(deleteByIdUrl, request.getConnector().getAuthConfig());
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
                if(!exError.contains("{") || !exError.contains("}")) {
                    Throwable throwable = e.getCause();
                    if(throwable instanceof NonRetriableException) {
                        exError = throwable.getMessage();
                    }
                }

                String message = "Unknown Error";
                if(exError.contains("{") && exError.contains("}")) {
                    int startingIndex = exError.indexOf("{") - 1;
                    int closingIndex = exError.indexOf("}") + 1;
                    String errorMessage = exError.substring(startingIndex + 1, closingIndex);
                    JsonNode node = mapper.readValue(errorMessage, JsonNode.class);
                    message = node.get("message").asText();
                }
                result.setMessage(message);
                result.setCode(HttpStatus.UNAUTHORIZED.name());
                result.setErrors(List.of("401 Unauthorized"));
            } catch (Exception e2) {
                e2.printStackTrace();
                result.setMessage("Unknown Error");
                result.setCode(HttpStatus.UNAUTHORIZED.name());
            }
        }
        return result;
    }

    @Override
    public void handleAuthenticationErrorMessage(TestConnectionResponse response, Exception e) {
        AuthenticationService.super.handleAuthenticationErrorMessage(response, e);
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        if (!EloquaSeed.SUPPORTED_ENTITIES.contains(request.getEntity())){
            String entity = extractCustomObjectId(request.getEntity());
            String describeEndpoint = eloquaEnpointCache.getUnchecked(request.getConnector().getAuthConfig()) + String.format(GET_CUSTOM_OBJECTS_BY_ID_ENDPOINT, entity);
            ResponseEntity<String> response = getClient().getResponse(describeEndpoint, request.getConnector().getAuthConfig());
            if(response.getStatusCode() == HttpStatus.OK) {
                ReadContext responseBody = JsonPath.parse(response.getBody());
                Map row = responseBody.read("$");
                EntitySchema schema = processRow(request.getConnector(), row);
                return Optional.of(schema);
            } else {
                return Optional.empty();
            }
        }
        ConnectorInfo connectorInfo = request.getConnector();
        return Optional.ofNullable(toEntitySchema(request.getEntity(), connectorInfo));
    }

    private HashBiMap<String, String> getObjectFieldIdMap(SyncRequest request, ConnectorInfo connector){
        String entityName = request.getEntitySchema().isCustom() ? extractCustomObjectId(request.getEntityName()) : request.getEntityName();
        Map<String, String > objectFieldIdMap = new HashMap<>();
        String url = request.getEntitySchema().isCustom() ? GET_CUSTOM_OBJECTS_BY_ID_ENDPOINT : GET_ENTITY_FIELDS_ENDPOINT;
        String describeEndpoint = eloquaEnpointCache.getUnchecked(connector.getAuthConfig()) + String.format(url, entityName);
        ResponseEntity<String> response = getClient().getResponse(describeEndpoint, connector.getAuthConfig());
        ReadContext responseBody = JsonPath.parse(response.getBody());
        String path = request.getEntitySchema().isCustom() ? "fields" : "elements";
        List<Map> rows = responseBody.read(path);
        for (Map map : rows) {
            objectFieldIdMap.put(map.get("id").toString(), map.get("internalName").toString());
        }
        return HashBiMap.create(objectFieldIdMap);
    }

    private List<String> getPicklistValues (String optionalListId, ConnectorInfo connector){
        Set<String> pickListValues = new HashSet<>();
        try {
            String optionalListValueUrl = eloquaEnpointCache.getUnchecked(connector.getAuthConfig()) + String.format(GET_OPTION_LIST_VALUES_ENDPOINT, optionalListId);
            ResponseEntity<String> response = getClient().getResponse(optionalListValueUrl, connector.getAuthConfig());
            ReadContext responseBody = JsonPath.parse(response.getBody());
            List<Map> rows = responseBody.read("elements");
            for (Map map : rows) {
                String displayName = (String) map.getOrDefault("displayName", "");
                String value = (String) map.getOrDefault("value", "");
                if (!displayName.equals("Please Select...")) {
                    pickListValues.add(value);
                }
            }
        } catch (JsonPathException e) {
            log.info("No values found for optionList {}", optionalListId);
        }
        return pickListValues.stream().collect(Collectors.toList());
    }

    private String getPredicateKey(String entityName) {
        return entityName.toLowerCase()+"_"+Constants.SYNCARI_SRC_PREDICATE;
    }

    private String getPredicateValue(SyncRequest request){
        return request.getSourceParams().getOrDefault(getPredicateKey(request.getEntityName()), "").toString();
    }

    private String getOptimizedPredicateString(SyncRequest request){
        String queryPredicateString = getPredicateValue(request);

        // Replace any spaces inside predicate
        return queryPredicateString.replaceAll("\\s+(?=(?:(?:[^']*'){2})*[^']*$)", "");
    }

    private void validateEntitySourceParams(SyncRequest syncRequest){
        String queryPredicate = getOptimizedPredicateString(syncRequest);
        if (StringUtils.containsIgnoreCase(queryPredicate, "customObjectRecordStatus=")
        ) {
            throw new NonRetriableException(ErrorCodes.BAD_REQUEST,
                    "Check entity source predicate: "+getPredicateValue(syncRequest),
                    ErrorCodes.BAD_REQUEST.name());
        }
    }

    @Override
    public boolean validateEntityConfig(EntityParams params) {
        if(params == null || params.getSchema() == null || params.getSourceParams() == null ||
                params.getSourceParams().isEmpty()) return true;
        SyncRequest request = new SyncRequest().setSourceParams(params.getSourceParams())
                .setConnector(params.getConnector()).setPageSize(1).setWatermark(new WatermarkInfo()
                        .setStart(Instant.EPOCH.toEpochMilli()).setEnd(Instant.EPOCH.toEpochMilli() + 1))
                .setEntitySchema(params.getSchema());
        validateEntitySourceParams(request);
        FetchResponse response = getByWatermark(request);
        log.debug("Response received ", response);
        try {
            if(response.getIterator().hasNext()) {
                response.getIterator().next();
            }
        } catch (Exception e) {
            String errorMessage = "Check entity source predicate: "+ getPredicateValue(request) + ", Error:"+e.getMessage();

            if( e instanceof NonRetriableException && ErrorCodes.BAD_REQUEST.name().equals(((NonRetriableException)e).getErrorCode())){
                throw new NonRetriableException(ErrorCodes.BAD_REQUEST, errorMessage, ErrorCodes.BAD_REQUEST.name());
            }

            throw new RuntimeException(errorMessage, e);
        }
        return true;
    }

    private EntitySchema toEntitySchema(String entityName, ConnectorInfo connector) {
        String describeEndpoint = eloquaEnpointCache.getUnchecked(connector.getAuthConfig()) + String.format(GET_ENTITY_FIELDS_ENDPOINT, entityName.toLowerCase());
        ResponseEntity<String> response = getClient().getResponse(describeEndpoint, connector.getAuthConfig());
        ReadContext responseBody = JsonPath.parse(response.getBody());
        EntitySchema entitySchema = EloquaSeed.getSeedEntitySchema(entityName);
        AttributeSchema predicate = new AttributeSchema(getPredicateKey(entitySchema.getApiName()), "textarea");
        predicate.setDisplayName("Filter Query for source");
        entitySchema.getSourceParams().add(predicate);
        List<Map> rows = responseBody.read("elements");
        String waterMarkField = WATERMARK_FIELD_MAP.getOrDefault(entityName.toLowerCase(), "ModifiedAt" );
        for (Map map : rows) {
            parseField(entityName, connector, entitySchema, waterMarkField, map);
        }
        return entitySchema;
    }

    private void parseField(String entityName, ConnectorInfo connector, EntitySchema entitySchema, String waterMarkField, Map map) {
        String apiName = map.get("internalName").toString();
        String displayName = map.get("name").toString();
        AttributeSchema attr = new AttributeSchema();
        attr.setApiName(apiName);
        attr.setDisplayName(displayName);
        String dataType = map.getOrDefault("dataType", "text").toString();
        // Eloqua uses date but send datetime to seconds
        if ("date".equals(dataType)){
            dataType = "datetime";
        }
        String displayType = map.getOrDefault("displayType", "text").toString();
        if ("text".equalsIgnoreCase(displayType) && "numeric".equalsIgnoreCase(dataType)){
            attr.setDataType("double");
        } else if ("text".equalsIgnoreCase(displayType)){
            attr.setDataType(dataType);
        } else if ("checkbox".equalsIgnoreCase(displayType)){
            // Eloqua checkbox comes with values instead of true or false hence changing it to picklist
            attr.setDataType("picklist");
            attr.setSubDataType(dataType);
            attr.setPicklistValues(List.of(map.getOrDefault("uncheckedValue", "").toString(), map.getOrDefault("checkedValue", "").toString()));
        } else if ("singleSelect".equalsIgnoreCase(displayType) || "multiSelect".equalsIgnoreCase(displayType)){
            if ("multiSelect".equalsIgnoreCase(displayType)){
                attr.setMultiValueField(true);
            }
            attr.setDataType("picklist");
            attr.setSubDataType(dataType);
            String optionList = map.getOrDefault("optionListId", "").toString();
            if (StringUtils.isNotBlank(optionList)){
                PicklistOptionCacheKey key = new PicklistOptionCacheKey(connector, optionList);
                try {
                    List<String> picklistValues = picklistOptionCache.get(key);
                    attr.setPicklistValues(picklistValues);
                } catch (UncheckedExecutionException | ExecutionException e) {
                    Throwable cause = e.getCause();
                    if(cause instanceof NonRetriableException && cause.getMessage().equalsIgnoreCase("404 NOT_FOUND")) {
                        log.error("Not setting picklist values for entity/field - {}/{} because of 404 error from Eloqua", entityName, apiName);
                    }
                }
            }
        } else {
            // Only case is data type being Long Text in that case we set it to text
            attr.setDataType("text");
        }

        if(StringUtils.isNotBlank(map.getOrDefault("defaultValue", "").toString())){
            attr.setDefaultValue(map.get("defaultValue").toString());
        }

        attr.setUpdateable(!Boolean.parseBoolean(map.getOrDefault("isReadOnly", "false").toString()));
        attr.setCustom(!Boolean.parseBoolean(map.getOrDefault("isStandard", "false").toString()));
        attr.setNillable(!Boolean.parseBoolean(map.getOrDefault("isRequired", "false").toString()));
        if(apiName.equalsIgnoreCase(waterMarkField)){
            attr.setWatermarkField(true).setSystem(true).setUpdateable(false).setNillable(false);
        }
        entitySchema.addField(attr);
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        List<String> entities = EloquaSeed.SUPPORTED_ENTITIES;
        List<EntitySchema> schemaList = new ArrayList<>();
        entities.forEach(e -> {
            EntitySchema entity = toEntitySchema(e, request.getConnector());
            schemaList.add(entity);
        });
        schemaList.addAll(fetchCustomObjectSchemas(request));
        return schemaList;
    }

    private List<EntitySchema> fetchCustomObjectSchemas(DescribeAllRequest request) {
        List<EntitySchema> customObjects = new ArrayList<>();
        int startingPage = 1;
        int fetchedSchemas = 0;
        while(startingPage == 1 || fetchedSchemas == 1000) {
            String describeEndpoint = eloquaEnpointCache.getUnchecked(request.getConnector().getAuthConfig()) + String.format(GET_CUSTOM_OBJECTS_ENDPOINT, startingPage);
            ResponseEntity<String> response = getClient().getResponse(describeEndpoint, request.getConnector().getAuthConfig());
            ReadContext responseBody = JsonPath.parse(response.getBody());
            List<Map> rows = responseBody.read("elements");
            rows.forEach(row -> {
                EntitySchema entitySchema = processRow(request.getConnector(), row);
                customObjects.add(entitySchema);
            });
            fetchedSchemas = rows.size();
            startingPage += 1;
        }
        return customObjects;
    }

    private EntitySchema processRow(ConnectorInfo connectorInfo, Map row) {
        String apiName = CUSTOM_OBJECT_PREFIX + (String) row.get("id");
        EntitySchema entitySchema = EloquaSeed.getCustomObjectSeedEntitySchema();
        entitySchema.setApiName(apiName);
        entitySchema.setDisplayName((String) row.get("name"));
        entitySchema.setCustom(true);
        List<Map> fields = (List<Map>) row.get("fields");
        fields.forEach(field -> {
            parseField(apiName, connectorInfo, entitySchema, "updatedAt", field);
        });
        AttributeSchema predicate = new AttributeSchema(getPredicateKey(entitySchema.getApiName()), "textarea");
        predicate.setDisplayName("Filter Query for source");
        entitySchema.getSourceParams().add(predicate);
        return entitySchema;
    }

    private String extractCustomObjectId(String apiName) {
        if(!apiName.contains(CUSTOM_OBJECT_PREFIX)) {
            throw new RuntimeException("Api Name does not contain custom object prefix");
        }
        return apiName.substring(CUSTOM_OBJECT_PREFIX.length());
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

    public SyncariEntityDataRestClient getClient() {
        return new EloquaRestClient(getSingleJsonConfig(), mapper);
    }

    @Override
    public List<Capability> getCapabilities() {
        return List.of(Capability.create, Capability.update, Capability.delete, Capability.search, Capability.getById, Capability.getByWatermark);
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
}
class EloquaIterator extends DefaultDataIterator {

    public EloquaIterator(WatermarkInfo baseWatermark, long offset, Function3<WatermarkInfo, Integer, Long,
            Pair<Long, Stream<EntityData>>> generator, List<EntityData> data, AttributeSchema watermarkField, int pageSize, int maxRecords) {
        super(baseWatermark, offset, generator, data, watermarkField,pageSize,maxRecords);
    }

    @Override
    protected boolean isLastPage() {
        return data.size() == 0;
    }

    protected long nextOffset(Pair<Long, Stream<EntityData>> results, List<EntityData> data) {
        if(data.isEmpty()) return 0;
        return (offset == 0 ? 1 : offset) + 1;
    }

    @Override
    public long getLastOffset() {
        return offset;
    }

    @Override
    public Offset getOffsetInfo() {
        return new Offset(Offset.OffsetType.PAGE_NUMBER, getEffectivePageSize());
    }
}
