package com.syncari.connector.impartner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import com.syncari.connector.AbstractConnectorTest;
import com.syncari.connector.ConnectorErrorCodes;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.DataServiceTest;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
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

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RunWith(SpringRunner.class)
@AutoConfigureDataMongo
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
@TestPropertySource("classpath:test_application.properties")
public class ImpartnerServiceTest extends AbstractConnectorTest implements DataServiceTest {

    @Autowired
    ImpartnerService service;

    private ConnectorInfo connector;

    // We want to toggle between apikey based testing and userpassword. 
    // This is a simple toss ;)
    public static final int toss = new Random().nextInt(2);

    @Override
    public ConnectorInfo getConnector() {
        if (connector == null) {
            connector = new ConnectorInfo();
            Map<String, Object> meta = new HashMap<>();
            meta.put(ImpartnerService.TIME_ZONE_ID, "America/Los_Angeles");
            connector.setMetaConfig(meta);
            AuthConfig authConfig = new AuthConfig();
            if (toss == 0) {
                meta.put("authType", AuthType.UserPassword.name());
                log.info("Using userpassword as impartner test credentials.");
                authConfig.setUserName("dev@syncari.com");
                authConfig.setPassword(System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME"));
            } else {
                meta.put("authType", AuthType.ApiKey.name());
                log.info("Using api key (prm-key) as credentials.");
                authConfig.setAccessToken(obtainPRMKey());
            }
            authConfig.addHeader("Authorization", "some junk value to be rejected and properly replaced by refreshToken call.");
            connector.setAuthConfig(authConfig);
            connector.setAuthConfig(service.refreshToken(connector));
            connector.setId(UUID.randomUUID().toString());
        }
        return connector;
    }

    private String obtainPRMKey() {
        ConnectorInfo dummyConnector = new ConnectorInfo();
        Map<String, Object> meta = new HashMap<>();
        meta.put(ImpartnerService.TIME_ZONE_ID, "America/Los_Angeles");
        meta.put("authType", AuthType.UserPassword.name());
        dummyConnector.setMetaConfig(meta);
        AuthConfig authConfig = new AuthConfig();
        authConfig.setUserName("dev@syncari.com");
        authConfig.setPassword(System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME"));
        dummyConnector.setAuthConfig(authConfig);
        return service.refreshToken(dummyConnector).getAccessToken();
    }

    @Test
    public void testApiKeytoPasswordSwitch(){
        connector = new ConnectorInfo();
        Map<String, Object> meta = new HashMap<>();
        meta.put(ImpartnerService.TIME_ZONE_ID, "America/Los_Angeles");
        connector.setMetaConfig(meta);
        AuthConfig authConfig = new AuthConfig();

        // Use apiKey
        meta.put("authType", AuthType.ApiKey.name());
        log.info("Using api key (prm-key) as credentials.");
        authConfig.setAccessToken(obtainPRMKey());
        authConfig.addHeader("Authorization", "some junk value to be rejected and properly replaced by refreshToken call.");
        connector.setAuthConfig(authConfig);
        connector.setId(UUID.randomUUID().toString());

        TestConnectionResponse testConnectionResponse = getAuthenticationService().testConnection(connector, List.of());
        assertTrue(testConnectionResponse.isSuccess());

        // Invalidate accessToken
        connector.getAuthConfig().setAccessToken("badAccessToken");
        testConnectionResponse = getAuthenticationService().testConnection(connector, List.of());
        assertFalse(testConnectionResponse.isSuccess());

        // Set user/password

        meta.put("authType", AuthType.UserPassword.name());
        log.info("Using userpassword as impartner test credentials.");
        connector.getAuthConfig().setUserName("dev@syncari.com");
        connector.getAuthConfig().setPassword(System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME"));

        testConnectionResponse = getAuthenticationService().testConnection(connector, List.of());
        assertTrue(testConnectionResponse.isSuccess());
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

    public List<String> skipPickListVerificationObjects() { return ListUtils.sum(ImpartnerService.SUPPORTED_OBJECTS_WITH_WATERMARK, ImpartnerService.SUPPORTED_OBJECTS_WITHOUT_WATERMARK); }
    public List<String> skipPickListVerificationAttributes() { return List.of(); }

    @Override
    public boolean skipWatermarkFieldVerification(String entity) {
        return !ImpartnerService.SUPPORTED_OBJECTS_WITH_WATERMARK.contains(entity);
    }

    public List<String> skipIdFieldVerificationObjects() { return List.of(); }

    @Override
    public String getDescribeObject() {
        return "Contact";
    }

    @Override
    @Test
    public void testConnectionTest() {
        retryWithBackoff(() -> {
            verifyTestConnection();
        });

        // Verify invalid scenarios
        ConnectorInfo connectorT = new ConnectorInfo();
        Map<String, Object> meta = new HashMap<>();
        meta.put("authType", AuthType.UserPassword.name());
        connectorT.setMetaConfig(meta);
        AuthConfig authConfig = new AuthConfig();
        authConfig.setUserName("dev_invalid@syncari.com");
        authConfig.setPassword(System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME"));
        connectorT.setAuthConfig(authConfig);
        
        TestConnectionResponse response = getAuthenticationService().testConnection(connectorT, List.of());
        assertFalse(response.isSuccess());
        assertEquals(ConnectorErrorCodes.CONNECTION_ERROR, response.getCode());
        assertTrue(response.getMessage().startsWith("Authentication failed."));
        assertFalse(response.getErrors().isEmpty());
        assertEquals(response.getErrors().get(0), "Invalid username/password");

        connectorT = new ConnectorInfo();
        meta = new HashMap<>();
        meta.put("authType", AuthType.ApiKey.name());
        connectorT.setMetaConfig(meta);
        authConfig = new AuthConfig();
        authConfig.setAccessToken("junktoken");
        connectorT.setAuthConfig(authConfig);
        
        response = getAuthenticationService().testConnection(connectorT, List.of());
        assertFalse(response.isSuccess());
        assertEquals(ConnectorErrorCodes.CONNECTION_ERROR, response.getCode());
        assertTrue(response.getMessage().startsWith("Authentication failed."));
    }

    @Test
    public void verifyFastBackoffRefreshToken() {
        // Test if the retry happens before bailing out.
        ConnectorInfo connectorT = new ConnectorInfo();
        Map<String, Object> meta = new HashMap<>();
        meta.put("authType", AuthType.UserPassword.name());
        connectorT.setMetaConfig(meta);
        AuthConfig authConfig = new AuthConfig();
        authConfig.setUserName("dev_invalid@syncari.com");
        authConfig.setPassword(System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME"));
        authConfig.addHeader("Authorization", "some junk value to be rejected and properly replaced by refreshToken call.");
        connectorT.setAuthConfig(authConfig);
        try {
            connectorT.setAuthConfig(service.refreshToken(connectorT));
        } catch (NonRetriableException e) {
            assertEquals(e.getErrorCode(), ErrorCodes.ACCESS_DENIED.toString());
        }
    }

    @Override
    @Test
    public void describeAllTest() {
        describeAll(null);
    }

    @Override
    @Test
    public void describeTest() {
        Optional<EntitySchema> schema = describe("Contact", null);
        ImpartnerService.SUPPORTED_OBJECTS_WITH_WATERMARK.forEach(x -> {
            describe(x, null);
        });
        ImpartnerService.SUPPORTED_OBJECTS_WITHOUT_WATERMARK.forEach(x -> {
            describe(x, null);
        });
    }

    @Test
    public void transformData() {
        EntitySchema schema = describe("Deal", null).get();
        schema.getAttributes().add(new AttributeSchema("test", "picklist").setMultiValueField(true).setSubDataType("string"));
        SyncRequest req = new SyncRequest().Builder(getConnector(), schema);
        List val = new ArrayList();
        val.add("100");
        val.add(null);
        req.getData().put(getConnector().getId(), List.of(new EntityData("Deal").setId("123").addValue("test", val)));
        req = service.transformData(req);
        assertEquals(1, ((List)req.getData().get(getConnector().getId()).get(0).getValues().get("test")).size());
    }

    @Test
    public void describeDealTest() {
        Optional<EntitySchema> schema = describe("Deal", null);
        assertTrue(schema.isPresent());
        assertTrue(schema.get().getAttributes().stream().filter(x -> x.getApiName().equalsIgnoreCase("dealowner__cf")).collect(Collectors.toList()).size() == 1);
        assertTrue(schema.get().getAttributes().stream().filter(x -> x.getApiName().equalsIgnoreCase("dealowner__cf")).collect(Collectors.toList()).get(0).isCustom());
    }

    @Test
    public void describeAccountDetailed() {
        Optional<EntitySchema> schema = describe("Account", null);
        assertTrue(schema.isPresent());
        EntitySchema accountSchema = schema.get();
        // Id Field
        assertEquals("integer", accountSchema.getField("Id").get().getDataType());
        assertTrue(accountSchema.getField("Id").get().isSystem());
        assertTrue(accountSchema.getField("Id").get().isIdField());
        assertFalse(accountSchema.getField("Id").get().isUpdateable());
        // String
        List.of("Site", "FaxAlternate", "MailingCity", "Website").stream().forEach(x -> {
            assertEquals("string", accountSchema.getField("Site").get().getDataType());
        });
        // Polymorphic reference not supported.
        /*
        assertEquals("polymorphicreference", accountSchema.getField("Accounts").get().getDataType());
        assertEquals("Account", accountSchema.getField("Accounts").get().getReferenceTo());
        assertEquals("Id", accountSchema.getField("Accounts").get().getReferenceTargetField());
        */
        // Single reference
        assertEquals("reference", accountSchema.getField("ConvertedApplicant").get().getDataType());
        assertEquals("Applicant", accountSchema.getField("ConvertedApplicant").get().getReferenceTo());
        assertEquals("Id", accountSchema.getField("ConvertedApplicant").get().getReferenceTargetField());
        assertEquals("boolean", accountSchema.getField("PartnerLocator").get().getDataType());

        // Single picklist
        assertEquals("picklist", accountSchema.getField("PartnerLevel").get().getDataType());
        assertFalse(accountSchema.getField("PartnerLevel").get().isMultiValueField());
        assertEquals("integer", accountSchema.getField("PartnerLevel").get().getSubDataType());

        // Single picklist
        assertEquals("picklist", accountSchema.getField("Number_Count__cf").get().getDataType());
        assertFalse(accountSchema.getField("Number_Count__cf").get().isMultiValueField());
        assertEquals("string", accountSchema.getField("Number_Count__cf").get().getSubDataType());
        assertTrue(accountSchema.getField("Number_Count__cf").get().getPicklistValues().contains("100"));
        assertTrue(accountSchema.getField("Number_Count__cf").get().getPicklistValues().contains("200"));

        // Multiple picklist
        assertEquals("picklist", accountSchema.getField("Number_Multi_Count__cf").get().getDataType());
        assertTrue(accountSchema.getField("Number_Multi_Count__cf").get().isMultiValueField());
        assertEquals("string", accountSchema.getField("Number_Multi_Count__cf").get().getSubDataType());
        assertTrue(accountSchema.getField("Number_Multi_Count__cf").get().getPicklistValues().contains("100"));
        assertTrue(accountSchema.getField("Number_Multi_Count__cf").get().getPicklistValues().contains("200"));

        // Single picklist
        assertEquals("picklist", accountSchema.getField("MailingCountry").get().getDataType());
        assertTrue(accountSchema.getField("MailingCountry").get().getPicklistValues().contains("Afghanistan"));
        // Single picklist
        assertEquals("picklist", accountSchema.getField("Primary_Vertical__cf").get().getDataType());
        assertTrue(accountSchema.getField("Primary_Vertical__cf").get().getPicklistValues().contains("Federal Government"));
        // Multiple picklist
        assertEquals("picklist", accountSchema.getField("Markets_Served__cf").get().getDataType());
        assertTrue(accountSchema.getField("Markets_Served__cf").get().isMultiValueField());
        assertEquals("string", accountSchema.getField("Markets_Served__cf").get().getSubDataType());
        assertTrue(accountSchema.getField("Markets_Served__cf").get().getPicklistValues().contains("Asia-Pacific"));
        // Decimal type
        assertEquals("number", accountSchema.getField("MailingLatitude").get().getDataType());
        assertEquals(9, accountSchema.getField("MailingLatitude").get().getPrecision());
        assertEquals(6, accountSchema.getField("MailingLatitude").get().getScale());
        assertEquals(9, accountSchema.getField("MailingLatitude").get().getLength());
        // TextArea type
        assertEquals("textarea", accountSchema.getField("Comments").get().getDataType());
        // Long type (LongInteger)
        assertEquals("long", accountSchema.getField("RevenueAttainment").get().getDataType());
        // Datetime type
        assertEquals("datetime", accountSchema.getField("Created").get().getDataType());
        assertEquals("datetime", accountSchema.getField("Updated").get().getDataType());
        assertTrue(accountSchema.getField("Updated").get().isWatermarkField());
        assertEquals("datetime", accountSchema.getField("CrmLastImported").get().getDataType());

        assertEquals("boolean", accountSchema.getField("IsTest").get().getDataType());

        List<String> requiredFields = accountSchema.getAttributes().stream()
            .filter(x -> !x.isNillable()).map(y -> y.getApiName()).collect(Collectors.toList());
        assertEquals(4, requiredFields.size());
    }

    @Override
    @Test
    public void getByWatermarkSinceEpoch() {
        verifyGetByWatermarkSinceEpoch("PartnerLevel");
        verifyGetByWatermarkSinceEpoch("Account");
        verifyGetByWatermarkSinceEpoch("User");
        verifyGetByWatermarkSinceEpoch("Lead");
        verifyGetByWatermarkSinceEpoch("Contact");
        verifyGetByWatermarkSinceEpoch("Deal");
        verifyGetByWatermarkSinceEpoch("Opportunity");
        verifyGetByWatermarkSinceEpoch("DealContact");
        verifyGetByWatermarkSinceEpoch("TestCustomObject__co");


    }

    @Override
    @Test
    public void getByWatermarkRecent() {
        verifyGetByWatermarkRecent("Account");
        verifyGetByWatermarkRecent("User");
        verifyGetByWatermarkRecent("Lead");
        verifyGetByWatermarkRecent("Contact");
        //verifyGetByWatermarkRecent("Opportunity");
    }

    @Override
    @Test
    public void getByWatermarkWithLimit() {
        verifyGetByWatermarkWithLimit("Account", 2);
        verifyGetByWatermarkWithLimit("User", 1);
        verifyGetByWatermarkWithLimit("Lead", 2);
        verifyGetByWatermarkWithLimit("Contact", 2);
        verifyGetByWatermarkWithLimit("Opportunity", 1);
    }

    @Override
    @Test
    public void getByWatermarkResultsOrdered() {
        verifyGetByWatermarkResultsOrdered("Account");
        verifyGetByWatermarkResultsOrdered("User");
        verifyGetByWatermarkResultsOrdered("Lead");
        verifyGetByWatermarkResultsOrdered("Contact");
    }

    @Override
    @Test
    public void getByIds() {
        verifyGetByIds("Account");
        verifyGetByIds("User", 1);
        verifyGetByIds("Lead");
        verifyGetByIds("Contact");
        verifyGetByIds("Opportunity", 1);
        verifyGetByIds("DealContact");
        verifyGetByIds("TestCustomObject__co");

    }

    @Test
    public void getByIdsForDeletedRecords(){
        String entityName = "Account";
        Optional<EntitySchema> entitySchema = describe(entityName, null);
        SyncRequest getByIdRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        List<EntityData> data = List.of(new EntityData().setId("23423"));
        for (EntityData ed: data) {
            getByIdRequest.addData(getConnector().getId(), ed);
        }
        data = getDataService().getByIds(getByIdRequest);
        assertTrue(data.isEmpty());
    }

    @Override
    public void getDeletedByWatermark() {
        // N/A
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
            edMap.put("PartnerLevel", 1371);
            edMap.put("TierOverrideDate", Date.from(ZonedDateTime.now().toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant()));
            edMap.put("TierOverrideExpirationDate", Date.from(ZonedDateTime.now().toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant()));
            data.add(edMap);
        }
        verifyCreateTestWithValues(utStr, "Account", data);

        data = new ArrayList<>();
        for (int i = 0; i < maxRecordsToTest; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("FirstName", utStr + i);
            edMap.put("LastName", utStr + "_Last_" + i);
            edMap.put("Email", utStr + i + "@syncari.com");
            edMap.put("Customer", utStr + i + "_Customer");
            data.add(edMap);
        }
        verifyCreateTestWithValues(utStr, "Lead", data);

        data = new ArrayList<>();
        for (int i = 0; i < maxRecordsToTest; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("Name", utStr + i);
            data.add(edMap);
        }
        verifyCreateTestWithValues(utStr, "Customer", data);

        data = new ArrayList<>();
        for (int i = 0; i < maxRecordsToTest; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("Name", utStr + i);
            data.add(edMap);
        }
        verifyCreateTestWithValues(utStr, "Deal", data);

        data = new ArrayList<>();
        for (int i = 0; i < maxRecordsToTest; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("Name", utStr + i);
            edMap.put("Owner", 3159461);
            data.add(edMap);
        }
        verifyCreateTestWithValues(utStr, "Opportunity", data);

        data = new ArrayList<>();
        for (int i = 0; i < maxRecordsToTest; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("FirstName", utStr + i);
            edMap.put("LastName", utStr + "_Last_" + i);
            data.add(edMap);
        }
        verifyCreateTestWithValues(utStr, "Contact", data);

        data = new ArrayList<>();
        for (int i = 0; i < maxRecordsToTest; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("Contact", 1862);
            edMap.put("CrmHash", 12);
            edMap.put("CrmId", utStr + i);
            edMap.put("CrmLastExportedHash", 12);
            edMap.put("CrmLastExportedVersion", 12);
            edMap.put("Deal", 8787);
            edMap.put("IsPrimary", true);

            data.add(edMap);
        }
        verifyCreateTestWithValues(utStr, "DealContact", data);

        data = new ArrayList<>();
        for (int i = 0; i < maxRecordsToTest; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("Customfield1__cf", true);
            edMap.put("CrmId", utStr + i);
            edMap.put("CrmLastExportedHash", 12);
            edMap.put("CrmLastExportedVersion", 12);
            edMap.put("Name", "CustomObject1");

            data.add(edMap);
        }
        verifyCreateTestWithValues(utStr, "TestCustomObject__co", data);

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
            edMap.put("PartnerLevel", 1371);
            edMap.put("Comments", utStr + i + "_comment");
            data.add(edMap);
        }
        verifyUpdateTestWithValues(utStr, "Account", data, "Comments");

        data = new ArrayList<>();
        for (int i = 0; i < maxRecordsToTest; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("FirstName", utStr + i);
            edMap.put("LastName", utStr + "_Last_" + i);
            edMap.put("Email", utStr + i + "@syncari.com");
            edMap.put("Customer", utStr + i + "_Customer");
            data.add(edMap);
        }
        verifyUpdateTestWithValues(utStr, "Lead", data, "LastName");

        data = new ArrayList<>();
        for (int i = 0; i < maxRecordsToTest; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("Name", utStr + i);
            edMap.put("MailingStreet", utStr + i + "_Street");
            data.add(edMap);
        }
        verifyUpdateTestWithValues(utStr, "Customer", data, "MailingStreet");

        data = new ArrayList<>();
        for (int i = 0; i < maxRecordsToTest; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("Name", utStr + i);
            edMap.put("Description", utStr + i + "_Description");
            data.add(edMap);
        }
        verifyUpdateTestWithValues(utStr, "Deal", data, "Description");

        data = new ArrayList<>();
        for (int i = 0; i < maxRecordsToTest; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("FirstName", utStr + i);
            edMap.put("LastName", utStr + "_Last_" + i);
            data.add(edMap);
        }
        verifyUpdateTestWithValues(utStr, "Contact", data, "LastName");


        data = new ArrayList<>();
        for (int i = 0; i < maxRecordsToTest; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("Contact", 1862);
            edMap.put("CrmHash", 12);
            edMap.put("CrmId", utStr + i);
            edMap.put("CrmLastExportedHash", 12);
            edMap.put("CrmLastExportedVersion", 12);
            edMap.put("Deal", 8787);
            edMap.put("IsPrimary", true);
            //edMap.put("RecordVersion", 12);

            data.add(edMap);
        }
        verifyUpdateTestWithValues(utStr, "DealContact", data, "CrmId");

        data = new ArrayList<>();
        for (int i = 0; i < maxRecordsToTest; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("Customfield1__cf", true);
            edMap.put("CrmId", utStr + i);
            edMap.put("CrmLastExportedHash", 12);
            edMap.put("CrmLastExportedVersion", 12);
            edMap.put("Name", "CustomObject1");

            data.add(edMap);
        }
        verifyUpdateTestWithValues(utStr, "TestCustomObject__co", data, "CrmId");
        
    }

    @Override
    @Test
    public void deleteTest() {
        String utStr = "ut-delete-" + System.currentTimeMillis();
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("Name", utStr + "i");
            edMap.put("PartnerLevel", 1371);
            data.add(edMap);
        }
        verifyDeleteTestWithValues(utStr, "Account", data);
    }

    @Test
    public void deleteNonExistentRecords() {
        SyncRequest request = getSyncRequest("Account");
        List<EntityData> data = List.of(new EntityData("Account").setId("67014"));
        request.setPageSize(2);
        request.setData(Map.of(getConnector().getId(), data));
        request.setConnector(getConnector());
        request.setData(Map.of(request.getConnector().getId(), data));
        SyncResponse response = getDataService().delete(request);
        assertTrue(response.getResults().get(0).isSuccess());
    }

    @Override
    public void batchCreateTest() {
        // covered by create test
    }

    @Override
    public void batchUpdateTest() {
        // covered by update test
    }

    @Override
    public void batchDeleteTest() {
        // covered by create/update test
    }

    @Override
    public void createCustomObjectTest() {
        // N/A
    }

    @Override
    public void updateCustomObjectTest() {
        // N/A
    }

    @Override
    public void deleteCustomObjectTest() {
        // N/A
    }

    public void verifyBatchFailureResult(Result result) {
        assertTrue("Failed to assert results contain expected error message. The message: " + result.getErrors().get(0), 
        result.getErrors().get(0).contains("\"success\":false,\"errors\":[{\"fields\":[\"PartnerLevel\"],\"code\":\"NOT_FOUND\"," +
            "\"message\":\"Partner Level: Record not found: PartnerLevel[@Id=1890]\"}]," +
            "\"message\":\"Partner Level: Record not found: PartnerLevel[@Id=1890]\""));
    }

    @Override
    @Test
    public void mixedBatchCreateFailuresTest() {
        String utStr = "ut-create-mixed-" + System.currentTimeMillis();
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("Name", utStr + "i");
            edMap.put("PartnerLevel", 1371);
            // set a random partnerlevel.
            if (i == 1) edMap.put("PartnerLevel", 1890);
            data.add(edMap);
        }
        verifyMixedBatchCreateTestFailures("Account", data, (Result result) -> verifyBatchFailureResult(result));
    }

    @Override
    @Test
    public void mixedBatchUpdateFailuresTest() {
        String utStr = "ut-update-mixed-" + System.currentTimeMillis();
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("Name", utStr + "i");
            edMap.put("PartnerLevel", 1371);
            data.add(edMap);
        }
        verifyMixedBatchUpdateTestFailures("Account", data, "PartnerLevel", 1890, (Result result) -> verifyBatchFailureResult(result));
    }

    @Override
    public void mixedBatchDeleteFailuresTest() {
        // Covered above.
    }

    @Test
    public void getByNonExistingIds() {
        Optional<EntitySchema> entitySchema = describe("Lead", null);
        SyncRequest getByIdRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        getByIdRequest.addData(getConnector().getId(), (new EntityData("Lead")).setId("123456"));
        // Get by non-existing ids should nto throw an exception
        List<EntityData> data = getDataService().getByIds(getByIdRequest);
        assertTrue(data.isEmpty());
    }

    @Override
    @Test
    public void allDataTypesTest() {
        List<EntityData> data = new ArrayList<>();
        EntityData ed = new EntityData("Lead");
        ed.addValue("FirstName", "allDataTypesTest_FirstName");
        ed.addValue("LastName", "allDataTypesTest_LastName");
        ed.addValue("Email", "allDataTypesTest@syncari.com");
        ed.addValue("Customer", "allDataTypesTest_Customer");
        ed.addValue("Phone", "123-456-7810");
        ed.addValue("Status", "969");
        ed.addValue("MailingCountry", "Guinea");
        ed.addValue("PartnerAccount", 1406302);
        ed.addValue("PartnerUser", 3159461);
        ed.addValue("ConvertedDate", Date.from(ZonedDateTime.parse("2011-12-03T00:00:00Z", DateTimeFormatter.ISO_DATE_TIME).toInstant()));
        ed.addValue("DateAssignedToPartner", "2011-12-04 00:00:00Z");
        ed.addValue("RegistrationDate", Date.from(ZonedDateTime.parse("2011-12-10T00:00:00Z", DateTimeFormatter.ISO_DATE_TIME).toInstant()));

        //ed.addValue("DistributedLeadAge", 100);
        ed.addValue("IsDealRegistration", false);
        ed.setSyncariEntityId(UUID.randomUUID().toString());
        data.add(ed);

        List<String> ids = new ArrayList<>();
        SyncRequest request = getSyncRequest("Lead");
        request.setPageSize(2);
        try {
            request.setData(Map.of(getConnector().getId(), data));
            SyncResponse response = getDataService().create(request);
            assertTrue(response.isSuccess());

            Optional<EntitySchema> entitySchema = describe("Lead", null);
            SyncRequest getByIdRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
            getByIdRequest.addData(getConnector().getId(), (new EntityData("Lead")).setId(response.getResults().get(0).getId()));
            ids.add(response.getResults().get(0).getId());
            data = getDataService().getByIds(getByIdRequest);
            assertTrue(data.size() == 1);
            // Well, everything comes as a string. We will rather verify the values and see if it can be cast.
            assertTrue(data.get(0).getId() instanceof String);
            assertFalse(StringUtils.isEmpty(data.get(0).getId()));
            assertEquals("allDataTypesTest_FirstName", data.get(0).getValue("FirstName"));
            assertEquals("allDataTypesTest_LastName", data.get(0).getValue("LastName"));
            assertEquals("allDataTypesTest@syncari.com", data.get(0).getValue("Email"));
            assertEquals("allDataTypesTest_Customer", data.get(0).getValue("Customer"));
            assertEquals("123-456-7810", data.get(0).getValue("Phone"));
            assertTrue(data.get(0).getValue("Status").toString().equalsIgnoreCase("969"));
            assertTrue(data.get(0).getValue("MailingCountry").toString().equals("Guinea"));
            assertTrue(data.get(0).getValue("PartnerAccount").toString().equalsIgnoreCase("1406302"));
            assertTrue(data.get(0).getValue("PartnerUser").toString().equalsIgnoreCase("3159461"));

            assertFalse(StringUtils.isEmpty(data.get(0).getValue("DateAssignedToPartner").toString()));
            assertEquals(ZonedDateTime.parse("2011-12-04T00:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME).toInstant().toEpochMilli(), data.get(0).getValue("DateAssignedToPartner"));
            assertEquals("2011-12-03", data.get(0).getValue("ConvertedDate"));
            assertEquals("2011-12-10", data.get(0).getValue("RegistrationDate"));
            assertFalse((Boolean) data.get(0).getValue("IsDealRegistration"));
        } finally {
            deleteRecords(request, ids);
        }
    }

    @Test
    @Ignore
    public void getByWatermarkRefTest() {
        List<EntityData> data = new ArrayList<>();
        EntityData ed = new EntityData("Lead");
        ed.addValue("FirstName", "watermarkRefTest_FirstName");
        ed.addValue("LastName", "watermarkRefTest_LastName");
        ed.addValue("Email", "watermarkRefTest@syncari.com");
        ed.addValue("Customer", "watermarkRefTest_Customer");
        ed.addValue("Phone", "123-456-7810");
        ed.addValue("Status", "969");
        ed.addValue("MailingCountry", "Guinea");
        ed.addValue("PartnerAccount", 1406302);
        ed.addValue("PartnerUser", 3159461);
        ed.addValue("DateAssignedToPartner", (new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ")).format(new Date()));
        ed.addValue("IsDealRegistration", true);
        ed.setSyncariEntityId(UUID.randomUUID().toString());
        data.add(ed);

        List<String> ids = new ArrayList<>();
        SyncRequest request = getSyncRequest("Lead");
        request.setPageSize(2);
        try {
            request.setData(Map.of(getConnector().getId(), data));
            SyncResponse response = getDataService().create(request);
            assertTrue(response.isSuccess());
            Thread.sleep(5000);

            Optional<EntitySchema> entitySchema = describe("Lead", null);
            SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
            syncRequest.setPageSize(100);
            WatermarkInfo watermark = new WatermarkInfo(Instant.now().toEpochMilli() - 60 * 1000, Instant.now().toEpochMilli(), true, 0).setResync(true);
            syncRequest.setWatermark(watermark);
            FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);

            assertTrue(byWatermark.getIterator().hasNext());
            List<EntityData> entities = byWatermark.getIterator().next();
            assertTrue(entities.size() > 0);
            int latestIndex = entities.size() - 1;
            ids.add(entities.get(latestIndex).getId());
            assertTrue(entities.get(latestIndex).getId() instanceof String);
            assertFalse(StringUtils.isEmpty(entities.get(latestIndex).getId()));
            assertEquals("watermarkRefTest_FirstName", entities.get(latestIndex).getValue("firstName"));
            assertEquals("watermarkRefTest_LastName", entities.get(latestIndex).getValue("lastName"));
            assertEquals("watermarkRefTest@syncari.com", entities.get(latestIndex).getValue("email"));
            assertEquals("watermarkRefTest_Customer", entities.get(latestIndex).getValue("customer"));
            assertEquals("123-456-7810", entities.get(latestIndex).getValue("phone"));
            assertTrue(entities.get(latestIndex).getValue("status").toString().equalsIgnoreCase("969"));
            assertTrue(entities.get(latestIndex).getValue("mailingCountry").toString().equals("Guinea"));
            assertTrue(entities.get(latestIndex).getValue("partnerAccount").toString().equalsIgnoreCase("1406302"));
            assertTrue(entities.get(latestIndex).getValue("partnerUser").toString().equalsIgnoreCase("3159461"));
            assertFalse(StringUtils.isEmpty(entities.get(latestIndex).getValue("dateAssignedToPartner").toString()));
            assertTrue((Boolean) entities.get(latestIndex).getValue("isDealRegistration"));
        } catch (Exception e) {
        } finally {
            deleteRecords(request, ids);
        }
    }

    @Test
    public void dealObjectTest() {
        List<EntityData> data = new ArrayList<>();
        EntityData ed = new EntityData("Deal");
        String dealName = "dealName_" + System.currentTimeMillis();
        ed.addValue("Name", dealName);
        Date cdt = new Date();
        String closeDate = (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")).format(cdt);
        ed.addValue("CloseDate", closeDate);
        ed.setSyncariEntityId(UUID.randomUUID().toString());
        data.add(ed);

        List<String> ids = new ArrayList<>();
        SyncRequest request = getSyncRequest("Deal");
        request.setPageSize(2);
        try {
            request.setData(Map.of(getConnector().getId(), data));
            SyncResponse response = getDataService().create(request);
            assertTrue(response.isSuccess());
            // Collect all ids to be cleaned-up in finally block.
            ids = response.getResults().stream().map(x -> x.getId()).collect(Collectors.toList());
            Thread.sleep(2000);

            Optional<EntitySchema> entitySchema = describe("Deal", null);
            SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get()).setEntitySchemaWithMappedFields(entitySchema.get());
            // Increase pagesize so we see the recently created deal
            syncRequest.setPageSize(100);
            WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0).setResync(true);
            syncRequest.setWatermark(watermark);
            FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);

            assertTrue(byWatermark.getIterator().hasNext());
            List<EntityData> entities = byWatermark.getIterator().next();
            assertTrue(entities.size() > 0);
            int latestIndex = entities.size() - 1;
            assertTrue(entities.get(latestIndex).getId() instanceof String);
            assertFalse(StringUtils.isEmpty(entities.get(latestIndex).getId()));
            assertEquals(dealName, entities.get(latestIndex).getValue("Name"));
            assertNotNull(entities.get(latestIndex).getValue("CloseDate"));
            LocalDate ldt1 = cdt.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate ldt2 =  LocalDate.parse(entities.get(latestIndex).getValue("CloseDate").toString());

            // Make sure there are no more records to pull and the last offset is set to 0.
            // Otherwise, we keep iterating over same time window.
            assertFalse(byWatermark.getIterator().hasNext());
            assertEquals(0, byWatermark.getIterator().getLastOffset());

            System.out.println(ldt1 + " " + ldt2);
            assertEquals(ldt1.getDayOfMonth(), ldt2.getDayOfMonth());
        } catch (InterruptedException e) {
            fail();
        } finally {
            deleteRecords(request, ids);
        }
    }

    @Test
    public void offsetPaginationTest() {
        Optional<EntitySchema> entitySchema = describe("Deal", null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get()).setEntitySchemaWithMappedFields(entitySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0).setResync(true);
        syncRequest.setWatermark(watermark);
        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);

        while(byWatermark.getIterator().hasNext()) {
            List<EntityData> entities = byWatermark.getIterator().next();
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


        syncRequest.setPageSize(8);
        watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 10).setResync(true);
        syncRequest.setWatermark(watermark);
        byWatermark = getDataService().getByWatermark(syncRequest);

        assertTrue(byWatermark.getIterator().hasNext());
        assertEquals(8, byWatermark.getIterator().next().size());
        assertTrue(byWatermark.getIterator().hasNext());
        assertEquals(8, byWatermark.getIterator().next().size());
        assertTrue(byWatermark.getIterator().hasNext());
//        assertEquals(4, byWatermark.getIterator().next().size());

        syncRequest.setPageSize(100);
        watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0).setResync(true);
        syncRequest.setWatermark(watermark);
        byWatermark = getDataService().getByWatermark(syncRequest);
        assertTrue(byWatermark.getIterator().hasNext());
        assertTrue(byWatermark.getIterator().next().size() > 0);
        assertFalse(byWatermark.getIterator().hasNext());
    }


    @Test
    public void dealObjectCustomReferenceFieldTest() {
        List<EntityData> data = new ArrayList<>();
        EntityData ed = new EntityData("Deal");
        String dealName = "dealName_" + System.currentTimeMillis();
        ed.addValue("Name", dealName);
        ed.addValue("dealOwner__cf","3237081");
        Date cdt = new Date();
        String closeDate = (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")).format(cdt);
        ed.addValue("CloseDate", closeDate);
        ed.setSyncariEntityId(UUID.randomUUID().toString());
        data.add(ed);

        List<String> ids = new ArrayList<>();
        SyncRequest request = getSyncRequest("Deal");
        request.setPageSize(2);
        try {
            request.setData(Map.of(getConnector().getId(), data));
            SyncResponse response = getDataService().create(request);
            assertTrue(response.isSuccess());
            // Collect all ids to be cleaned-up in finally block.
            ids = response.getResults().stream().map(x -> x.getId()).collect(Collectors.toList());
            Thread.sleep(2000);

            Optional<EntitySchema> entitySchema = describe("Deal", null);
            SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get()).setEntitySchemaWithMappedFields(entitySchema.get());
            // Increase pagesize so we see the recently created deal
            syncRequest.setPageSize(100);
            WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0).setResync(true);
            syncRequest.setWatermark(watermark);
            FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);

            assertTrue(byWatermark.getIterator().hasNext());
            List<EntityData> entities = byWatermark.getIterator().next();
            assertTrue(entities.size() > 0);
            int latestIndex = entities.size() - 1;
            assertTrue(entities.get(latestIndex).getId() instanceof String);
            assertFalse(StringUtils.isEmpty(entities.get(latestIndex).getId()));
            assertEquals(dealName, entities.get(latestIndex).getValue("Name"));
            assertNotNull(entities.get(latestIndex).getValue("CloseDate"));
            assertEquals(3237081, entities.get(latestIndex).getValue("dealOwner__cf"));
            LocalDate ldt1 = cdt.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate ldt2 =  LocalDate.parse(entities.get(latestIndex).getValue("CloseDate").toString());

            System.out.println(ldt1 + " " + ldt2);
            assertEquals(ldt1.getDayOfMonth(), ldt2.getDayOfMonth());
        } catch (InterruptedException e) {
            fail();
        } finally {
            deleteRecords(request, ids);
        }
    }
    @Override
    public void referencesTest() {
        // covered in allDatatypesTest
        // EntitySchema account = describe("Account", null).get();
        //List<String> polymor = account.getAttributes().stream()
        //    .filter(x -> x.getDataType().equalsIgnoreCase("polymorphicreference"))
        //    .map(x -> x.getApiName()).collect(Collectors.toList());
        //assertNotNull(polymor);
    }

    @Test
    public void multiValuedPickList() {
        EntitySchema account = describe("Account", null).get();
        //List<String> polymor = account.getAttributes().stream()
        //    .filter(x -> x.getDataType().equalsIgnoreCase("polymorphicreference"))
        //    .map(x -> x.getApiName()).collect(Collectors.toList());
        //assertNotNull(polymor);
        List<String> multiPick = account.getAttributes().stream()
            .filter(x -> x.getDataType().equalsIgnoreCase("picklist") && x.isMultiValueField())
            .map(x -> x.getApiName()).collect(Collectors.toList());
        assertNotNull(multiPick);

        List<EntityData> data = new ArrayList<>();
        EntityData ed = new EntityData("Account");
        ed.addValue("Name", "MultiValuedTest");
        ed.addValue("PartnerLevel", 1371);
        ed.addValue("Markets_Served__cf", List.of("Africa","Asia-Pacific","Latin America"));
        ed.addValue("Number_Count__cf", 100);
        ed.addValue("Number_Multi_Count__cf", List.of(100, 200));
        ed.setSyncariEntityId(UUID.randomUUID().toString());
        data.add(ed);
        
        List<String> ids = new ArrayList<>();
        SyncRequest request = getSyncRequest("Account");
        request.setPageSize(2);
        try {
            request.setData(Map.of(getConnector().getId(), data));
            SyncResponse response = getDataService().create(request);
            assertTrue(response.isSuccess());

            SyncRequest getByIdRequest = new SyncRequest().Builder(getConnector(), account);
            getByIdRequest.addData(getConnector().getId(), (new EntityData("Account")).setId(response.getResults().get(0).getId()));
            ids.add(response.getResults().get(0).getId());
            data = getDataService().getByIds(getByIdRequest);
            assertTrue(data.size() == 1);
            // Well, everything comes as a string. We will rather verify the values and see if it can be cast.
            assertTrue(data.get(0).getId() instanceof String);
            assertFalse(StringUtils.isEmpty(data.get(0).getId()));
            assertTrue(data.get(0).getValue("Markets_Served__cf") instanceof List);
            assertTrue(((List) data.get(0).getValue("Markets_Served__cf")).contains("Asia-Pacific"));
            assertTrue(((List) data.get(0).getValue("Markets_Served__cf")).contains("Africa"));
            assertTrue(((List) data.get(0).getValue("Markets_Served__cf")).contains("Latin America"));

            assertEquals(data.get(0).getValue("Number_Count__cf"), "100");

            assertTrue(data.get(0).getValue("Number_Multi_Count__cf") instanceof List);
            assertTrue(((List) data.get(0).getValue("Number_Multi_Count__cf")).contains("100"));
            assertTrue(((List) data.get(0).getValue("Number_Multi_Count__cf")).contains("200"));

            // Now modify the data.
            ed.addValue("Markets_Served__cf", List.of("Africa","Asia-Pacific","Latin America","Europe"));
            ed.addValue("Number_Count__cf", Integer.valueOf(200));
            ed.addValue("Number_Multi_Count__cf", List.of(Integer.valueOf(300), Integer.valueOf(400)));
            ed.setId(ids.get(0));
            ed.setSyncariEntityId(data.get(0).getSyncariEntityId());
            data.clear();
            data.add(ed);
            request.setData(Map.of(getConnector().getId(), data));
            response = getDataService().update(request);
            assertTrue(response.isSuccess());

            // Same id updated this time.
            data = getDataService().getByIds(getByIdRequest);
            assertTrue(data.size() == 1);
            assertTrue(data.get(0).getValue("Markets_Served__cf") instanceof List);
            assertTrue(((List) data.get(0).getValue("Markets_Served__cf")).contains("Europe"));
            assertTrue(((List) data.get(0).getValue("Markets_Served__cf")).contains("Asia-Pacific"));
            assertTrue(((List) data.get(0).getValue("Markets_Served__cf")).contains("Africa"));
            assertTrue(((List) data.get(0).getValue("Markets_Served__cf")).contains("Latin America"));

            assertEquals(data.get(0).getValue("Number_Count__cf"), "200");

            assertTrue(data.get(0).getValue("Number_Multi_Count__cf") instanceof List);
            assertFalse(((List) data.get(0).getValue("Number_Multi_Count__cf")).contains("100"));
            assertFalse(((List) data.get(0).getValue("Number_Multi_Count__cf")).contains("200"));
            assertTrue(((List) data.get(0).getValue("Number_Multi_Count__cf")).contains("300"));
            assertTrue(((List) data.get(0).getValue("Number_Multi_Count__cf")).contains("400"));
        } finally {
            deleteRecords(request, ids);
        }
    }

    @Override
    public void rateLimitTest() {
        // N/A
    }

    @Test
    public void updateDeletedRecord() {
        List<EntityData> data = new ArrayList<>();
        EntityData ed = new EntityData("Deal");
        ed.setId("1234");
        ed.addValue("dealOwner__cf","3237081");
        SyncRequest request = getSyncRequest("Deal");
        request.setData(Map.of(connector.getId(), List.of(ed)));
        SyncResponse syncResponse = service.update(request);
        assertFalse(syncResponse.isSuccess());
        assertFalse(syncResponse.getResults().isEmpty());
        assertTrue(syncResponse.getResults().get(0).getErrorCode().equalsIgnoreCase("DATA_NOT_FOUND"));
    }
    
}
