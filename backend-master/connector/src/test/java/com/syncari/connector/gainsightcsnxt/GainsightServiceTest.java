package com.syncari.connector.gainsightcsnxt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.syncari.connector.AbstractConnectorTest;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.DataServiceTest;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@AutoConfigureDataMongo
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
@TestPropertySource("classpath:test_application.properties")
public class GainsightServiceTest extends AbstractConnectorTest implements DataServiceTest {

    @Autowired
    GainsightService service;

    private ConnectorInfo connector;

    @Before
    public void before() throws IOException {
        if (connector == null) {
            connector = createConnector();
        }
    }

    private ConnectorInfo createConnector() {
        ConnectorInfo conn = new ConnectorInfo();
        conn.setId("123");
        conn.setName("gainsightcs");
        conn.setEndpoint("https://sb-syncari.gainsightcloud.com");
        AuthConfig authConfig = new AuthConfig();
        authConfig.setAccessToken("fe3db5e4-8c07-4d7a-a0c8-db4e05d07cd5");
        conn.setAuthConfig(authConfig);
        return conn;
    }

    @Override
    public ConnectorInfo getConnector() {
        if (connector == null) connector = createConnector();
        return connector;
    }

    @Override
    public MetadataService getMetadataService() { return service; }
    @Override
    public AuthenticationService getAuthenticationService() { return service; }
    @Override
    public CommonDataService getDataService() { return service; }
    @Override
    public String getDescribeObject() {
        return "company";
    }

    @Override
    public List<String> skipPickListVerificationObjects() {
        return List.of("gs_opportunity", "gs_opportunity_stage");
    }

    @Override
    public List<String> skipPickListVerificationAttributes() {
        return List.of("CompanyType","LeadStatus");
    }

    @Override
    public List<String> skipWatermarkFieldVerificationObjects() {
        return List.of("email_logs", "survey_text_analytics", "user_shared_detail");
    }

    @Override
    public List<String> skipIdFieldVerificationObjects() {
        return List.of("email_logs");
    }
    
    @Override
    @Test
    public void testConnectionTest() {
        retryWithBackoff(() -> {
            verifyTestConnection();
        });
    }

    @Override
    @Test
    public void describeAllTest() {
        describeAll(null);
    }

    @Override
    @Test
    public void describeTest() {
        describe(null, null);
        describe("company", null);
        describe("gsuser", null);
        describe("person", null);
        describe("company_person", null);
        describe("custom_object__gc", null);
    }

    @Override
    @Test
    public void getByWatermarkSinceEpoch() {
        verifyGetByWatermarkSinceEpoch("company");
        verifyGetByWatermarkSinceEpoch("gsuser");
        verifyGetByWatermarkSinceEpoch("person");
        verifyGetByWatermarkSinceEpoch("company_person");
    }

    @Override
    @Test
    public void getByWatermarkRecent() {
        verifyGetByWatermarkRecent("company");
        verifyGetByWatermarkRecent("gsuser");
        verifyGetByWatermarkRecent("person");
    }

    @Override
    @Test
    public void getByWatermarkWithLimit() {
        retryWithBackoff(() -> {
            verifyGetByWatermarkWithLimit("company", 2);
            verifyGetByWatermarkWithLimit("gsuser", 2);
            verifyGetByWatermarkWithLimit("person", 2);
        });
    }

    @Override
    @Test
    public void getByWatermarkResultsOrdered() {
        retryWithBackoff(() -> {
            verifyGetByWatermarkResultsOrdered("company");
            verifyGetByWatermarkResultsOrdered("gsuser");
            verifyGetByWatermarkResultsOrdered("person");
        });
    }

    @Override
    @Test
    public void getByIds() {
        retryWithBackoff(() -> {
            verifyGetByIds("company");
            verifyGetByIds("gsuser");
            verifyGetByIds("person");
            verifyGetByIds("gs_opportunity", 1);
        });
        
    }

    @Override
    public void getDeletedByWatermark() {
        // Not supported.
    }

    @Override
    @Test
    public void createTest() {
        String utStr = "ut-create-" + System.currentTimeMillis();
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("Name", utStr + "i");
            data.add(edMap);
        }
        verifyCreateTestWithValues(utStr, "company", data);
    }

    @Override
    @Test
    public void updateTest() {
        String utStr = "ut-update-" + System.currentTimeMillis();
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("Name", utStr + "i");
            edMap.put("BillingAddress", "BAddress_" + i);
            data.add(edMap);
        }
        verifyUpdateTestWithValues(utStr, "company", data, "BillingAddress");
    }

    @Override
    public void deleteTest() {
        String utStr = "ut-delete-" + System.currentTimeMillis();
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("Name", utStr + "i");
            data.add(edMap);
        }
        verifyDeleteTestWithValues(utStr, "company", data);
    }

    @Override
    public void batchCreateTest() {
        // covered by createTest
    }

    @Override
    public void batchUpdateTest() {
        // covered by updateTest
    }

    @Override
    public void batchDeleteTest() {
        // covered by deleteTest
    }

    @Override
    @Test
    public void createCustomObjectTest() {
        String utStr = "ut-customObject-" + System.currentTimeMillis();
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("Custom_Field__gc", utStr + "i");
            data.add(edMap);
        }
        verifyCreateTestWithValues(utStr, "custom_object__gc", data);
    }

    @Override
    @Test
    public void updateCustomObjectTest() {
        String utStr = "ut-customObject-" + System.currentTimeMillis();
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("Custom_Field__gc", utStr + "i");
            edMap.put("Custom_Lookup__gc", "1P01ML8DV8K1EGJD08UDL9N55FB1XLCSSS3S");
            data.add(edMap);
        }
        verifyUpdateTestWithValues(utStr, "custom_object__gc", data, "Custom_Field__gc");
    }

    @Override
    public void deleteCustomObjectTest() {
        // as part of create/update tests
    }

    @Override
    @Test
    public void mixedBatchCreateFailuresTest() {
        String utStr = "ut-create-mixed-" + System.currentTimeMillis();
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("Name", utStr + "i");
            if (i == 3) edMap.put("Stage", "xyz");
            data.add(edMap);
        }
        verifyMixedBatchCreateTestFailures("company", data, (Result result) -> verifyBatchFailureResult(result));
    }

    @Override
    @Test
    public void mixedBatchUpdateFailuresTest() {
        String utStr = "ut-update-mixed-" + System.currentTimeMillis();
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("Name", utStr + "i");
            data.add(edMap);
        }
        verifyMixedBatchUpdateTestFailures("company", data, "Stage", "xyz", (Result result) -> verifyBatchFailureResult(result));
    }

    public void verifyBatchFailureResult(Result result) {
        assertTrue(result.getErrors().get(0).contains(
            "\"errorCode\":\"GSOBJ_1012\",\"errorDesc\":\"Invalid/Inactive value provided for picklist. (Stage = xyz).\""));
        assertTrue(result.getErrors().get(0).contains(
            "{\"errorMessage\":\"Invalid/Inactive value provided for picklist.\",\"errorCode\":\"GSOBJ_1012\"," +
            "\"fieldName\":\"Stage\",\"invalidValue\":[\"xyz\"]}"));
    }

    @Override
    public void mixedBatchDeleteFailuresTest() {
        // Not applicable.
    }

    @Test
    public void offsetPaginationTest() {
        Optional<EntitySchema> entitySchema = describe("gs_opportunity", null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0).setResync(true);
        syncRequest.setWatermark(watermark);
        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);

        while(byWatermark.getIterator().hasNext()) {
            List<EntityData> entities = byWatermark.getIterator().next();
            assertNotNull(entities);
        }
        assertFalse(byWatermark.getIterator().hasNext());
        assertEquals(0, byWatermark.getIterator().getLastOffset());


        // Test with limited records in the page. this should still stop and the end offset should be still 0.
        syncRequest.setPageSize(5);
        syncRequest.setWatermark(watermark);
        byWatermark = getDataService().getByWatermark(syncRequest);

        while(byWatermark.getIterator().hasNext()) {
            List<EntityData> entities = byWatermark.getIterator().next();
        }
        assertFalse(byWatermark.getIterator().hasNext());
        assertEquals(0, byWatermark.getIterator().getLastOffset());
    }

    @Override
    @Test
    public void allDataTypesTest() {
        Optional<EntitySchema> entitySchema = describe("gsuser", null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        watermark.setLimit(1);
        syncRequest.setWatermark(watermark);
        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
        assertTrue (byWatermark.getIterator().hasNext());
        List<EntityData> users = byWatermark.getIterator().next();
        assertNotNull(users);
        assertTrue(users.size() == 1);
        assertTrue(users.get(0).getId() instanceof String);
        assertTrue(users.get(0).getValue("Gsid") instanceof String);
        assertTrue(users.get(0).getValue("SFDCUserName") instanceof String);
        assertTrue(users.get(0).getValue("IsActiveUser") instanceof Boolean);
        assertTrue(users.get(0).getValue("FirstName") instanceof String);
        assertTrue(users.get(0).getValue("CreatedDate") instanceof String);
        assertTrue(users.get(0).getValue("LicenseType") instanceof String);
        assertTrue(users.get(0).getValue("Email") instanceof String);

        entitySchema = describe("company", null);
        syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        watermark.setLimit(1);
        syncRequest.setWatermark(watermark);
        byWatermark = getDataService().getByWatermark(syncRequest);
        assertTrue (byWatermark.getIterator().hasNext());
        List<EntityData> account = byWatermark.getIterator().next();
        assertNotNull(account);
        assertTrue(account.size() == 1);
        assertTrue(account.get(0).getId() instanceof String);
        assertTrue(account.get(0).getValue("Gsid") instanceof String);
        assertTrue(account.get(0).getValue("Name") instanceof String);
        //assertTrue(account.get(0).getValue("IsActiveUser") instanceof Boolean);
        //assertTrue(account.get(0).getValue("SfdcAccountId") instanceof String);
        assertTrue(account.get(0).getValue("CreatedDate") instanceof String);
        assertTrue(account.get(0).getValue("CreatedBy") instanceof String);
        //assertTrue(account.get(0).getValue("Email") instanceof String);

        for (AttributeSchema attr : entitySchema.get().getAttributes()) {
            if(attr.isWatermarkField()) {
                assertFalse(attr.isNillable());
                assertFalse(attr.isUpdateable());
                assertTrue(attr.isSystem());
            }
            if(attr.isIdField()) {
                assertFalse(attr.isNillable());
                assertFalse(attr.isUpdateable());
                assertTrue(attr.isUnique());
                assertTrue(attr.isSystem());
            }
        }

    }

    @Override
    @Test
    public void referencesTest() {
        String utStr = "ut-referencesTest-" + System.currentTimeMillis();
        List<EntityData> data = new ArrayList<>();
        Map<String, Object> edMap = new HashMap<>();
        edMap.put("Name", utStr + "i");
        edMap.put("Custom_Lookup_Field__gc", "1P01ML8DV8K1EGJD08UDL9N55FB1XLCSSS3S");
        edMap.put("Csm", "1P01ML8DV8K1EGJD08UDL9N55FB1XLCSSS3S");
        EntityData ed = new EntityData("company").withValues(edMap);
        data.add(ed.setSyncariEntityId(UUID.randomUUID().toString()));

        List<String> ids = new ArrayList<>();
        SyncRequest request = getSyncRequest("company");
        try {
            request.setData(Map.of(getConnector().getId(), data));
            SyncResponse response = getDataService().create(request);
            assertTrue(response.isSuccess());
            ids.add(response.getResults().get(0).getId());

            Optional<EntitySchema> entitySchema = describe("company", null);
            SyncRequest getByIdRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
            getByIdRequest.addData(getConnector().getId(), ed.setId(ids.get(0)));
            data = getDataService().getByIds(getByIdRequest);
            assertNotNull(data);
            assertEquals("1P01ML8DV8K1EGJD08UDL9N55FB1XLCSSS3S", data.get(0).getValueAsString("Custom_Lookup_Field__gc"));
            assertEquals("1P01ML8DV8K1EGJD08UDL9N55FB1XLCSSS3S", data.get(0).getValueAsString("Csm"));
        } finally {
            deleteRecords(request, ids);
        }
    }

    @Override
    public void rateLimitTest() {
        // NYI
    }

    @Test
    public void cudUserTest() {
        ConnectorInfo connector = createConnector();
        DescribeRequest describeRequest = new DescribeRequest(connector, "gsuser");
        Optional<EntitySchema> entitySchema = service.describe(describeRequest);
        assertTrue(entitySchema.isPresent());
        EntityData entityData = new EntityData("gsuser");
        int n = 22;
        entityData.addValue("Name", "Test Syncari - " + n );
        entityData.addValue("Email", "test" + n + "@test.com");
        entityData.addValue("SFDCUserName", "test" + n + "@gs.com");
        entityData.addValue("CompanyId", "1P026279PK0X5QOT0IQ1GJWOLQBHI1X5O6I7");
        SyncRequest syncRequest = new SyncRequest();
        syncRequest.setEntitySchema(entitySchema.get());
        syncRequest.setConnector(connector);
        syncRequest.setData(Map.of(connector.getId(), List.of(entityData)));
        SyncResponse syncResponse = service.create(syncRequest);
        assertTrue(syncResponse.isSuccess());
        String id = syncResponse.getResults().get(0).getId();
        EntityData updateData = new EntityData("gsuser");
        updateData.setId(id);
        updateData.addValue("Name", "Test" + n + " Syncari Updated");
        syncRequest.setData(Map.of(connector.getId(), List.of(updateData)));
        try {
            syncResponse = service.update(syncRequest);
            assertTrue(syncResponse.isSuccess());
            updateData.addValue("UYTguyegduwyge", "Test" + n + " Syncari Updated");
            syncResponse = service.update(syncRequest);
            assertFalse(syncResponse.isSuccess());
            assertFalse(syncResponse.getErrors().isEmpty());
        } finally {
            service.delete(syncRequest);
        }
    }

}
