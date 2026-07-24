package com.syncari.connector.mongodb;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.DataServiceTest;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@Slf4j
@RunWith(SpringRunner.class)
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
public class MongoDBServiceTest implements DataServiceTest {

    private static final String TEST_HOST = "localhost";
    private static final int TEST_PORT = 27019;
    private static final String TEST_DATABASE = "test_syncari_db";
    private static final String TEST_COLLECTION_USERS = "users";
    private static final String TEST_COLLECTION_ORDERS = "orders";

    private static MongodExecutable mongodExecutable;
    private static MongodProcess mongodProcess;
    private static MongoClient mongoClient;

    @Autowired
    MongoDBService mongoDBService;

    private ConnectorInfo connector;

    @BeforeClass
    public static void setUp() throws Exception {
        log.info("Starting embedded MongoDB on port {}", TEST_PORT);

        MongodStarter starter = MongodStarter.getDefaultInstance();
        mongodExecutable = starter.prepare(new MongodConfigBuilder()
                .version(Version.Main.PRODUCTION)
                .net(new Net(TEST_HOST, TEST_PORT, false))
                .build());

        mongodProcess = mongodExecutable.start();

        // Create test client and seed data
        String connectionString = "mongodb://" + TEST_HOST + ":" + TEST_PORT;
        mongoClient = MongoClients.create(connectionString);

        seedTestData();

        log.info("Embedded MongoDB started successfully");
    }

    @AfterClass
    public static void tearDown() {
        if (mongoClient != null) {
            mongoClient.close();
        }
        if (mongodProcess != null) {
            mongodProcess.stop();
        }
        if (mongodExecutable != null) {
            mongodExecutable.stop();
        }
        log.info("Embedded MongoDB stopped");
    }

    private static void seedTestData() {
        MongoDatabase database = mongoClient.getDatabase(TEST_DATABASE);

        // Seed users collection
        MongoCollection<Document> users = database.getCollection(TEST_COLLECTION_USERS);
        users.deleteMany(new Document()); // Clear existing

        List<Document> userDocs = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            Document user = new Document()
                    .append("email", "user" + i + "@example.com")
                    .append("name", "User " + i)
                    .append("age", 20 + i)
                    .append("isActive", i % 2 == 0)
                    .append("createdAt", new Date(System.currentTimeMillis() - (10 - i) * 1000))
                    .append("updatedAt", new Date(System.currentTimeMillis() - (10 - i) * 500));
            userDocs.add(user);
        }
        users.insertMany(userDocs);

        // Seed orders collection
        MongoCollection<Document> orders = database.getCollection(TEST_COLLECTION_ORDERS);
        orders.deleteMany(new Document()); // Clear existing

        List<Document> orderDocs = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Document order = new Document()
                    .append("orderNumber", "ORD-" + String.format("%04d", i))
                    .append("customerId", "user" + i + "@example.com")
                    .append("amount", 100.0 * i)
                    .append("status", i % 2 == 0 ? "completed" : "pending")
                    .append("items", List.of("item1", "item2"))
                    .append("metadata", new Document("source", "web").append("campaign", "spring-sale"))
                    .append("createdAt", new Date(System.currentTimeMillis() - (5 - i) * 2000))
                    .append("updatedAt", new Date(System.currentTimeMillis() - (5 - i) * 1000));
            orderDocs.add(order);
        }
        orders.insertMany(orderDocs);

        // Seed products collection (no timestamp fields - will use _id as watermark)
        MongoCollection<Document> products = database.getCollection("products");
        products.deleteMany(new Document()); // Clear existing

        List<Document> productDocs = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            Document product = new Document()
                    .append("sku", "SKU-" + i)
                    .append("name", "Product " + i)
                    .append("price", 10.0 * i)
                    .append("inStock", i % 3 != 0);
            productDocs.add(product);
        }
        products.insertMany(productDocs);

        log.info("Seeded {} users, {} orders, and {} products", userDocs.size(), orderDocs.size(), productDocs.size());
    }

    @Override
    public ConnectorInfo getConnector() {
        if (connector == null) {
            connector = new ConnectorInfo();
            connector.setId(UUID.randomUUID().toString());
            connector.setInstanceId(UUID.randomUUID().toString());

            AuthConfig authConfig = new AuthConfig();
            authConfig.setUserName("");
            authConfig.setPassword("");
            connector.setAuthConfig(authConfig);

            Map<String, Object> metaConfig = new HashMap<>();
            metaConfig.put("host", TEST_HOST);
            metaConfig.put("port", String.valueOf(TEST_PORT));
            metaConfig.put("database", TEST_DATABASE);
            metaConfig.put("authDatabase", "admin");
            metaConfig.put("useSsl", "false");
            connector.setMetaConfig(metaConfig);
        }
        return connector;
    }

    @Override
    public AuthenticationService getAuthenticationService() {
        return mongoDBService;
    }

    @Override
    public MetadataService getMetadataService() {
        return mongoDBService;
    }

    @Override
    public CommonDataService getDataService() {
        return mongoDBService;
    }

    @Override
    public String getDescribeObject() {
        return TEST_COLLECTION_USERS;
    }

    @Override
    @Test
    public void testConnectionTest() {
        log.info("Testing MongoDB connection");
        verifyTestConnection();
    }

    @Override
    @Test
    public void describeAllTest() {
        log.info("Testing describeAll");
        DescribeAllRequest request = new DescribeAllRequest(getConnector(), getDescribeObjects());
        List<EntitySchema> entities = getMetadataService().describeAll(request);

        assertNotNull(entities);
        assertTrue("Should find at least 2 collections", entities.size() >= 2);

        // Verify users collection schema
        Optional<EntitySchema> usersSchema = entities.stream()
                .filter(e -> TEST_COLLECTION_USERS.equals(e.getApiName()))
                .findFirst();
        assertTrue("Users collection should be present", usersSchema.isPresent());

        EntitySchema users = usersSchema.get();
        assertNotNull("Should have _id field", users.getField("_id"));
        assertTrue("_id should be marked as ID field", users.getField("_id").get().isIdField());
        assertNotNull("Should have watermark field", users.getWatermarkField());

        log.info("Found {} collections with schemas inferred", entities.size());
    }

    @Override
    @Test
    public void describeTest() {
        log.info("Testing describe for {}", TEST_COLLECTION_USERS);
        Optional<EntitySchema> schema = describe(TEST_COLLECTION_USERS, null);

        assertTrue("Schema should be present", schema.isPresent());
        EntitySchema entitySchema = schema.get();

        assertEquals(TEST_COLLECTION_USERS, entitySchema.getApiName());
        assertNotNull("Should have attributes", entitySchema.getAttributes());
        assertTrue("Should have multiple attributes", entitySchema.getAttributes().size() > 0);

        // Verify _id field
        Optional<AttributeSchema> idField = entitySchema.getField("_id");
        assertTrue("Should have _id field", idField.isPresent());
        assertTrue("_id should be ID field", idField.get().isIdField());
        assertFalse("_id should not be nillable", idField.get().isNillable());

        // Verify inferred fields
        assertTrue("Should have email field", entitySchema.getField("email").isPresent());
        assertTrue("Should have name field", entitySchema.getField("name").isPresent());
        assertTrue("Should have age field", entitySchema.getField("age").isPresent());

        log.info("Schema for {} has {} fields", TEST_COLLECTION_USERS, entitySchema.getAttributes().size());
    }

    @Override
    @Test
    public void getByWatermarkSinceEpoch() {
        log.info("Testing getByWatermark since epoch");
        EntitySchema entitySchema = describe(TEST_COLLECTION_USERS, null).get();

        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema);
        WatermarkInfo watermark = new WatermarkInfo(
                Instant.EPOCH.toEpochMilli(),
                Instant.now().toEpochMilli(),
                true,
                0
        );
        syncRequest.setWatermark(watermark);

        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
        assertNotNull(byWatermark);
        assertTrue("Should have data", byWatermark.getIterator().hasNext());

        List<EntityData> data = byWatermark.getIterator().next();
        assertNotNull(data);
        assertTrue("Should have at least one record", data.size() > 0);

        EntityData firstRecord = data.get(0);
        assertNotNull("Should have ID", firstRecord.getId());
        assertNotNull("Should have last modified", firstRecord.getLastModified());
        assertNotNull("Should have values", firstRecord.getValues());

        log.info("Retrieved {} records from epoch", data.size());
    }

    @Override
    @Test
    public void getByWatermarkRecent() {
        log.info("Testing getByWatermark recent");
        verifyGetByWatermarkRecent(TEST_COLLECTION_USERS);
    }

    @Override
    @Test
    public void getByWatermarkWithLimit() {
        log.info("Testing getByWatermark with limit");
        verifyGetByWatermarkWithLimit(TEST_COLLECTION_USERS, 3);
    }

    @Test
    public void getByWatermarkOrdered() {
        log.info("Testing getByWatermark ordering");
        EntitySchema entitySchema = describe(TEST_COLLECTION_USERS, null).get();

        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema);
        WatermarkInfo watermark = new WatermarkInfo(
                Instant.EPOCH.toEpochMilli(),
                Instant.now().toEpochMilli(),
                true,
                0
        );
        syncRequest.setWatermark(watermark);

        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);

        Long previousTimestamp = 0L;
        while (byWatermark.getIterator().hasNext()) {
            List<EntityData> batch = byWatermark.getIterator().next();
            for (EntityData data : batch) {
                Long currentTimestamp = data.getLastModified();
                assertTrue("Records should be ordered by watermark",
                        currentTimestamp >= previousTimestamp);
                previousTimestamp = currentTimestamp;
            }
        }

        log.info("Verified watermark ordering");
    }

    @Override
    @Test
    public void getByWatermarkResultsOrdered() {
        log.info("Testing getByWatermark results ordered");
        verifyGetByWatermarkResultsOrdered(TEST_COLLECTION_USERS);
    }

    @Override
    @Test
    public void getByIds() {
        log.info("Testing getByIds");
        EntitySchema entitySchema = describe(TEST_COLLECTION_USERS, null).get();

        // First get some records to get their IDs
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema);
        WatermarkInfo watermark = new WatermarkInfo(
                Instant.EPOCH.toEpochMilli(),
                Instant.now().toEpochMilli(),
                true,
                0
        );
        syncRequest.setWatermark(watermark);
        syncRequest.setPageSize(2);

        FetchResponse response = getDataService().getByWatermark(syncRequest);
        assertTrue("Iterator should have data", response.getIterator().hasNext());
        List<EntityData> records = response.getIterator().next();
        assertNotNull("Records should not be null", records);
        assertTrue("Should have at least 2 records, but got " + records.size(), records.size() >= 2);

        // Now fetch by IDs
        EntityData ed1 = new EntityData(TEST_COLLECTION_USERS)
                .setConnectorId(getConnector().getId())
                .setId(records.get(0).getId());

        EntityData ed2 = new EntityData(TEST_COLLECTION_USERS)
                .setConnectorId(getConnector().getId())
                .setId(records.get(1).getId());

        Map<String, List<EntityData>> dataMap = Map.of(
                getConnector().getId(),
                List.of(ed1, ed2)
        );

        SyncRequest request = new SyncRequest()
                .setConnector(getConnector())
                .setEntitySchema(entitySchema)
                .setData(dataMap);

        List<EntityData> byIds = getDataService().getByIds(request);
        assertEquals("Should retrieve 2 records", 2, byIds.size());

        log.info("Successfully retrieved {} records by IDs", byIds.size());
    }

    @Override
    @Test
    public void createTest() {
        log.info("Testing create");
        String testEmail = "test-create-" + System.currentTimeMillis() + "@example.com";

        EntitySchema entitySchema = describe(TEST_COLLECTION_USERS, null).get();

        Map<String, Object> values = new HashMap<>();
        values.put("email", testEmail);
        values.put("name", "Test User");
        values.put("age", 25);
        values.put("isActive", true);
        values.put("createdAt", new Date());
        values.put("updatedAt", new Date());

        EntityData entityData = new EntityData(TEST_COLLECTION_USERS)
                .withValues(values)
                .setSyncariEntityId(UUID.randomUUID().toString());

        SyncRequest request = new SyncRequest()
                .setConnector(getConnector())
                .setEntitySchema(entitySchema)
                .setData(Map.of(getConnector().getId(), List.of(entityData)));

        SyncResponse response = getDataService().create(request);

        assertTrue("Create should succeed", response.isSuccess());
        assertEquals("Should have 1 result", 1, response.getResults().size());

        Result result = response.getResults().get(0);
        assertNotNull("Should have ID", result.getId());
        assertTrue("Result should be successful", result.isSuccess());

        log.info("Created record with ID: {}", result.getId());

        // Cleanup
        cleanup(entitySchema, List.of(result.getId()));
    }

    @Override
    @Test
    public void batchCreateTest() {
        log.info("Testing batch create");
        String testPrefix = "batch-create-" + System.currentTimeMillis();

        EntitySchema entitySchema = describe(TEST_COLLECTION_USERS, null).get();

        List<EntityData> dataList = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> values = new HashMap<>();
            values.put("email", testPrefix + "-" + i + "@example.com");
            values.put("name", "Batch User " + i);
            values.put("age", 25 + i);
            values.put("isActive", i % 2 == 0);
            values.put("createdAt", new Date());
            values.put("updatedAt", new Date());

            EntityData entityData = new EntityData(TEST_COLLECTION_USERS)
                    .withValues(values)
                    .setSyncariEntityId(UUID.randomUUID().toString());

            dataList.add(entityData);
        }

        SyncRequest request = new SyncRequest()
                .setConnector(getConnector())
                .setEntitySchema(entitySchema)
                .setData(Map.of(getConnector().getId(), dataList));

        SyncResponse response = getDataService().create(request);

        assertTrue("Batch create should succeed", response.isSuccess());
        assertEquals("Should have 5 results", 5, response.getResults().size());

        List<String> ids = new ArrayList<>();
        for (Result result : response.getResults()) {
            assertNotNull("Should have ID", result.getId());
            assertTrue("Result should be successful", result.isSuccess());
            ids.add(result.getId());
        }

        log.info("Created {} records in batch", ids.size());

        // Cleanup
        cleanup(entitySchema, ids);
    }

    @Override
    @Test
    public void updateTest() {
        log.info("Testing update");
        String testEmail = "test-update-" + System.currentTimeMillis() + "@example.com";

        EntitySchema entitySchema = describe(TEST_COLLECTION_USERS, null).get();

        // First create a record
        Map<String, Object> createValues = new HashMap<>();
        createValues.put("email", testEmail);
        createValues.put("name", "Original Name");
        createValues.put("age", 25);
        createValues.put("isActive", true);
        createValues.put("createdAt", new Date());
        createValues.put("updatedAt", new Date());

        EntityData createData = new EntityData(TEST_COLLECTION_USERS)
                .withValues(createValues)
                .setSyncariEntityId(UUID.randomUUID().toString());

        SyncRequest createRequest = new SyncRequest()
                .setConnector(getConnector())
                .setEntitySchema(entitySchema)
                .setData(Map.of(getConnector().getId(), List.of(createData)));

        SyncResponse createResponse = getDataService().create(createRequest);
        assertTrue(createResponse.isSuccess());
        String recordId = createResponse.getResults().get(0).getId();

        // Now update the record
        Map<String, Object> updateValues = new HashMap<>();
        updateValues.put("_id", recordId);
        updateValues.put("name", "Updated Name");
        updateValues.put("age", 30);
        updateValues.put("updatedAt", new Date());

        EntityData updateData = new EntityData(TEST_COLLECTION_USERS)
                .withValues(updateValues)
                .setId(recordId)
                .setSyncariEntityId(UUID.randomUUID().toString());

        SyncRequest updateRequest = new SyncRequest()
                .setConnector(getConnector())
                .setEntitySchema(entitySchema)
                .setData(Map.of(getConnector().getId(), List.of(updateData)));

        SyncResponse updateResponse = getDataService().update(updateRequest);

        assertTrue("Update should succeed", updateResponse.isSuccess());
        assertEquals("Should have 1 result", 1, updateResponse.getResults().size());
        assertTrue("Result should be successful", updateResponse.getResults().get(0).isSuccess());

        log.info("Updated record with ID: {}", recordId);

        // Cleanup
        cleanup(entitySchema, List.of(recordId));
    }

    @Override
    @Test
    public void deleteTest() {
        log.info("Testing delete");
        String testEmail = "test-delete-" + System.currentTimeMillis() + "@example.com";

        EntitySchema entitySchema = describe(TEST_COLLECTION_USERS, null).get();

        // First create a record
        Map<String, Object> values = new HashMap<>();
        values.put("email", testEmail);
        values.put("name", "To Delete");
        values.put("age", 25);
        values.put("createdAt", new Date());
        values.put("updatedAt", new Date());

        EntityData createData = new EntityData(TEST_COLLECTION_USERS)
                .withValues(values)
                .setSyncariEntityId(UUID.randomUUID().toString());

        SyncRequest createRequest = new SyncRequest()
                .setConnector(getConnector())
                .setEntitySchema(entitySchema)
                .setData(Map.of(getConnector().getId(), List.of(createData)));

        SyncResponse createResponse = getDataService().create(createRequest);
        assertTrue(createResponse.isSuccess());
        String recordId = createResponse.getResults().get(0).getId();

        // Now delete the record
        EntityData deleteData = new EntityData(TEST_COLLECTION_USERS)
                .setId(recordId)
                .setSyncariEntityId(UUID.randomUUID().toString());

        SyncRequest deleteRequest = new SyncRequest()
                .setConnector(getConnector())
                .setEntitySchema(entitySchema)
                .setData(Map.of(getConnector().getId(), List.of(deleteData)));

        SyncResponse deleteResponse = getDataService().delete(deleteRequest);

        assertTrue("Delete should succeed", deleteResponse.isSuccess());
        assertEquals("Should have 1 result", 1, deleteResponse.getResults().size());
        assertTrue("Result should be successful", deleteResponse.getResults().get(0).isSuccess());

        log.info("Deleted record with ID: {}", recordId);
    }

    @Test
    public void testNestedDocuments() {
        log.info("Testing nested documents");
        EntitySchema entitySchema = describe(TEST_COLLECTION_ORDERS, null).get();

        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema);
        WatermarkInfo watermark = new WatermarkInfo(
                Instant.EPOCH.toEpochMilli(),
                Instant.now().toEpochMilli(),
                true,
                0
        );
        syncRequest.setWatermark(watermark);

        FetchResponse response = getDataService().getByWatermark(syncRequest);
        assertTrue(response.getIterator().hasNext());

        List<EntityData> data = response.getIterator().next();
        assertTrue("Should have orders", data.size() > 0);

        EntityData order = data.get(0);
        assertNotNull("Should have metadata", order.getValue("metadata"));
        assertTrue("Metadata should be a Map", order.getValue("metadata") instanceof Map);

        assertNotNull("Should have items", order.getValue("items"));
        assertTrue("Items should be a List", order.getValue("items") instanceof List);

        log.info("Verified nested document handling");
    }

    @Test
    public void testMultipleDataTypes() {
        log.info("Testing multiple data types");
        EntitySchema entitySchema = describe(TEST_COLLECTION_USERS, null).get();

        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema);
        WatermarkInfo watermark = new WatermarkInfo(
                Instant.EPOCH.toEpochMilli(),
                Instant.now().toEpochMilli(),
                true,
                0
        );
        syncRequest.setWatermark(watermark);

        FetchResponse response = getDataService().getByWatermark(syncRequest);
        assertTrue(response.getIterator().hasNext());

        List<EntityData> data = response.getIterator().next();
        EntityData user = data.get(0);

        // Verify string
        assertTrue("Email should be String", user.getValue("email") instanceof String);

        // Verify integer/long
        Object age = user.getValue("age");
        assertTrue("Age should be Number", age instanceof Number);

        // Verify boolean
        Object isActive = user.getValue("isActive");
        assertTrue("isActive should be Boolean", isActive instanceof Boolean);

        log.info("Verified multiple data type handling");
    }

    @Test
    public void testCollectionWithoutTimestampFields() {
        log.info("Testing collection without timestamp fields (uses _id as watermark)");

        // Describe products collection (has no timestamp fields)
        EntitySchema entitySchema = describe("products", null).get();

        // Verify _id is used as watermark field
        assertNotNull("Should have watermark field", entitySchema.getWatermarkField());
        assertEquals("Watermark field should be _id", "_id", entitySchema.getWatermarkField().getApiName());
        assertTrue("Watermark field should be marked as createdAt", entitySchema.getWatermarkField().isCreatedAtField());

        log.info("Watermark field: {}", entitySchema.getWatermarkField().getApiName());

        // Test fetching data using _id as watermark
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema);
        WatermarkInfo watermark = new WatermarkInfo(
                Instant.EPOCH.toEpochMilli(),
                Instant.now().toEpochMilli(),
                true,
                0
        );
        syncRequest.setWatermark(watermark);

        FetchResponse response = getDataService().getByWatermark(syncRequest);
        assertTrue("Should have data", response.getIterator().hasNext());

        List<EntityData> data = response.getIterator().next();
        assertNotNull("Data should not be null", data);
        assertTrue("Should have products", data.size() > 0);

        // Verify data structure
        EntityData product = data.get(0);
        assertNotNull("Should have ID", product.getId());
        assertNotNull("Should have SKU", product.getValue("sku"));
        assertNotNull("Should have name", product.getValue("name"));
        assertNotNull("Should have price", product.getValue("price"));

        log.info("Successfully fetched {} products using _id as watermark", data.size());
    }

    @Test
    public void testGetByWatermarkWithSameTimestamp() {
        log.info("Testing getByWatermark when multiple records have identical timestamps");

        // Create a test collection with records having the same timestamp
        String collectionName = "same_timestamp_test";
        MongoDatabase database = mongoClient.getDatabase(TEST_DATABASE);
        MongoCollection<Document> collection = database.getCollection(collectionName);
        collection.deleteMany(new Document()); // Clear existing

        // Create 5 records with the EXACT same createdAt timestamp
        // Using "createdAt" field name so schema inferrer will detect it as watermark field
        Date sameTimestamp = new Date(System.currentTimeMillis() - 10000);
        List<Document> docs = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Document doc = new Document()
                    .append("name", "Record " + i)
                    .append("value", i * 100)
                    .append("createdAt", sameTimestamp);
            docs.add(doc);
        }
        collection.insertMany(docs);

        log.info("Created 5 records with identical createdAt: {}", sameTimestamp);

        // Get schema
        EntitySchema entitySchema = describe(collectionName, null).get();
        assertNotNull("Schema should exist", entitySchema);

        // Verify createdAt is the watermark field
        log.info("Watermark field: {}", entitySchema.getWatermarkField().getApiName());

        // Set page size to 3 (smaller than total records)
        // This forces pagination in the middle of records with same timestamp
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema);
        syncRequest.setPageSize(3);

        WatermarkInfo watermark = new WatermarkInfo(
                Instant.EPOCH.toEpochMilli(),
                Instant.now().toEpochMilli(),
                true,
                0
        );
        syncRequest.setWatermark(watermark);

        FetchResponse response = getDataService().getByWatermark(syncRequest);

        // Collect all records across all pages
        List<EntityData> allRecords = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        int pageCount = 0;

        while (response.getIterator().hasNext()) {
            List<EntityData> batch = response.getIterator().next();
            pageCount++;
            log.info("Page {}: Retrieved {} records", pageCount, batch.size());

            for (EntityData record : batch) {
                String id = record.getId();
                assertFalse("Should not see duplicate IDs: " + id, seenIds.contains(id));
                seenIds.add(id);
                allRecords.add(record);

                // Verify createdAt timestamp
                Object recordTimestamp = record.getValue("createdAt");
                assertNotNull("Record should have createdAt", recordTimestamp);
                log.info("Record ID: {}, Name: {}, CreatedAt: {}",
                    id, record.getValue("name"), recordTimestamp);
            }
        }

        // CRITICAL ASSERTION: All 5 records should be retrieved despite having same timestamp
        assertEquals("Should retrieve all 5 records with same timestamp, but got " + allRecords.size(),
            5, allRecords.size());

        // Verify we actually had multiple pages (proving pagination occurred)
        assertTrue("Should have had at least 2 pages due to page size of 3", pageCount >= 2);

        log.info("Successfully retrieved all {} records across {} pages", allRecords.size(), pageCount);

        // Verify all expected names are present
        Set<String> names = allRecords.stream()
                .map(ed -> (String) ed.getValue("name"))
                .collect(Collectors.toSet());

        for (int i = 1; i <= 5; i++) {
            String expectedName = "Record " + i;
            assertTrue("Should contain " + expectedName, names.contains(expectedName));
        }

        // Cleanup
        collection.drop();
    }

    private void cleanup(EntitySchema entitySchema, List<String> ids) {
        log.info("Cleaning up {} test records", ids.size());

        List<EntityData> deleteList = new ArrayList<>();
        for (String id : ids) {
            EntityData deleteData = new EntityData(entitySchema.getApiName())
                    .setId(id)
                    .setSyncariEntityId(UUID.randomUUID().toString());
            deleteList.add(deleteData);
        }

        SyncRequest deleteRequest = new SyncRequest()
                .setConnector(getConnector())
                .setEntitySchema(entitySchema)
                .setData(Map.of(getConnector().getId(), deleteList));

        getDataService().delete(deleteRequest);
    }

    @Override
    public Optional<EntitySchema> describe(String describeObject, Runnable runnable) {
        DescribeRequest request = new DescribeRequest(getConnector(), describeObject);
        return getMetadataService().describe(request);
    }

    @Override
    public void getDeletedByWatermark() {
        // MongoDB doesn't track deletions by default
    }

    @Override
    public void batchUpdateTest() {
        // Covered by updateTest
    }

    @Override
    public void batchDeleteTest() {
        // Covered by deleteTest
    }

    @Override
    public void createCustomObjectTest() {
        // Not applicable for MongoDB
    }

    @Override
    public void updateCustomObjectTest() {
        // Not applicable for MongoDB
    }

    @Override
    public void deleteCustomObjectTest() {
        // Not applicable for MongoDB
    }

    @Override
    public void mixedBatchCreateFailuresTest() {
        // TODO: Implement mixed failure scenarios
    }

    @Override
    public void mixedBatchUpdateFailuresTest() {
        // TODO: Implement mixed failure scenarios
    }

    @Override
    public void mixedBatchDeleteFailuresTest() {
        // TODO: Implement mixed failure scenarios
    }

    @Override
    public void allDataTypesTest() {
        // Covered by testMultipleDataTypes
    }

    @Override
    public void referencesTest() {
        // Not applicable for MongoDB (no foreign keys)
    }

    @Override
    public void rateLimitTest() {
        // MongoDB doesn't have built-in rate limiting
    }
}
