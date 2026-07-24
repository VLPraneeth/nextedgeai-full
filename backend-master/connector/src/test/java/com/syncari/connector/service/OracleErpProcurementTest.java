package com.syncari.connector.service;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.OracleErpProcurement.OracleERPProcurementService;
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
 * Integration tests for Oracle ERP Procurement Service.
 *
 * Entities tested:
 * - suppliers (REST)
 *
 * To run: Set USERNAME, PASSWORD, and URL, then remove @Ignore
 */
@Ignore("Integration test - requires Oracle ERP credentials")
@RunWith(SpringRunner.class)
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
public class OracleErpProcurementTest {

    // ===========================================
    // TEST CONFIGURATION - Update before running
    // ===========================================

    private static final String USERNAME = "";
    private static final String PASSWORD = "";
    private static final String URL = "";

    // Max records per test fetch - keeps tests fast
    private static final int MAX_RECORDS = 200;

    // All entities to test
    private static final List<String> ALL_ENTITIES = List.of(
            OracleERPProcurementService.SUPPLIERS_ENTITY_NAME
    );

    @Autowired
    private OracleERPProcurementService service;

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
        conn.setId("test-procurement-new");
        conn.setName("oracleErpProcurement");

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

    private EntitySchema describeEntity(String entityName) {
        DescribeRequest request = new DescribeRequest(connector, entityName);
        Optional<EntitySchema> schema = service.describe(request);
        assertTrue("Schema should be present for " + entityName, schema.isPresent());
        assertEquals("Entity name should match", entityName, schema.get().getApiName());
        return schema.get();
    }

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

    private String getFirstRecordId(EntitySchema schema, String entityName) {
        List<EntityData> data = fetchByWatermark(schema, entityName, 1);
        if (!data.isEmpty()) {
            return data.get(0).getId();
        }
        return null;
    }

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

            AttributeSchema idField = schema.getIdField();
            assertNotNull("ID field should exist for " + entityName, idField);

            AttributeSchema watermarkField = schema.getWatermarkField();
            assertNotNull("Watermark field should exist for " + entityName, watermarkField);

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

            String recordId = getFirstRecordId(schema, entityName);
            assertNotNull("Should have a record to test with for " + entityName, recordId);

            List<EntityData> results = fetchById(schema, entityName, recordId);

            assertFalse("Should return the record for " + entityName, results.isEmpty());
            assertEquals("Should return correct record for " + entityName, recordId, results.get(0).getId());
        }
    }

    // ===========================================
    // CU (CREATE, UPDATE) TESTS
    // Note: Oracle Procurement Suppliers does NOT support DELETE
    // Pattern: Create -> Update -> Verify (no delete)
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

    /**
     * CU test for suppliers (Supplier).
     *
     * Create -> Update pattern (no delete - not supported by Oracle API).
     * Uses Alias as freetext field for update verification.
     *
     * NOTE: If this test fails, verify/update hardcoded values:
     * - SupplierType: "Supplier" (standard type)
     * - BusinessRelationshipCode: "SPEND_AUTHORIZED" (required)
     * - TaxOrganizationTypeCode: "CORPORATION" (required)
     *
     * WARNING: This test creates a supplier that cannot be deleted via API.
     * Created suppliers will have name prefix "SyncariTest_" for manual cleanup.
     */
    @Test
    public void testCUSupplier() {
        String entityName = OracleERPProcurementService.SUPPLIERS_ENTITY_NAME;
        EntitySchema schema = describeEntity(entityName);

        // CREATE
        String uniqueId = generateUniqueId();
        String supplierName = "SyncariTest_" + uniqueId;

        Map<String, Object> createData = new HashMap<>();
        createData.put("Supplier", supplierName);
        createData.put("SupplierType", "Supplier");
        createData.put("BusinessRelationshipCode", "SPEND_AUTHORIZED");
        createData.put("TaxOrganizationTypeCode", "CORPORATION");

        SyncRequest createRequest = createWriteRequest(schema, entityName, createData);
        SyncResponse createResponse = service.create(createRequest);

        assertNotNull("Create response should not be null", createResponse);
        assertFalse("Create should return results", createResponse.getResults().isEmpty());
        Result createResult = createResponse.getResults().get(0);
        assertTrue("Create should succeed: " + getErrorMessage(createResult), createResult.isSuccess());
        String createdId = createResult.getId();
        assertNotNull("Created ID should not be null", createdId);

        // Verify created record exists
        List<EntityData> fetchedAfterCreate = fetchById(schema, entityName, createdId);
        assertFalse("Should fetch created record", fetchedAfterCreate.isEmpty());
        assertEquals("Supplier name should match", supplierName,
                fetchedAfterCreate.get(0).getValues().get("Supplier"));

        // UPDATE - use Alias as freetext field
        String updatedAlias = "Updated_" + generateUniqueId();
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("SupplierId", createdId);
        updateData.put("Alias", updatedAlias);

        SyncRequest updateRequest = createUpdateRequest(schema, entityName, createdId, updateData);
        SyncResponse updateResponse = service.update(updateRequest);

        assertNotNull("Update response should not be null", updateResponse);
        assertFalse("Update should return results", updateResponse.getResults().isEmpty());
        Result updateResult = updateResponse.getResults().get(0);
        assertTrue("Update should succeed: " + getErrorMessage(updateResult), updateResult.isSuccess());

        // Verify update
        List<EntityData> fetchedAfterUpdate = fetchById(schema, entityName, createdId);
        assertFalse("Should fetch updated record", fetchedAfterUpdate.isEmpty());
        assertEquals("Alias should be updated", updatedAlias,
                fetchedAfterUpdate.get(0).getValues().get("Alias"));

        // NOTE: Cannot delete - Oracle Procurement Suppliers API does not support DELETE
        // Created record will remain with name prefix "SyncariTest_" for manual identification
    }

    private String getErrorMessage(Result result) {
        if (result.getErrors() != null && !result.getErrors().isEmpty()) {
            return result.getErrors().toString();
        }
        return "";
    }

}
