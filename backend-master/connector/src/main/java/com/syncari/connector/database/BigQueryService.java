package com.syncari.connector.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.gax.paging.Page;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.*;
import com.google.cloud.bigquery.InsertAllRequest.RowToInsert;
import com.google.gson.stream.MalformedJsonException;
import com.syncari.connector.*;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.DefaultDataIterator;
import com.syncari.connector.service.Transformer;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.utils.DateUtil;
import com.syncari.utils.Pair;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jooq.lambda.function.Function3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

@Slf4j
@Component(Constants.BIGQUERY)
public class BigQueryService implements CommonDataService, MetadataService, SynapseInfoService, AuthenticationService {
    private static final Collection<String> scopes = List.of("https://www.googleapis.com/auth/bigquery",
            "https://www.googleapis.com/auth/bigquery.insertdata");
    public static final String QUOTE = "'";
    public static final String PROJECT_ID = "projectId";
    public static final String DATASET_ID = "datasetId";
    public static final String DATABASE_NAME = "dbName";
    private static final String dateFormat = "yyyy-MM-dd";
    public static final int BAD_REQUEST = 400;
    // BQ does not guarentee data reads for upto 90 mins. To overcome it, fetch data for past 90 mins
    // to avoid data loss
    public static final long _90_MIN_IN_MILLI = 90 * 60 * 1000;
    public static final String PAYLOAD_SIZE_LIMIT_MESSAGE_PREFIX = "Request payload size exceeds the limit";
    public static final String QUERY_TOO_LARGE_MESSAGE_PREFIX = "The query is too large";
    private static final String queryByWm = "SELECT * FROM `%s.%s.%s` WHERE %s BETWEEN @startDate AND @endDate ORDER BY %s ASC, %s ASC LIMIT %s OFFSET %s";
    private static final String queryById = "SELECT * FROM `%s.%s.%s` WHERE %s IN (%s)";
    private static final String queryByIdComposite = "SELECT * FROM `%s.%s.%s` WHERE (%s) IN (%s)";
    private static final String deleteById = "DELETE FROM `%s.%s.%s` WHERE %s IN (%s)";
    private static final String describeOne = "SELECT * EXCEPT(is_generated, generation_expression, is_stored, is_updatable)" +
            " FROM `%s`.%s.INFORMATION_SCHEMA.COLUMNS WHERE table_name='%s'";
    public static final String updateById = "UPDATE `%s.%s.%s` SET %s WHERE %s IN (%s)";
    List<StandardSQLTypeName> needsQuote = List.of(StandardSQLTypeName.STRING, StandardSQLTypeName.DATE, StandardSQLTypeName.DATETIME, StandardSQLTypeName.TIMESTAMP);
    @Autowired
    Transformer transformer;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    DateUtil dateUtil;
    @Autowired
    CompositeKeyHelper compositeKeyHelper;
    @Autowired
    BigQueryRateLimiter rateLimiter;

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        AuthField key = new AuthField();
        key.setDataType("password");
        key.setName("accessToken");
        key.setLabel("Key");
        return List.of(new AuthMetadata(AuthType.ApiKey, List.of(key), "Key", ""));
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19198410595860";
    }

    @Override
    public List<Capability> getCapabilities() {
        var capabilities = new ArrayList<Capability>();
        capabilities.add(Capability.schemaEditInSyncari);
        capabilities.add(Capability.userEditableId);
        capabilities.add(Capability.userEditableWm);
        capabilities.add(Capability.compositeId);
        return capabilities;
    }

    @Override
    public List<AuthField> getConfigureFields() {
        AuthField bootstrappable = new AuthField().setDataType("checkbox").setName("bootstrapable")
                .setLabel("Instantiate with Syncari entities");
        AuthField projectId = new AuthField().setName("projectId").setLabel(i18n("projectId"))
                .setDataType("text").setHelpSummary(i18n("projectId_summary"));
        AuthField datasetId = new AuthField().setName("datasetId").setLabel(i18n("datasetId"))
                .setDataType("text").setHelpSummary(i18n("datasetId_summary"));
        return List.of(projectId, datasetId, bootstrappable, ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public String getCategory() {
        return "Datawarehouse";
    }

    @Override
    public String getName() {
        return Constants.BIGQUERY;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/bigquery.svg")
                .setDisplayName("Bigquery")
                .setBackgroundColor("#F5F9FF")
                .setHelpUrl(helpArticlesBaseUrl + "/360059914992");
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        if (request.getEntitySchema() == null)
            throw new RuntimeException("Schema cannot be null");
        WatermarkInfo watermark = request.getWatermark();
        Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator = (wm, pageSize, offset) -> {
            String wmField = request.getEntitySchema().getWatermarkField().getApiName();
            String idField = request.getEntitySchema().getIdField().getApiName();
            String formatted = String.format(queryByWm, getValue(request.getConnector(), PROJECT_ID),
                    getValue(request.getConnector(), DATASET_ID), request.getEntityName(), wmField, wmField, idField, pageSize, offset);
            List<EntityData> results = extractData(request,
                    getQueryConfig(Instant.ofEpochMilli(Math.max(wm.getStart() - _90_MIN_IN_MILLI, 0))
                            , Instant.ofEpochMilli(wm.getEnd()), formatted,
                            request.getEntitySchema().getWatermarkField().getDataType()));
            log.info("Got {} rows for wm {}", results.size(), wm);
            return Pair.of(Long.valueOf(results.size()), results.stream());
        };
        int pageSize = request.getPageSize() == 0 ? 100 : Math.min(request.getPageSize(), 100);
        DefaultDataIterator iterator = new DBIterator(watermark, watermark.getOffset(), generator,
                new ArrayList<>(), request.getEntitySchema().getWatermarkField(), pageSize, request.getWatermark().getLimit(), DBIterator.DEFAULT_ZONE,
                request.getEntitySchema().getWatermarkField().getDataType());
        return new FetchResponse(request.getWatermark(), iterator);
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        BigQuery bigQuery = bigQueryService(request.getConnector());
        String dataset = getValue(request.getConnector(), DATASET_ID);
        TableId tableId = TableId.of(dataset, request.getEntity());
        Table table = bigQuery.getTable(tableId);
        if (table == null)
            return Optional.empty();
        return Optional.of(getSchema(table));
    }
    
    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        BigQuery bigQuery = bigQueryService(request.getConnector());
        Page<Table> tablePage = bigQuery.listTables(getValue(request.getConnector(), DATASET_ID));
        Map<String, EntitySchema> schemaMap = new HashMap<>();
        tablePage.iterateAll().forEach(table -> {
            Table table1 = bigQuery.getTable(table.getTableId());
            log.debug("Fetched table - {}", table1.getTableId().getTable());
            schemaMap.putIfAbsent(table1.getTableId().getTable(), getSchema(table1));
        });
        return schemaMap.values().stream().collect(Collectors.toList());
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse result = new TestConnectionResponse();

        try {
            if (StringUtils.isEmpty(getValue(config, PROJECT_ID))) {
                throw new RuntimeException(String.format("%s not specified.", i18n("projectId")));
            }
    
            if (StringUtils.isEmpty(getValue(config, DATASET_ID))) {
                throw new RuntimeException(String.format("%s not specified.", i18n("datasetId")));
            }

            DescribeAllRequest request = new DescribeAllRequest(config, List.of());
            describeAll(request);
        } catch (Exception e) {
            log.error("BigQuery testConnection failed due to " + e.getMessage(), e);
            handleConnectionErrors(result, e);
            return result;
        }
        return result;
    }

    private void handleConnectionErrors(TestConnectionResponse response, Exception e) {
        if (e instanceof RuntimeException) {
            if (ExceptionUtils.getRootCause(e) instanceof MalformedJsonException) {
                response.setCode(ConnectorErrorCodes.CONNECTION_ERROR);
                response.setMessage(TestConnectionResponse.AUTH_FAILED_MESSAGE + 
                    " Details: Invalid key (token). This is your JSON Key for which the Service Account related to the Project.");
                return;
            }
        }
        handleAuthenticationErrorMessage(response, e);
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        var datasetId = getValue(request.getConnector(), DATASET_ID);
        var tableName = request.getEntityName();
        var fieldName = request.getSchema().getApiName();

        // Rate limit table update operations to stay within BigQuery quota (5 updates per 10 seconds)
        rateLimiter.acquirePermit(datasetId, tableName);

        Table existing = bigQueryService(request.getConnector()).getTable(TableId.of(datasetId, tableName));
        if (existing != null) {
            var existingFields = new ArrayList<>(existing.getDefinition().getSchema().getFields());
            Set<String> existingFieldNames = existingFields.stream().map(f -> f.getName()).collect(Collectors.toSet());
            if (existingFieldNames.contains(fieldName)) {
                log.warn("Field already exists.Skipping field for {} tableName {} fieldName {}", datasetId,
                        tableName, fieldName);
                return request.getSchema();
            } else {
                log.info("Creating field {} for {} tableName {}", fieldName, datasetId, tableName);
                existingFields.add(toBQField(request.getSchema(), getType(request.getSchema().getDataType())));
                Schema newSchema = Schema.of(existingFields);
                existing.toBuilder().setDefinition(StandardTableDefinition.of(newSchema)).build().update();
            }
        } else {
            log.warn("Table not found, Skipping table update for {} tableName {} fields {}", datasetId, tableName,
                    fieldName);
        }
        return request.getSchema();
    }

    public CreateFieldsResponse createFields(CreateFieldsRequest request) {
        var datasetId = getValue(request.getConnector(), DATASET_ID);
        var tableName = request.getEntityName();
        TableId tableId = TableId.of(datasetId, tableName);
        Table existing = bigQueryService(request.getConnector()).getTable(tableId);
        if (existing != null) {
            var existingFields = new ArrayList<>(existing.getDefinition().getSchema().getFields());
            Set<String> existingFieldNames = existingFields.stream().map(Field::getName).collect(Collectors.toSet());
            List<AttributeSchema> fieldsToCreate = request.getSchemas().stream()
                    .filter(schema -> !existingFieldNames.contains(schema.getApiName()))
                    .collect(Collectors.toList());
            if (fieldsToCreate.isEmpty()) {
                log.warn("All fields already exist. Skipping field creation for {} tableName {}", datasetId, tableName);
            } else {
                log.info("Creating {} fields for {} tableName {}", fieldsToCreate.size(), datasetId, tableName);
                fieldsToCreate.forEach(schema ->
                        existingFields.add(toBQField(schema, getType(schema.getDataType()), true))
                );
                Schema newSchema = Schema.of(existingFields);
                existing.toBuilder().setDefinition(StandardTableDefinition.of(newSchema)).build().update();
            }
        } else {
            // Table doesn't exist, create it with the requested fields
            log.info("Table not found, creating table {} with {} fields", tableName, request.getSchemas().size());
            List<Field> fieldList = request.getSchemas().stream()
                    .map(schema -> toBQField(schema, getType(schema.getDataType())))
                    .collect(Collectors.toList());
            Schema schema = Schema.of(fieldList);
            TableDefinition tableDefinition = StandardTableDefinition.of(schema);
            TableInfo tableInfo = TableInfo.newBuilder(tableId, tableDefinition).build();
            bigQueryService(request.getConnector()).create(tableInfo);
            existing = bigQueryService(request.getConnector()).getTable(tableId);
            if (existing == null) {
                throw new RuntimeException(format("Could not create Bigquery table %s", tableId));
            }
            log.info("Bigquery Table {} created successfully with {} fields", tableName, request.getSchemas().size());
        }
        return new CreateFieldsResponse(request.getEntityName(), request.getConnector(), request.getSchemas());
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        var datasetId = getValue(request.getConnector(), DATASET_ID);
        var tableName = request.getEntityName();
        var fieldName = request.getFieldName();

        // Rate limit table update operations to stay within BigQuery quota (5 updates per 10 seconds)
        rateLimiter.acquirePermit(datasetId, tableName);

        Table existing = bigQueryService(request.getConnector()).getTable(TableId.of(datasetId, request.getEntityName()));
        if (existing != null) {
            var existingFields = new ArrayList<>(existing.getDefinition().getSchema().getFields());
            Set<String> existingFieldNames = existingFields.stream().map(f -> f.getName()).collect(Collectors.toSet());
            existingFields.forEach(field -> {
                if (existingFieldNames.contains(fieldName)) {
                    existingFields.remove(field);
                    Schema newSchema = Schema.of(existingFields);
                    existing.toBuilder().setDefinition(StandardTableDefinition.of(newSchema)).build().update();
                    log.info("field deleted for dataset:{},tableName:{},:fieldName:{}", datasetId, tableName, fieldName);
                } else {
                    log.warn("Field not found for dataset:{},tableName:{},:fieldName:{}", datasetId, tableName, fieldName);
                }
            });
        } else {
            log.warn("Table not found, Skipping field delete for dataset:{},tableName:{},:fieldName:{}", datasetId, tableName,
                    fieldName);
        }
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        if (!request.getEntitySchema().hasIdField()) {
            throw new RuntimeException("ID field not found for schema " + request.getEntitySchema().getApiName());
        }
        if (!request.getEntitySchema().hasWatermarkField()) {
            throw new RuntimeException("Watermark  field not found for schema " + request.getEntitySchema().getApiName());
        }
        boolean isNumericID = isNumericType(getType(request.getEntitySchema().getIdField().getDataType()));
        boolean isComposite = !StringUtils.isBlank(request.getEntitySchema().getIdField().getCompositeKey());
        String idsAsString = "";
        String formatted = null;
        if(!isComposite) {
            if (isNumericID) {
                idsAsString = String.join(", ",
                        getIds(request).stream().filter(id -> id != null).collect(Collectors.toList()));
            } else {
                idsAsString = String.join(", ",
                        getIds(request).stream().filter(id -> id != null).map(i -> String.format("'%s'", i)).collect(Collectors.toList()));
            }
            formatted = String.format(queryById, getValue(request.getConnector(), PROJECT_ID),
                    getValue(request.getConnector(), DATASET_ID), request.getEntityName(),
                    request.getEntitySchema().getIdField().getApiName(), idsAsString);
        } else {
            List<String> ids = getIds(request);
            List<String> idPredicates = new ArrayList<>();
            String[] keys = request.getEntitySchema().getCompositeKeyAttributes().stream().map(a -> a.getApiName()).toArray(String[]::new);
            String idFieldNames = String.join(",", keys);
            for(String id : ids) {
                List<String> innerPredicate = new ArrayList<>();
                String[] values = id.split(Pattern.quote(EntitySchema.COMPOSITE_KEY_DELIMETER));
                for (int i =0; i< keys.length; i++) {
                    if (isNumericID) {
                        innerPredicate.add(values[i]);
                    } else {
                        innerPredicate.add(QUOTE+values[i]+QUOTE);
                    }
                }
                idPredicates.add("("+innerPredicate.stream().collect(Collectors.joining(","))+")");
            }
            String idString = idPredicates.stream().collect(Collectors.joining(","));
            formatted = String.format(queryByIdComposite, getValue(request.getConnector(), PROJECT_ID),
                    getValue(request.getConnector(), DATASET_ID), request.getEntityName(), idFieldNames,
                    idString);
        }
        log.debug("formatted query {}", formatted);
        return extractData(request, QueryJobConfiguration.newBuilder(formatted).build());
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        assignChangeSetValue(request, "insert");
        return processCreate(request, false);
    }

    private SyncResponse processCreate(SyncRequest request, boolean isUpdateOrDelete) {
        var datasetId = getValue(request.getConnector(), DATASET_ID);
        var projectId = getValue(request.getConnector(), PROJECT_ID);
        var tableName = request.getEntityName();
        TableId tableId = TableId.of(projectId, datasetId, tableName);
        List<RowToInsert> rows = new ArrayList<>();
        Optional<EntitySchema> existing = describe(new DescribeRequest(request.getConnector(), tableName));
        boolean hasSyncariId = (existing.isPresent() && existing.get().hasField(Constants.SYNCARI_ID));
        String insertOpt = request.getDestParams().getOrDefault(Constants.BQ_INSERT_OPTION, "").toString();
        boolean skipInsertId = (Constants.BQ_FULL_RECORD_TO_INSERT_OPTION.equalsIgnoreCase(insertOpt)
                || Constants.BQ_PARTIAL_RECORD_TO_INSERT_OPTION.equalsIgnoreCase(insertOpt));
        Map<String, Object> idMap = new HashMap<>();
        request.getData().get(request.getConnector().getId()).stream().forEach(entry -> {
            Map<String, Object> values = new HashMap<>();
            if(hasSyncariId) {
                values.put(Constants.SYNCARI_ID, entry.getSyncariEntityId());
            }
            String id = (request.getEntitySchema().hasIdField()
                    && entry.getValueAsString(request.getEntitySchema().getIdField().getApiName()) != null)
                            ? request.getEntitySchema().hasCompositeKeyFields() ? compositeKeyHelper.composeIdKeys(entry, request.getEntitySchema()) : entry.getValueAsString(request.getEntitySchema().getIdField().getApiName())
                            : entry.getSyncariEntityId();
            if(StringUtils.isNotBlank(entry.getId())) {
                values.put(request.getEntitySchema().getIdField().getApiName(), entry.getId());
            }else if (StringUtils.isNotEmpty(id)){
                values.put(request.getEntitySchema().getIdField().getApiName(), id);
            }
            entry.getValues().forEach((k, v) -> {
                existing.flatMap(e -> e.getField(k))
                        .ifPresent(a -> {
                            if ((v == null || v.toString().isEmpty()) && isNumeric(a)) {
                                // Skip if v is null/empty and attr is numeric
                                return;
                            }
                            values.put(k, toBQType(v, a));
                        });
            });
            if(skipInsertId) {
                rows.add(RowToInsert.of(values));
            } else {
                rows.add(RowToInsert.of(id, values));
            }
            idMap.put(id, entry.getSyncariEntityId());
        });
        return insertRows(tableId, rows, request.getConnector(), idMap, request.getEntitySchema().getIdField().getApiName(), skipInsertId);
    }

    private boolean isNumeric(AttributeSchema a) {
        String dataType = a.getDataType().toLowerCase();
        return "double".equalsIgnoreCase(dataType) || "integer".equalsIgnoreCase(dataType)
                || "number".equalsIgnoreCase(dataType) || "float".equalsIgnoreCase(dataType);
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        assignChangeSetValue(request, "update");
        if (evaluateInsertOption(request)) return processCreate(request, true);
        SyncResponse response = new SyncResponse();
        List<EntityData> data = request.getData().get(request.getConnector().getId());
        if (data == null || data.isEmpty() || request.getIds().isEmpty())
            return response;

        int retryCount = 3;
        Exception original = null;
        while (retryCount > 0) {
            try {
                return updateRows(request, request.getData().get(request.getConnector().getId()));
            } catch (BigQueryException ex) {
                if (isPayloadTooLarge(ex)) {
                    List<EntityData> list = request.getData().get(request.getConnector().getId());
                    List<List<EntityData>> partitions = ListUtils.partition(list, 100);
                    partitions.forEach(partition -> {
                        SyncResponse syncResponse = null;
                        try {
                            syncResponse = updateRows(request, partition);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        response.getResults().addAll(syncResponse.getResults());
                        response.getErrors().addAll(syncResponse.getErrors());
                    });
                    return response;
                } else {
                    original = ex;
                }
            } catch (Exception e) {
                original = e;
            }
            retryCount--;
        }
        if (retryCount == 0) {
            if (original != null) {
                throw new RuntimeException(original);
            }
        } else {
            throw new RuntimeException("Retries exhausted. Cannot update rows into BQ table " + request.getEntityName());
        }
        return response;
    }

    private SyncResponse updateRows(SyncRequest request, List<EntityData> partition) throws InterruptedException {
        SyncResponse response = new SyncResponse();
        List<String> criteriaParts = new ArrayList();
        List<String> ids = new ArrayList<>();
        String dataType = request.getEntitySchema().getIdField().getDataType();
        request.getEntitySchema().getAttributes().stream().forEach(field -> {
            if(field.isIdField() || field.isWatermarkField()) return;
            String criteria = "";
            List<String> caseParts = new ArrayList();
            partition.stream().forEach(d -> {
                if(d.has(field.getApiName())) {
                    Object v = d.getValue(field.getApiName());
                    if (dataType.contains("int")) {
                        caseParts.add(" WHEN " + d.getId() + " THEN " + getDecorated(v, field));
                    } else {
                        caseParts.add(" WHEN '" + d.getId() + "' THEN " + getDecorated(v, field));
                    }
                }
                ids.add(d.getId());
            });
            if(!caseParts.isEmpty()) {
                criteria = field.getApiName() + " = CASE " + request.getEntitySchema().getIdField().getApiName()
                        + String.join(" ", caseParts) + " ELSE " + field.getApiName() + " END ";
                criteriaParts.add(criteria);
            }
        });
        String criteria = String.join(", ", criteriaParts);
        String idsAsString = getIdsAsString(request);
        String formatted = String.format(updateById, getValue(request.getConnector(), PROJECT_ID),
                getValue(request.getConnector(), DATASET_ID), request.getEntityName(), criteria, request.getEntitySchema().getIdField().getApiName(), idsAsString);
        log.debug(formatted);
        QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(formatted).build();
        TableResult r = bigQueryService(request.getConnector()).query(queryConfig);
        log.info("Successfully updated {} records in datastore", partition.size());
        return response;
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        assignChangeSetValue(request, "delete");
        if (evaluateInsertOption(request)) return processCreate(request, true);
        SyncResponse response = new SyncResponse();
        List<EntityData> data = request.getData().get(request.getConnector().getId());
        if (data == null || data.isEmpty() || request.getIds().isEmpty())
            return response;
        String idsAsString = getIdsAsString(request);
        String formatted = String.format(deleteById, getValue(request.getConnector(), PROJECT_ID),
                getValue(request.getConnector(), DATASET_ID), request.getEntityName(),
                request.getEntitySchema().getIdField().getApiName(), idsAsString);
        log.debug(formatted);
        QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(formatted).build();
        TableResult r = runQuery(request.getConnector(), queryConfig);
        return response;
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        var datasetId = getValue(request.getConnector(), DATASET_ID);
        var tableName = request.getSchema().getApiName();
        TableId tableId = TableId.of(datasetId, tableName);
        Table existing = bigQueryService(request.getConnector()).getTable(tableId);
        boolean addSyncariId = false;
        if(!request.getSchema().hasIdField() && !request.getSchema().hasField(Constants.SYNCARI_ID)) {
            addSyncariId = true;
        }
        if (existing == null) {
            List<Field> fieldList = request.getSchema().getAttributes().stream()
                    .map(attr -> toBQField(attr, getType(attr.getDataType())))
                    .collect(Collectors.toList());
            if(addSyncariId) {
                fieldList.add(toBQField(Constants.SYNCARI_ID, getType("string")));
            }
            Schema schema = Schema.of(fieldList);
            TableDefinition tableDefinition = StandardTableDefinition.of(schema);
            TableInfo tableInfo = TableInfo.newBuilder(tableId, tableDefinition).build();
            bigQueryService(request.getConnector()).create(tableInfo);
            existing = bigQueryService(request.getConnector()).getTable(tableId);
            if (existing == null)
                throw new RuntimeException(format("Could not create Bigquery table %s", tableId));
            log.info(format("Bigquery Table %s created successfully", existing.getTableId()));
        } else {
            log.warn("Table {} in dataset {} already exists, skipping creation", tableName, datasetId);
        }
        return request.getSchema();
    }

    @Override
    public void deleteObject(DeleteObjectRequest request) {
        var datasetId = getValue(request.getConnector(), DATASET_ID);
        var tableName = request.getEntityName();
        TableId tableId = TableId.of(datasetId, tableName);
        Table existing = bigQueryService(request.getConnector()).getTable(tableId);
        if (existing != null) {
            bigQueryService(request.getConnector()).delete(tableId);
            existing = bigQueryService(request.getConnector()).getTable(tableId);
            if (existing != null)
                throw new RuntimeException(format("Could not delete Bigquery table %s", tableId));
            log.info(format("Bigquery Table %s deleted successfully", tableId));
        } else {
            log.warn("Table {} in dataset {} does not exists, skipping delete", tableName, datasetId);
        }
    }

    @Override
	public List<EntityData> search(SearchRequest request) {
    	String query = request.getQuery();
		if(request.getParams() != null) {
			for (Object param : request.getParams()) {
				if(param == null) continue;
				if(!request.getQuery().contains("?")) break;
				query = query.replaceFirst("\\?", param.toString());
			}
		}
		log.debug("Before {}, After {}", request.getQuery(), query);
		TableResult r = runQuery(request.getConnector(), QueryJobConfiguration.newBuilder(query).build());
        List<EntityData> results = new ArrayList<>();
        for (FieldValueList row : r.iterateAll()) {
            EntityData data = new EntityData();
            r.getSchema().getFields().forEach(field -> {
                FieldValue value = row.get(field.getName());
                data.addValue(field.getName(), value.getValue());
            });
            results.add(data);
        }
		return results;
	}
    
    @Override
    public Map<String, String> getEntityMappings() {
        return new HashMap<>();
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    static String getValue(ConnectorInfo connector, String key) {
        Object schema = connector.getMetaConfig().get(key);
        return schema == null ? "" : schema.toString();
    }
    
    private BigQuery bigQueryService(ConnectorInfo connector) {
        try {
            Credentials credentials = GoogleCredentials
                    .fromStream(new ByteArrayInputStream(connector.getAuthConfig().getAccessToken().getBytes()))
                    .createScoped(scopes);
            return BigQueryOptions.newBuilder().setCredentials(credentials).build().getService();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    private Field toBQField(String fieldName, StandardSQLTypeName type){
        return type == StandardSQLTypeName.ARRAY
                ? Field.newBuilder(fieldName, LegacySQLTypeName.STRING, new Field[0]).setMode(Field.Mode.REPEATED)
                        .build()
                : Field.of(fieldName, type);
    }


    private Field toBQField(AttributeSchema attribute, StandardSQLTypeName type) {
        return toBQField(attribute, type, false);
    }

    private Field toBQField(AttributeSchema attribute, StandardSQLTypeName type, boolean modifyingSchema) {
        //when modifying an existing schema, fields cannot be required in BQ
        boolean isNullable = modifyingSchema || attribute.isNillable();
        return type == StandardSQLTypeName.ARRAY
                ? Field.newBuilder(attribute.getApiName(), LegacySQLTypeName.STRING, new Field[0]).setMode(Field.Mode.REPEATED)
                .build()
                : Field.newBuilder(attribute.getApiName(), type).setMode(isNullable ? Field.Mode.NULLABLE : Field.Mode.REQUIRED).build();
    }

    private boolean isNumericType(StandardSQLTypeName sqlType) {
        return sqlType == StandardSQLTypeName.NUMERIC || sqlType == StandardSQLTypeName.INT64;
    }
    
    private StandardSQLTypeName getType(String datatype) {
        switch (datatype) {
        case "boolean":
        case "bool":
            return StandardSQLTypeName.BOOL;
        case "date":
            return StandardSQLTypeName.DATE;
        case "datetime":
            return StandardSQLTypeName.DATETIME;
        case "timestamp":
            return StandardSQLTypeName.TIMESTAMP;
        case "integer":
            return StandardSQLTypeName.INT64;
        case "float":
            return StandardSQLTypeName.FLOAT64;
        case "list":
            return StandardSQLTypeName.ARRAY;
        case "number":
        case "double":
            return StandardSQLTypeName.BIGNUMERIC;

        default:
            return StandardSQLTypeName.STRING;
        }
    }
    
    private String getSyncariType(String datatype) {
        switch (datatype) {
        case "BOOL":
            return "boolean";
        case "INT64":
            return "integer";
        case "FLOAT64":
        case "FLOAT":
            return "number";
        case "ARRAY":
            return "list";
        case "NUMERIC":
            case "BIGNUMERIC":
            case "BIGDECIMAL":
            case "DECIMAL":
            return "number";
            
        default:
            return datatype.toLowerCase();
        }
    }
    
    SyncResponse insertRows(TableId tableId, List<RowToInsert> rows, ConnectorInfo connector, Map<String, Object> idMap,
                            String idField, boolean skipInsertId) {
        SyncResponse resp = new SyncResponse();
        if(rows.isEmpty()){
            log.debug("Warning : Rows are empty for table {}",tableId);
            return resp;
        }
        List<List<RowToInsert>> rowPartitions = ListUtils.partition(rows, 10000);
        for(List<RowToInsert> rowPartition : rowPartitions) {
            InsertAllRequest request = InsertAllRequest.of(tableId, rowPartition);
            int retryCount = 3;
            Exception original = null;
            while (retryCount > 0) {
                try {
                    InsertAllResponse response = bigQueryService(connector).insertAll(request);
                    if (response.hasErrors()) {
                        // TODO collect errors in resp
                        log.info("Insert request: {}", request);
                        response.getInsertErrors().forEach((k, v) -> {
                            v.stream().forEach(err -> {
                                log.error(format("Error : %s", err));
                                String syncariId = getSyncariId(idMap, k.toString());
                                Result result = new Result(false, k.toString(), syncariId);
                                result.addError(err.getMessage());
                                resp.getResults().add(result);
                            });
                        });
                    } else {
                        rows.forEach(row -> {
                            if(row == null) return;
                            String syncariId = getSyncariId(idMap, row.getId());
                            String externalId = row.getId();
                            if(skipInsertId && idField != null) {
                                Object idVal = row.getContent().getOrDefault(idField, null);
                                externalId = (idVal == null ? null : idVal.toString());
                                syncariId = getSyncariId(idMap, externalId);
                            }
                            resp.getResults().add(new Result(true, externalId, syncariId));
                        });
                        log.info("Inserted {} rows in {}", rows.size(), tableId);
                    }
                    return resp;
                } catch (BigQueryException ex) {
                    if (isPayloadTooLarge(ex)) {
                        List<List<RowToInsert>> partitions = ListUtils.partition(rowPartition, (rowPartition.size() % 2) + rowPartition.size() / 2);
                        partitions.forEach(partition -> {
                            SyncResponse syncResponse = insertRows(tableId, partition, connector, idMap, idField, skipInsertId);
                            resp.getResults().addAll(syncResponse.getResults());
                            resp.getErrors().addAll(syncResponse.getErrors());
                        });
                        return resp;
                    } else {
                        original = ex;
                    }
                } catch (Exception e) {
                    original = e;
                }
                retryCount--;
            }
            if (retryCount == 0) {
                if (original != null) {
                    throw new RuntimeException(original);
                }
            } else {
                throw new RuntimeException("Retries exhausted. Cannot insert rows into BQ table " + tableId);
            }
        }
        return resp;
    }

    private String getSyncariId(Map<String, Object> idMap, String externalId) {
        return externalId == null ? null : (idMap.getOrDefault(externalId, "") == null ? null : idMap.getOrDefault(externalId, "").toString());
    }

    private boolean isPayloadTooLarge(BigQueryException ex) {
        return (ex.getCode() == BAD_REQUEST && ex.getMessage().startsWith(PAYLOAD_SIZE_LIMIT_MESSAGE_PREFIX)) || ex.getMessage().contains(QUERY_TOO_LARGE_MESSAGE_PREFIX);
    }
    
    private TableResult runQuery(ConnectorInfo connector, QueryJobConfiguration queryConfig)  {
        try {
            log.debug(queryConfig.getQuery());
            return bigQueryService(connector).query(queryConfig);
        } catch(Exception e){
            throw new RuntimeException(e);
        }
    }
    
    private QueryJobConfiguration getQueryConfig(Instant startDate, Instant endDate, String queryString, String wmDatatype) {
        QueryJobConfiguration config = null;
        switch (wmDatatype.toLowerCase()) {
        case "date":
            config = QueryJobConfiguration.newBuilder(queryString)
                    .addNamedParameter("startDate",
                            QueryParameterValue.date(dateUtil.format(startDate.toEpochMilli(), dateFormat)))
                    .addNamedParameter("endDate",
                            QueryParameterValue.date(dateUtil.format(endDate.toEpochMilli(), dateFormat)))
                    .build();
            break;
        case "datetime":
            String formatStart = dateUtil.formatDate(startDate, DateUtil.dateTimeFormatMicro);
            String formatEnd = dateUtil.formatDate(endDate, DateUtil.dateTimeFormatMicro);
            config = QueryJobConfiguration.newBuilder(queryString)
                    .addNamedParameter("startDate", QueryParameterValue.dateTime(formatStart))
                    .addNamedParameter("endDate", QueryParameterValue.dateTime(formatEnd)).build();
            break;
        case "timestamp":
            config = QueryJobConfiguration.newBuilder(queryString)
                    .addNamedParameter("startDate", QueryParameterValue.timestamp(startDate.toEpochMilli()*1000))
                    .addNamedParameter("endDate", QueryParameterValue.timestamp(endDate.toEpochMilli()*1000)).build();
            break;

        default:
            config = QueryJobConfiguration.newBuilder(queryString).build();
            break;
        }
        log.debug(config.getQuery());
        return config;
    }
    
    private List<EntityData> extractData(SyncRequest request, QueryJobConfiguration queryConfig) {
        AttributeSchema watermarkField = request.getEntitySchema().getWatermarkField();
        TableResult r = runQuery(request.getConnector(), queryConfig);
        List<EntityData> results = new ArrayList<>();
        for (FieldValueList row : r.iterateAll()) {
            EntityData data = new EntityData(request.getEntityName());
            AttributeSchema idField = request.getEntitySchema().getIdField();
            FieldValue idValue = null;
            for(Field field : r.getSchema().getFields()) {
                FieldValue value = row.get(field.getName());
                Object valueToBeUsed = fixDataTypes(request.getEntitySchema(), value, field);
                if (null != valueToBeUsed){
                    data.addValue(field.getName(), valueToBeUsed);
                    if(watermarkField.getApiName().equalsIgnoreCase(field.getName())) {
                        if("datetime".equalsIgnoreCase(watermarkField.getDataType())) {
                            data.setLastModified(dateUtil.convertDateTime(valueToBeUsed.toString()).toInstant().toEpochMilli());
                        } else if("date".equalsIgnoreCase(watermarkField.getDataType())) {
                            data.setLastModified(DateUtil.parse(value.getStringValue(), DateUtil.dateOnlyFormat).getTime());
                        } else {
                            data.setLastModified(value.getTimestampValue()/1000);
                        }
                    }
                }
                if(idField.getApiName().equalsIgnoreCase(field.getName())) {
                    idValue = value;
                }
            }
            if(idValue != null) {
                // if composite key, construct a value
                if (idField != null && !StringUtils.isBlank(idField.getCompositeKey())) {
                    data.setId(compositeKeyHelper.composeIdKeys(data, request.getEntitySchema()));
                } else if(idValue.getStringValue() != null){
                    data.setId(idValue.getStringValue());
                } else {
                    throw new RuntimeException("Record received from Bigquery with null value for id");
                }
            }
            results.add(data);
        }
        return results;
    }

    private Object fixDataTypes(EntitySchema entitySchema, FieldValue value, Field field) {
        if((value == null) || (null == value.getValue())) return null;
        var attributeSchema = entitySchema.getField(field.getName());
        return attributeSchema.map(a -> {
            switch (a.getDataType()) {
                case "timestamp" :
                    return Instant.ofEpochMilli(value.getTimestampValue() / 1000);
                default:
                    if(a.isMultiValueField()) {
                        return value.getRepeatedValue().stream().map(fieldValue -> fieldValue.getValue()).collect(Collectors.toList());
                    }
                    return value.getValue();
            }
        }).orElse(value.getValue());
    }
    
    private String getDecorated(Object val, AttributeSchema field) {
        if(val == null) {
            return nullIfExpression(field);
        }else{
            return convertedValue(val, field);
        }
    }

    private String nullIfExpression(AttributeSchema field) {
        switch (field.getDataType()){
            case "datetime": return "nullif(DATETIME(1990,1,1,0,0,0),DATETIME(1990,1,1,0,0,0))";
            case "date": return "nullif(DATE(1990,1,1),DATE(1990,1,1))";
            case "timestamp": return "nullif(TIMESTAMP('1990-01-01 12:00:00+00'),TIMESTAMP('1990-01-01 12:00:00+00'))";
            case "string":
                if (!field.isMultiValueField()) {
                    return "nullif('','')";
                } else {
                    return "CAST(NULL AS ARRAY<STRING>)";
                }
            case "number":
            case "integer":return "nullif(0,0)";
            case "double":return "nullif(0.0,0.0)";
            default: return "nullif('','')";
        }
    }
    private String convertedValue(Object value, AttributeSchema field) {
        if(value == null) return null;
        switch (field.getDataType()){
            case "datetime":
                return String.format("datetime('%s')", DateUtil.format(ZonedDateTime.class.cast(value),"yyyy-MM-dd HH:mm:ss"));
            case "date": return String.format("date('%s')",DateUtil.format(Date.class.cast(value),"yyyy-MM-dd"));
            case "timestamp": return String.format("timestamp('%s')",DateUtil.format(ZonedDateTime.ofInstant(
                    Instant.class.cast(value), ZoneOffset.UTC),"yyyy-MM-dd HH:mm:ssZ"));
            case "number": return String.format("CAST(%s AS NUMERIC)", value);
            case "string":
                if (!field.isMultiValueField())
                    return "'"+ escapeBQString(value.toString())+"'";
                else
                    if (value != null && List.class.isAssignableFrom(value.getClass())) {
                        return "[" + ((List)value).stream().map(s -> "'" + escapeBQString(s.toString()) + "'").collect(Collectors.joining(",")) +  "]";
                    }
                    return "[]";
            default: return value.toString();
        }
    }
    private Object toBQType(Object value, AttributeSchema field) {
        if(value == null) return value;
        log.debug("Converting object={}, from dataType={}", value, field.getDataType());
        switch (field.getDataType()){
            case "datetime":return  DateUtil.format(DateUtil.convertDate(ZonedDateTime.class, value),"yyyy-MM-dd HH:mm:ss");
            case "date": return DateUtil.format(Date.class.cast(value),"yyyy-MM-dd");
            //bq timestamp precision is microseconds
            case "timestamp": return  DateUtil.format(ZonedDateTime.ofInstant(Instant.class.cast(value),ZoneOffset.UTC),"yyyy-MM-dd HH:mm:ss.S");
            default: return value;
        }
    }
    private String escapeBQString(String s) {
        return s.replace("\\", "\\\\")
                .replaceAll("\n", "\\\\n").replaceAll("\r", "\\\\r")
                .replaceAll("\t", "\\\\t").replaceAll("\b", "\\\\b")
                .replaceAll("\"", "\\\"").replaceAll("'", "\\\\'");
    }

    private AttributeSchema getAttribute(Field row) {
        AttributeSchema attr = new AttributeSchema(row.getName(), getSyncariType(row.getType().name()));
        attr.setDisplayName(attr.getApiName());
        if(row.getMode() == Field.Mode.REPEATED) {
            attr.setMultiValueField(true);
        }
        return attr;
    }
    
    private List<String> getIds(SyncRequest request) {
        List<EntityData> entityList = request.getData().get(request.getConnector().getId());
        return entityList.stream().map(e -> e.getId()).collect(Collectors.toList());
    }

    private EntitySchema getSchema(Table table) {
        EntitySchema entitySchema = new EntitySchema(table.getTableId().getTable());
        entitySchema.setDisplayName(table.getTableId().getTable());
        if (table.getDefinition().getType() == TableDefinition.Type.VIEW) {
            entitySchema.setReadOnly(true);
        }
        FieldList fields = table.getDefinition().getSchema().getFields();
        log.debug("Fetched {} fields", fields.size());
        fields.forEach(f -> {
            log.debug("Fetched field - {}", f.getName());
            entitySchema.addField(getAttribute(f));
        });
        List<AttributeSchema.Picklist> picklistFields = entitySchema.getAttributes().stream()
                .map(attribute -> new AttributeSchema.Picklist(attribute.getApiName(), attribute.getDisplayName()))
                .collect(Collectors.toList());

        AttributeSchema action = new AttributeSchema(Constants.BQ_INSERT_OPTION, "picklist")
                .setDisplayName("Destination insert option type")
                .setInitializable(true)
                .setUpdateable(true)
                .setDefaultValue(Constants.BQ_INSERT_AND_UPDATE_OPTION)
                .setPicklist(List.of(
                        new AttributeSchema.Picklist(Constants.BQ_INSERT_AND_UPDATE_OPTION, "Insert and Update records"),
                        new AttributeSchema.Picklist(Constants.BQ_FULL_RECORD_TO_INSERT_OPTION, "Convert Updates/Deletes to Inserts (Full Record)"),
                        new AttributeSchema.Picklist(Constants.BQ_PARTIAL_RECORD_TO_INSERT_OPTION, "Convert Updates/Deletes to Inserts (Changed fields only)")));
        entitySchema.getDestParams().add(action);

        AttributeSchema changesetField = new AttributeSchema(Constants.BQ_CHANGESET_FIELD, "picklist")
                .setDisplayName("Field to store original changeset")
                .setInitializable(true)
                .setUpdateable(true);
        entitySchema.getDestParams().add(changesetField);

        return entitySchema;
    }

    private boolean evaluateInsertOption(SyncRequest request) {
        String insertOption = (String) request.getDestParams().getOrDefault(Constants.BQ_INSERT_OPTION, null);
        return Constants.BQ_FULL_RECORD_TO_INSERT_OPTION.equalsIgnoreCase(insertOption) || Constants.BQ_PARTIAL_RECORD_TO_INSERT_OPTION.equalsIgnoreCase(insertOption);
    }

    private void assignChangeSetValue(SyncRequest request, String changesetValue) {
        String changeSetFieldId = (String) request.getDestParams().getOrDefault(Constants.BQ_CHANGESET_FIELD, null);
        String insertOption = (String) request.getDestParams().getOrDefault(Constants.BQ_INSERT_OPTION, null);
        if (insertOption != null && !Constants.BQ_INSERT_AND_UPDATE_OPTION.equalsIgnoreCase(insertOption) && StringUtils.isNotBlank(changeSetFieldId)){
            Optional<AttributeSchema> changesetAttribute = request.getEntitySchema().getAttributes().stream()
                    .filter(attributeSchema -> attributeSchema.getId()
                            .equalsIgnoreCase(changeSetFieldId))
                    .findFirst();
            changesetAttribute.ifPresent(attributeSchema -> request.getData().entrySet().stream()
                    .flatMap(entries -> entries.getValue().stream())
                    .forEach(entityData -> entityData.addValue(attributeSchema.getApiName(), changesetValue)));
        }
    }

    private static String getIdsAsString(SyncRequest request) {
        String dataType = request.getEntitySchema().getIdField().getDataType();
        return request.getIds().stream()
                .map(i -> dataType.contains("int") ? format("%s", i) : format("'%s'", i))
                .collect(Collectors.joining(", "));
    }
}

@EqualsAndHashCode
@ToString
class FieldDefinition{
    public final String syncariId;
    public final String tableName;
    public final String fieldName;
    public final StandardSQLTypeName type;

    public FieldDefinition(String syncariId, String tableName, String fieldName, StandardSQLTypeName type){
        this.syncariId = syncariId;
        this.tableName = tableName;
        this.fieldName = fieldName;
        this.type = type;
    }
}