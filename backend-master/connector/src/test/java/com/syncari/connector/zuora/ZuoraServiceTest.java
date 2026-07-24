package com.syncari.connector.zuora;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.AbstractConnectorTest;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.DataServiceTest;
import com.syncari.connector.EntityData;
import com.syncari.connector.RetryRule;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.database.HsqlService;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.utils.Retry;
import com.syncari.connector.TestFileStorage;
import com.syncari.connector.data.iterator.EntityDataBatchIterator;

import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
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
@Ignore("The account is inactive")
public class ZuoraServiceTest extends AbstractConnectorTest implements DataServiceTest {

    @Autowired
    ZuoraService service;
    @Autowired
    HsqlService localStorage;

    private ConnectorInfo connector;

    String tokenForTest;

    @Rule
    public RetryRule retryRule = new RetryRule();

    @Before
    public void setup() {
        if (connector == null) {
            connector = getConnector();    
        }
        //dependency across tests
        cleanupLocalStorage();
    }

    protected void cleanupLocalStorage() {
        ZuoraService.SUPPORTED_OBJECTS.forEach(x -> {
            connector.setMetaConfig(Map.of("fileName", x));
            localStorage.cleanupDB(HsqlService.getDbName(connector));    
        });
        connector.setMetaConfig(new HashMap<>());
    }

    @Override
    public ConnectorInfo getConnector() {
        if (connector == null) {
            connector = new ConnectorInfo();
            connector.setId("Zuora_"+ UUID.randomUUID().toString());
            connector.setName("Zuora");
            connector.setEndpoint("https://rest.apisandbox.zuora.com/");
            AuthConfig authConfig = new AuthConfig();
            authConfig.setClientId("b2243f63-0201-494d-93bb-e4ce988be4fd");
            authConfig.setClientSecret(System.getenv().getOrDefault("TEST_CLIENT_SECRET", "REPLACE_ME"));
            authConfig.setEndpoint("https://rest.apisandbox.zuora.com/oauth/token");
            authConfig.setRedirectUri("https://localhost/postman");
            authConfig.setAccessToken(getAccessToken(false, authConfig));
            connector.setAuthConfig(authConfig);
            UUID uuid = UUID.randomUUID();
            connector.setId(uuid.toString());
        }
        return connector;
    }

    private String getAccessToken(boolean forceRefresh, AuthConfig config) {
        if (tokenForTest != null && !forceRefresh) {
            return tokenForTest;
        }
        try {
            String parameters =
                    "client_id=" + URLEncoder.encode(config.getClientId(), java.nio.charset.StandardCharsets.UTF_8.toString()) +
                    "&client_secret=" + URLEncoder.encode(config.getClientSecret(), java.nio.charset.StandardCharsets.UTF_8.toString())
                + "&grant_type=client_credentials";

            URL url;
            HttpURLConnection connection = null;
            url = new URL("https://rest.apisandbox.zuora.com/oauth/token");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("Content-Length", "" + Integer.toString(parameters.getBytes().length));
            connection.setDoOutput(true);
            connection.connect();
            
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()));
            out.write(parameters);
            out.close();
            
            BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            StringBuffer response = new StringBuffer(); 
            while((line = rd.readLine()) != null) {
                response.append(line);
            }
            rd.close();
            
            JsonNode jResponse = (new ObjectMapper()).readTree(response.toString());
            tokenForTest = jResponse.get("access_token").asText();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return tokenForTest;
    }

    @Override
    public AuthenticationService getAuthenticationService() {
        return service;
    }

    @Override
    public MetadataService getMetadataService() {
        return service;
    }

    @Override
    public CommonDataService getDataService() {
        return service;
    }

    public List<String> skipPickListVerificationObjects() { return List.of(); }
    public List<String> skipPickListVerificationAttributes() { return List.of(); }
    public List<String> skipWatermarkFieldVerificationObjects() { return List.of(); }
    public List<String> skipIdFieldVerificationObjects() { return List.of(); }

    @Override
    public String getDescribeObject() {
        return "Account";
    }

    @Override
    @Test
    public void testConnectionTest() {
        verifyTestConnection();
    }

    @Override
    @Test
    public void describeAllTest() {
        describeAll(null);
    }

    @Override
    @Test
    public void describeTest() {
        Optional<EntitySchema> schema = describe("Usage", null);
        assertTrue(schema.isPresent());
    }

    @Test
    public void describeDetailedTest() {
        EntitySchema schema = describe("Account", null).get();
        //System.out.println(schema);
        assertNotNull(schema);
        AttributeSchema idField = schema.getIdField();
        assertNotNull(idField);
        assertEquals("text", idField.getDataType());
        assertEquals("ID", idField.getDisplayName());
        assertFalse(idField.isNillable());
        
        AttributeSchema wmField = schema.getWatermarkField();
        assertNotNull(wmField);
        assertEquals("datetime", wmField.getDataType());
        assertEquals("Updated Date", wmField.getDisplayName());
        assertFalse(wmField.isNillable());
        
        AttributeSchema autoPay = schema.getAttributes().stream().filter(x -> "AutoPay".equalsIgnoreCase(x.getApiName()))
            .findFirst().get();
        assertNotNull(autoPay);
        assertEquals("boolean", autoPay.getDataType());
        assertEquals("Auto Pay", autoPay.getDisplayName());
        
        AttributeSchema TaxExemptStatus = schema.getAttributes().stream().filter(x -> "TaxExemptStatus".equalsIgnoreCase(x.getApiName()))
            .findFirst().get();
        assertNotNull(TaxExemptStatus);
        assertEquals("picklist", TaxExemptStatus.getDataType());
        assertEquals(List.of("No", "Yes", "PendingVerification"), TaxExemptStatus.getPicklistValues());
        
        AttributeSchema accountBalance = schema.getAttributes().stream().filter(x -> "Balance".equalsIgnoreCase(x.getApiName()))
            .findFirst().get();
        assertNotNull(accountBalance);
        assertEquals("decimal", accountBalance.getDataType());
        assertEquals("Account Balance", accountBalance.getDisplayName());
        
        AttributeSchema billToId = schema.getAttributes().stream().filter(x -> "BillToId".equalsIgnoreCase(x.getApiName()))
            .findFirst().get();
        assertNotNull(billToId);
        assertEquals("reference", billToId.getDataType());
        assertEquals("Contact", billToId.getReferenceTo());
        assertEquals("Id", billToId.getReferenceTargetField());

        AttributeSchema billCycleDay = schema.getAttributes().stream().filter(x -> "BillCycleDay".equalsIgnoreCase(x.getApiName()))
            .findFirst().get();
        assertNotNull(billCycleDay);
        assertEquals("integer", billCycleDay.getDataType());

        AttributeSchema lastInvoiceDate = schema.getAttributes().stream().filter(x -> "LastInvoiceDate".equalsIgnoreCase(x.getApiName()))
            .findFirst().get();
        assertNotNull(lastInvoiceDate);
        assertEquals("date", lastInvoiceDate.getDataType());

        AttributeSchema corporateRegion = schema.getAttributes().stream().filter(x -> "CorporateRegion__h".equalsIgnoreCase(x.getApiName()))
            .findFirst().get();
        assertNotNull(corporateRegion);
        assertEquals("picklist", corporateRegion.getDataType());
        assertTrue(corporateRegion.isCustom());
        assertTrue(corporateRegion.getPicklistValues().contains("North America"));
        
    }

    @Test
    public void describeUsageObject() {
        EntitySchema schema = describe("Usage", null).get();
        //System.out.println(schema);
        assertNotNull(schema);
        AttributeSchema accountId = schema.getAttributes().stream().filter(x -> "AccountId".equalsIgnoreCase(x.getApiName()))
            .findFirst().get();
        assertNotNull(accountId);
        assertEquals("reference", accountId.getDataType());
        assertEquals("Account", accountId.getReferenceTo());
        assertTrue(accountId.isCreateOnly());
        assertTrue(accountId.isUpdateable());

        List<String> createOnlyFields = List.of("AccountNumber", "SubscriptionId", "SubscriptionNumber");
        createOnlyFields.stream().forEach(field -> {
            AttributeSchema attr = schema.getAttributes().stream().filter(x -> field.equalsIgnoreCase(x.getApiName()))
                .findFirst().get();
            assertTrue(attr.isCreateOnly());
            assertTrue(attr.isUpdateable());
        });

        List<String> nonUpdateableFields = List.of("CreatedById","CreatedDate","SourceName", "SourceType","UpdatedById","UpdatedDate");
        nonUpdateableFields.stream().forEach(field -> {
            AttributeSchema attr = schema.getAttributes().stream().filter(x -> field.equalsIgnoreCase(x.getApiName()))
                .findFirst().get();
            assertFalse(attr.isInitializable());
            assertFalse(attr.isCreateOnly());
        });
        
        List<String> updateableFields = List.of("Description", "EndDateTime", "Quantity", "RbeStatus", "StartDateTime",
            "SubmissionDateTime","UOM");
        updateableFields.stream().forEach(field -> {
            AttributeSchema attr = schema.getAttributes().stream().filter(x -> field.equalsIgnoreCase(x.getApiName()))
                .findFirst().get();
            assertTrue(attr.isInitializable());
            assertTrue(attr.isUpdateable());
        });
    }

    @Test
    public void describeBillingPreviewRun() {
        EntitySchema schema = describe("BillingPreviewRun", null).get();
        //System.out.println(schema);
        assertNotNull(schema);
        AttributeSchema idField = schema.getIdField();
        assertNotNull(idField);
        assertEquals("text", idField.getDataType());
        assertEquals("ID", idField.getDisplayName());
        assertFalse(idField.isNillable());
        
        AttributeSchema wmField = schema.getWatermarkField();
        assertNotNull(wmField);
        assertEquals("datetime", wmField.getDataType());
        assertEquals("Target Date", wmField.getDisplayName());
        assertFalse(wmField.isNillable());
        
        AttributeSchema accountId = schema.getAttributes().stream().filter(x -> "Account_ID".equalsIgnoreCase(x.getApiName()))
            .findFirst().get();
        assertNotNull(accountId);
        assertEquals("reference", accountId.getDataType());
        assertEquals("Account", accountId.getReferenceTo());
        
        AttributeSchema subscriptionId = schema.getAttributes().stream().filter(x -> "Subscription_SubscriptionId".equalsIgnoreCase(x.getApiName()))
            .findFirst().get();
        assertNotNull(subscriptionId);
        assertEquals("reference", subscriptionId.getDataType());
        assertEquals("Subscription", subscriptionId.getReferenceTo());

        AttributeSchema chargeAmount = schema.getAttributes().stream().filter(x -> "InvoiceItem_ChargeAmount".equalsIgnoreCase(x.getApiName()))
            .findFirst().get();
        assertNotNull(chargeAmount);
        assertEquals("decimal", chargeAmount.getDataType());
        
        AttributeSchema invStartDate = schema.getAttributes().stream().filter(x -> "InvoiceItem_ServiceStartDate".equalsIgnoreCase(x.getApiName()))
            .findFirst().get();
        assertNotNull(invStartDate);
        assertEquals("date", invStartDate.getDataType());
    }

    @Override
    @Test
    public void getByWatermarkSinceEpoch() {
        verifyGetByWatermarkSinceEpoch("Account");
    }

    @Override
    @Test
    public void getByWatermarkRecent() {
        verifyGetByWatermarkRecent("Contact");
    }

    @Override
    @Test
    public void getByWatermarkWithLimit() {
        verifyGetByWatermarkWithLimit("Account", 2);
        verifyGetByWatermarkWithLimit("Subscription", 2);
    }

    @Override
    @Test
    public void getByWatermarkResultsOrdered() {
        verifyGetByWatermarkResultsOrdered("Account");
        for (String entity: ZuoraService.SUPPORTED_OBJECTS) {
            if ("BillingPreviewRun".equalsIgnoreCase(entity)) continue;
            verifyGetByWatermarkResultsOrdered(entity);
        }
    }

    @Override
    @Test
    @Retry
    public void getByIds() {
        verifyGetByIds("AccountingCode", 1);
        for (String entity: ZuoraService.SUPPORTED_OBJECTS) {
            if ("BillingPreviewRun".equalsIgnoreCase(entity)) continue;
            verifyGetByIds(entity, 1);
        }
    }

    @Test
    public void getByIdNotFoundTest() {
        EntitySchema schema = describe("Account", null).get();
        SyncRequest getByIdRequest = new SyncRequest().Builder(getConnector(), schema);
        EntityData account = new EntityData("Account");
        account.setId("123434345345");
        getByIdRequest.addData(getByIdRequest.getConnector().getId(), account);
        List<EntityData> data = service.getByIds(getByIdRequest);
        assertEquals(data.size(), 0);
    }

    @Override
    public void getDeletedByWatermark() {
        // TODO Auto-generated method stub
        
    }

    @Override
    @Test
    public void createTest() {
        int maxRecordsToTest = 2;
        String utStr = "ut-create-" + System.currentTimeMillis();
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < maxRecordsToTest; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("Name", utStr + i);
            edMap.put("Notes", "This is test notes " + i);
            edMap.put("Currency", "USD");
            edMap.put("BillCycleDay", 1);
            edMap.put("Status", "Draft");
            data.add(edMap);
        }
        verifyCreateTestWithValues(utStr, "Account", data);
    }

    @Test
    @Ignore("Use this for testing large volume of data")
    public void getByWatermark_GT_2K() {
        Optional<EntitySchema> entitySchema = describe("Account", null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        //watermark.setLimit(1);
        syncRequest.setWatermark(watermark);
        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
        int dataCount = 0;
        while (byWatermark.getIterator().hasNext()) {
            dataCount += byWatermark.getIterator().next().size();
        }
        // Use the below test to generate 2k records.
        assertTrue(dataCount > 2000);
    }

    @Test
    @Ignore("Use this for generating and testing large volume of data")
    public void createTestRealSlow() {
        int maxRecordsToTest = 100;
        String utStr = "2kBatchTest_";
        for (int j = 0; j < 20; j++) {
            List<Map<String, Object>> data = new ArrayList<>();
            for (int i = 0; i < maxRecordsToTest; i++) {
                Map<String, Object> edMap = new HashMap<>();
                edMap.put("Name", utStr + i);
                edMap.put("Notes", "This is test notes " + i);
                edMap.put("Currency", "USD");
                edMap.put("BillCycleDay", 1);
                edMap.put("Status", "Draft");
                data.add(edMap);
            }

            List<EntityData> raw = new ArrayList<>();
            for (int i = 0; i < data.size(); i++) {
                EntityData ed = new EntityData("Account").withValues(data.get(i));
                raw.add(ed.setSyncariEntityId(UUID.randomUUID().toString()));
            }

            SyncRequest request = getSyncRequest("Account");
            request.setPageSize(2);
            request.setData(Map.of(getConnector().getId(), raw));
            SyncResponse response = getDataService().create(request);

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                // do nothing.
            }
        }
    }

    @Override
    @Test
    public void updateTest() {
        int maxRecordsToTest = 2;
        String utStr = "ut-update-" + System.currentTimeMillis();
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < maxRecordsToTest; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("Name", utStr + i);
            edMap.put("Notes", "This is test notes " + i);
            edMap.put("Currency", "USD");
            edMap.put("BillCycleDay", 1);
            edMap.put("Status", "Draft");
            data.add(edMap);
        }
        verifyUpdateTestWithValues(utStr, "Account", data, "Notes");
    }

    @Override
    public void deleteTest() {
        // Covered by CRU tests
    }

    @Override
    public void batchCreateTest() {
        // covered by createTest;
    }

    @Override
    public void batchUpdateTest() {
        // covered by updateTest;
    }

    @Override
    public void batchDeleteTest() {
        // Covered by CRU tests
    }

    @Override
    public void createCustomObjectTest() {
        // Not yet supported
    }

    @Override
    public void updateCustomObjectTest() {
        // Not yet supported
    }

    @Override
    public void deleteCustomObjectTest() {
        // Not yet supported
    }

    @Test
    public void downloadBillingPreviewRuns() throws InterruptedException {
        SyncRequest request = new SyncRequest();
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0));
        request.setEntitySchema(ZuoraSeed.getBillingPreviewRunSchema());
        request.setConnector(connector);
        request.setStorage(new TestFileStorage());
        FetchResponse byWatermark = service.getByWatermark(request);
        List<BatchJob> batchJobs = byWatermark.getBatchJobs();
        assertEquals(1, batchJobs.size());
        assertTrue(batchJobs.get(0).isPending());
        BatchJob batchJob = batchJobs.get(0);
        Instant stopAt = Instant.now().plus(5, ChronoUnit.MINUTES);
        while (!batchJob.isCompleted()) {
            Thread.sleep(10000);
            request.setBatchJobs(batchJobs);
            byWatermark = service.getByWatermark(request);
            batchJobs = byWatermark.getBatchJobs();
            batchJob = batchJobs.get(0);
            if (Instant.now().toEpochMilli() >= stopAt.toEpochMilli()) {
                fail("Failed to download Billing Preview Run in 5 minutes, aborting.");
                break;
            }
        }
        assertEquals(1, batchJobs.size());
        assertTrue(batchJobs.get(0).isCompleted());
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        assertTrue(next.size() >= 5);
        EntityData oneRecord = next.get(0);
        assertNotNull(oneRecord);
        assertNotNull(oneRecord.getCreatedAt());
        assertNotNull(oneRecord.getValue("targetDate"));
        assertNotNull(oneRecord.getValue("runNumber"));
        assertNotNull(oneRecord.getValue("startDate"));
        assertNotNull(oneRecord.getValue("endDate"));
        assertNotNull(oneRecord.getValue("Account_ID"));
        assertNotNull(oneRecord.getValue("totalAccounts"));
        assertNotNull(oneRecord.getValue("succeededAccounts"));
        assertNotNull(oneRecord.getValue("includingEvergreenSubscription"));
        assertNotNull(oneRecord.getValue("Status"));
    }

    public void verifyBatchFailureResult(Result result) {
        assertTrue("Failed to assert results contain expected error message. The message: " + result.getErrors().get(0), 
        result.getErrors().get(0).contains("\"Success\":false,\"Errors\":[{\"Code\":\"MISSING_REQUIRED_VALUE\"," + 
            "\"Message\":\"Missing required value: Status\"}]"));
    }

    @Override
    @Test
    public void mixedBatchCreateFailuresTest() {
        int maxRecordsToTest = 3;
        String utStr = "ut-create-mixed-" + System.currentTimeMillis();
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < maxRecordsToTest; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("Name", utStr + i);
            edMap.put("Notes", "This is test notes " + i);
            edMap.put("Currency", "USD");
            edMap.put("BillCycleDay", 1);
            if (i != 1) edMap.put("Status", "Draft");
            data.add(edMap);
        }
        verifyMixedBatchCreateTestFailures("Account", data, (Result result) -> verifyBatchFailureResult(result));
    }

    public void verifyBatchUpdateFailureResult(Result result) {
        assertTrue("Failed to assert results contain expected error message. The message: " + result.getErrors().get(0), 
        result.getErrors().get(0).contains("\"Success\":false,\"Errors\":[{\"Code\":\"INVALID_VALUE\"," + 
            "\"Message\":\"invalid value for field Status: \"}]"));
    }

    @Override
    @Test
    public void mixedBatchUpdateFailuresTest() {
        int maxRecordsToTest = 3;
        String utStr = "ut-create-mixed-" + System.currentTimeMillis();
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < maxRecordsToTest; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("Name", utStr + i);
            edMap.put("Notes", "This is test notes " + i);
            edMap.put("Currency", "USD");
            edMap.put("BillCycleDay", 1);
            edMap.put("Status", "Draft");
            data.add(edMap);
        }
        verifyMixedBatchUpdateTestFailures("Account", data, "Status", "", (Result result) -> verifyBatchUpdateFailureResult(result));
        
    }

    @Override
    public void mixedBatchDeleteFailuresTest() {
        // TODO Auto-generated method stub
        
    }

    @Override
    @Test
    @Retry
    public void allDataTypesTest() {
        Instant begin = Instant.now();
        List<EntityData> data = new ArrayList<>();
        EntityData ed = new EntityData("Product");
        ed.addValue("Name", "allDataTypesTest_ProductName");
        ed.addValue("Description", "allDataTypesTest_ProductDescription");
        ed.addValue("EffectiveEndDate", "2066-10-20");
        ed.addValue("EffectiveStartDate", "1966-10-20");
        ed.addValue("SKU", "API-SKU1476935173677");
        ed.setSyncariEntityId(UUID.randomUUID().toString());
        data.add(ed);

        List<String> ids = new ArrayList<>();
        SyncRequest request = getSyncRequest("Product");
        request.setPageSize(2);
        try {
            Thread.sleep(2000);
            request.setData(Map.of(getConnector().getId(), data));
            SyncResponse response = getDataService().create(request);
            assertTrue(response.isSuccess());

            Optional<EntitySchema> entitySchema = describe("Product", null);
            SyncRequest getByIdRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
            getByIdRequest.addData(getConnector().getId(), (new EntityData("Product")).setId(response.getResults().get(0).getId()));
            ids.add(response.getResults().get(0).getId());
            data = getDataService().getByIds(getByIdRequest);
            assertTrue(data.size() == 1);

            // Also getByWatermark that exercises different path.
            SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
            WatermarkInfo watermark = new WatermarkInfo(begin.minus(5, ChronoUnit.SECONDS).toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
            watermark.setLimit(1);
            syncRequest.setWatermark(watermark);
            FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
            assertTrue(byWatermark.getIterator().hasNext());
            List<EntityData> dataByWatermark = byWatermark.getIterator().next();

            List<EntityData> twoSameRecordsToVerify = List.of(data.get(0), dataByWatermark.get(0));
            twoSameRecordsToVerify.forEach(x -> {
                assertTrue(x.getId() instanceof String);
                assertFalse(StringUtils.isEmpty(x.getId()));
                assertEquals("allDataTypesTest_ProductName", x.getValue("Name"));
                assertEquals("allDataTypesTest_ProductDescription", x.getValue("Description"));
                assertEquals("2066-10-20", x.getValue("EffectiveEndDate"));
                assertEquals("1966-10-20", x.getValue("EffectiveStartDate"));
                assertEquals("API-SKU1476935173677", x.getValue("SKU"));
                // Custom field.
                assertEquals("Dan Swanson", x.getValue("ProductManager__c"));
                // Verify something recent.
                assertTrue(x.getCreatedAt() >= begin.toEpochMilli());
                assertTrue(x.getLastModified() >= begin.toEpochMilli());
            });
            
        } catch (InterruptedException e) {
            // do nothing.
        } finally {
            deleteRecords(request, ids);
        }

        // Run some usage datatype checks
        Optional<EntitySchema> entitySchema = describe("Usage", null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        watermark.setLimit(10);
        syncRequest.setWatermark(watermark);
        
        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
        assertTrue(byWatermark.getIterator().hasNext());
        data = byWatermark.getIterator().next();
        assertNotNull(data);
        data.stream().forEach(rec -> {
            assertTrue(rec.getValues().get("Quantity") instanceof Integer || 
                rec.getValues().get("Quantity") instanceof Double);
            assertTrue(rec.getValues().get("StartDateTime") instanceof Long);
            assertTrue(rec.getValues().get("EndDateTime") instanceof Long);
            assertTrue(rec.getValues().get("SourceType") instanceof String);
        });
        
    }

    @Override
    @Test
    public void referencesTest() {
        Instant begin = Instant.now();
        Optional<EntitySchema> entitySchema = describe("Account", null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        watermark.setLimit(1);
        syncRequest.setWatermark(watermark);
        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
        assertTrue(byWatermark.getIterator().hasNext());
        List<EntityData> dataByWatermark = byWatermark.getIterator().next();
        assertTrue(dataByWatermark.size() > 0);
        EntityData parent = dataByWatermark.get(0);

        List<EntityData> data = new ArrayList<>();
        EntityData ed = new EntityData("account");
        ed.addValue("Name", "referencesTest_Name");
        ed.addValue("Currency", "USD");
        ed.addValue("BillCycleDay", 1);
        ed.addValue("Status", "Draft");
        ed.addValue("ParentId", parent.getId());
        ed.setSyncariEntityId(UUID.randomUUID().toString());
        data.add(ed);

        List<String> ids = new ArrayList<>();
        SyncRequest request = getSyncRequest("Account");
        request.setPageSize(2);
        try {
            request.setData(Map.of(getConnector().getId(), data));
            SyncResponse response = getDataService().create(request);
            assertTrue(response.isSuccess());
            Optional<EntitySchema> es = describe("Account", null);
            SyncRequest getByIdRequest = new SyncRequest().Builder(getConnector(), es.get());
            getByIdRequest.addData(getConnector().getId(), (new EntityData("Account")).setId(response.getResults().get(0).getId()));
            ids.add(response.getResults().get(0).getId());
            data = getDataService().getByIds(getByIdRequest);
            assertTrue(data.size() == 1);

            // Also getByWatermark that exercises different path.
            syncRequest = new SyncRequest().Builder(getConnector(), es.get());
            watermark = new WatermarkInfo(begin.minus(5, ChronoUnit.SECONDS).toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
            watermark.setLimit(1);
            syncRequest.setWatermark(watermark);
            byWatermark = getDataService().getByWatermark(syncRequest);
            assertTrue(byWatermark.getIterator().hasNext());
            dataByWatermark = byWatermark.getIterator().next();

            List<EntityData> twoSameRecordsToVerify = List.of(data.get(0), dataByWatermark.get(0));
            twoSameRecordsToVerify.forEach(x -> {
                assertTrue(x.getId() instanceof String);
                assertFalse(StringUtils.isEmpty(x.getId()));
                assertEquals(parent.getId(), x.getValueAsString("ParentId"));
            });
        } finally {
            deleteRecords(request, ids);
        }
    }

    @Override
    @Test
    public void rateLimitTest() {
        ZuoraService mockService = Mockito.spy(service);
        Mockito.doThrow(new NonRetriableException(ErrorCodes.TOO_MANY_REQUESTS, "Too many requests", ErrorCodes.TOO_MANY_REQUESTS.toString()))
            .when(mockService).getResponse(any(String.class), any(AuthConfig.class), any(ZuoraRestClient.class));
        verifyRateLimit(mockService);
    }

}
