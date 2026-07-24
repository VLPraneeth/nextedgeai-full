package com.syncari.connector.aws.dynamodb;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.syncari.connector.Capability;
import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.aws.AWSRestClient;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.DefaultCursorBasedIterator;
import com.syncari.connector.data.iterator.LocalStorageService;
import com.syncari.connector.exception.ConnectorException;
import com.syncari.connector.service.Transformer;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.utils.DateUtil;
import com.syncari.utils.file.S3FileManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.jooq.lambda.function.Function3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component(Constants.DYNAMODB)
public class DynamoDbService implements SynapseInfoService, MetadataService, AuthenticationService, CommonDataService {

    @Autowired
    Transformer transformer;
    @Autowired
    ObjectMapper mapper;

    @Autowired
    DateUtil dateUtil;

    @Autowired
    LocalStorageService localStorageService;
    public static int API_MAX_PAGESIZE = 200;
    public static int MAX_RECORD_ONE_CYCLE=2000;
    public static int MAX_RECORDS_ONE_PUT_BATCH=25;

    public static final long _WATERMARK_INCREMENT = 1 * 24 * 60 * 60 * 1l; //1 days

    private static final String dateFormat = "yyyy-MM-dd HH:mm:ss Z";

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getS3Auth());
    }

    @Override
    public List<Capability> getCapabilities() {
        var capabilities = new ArrayList<Capability>();
        capabilities.add(Capability.schemaEditInSyncari);
        capabilities.add(Capability.userEditableId);
        capabilities.add(Capability.schemaCreateField);
        capabilities.add(Capability.userEditableWm);
        capabilities.add(Capability.compositeId);
        return capabilities;
    }

    @Override
    public boolean validate(ConnectorInfo connector) {
        String regionName = connector.getMetaConfig().getOrDefault("region", "").toString();
        if (StringUtils.isBlank(regionName)) {
            throw new RuntimeException("region_name_required");
        }
        return new AWSRestClient(getSingleJsonConfig()).isRegionSupported(regionName);
    }

    @Override
    public List<AuthField> getConfigureFields() {
        AuthField region = new AuthField().setRequired(true).setDataType("text").setName("region")
                .setLabel("Region").setHelpSummary(i18n("dynamo_region_help"));
        return List.of(region,ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public Map<String, String> getEntityMappings() {
        return Map.of();
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    @Override
    public String getName() {
        return Constants.DYNAMODB;
    }

    @Override
    public String getCategory() {
        return "Database";
    }

    @Override
    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/dynamodb.svg")
                .setDisplayName("Amazon DynamoDB")
                .setBackgroundColor("#F9F9F9")
                .setHelpUrl(helpArticlesBaseUrl + "/4407683403540");
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19203348744724";
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        ConnectorInfo connectorInfo = request.getConnector();
        AuthConfig config = connectorInfo.getAuthConfig();
        EntitySchema entitySchema = null;
        try {
            // Describe results call only tables and no attributes of table, it is because describe call does not return anything.
            entitySchema = toEntitySchema(request.getEntity());
        } catch (AmazonServiceException e) {
            log.error(e.getErrorMessage());
        }
        return Optional.ofNullable(entitySchema);
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        // first list tables and then for each table call describe
        List<String> tableNames = listTables(request.getConnector());
        log.info("All Tables: {}", StringUtils.join(tableNames, ", "));
        final List<EntitySchema> entitySchemaList = new ArrayList<>();
        tableNames.forEach(tableName -> {
            Optional<EntitySchema> entitySchema = describe(new DescribeRequest(request.getConnector(), tableName));
            entitySchema.ifPresent(schema -> entitySchemaList.add(schema));
        });
        return entitySchemaList;
    }

    private EntitySchema toEntitySchema(String tableName){
        EntitySchema entitySchema = new EntitySchema();
        entitySchema.setApiName(tableName);
        entitySchema.setDisplayName(tableName);
        return entitySchema;
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new RuntimeException("createObject not supported in " + this.getUIMetadata().getDisplayName()  + " yet");
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
        return new JsonParserConfig(null, null, null, StringUtils.capitalize("Id"), true, null);
    }

    protected AWSRestClient getClient() {
        return new AWSRestClient(getSingleJsonConfig(), mapper);
    }

    public List<String> listTables(ConnectorInfo connectorInfo) {
        AuthConfig config = connectorInfo.getAuthConfig();
        AmazonDynamoDB ddb = S3FileManager.getDDBClient(config.getAccessToken(), config.getClientSecret(), connectorInfo.getMetaConfig().get("region").toString());
        ListTablesRequest request;
        List<String> tableNames = new ArrayList<>();
        boolean more_tables = true;
        String last_tablename = null;

        while(more_tables) {
            if (last_tablename != null) {
                request = new ListTablesRequest()
                        .withLimit(50)
                        .withExclusiveStartTableName(last_tablename);
            } else {
                request = new ListTablesRequest().withLimit(50);
            }
            ListTablesResult table_list = ddb.listTables(request);
            last_tablename = table_list.getLastEvaluatedTableName();
            if (last_tablename == null) {
                more_tables = false;
            }
            tableNames.addAll(table_list.getTableNames());
        }
        return tableNames;

    }
    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse result = new TestConnectionResponse();
        try {
            if(StringUtils.isBlank(config.getAuthConfig().getAccessToken())) {
                throw new RuntimeException(i18n("access_key_required"));
            }
            if(StringUtils.isBlank(config.getAuthConfig().getClientSecret())) {
                throw new RuntimeException(i18n("secret_key_required"));
            }
            if(!config.getMetaConfig().containsKey("region") || StringUtils.isBlank(config.getMetaConfig().get("region").toString())) {
                throw new RuntimeException(i18n("client_region_required"));
            }
            List<String> tableNames = this.listTables(config);
            result.setCode("200");
        } catch (ConnectorException e) {
            result.setMessage(e.getMessage());
            result.setCode(HttpStatus.UNAUTHORIZED.name());
        } catch (Exception e) {
            result.setMessage(e.getMessage());
            result.setCode(HttpStatus.UNAUTHORIZED.name());
        }
        return result;
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        WatermarkInfo watermark = request.getWatermark();
        Long start = Instant.ofEpochMilli(watermark.getStart()).truncatedTo(ChronoUnit.SECONDS).getEpochSecond();
        Long end = Instant.ofEpochMilli(watermark.getEnd()).truncatedTo(ChronoUnit.SECONDS).getEpochSecond();
        WatermarkInfo initialWatermark = new WatermarkInfo(start, end, watermark.isInitial(), watermark.getOffset());
        WatermarkInfo windowedWatermark = new WatermarkInfo(start, end, watermark.isInitial(), watermark.getOffset());
        if (!watermark.isInitial() && !watermark.isResync()){
            windowedWatermark.setEnd(Math.min(end,start + _WATERMARK_INCREMENT));
        }

        ConnectorInfo connectorInfo = request.getConnector();
        AuthConfig config = connectorInfo.getAuthConfig();
        AmazonDynamoDB ddb = S3FileManager.getDDBClient(config.getAccessToken(), config.getClientSecret(), connectorInfo.getMetaConfig().get("region").toString());

        Function3<WatermarkInfo, Integer, String, DataWithCursor> generator = (wm, pageSize, changeStream) -> {
            String startT = (String.valueOf(windowedWatermark.getStart()));
            String endT = (String.valueOf(windowedWatermark.getEnd()));
            log.info("Using Start Watermark {}, end {}",windowedWatermark.getStart(),windowedWatermark.getEnd());
            Map<String, String> expressionAttribNames = new HashMap<>();
            String attribNameForQuery = "#" + RandomStringUtils.random(3,"ABCDEFGHIJKLMNOPQRSTUVWXYZ");
            expressionAttribNames.put(attribNameForQuery,request.getEntitySchema().getWatermarkField().getApiName());
            String filterExpression = String.format("%s between :val1 and :val2",attribNameForQuery);
            Map<String, AttributeValue> expressionAttributeValues =
                    new HashMap<String, AttributeValue>();
            expressionAttributeValues.put(":val1", new AttributeValue().withN(startT));
            expressionAttributeValues.put(":val2", new AttributeValue().withN(endT));

            ScanRequest scanRequest = new ScanRequest(request.getEntityName()).withFilterExpression(filterExpression)
                    .withExpressionAttributeValues(expressionAttributeValues).withExpressionAttributeNames(expressionAttribNames);
            if (watermark.isResync()){
                scanRequest.setLimit(pageSize);
            }
            try{
                if (StringUtils.isNotEmpty(changeStream)){
                    TypeReference<HashMap<String, AttributeValue>> typeRef
                            = new TypeReference<>() {};
                    Map<String, AttributeValue> lastKeyEvaluated = mapper.readValue(changeStream,typeRef);
                    if ((null != lastKeyEvaluated) && (MapUtils.isNotEmpty(lastKeyEvaluated))){
                        lastKeyEvaluated.entrySet().forEach(entry -> scanRequest.addExclusiveStartKeyEntry(entry.getKey(), entry.getValue()));
                    }
                }
            }catch (JsonProcessingException exception){
                log.error("Could not parse the incoming last evaluated key change stream {} with exception {}", changeStream, exception);
            }

            ScanResult result = ddb.scan(scanRequest);
            return processResponse(result, changeStream, request);
        };

        localStorageService.provisionIfNotExists(request, Constants.DYNAMODB + request.getEntityName());
        if (watermark.isResync() && start==0){
            localStorageService.cleanupDB(request);
        }
        long maxLocalWatermark = (watermark.isResync() && start==0) ? 0 : localStorageService.maxWatermark(request.getConnector(), request.getEntityName());
        long startWaterMarkToSetIfGreater = maxLocalWatermark/1000;
        if(startWaterMarkToSetIfGreater > start) {
            long endWaterMarkToSet = Math.min(Math.max(initialWatermark.getEnd(),startWaterMarkToSetIfGreater),startWaterMarkToSetIfGreater+_WATERMARK_INCREMENT);
            windowedWatermark.setStart(startWaterMarkToSetIfGreater).setEnd(endWaterMarkToSet);
        }

        int pgSize = (request.getPageSize() <= 0) ? API_MAX_PAGESIZE : request.getPageSize();
        DefaultCursorBasedIterator iterator = new DefaultCursorBasedIterator(request.getWatermark(),
                request.getWatermark().getChangeStream(),
                request.getWatermark().getOffset(), generator, new ArrayList<>(),
                pgSize, request.getWatermark().getLimit(),true);
        localStorageService.fetch(request,iterator);
        return localStorageService.getByWatermark(request);
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        if(!request.getEntitySchema().hasIdField()) {
            throw new RuntimeException("Id field not defined for entity " + request.getEntityName());
        }
        if(!request.getEntitySchema().hasCompositeKeyFields()) {
            return getByIdsNotCompositeKey(request);
        }
        List<Map<String, String>> idsAndCompositeKey = getIdAndCompositeKeyCombination(request);
        if (CollectionUtils.isEmpty(idsAndCompositeKey)) {
            throw new RuntimeException("Incoming Ids and composited field not defined for entity" + request.getEntityName());
        }
        List<Map<String, String>> expresAttribNamesList = new ArrayList<>();
        List<String> filterExpression = IntStream.range(0,idsAndCompositeKey.size()).mapToObj(i -> {
            Map<String, String> expresAttribNames = new HashMap<>();
            Map<String, String> keyMap =  idsAndCompositeKey.get(i);
            Set<String> keySet = keyMap.keySet();
            String innerString =  StringUtils.join(keySet.stream().map(apiNameAsKey ->  {
                String name = "#" + RandomStringUtils.random(3,"ABCDEFGHIJKLMNOPQRSTUVWXYZ");
                expresAttribNames.put(name, apiNameAsKey);
                return String.format("%s=%s",name,":val_"+apiNameAsKey+"_"+i);
            }).collect(Collectors.toList())," AND ");
            expresAttribNamesList.add(expresAttribNames);
            return "(" + innerString + ")";
        }).collect(Collectors.toList());
        log.info("Filter expression used is {}", filterExpression);

        List<AttributeSchema> compositeKeySchema = request.getEntitySchema().getCompositeKeyFields();
        List<Map<String, AttributeValue>> expressionAttributeValuesList = new ArrayList<>();

        IntStream.range(0,idsAndCompositeKey.size()).forEach(i -> {
            Map<String, AttributeValue> exprAttribMap = new HashMap<>();
            Map<String, String> keyMap =  idsAndCompositeKey.get(i);

            //assert(keyMap.size()==compositeKeySchema.size() : "compositeKeys in schema size should be equal to keyset in request");
            IntStream.range(0,keyMap.size()).forEach(index -> {
                String compositeKeyApiName = compositeKeySchema.get(index).getApiName();
                String compositeKeyDataType = compositeKeySchema.get(index).getDataType();
                if(keyMap.containsKey(compositeKeyApiName)){
                    if (compositeKeyDataType.equals("int") || (compositeKeyDataType.equals("integer")) || isDateRelatedField(Optional.of(compositeKeyDataType))){
                        exprAttribMap.put(":val_" + compositeKeyApiName + "_" + i, new AttributeValue().withN(keyMap.get(compositeKeyApiName)));
                    }else{
                        exprAttribMap.put(":val_" + compositeKeyApiName + "_" + i, new AttributeValue().withS(keyMap.get(compositeKeyApiName)));
                    }
                }
            });
            expressionAttributeValuesList.add(exprAttribMap);
        });

        List<EntityData> result  = new ArrayList<>();
        ConnectorInfo connectorInfo= request.getConnector();
        AuthConfig config = connectorInfo.getAuthConfig();
        AmazonDynamoDB ddb = S3FileManager.getDDBClient(config.getAccessToken(), config.getClientSecret(), connectorInfo.getMetaConfig().get("region").toString());

        IntStream.range(0,filterExpression.size()).forEach(i -> {
            QueryRequest queryRequest = new QueryRequest().withTableName(request.getEntityName()).withKeyConditionExpression(filterExpression.get(i))
                    .withExpressionAttributeValues(expressionAttributeValuesList.get(i)).withLimit(API_MAX_PAGESIZE).withExpressionAttributeNames(expresAttribNamesList.get(i));
            QueryResult queryResult = ddb.query(queryRequest);
            //process scanresult to entitydata and add it to result
            result.addAll(processQueryResult(queryResult, "", request));
        });
        return result;
    }

    public List<EntityData> getByIdsNotCompositeKey(SyncRequest request) {
        if(!request.getEntitySchema().hasIdField()) {
            throw new RuntimeException("Id field not defined for entity " + request.getEntityName());
        }

        List<String> ids = getIds(request);
        if (CollectionUtils.isEmpty(ids)) {
            throw new RuntimeException("Incoming Ids field not defined for entity" + request.getEntityName());
        }
        String filterExpressionPart = StringUtils.join(IntStream.range(0, ids.size()).mapToObj(i -> ":val"+i).collect(Collectors.toList()), ",");
        StringBuilder filterExpression = new StringBuilder(String.format("%s IN (%s)",request.getEntitySchema().getIdField().getApiName(),filterExpressionPart));
        List<EntityData> result  = new ArrayList<>();
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        IntStream.range(0, ids.size()).forEach(x-> expressionAttributeValues.put(":val" + x, new AttributeValue().withS(ids.get(x))));
        ScanRequest scanRequest = new ScanRequest(request.getEntityName()).withFilterExpression(filterExpression.toString())
                .withExpressionAttributeValues(expressionAttributeValues)
                .withLimit(API_MAX_PAGESIZE);
        ConnectorInfo connectorInfo= request.getConnector();
        AuthConfig config = connectorInfo.getAuthConfig();
        AmazonDynamoDB ddb = S3FileManager.getDDBClient(config.getAccessToken(), config.getClientSecret(), connectorInfo.getMetaConfig().get("region").toString());
        ScanResult scanResult = ddb.scan(scanRequest);
        //process scanresult to entitydata and add it to result
        result.addAll(processScanResult(scanResult, "", request));
        return result;

    }

    private List<Map<String, String>> getIdAndCompositeKeyCombination(SyncRequest request) {
        List<EntityData> entityList = request.getData().get(request.getConnector().getId());
        String idField = request.getEntitySchema().getIdField().getApiName();
        List<AttributeSchema> compositeKeyFieldList = request.getEntitySchema().getCompositeKeyFields();
        final List<Map<String, String>> result = new ArrayList<>();
        entityList.forEach(e -> {
            Map<String, String> mapToAdd = new HashMap<>();
            String splitArray[] = e.getId().split(Pattern.quote(EntitySchema.COMPOSITE_KEY_DELIMETER));
            //assert(splitArray.length, compositeKeyFieldList.size(), "Size of compositeKey and its values should be same");
            if ((ArrayUtils.isNotEmpty(splitArray)) && (CollectionUtils.isNotEmpty(compositeKeyFieldList)) && (splitArray.length == compositeKeyFieldList.size())){
                IntStream.range(0, compositeKeyFieldList.size()).forEach(x -> mapToAdd.put(compositeKeyFieldList.get(x).getApiName(),splitArray[x]));
            }else {
                mapToAdd.put(idField, e.getId());
            }
            result.add(mapToAdd);
        });
        return result;
    }
    private List<String> getIds(SyncRequest request) {
        List<EntityData> entityList = request.getData().get(request.getConnector().getId());
        return entityList.stream().map(e -> e.getId()).collect(Collectors.toList());
    }

    public DataWithCursor processResponse(ScanResult response, String prevEvalKey, SyncRequest request) {
        log.debug("Data received " + response);
        String lastEvaluatedKey="";
        try {
            Map<String, AttributeValue> lastEvaluatedKeyMap = response.getLastEvaluatedKey();
            if(MapUtils.isNotEmpty(lastEvaluatedKeyMap)){
                lastEvaluatedKey =  mapper.writeValueAsString(lastEvaluatedKeyMap);
            }
        } catch (JsonProcessingException e1) {
            throw new RuntimeException("Failed to read entities.", e1);
        }
        List<EntityData> result = processScanResult(response, prevEvalKey, request);
        return new DataWithCursor(prevEvalKey,lastEvaluatedKey,result);
    }



    public List<EntityData> processScanResult(ScanResult response, String prevEvalKey, SyncRequest request) {
        List<Map<String, AttributeValue>> items = response.getItems();
        if (CollectionUtils.isEmpty(items)) {
            // return empty
            return List.of();
        }
        return processScanorQueryResult(request,items);
    }

    public List<EntityData> processQueryResult(QueryResult response, String prevEvalKey, SyncRequest request) {
        List<Map<String, AttributeValue>> items = response.getItems();
        if (CollectionUtils.isEmpty(items)) {
            // return empty
            return List.of();
        }
        return processScanorQueryResult(request,items);
    }

    private List<EntityData> processScanorQueryResult(SyncRequest request,List<Map<String, AttributeValue>> items ){
        List<EntityData> result = new ArrayList<>();
        EntitySchema entitySchema = request.getEntitySchema();
        String idField = entitySchema.getIdField().getApiName();
        String wmField = entitySchema.getWatermarkField().getApiName();
        List<String> compositeKeyFields = request.getEntitySchema().getCompositeKeyFieldNames();
        for (Object r : items) {
            Map row = (Map) r;
            var ed = new EntityData();
            if (getSingleJsonConfig().isFieldKey()) {
                ed.setName(request.getEntityName());
                //e.setCreatedAt(ZonedDateTime.parse(row.get("CreatedDate").toString()).toEpochSecond()*1000);
                row.forEach((k, v) -> {
                    AttributeValue valueMap = (AttributeValue) v;
                    Optional<AttributeSchema> field = entitySchema.getField(k.toString());
                    Optional<String> datatype = field.isPresent() ?  Optional.of(field.get().getDataType()) : Optional.empty();
                    if (k.toString().equalsIgnoreCase(idField)) {
                        ed.setId(valueMap.getS());
                        ed.addValue(k.toString(),getValueFromAttribvalueMap(datatype,valueMap));
                    }
                    if (compositeKeyFields.contains(k.toString())){
                        ed.addCompositeKey(k.toString(), getValueFromAttribvalueMap(datatype,valueMap));
                        ed.addValue(k.toString(),getValueFromAttribvalueMap(datatype,valueMap));
                    }
                    if (wmField.equalsIgnoreCase(k.toString())) {
                        String wmFieldDataType = request.getEntitySchema().getWatermarkField().getDataType().toLowerCase();
                        ed.setLastModified(getLastModified(Optional.of(wmFieldDataType),valueMap));
                        ed.addValue(k.toString(),getValueFromAttribvalueMap(Optional.of(wmFieldDataType),valueMap));

                    }else{
                        ed.addValue(k.toString(),getValueFromAttribvalueMap(datatype,valueMap));
                    }
                });
                if (CollectionUtils.isNotEmpty(compositeKeyFields) && MapUtils.isNotEmpty(ed.getCompositeKeyData()) && StringUtils.isEmpty(ed.getId())){
                    String idValue = StringUtils.join(IntStream.range(0,compositeKeyFields.size()).mapToObj(i -> ed.getCompositeKeyData().get(compositeKeyFields.get(i))).collect(Collectors.toList()),EntitySchema.COMPOSITE_KEY_DELIMETER);
                    ed.setId(idValue);
                }
            }
            result.add(ed);
        }
        return result;
    }

    private Object getValueFromAttribvalueMap(Optional<String> dataType,AttributeValue valueMap){
        if (!isDateRelatedField(dataType)){
            if (null != valueMap.getS()){
                return valueMap.getS();
            }else if (null != valueMap.getN()){
                return valueMap.getN();
            }else if (null != valueMap.getBOOL()){
                return valueMap.getBOOL();
            }else if (null != valueMap.getL()){
                List<AttributeValue> listVals = valueMap.getL();
                List<Object> listToStore = new ArrayList<>();
                listVals.forEach(x -> {
                    Object item = getValueFromAttribvalueMap(Optional.empty(),x);
                    listToStore.add(item);
                });
                return listToStore;
            }else if (null != valueMap.getM()){
                Map<String, AttributeValue> mapVals = valueMap.getM();
                Map<String, Object> mapToStore = new HashMap<>();
                mapVals.forEach( (x,y) -> {
                    Object valueToStore = getValueFromAttribvalueMap(Optional.empty(), y);
                    mapToStore.put(x,valueToStore);
                });
                return mapToStore;
            }else{
                return valueMap.getS();
            }
        }else{
            return getDateRelatedField(dataType, valueMap);
        }

    }

    private boolean isDateRelatedField(Optional<String> dataType){
        return (dataType.isPresent() && ("timestamp".equalsIgnoreCase(dataType.get()) ||
                "datetime".equalsIgnoreCase(dataType.get()) ||
                "date".equalsIgnoreCase(dataType.get())));
    }

    private Object getDateRelatedField(Optional<String> dataType, AttributeValue valueMap) {
        Object result;
        if (isDateRelatedField(dataType)){
            if (null != valueMap.getS()){
                try {
                    result = ZonedDateTime.parse(valueMap.getS());
                } catch (DateTimeParseException exception) {
                    return valueMap.getS();
                }
            } else if (null != valueMap.getN()){
                //convert to milliseconds, assuming integer is in seconds
                result = ZonedDateTime.ofInstant(Instant.ofEpochSecond(Long.valueOf(valueMap.getN())), ZoneOffset.UTC);;
            }else{
                throw new RuntimeException(String.format("This type of date is not supported from source synapse, valuemap is {} ",valueMap));
            }
        } else{
            throw new RuntimeException("Datatype passed to get data is not supported by this method");
        }
        return result;
    }

    private Long getLastModified(Optional<String> dataType, AttributeValue valueMap) {
        Long result = 0l;
        if (isDateRelatedField(dataType)){
            if (null != valueMap.getS()){
                result = ZonedDateTime.parse(valueMap.getS()).toEpochSecond();
            }else if (null != valueMap.getN()){
                //convert to milliseconds, assuming integer is in seconds
                result = Long.valueOf(valueMap.getN()) * 1000l;
            }else{
                throw new RuntimeException(String.format("This type of date is not supported from source synapse, valuemap is {} ",valueMap));
            }
        } else{
            throw new RuntimeException("Datatype passed to get data is not supported by this method");
        }
        return result;
    }

    protected String getCased(String name) {
        return StringUtils.isBlank(name) ? name : name.toLowerCase();
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return Instant.EPOCH.toEpochMilli();
    }


    @Override
    public SyncResponse create(SyncRequest request) {
        return batchWriteRequests(request, false);
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        if (MapUtils.isEmpty(request.getData())){
            log.info("Empty Request to insert data to dynamodb");
            return new SyncResponse();
        }
        SyncResponse response = new SyncResponse();
        // List of data to be added to dynamo
        List<EntityData> dataToBeCreated = request.getData().get(request.getConnector().getId());
        List<List<EntityData>> partitionedList =  Lists.partition(dataToBeCreated, MAX_RECORDS_ONE_PUT_BATCH);
        ConnectorInfo connectorInfo= request.getConnector();
        AuthConfig config = connectorInfo.getAuthConfig();
        AmazonDynamoDB ddb = S3FileManager.getDDBClient(config.getAccessToken(), config.getClientSecret(), connectorInfo.getMetaConfig().get("region").toString());
        partitionedList.forEach(entitDataList -> entitDataList.forEach(ed-> {
            try{
                Optional<UpdateItemRequest> updateItemRequest = buildUpdateItemRequest(ed, request);
                if (updateItemRequest.isPresent()){
                    UpdateItemResult updateItemResult = ddb.updateItem(updateItemRequest.get());
                }
                Result res = new Result(true, ed.getId(), ed.getSyncariEntityId());
                response.getResults().add(res);
            }catch (Exception e){
                log.error("Write failed with exception message {}", e.getMessage());
                Result errResult = new Result(false, ed.getId(), ed.getSyncariEntityId());
                errResult.addError(e.getMessage());
                response.getResults().add(errResult);
            }
        }));
        return response;
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        return batchWriteRequests(request, true);
    }

    private Optional<PutRequest> buildPutRequest(EntityData ed,SyncRequest request){
        EntitySchema entitySchema =  request.getEntitySchema();
        AttributeSchema idField = entitySchema.getIdField();
        AttributeSchema watermarkField = entitySchema.getWatermarkField();
        List<AttributeSchema> compositeFields = entitySchema.getCompositeKeyFields();
        Map <String, AttributeValue> attributeValuesMap = new HashMap<>();
        Map<String, Object> edAttributes = ed.getValues();
        if (MapUtils.isEmpty(edAttributes)){
            return Optional.empty();
        }
        edAttributes.forEach( (edAttrib,value) -> {
            Optional<AttributeSchema> attributeSchema =  entitySchema.getField(edAttrib);
            AttributeValue attribValueToAdd = new AttributeValue();
            if (attributeSchema.isPresent() && (null != value)){
                String dataType = attributeSchema.get().getDataType();
                if (dataType.equals("id") || edAttrib.equals(idField.getApiName())){
                    buildIdForRequest((String)value, compositeFields, idField.getApiName(), attributeValuesMap);
                }else{
                    if (dataType.equalsIgnoreCase("integer")){
                        attribValueToAdd = new AttributeValue().withN(String.valueOf(value));
                    } else if (dataType.equalsIgnoreCase("double")){
                        attribValueToAdd = new AttributeValue().withN(String.valueOf(value));
                    } else if (dataType.equalsIgnoreCase("boolean")){
                        attribValueToAdd = new AttributeValue().withBOOL((Boolean) value);
                    }else if (dataType.equalsIgnoreCase("string")){
                        attribValueToAdd = new AttributeValue().withS((String) value);
                    }else if (isDateRelatedField(Optional.of(dataType)) && (watermarkField.getApiName().equals(attributeSchema.get().getApiName()))) {
                        attribValueToAdd = new AttributeValue().withN(String.valueOf(value));
                    }else if (isDateRelatedField(Optional.of(dataType))){
                        attribValueToAdd = new AttributeValue().withS(String.valueOf(value));
                    }else if (dataType.equalsIgnoreCase("object")){
                        attribValueToAdd = parseObject(value);
                    }
                    attributeValuesMap.put(attributeSchema.get().getApiName(), attribValueToAdd);
                }
            }
        });
        return Optional.of(new PutRequest().withItem(attributeValuesMap));
    }

    private Optional<UpdateItemRequest> buildUpdateItemRequest(EntityData ed,SyncRequest request){
        EntitySchema entitySchema =  request.getEntitySchema();
        AttributeSchema idField = entitySchema.getIdField();
        AttributeSchema watermarkField = entitySchema.getWatermarkField();
        List<AttributeSchema> compositeFields = entitySchema.getCompositeKeyFields();
        Map <String, AttributeValue> attributeValuesMap = new HashMap<>();
        Map <String, AttributeValueUpdate> attributeValuesUpdateMap = new HashMap<>();
        Map<String, Object> edAttributes = ed.getValues();
        if (MapUtils.isEmpty(edAttributes)){
            return Optional.empty();
        }
        String idValue = ed.getId();
        if (null != idValue){
            buildIdForRequest(idValue,compositeFields, idField.getApiName(),attributeValuesMap);
        }
        edAttributes.forEach((edAttrib,value) -> {
            Optional<AttributeSchema> attributeSchema =  entitySchema.getField(edAttrib);
            AttributeValueUpdate attribValueToUpdate = null;
            if (attributeSchema.isPresent() && (null != value)){
                String dataType = attributeSchema.get().getDataType();
                if (dataType.equals("id") || edAttrib.equals(idField.getApiName())){
                    buildIdForRequest((String)value, compositeFields, idField.getApiName(), attributeValuesMap);
                }else{
                    if ((dataType.equalsIgnoreCase("integer")) || (dataType.equalsIgnoreCase("double"))){
                        attribValueToUpdate = new AttributeValueUpdate().withValue(new AttributeValue().withN(String.valueOf(value)));
                    }else if (dataType.equalsIgnoreCase("boolean")){
                        attribValueToUpdate = new AttributeValueUpdate().withValue(new AttributeValue().withBOOL((Boolean) value));
                    }else if (dataType.equalsIgnoreCase("string")){
                        attribValueToUpdate = new AttributeValueUpdate().withValue(new AttributeValue().withS((String)value));
                    }else if (dataType.equalsIgnoreCase("object")){
                        attribValueToUpdate = new AttributeValueUpdate().withValue(parseObject(value));
                    }else if (isDateRelatedField(Optional.of(dataType)) && (watermarkField.getApiName().equals(attributeSchema.get().getApiName()))) {
                        attribValueToUpdate = new AttributeValueUpdate().withValue(new AttributeValue().withN(String.valueOf(value)));
                    }else if (isDateRelatedField(Optional.of(dataType))){
                        attribValueToUpdate = new AttributeValueUpdate().withValue(new AttributeValue().withS(String.valueOf(value)));
                    }
                    if (attribValueToUpdate != null) {
                        attributeValuesUpdateMap.put(attributeSchema.get().getApiName(),attribValueToUpdate);
                    }
                }
            }
        });
        return Optional.of(new UpdateItemRequest().withTableName(request.getEntityName()).withKey(attributeValuesMap).withAttributeUpdates(attributeValuesUpdateMap));
    }

    private AttributeValue parseObject(Object value){
        if (value instanceof List){
            List<AttributeValue> listToAdd = new ArrayList<>();
            ((List<?>) value).forEach(val -> {
                AttributeValue attributeValueToAdd = parseObject(val);
                listToAdd.add(attributeValueToAdd);
            });
            return new AttributeValue().withL(listToAdd);
        } else if (value instanceof Map){
            Map<String, AttributeValue> mapToSendToDDB = new HashMap<>();
            ((Map<String,?>) value).forEach( (x, y) ->  {
                AttributeValue attributeValueToAdd = parseObject(y);
                mapToSendToDDB.put(x, attributeValueToAdd);
            });
            return new AttributeValue().withM(mapToSendToDDB);
        }else if (value instanceof EntityData){
            return parseObject(((EntityData) value).getValues());
        }else if (value instanceof String){
            return new AttributeValue().withS(String.valueOf(value));
        }else if (value instanceof Boolean){
            return new AttributeValue().withBOOL((Boolean)value);
        }else if (value instanceof Number){
            return new AttributeValue().withN(String.valueOf(value));
        }else{
            log.info("Data type for parsing is not listed, value is {} , trying to parse that to string", value);
            return new AttributeValue().withS(String.valueOf(value));
        }
    }

    private Optional<DeleteRequest> buildDeleteRequest(EntityData ed,SyncRequest request){
        EntitySchema entitySchema =  request.getEntitySchema();
        AttributeSchema idField = entitySchema.getIdField();
        List<AttributeSchema> compositeFields = entitySchema.getCompositeKeyFields();
        Map <String, AttributeValue> attributeValuesMap = new HashMap<>();
        Map<String, Object> edAttributes = ed.getValues();
        //Check value in attributes or value of id in Entity data
        String idValue = ed.getId();
        if (null != idValue){
            buildIdForRequest(idValue,compositeFields, idField.getApiName(),attributeValuesMap);
        }else{
            if (MapUtils.isEmpty(edAttributes)){
                return Optional.empty();
            }
            edAttributes.forEach((edAttrib,value) -> {
                Optional<AttributeSchema> attributeSchema =  entitySchema.getField(edAttrib);
                if (attributeSchema.isPresent()){
                    String dataType = attributeSchema.get().getDataType();
                    if (dataType.equals("id") || edAttrib.equals(idField.getApiName())){
                        buildIdForRequest((String)value, compositeFields, idField.getApiName(), attributeValuesMap);
                    }
                }
            });
        }
        return Optional.of(new DeleteRequest().withKey(attributeValuesMap));
    }

    private void buildIdForRequest(String value, List<AttributeSchema> compositeFields, String idFieldApiName, Map<String, AttributeValue> attributeValuesMap) {
        if (CollectionUtils.isEmpty(compositeFields)){
            // Assume id field is the partitionKey and stored as it is instead of storing as another attribute
            attributeValuesMap.put(idFieldApiName,new AttributeValue().withS((String) value));
        }else{
            String [] splittedArray = StringUtils.split(value,Pattern.quote(EntitySchema.COMPOSITE_KEY_DELIMETER));
            if ((null == splittedArray) || (splittedArray.length != compositeFields.size())){
                return;
            }
            IntStream.range(0, splittedArray.length).forEach(x -> {
                if (compositeFields.get(x).getDataType().equalsIgnoreCase("integer")){
                    attributeValuesMap.put(compositeFields.get(x).getApiName(),new AttributeValue().withN(splittedArray[x]));
                }else{
                    attributeValuesMap.put(compositeFields.get(x).getApiName(),new AttributeValue().withS(splittedArray[x]));
                }
            });
        }
    }

    private SyncResponse batchWriteRequests(SyncRequest request, boolean isDelete){
        if (MapUtils.isEmpty(request.getData())){
            log.info("Empty Request to insert data to dynamodb");
            return new SyncResponse();
        }
        SyncResponse response = new SyncResponse();
        // List of data to be added to dynamo
        List<EntityData> dataToBeCreated = request.getData().get(request.getConnector().getId());
        List<List<EntityData>> partitionedList =  Lists.partition(dataToBeCreated, MAX_RECORDS_ONE_PUT_BATCH);

        List<AttributeSchema> compositeFields = request.getEntitySchema().getCompositeKeyFields();
        List<String> compositeFieldsApiNames = compositeFields.stream().map(x -> x.getApiName()).collect(Collectors.toList());
        AttributeSchema idField = request.getEntitySchema().getIdField();

        ConnectorInfo connectorInfo= request.getConnector();
        AuthConfig config = connectorInfo.getAuthConfig();
        AmazonDynamoDB ddb = S3FileManager.getDDBClient(config.getAccessToken(), config.getClientSecret(), connectorInfo.getMetaConfig().get("region").toString());
        List<String> idsList = new ArrayList<>();
        List<String> syncariIdList = new ArrayList<>();

        partitionedList.forEach(entitDataList -> {
            BatchWriteItemRequest batchWriteItemRequest = new BatchWriteItemRequest();
            List<WriteRequest> writeRequests = new ArrayList<>();
            entitDataList.forEach(entityData -> {
                if (!isDelete){
                    Optional<PutRequest> putRequest = buildPutRequest(entityData, request);
                    if (putRequest.isPresent()){
                        log.debug("Put Request - {}", putRequest.get());
                        writeRequests.add(new WriteRequest().withPutRequest(putRequest.get()));

                        String idValue =  (CollectionUtils.isNotEmpty(compositeFields) && (entityData.getValues().keySet().containsAll(compositeFieldsApiNames))) ?
                                StringUtils.join(IntStream.range(0,compositeFields.size()).mapToObj(i -> entityData.getValues().get(compositeFields.get(i).getApiName())).collect(Collectors.toList()), EntitySchema.COMPOSITE_KEY_DELIMETER)
                                : entityData.getValues().keySet().contains(idField.getApiName()) ? (String)entityData.getValues().get(idField.getApiName()) : "";
                        idsList.add(idValue);
                    }
                }else{
                    Optional<DeleteRequest> delRequest = buildDeleteRequest(entityData, request);
                    if (delRequest.isPresent()){
                        writeRequests.add(new WriteRequest().withDeleteRequest(delRequest.get()));
                        idsList.add(entityData.getId());

                    }
                }
                syncariIdList.add(entityData.getSyncariEntityId());
            });
            String syncariIds = StringUtils.join(syncariIdList, ",");
            String ids = StringUtils.join(idsList, ",");
            try{
                batchWriteItemRequest.addRequestItemsEntry(request.getEntityName(), writeRequests);
                BatchWriteItemResult result = ddb.batchWriteItem(batchWriteItemRequest);
                IntStream.range(0,syncariIdList.size()).forEach(x -> {
                    response.getResults().add(new Result(true,idsList.get(x), syncariIdList.get(x)));
                    log.debug("All items with syncarids {} are successfully processed",syncariIds);
                    response.setSuccess(true);
                });
                log.info("Batch processing passed to dynamo db with syncariIds {}", syncariIds);
            }catch (Exception e){
                log.error("Batch processing failed to dynamo db with syncariId {} and message {}", syncariIds,e.getMessage());
                IntStream.range(0,syncariIdList.size()).forEach(x -> {
                    Result result = new Result(false,idsList.get(x), syncariIdList.get(x));
                    result.setErrors(List.of(e.getMessage()));
                    response.getResults().add(result);
                    response.setSuccess(false);
                });
            }
        });
        return response;
    }
}