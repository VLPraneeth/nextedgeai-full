package com.syncari.connector.oracle;

import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Instant;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@AutoConfigureDataMongo
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
public class OracleSalesCrmServiceTest extends AbstractConnectorTest implements DataServiceTest {

    private static final String USERNAME = "scott@syncari.com";
    private static final String PASSWORD = System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME");

    private ConnectorInfo connector;

    @Autowired
    private OracleSalesCrmService oracleSalesCrmService;

    @Override
    public ConnectorInfo getConnector() {
        if (connector == null) connector = createConnector();
        return connector;
    }

    @Override
    public AuthenticationService getAuthenticationService() {
        return oracleSalesCrmService;
    }

    @Override
    public MetadataService getMetadataService() {
        return oracleSalesCrmService;
    }

    @Override
    public CommonDataService getDataService() {
        return oracleSalesCrmService;
    }

    @Override
    public String getDescribeObject() {
        return Constants.ACCOUNT.toLowerCase();
    }

    @Override
    @Test
    public void testConnectionTest() {
        verifyTestConnection();

        ConnectorInfo conn = createConnector();
        conn.getAuthConfig().setUserName("junk");
        TestConnectionResponse resp = getAuthenticationService().testConnection(conn, List.of());
        assertFalse(resp.isSuccess());
        assertTrue(resp.getMessage().startsWith("Authentication failed."));
        assertFalse(resp.getErrors().isEmpty());
        assertEquals(resp.getErrors().get(0), "401 Unauthorized");
    }

    @Override
    @Test
    public void describeAllTest() {
        describeAll(null);
    }

    // describe Test for Account, User, Activities,Opportunities, Notes
    @Override
    @Test
    public void describeTest() {
        describe("deals", null);
        describe("accounts", null);
        describe("leads", null);
        describe("contacts", null);
        describe("activities", null);
        describe("opportunities", null);
        describe("resourceUsers", null);
        describe("partners", null);
        describe("partnerContacts", null);
    }

    @Test
    public void describeReadOnlyAttributeTest(){
        String describeObject = "accounts";
        String key = getConnector().connectionHash() + "_" + describeObject;
        Optional<EntitySchema> response = getDescribeEntitySchema(describeObject);
        assertTrue("Failed describe for object " + describeObject, response.isPresent());
        assertEquals(describeObject, response.get().getApiName());
        response.get().getAttributes().stream().forEach(x -> x.setStatus(Status.ACTIVE));
        assertTrue(response.get().getField("AddressLine1").isPresent());
        assertTrue(response.get().getField("AddressLine1").get().isUpdateable());
    }

    @Test
    public void describeReferenceAttributeTest(){
        String describeObject = "contacts";
        Optional<EntitySchema> response = getDescribeEntitySchema(describeObject);
        assertTrue("Failed describe for object " + describeObject, response.isPresent());
        assertEquals(describeObject, response.get().getApiName());
        response.get().getAttributes().stream().forEach(x -> x.setStatus(Status.ACTIVE));
        assertTrue(response.get().getField("AccountPartyNumber").isPresent());
        assertTrue(response.get().getField("AccountPartyNumber").get().isReference());
        assertEquals("accounts",response.get().getField("AccountPartyNumber").get().getReferenceTo());
        assertEquals("PartyNumber",response.get().getField("AccountPartyNumber").get().getReferenceTargetField());

        describeObject = "partnerContacts";
        response = getDescribeEntitySchema(describeObject);
        assertTrue("Failed describe for object " + describeObject, response.isPresent());
        assertEquals(describeObject, response.get().getApiName());
        response.get().getAttributes().stream().forEach(x -> x.setStatus(Status.ACTIVE));
        assertTrue(response.get().getField("PartnerCompanyNumber").isPresent());
        assertTrue(response.get().getField("PartnerCompanyNumber").get().isReference());
        assertEquals("partners",response.get().getField("PartnerCompanyNumber").get().getReferenceTo());
        assertEquals("CompanyNumber",response.get().getField("PartnerCompanyNumber").get().getReferenceTargetField());

        describeObject = "leads";
        response = getDescribeEntitySchema(describeObject);
        assertTrue("Failed describe for object " + describeObject, response.isPresent());
        assertEquals(describeObject, response.get().getApiName());
        response.get().getAttributes().stream().forEach(x -> x.setStatus(Status.ACTIVE));
        assertTrue(response.get().getField("PrimaryContactId").isPresent());
        assertTrue(response.get().getField("PrimaryContactId").get().isReference());
        assertEquals("partnerContacts",response.get().getField("PrimaryContactId").get().getReferenceTo());
        assertEquals("PartyNumber",response.get().getField("PrimaryContactId").get().getReferenceTargetField());
        assertTrue(response.get().getField("PartnerCompanyNumber").isPresent());
        assertTrue(response.get().getField("PartnerCompanyNumber").get().isReference());
        assertEquals("partners",response.get().getField("PartnerCompanyNumber").get().getReferenceTo());
        assertEquals("CompanyNumber",response.get().getField("PartnerCompanyNumber").get().getReferenceTargetField());

    }

    @Override
    @Test
    public void getByWatermarkSinceEpoch() {
        verifyGetByWatermarkSinceEpoch("accounts");
        verifyGetByWatermarkSinceEpoch("contacts");
    }

    @Test
    public void getByWatermarkValidEndpoints() {
        oracleSalesCrmService.SUPPORTED_ENTITIES.forEach(entity -> {
            verifyGetByWatermarkValidEndpoints(entity);
        });
    }

    @Override
    @Test
    public void getByWatermarkRecent() {
        verifyGetByWatermarkRecent("accounts");
        verifyGetByWatermarkRecent("contacts");
    }

    @Override
    @Test
    public void getByWatermarkWithLimit() {
        verifyGetByWatermarkWithLimit("accounts",2);
        verifyGetByWatermarkWithLimit("contacts",2);
    }

    @Test
    public void getByWatermarkPagination() {
        verifyGetByWatermarPagination("accounts",1, 3);
        verifyGetByWatermarPagination("contacts",1, 3);
    }



    @Override
    @Test
    public void getByWatermarkResultsOrdered() {
        verifyGetByWatermarkResultsOrdered("accounts");
        verifyGetByWatermarkResultsOrdered("contacts");
    }

    @Override
    @Test
    public void getByIds() {
        verifyGetByIds("accounts",2);
    }

    @Override
    public void getDeletedByWatermark() {

    }

    @Override
    @Test
    public void createTest() {
        String utStr = "ut-create-" + System.currentTimeMillis();
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("FirstName", utStr + i);
            edMap.put("LastName", utStr + i);
            data.add(edMap);
        }
        verifyCreateTestWithValues(utStr, "contacts", data);
    }

    @Test
    public void cudSpecialCharsTest() {
        String utStr = "ut-cud-contact" + System.currentTimeMillis();
        List<Map<String, Object>> edDataM = new ArrayList<>();
        for (int i = 0; i < 1; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("FirstName", "Region Sjælland" + i);
            edMap.put("LastName", utStr + i);
            edDataM.add(edMap);
        }

        List<EntityData> edData = new ArrayList<>();
        for (int i = 0; i < edDataM.size(); i++) {
            EntityData ed = new EntityData("contacts").withValues(edDataM.get(i));
            edData.add(ed.setSyncariEntityId(UUID.randomUUID().toString()));
        }

        List<String> ids = new ArrayList<>();
        SyncRequest request = getSyncRequest("contacts");
        try {
            request.setPageSize(2);
            request.setData(Map.of(getConnector().getId(), edData));
            SyncResponse response = getDataService().create(request);
            assertTrue(response.isSuccess());
            String contactId = response.getResults().get(0).getId();
            ids.add(contactId);
            edData.get(0).setId(contactId);
            edData.get(0).addValue("LastName", "København V");

            request.setData(Map.of(getConnector().getId(), edData));
            response = getDataService().update(request);
            
            List<EntityData>entityDataList =  getDataService().getByIds(request);
            assertEquals(entityDataList.size(), ids.size());
            entityDataList.forEach(ed -> {
                assertEquals("Region Sjælland0", ed.getValueAsString("FirstName"));
                assertEquals("København V", ed.getValueAsString("LastName"));
            });
        } finally {
            deleteRecords(request, ids);
        }

    }

    @Test
    public void createAccountTest() {
        String utStr = "ut-create-" + System.currentTimeMillis();
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < 1; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("AddressLine1", utStr + i);
            edMap.put("OrganizationName", "test");
            edMap.put("Type", "ZCA_PROSPECT");
            data.add(edMap);
        }
        String entityName = "accounts";
        List<EntityData> edData = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            EntityData ed = new EntityData(entityName).withValues(data.get(i));
            edData.add(ed.setSyncariEntityId(UUID.randomUUID().toString()));
        }

        List<String> ids = new ArrayList<>();
        SyncRequest request = getSyncRequest(entityName);
        request.setPageSize(2);
        request.setData(Map.of(getConnector().getId(), edData));
        SyncResponse response = getDataService().create(request);
        assertFalse(response.isSuccess());
        assertNotNull(response.getErrors());
        assertTrue(response.getResults().get(0).getErrors().get(0).contains("Attribute Country in LocationEO__DefCustomizer__ is required"));
    }

    @Test
    @Ignore
    public void createDealsTest() {
        String utStr = "ut-create-deal-contact" + System.currentTimeMillis();
        List<Map<String, Object>> edDataM = new ArrayList<>();
        for (int i = 0; i < 1; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("FirstName", utStr + i);
            edMap.put("LastName", utStr + i);
            edDataM.add(edMap);
        }

        List<EntityData> edData = new ArrayList<>();
        for (int i = 0; i < edDataM.size(); i++) {
            EntityData ed = new EntityData("contacts").withValues(edDataM.get(i));
            edData.add(ed.setSyncariEntityId(UUID.randomUUID().toString()));
        }

        List<String> ids = new ArrayList<>();
        SyncRequest request = getSyncRequest("contacts");
        request.setPageSize(2);
        request.setData(Map.of(getConnector().getId(), edData));
        SyncResponse response = getDataService().create(request);
        String contactId = response.getResults().get(0).getId();
        ids.add(contactId);

        try {
            utStr = "ut-create-deal" + System.currentTimeMillis();
            List<Map<String, Object>> data = new ArrayList<>();
            for (int i = 0; i < 1; i++) {
                Map<String, Object> edMap = new HashMap<>();
                edMap.put("CloseDate", "2020-09-30");
                edMap.put("ContactId", contactId);
                edMap.put("DealSize", 100);
                edMap.put("DealType", "ORA_EXISTING");
                data.add(edMap);
            }
            verifyCreateTestWithValues(utStr, "deals", data);
        } finally {
            deleteRecords(request, ids);
        }
    }

    @Override
    @Test
    public void updateTest() {
        String utStr = "ut-update-" + System.currentTimeMillis();
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < 1; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("FirstName", utStr + i);
            edMap.put("LastName", "København V" + i);
            data.add(edMap);
        }
        verifyUpdateTestWithValues(utStr, "contacts", data, "LastName");
    }

    @Override
    @Test
    public void deleteTest() {
        String utStr = "ut-delete-" + System.currentTimeMillis();
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < 1; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("FirstName", utStr + i);
            edMap.put("LastName", utStr + i);
            data.add(edMap);
        }
        verifyDeleteTestWithValues(utStr, "contacts", data);
    }

    @Override
    @Test
    public void batchCreateTest() {
        String utStr = "ut-create-" + System.currentTimeMillis();
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("FirstName", utStr + i);
            edMap.put("LastName", utStr + i);
            data.add(edMap);
        }
        verifyCreateTestWithValues(utStr, "contacts", data);
    }

    @Override
    @Test
    public void batchUpdateTest() {
        String utStr = "ut-update-" + System.currentTimeMillis();
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("FirstName", utStr + i);
            edMap.put("LastName", utStr + i);
            data.add(edMap);
        }
        verifyUpdateTestWithValues(utStr, "contacts", data, "LastName");
    }

    @Override
    @Test
    public void batchDeleteTest() {
        String utStr = "ut-delete-" + System.currentTimeMillis();
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("FirstName", utStr + i);
            edMap.put("LastName", utStr + i);
            data.add(edMap);
        }
        verifyDeleteTestWithValues(utStr, "contacts", data);
    }

    @Override
    public void createCustomObjectTest() {
        // Not applicable.
    }

    @Override
    public void updateCustomObjectTest() {
        //Not applicable
    }

    @Override
    public void deleteCustomObjectTest() {
        //Not applicable
    }

    @Override
    public void mixedBatchCreateFailuresTest() {
        //Not applicable
    }

    @Override
    public void mixedBatchUpdateFailuresTest() {
        //Not applicable
    }

    @Override
    public void mixedBatchDeleteFailuresTest() {
        //Not applicable
    }

    @Test
    public void offsetPaginationTest() {
        Optional<EntitySchema> entitySchema = describe("accounts", null);
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
        String utStr = "allDataType-test-" + System.currentTimeMillis();
        Map<String, Object> edMap = new HashMap<>();
        edMap.put("FirstName", utStr);
        edMap.put("LastName", utStr+"_lastname");
        edMap.put("NamedFlag", false);
        EntityData ed = new EntityData("contacts").withValues(edMap);
        List<EntityData> data = new ArrayList<>();
        data.add(ed.setSyncariEntityId(UUID.randomUUID().toString()));

        SyncRequest request = getSyncRequest("contacts");
        request.setPageSize(2);
        List<String> ids = new ArrayList<>();
        try {
            request.setData(Map.of(getConnector().getId(), data));
            SyncResponse response = getDataService().create(request);
            assertTrue(response.isSuccess());
            assertEquals(data.size(), response.getResults().size());
            response.getResults().forEach(x -> {
                assertNotNull(x.getId());
                assertNotNull(x.getSyncariId());
                ids.add(x.getId());
            });
            assertEquals(data.size(), ids.size());
            ed.setId(ids.get(0));
            List<EntityData>entityDataList =  getDataService().getByIds(request);
            assertEquals(entityDataList.size(), ids.size());
            entityDataList.forEach(edData -> {
                assertFalse((Boolean)edData.getValue("NamedFlag")); // Boolean
                assertTrue(((String)edData.getValue("FirstName")).contains("allDataType-test-")); // String
                assertTrue(edData.getValue("CreationDate") instanceof String); // date to string
            });
        } finally {
            deleteRecords(request, ids);
        }
    }

    @Override
    public void referencesTest() {
        //Not applicable
    }

    @Override
    public void rateLimitTest() {
        // Mostly making batch calls, don't think it is required.
    }

    private ConnectorInfo createConnector(){
        ConnectorInfo connector = new ConnectorInfo();
        connector.setId("1234");
        connector.setName("oraclecxid");
        connector.setEndpoint("https://fa-eusl-saasfaprod1.fa.ocs.oraclecloud.com");
        AuthConfig authConfig = new AuthConfig(USERNAME, PASSWORD, "");
        authConfig.setAccessToken(PASSWORD);
        connector.setAuthConfig(authConfig);
        return connector;
    }

    @Test
    public void testRefetchSchemaWhenFieldDeletionsDetected() {
        DescribeRequest mockRequest = mock(DescribeRequest.class);
        ConnectorInfo mockConnectorInfo = mock(ConnectorInfo.class);
        EntitySchema existingSchema = new EntitySchema("TestEntity");
        existingSchema.addField(new AttributeSchema("id", "string"));
        existingSchema.addField(new AttributeSchema("phone", "string"));

        EntitySchema fetchedSchema = new EntitySchema("TestEntity");
        fetchedSchema.addField(new AttributeSchema("id", "string"));

        when(mockRequest.getConnector()).thenReturn(mockConnectorInfo);
        when(mockRequest.getEntity()).thenReturn("TestEntity");
        when(mockRequest.getExistingSchema()).thenReturn(Optional.of(existingSchema));

        oracleSalesCrmService = spy(oracleSalesCrmService);
        doReturn(Optional.of(fetchedSchema)).doReturn(Optional.of(existingSchema))
                .when(oracleSalesCrmService).toEntitySchema("TestEntity", mockConnectorInfo);

        Optional<EntitySchema> result = oracleSalesCrmService.describe(mockRequest);

        assertTrue("Expected schema should be refetched", result.isPresent());
        assertTrue(result.get().hasField("phone"));
        verify(oracleSalesCrmService, times(2)).toEntitySchema("TestEntity", mockConnectorInfo);
    }
}
