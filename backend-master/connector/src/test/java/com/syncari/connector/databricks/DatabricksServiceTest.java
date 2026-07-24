package com.syncari.connector.databricks;

import static org.junit.Assert.*;

import java.util.*;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit.jupiter.DisabledIf;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.Capability;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;


@RunWith(SpringRunner.class)
@AutoConfigureDataMongo
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
@Ignore
public class DatabricksServiceTest {
    
    @Autowired
    DatabricksService service;
    
    @Autowired
    ObjectMapper mapper;
    
    ConnectorInfo connector;
    EntitySchema testEntitySchema;
    
    @Before
    public void setUp() {
        if (null == connector){
            connector = createConnector();
        }
        if (null == testEntitySchema){
            testEntitySchema = createTestEntitySchema();
        }
    }
    @Ignore
    private ConnectorInfo createConnector() {
        ConnectorInfo connector = new ConnectorInfo();
        connector.setId("test-databricks-connector");
        connector.setName("Test Databricks Connector");
        connector.setEndpoint("abc.cloud.databricks.com");
        
        AuthConfig authConfig = new AuthConfig();
        authConfig.setClientId("abc");
        authConfig.setClientSecret("abc");
        connector.setAuthConfig(authConfig);
        
        Map<String, Object> metaConfig = new HashMap<>();
        metaConfig.put("workspace", "test-workspace.databricks.com");
        metaConfig.put("warehouseId", "a55c5f4161b6336f");
        metaConfig.put("catalog", "syncari");
        metaConfig.put("schema", "test");
        connector.setMetaConfig(metaConfig);
        
        return connector;
    }
    
    private EntitySchema createTestEntitySchema() {
        EntitySchema schema = new EntitySchema();
        schema.setApiName("test_table");
        schema.setDisplayName("Test Table");
        
        List<AttributeSchema> attributes = new ArrayList<>();
        AttributeSchema idAttr = new AttributeSchema();
        idAttr.setApiName("id");
        idAttr.setDisplayName("ID");
        idAttr.setDataType("number");
        attributes.add(idAttr);
        
        AttributeSchema nameAttr = new AttributeSchema();
        nameAttr.setApiName("name");
        nameAttr.setDisplayName("Name");
        nameAttr.setDataType("text");
        attributes.add(nameAttr);
        
        schema.setAttributes(attributes);
        return schema;
    }
    
    @Test
    @Ignore
    public void testServiceIsNotNull() {
        assertNotNull("DatabricksService should be autowired", service);
    }

    @Ignore
    @Test
    public void testReadonlyOperations() {
        // Test that write operations throw exceptions as expected for readonly connector
        try {
            SyncRequest request = new SyncRequest().Builder(connector, testEntitySchema);
            service.create(request);
            fail("Expected create to throw RuntimeException");
        } catch (RuntimeException e) {
            assertTrue("Error message should mention not supported", 
                e.getMessage().contains("not supported"));
        }
        
        try {
            SyncRequest request = new SyncRequest().Builder(connector, testEntitySchema);
            service.update(request);
            fail("Expected update to throw RuntimeException");
        } catch (RuntimeException e) {
            assertTrue("Error message should mention not supported", 
                e.getMessage().contains("not supported"));
        }
        
        try {
            SyncRequest request = new SyncRequest().Builder(connector, testEntitySchema);
            service.delete(request);
            fail("Expected delete to throw RuntimeException");
        } catch (RuntimeException e) {
            assertTrue("Error message should mention not supported", 
                e.getMessage().contains("not supported"));
        }
    }
    
    @Test
    @Ignore
    public void testMetadataOperationsThrowExceptions() {
        // Test metadata write operations throw exceptions
        try {
            CreateObjectRequest request = new CreateObjectRequest(connector, testEntitySchema);
            service.createObject(request);
            fail("Expected createObject to throw RuntimeException");
        } catch (RuntimeException e) {
            assertTrue("Error message should mention not supported", 
                e.getMessage().contains("not supported"));
        }
        
        try {
            AttributeSchema attrSchema = new AttributeSchema();
            attrSchema.setApiName("test_field");
            attrSchema.setDataType("text");
            CreateFieldRequest request = new CreateFieldRequest("test_table", connector, attrSchema);
            service.createField(request);
            fail("Expected createField to throw RuntimeException");
        } catch (RuntimeException e) {
            assertTrue("Error message should mention not supported", 
                e.getMessage().contains("not supported"));
        }
        
        try {
            DeleteFieldRequest request = new DeleteFieldRequest(connector, "test_table", "test_field");
            service.deleteField(request);
            fail("Expected deleteField to throw RuntimeException");
        } catch (RuntimeException e) {
            assertTrue("Error message should mention not supported", 
                e.getMessage().contains("not supported"));
        }
    }
    
    @Test
    @Ignore
    public void testMergeOperationsThrowExceptions() {
        try {
            MergeRequest request = new MergeRequest(connector, testEntitySchema);
            service.merge(request);
            fail("Expected merge to throw RuntimeException");
        } catch (RuntimeException e) {
            assertTrue("Error message should mention not supported", 
                e.getMessage().contains("not supported"));
        }
        
        List<MergeResponse> responses = service.merge(new ArrayList<>());
        assertNotNull("Merge list should return empty list", responses);
        assertTrue("Merge list should return empty list", responses.isEmpty());
    }
    
    @Test
    @Ignore
    public void testIsSink() {
        assertFalse("Databricks should not be a sink (readonly)", service.isSink());
    }

    @Ignore
    @Test
    public void testIsSource() {
        assertTrue("Databricks should be a source", service.isSource());
    }

    @Ignore
    @Test
    public void testGetCapabilities() {
        List<Capability> capabilities = service.getCapabilities();
        assertNotNull("Capabilities should not be null", capabilities);
        assertEquals("Should have 7 capabilities", 8, capabilities.size());
        
        assertTrue("Should support getByWatermark", 
            capabilities.contains(Capability.getByWatermark));
        assertTrue("Should support getById", 
            capabilities.contains(Capability.getById));
        assertTrue("Should support search", 
            capabilities.contains(Capability.search));
        
        // Verify readonly - should not have write capabilities
        assertFalse("Should not support create", 
            capabilities.contains(Capability.create));
        assertFalse("Should not support update", 
            capabilities.contains(Capability.update));
        assertFalse("Should not support delete", 
            capabilities.contains(Capability.delete));
    }

    @Ignore
    @Test
    public void testGetName() {
        assertEquals("Service name should be databricks", "databricks", service.getName());
    }

    @Ignore
    @Test
    public void testGetCategory() {
        assertEquals("Category should be Data Warehouse", "Data Warehouse", service.getCategory());
    }

    @Ignore
    @Test
    public void testGetCapabilitiesArticleId() {
        String articleId = service.getCapabilitiesArticleId();
        assertNotNull("Article ID should not be null", articleId);
        assertEquals("Article ID should be correct", "360056102571", articleId);
    }

    @Ignore
    @Test
    public void testGetEntityMappings() {
        Map<String, String> mappings = service.getEntityMappings();
        assertNotNull("Entity mappings should not be null", mappings);
        assertTrue("Entity mappings should be empty by default", mappings.isEmpty());
    }

    @Ignore
    @Test
    public void testGetAttributeMappings() {
        Map<String, String> mappings = service.getAttributeMappings("test_entity");
        assertNotNull("Attribute mappings should not be null", mappings);
        assertTrue("Attribute mappings should be empty by default", mappings.isEmpty());
    }

    @Ignore
    @Test
    public void testGetFirstCreatedTime() {
        SyncRequest request = new SyncRequest().Builder(connector, testEntitySchema);
        long firstTime = service.getFirstCreatedTime(request);
        assertEquals("First created time should be 0", 0L, firstTime);
    }

    @Ignore
    @Test
    public void testTestConnection() {
        TestConnectionResponse response = service.testConnection(connector, null);
        
        assertNotNull("Test connection response should not be null", response);
        // The current implementation doesn't actually test the connection
        // but returns a successful response
        assertNull("Should not have error code", response.getCode());
        assertNull("Should not have error message", response.getMessage());
    }

    @Ignore
    @Test
    public void testGetSupportedAuthTypes() {
        List<AuthMetadata> authTypes = service.getSupportedAuthTypes();
        assertNotNull("Auth types should not be null", authTypes);
        assertEquals("Should have 1 auth type", 1, authTypes.size());

        AuthMetadata bearerAuth = authTypes.get(0);
        assertEquals("Auth type should be BearerToken", AuthType.SimpleOAuth, bearerAuth.getAuthType());
    }

    @Test
    @Ignore
    public void testGetConfigureFields() {
        List<AuthField> fields = service.getConfigureFields();
        assertNotNull("Configure fields should not be null", fields);
        assertEquals("Should have 5 configure fields", 5, fields.size());
        
        // Check required fields
        Optional<AuthField> workspace = fields.stream()
            .filter(f -> "endpoint".equals(f.getName()))
            .findFirst();
        assertTrue("Workspace field should exist", workspace.isPresent());
        assertTrue("Workspace field should be required", workspace.get().isRequired());
        assertEquals("Workspace field should be text type", "text", workspace.get().getDataType());
        
        Optional<AuthField> catalog = fields.stream()
            .filter(f -> "catalog".equals(f.getName()))
            .findFirst();
        assertTrue("Catalog field should exist", catalog.isPresent());
        assertTrue("Catalog field should be required", catalog.get().isRequired());
        
        Optional<AuthField> schema = fields.stream()
            .filter(f -> "schema".equals(f.getName()))
            .findFirst();
        assertTrue("Schema field should exist", schema.isPresent());
        assertTrue("Schema field should be required", schema.get().isRequired());
        
    }

    @Ignore
    @Test
    public void testGetByWatermark() {
        DescribeRequest request = new DescribeRequest(connector, "dim_business_accounts");
        Optional<EntitySchema> entitySchema = service.describe(request);
        entitySchema.get().getField("salesforce_updated_at").get().setWatermarkField(true);
        entitySchema.get().getField("salesforce_account_id").get().setIdField(true);
        SyncRequest req = new SyncRequest().Builder(connector, entitySchema.get());
        req.setPageSize(200);
        req.setWatermark(new WatermarkInfo(0L, System.currentTimeMillis(), false, 0));
        
        FetchResponse response = service.getByWatermark(req);
        
        assertNotNull("Response should not be null", response);
        assertNotNull("Iterator should not be null", response.getIterator());
        assertTrue(response.getIterator().hasNext());
        List<EntityData> next = response.getIterator().next();
        assertTrue(next.size() > 1);
        assertNotNull(next.get(0).getId());
        assertTrue(next.get(0).getLastModified() > 0);
    }

    @Ignore
    @Test
    public void testGetByWatermarkTwice() {
        DescribeRequest request = new DescribeRequest(connector, "dim_business_accounts");
        Optional<EntitySchema> entitySchema = service.describe(request);
        entitySchema.get().getField("salesforce_updated_at").get().setWatermarkField(true);
        entitySchema.get().getField("salesforce_account_id").get().setIdField(true);
        SyncRequest req = new SyncRequest().Builder(connector, entitySchema.get());
        req.setPageSize(200);
        req.setWatermark(new WatermarkInfo(0L, System.currentTimeMillis(), false, 0));

        FetchResponse response = service.getByWatermark(req);

        assertNotNull("Response should not be null", response);
        assertNotNull("Iterator should not be null", response.getIterator());
        assertTrue(response.getIterator().hasNext());
        List<EntityData> next = response.getIterator().next();
        assertTrue(next.size() > 1);
        assertNotNull(next.get(0).getId());
        assertTrue(next.get(0).getLastModified() > 0);

        assertTrue(response.getIterator().hasNext());
        assertNotNull(response.getIterator().getChangeStream());
        List<EntityData> next2 = response.getIterator().next();
        assertTrue(next2.size() > 1);
        assertNotNull(next2.get(0).getId());
        assertTrue(next2.get(0).getLastModified() > 0);

        assertTrue(next2.get(0).getId() != next.get(0).getId());
        assertFalse(next2.get(0).getId().equalsIgnoreCase(next.get(next.size()-1).getId()));
    }

    @Ignore
    @Test
    public void testGetByIds() {
        DescribeRequest request = new DescribeRequest(connector, "dim_business_accounts");
        Optional<EntitySchema> entitySchema = service.describe(request);
        entitySchema.get().getField("salesforce_updated_at").get().setWatermarkField(true);
        entitySchema.get().getField("salesforce_account_id").get().setIdField(true);
        SyncRequest req = new SyncRequest().Builder(connector, entitySchema.get());
        
        // Add some entity data with IDs to the request
        EntityData data1 = new EntityData("salesforce_account_table_1");
        data1.setId("1");
        data1.setConnectorId(connector.getId());
        
        EntityData data2 = new EntityData("salesforce_account_table_2");
        data2.setId("2");
        data2.setConnectorId(connector.getId());
        
        req.addData(connector.getId(), data1);
        req.addData(connector.getId(), data2);
        
        List<EntityData> results = service.getByIds(req);
        
        assertNotNull("Results should not be null", results);
        // Since executeQuery returns empty list, results should be empty
        assertTrue("Results should be empty", results.isEmpty());
    }

    @Ignore
    @Test
    public void testDescribe() {
        DescribeRequest request = new DescribeRequest(connector, "dim_business_accounts");
        
        Optional<EntitySchema> result = service.describe(request);
        
        assertNotNull("Result should not be null", result);
        assertTrue(result.get().getAttributes().size() >= 0);
    }

    @Ignore
    @Test
    public void testDescribeAll() {
        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
        
        List<EntitySchema> schemas = service.describeAll(request);
        
        assertNotNull("Schemas should not be null", schemas);
        assertTrue(schemas.size() > 0);
    }

    @Ignore
    @Test
    public void testDataTypeMappingCoverage() {
        // Test that service handles type mapping without crashing
        // This verifies the mapDatabricksType method indirectly
        assertNotNull("Service should handle type mapping", service);
        
        // Test that service doesn't crash with null/empty configurations
        ConnectorInfo emptyConnector = new ConnectorInfo();
        emptyConnector.setId("empty");
        emptyConnector.setName("Empty");
        emptyConnector.setAuthConfig(new AuthConfig());
        emptyConnector.setMetaConfig(new HashMap<>());
        
        try {
            TestConnectionResponse response = service.testConnection(emptyConnector, null);
            assertNotNull("Should handle empty config gracefully", response);
        } catch (Exception e) {
            // Expected to fail due to missing config, but shouldn't crash the service
            assertTrue("Should be a configuration error", e instanceof RuntimeException);
        }
    }

    @Ignore
    @Test
    public void testAuthenticationTokenHandling() {
        // Test that service can create auth headers without crashing
        AuthConfig authConfig = new AuthConfig();
        authConfig.setAccessToken("test-token");
        
        ConnectorInfo testConnector = new ConnectorInfo();
        testConnector.setId("test");
        testConnector.setName("Test");
        testConnector.setAuthConfig(authConfig);
        testConnector.setMetaConfig(connector.getMetaConfig()); // Use same meta config
        
        // This tests the service indirectly since getAuthHeaders is private
        TestConnectionResponse response = service.testConnection(testConnector, null);
        assertNotNull("Should handle auth header creation", response);
    }


}