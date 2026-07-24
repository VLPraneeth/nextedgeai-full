package com.syncari.connector.service;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.OracleErpReceivables.OracleERPReceivablesService;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Integration tests for Oracle ERP Receivables Service.
 *
 * Entities tested:
 * - customer_accounts (SOAP)
 * - customer_parties (SOAP)
 * - customer_party_sites (SOAP)
 * - payment_terms (REST)
 *
 * To run: Set USERNAME, PASSWORD, and URL, then remove @Ignore
 */
@Ignore("Integration test - requires Oracle ERP credentials")
@RunWith(SpringRunner.class)
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
public class OracleErpReceivablesTest {

    // ===========================================
    // TEST CONFIGURATION - Update before running
    // ===========================================

    private static final String USERNAME = "";
    private static final String PASSWORD = "";
    private static final String URL = "";


    // All entities to test
    private static final List<String> ALL_ENTITIES = List.of(
            OracleERPReceivablesService.CUSTOMER_ACCOUNT_ENTITY_NAME,
            OracleERPReceivablesService.CUSTOMER_PARTY_ENTITY_NAME,
            OracleERPReceivablesService.CUSTOMER_PARTY_SITE_ENTITY_NAME,
            OracleERPReceivablesService.PAYMENT_TERMS_ENTITY_NAME
    );

    @Autowired
    private OracleERPReceivablesService service;

    private ConnectorInfo connector;

    // ===========================================
    // SETUP
    // ===========================================

    @Before
    public void setUp() {
        connector = createConnector();
    }

    private ConnectorInfo createConnector() {
        ConnectorInfo conn = new ConnectorInfo();
        conn.setId("test-receivables-new");
        conn.setName("oracleErpReceivables");

        AuthConfig authConfig = new AuthConfig();
        authConfig.setEndpoint(URL);
        authConfig.setUserName(USERNAME);
        authConfig.setAccessToken(PASSWORD);
        authConfig.setPassword(PASSWORD);
        conn.setAuthConfig(authConfig);

        return conn;
    }

    // ===========================================
    // HELPER METHODS
    // ===========================================

    /**
     * Describe an entity and return its schema.
     */
    private EntitySchema describeEntity(String entityName) {
        DescribeRequest request = new DescribeRequest(connector, entityName);
        Optional<EntitySchema> schema = service.describe(request);
        assertTrue("Schema should be present for " + entityName, schema.isPresent());
        assertEquals("Entity name should match", entityName, schema.get().getApiName());
        return schema.get();
    }

    // Max records per test fetch - keeps tests fast
    private static final int MAX_RECORDS = 200;

    /**
     * Fetch records by watermark with specified page size.
     * Uses epoch to current time to guarantee at least 1 record.
     * Limited to MAX_RECORDS to keep tests fast.
     */
    private List<EntityData> fetchByWatermark(EntitySchema schema, String entityName, int pageSize) {
        long startTime = Instant.EPOCH.toEpochMilli();
        long endTime = Instant.now().toEpochMilli();
        WatermarkInfo watermark = new WatermarkInfo(startTime, endTime, true, 0);
        watermark.setLimit(MAX_RECORDS);

        SyncRequest request = new SyncRequest().Builder(connector, schema);
        request.setWatermark(watermark);
        request.setPageSize(Math.min(pageSize, MAX_RECORDS));

        EntityData entity = new EntityData(entityName);
        request.addData(connector.getId(), entity);

        FetchResponse response = service.getByWatermark(request);
        assertNotNull("Response should not be null", response);
        assertNotNull("Iterator should not be null", response.getIterator());
        assertTrue("Should have data for " + entityName, response.getIterator().hasNext());

        return response.getIterator().next();
    }

    /**
     * Get the first record ID for an entity via watermark fetch.
     * No hardcoded IDs - always fetches live data.
     */
    private String getFirstRecordId(EntitySchema schema, String entityName) {
        List<EntityData> data = fetchByWatermark(schema, entityName, 1);
        if (!data.isEmpty()) {
            return data.get(0).getId();
        }
        return null;
    }

    /**
     * Fetch a record by ID.
     */
    private List<EntityData> fetchById(EntitySchema schema, String entityName, String id) {
        SyncRequest request = new SyncRequest().Builder(connector, schema);

        EntityData entity = new EntityData(entityName);
        entity.setId(id);
        request.addData(connector.getId(), entity);

        return service.getByIds(request);
    }

    // ===========================================
    // CONNECTION TESTS
    // ===========================================

    @Test
    public void testConnection() {
        TestConnectionResponse result = service.testConnection(connector, ALL_ENTITIES);
        boolean hasErrors = result.getErrors() != null && !result.getErrors().isEmpty();
        assertFalse("Connection should succeed without errors", hasErrors);
    }

    // ===========================================
    // DESCRIBE TESTS
    // ===========================================

    @Test
    public void testDescribeAll() {
        for (String entityName : ALL_ENTITIES) {
            EntitySchema schema = describeEntity(entityName);

            // Verify ID field exists
            AttributeSchema idField = schema.getIdField();
            assertNotNull("ID field should exist for " + entityName, idField);

            // Verify watermark field exists
            AttributeSchema watermarkField = schema.getWatermarkField();
            assertNotNull("Watermark field should exist for " + entityName, watermarkField);

            // Verify all attributes have valid datatype
            for (AttributeSchema attr : schema.getAttributes()) {
                assertFalse("Attribute " + attr.getApiName() + " should have datatype for " + entityName,
                        attr.getDataType() == null || attr.getDataType().isEmpty());
            }
        }
    }

    // ===========================================
    // GET BY WATERMARK TESTS
    // ===========================================

    @Test
    public void testGetByWatermark() {
        for (String entityName : ALL_ENTITIES) {
            EntitySchema schema = describeEntity(entityName);

            List<EntityData> data = fetchByWatermark(schema, entityName, 5);

            assertFalse("Should return records for " + entityName, data.isEmpty());
            for (EntityData record : data) {
                assertNotNull("Record should have ID for " + entityName, record.getId());
                assertNotNull("Record should have lastModified for " + entityName, record.getLastModified());
            }
        }
    }

    // ===========================================
    // GET BY ID TESTS
    // ===========================================

    @Test
    public void testGetByIds() {
        for (String entityName : ALL_ENTITIES) {
            EntitySchema schema = describeEntity(entityName);

            // Fetch 1 record to get a valid ID
            String recordId = getFirstRecordId(schema, entityName);
            assertNotNull("Should have a record to test with for " + entityName, recordId);

            // Fetch by ID
            List<EntityData> results = fetchById(schema, entityName, recordId);

            assertFalse("Should return the record for " + entityName, results.isEmpty());
            assertEquals("Should return correct record for " + entityName, recordId, results.get(0).getId());
        }
    }

    // ===========================================
    // CUD (CREATE, UPDATE, DELETE) TESTS
    // Pattern: Create -> Update -> Delete -> Verify deleted
    // ===========================================

    private String generateUniqueId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private SyncRequest createWriteRequest(EntitySchema schema, String entityName, Map<String, Object> data) {
        SyncRequest request = new SyncRequest().Builder(connector, schema);
        EntityData entity = new EntityData(entityName);
        entity.setValues(data);
        entity.setSyncariEntityId("test-" + generateUniqueId());
        request.addData(connector.getId(), entity);
        return request;
    }

    private SyncRequest createUpdateRequest(EntitySchema schema, String entityName, String id, Map<String, Object> data) {
        SyncRequest request = new SyncRequest().Builder(connector, schema);
        EntityData entity = new EntityData(entityName);
        entity.setId(id);
        entity.setValues(data);
        entity.setSyncariEntityId("test-" + generateUniqueId());
        request.addData(connector.getId(), entity);
        return request;
    }

    private SyncRequest createDeleteRequest(EntitySchema schema, String entityName, String id) {
        SyncRequest request = new SyncRequest().Builder(connector, schema);
        EntityData entity = new EntityData(entityName);
        entity.setId(id);
        entity.setSyncariEntityId("test-" + generateUniqueId());
        request.addData(connector.getId(), entity);
        return request;
    }

    /**
     * CUD test for customer_party_sites (Location).
     *
     * Create -> Update -> Delete pattern.
     * Uses Address2 as freetext field for update verification.
     *
     * NOTE: If this test fails, verify/update hardcoded values:
     * - Country: "US" (ISO country code)
     * - City, State, PostalCode: Should match valid combinations for the country
     */
    @Test
    public void testCUDCustomerPartySite() {
        String entityName = OracleERPReceivablesService.CUSTOMER_PARTY_SITE_ENTITY_NAME;
        EntitySchema schema = describeEntity(entityName);
        String createdId = null;

        try {
            // CREATE
            String uniqueId = generateUniqueId();
            Map<String, Object> createData = new HashMap<>();
            createData.put("Address1", "Test Address " + uniqueId);
            createData.put("City", "San Francisco");
            createData.put("State", "CA");
            createData.put("PostalCode", "94105");
            createData.put("Country", "US");
            createData.put("Address2", "Suite 100");

            SyncRequest createRequest = createWriteRequest(schema, entityName, createData);
            SyncResponse createResponse = service.create(createRequest);

            assertNotNull("Create response should not be null", createResponse);
            assertFalse("Create should return results", createResponse.getResults().isEmpty());
            Result createResult = createResponse.getResults().get(0);
            assertTrue("Create should succeed: " + getErrorMessage(createResult), createResult.isSuccess());
            createdId = createResult.getId();
            assertNotNull("Created ID should not be null", createdId);

            // Verify created record exists
            List<EntityData> fetchedAfterCreate = fetchById(schema, entityName, createdId);
            assertFalse("Should fetch created record", fetchedAfterCreate.isEmpty());

            // UPDATE - use Address2 as freetext field
            String updatedAddress2 = "Updated Suite " + generateUniqueId();
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("LocationId", createdId);
            updateData.put("Address2", updatedAddress2);

            SyncRequest updateRequest = createUpdateRequest(schema, entityName, createdId, updateData);
            SyncResponse updateResponse = service.update(updateRequest);

            assertNotNull("Update response should not be null", updateResponse);
            assertFalse("Update should return results", updateResponse.getResults().isEmpty());
            Result updateResult = updateResponse.getResults().get(0);
            assertTrue("Update should succeed: " + getErrorMessage(updateResult), updateResult.isSuccess());

            // Verify update
            List<EntityData> fetchedAfterUpdate = fetchById(schema, entityName, createdId);
            assertFalse("Should fetch updated record", fetchedAfterUpdate.isEmpty());
            assertEquals("Address2 should be updated", updatedAddress2,
                    fetchedAfterUpdate.get(0).getValues().get("Address2"));

            // DELETE
            SyncRequest deleteRequest = createDeleteRequest(schema, entityName, createdId);
            SyncResponse deleteResponse = service.delete(deleteRequest);

            assertNotNull("Delete response should not be null", deleteResponse);
            assertFalse("Delete should return results", deleteResponse.getResults().isEmpty());
            Result deleteResult = deleteResponse.getResults().get(0);
            assertTrue("Delete should succeed: " + getErrorMessage(deleteResult), deleteResult.isSuccess());

            createdId = null; // Mark as deleted for cleanup

        } finally {
            // Cleanup: delete if test failed mid-way
            if (createdId != null) {
                try {
                    SyncRequest cleanupRequest = createDeleteRequest(schema, entityName, createdId);
                    service.delete(cleanupRequest);
                } catch (Exception ignored) {
                    // Cleanup errors are non-fatal
                }
            }
        }
    }

    /**
     * CUD test for customer_parties (Organization).
     *
     * Create -> Update -> Delete pattern.
     * Uses OrganizationProfile with OrganizationName for updates.
     *
     * NOTE: If this test fails, verify/update hardcoded values:
     * - CreatedByModule: "HZ_WS" (standard value for web service integrations)
     * - PartyUsageCode: "CUSTOMER" (marks organization as customer)
     */
    @Test
    public void testCUDCustomerParty() {
        String entityName = OracleERPReceivablesService.CUSTOMER_PARTY_ENTITY_NAME;
        EntitySchema schema = describeEntity(entityName);
        String createdId = null;

        try {
            // CREATE - Organization requires OrganizationProfile with OrganizationName and CreatedByModule
            String uniqueId = generateUniqueId();
            Map<String, Object> createData = new HashMap<>();
            createData.put("CreatedByModule", "HZ_WS");

            // OrganizationProfile is required for Organization creation
            // CreatedByModule must also be inside OrganizationProfile per Oracle API
            Map<String, Object> orgProfile = new HashMap<>();
            orgProfile.put("OrganizationName", "Test Org " + uniqueId);
            orgProfile.put("CreatedByModule", "HZ_WS");
            createData.put("OrganizationProfile", List.of(orgProfile));

            // PartyUsageAssignment - marks this as a CUSTOMER
            Map<String, Object> partyUsage = new HashMap<>();
            partyUsage.put("PartyUsageCode", "CUSTOMER");
            partyUsage.put("CreatedByModule", "HZ_WS");
            createData.put("PartyUsageAssignment", List.of(partyUsage));

            SyncRequest createRequest = createWriteRequest(schema, entityName, createData);
            SyncResponse createResponse = service.create(createRequest);

            assertNotNull("Create response should not be null", createResponse);
            assertFalse("Create should return results", createResponse.getResults().isEmpty());
            Result createResult = createResponse.getResults().get(0);
            assertTrue("Create should succeed: " + getErrorMessage(createResult), createResult.isSuccess());
            createdId = createResult.getId();
            assertNotNull("Created ID should not be null", createdId);

            // Verify created record exists
            List<EntityData> fetchedAfterCreate = fetchById(schema, entityName, createdId);
            assertFalse("Should fetch created record", fetchedAfterCreate.isEmpty());

            // UPDATE - update OrganizationName via OrganizationProfile
            String updatedName = "Updated Org " + generateUniqueId();
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("PartyId", createdId);

            Map<String, Object> updateOrgProfile = new HashMap<>();
            updateOrgProfile.put("OrganizationName", updatedName);
            updateData.put("OrganizationProfile", List.of(updateOrgProfile));

            SyncRequest updateRequest = createUpdateRequest(schema, entityName, createdId, updateData);
            SyncResponse updateResponse = service.update(updateRequest);

            assertNotNull("Update response should not be null", updateResponse);
            assertFalse("Update should return results", updateResponse.getResults().isEmpty());
            Result updateResult = updateResponse.getResults().get(0);
            assertTrue("Update should succeed: " + getErrorMessage(updateResult), updateResult.isSuccess());

            // DELETE
            SyncRequest deleteRequest = createDeleteRequest(schema, entityName, createdId);
            SyncResponse deleteResponse = service.delete(deleteRequest);

            assertNotNull("Delete response should not be null", deleteResponse);
            assertFalse("Delete should return results", deleteResponse.getResults().isEmpty());
            Result deleteResult = deleteResponse.getResults().get(0);
            assertTrue("Delete should succeed: " + getErrorMessage(deleteResult), deleteResult.isSuccess());

            createdId = null; // Mark as deleted for cleanup

        } finally {
            // Cleanup: delete if test failed mid-way
            if (createdId != null) {
                try {
                    SyncRequest cleanupRequest = createDeleteRequest(schema, entityName, createdId);
                    service.delete(cleanupRequest);
                } catch (Exception ignored) {
                    // Cleanup errors are non-fatal
                }
            }
        }
    }

    /**
     * CUD test for customer_accounts.
     *
     * Create -> Update -> Delete pattern.
     * Requires an existing PartyId. Uses AccountName as freetext field.
     *
     * Note: CustomerAccount requires a valid PartyId (Organization).
     * We first fetch an existing party to use as parent.
     *
     * NOTE: If this test fails, verify/update hardcoded values:
     * - CreatedByModule: "HZ_WS" (standard value for web service integrations)
     * - PartyId: Fetched dynamically from existing customer_parties
     */
    @Test
    public void testCUDCustomerAccount() {
        String entityName = OracleERPReceivablesService.CUSTOMER_ACCOUNT_ENTITY_NAME;
        EntitySchema schema = describeEntity(entityName);
        String createdId = null;

        // First, get an existing PartyId to link the account to
        EntitySchema partySchema = describeEntity(OracleERPReceivablesService.CUSTOMER_PARTY_ENTITY_NAME);
        String partyId = getFirstRecordId(partySchema, OracleERPReceivablesService.CUSTOMER_PARTY_ENTITY_NAME);
        assertNotNull("Need an existing PartyId to create CustomerAccount", partyId);

        try {
            // CREATE
            String uniqueId = generateUniqueId();
            Map<String, Object> createData = new HashMap<>();
            createData.put("PartyId", partyId);
            createData.put("CreatedByModule", "HZ_WS");
            createData.put("AccountName", "Test Account " + uniqueId);
            createData.put("AccountNumber", "SYNCARI-" + uniqueId);

            SyncRequest createRequest = createWriteRequest(schema, entityName, createData);
            SyncResponse createResponse = service.create(createRequest);

            assertNotNull("Create response should not be null", createResponse);
            assertFalse("Create should return results", createResponse.getResults().isEmpty());
            Result createResult = createResponse.getResults().get(0);
            assertTrue("Create should succeed: " + getErrorMessage(createResult), createResult.isSuccess());
            createdId = createResult.getId();
            assertNotNull("Created ID should not be null", createdId);

            // Verify created record exists
            List<EntityData> fetchedAfterCreate = fetchById(schema, entityName, createdId);
            assertFalse("Should fetch created record", fetchedAfterCreate.isEmpty());

            // UPDATE - use AccountName as freetext field
            String updatedAccountName = "Updated Account " + generateUniqueId();
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("CustomerAccountId", createdId);
            updateData.put("AccountName", updatedAccountName);

            SyncRequest updateRequest = createUpdateRequest(schema, entityName, createdId, updateData);
            SyncResponse updateResponse = service.update(updateRequest);

            assertNotNull("Update response should not be null", updateResponse);
            assertFalse("Update should return results", updateResponse.getResults().isEmpty());
            Result updateResult = updateResponse.getResults().get(0);
            assertTrue("Update should succeed: " + getErrorMessage(updateResult), updateResult.isSuccess());

            // Verify update
            List<EntityData> fetchedAfterUpdate = fetchById(schema, entityName, createdId);
            assertFalse("Should fetch updated record", fetchedAfterUpdate.isEmpty());
            assertEquals("AccountName should be updated", updatedAccountName,
                    fetchedAfterUpdate.get(0).getValues().get("AccountName"));

            // DELETE
            SyncRequest deleteRequest = createDeleteRequest(schema, entityName, createdId);
            SyncResponse deleteResponse = service.delete(deleteRequest);

            assertNotNull("Delete response should not be null", deleteResponse);
            assertFalse("Delete should return results", deleteResponse.getResults().isEmpty());
            Result deleteResult = deleteResponse.getResults().get(0);
            assertTrue("Delete should succeed: " + getErrorMessage(deleteResult), deleteResult.isSuccess());

            createdId = null; // Mark as deleted for cleanup

        } finally {
            // Cleanup: delete if test failed mid-way
            if (createdId != null) {
                try {
                    SyncRequest cleanupRequest = createDeleteRequest(schema, entityName, createdId);
                    service.delete(cleanupRequest);
                } catch (Exception ignored) {
                    // Cleanup errors are non-fatal
                }
            }
        }
    }

    private String getErrorMessage(Result result) {
        if (result.getErrors() != null && !result.getErrors().isEmpty()) {
            return result.getErrors().toString();
        }
        return "";
    }

}
