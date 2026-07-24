package com.syncari.connector.mongodb;

import com.google.common.collect.Lists;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoSecurityException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.MongoWriteException;
import com.mongodb.bulk.BulkWriteError;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.syncari.connector.Capability;
import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.DefaultCursorBasedIterator;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.SynapseInfoService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.jooq.lambda.function.Function3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component(Constants.MONGODB)
public class MongoDBService implements SynapseInfoService, MetadataService, AuthenticationService, CommonDataService {

    @Autowired
    private MongoDBConnectionManager connectionManager;

    @Autowired
    private MongoDBTypeMapper typeMapper;

    @Autowired
    private MongoDBSchemaInferrer schemaInferrer;

    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_BATCH_SIZE = 25;
    private static final long WATERMARK_INCREMENT = 1 * 24 * 60 * 60 * 1000L; // 1 day in milliseconds

    // ==================== SynapseInfoService Implementation ====================

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getUserPwd());
    }

    @Override
    public List<Capability> getCapabilities() {
        List<Capability> capabilities = new ArrayList<>();
        capabilities.add(Capability.schemaEditInSyncari);
        capabilities.add(Capability.userEditableId);
        capabilities.add(Capability.userEditableWm);
        capabilities.add(Capability.noWatermark);
        capabilities.add(Capability.compositeId);
        capabilities.add(Capability.schemaCreateField);
        return capabilities;
    }

    @Override
    public List<AuthField> getConfigureFields() {
        List<AuthField> fields = new ArrayList<>();

        fields.add(new AuthField()
                .setRequired(false)
                .setDataType("text")
                .setName("connectionString")
                .setLabel("Connection String Override")
                .setHelpSummary(i18n("mongodb_connection_string_help")));

        fields.add(new AuthField()
                .setRequired(true)
                .setDataType("text")
                .setName("host")
                .setLabel("Host")
                .setHelpSummary(i18n("mongodb_host_help")));

        fields.add(new AuthField()
                .setRequired(false)
                .setDataType("integer")
                .setName("port")
                .setLabel("Port")
                .setDefaultValue("27017")
                .setHelpSummary(i18n("mongodb_port_help")));

        fields.add(new AuthField()
                .setRequired(true)
                .setDataType("text")
                .setName("database")
                .setLabel("Database Name")
                .setHelpSummary(i18n("mongodb_database_help")));

        fields.add(new AuthField()
                .setRequired(false)
                .setDataType("text")
                .setName("authDatabase")
                .setLabel("Authentication Database")
                .setDefaultValue("admin")
                .setHelpSummary(i18n("mongodb_auth_database_help")));

        fields.add(new AuthField()
                .setRequired(false)
                .setDataType("checkbox")
                .setName("useSsl")
                .setLabel("Use SSL/TLS Connection")
                .setDefaultValue("true")
                .setHelpSummary(i18n("mongodb_ssl_help")));

        fields.add(new AuthField()
                .setRequired(false)
                .setDataType("checkbox")
                .setName("sslValidateCertificates")
                .setLabel("Validate SSL Certificates")
                .setDefaultValue("false")
                .setHelpSummary(i18n("mongodb_ssl_validate_help")));

        fields.add(ConnectorHelper.getSupportedAuthPicker());

        return fields;
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
        return Constants.MONGODB;
    }

    @Override
    public String getCategory() {
        return "Database";
    }

    @Override
    public UIMetadata getUIMetadata() {
        return new UIMetadata()
                .setIconPath("/assets/icons/logos/mongodb.svg")
                .setDisplayName("MongoDB")
                .setBackgroundColor("#00ED64")
                .setHelpUrl(helpArticlesBaseUrl + "/mongodb");
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "mongodb-capabilities";
    }

    @Override
    public boolean validate(ConnectorInfo connector) {
        String connectionString = getMetaValue(connector, "connectionString");

        if (StringUtils.isBlank(connectionString)) {
            String host = getMetaValue(connector, "host");
            String database = getMetaValue(connector, "database");

            if (StringUtils.isBlank(host)) {
                throw new RuntimeException(i18n("mongodb_host_required"));
            }
            if (StringUtils.isBlank(database)) {
                throw new RuntimeException(i18n("mongodb_database_required"));
            }
        }

        return true;
    }

    // ==================== AuthenticationService Implementation ====================

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse response = new TestConnectionResponse();

        try {
            MongoClient client = connectionManager.getClient(config);
            String databaseName = connectionManager.getDatabaseName(config);
            MongoDatabase database = client.getDatabase(databaseName);

            // Test connection by listing collections
            database.listCollectionNames().first();

            // Success - leave message and errors null/empty for isSuccess() to return true
            response.setCode("200");

        } catch (MongoSecurityException e) {
            log.error("MongoDB authentication failed: {}", e.getMessage());
            response.setCode(HttpStatus.UNAUTHORIZED.name());
            response.setMessage("Authentication failed: " + e.getMessage());
        } catch (MongoTimeoutException e) {
            log.error("MongoDB connection timeout: {}", e.getMessage());
            response.setCode(HttpStatus.REQUEST_TIMEOUT.name());
            response.setMessage("Connection timeout: " + e.getMessage());
        } catch (Exception e) {
            log.error("MongoDB connection failed: {}", e.getMessage(), e);
            response.setCode(HttpStatus.BAD_REQUEST.name());
            response.setMessage("Connection failed: " + e.getMessage());
        }

        return response;
    }

    // ==================== MetadataService Implementation ====================

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        List<EntitySchema> schemas = new ArrayList<>();

        try {
            MongoClient client = connectionManager.getClient(request.getConnector());
            String databaseName = connectionManager.getDatabaseName(request.getConnector());
            MongoDatabase database = client.getDatabase(databaseName);

            // List all collections
            for (String collectionName : database.listCollectionNames()) {
                try {
                    MongoCollection<Document> collection = database.getCollection(collectionName);
                    EntitySchema schema = schemaInferrer.inferSchema(collection, collectionName);
                    schemas.add(schema);
                } catch (Exception e) {
                    log.error("Failed to describe collection {}: {}", collectionName, e.getMessage());
                    // Add basic schema without attributes
                    EntitySchema basicSchema = new EntitySchema();
                    basicSchema.setApiName(collectionName);
                    basicSchema.setDisplayName(collectionName);
                    schemas.add(basicSchema);
                }
            }

        } catch (Exception e) {
            log.error("Failed to list collections: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to list MongoDB collections: " + e.getMessage(), e);
        }

        return schemas;
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        try {
            MongoCollection<Document> collection = getCollection(request.getConnector(), request.getEntity());

            EntitySchema schema = schemaInferrer.inferSchema(collection, request.getEntity());
            return Optional.of(schema);

        } catch (Exception e) {
            log.error("Failed to describe collection {}: {}", request.getEntity(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        // MongoDB creates collections implicitly on first insert
        EntitySchema schema = new EntitySchema();
        schema.setApiName(request.getSchema().getApiName());
        schema.setDisplayName(request.getSchema().getDisplayName());

        // Add default _id field
        AttributeSchema idField = new AttributeSchema();
        idField.setApiName("_id");
        idField.setDisplayName("ID");
        idField.setDataType("id");
        idField.setIdField(true);
        idField.setNillable(false);
        idField.setUpdateable(false);
        idField.setLength(24);

        schema.getAttributes().add(idField);

        return schema;
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        // MongoDB is schemaless - fields are created on document insert
        return request.getSchema();
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        try {
            MongoCollection<Document> collection = getCollection(request.getConnector(), request.getEntityName());

            // Use $unset to remove field from all documents
            Document unset = new Document("$unset", new Document(request.getFieldName(), ""));
            collection.updateMany(new Document(), unset);

        } catch (Exception e) {
            log.error("Failed to delete field {}: {}", request.getFieldName(), e.getMessage(), e);
            throw new RuntimeException("Failed to delete field: " + e.getMessage(), e);
        }
    }

    // ==================== CommonDataService Implementation ====================

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        if (request.getEntitySchema() == null) {
            throw new RuntimeException("Schema cannot be null");
        }
        if (request.getEntitySchema().getWatermarkField() == null) {
            throw new RuntimeException("Watermark field cannot be null");
        }

        WatermarkInfo watermark = request.getWatermark();
        String watermarkField = request.getEntitySchema().getWatermarkField().getApiName();
        boolean isObjectIdWatermark = "_id".equals(watermarkField);

        long startMillis = watermark.getStart() > 0 ? watermark.getStart() : Instant.EPOCH.toEpochMilli();
        long endMillis = watermark.getEnd();

        // Apply windowing for non-initial syncs
        if (!watermark.isInitial() && !watermark.isResync()) {
            endMillis = Math.min(endMillis, startMillis + WATERMARK_INCREMENT);
        } else if (watermark.isInitial() || watermark.isResync()) {
            endMillis = Instant.now().toEpochMilli();
        }

        final long finalStartMillis = startMillis;
        final long finalEndMillis = endMillis;

        // Determine watermark field type once before creating the generator
        MongoCollection<Document> collection = getCollection(request.getConnector(), request.getEntityName());
        final boolean useNumericWatermark;
        if (isObjectIdWatermark) {
            useNumericWatermark = false;
        } else {
            Document sample = collection.find().limit(1).first();
            Object sampleWmValue = sample != null ? sample.get(watermarkField) : null;
            useNumericWatermark = sampleWmValue instanceof Long || sampleWmValue instanceof Integer;
        }

        Function3<WatermarkInfo, Integer, String, DataWithCursor> generator =
                (wm, pageSize, changeStream) -> {
                    try {
                        MongoCollection<Document> coll = getCollection(request.getConnector(), request.getEntityName());

                        // Parse pagination cursor to get both watermark value and _id for tie-breaking
                        Object lastWmValue = null;
                        Object lastId = null;
                        if (StringUtils.isNotBlank(changeStream)) {
                            try {
                                Document lastDoc = Document.parse(changeStream);
                                lastWmValue = lastDoc.get(watermarkField);
                                lastId = lastDoc.get("_id");
                            } catch (Exception e) {
                                log.warn("Failed to parse changeStream: {}", e.getMessage());
                            }
                        }

                        // Build date range filter based on field type, including pagination condition
                        // When paginating, use _id as tie-breaker to handle records with same timestamp
                        Bson filter;
                        if (isObjectIdWatermark) {
                            // For _id (ObjectId), create ObjectIds from timestamps
                            ObjectId startObjectId = new ObjectId(new Date(finalStartMillis));
                            ObjectId endObjectId = new ObjectId(new Date(finalEndMillis));
                            if (lastWmValue != null) {
                                // Simple case: _id is the watermark, so just use gt
                                filter = Filters.and(
                                        Filters.gt(watermarkField, lastWmValue),
                                        Filters.gte(watermarkField, startObjectId),
                                        Filters.lt(watermarkField, endObjectId)
                                );
                            } else {
                                filter = Filters.and(
                                        Filters.gte(watermarkField, startObjectId),
                                        Filters.lt(watermarkField, endObjectId)
                                );
                            }
                        } else if (useNumericWatermark) {
                            // For Long/Integer timestamp fields (NumberLong in MongoDB)
                            if (lastWmValue != null && lastId != null) {
                                // Use compound filter: (wm > lastWm) OR (wm == lastWm AND _id > lastId)
                                filter = Filters.and(
                                        Filters.or(
                                                Filters.gt(watermarkField, lastWmValue),
                                                Filters.and(
                                                        Filters.eq(watermarkField, lastWmValue),
                                                        Filters.gt("_id", lastId)
                                                )
                                        ),
                                        Filters.gte(watermarkField, finalStartMillis),
                                        Filters.lt(watermarkField, finalEndMillis)
                                );
                            } else {
                                filter = Filters.and(
                                        Filters.gte(watermarkField, finalStartMillis),
                                        Filters.lt(watermarkField, finalEndMillis)
                                );
                            }
                        } else {
                            // For Date fields
                            if (lastWmValue != null && lastId != null) {
                                // Use compound filter: (wm > lastWm) OR (wm == lastWm AND _id > lastId)
                                filter = Filters.and(
                                        Filters.or(
                                                Filters.gt(watermarkField, lastWmValue),
                                                Filters.and(
                                                        Filters.eq(watermarkField, lastWmValue),
                                                        Filters.gt("_id", lastId)
                                                )
                                        ),
                                        Filters.gte(watermarkField, new Date(finalStartMillis)),
                                        Filters.lt(watermarkField, new Date(finalEndMillis))
                                );
                            } else {
                                filter = Filters.and(
                                        Filters.gte(watermarkField, new Date(finalStartMillis)),
                                        Filters.lt(watermarkField, new Date(finalEndMillis))
                                );
                            }
                        }

                        // Sort by watermark field AND _id to ensure consistent ordering and tie-breaking
                        Bson sort = isObjectIdWatermark
                                ? Sorts.ascending(watermarkField)
                                : Sorts.orderBy(Sorts.ascending(watermarkField), Sorts.ascending("_id"));

                        FindIterable<Document> cursor = coll.find(filter)
                                .sort(sort)
                                .limit(pageSize);

                        List<EntityData> entities = new ArrayList<>();
                        String newCursor = null;
                        Document lastDoc = null;

                        for (Document doc : cursor) {
                            EntityData entity = typeMapper.documentToEntityData(doc, request.getEntitySchema(), request.getEntityName());
                            entities.add(entity);
                            lastDoc = doc;
                        }

                        // Always store cursor if we have any entities, regardless of page size
                        // This ensures position tracking even on the last page with fewer records
                        if (lastDoc != null) {
                            newCursor = lastDoc.toJson();
                        }

                        return new DataWithCursor(changeStream, newCursor, entities);

                    } catch (Exception e) {
                        log.error("Failed to fetch data by watermark: {}", e.getMessage(), e);
                        handleMongoException(e);
                        return new DataWithCursor(changeStream, null, Collections.emptyList());
                    }
                };

        int pageSize = request.getPageSize() > 0 ? request.getPageSize() : DEFAULT_PAGE_SIZE;

        DefaultCursorBasedIterator iterator = new DefaultCursorBasedIterator(
                watermark,
                watermark.getChangeStream(),
                watermark.getOffset(),
                generator,
                new ArrayList<>(),
                pageSize,
                watermark.getLimit(),
                true
        );

        return new FetchResponse(watermark, iterator);
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        if (!request.getEntitySchema().hasIdField()) {
            throw new RuntimeException("ID field not defined for entity " + request.getEntityName());
        }

        List<EntityData> dataList = request.getData().get(request.getConnector().getId());
        if (dataList == null || dataList.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> ids = dataList.stream()
                .map(EntityData::getId)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());

        if (ids.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            MongoCollection<Document> collection = getCollection(request.getConnector(), request.getEntityName());

            EntitySchema schema = request.getEntitySchema();
            String idField = schema.getIdField().getApiName();

            Bson filter;
            if (schema.hasCompositeKeyFields()) {
                // Handle composite keys
                List<Bson> orFilters = new ArrayList<>();
                for (String compositeId : ids) {
                    Map<String, String> keyMap = parseCompositeKey(compositeId, schema);
                    List<Bson> andFilters = new ArrayList<>();
                    keyMap.forEach((field, value) -> {
                        Object convertedValue = convertIdValueForQuery(value, field, schema);
                        andFilters.add(Filters.eq(field, convertedValue));
                    });
                    orFilters.add(Filters.and(andFilters));
                }
                filter = Filters.or(orFilters);
            } else {
                // Simple _id lookup
                List<Object> objectIds = ids.stream()
                        .map(id -> convertIdValueForQuery(id, idField, schema))
                        .collect(Collectors.toList());
                filter = Filters.in(idField, objectIds);
            }

            // Execute query
            List<EntityData> results = new ArrayList<>();
            for (Document doc : collection.find(filter)) {
                results.add(typeMapper.documentToEntityData(doc, schema, request.getEntityName()));
            }

            return results;

        } catch (Exception e) {
            log.error("Failed to get by IDs: {}", e.getMessage(), e);
            handleMongoException(e);
            return Collections.emptyList();
        }
    }

    private MongoCollection<Document> getCollection(ConnectorInfo request, String request1) {
        MongoClient client = connectionManager.getClient(request);
        String databaseName = connectionManager.getDatabaseName(request);
        MongoDatabase database = client.getDatabase(databaseName);
        MongoCollection<Document> collection = database.getCollection(request1);
        return collection;
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        List<EntityData> dataList = request.getData().get(request.getConnector().getId());
        if (dataList == null || dataList.isEmpty()) {
            return new SyncResponse();
        }

        SyncResponse response = new SyncResponse();

        try {
            MongoCollection<Document> collection = getCollection(request.getConnector(), request.getEntityName());

            // Batch inserts
            List<List<EntityData>> batches = Lists.partition(dataList, MAX_BATCH_SIZE);

            for (List<EntityData> batch : batches) {
                List<Document> documents = batch.stream()
                        .map(ed -> typeMapper.entityDataToDocument(ed, request.getEntitySchema()))
                        .collect(Collectors.toList());

                try {
                    collection.insertMany(documents);

                    // Build success results
                    for (int i = 0; i < batch.size(); i++) {
                        EntityData ed = batch.get(i);
                        String insertedId = documents.get(i).getObjectId("_id").toHexString();
                        response.getResults().add(new Result(true, insertedId, ed.getSyncariEntityId()));
                    }

                } catch (MongoBulkWriteException e) {
                    handleBulkWriteErrors(e, batch, response);
                } catch (Exception e) {
                    log.error("Batch insert failed: {}", e.getMessage());
                    for (EntityData ed : batch) {
                        Result result = new Result(false, ed.getId(), ed.getSyncariEntityId());
                        result.addError(e.getMessage());
                        response.getResults().add(result);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to create documents: {}", e.getMessage(), e);
            for (EntityData ed : dataList) {
                Result result = new Result(false, ed.getId(), ed.getSyncariEntityId());
                result.addError(e.getMessage());
                response.getResults().add(result);
            }
        }

        return response;
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        List<EntityData> dataList = request.getData().get(request.getConnector().getId());
        if (dataList == null || dataList.isEmpty()) {
            return new SyncResponse();
        }

        SyncResponse response = new SyncResponse();

        try {
            MongoCollection<Document> collection = getCollection(request.getConnector(), request.getEntityName());

            EntitySchema schema = request.getEntitySchema();

            for (EntityData ed : dataList) {
                try {
                    Bson filter = buildIdFilter(ed.getId(), schema);
                    Document updateDoc = typeMapper.entityDataToDocument(ed, schema);
                    updateDoc.remove("_id"); // Don't update _id

                    Document update = new Document("$set", updateDoc);
                    UpdateResult result = collection.updateOne(filter, update);

                    if (result.getMatchedCount() > 0) {
                        response.getResults().add(new Result(true, ed.getId(), ed.getSyncariEntityId()));
                    } else {
                        Result failResult = new Result(false, ed.getId(), ed.getSyncariEntityId());
                        failResult.addError("Document not found");
                        response.getResults().add(failResult);
                    }

                } catch (Exception e) {
                    log.error("Failed to update document {}: {}", ed.getId(), e.getMessage());
                    Result result = new Result(false, ed.getId(), ed.getSyncariEntityId());
                    result.addError(e.getMessage());
                    response.getResults().add(result);
                }
            }

        } catch (Exception e) {
            log.error("Failed to update documents: {}", e.getMessage(), e);
            for (EntityData ed : dataList) {
                Result result = new Result(false, ed.getId(), ed.getSyncariEntityId());
                result.addError(e.getMessage());
                response.getResults().add(result);
            }
        }

        return response;
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        List<EntityData> dataList = request.getData().get(request.getConnector().getId());
        if (dataList == null || dataList.isEmpty()) {
            return new SyncResponse();
        }

        SyncResponse response = new SyncResponse();

        try {
            MongoCollection<Document> collection = getCollection(request.getConnector(), request.getEntityName());

            EntitySchema schema = request.getEntitySchema();
            List<String> ids = dataList.stream()
                    .map(EntityData::getId)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toList());

            Bson filter = buildIdsFilter(ids, schema);
            DeleteResult result = collection.deleteMany(filter);

            // Mark all as successful
            for (EntityData ed : dataList) {
                response.getResults().add(new Result(true, ed.getId(), ed.getSyncariEntityId()));
            }

        } catch (Exception e) {
            log.error("Failed to delete documents: {}", e.getMessage(), e);
            for (EntityData ed : dataList) {
                Result result = new Result(false, ed.getId(), ed.getSyncariEntityId());
                result.addError(e.getMessage());
                response.getResults().add(result);
            }
        }

        return response;
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        try {
            MongoCollection<Document> collection = getCollection(request.getConnector(), request.getEntityName());

            Document oldest = collection.find()
                    .sort(Sorts.ascending("_id"))
                    .limit(1)
                    .first();

            if (oldest != null) {
                ObjectId oid = oldest.getObjectId("_id");
                return oid.getDate().getTime();
            }

        } catch (Exception e) {
            log.warn("Failed to get first created time: {}", e.getMessage());
        }

        return Instant.EPOCH.toEpochMilli();
    }

    // ==================== Helper Methods ====================

    private Bson buildIdFilter(String id, EntitySchema schema) {
        String idField = schema.getIdField().getApiName();

        if (schema.hasCompositeKeyFields()) {
            Map<String, String> keyMap = parseCompositeKey(id, schema);
            List<Bson> filters = new ArrayList<>();
            keyMap.forEach((field, value) -> {
                Object convertedValue = convertIdValueForQuery(value, field, schema);
                filters.add(Filters.eq(field, convertedValue));
            });
            return Filters.and(filters);
        } else {
            Object convertedId = convertIdValueForQuery(id, idField, schema);
            return Filters.eq(idField, convertedId);
        }
    }

    private Bson buildIdsFilter(List<String> ids, EntitySchema schema) {
        String idField = schema.getIdField().getApiName();

        if (schema.hasCompositeKeyFields()) {
            List<Bson> orFilters = new ArrayList<>();
            for (String id : ids) {
                orFilters.add(buildIdFilter(id, schema));
            }
            return Filters.or(orFilters);
        } else {
            List<Object> objectIds = ids.stream()
                    .map(id -> convertIdValueForQuery(id, idField, schema))
                    .collect(Collectors.toList());
            return Filters.in(idField, objectIds);
        }
    }

    private Object convertIdValueForQuery(String value, String fieldName, EntitySchema schema) {
        if ("_id".equals(fieldName) && ObjectId.isValid(value)) {
            return new ObjectId(value);
        }

        Optional<AttributeSchema> fieldSchema = schema.getField(fieldName);
        if (fieldSchema.isPresent()) {
            String dataType = fieldSchema.get().getDataType();
            try {
                switch (dataType.toLowerCase()) {
                    case "integer":
                    case "int":
                        return Integer.parseInt(value);
                    case "long":
                        return Long.parseLong(value);
                    case "double":
                    case "float":
                        return Double.parseDouble(value);
                    default:
                        return value;
                }
            } catch (Exception e) {
                log.warn("Failed to convert ID value {} to type {}: {}", value, dataType, e.getMessage());
            }
        }

        return value;
    }

    private Map<String, String> parseCompositeKey(String compositeId, EntitySchema schema) {
        Map<String, String> keyMap = new HashMap<>();
        List<AttributeSchema> compositeFields = schema.getCompositeKeyFields();

        if (compositeId.contains(EntitySchema.COMPOSITE_KEY_DELIMETER)) {
            String[] parts = compositeId.split("\\" + EntitySchema.COMPOSITE_KEY_DELIMETER);
            for (int i = 0; i < parts.length && i < compositeFields.size(); i++) {
                keyMap.put(compositeFields.get(i).getApiName(), parts[i]);
            }
        } else {
            // Single field, use as is
            if (!compositeFields.isEmpty()) {
                keyMap.put(compositeFields.get(0).getApiName(), compositeId);
            }
        }

        return keyMap;
    }

    private void handleBulkWriteErrors(MongoBulkWriteException e, List<EntityData> batch, SyncResponse response) {
        List<BulkWriteError> errors = e.getWriteErrors();
        Set<Integer> failedIndices = errors.stream()
                .map(BulkWriteError::getIndex)
                .collect(Collectors.toSet());

        for (int i = 0; i < batch.size(); i++) {
            EntityData ed = batch.get(i);
            final int index = i;
            if (failedIndices.contains(index)) {
                Result result = new Result(false, ed.getId(), ed.getSyncariEntityId());
                BulkWriteError error = errors.stream()
                        .filter(err -> err.getIndex() == index)
                        .findFirst()
                        .orElse(null);
                if (error != null) {
                    result.addError(error.getMessage());
                }
                response.getResults().add(result);
            } else {
                response.getResults().add(new Result(true, ed.getId(), ed.getSyncariEntityId()));
            }
        }
    }

    private void handleMongoException(Exception e) {
        if (e instanceof MongoWriteException) {
            MongoWriteException mwe = (MongoWriteException) e;
            if (mwe.getError().getCode() == 11000) {
                throw new NonRetriableException(
                        ErrorCodes.UPDATE_FAILED.name(),
                        "Duplicate key error: " + mwe.getError().getMessage(),
                        HttpStatus.CONFLICT.name(),
                        e
                );
            }
        } else if (e instanceof MongoTimeoutException) {
            throw new RetriableException(
                    ErrorCodes.TIME_OUT.name(),
                    "MongoDB operation timed out",
                    ErrorCodes.TIME_OUT.name(),
                    e
            );
        } else if (e instanceof MongoSecurityException) {
            throw new NonRetriableException(
                    ErrorCodes.LOGIN_ERROR.name(),
                    "MongoDB authentication failed",
                    HttpStatus.UNAUTHORIZED.name(),
                    e
            );
        }

        throw new RuntimeException("MongoDB operation failed: " + e.getMessage(), e);
    }

    private String getMetaValue(ConnectorInfo connector, String key) {
        return getMetaValue(connector, key, null);
    }

    private String getMetaValue(ConnectorInfo connector, String key, String defaultValue) {
        Object value = connector.getMetaConfig().get(key);
        return value != null ? value.toString() : defaultValue;
    }
}
