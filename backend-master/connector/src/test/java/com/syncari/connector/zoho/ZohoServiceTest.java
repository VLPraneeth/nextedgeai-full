package com.syncari.connector.zoho;

import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.utils.Retry;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@RunWith(SpringRunner.class)
@AutoConfigureDataMongo
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
@TestPropertySource("classpath:test_application.properties")
public class ZohoServiceTest extends AbstractConnectorTest implements DataServiceTest {
    @Autowired
    ZohoService service;

    @Value("${zoho.service.url}")
    String zohoServiceURL;

    @Value("${zoho.account.url}")
    String zohoAccountURL;

    @Value("${zoho.client.id}")
    String zohoClientId;

    @Value("${zoho.client.secret}")
    String zohoClientSecret;

    @Value("${zoho.client.refreshToken}")
    String zohoRefreshToken;

    private static ConnectorInfo connector;

    @Rule
    public RetryRule retryRule = new RetryRule();

    @Before
    public void before() throws IOException {
        if (connector == null) {
            connector = createConnector();
            retryWithBackoffOnRunTimeException(() -> {
                connector.setAuthConfig(service.refreshToken(connector));
            });
        }
    }

    private ConnectorInfo createConnector() {
        ConnectorInfo connector = new ConnectorInfo();
        connector.setName("Zoho");
        AuthConfig authConfig = new AuthConfig();
        authConfig.setClientId(zohoClientId);
        authConfig.setClientSecret(zohoClientSecret);
        authConfig.setRefreshToken(zohoRefreshToken);
        authConfig.setEndpoint(zohoAccountURL);
        Map<String, Object> metaConfig = new HashMap<>();
        metaConfig.put(ZohoService.SERVICE_URL_AUTH_FIELD, zohoServiceURL);
        connector.setMetaConfig(metaConfig);
        connector.setAuthConfig(authConfig);
        connector.setId(UUID.randomUUID().toString());
        return connector;
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
        return "Accounts";
    }
    @Override
    public List<String> skipPickListVerificationObjects() {
        // Activities.Call_Status attribute does not have picklist values.
        return List.of("Activities", "Calls");
    }

    @Override
    public List<String> skipWatermarkFieldVerificationObjects() {
        return List.of("Quoted_Items", "Ordered_Items", "Purchase_Items", "Invoiced_Items"); 
    }

    @Override
    @Test
    public void testConnectionTest() {
        retryWithBackoff(() -> {
            verifyTestConnection();
        });

        ConnectorInfo connectorInfo = createConnector();
        connectorInfo.getAuthConfig().setAccessToken("");
        connectorInfo.getAuthConfig().setClientSecret("");
        TestConnectionResponse resp = getAuthenticationService().testConnection(connectorInfo, List.of());
        assertFalse(resp.isSuccess());
        assertTrue(resp.getMessage().startsWith("Authentication failed."));
        assertFalse(resp.getErrors().isEmpty());
        assertEquals(resp.getErrors().get(0), "401 ");
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
        describe("users", null);
        describe("Accounts", null);
        Optional<EntitySchema> contacts = describe("Contacts", null);
        assertFalse(contacts.get().getField("Last_Name").get().isNillable());
//        describe("CustomObjects", null);
//        var schemaOpt = describe("Products", null);
//        assertTrue(schemaOpt.isPresent());
//        assertTrue(schemaOpt.get().getField("Tax").isPresent());
//        assertFalse(schemaOpt.get().getField("Tax").get().isUpdateable());
    }

    @Test
    public void describeContacts() {
        retryWithBackoff(() -> {
            Optional<EntitySchema> response = describe("Contacts", null);
            Optional<AttributeSchema> modifiedTime = response.get().getAttributes().stream()
                .filter(x -> "Modified_Time".equalsIgnoreCase(x.getApiName())).findFirst();
            assertTrue(modifiedTime.isPresent());
            assertFalse(response.get().isReadOnly());
            assertTrue(modifiedTime.get().isSystem());
            assertTrue(modifiedTime.get().isWatermarkField());
            assertTrue(modifiedTime.get().isUpdatedAtField());
        });
    }

    @Test
    public void describeDeals() {
        retryWithBackoff(() -> {
            Optional<EntitySchema> response = describe("Deals", null);
            Optional<AttributeSchema> modifiedTime = response.get().getAttributes().stream()
                    .filter(x -> "Modified_Time".equalsIgnoreCase(x.getApiName())).findFirst();
            assertTrue(modifiedTime.isPresent());
            assertFalse(response.get().isReadOnly());
            assertTrue(modifiedTime.get().isSystem());
            assertTrue(modifiedTime.get().isWatermarkField());
            assertTrue(modifiedTime.get().isUpdatedAtField());
        });
    }

    @Test
    @Ignore
    public void describePurchaseOrders() {
        retryWithBackoff(() -> {
            Optional<EntitySchema> response = describe("Purchase_Orders", null);
            Optional<AttributeSchema> modifiedTime = response.get().getAttributes().stream()
                    .filter(x -> "Modified_Time".equalsIgnoreCase(x.getApiName())).findFirst();
            assertTrue(modifiedTime.isPresent());
            assertFalse(response.get().isReadOnly());
            assertTrue(modifiedTime.get().isSystem());
            assertTrue(modifiedTime.get().isWatermarkField());
            assertTrue(modifiedTime.get().isUpdatedAtField());
        });
    }

    @Override
    @Test
    public void getByWatermarkSinceEpoch() {
        retryWithBackoff(() -> {
            verifyGetByWatermarkSinceEpoch("Accounts");
//            verifyGetByWatermarkSinceEpoch("CustomObjects");
        });
    }

    @Override
    @Test
    public void getByWatermarkWithLimit() {
        retryWithBackoff(() -> {
            verifyGetByWatermarkWithLimit("Accounts", 2);
            verifyGetByWatermarkWithLimit("users", 2);
//            verifyGetByWatermarkWithLimit("CustomObjects", 2);
        });
    }

    @Override
    @Test
    public void getByWatermarkRecent() {
        retryWithBackoff(() -> {
            verifyGetByWatermarkRecent("Contacts");
//            verifyGetByWatermarkRecent("Invoiced_Items");
//            verifyGetByWatermarkRecent("CustomObjects");
        });
    }

    @Override
    @Test
    public void getByWatermarkResultsOrdered() {
        retryWithBackoff(() -> {
            verifyGetByWatermarkResultsOrdered("Activities");
//            verifyGetByWatermarkResultsOrdered("CustomObjects");
        });
    }

    @Override
    @Test
    public void getDeletedByWatermark() {
        // covered in lead crud
    }

    @Override
    @Test
    public void getByIds() {
        retryWithBackoff(() -> {
            verifyGetByIds("Leads");
//            verifyGetByIds("CustomObjects");
//            verifyGetByIds("Products");
        });
    }

    @Test
    public void applyPrune() {
        verifyPruneLogic("Leads");
    }

    @Override
    public void createTest() {
        // no-op covered by batchCreateTest.
    }

    @Override
    public void updateTest() {
        // no-op covered by batchUpdateTest.
    }

    @Test
    public void testCreate() {
        long startTime = Instant.now().toEpochMilli();
        List<EntityData> data = new ArrayList<>();
        EntityData entityData = new EntityData("Accounts");
        entityData.addValue("Owner", "4867003000000307001");
        entityData.addValue("Account_Name", "test account");
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        Date date = today.getTime();
        entityData.addValue("Handoff_Date", date);
        entityData.addValue("Handoff_Date_2", "Wed Nov 24 00:17:41 PST 2021");
        ZonedDateTime dateTime = ZonedDateTime.ofInstant(Instant.now(), java.time.ZoneOffset.UTC);
        entityData.addValue("Handoff_DateTime", dateTime);
        entityData.addValue("Handoff_DateTime_2", "Wed Nov 24 00:17:41 PST 2021");
        List<String> multiValues = Arrays.asList("One", "Two");
        entityData.addValue("Applications", multiValues);
        data.add(entityData);
        List<String> ids = new ArrayList<>();
        SyncRequest request = getSyncRequest("Accounts");
        boolean cleaned = false;
        try {
            Thread.sleep(2000);
            request.setData(Map.of(getConnector().getId(), data));
            SyncResponse response = getDataService().create(request);
            assertTrue(response.isSuccess());
            assertEquals(data.size(), response.getResults().size());
            response.getResults().forEach(x -> {
                assertNotNull(x.getId());
                ids.add(x.getId());
            });
            assertEquals(data.size(), ids.size());
            assertNotNull(ids.get(0));
            getById(ids.get(0));
            deleteRecords(request, ids);
            cleaned = true;
            // We want to test scenario where the second value is passed as WM.
            // We had weird issues with the Zoho Endpoint, where it expects a particular format for the `If-Modified-Since` header.
            long secondValue = startTime / 100000 * 100000;
            // Verify that we saw the just deleted records.
            verifyGetDeletedByWatermark("Accounts", secondValue, ids);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        } finally {
            // mostly no-op for this method.
            if (!cleaned) deleteRecords(request, ids);
        }
    }

    @Test
    public void testBatchCreate() {
        Random rand = new Random();
        int random = rand.nextInt(1000000);;
        long startTime = Instant.now().toEpochMilli();
        List<EntityData> data = new ArrayList<>();
        EntityData entityData = new EntityData("Accounts");
        entityData.addValue("Owner", "4867003000000307001");
        entityData.addValue("Account_Name", "test account");
        entityData.addValue("TestDuplicate", random);
        data.add(entityData);
        EntityData entityData1 = new EntityData("Accounts");
        entityData1.addValue("Owner", "4867003000000307001");
        entityData1.addValue("Account_Name", "test account");
        entityData1.addValue("TestDuplicate", random);
        data.add(entityData1);
        List<String> ids = new ArrayList<>();
        SyncRequest request = getSyncRequest("Accounts");
        boolean cleaned = false;
        try {
            Thread.sleep(2000);
            request.setData(Map.of(getConnector().getId(), data));
            SyncResponse response = getDataService().create(request);
            assertTrue(response.isSuccess());
            assertEquals(data.size(), response.getResults().size());
            response.getResults().forEach(x -> {
                if(x.isSuccess()) ids.add(x.getId());
            });
            assertEquals(2, ids.size());
            assertNotNull(ids.get(0));
            getById(ids.get(0));
            deleteRecords(request, ids);
            cleaned = true;
            // We want to test scenario where the second value is passed as WM.
            // We had weird issues with the Zoho Endpoint, where it expects a particular format for the `If-Modified-Since` header.
            long secondValue = startTime / 100000 * 100000;
            // Verify that we saw the just deleted records.
            verifyGetDeletedByWatermark("Accounts", secondValue, ids);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        } finally {
            // mostly no-op for this method.
            if (!cleaned) deleteRecords(request, ids);
        }
    }

    public void getById(String id) {
        Optional<EntitySchema> entitySchema = describe("Accounts", null);
        SyncRequest getByIdRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        EntityData entityData = new EntityData();
        entityData.setId(id);
        getByIdRequest.addData(getConnector().getId(), entityData);
        List<EntityData> data = getDataService().getByIds(getByIdRequest);
        assertNotNull(data);
        List<String> multiValues = (List<String>) data.get(0).getValue("Applications");
        assertNotNull(multiValues);
    }

    @Override
    @Test
    @Retry
    public void deleteTest() {
        long startTime = Instant.now().toEpochMilli();
        String utStr = "ut=deleteTest" + System.currentTimeMillis();
        List<EntityData> data = new ArrayList<>();
        data.add(createLeadEntity(utStr + "@syncari.com", utStr + "_First", utStr + "_Last"));
        List<String> ids = new ArrayList<>();
        SyncRequest request = getSyncRequest("Leads");
        boolean cleaned = false;
        try {
            Thread.sleep(2000);
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
            deleteRecords(request, ids);
            cleaned = true;
            // We want to test scenario where the second value is passed as WM. 
            // We had weird issues with the Zoho Endpoint, where it expects a particular format for the `If-Modified-Since` header.
            long secondValue = startTime / 100000 * 100000;
            // Verify that we saw the just deleted records.
            verifyGetDeletedByWatermark("Leads", secondValue, ids);
        } catch (InterruptedException e) {
            // nothing.
        } finally {
            // mostly no-op for this method.
            if (!cleaned) deleteRecords(request, ids);
        }
    }

    @Test
    public void getIfModifiedSince() {
        ZohoRestClient zrc = service.getClient();
        String formatted = zrc.getIfModifiedSince(1636479900000L);
        String formatted2 = zrc.getIfModifiedSince(1636479900200L);
        // ms is ignored.
        assertEquals(formatted, formatted2);
        long now = Instant.now().toEpochMilli();
        formatted = zrc.getIfModifiedSince(now);
        // seconds and ms is evaluated as same format.
        formatted2 = zrc.getIfModifiedSince(now / 1000 * 1000);
        assertEquals(formatted, formatted2);
    }

    @Override
    @Test
    public void batchCreateTest() {
        retryWithBackoff(() -> {
            String utStr = "ut-cust-create-" + System.currentTimeMillis();
            List<EntityData> data = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                data.add(createLeadEntity(utStr + i + "@syncari.com", utStr + "_First" + i, utStr + "_Last" + i));
            }
            verifyCreateTest(utStr, "Leads", data);
        });
    }

    @Override
    @Test
    public void batchUpdateTest() {
        retryWithBackoff(() -> {
            String utStr = "ut-update-" + System.currentTimeMillis();
            List<EntityData> data = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                data.add(createLeadEntity(utStr + i + "@syncari.com", utStr + "_First" + i, utStr + "_Last" + i));
            }
            verifyUpdateTest(utStr, "Leads", data, "Last_Name");
        });
    }

    @Override
    @Test
    public void batchDeleteTest() {
        retryWithBackoff(() -> {
            String utStr = "ut-delete-" + System.currentTimeMillis();
            List<EntityData> data = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                data.add(createLeadEntity(utStr + i + "@syncari.com", utStr + "_First" + i, utStr + "_Last" + i));
            }
            verifyDeleteTest(utStr, "Leads", data);
        });
    }

    @Override
    @Ignore
    @Test
    public void createCustomObjectTest() {
        retryWithBackoff(() -> {
            String utStr = "ut-cust-create-" + System.currentTimeMillis();
            List<EntityData> data = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                data.add(createCustomObjectEntity(utStr + "@syncari.com", utStr));
            }
            verifyCreateTest(utStr, "CustomObjects", data);
        });
    }

    @Override
    @Ignore
    @Test
    public void updateCustomObjectTest() {
        retryWithBackoff(() -> {
            String utStr = "ut-cust-update-" + System.currentTimeMillis();
            List<EntityData> data = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                data.add(createCustomObjectEntity(utStr + "@syncari.com", utStr));
            }
            verifyUpdateTest(utStr, "CustomObjects", data, "Name");
        });
    }

    @Override
    @Ignore
    @Test
    public void deleteCustomObjectTest() {
        retryWithBackoff(() -> {
            String utStr = "ut-cust-delete-" + System.currentTimeMillis();
            List<EntityData> data = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                data.add(createCustomObjectEntity(utStr + "@syncari.com", utStr));
            }
            verifyDeleteTest(utStr, "CustomObjects", data);
        });
    }

    @Test
    @Ignore
    public void describeSpecial() {
        DescribeRequest request = new DescribeRequest(getConnector(), "Invoiced_Items");
        Optional<EntitySchema> enitySchema = getMetadataService().describe(request);
        assertFalse(enitySchema.get().isReadOnly());
        assertTrue(enitySchema.get().hasWatermarkField());
        assertTrue(enitySchema.get().getWatermarkAttr().get().getApiName().equalsIgnoreCase("Modified_Time"));
    }

    @Override
    public void mixedBatchCreateFailuresTest() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void mixedBatchUpdateFailuresTest() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void mixedBatchDeleteFailuresTest() {
        // TODO Auto-generated method stub
        
    }

    @Override
    @Test
    public void allDataTypesTest() {
        DescribeRequest request = new DescribeRequest(getConnector(), "Leads");
        Optional<EntitySchema> enittySchema = getMetadataService().describe(request);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), enittySchema.get()).setEntitySchemaWithMappedFields(enittySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0);
        watermark.setLimit(2);
        syncRequest.setWatermark(watermark);
        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
        String id = "";
        while (byWatermark.getIterator().hasNext()) {
            List<EntityData> data = byWatermark.getIterator().next();
            assertFalse(data.isEmpty());
            // make sure limit is applied.
            assertTrue(data.size() == 2);
            verifyLeadAllDataTypes(data.get(0));
            id = data.get(0).getId();
        }
        syncRequest = new SyncRequest().Builder(getConnector(), enittySchema.get()).setEntitySchemaWithMappedFields(enittySchema.get());
        syncRequest.addData(getConnector().getId(), new EntityData("Leads").setId(id));
        List<EntityData> data = getDataService().getByIds(syncRequest);
        assertTrue(data.size() == 1);
        verifyLeadAllDataTypes(data.get(0));
    }

    private void verifyLeadAllDataTypes(EntityData ed) {
        assertTrue(ed.getValue("Id") instanceof String);
        assertTrue(ed.getValue("City") instanceof String);
        assertTrue(ed.getValue("Email_Opt_Out") instanceof Boolean);
        assertTrue(ed.getValue("Owner") instanceof String);
        assertTrue(ed.getValue("Created_By") instanceof String);
        assertTrue(ed.getValue("Email") instanceof String);
        assertTrue(ed.getValue("Annual_Revenue") instanceof Integer);
        assertNotNull(ed.getId());
        assertNotNull(ed.getCreatedAt());
        assertTrue(ed.getCreatedAt() > 0);
        assertNotNull(ed.getLastModified());
        assertTrue(ed.getLastModified() > 0);
    }

    @Override
    @Test
    public void referencesTest() {
        DescribeRequest request = new DescribeRequest(getConnector(), "Contacts");
        Optional<EntitySchema> entitySchema = getMetadataService().describe(request);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get()).setEntitySchemaWithMappedFields(entitySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0);
        watermark.setLimit(2);
        syncRequest.setWatermark(watermark);
        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
        String id = "";
        while (byWatermark.getIterator().hasNext()) {
            List<EntityData> data = byWatermark.getIterator().next();
            assertFalse(data.isEmpty());
            // make sure limit is applied.
            assertTrue(data.size() == 2);
            assertTrue(data.get(0).getValue("Owner") instanceof String);
            assertTrue(data.get(0).getValue("Account_Name") instanceof String);
            id = data.get(0).getId();
        }
        syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get()).setEntitySchemaWithMappedFields(entitySchema.get());
        syncRequest.addData(getConnector().getId(), new EntityData("Contacts").setId(id));
        List<EntityData> data = getDataService().getByIds(syncRequest);
        assertTrue(data.size() == 1);
        assertTrue(data.get(0).getValue("Owner") instanceof String);
        assertTrue(data.get(0).getValue("Account_Name") instanceof String);
    }

    //@Override TODO: Make this as part of the DataServiceTest
    @Test
    public void verifyGetMaxRecordsPerEntitySyncCycle() {
        verifyMaxRecordsPerEntitySyncCycle("Contacts", 400);
    }

    @Override
    @Test
    public void rateLimitTest() {
        ZohoService zohoService = Mockito.spy(service);
        ZohoRestClient mockClient = Mockito.mock(ZohoRestClient.class);

        // Throw NonRetriableException and check
        doReturn(mockClient).when(zohoService).getClient();
        Mockito.doThrow(new NonRetriableException(ErrorCodes.TOO_MANY_REQUESTS, "Too many requests", ErrorCodes.TOO_MANY_REQUESTS.toString()))
            .when(mockClient).getResponse(any(String.class), any(AuthConfig.class));
        verifyRateLimit(zohoService);

        // Throw RetriableException and check
        doReturn(mockClient).when(zohoService).getClient();
        Mockito.doThrow(new RetriableException(ErrorCodes.TOO_MANY_REQUESTS, "Too many requests", ErrorCodes.TOO_MANY_REQUESTS.toString()))
            .when(mockClient).getResponse(any(String.class), any(AuthConfig.class));
        verifyRateLimit(zohoService);

        /*
        //Mockito.doThrow(new NonRetriableException(ErrorCodes.TOO_MANY_REQUESTS, "Too many requests", ErrorCodes.TOO_MANY_REQUESTS.toString()))
        //    .when(mockClient).postRaw(any(HttpHeaders.class), any(String.class), any(String.class), any(AuthConfig.class));
        tryInSeconds = DateUtil.getSecondsToNextHour();
        try {
            EntitySchema entitySchema = describe("Contacts", null).get();
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema).setPageSize(2);
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            FetchResponse response = zohoService.getByWatermark(request);
            response.getIterator().hasNext();
            fail();
        } catch (QuotaExceededException e) {
            assertEquals(ErrorCodes.TOO_MANY_REQUESTS.name(), e.getErrorCode());
            // This can be flaky for exact top of the hour test runs ?
            assertTrue(e.getTryInSeconds() >= tryInSeconds - 10 && e.getTryInSeconds() <= tryInSeconds + 10);
        }

        tryInSeconds = DateUtil.getSecondsToNextHour();
        try {
            EntitySchema entitySchema = describe("Leads", null).get();
            SyncRequest request = new SyncRequest().Builder(connector, entitySchema).setPageSize(2);
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            request.addData(connector.getId(), new EntityData().setId("randomid"));
            List<EntityData> response = zohoService.getByIds(request);
            fail();
        } catch (QuotaExceededException e) {
            assertEquals(ErrorCodes.TOO_MANY_REQUESTS.name(), e.getErrorCode());
            // This can be flaky for exact top of the hour test runs ?
            assertTrue(e.getTryInSeconds() >= tryInSeconds - 10 && e.getTryInSeconds() <= tryInSeconds + 10);
        }
        */
    }

    private EntityData createLeadEntity(String email, String firstName, String lastName) {
        Map<String, Object> edMap = new HashMap<>();
        edMap.put("Email", email);
        edMap.put("First_Name", firstName);
        edMap.put("Last_Name", lastName);
        return new EntityData("Leads").withValues(edMap).setConnectorId(connector.getId())
            .setSyncariEntityId(UUID.randomUUID().toString());
    }

    private EntityData createCustomObjectEntity(String email, String name) {
        Map<String, Object> edMap = new HashMap<>();
        edMap.put("Email", email);
        edMap.put("Name", name);
        return new EntityData("CustomObjects").withValues(edMap).setConnectorId(connector.getId())
            .setSyncariEntityId(UUID.randomUUID().toString());
    }

}
