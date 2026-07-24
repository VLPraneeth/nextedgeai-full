package com.syncari.connector.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.ParseException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.AbstractConnectorTest;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.DataServiceTest;
import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.connector.Status;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.EntityDataBatchIterator;
import com.syncari.connector.data.iterator.ODataEntityDataIterator;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;

import com.syncari.utils.DateUtil;
import org.apache.olingo.client.api.communication.request.retrieve.ODataEntitySetIteratorRequest;
import org.apache.olingo.client.api.communication.request.retrieve.ODataEntitySetRequest;
import org.apache.olingo.client.api.domain.ClientEntity;
import org.apache.olingo.client.api.domain.ClientEntitySet;
import org.apache.olingo.client.api.http.HttpClientException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
public class MSDServiceTest extends AbstractConnectorTest implements DataServiceTest {
    public static final String ACCOUNT_WITH_ID_DOES_NOT_EXIST = "With Id = %s Does Not Exist ";

    @Autowired
    MsDynamicsService service;

    @Value("${msdynamics.endpoint}")
    String msdEndpoint;

    @Value("${msdynamics.crm.service.url}")
    String msdCrmServiceURL;

    @Value("${msdynamics.client.id}")
    String msdClientId;

    @Value("${msdynamics.client.secret}")
    String msdClientSecret;

    private ConnectorInfo connector;
    private String tokenForTest;

    @Before
    public void before() throws IOException {
        connector = createConnector();
    }

    private ConnectorInfo createConnector() {
        ConnectorInfo c = new ConnectorInfo();
        c.setName("MSDynamics CRM");
        AuthConfig authConfig = new AuthConfig();
        authConfig.setClientId(msdClientId);
        authConfig.setClientSecret(msdClientSecret);
        authConfig.setEndpoint(msdEndpoint);
        authConfig.setRedirectUri("https://localhost/postman");
        authConfig.setAccessToken(getAccessToken(false));
        c.setMetaConfig(Map.of(MsDynamicsService.SERVICE_URL_AUTH_FIELD, msdCrmServiceURL,
                "webhook_id", "21a46eeb-5269-ee11-9ae7-000d3a190fe1", "webhook_signing_secret", "code"));
        c.setAuthConfig(authConfig);
        UUID uuid = UUID.randomUUID();
        c.setId(uuid.toString());
        return c;
    }

    private String getAccessToken(boolean forceRefresh) {
        if (tokenForTest != null && !forceRefresh) {
            return tokenForTest;
        }
        try {
            String parameters = "resource=" + 
                URLEncoder.encode(msdCrmServiceURL, java.nio.charset.StandardCharsets.UTF_8.toString()) + 
                "&client_id=" + URLEncoder.encode(msdClientId, java.nio.charset.StandardCharsets.UTF_8.toString()) + 
                "&client_secret=" + URLEncoder.encode(msdClientSecret, java.nio.charset.StandardCharsets.UTF_8.toString())
                + "&grant_type=client_credentials";

            URL url;
            HttpURLConnection connection = null;
            url = new URL(String.format(MsDynamicsService.OAUTH_URL, msdEndpoint));
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
    public ConnectorInfo getConnector() {
        if (connector == null) connector = createConnector();
        return connector;
    }

    @Override
    public AuthenticationService getAuthenticationService() { return service; }

    @Override
    public MetadataService getMetadataService() { return service; }

    @Override
    public CommonDataService getDataService() { return service; }

    @Override
    public String getDescribeObject() { return "account"; }

    @Override
    public List<String> skipWatermarkFieldVerificationObjects() {
        return List.of("applicationuserrole"); 
    }

    @Override
    public List<String> skipIdFieldVerificationObjects() {
        return List.of("opportunityclose"); 
    }

    @Override
    @Test
    public void testConnectionTest() {
        ConnectorInfo conn = createConnector();
        conn.setMetaConfig(Map.of(MsDynamicsService.SERVICE_URL_AUTH_FIELD, msdCrmServiceURL));
        assertEquals(msdCrmServiceURL ,service.getCRMServiceURL(conn.getMetaConfig()));

        conn.setMetaConfig(Map.of(MsDynamicsService.SERVICE_URL_AUTH_FIELD, msdCrmServiceURL + "/"));
        assertEquals(msdCrmServiceURL ,service.getCRMServiceURL(conn.getMetaConfig())); // trailing space is ignored

        conn.setMetaConfig(Map.of());
        assertEquals("" ,service.getCRMServiceURL(conn.getMetaConfig())); // return empty string if service url is not set

        TestConnectionResponse response = service.testConnection(connector, new ArrayList<>());
        assertTrue("Failed MSD Test Connection", response.isSuccess());
    }

    @Override
    @Test
    public void describeAllTest() {
        //describeAll(null);
        // This is a costly call for MSD since there are 703 objects.
        // We cover as much as in describe test.
        DescribeAllRequest request = new DescribeAllRequest(connector, List.of("account", "contact", "mailboxtrackingcategory"));
        List<EntitySchema> entities = service.describeAll(request);
        assertEquals(3, entities.size());
    }

    @Override
    @Test
    public void describeTest() {
        // Non-existent entity
        DescribeRequest request = new DescribeRequest(connector, "non_existent_entity");
        Optional<EntitySchema> entity = service.describe(request);
        assertFalse("Failed to assert that non_existent_entity is empty", entity.isPresent());

        describe(null, null);
        entity = describe("account", null);
        assertEquals("account", entity.get().getApiName());
        assertEquals("Business that represents a customer or potential customer. " +
            "The company that is billed in business transactions.", entity.get().getDescription());
        assertEquals("Account", entity.get().getDisplayName());
        assertFalse(entity.get().isCustom());
        AttributeSchema accountIdField = null;
        AttributeSchema watermarkField = null;
        for (AttributeSchema attr: entity.get().getAttributes()) {
            if (attr.getApiName().equalsIgnoreCase("accountid")) {
                accountIdField = attr;
            } else if (MsDynamicsService.DEF_WATERMARK_FIELD.equalsIgnoreCase(attr.getApiName())) {
                watermarkField = attr;
            }
            assertNotNull(attr.getApiName());
            assertNotNull(attr.getDisplayName());
            assertNotNull(attr.getDataType());
            if (attr.getApiName().equalsIgnoreCase("ownerid")) {
                assertEquals("systemuser", attr.getReferenceTo());
                assertEquals("systemusers", attr.getReferenceToPluralName());
            }
        }
        assertNotNull(accountIdField);
        assertTrue(accountIdField.isIdField());
        assertTrue(accountIdField.isSystem());
        assertNotNull(watermarkField);
        assertTrue(watermarkField.isWatermarkField());
        assertTrue(watermarkField.isSystem());
        
        // Custom entity
        entity = describe("msdyn_iotdevicevisualizationconfiguration", null);
        assertEquals("msdyn_iotdevicevisualizationconfiguration", entity.get().getApiName());
        assertEquals("IoT Device Visualization Configuration", entity.get().getDescription());
        assertEquals("IoT Device Visualization Configuration", entity.get().getDisplayName());
        assertTrue(entity.get().isCustom());

        entity = describe("applicationuserrole", null);
        assertEquals("applicationuserrole", entity.get().getApiName());
        assertEquals("applicationuserrole", entity.get().getDisplayName());
        assertTrue(entity.get().isCustom());

        // Different plural name
        entity = describe("transactioncurrency", null);
        assertEquals("transactioncurrency", entity.get().getApiName());
        assertEquals("transactioncurrencies", entity.get().getPluralName());
        assertEquals("Currency in which a financial transaction is carried out.", entity.get().getDescription());
        assertEquals("Currency", entity.get().getDisplayName());
        assertFalse(entity.get().isCustom());

        // Default precision for money does not messup syncari schema.
        entity = describe("opportunityclose", null);
        assertEquals("opportunityclose", entity.get().getApiName());
        assertEquals("opportunitycloses", entity.get().getPluralName());
        List<AttributeSchema> moneys = entity.get().getAttributes().stream()
            .filter(x -> x.getApiName().startsWith("actualrevenue")).collect(Collectors.toList());
        moneys.forEach(x -> {
            assertEquals(19, x.getPrecision());
            assertEquals(4, x.getScale());
        });
    }

    @Test
    public void getFirstCreatedTime() {
        SyncRequest request = new SyncRequest().Builder(connector, service.describe(new DescribeRequest(connector, "account")).get());
        long time = service.getFirstCreatedTime(request);
        assertTrue(time > Instant.EPOCH.toEpochMilli());
    }

    @Override
    @Test
    public void getByWatermarkSinceEpoch() {
        verifyGetByWatermarkSinceEpoch("account");
        //verifyGetByWatermarkSinceEpoch("msdyn_iotdevicevisualizationconfiguration");
    }

    @Override
    @Test
    public void getByWatermarkRecent() {
        verifyGetByWatermarkRecent("account");
        //verifyGetByWatermarkRecent("msdyn_iotdevicevisualizationconfiguration");
    }

    @Override
    @Test
    public void getByWatermarkResultsOrdered() {
        verifyGetByWatermarkResultsOrdered("account");
        //verifyGetByWatermarkResultsOrdered("msdyn_iotdevicevisualizationconfiguration");
    }

    @Override
    @Test
    public void getDeletedByWatermark() {
        // TBD we need to delete records before testing this.
        //verifyGetDeletedByWatermark("account");
        //verifyGetDeletedByWatermark("msdyn_iotdevicevisualizationconfiguration");
    }

    @Test
    public void getTransactionCurrenciesFirstCreatedTime() {
        SyncRequest request = new SyncRequest().Builder(connector, service.describe(new DescribeRequest(connector, "transactioncurrency")).get());
        long time = service.getFirstCreatedTime(request);
        assertTrue(time > Instant.EPOCH.toEpochMilli());
    }

    @Test
    public void getByWatermark() {
        DescribeRequest dRequest = new DescribeRequest(connector, "account");
        EntitySchema accountSchema = service.describe(dRequest).get();
        SyncRequest request = new SyncRequest().Builder(connector, accountSchema);
        long time = service.getFirstCreatedTime(request);
        request.setWatermark(new WatermarkInfo(time, -1, true, 0));
        FetchResponse fetchResponse = service.getByWatermark(request);
        EntityDataBatchIterator iterator = fetchResponse.getIterator();
        int count = 0;
        while (iterator.hasNext()) {
            List<EntityData> data = iterator.next();
            assertNotNull(data);
            assertNotNull(data.get(count).getValueAsString("name"));
            assertNotNull(data.get(count).getValueAsString("accountnumber"));
            assertNotNull(data.get(count).getValueAsString("websiteurl"));
            assertNotNull(data.get(count).getValueAsString("revenue"));
            assertNotNull(data.get(count).getValueAsString("emailaddress1"));
            assertNotNull(data.get(count).getValueAsString("address1_country"));
            assertNotNull(data.get(count).getValueAsString("modifiedby"));
            assertNotNull(data.get(count).getValueAsString("modifiedon"));
            assertNotNull(data.get(count).getValueAsString("createdon"));
            assertNotNull(data.get(count).getValueAsString("primarycontactid"));
            assertNotNull(data.get(count).getValueAsString("ownerid"));
            assertNotNull(data.get(count).getValueAsString("transactioncurrencyid"));
            count += 1;
        }
        assertTrue(count > 0);

        // Watermark with start and end boundaries
        request = new SyncRequest().Builder(connector, accountSchema);
        request.setWatermark(new WatermarkInfo(time, System.currentTimeMillis(), true, 0));
        fetchResponse = service.getByWatermark(request);
        iterator = fetchResponse.getIterator();
        count = 0;
        while (iterator.hasNext()) {
            count += 1;
            List<EntityData> data = iterator.next();
            assertNotNull(data);
        }
        assertTrue(count > 0);
    }

    @Override
    @Test
    public void getByWatermarkWithLimit() {
        DescribeRequest dRequest = new DescribeRequest(connector, "contact");
        EntitySchema contactSchema = service.describe(dRequest).get();
        SyncRequest request = new SyncRequest().Builder(connector, contactSchema);
        long time = service.getFirstCreatedTime(request);
        request.setWatermark(new WatermarkInfo(time, -1, true, 0).setLimit(2));
        FetchResponse fetchResponse = service.getByWatermark(request);
        EntityDataBatchIterator iterator = fetchResponse.getIterator();
        long prevLastModified = 0l;
        assertTrue(iterator.hasNext());
        List<EntityData> data = iterator.next();
        for (EntityData record : data) {
            assertTrue(record.getLastModified()>=prevLastModified);
            prevLastModified = record.getLastModified();
        }

        assertEquals(2,data.size());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void getByWatermarkContactsWithPagination() {
        DescribeRequest dRequest = new DescribeRequest(connector, "contact");
        EntitySchema contactSchema = service.describe(dRequest).get();
        SyncRequest request = new SyncRequest().Builder(connector, contactSchema);
        long time = service.getFirstCreatedTime(request);
        request.setWatermark(new WatermarkInfo(time, -1, true, 0));
        request.setPageSize(5);
        FetchResponse fetchResponse = service.getByWatermark(request);
        EntityDataBatchIterator iterator = fetchResponse.getIterator();
        int count = 0;
        int pageCount = 0;
        long prevLastModified = 0l;
        while (iterator.hasNext()) {
            List<EntityData> data = iterator.next();
            assertTrue(data.size() <=5);
            pageCount++;
            for (EntityData record : data) {
                assertNotNull(record.getValueAsString("yomifullname"));
                assertNotNull(record.getValueAsString("fullname"));
                assertNotNull(record.getValueAsString("emailaddress1"));
                assertNotNull(record.getValueAsString("lastname"));
                assertNotNull(record.getValueAsString("modifiedby"));
                assertNotNull(record.getValueAsString("modifiedon"));
                assertNotNull(record.getValueAsString("createdon"));
                assertNotNull(record.getValueAsString("parentcustomerid"));
                assertNotNull(record.getValueAsString("ownerid"));
                //ensure results ordered by lastmodified
                assertTrue(record.getLastModified()>=prevLastModified);
                prevLastModified = record.getLastModified();
                count++;
            }
        }
        assertEquals(13,count);
        assertEquals(3,pageCount);
    }

    @Override
    @Test
    public void getByIds() {
        EntitySchema accountSchema = new EntitySchema("account");
        SyncRequest request = new SyncRequest().Builder(connector, accountSchema);

        List<EntityData> entities = null;
        try {
            entities = service.getByIds(request);
            // No id attribute in the request, this call should return empty collection.
            assertTrue(entities.isEmpty());
        } catch (NoSuchElementException e) {
            // expected.
        }

        accountSchema.addField(new AttributeSchema("accountid", "string").setStatus(Status.ACTIVE).setIdField(true));
        accountSchema.addField(new AttributeSchema("name", "string").setStatus(Status.ACTIVE));
        accountSchema.addField(new AttributeSchema("modifiedon", "datetime")
            .setWatermarkField(true).setStatus(Status.ACTIVE));
        request = new SyncRequest().Builder(connector, accountSchema);
        long time = service.getFirstCreatedTime(request);
        request.setWatermark(new WatermarkInfo(time, -1, true, 0));

        entities = service.getByIds(request);
        // No ids set in the request, this call should return empty collection.
        assertTrue(entities.isEmpty());

        FetchResponse fetchResponse = service.getByWatermark(request);
        EntityDataBatchIterator iterator = fetchResponse.getIterator();
        while (iterator.hasNext()) {
            for (EntityData ed: iterator.next()) {
                request.addData(connector.getId(), ed);
            }
        }
        assertTrue(request.getIds().size() > 0);

        entities = service.getByIds(request);
        assertFalse(entities.isEmpty());
        assertEquals(request.getIds().size(), entities.size());
    }

    @Override
    @Test
    public void allDataTypesTest() {
        // Covers the datatypes listed here, we still need to cover many.
        // https://docs.microsoft.com/en-us/dynamics365/customer-engagement/web-api/attributetypecode?view=dynamics-ce-odata-9
        EntitySchema accountSchema = new EntitySchema("account");
        SyncRequest request = new SyncRequest().Builder(connector, accountSchema);
        accountSchema.addField(new AttributeSchema("accountid", "string").setStatus(Status.ACTIVE).setIdField(true));
        accountSchema.addField(new AttributeSchema("name", "string").setStatus(Status.ACTIVE));
        accountSchema.addField(new AttributeSchema("modifiedon", "datetime")
            .setWatermarkField(true).setStatus(Status.ACTIVE));
        accountSchema.addField(new AttributeSchema("merged", "boolean"));
        accountSchema.addField(new AttributeSchema("revenue_base", "double"));
        accountSchema.addField(new AttributeSchema("ownerid", "reference"));
        accountSchema.addField(new AttributeSchema("exchangerate", "double"));
        accountSchema.addField(new AttributeSchema("openrevenue", "double"));
        accountSchema.addField(new AttributeSchema("versionnumber", "double"));
        accountSchema.addField(new AttributeSchema("accountratingcode", "picklist"));
        accountSchema.addField(new AttributeSchema("numberofemployees", "integer"));
        accountSchema.addField(new AttributeSchema("statuscode", "string"));
        // verify unicode chars are supported.
        accountSchema.addField(new AttributeSchema("description", "string"));

        List<EntityData> entities = service.filterByAttributeValues(request, "name", 
                new ArrayList<>(List.of("A. Datum Corporation (sample)")));
        log.info("entities {}", entities);
        assertNotNull(entities);
        assertEquals("A. Datum Corporation (sample)", entities.get(0).getValue("name").toString());
        assertEquals("7f3e3b00-bac4-eb11-8235-000d3a132a6c", entities.get(0).getValue("accountid").toString());
        // TODO convert this to Datetime and compare.
        assertEquals("2021-06-03T23:01:08Z", entities.get(0).getValue("modifiedon").toString());
        assertEquals(false, entities.get(0).getValue("merged"));
        assertEquals(10000, Double.parseDouble(entities.get(0).getValue("revenue_base").toString()), 0.01);
        assertEquals(0.0, Double.parseDouble(entities.get(0).getValue("openrevenue").toString()), 0.01);
        assertEquals("41de119c-47c0-eb11-8235-0022481c9d1a", entities.get(0).getValue("ownerid").toString());
        assertEquals("1", entities.get(0).getValue("accountratingcode").toString());
        assertEquals(6200, entities.get(0).getValue("numberofemployees"));
        assertEquals(1, entities.get(0).getValue("statuscode"));
        assertEquals("非常自豪能够帮助人们享受生活的产品而闻名的品牌 \n\nA. Datum", entities.get(0).getValue("description").toString());
    }

    @Test
    public void validateMoneyType() {
        DescribeRequest dRequest = new DescribeRequest(connector, "opportunityclose");
        EntitySchema entity = service.describe(dRequest).get();
        SyncRequest request = new SyncRequest().Builder(connector, entity);
        request.setWatermark(new WatermarkInfo(-1, -1, true, 0));
        FetchResponse fetchResponse = service.getByWatermark(request);
        EntityDataBatchIterator iterator = fetchResponse.getIterator();
        while (iterator.hasNext()) {
            List<EntityData> data = iterator.next();
            assertNotNull(data);
            assertEquals(22469.39, Double.parseDouble(data.get(0).getValue("actualrevenue").toString()), 0.1);
            assertEquals(22469.39, Double.parseDouble(data.get(0).getValue("actualrevenue_base").toString()), 0.1);
        }
    }

    @Test
    public void create_entity_invalid_picklist() {
        final String testAccountName = "Create Invalid picklist Test";
        EntitySchema accountSchema = getTestAccountEntitySchema();
        try {
            EntityData accountRecord = new EntityData(accountSchema.getApiName()).setConnectorId(connector.getId())
                .setSyncariEntityId(UUID.randomUUID().toString())
                .setValues(new HashMap<>(Map.of(
                                "name", "Create Entity Test",
                                "preferredcontactmethodcode", 0
                        )));
            SyncResponse createResponse = executeOp(Operation.create, accountSchema, 
                new ArrayList<>(List.of(accountRecord)));
            // Assert Error
            assertFalse(createResponse.isSuccess());
            assertTrue(createResponse.getErrors().get(0).contains(
                "(0x8004431a) A validation error occurred. " +
                "The value 0 of 'preferredcontactmethodcode' on record of type 'account' is outside the valid range. " +
                "Accepted Values: 1,2,3,4,5 [HTTP/1.1 400 Bad Request]"
            ));
        } finally {
            cleanupRecordsByAttributes(accountSchema, "name", new ArrayList<>(List.of(testAccountName)));
        }
    }

    @Test
    public void create_entity_invalid_accountid() {
        final String testAccountName = "Create Invalid AccountId Test";
        EntitySchema accountSchema = getTestAccountEntitySchema();
        try {
            EntityData accountRecord = new EntityData(accountSchema.getApiName()).setConnectorId(connector.getId())
                .setSyncariEntityId(UUID.randomUUID().toString())
                .setValues(new HashMap<>(Map.of(
                    "name", testAccountName,
                    "accountid", "invalidid",
                    "preferredcontactmethodcode", 0
                )));
            SyncResponse createResponse = executeOp(Operation.create, accountSchema, 
                new ArrayList<>(List.of(accountRecord)));
            // Assert Error
            assertFalse(createResponse.isSuccess());
            assertTrue(createResponse.getErrors().get(0).contains(
                "Microsoft.OData.ODataException: Cannot convert the literal 'invalidid' to the expected type 'Edm.Guid'"
            ));
        } finally {
            cleanupRecordsByAttributes(accountSchema, "name", new ArrayList<>(List.of(testAccountName)));
        }
    }
    
    @Override
    @Test
    public void mixedBatchCreateFailuresTest() {
        final String testAccountName = "Create Mixed Entities Test";
        EntitySchema accountSchema = getTestAccountEntitySchema();
        try {
            // one valid record should succeed, here we test a batch with good and bad record.
            EntityData accountRecord2 = new EntityData(accountSchema.getApiName()).setConnectorId(connector.getId())
                .setSyncariEntityId(UUID.randomUUID().toString())
                .setValues(new HashMap<>(Map.of(
                        "name", testAccountName,
                        "preferredcontactmethodcode", 1,
                        "numberofemployees", 18,
                        "openrevenue", 200000000.20
                    )));
            EntityData accountRecord = new EntityData(accountSchema.getApiName()).setConnectorId(connector.getId())
                .setSyncariEntityId(UUID.randomUUID().toString())
                .setValues(new HashMap<>(Map.of(
                        "name", testAccountName,
                        "accountid", "invalidid",
                        "preferredcontactmethodcode", 0
                    )));
            List<EntityData> records = new ArrayList<>();
            records.add(accountRecord);
            records.add(accountRecord2);
            SyncResponse createResponse = executeOp(Operation.create, accountSchema, records);
            assertTrue(createResponse.getResults().size() >= 2);
            assertTrue(createResponse.getErrors().size() >= 1);
            // Assert Success
            assertTrue(createResponse.getResults().get(1).isSuccess());
            assertNotNull(createResponse.getResults().get(1).getId());
            assertNotNull(createResponse.getResults().get(1).getSyncariId());
            // Assert Failure
            assertFalse(createResponse.getResults().get(0).isSuccess());
            assertNull(createResponse.getResults().get(0).getId());
            assertNotNull(createResponse.getResults().get(1).getSyncariId());
            // Assert Error
            assertTrue(createResponse.getErrors().get(0).contains(
                "Microsoft.OData.ODataException: Cannot convert the literal 'invalidid' to the expected type 'Edm.Guid'"
            ));
        } finally {
            cleanupRecordsByAttributes(accountSchema, "name", new ArrayList<>(List.of(testAccountName)));
        }
    }

    @Override
    @Test
    public void batchCreateTest() {
        final List<String> testAccountNames = 
            new ArrayList<>(List.of("Create full success Entities Test 1", "Create full success Entities Test 2"));
        EntitySchema accountSchema = getTestAccountEntitySchema();
        try {
            EntityData accountRecord = new EntityData(accountSchema.getApiName()).setConnectorId(connector.getId())
                .setSyncariEntityId(UUID.randomUUID().toString())
                .setValues(new HashMap<>(Map.of(
                    "name", testAccountNames.get(0),
                    "preferredcontactmethodcode", 1
                )));
            // one valid record should succeed, here we test a batch with good and bad record.
            EntityData accountRecord2 = new EntityData(accountSchema.getApiName()).setConnectorId(connector.getId())
                .setSyncariEntityId(UUID.randomUUID().toString())
                .setValues(new HashMap<>(Map.of(
                    "name", testAccountNames.get(1),
                    "preferredcontactmethodcode", 2
                )));
            SyncResponse createResponse = executeOp(Operation.create, accountSchema, 
                new ArrayList<>(List.of(accountRecord, accountRecord2)));
            assertTrue(createResponse.isSuccess());
            assertTrue(createResponse.getErrors().isEmpty());
            assertEquals(2, createResponse.getResults().size());
            assertFalse(createResponse.getResults().get(0).getId().isEmpty());
            assertFalse(createResponse.getResults().get(0).getSyncariId().isEmpty());
            assertFalse(createResponse.getResults().get(1).getId().isEmpty());
            assertFalse(createResponse.getResults().get(1).getSyncariId().isEmpty());
        } finally {
            cleanupRecordsByAttributes(accountSchema, "name", testAccountNames);
        }
    }

    @Test
    public void cleanupAccountsTest() {
        cleanupRecordsByAttributes(getTestAccountEntitySchema(), "name", 
            new ArrayList<>(List.of("Cleanup Accounts Test")));
    }

    @Override
    @Test
    public void deleteTest() {
        final String testAccountName = "Delete Entity Test";
        EntitySchema accountSchema = getTestAccountEntitySchema();
        try {
            SyncRequest request = new SyncRequest().Builder(connector, accountSchema);
            request.setWatermark(new WatermarkInfo(-1, -1, true, 0));
            FetchResponse fetchResponse = service.getByWatermark(request);
            EntityDataBatchIterator iterator = fetchResponse.getIterator();
            while (iterator.hasNext()) {
                List<EntityData> data = iterator.next();
                assertNotNull(data);
            }
            String changeStream = request.getWatermark().getChangeStream();

            EntityData accountRecord = new EntityData(accountSchema.getApiName()).setConnectorId(connector.getId())
                .setSyncariEntityId(UUID.randomUUID().toString())
                .setValues(new HashMap<>(Map.of(
                                "name", testAccountName,
                                "preferredcontactmethodcode", 1
                        )));
            SyncResponse response = executeOp(Operation.create, accountSchema, new ArrayList<>(List.of(accountRecord)));
            assertTrue(response.isSuccess());
            String deletedId = response.getResults().get(0).getId();
            accountRecord.setId(deletedId);
            response = executeOp(Operation.delete, accountSchema, new ArrayList<>(List.of(accountRecord)));
            assertTrue(response.isSuccess());
            assertTrue(response.getErrors().isEmpty());
            assertTrue(response.getResults().get(0).isSuccess());
            assertFalse(response.getResults().get(0).getSyncariId().isEmpty());
            assertFalse(response.getResults().get(0).getId().isEmpty());

            // Deleted records are only fetched through webhooks
//            request = new SyncRequest().Builder(connector, accountSchema);
//            request.setWatermark(new WatermarkInfo(-1, -1, true, 0).setChangeStream(changeStream));
//            fetchResponse = service.getByWatermark(request);
//            iterator = fetchResponse.getIterator();
//            boolean found = false;
//            while (iterator.hasNext()) {
//                List<EntityData> data = iterator.next();
//                assertNotNull(data);
//                found = data.stream().anyMatch(x -> deletedId.equalsIgnoreCase(x.getId()));
//            }
//            // Make sure we get back the deleted entities.
//            assertTrue(found);
        } finally {
            cleanupRecordsByAttributes(accountSchema, "name", new ArrayList<>(List.of(testAccountName)));
        }
    }

    @Test
    public void delete_basic_no_tracking_supported() {
        // This object does not support change tracking, we will throw error but continue to pull updates.
        DescribeRequest dRequest = new DescribeRequest(connector, "opportunityclose");
        EntitySchema entity = service.describe(dRequest).get();
        SyncRequest request = new SyncRequest().Builder(connector, entity);
        request.setWatermark(new WatermarkInfo(-1, -1, true, 0));
        FetchResponse fetchResponse = service.getByWatermark(request);
        EntityDataBatchIterator iterator = fetchResponse.getIterator();
        List<EntityData> data = new ArrayList<>();
        while (iterator.hasNext()) {
            data = iterator.next();
        }
        assertNotNull(data);
        assertTrue(data.size() > 0);
    }

    @Test
    public void delete_invalidId() {
        final String testAccountName = "Delete InvalidId Entity Test";
        EntitySchema accountSchema = getTestAccountEntitySchema();
        String randomUUID = UUID.randomUUID().toString();
        try {
            EntityData accountRecord = new EntityData(accountSchema.getApiName()).setConnectorId(connector.getId())
                .setSyncariEntityId(UUID.randomUUID().toString())
                .setValues(new HashMap<>(Map.of(
                                "name", testAccountName,
                                "preferredcontactmethodcode", 1
                        )));
            // Put some random UUID
            accountRecord.setId(randomUUID);
            SyncResponse response = executeOp(Operation.delete, accountSchema, new ArrayList<>(List.of(accountRecord)));
            // Assert Error
            assertFalse(response.isSuccess());
            assertTrue(response.getErrors().get(0).contains(String.format(ACCOUNT_WITH_ID_DOES_NOT_EXIST, randomUUID)));
        } finally {
            cleanupRecordsByAttributes(accountSchema, "name", new ArrayList<>(List.of(testAccountName)));
        }
    }

    @Override
    @Test
    public void updateTest() {
        final String testAccountName = "Update Entity Test";
        EntitySchema accountSchema = getTestAccountEntitySchema();
        try {
            EntityData accountRecord = new EntityData(accountSchema.getApiName()).setConnectorId(connector.getId())
                .setSyncariEntityId(UUID.randomUUID().toString())
                .setValues(new HashMap<>(Map.of(
                                "name", testAccountName,
                                "preferredcontactmethodcode", 1,
                                "numberofemployees", 18,
                                "openrevenue", 200000000.20,
                                "primarycontactid", "e83e3b00-bac4-eb11-8235-000d3a132a6c"
                        )));
            SyncResponse response = executeOp(Operation.create, accountSchema, new ArrayList<>(List.of(accountRecord)));
            assertTrue(response.isSuccess());

            SyncRequest request = new SyncRequest().Builder(connector, accountSchema);
            List<EntityData> entities = service.filterByAttributeValues(request, "name", new ArrayList<>(List.of(testAccountName)));
            assertEquals(1, entities.size());
            // assert update took effect.
            assertEquals(1, Integer.parseInt(entities.get(0).getValue("preferredcontactmethodcode").toString()));
            assertEquals("e83e3b00-bac4-eb11-8235-000d3a132a6c", entities.get(0).getValue("primarycontactid").toString());
            assertEquals("145c1282-bcc4-eb11-bacc-0022481f164c", entities.get(0).getValue("ownerid").toString());
            
            accountRecord.setId(response.getResults().get(0).getId());
            accountRecord.getValues().put("preferredcontactmethodcode", 2);
            accountRecord.getValues().put("ownerid", "41de119c-47c0-eb11-8235-0022481c9d1a");
            response = executeOp(Operation.update, accountSchema, new ArrayList<>(List.of(accountRecord)));
            assertTrue(response.isSuccess());
            assertTrue(response.getErrors().isEmpty());
            assertTrue(response.getResults().get(0).isSuccess());
            assertFalse(response.getResults().get(0).getSyncariId().isEmpty());
            assertFalse(response.getResults().get(0).getId().isEmpty());

            request = new SyncRequest().Builder(connector, accountSchema);
            entities = service.filterByAttributeValues(request, "name", new ArrayList<>(List.of(testAccountName)));
            assertEquals(1, entities.size());
            // assert update took effect.
            assertEquals(2, Integer.parseInt(entities.get(0).getValue("preferredcontactmethodcode").toString()));
            assertEquals("e83e3b00-bac4-eb11-8235-000d3a132a6c", entities.get(0).getValue("primarycontactid").toString());
            assertEquals("41de119c-47c0-eb11-8235-0022481c9d1a", entities.get(0).getValue("ownerid").toString());
        } finally {
            cleanupRecordsByAttributes(accountSchema, "name", new ArrayList<>(List.of(testAccountName)));
        }
    }

    @Test
    public void updateContactTest() {
        final String testContactName = "Update Entity Test";
        EntitySchema contactEntitySchema = getTestContactEntitySchema();
        try {
            EntityData contactRecord = new EntityData(contactEntitySchema.getApiName()).setConnectorId(connector.getId())
                    .setSyncariEntityId(UUID.randomUUID().toString())
                    .setValues(new HashMap<>(Map.of(
                            "fullname", testContactName,
                            "firstname", "Update Entity",
                            "lastname", "Test"
,                           "anniversary", DateUtil.parse("2019-01-05", "yyyy-MM-dd"),
                            "adx_identity_lastsuccessfullogin", DateUtil.parse("2019-01-05T00:00", "yyyy-MM-dd'T'HH:mm")
                    )));
            SyncResponse response = executeOp(Operation.create, contactEntitySchema, new ArrayList<>(List.of(contactRecord)));
            assertTrue(response.isSuccess());

            SyncRequest request = new SyncRequest().Builder(connector, contactEntitySchema);
            List<EntityData> entities = service.filterByAttributeValues(request, "fullname", new ArrayList<>(List.of(testContactName)));
            assertEquals(1, entities.size());
            // assert update took effect.
            assertEquals(testContactName, entities.get(0).getValue("fullname").toString());
            assertEquals("2019-01-05", entities.get(0).getValue("anniversary").toString());
            assertEquals("2019-01-05T00:00:00Z", entities.get(0).getValue("adx_identity_lastsuccessfullogin").toString());

        } finally {
            cleanupRecordsByAttributes(contactEntitySchema, "fullname", new ArrayList<>(List.of(testContactName)));
        }
    }
    @Test
    public void update_invalidId() {
        final String testAccountName = "Update InvalidId Entity Test";
        EntitySchema accountSchema = getTestAccountEntitySchema();
        String randomUUID = UUID.randomUUID().toString();
        try {
            EntityData accountRecord = new EntityData(accountSchema.getApiName()).setConnectorId(connector.getId())
                .setSyncariEntityId(UUID.randomUUID().toString())
                .setValues(new HashMap<>(Map.of(
                                "name", testAccountName,
                                "preferredcontactmethodcode", 1
                        )));
            // Put some random UUID
            accountRecord.setId(randomUUID);
            accountRecord.getValues().put("preferredcontactmethodcode", 2);
            SyncResponse response = executeOp(Operation.update, accountSchema, new ArrayList<>(List.of(accountRecord)));
            // Assert Error
            assertFalse(response.isSuccess());
            assertTrue(response.getErrors().get(0).contains(String.format(ACCOUNT_WITH_ID_DOES_NOT_EXIST, randomUUID)));
        } finally {
            cleanupRecordsByAttributes(accountSchema, "name", new ArrayList<>(List.of(testAccountName)));
        }
    }

    @Test
    @Override
    public void mixedBatchUpdateFailuresTest() {
        final String testAccountName = "Mixed Batch Update Entity Test";
        EntitySchema accountSchema = getTestAccountEntitySchema();
        try {
            EntityData accountRecord = new EntityData(accountSchema.getApiName()).setConnectorId(connector.getId())
                .setSyncariEntityId(UUID.randomUUID().toString())
                .setValues(new HashMap<>(Map.of(
                                "name", testAccountName,
                                "preferredcontactmethodcode", 1,
                                "numberofemployees", 18,
                                "openrevenue", 200000000.20,
                                "primarycontactid", "e83e3b00-bac4-eb11-8235-000d3a132a6c"
                        )));
            SyncResponse response = executeOp(Operation.create, accountSchema, new ArrayList<>(List.of(accountRecord)));
            assertTrue(response.isSuccess());

            SyncRequest request = new SyncRequest().Builder(connector, accountSchema);
            List<EntityData> entities = service.filterByAttributeValues(request, "name", new ArrayList<>(List.of(testAccountName)));
            assertEquals(1, entities.size());
            // assert update took effect.
            assertEquals(1, Integer.parseInt(entities.get(0).getValue("preferredcontactmethodcode").toString()));
            assertEquals("e83e3b00-bac4-eb11-8235-000d3a132a6c", entities.get(0).getValue("primarycontactid").toString());
            assertEquals("145c1282-bcc4-eb11-bacc-0022481f164c", entities.get(0).getValue("ownerid").toString());
            
            accountRecord.setId(response.getResults().get(0).getId());
            accountRecord.getValues().put("preferredcontactmethodcode", 2);
            accountRecord.getValues().put("ownerid", "41de119c-47c0-eb11-8235-0022481c9d1a");

            // This record is not there in MSD, so should fail.
            EntityData accountRecord2 = new EntityData(accountSchema.getApiName()).setConnectorId(connector.getId())
                .setSyncariEntityId(UUID.randomUUID().toString())
                .setId(UUID.randomUUID().toString())
                .setValues(new HashMap<>(Map.of(
                                "name", testAccountName,
                                "preferredcontactmethodcode", 1
                        )));

            response = executeOp(Operation.update, accountSchema, new ArrayList<>(List.of(accountRecord, accountRecord2)));
            assertFalse(response.isSuccess());
            assertFalse(response.getErrors().isEmpty());
            // Assert success record
            assertTrue(response.getResults().get(0).isSuccess());
            assertFalse(response.getResults().get(0).getSyncariId().isEmpty());
            assertFalse(response.getResults().get(0).getId().isEmpty());
            // Assert failed record
            assertFalse(response.getResults().get(1).isSuccess());
            assertNotNull(response.getResults().get(1).getSyncariId());
            assertNotNull(response.getResults().get(1).getId());
            // Assert error
            assertTrue(response.getErrors().size() > 0);
            assertTrue(response.getErrors().get(0).contains(
                String.format(ACCOUNT_WITH_ID_DOES_NOT_EXIST, response.getResults().get(1).getId())));
        } finally {
            cleanupRecordsByAttributes(accountSchema, "name", new ArrayList<>(List.of(testAccountName)));
        }
    }

    @Test
    public void catchExceptionRetry() {
        DescribeRequest dRequest = new DescribeRequest(connector, "contact");
        EntitySchema contactSchema = service.describe(dRequest).get();
        SyncRequest request = new SyncRequest().Builder(connector, contactSchema);
        long time = service.getFirstCreatedTime(request);
        request.setWatermark(new WatermarkInfo(time, -1, true, 0).setLimit(2));
        try {
            FetchResponse fetchResponse = service.getByWatermark(request);
            ODataEntityDataIterator iterator = (ODataEntityDataIterator) fetchResponse.getIterator();
            ODataEntitySetIteratorRequest<ClientEntitySet, ClientEntity> mck = mock(ODataEntitySetIteratorRequest.class);
            when(mck.execute()).thenThrow(
                new HttpClientException("Mock connection exception thrown.", new ConnectException("Connection timed out")));
            ODataEntityDataIterator mckIter = spy(iterator);
            when(mckIter.getClientEntitySetRequest(any())).thenReturn(mck);
            mckIter.hasNext();
            List<EntityData> data = mckIter.next();
            fail();
        } catch (Exception e) {
            log.error("Error {} ", e.getMessage(), e);
            assertTrue(e.getMessage().contains("Exceeded 5 of retries with backoffs and original exception"));
            assertTrue(e.getMessage().contains("Mock connection exception thrown."));
        }
    }

    private EntitySchema getTestAccountEntitySchema() {
        EntitySchema accountSchema = service.describe(new DescribeRequest(connector, "account")).get();
        return accountSchema;
    }

    private EntitySchema getTestContactEntitySchema() {
        EntitySchema contact = service.describe(new DescribeRequest(connector, "contact")).get();
        return contact;
    }

    private SyncResponse executeOp(Operation op, EntitySchema schema, List<EntityData> records) {
        Map<String, List<EntityData>> recordsMap = Map.of(connector.getId(), records);
        SyncRequest request = new SyncRequest();
        request.setConnector(connector).setEntitySchema(schema).setData(recordsMap);
        switch (op) {
            case create:
                return service.create(request);
            case update:
                return service.update(request);
            case delete:
                return service.delete(request);
            default:
                throw new IllegalArgumentException(String.format("Operation %s not supported.", op));
        }
    }

    // It is safe to call this without expecting the values to be present. It is a noop for such cases.
    private void cleanupRecordsByAttributes(EntitySchema schema, String attributeName, List<String> values) {
        //EntitySchema accountSchema = getTestAccountEntitySchema();
        SyncRequest request = new SyncRequest().Builder(connector, schema);
        request.setWatermark(new WatermarkInfo(-1, -1, true, 0));
        List<EntityData> entities = service.filterByAttributeValues(request, attributeName, values);
        executeOp(Operation.delete, schema, entities);
    }

    @Override
    public void createTest() {
        // TODO Auto-generated method stub
    }

    @Override
    public void batchUpdateTest() {
        // TODO Auto-generated method stub   
    }

    @Override
    public void batchDeleteTest() {
        // TODO Auto-generated method stub
    }

    @Override
    public void createCustomObjectTest() {
        // TODO Auto-generated method stub
    }

    @Override
    public void updateCustomObjectTest() {
        // TODO Auto-generated method stub
    }

    @Override
    public void deleteCustomObjectTest() {
        // TODO Auto-generated method stub
    }

    @Override
    public void mixedBatchDeleteFailuresTest() {
        // TODO Auto-generated method stub
    }

    @Override
    public void referencesTest() {
        // covered in updateTest
    }

    @Override
    public void rateLimitTest() {
        // TODO Auto-generated method stub
    }

    @Test
    public void webhookTest() {
        WebhookRequest webhookRequest = new WebhookRequest();
        webhookRequest.setBody("{\n" +
                "  \"BusinessUnitId\": \"484538d3-fc8e-eb11-b1ac-000d3a197cea\",\n" +
                "  \"CorrelationId\": \"c7b26659-f936-4473-aeb0-600b53f965a7\",\n" +
                "  \"Depth\": 1,\n" +
                "  \"InitiatingUserAgent\": \"\",\n" +
                "  \"InitiatingUserAzureActiveDirectoryObjectId\": \"a3b9348f-6595-408e-b2e0-c8e7873c8296\",\n" +
                "  \"InitiatingUserId\": \"3a4c38d3-fc8e-eb11-b1ac-000d3a197cea\",\n" +
                "  \"InputParameters\": [\n" +
                "    {\n" +
                "      \"key\": \"Target\",\n" +
                "      \"value\": {\n" +
                "        \"__type\": \"EntityReference:http://schemas.microsoft.com/xrm/2011/Contracts\",\n" +
                "        \"Id\": \"01b5eeb5-455e-ee11-be6f-000d3a1aaa29\",\n" +
                "        \"KeyAttributes\": [],\n" +
                "        \"LogicalName\": \"account\",\n" +
                "        \"Name\": null,\n" +
                "        \"RowVersion\": null\n" +
                "      }\n" +
                "    }\n" +
                "  ],\n" +
                "  \"IsExecutingOffline\": false,\n" +
                "  \"IsInTransaction\": true,\n" +
                "  \"IsOfflinePlayback\": false,\n" +
                "  \"IsolationMode\": 1,\n" +
                "  \"MessageName\": \"Delete\",\n" +
                "  \"Mode\": 0,\n" +
                "  \"OperationCreatedOn\": \"/Date(1697168286708)/\",\n" +
                "  \"OperationId\": \"071864f2-b9ef-46a1-b955-451f8b9a8767\",\n" +
                "  \"OrganizationId\": \"ae89a861-e7ad-41ae-a3b2-2533aa62c095\",\n" +
                "  \"OrganizationName\": \"ae89a861e7ad41aea3b22533aa62c095\",\n" +
                "  \"OutputParameters\": [],\n" +
                "  \"OwningExtension\": {\n" +
                "    \"Id\": \"6e8071b0-7969-ee11-9ae7-000d3a190fe1\",\n" +
                "    \"KeyAttributes\": [],\n" +
                "    \"LogicalName\": \"sdkmessageprocessingstep\",\n" +
                "    \"Name\": \"Syncari Webhook Delete Step for account\",\n" +
                "    \"RowVersion\": null\n" +
                "  },\n" +
                "  \"ParentContext\": {\n" +
                "    \"BusinessUnitId\": \"484538d3-fc8e-eb11-b1ac-000d3a197cea\",\n" +
                "    \"CorrelationId\": \"c7b26659-f936-4473-aeb0-600b53f965a7\",\n" +
                "    \"Depth\": 1,\n" +
                "    \"InitiatingUserAgent\": \"\",\n" +
                "    \"InitiatingUserAzureActiveDirectoryObjectId\": \"a3b9348f-6595-408e-b2e0-c8e7873c8296\",\n" +
                "    \"InitiatingUserId\": \"3a4c38d3-fc8e-eb11-b1ac-000d3a197cea\",\n" +
                "    \"InputParameters\": [\n" +
                "      {\n" +
                "        \"key\": \"Target\",\n" +
                "        \"value\": {\n" +
                "          \"__type\": \"EntityReference:http://schemas.microsoft.com/xrm/2011/Contracts\",\n" +
                "          \"Id\": \"01b5eeb5-455e-ee11-be6f-000d3a1aaa29\",\n" +
                "          \"KeyAttributes\": [],\n" +
                "          \"LogicalName\": \"account\",\n" +
                "          \"Name\": null,\n" +
                "          \"RowVersion\": null\n" +
                "        }\n" +
                "      },\n" +
                "      {\n" +
                "        \"key\": \"x-ms-app-name\",\n" +
                "        \"value\": \"d365custom\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"IsExecutingOffline\": false,\n" +
                "    \"IsInTransaction\": true,\n" +
                "    \"IsOfflinePlayback\": false,\n" +
                "    \"IsolationMode\": 1,\n" +
                "    \"MessageName\": \"Delete\",\n" +
                "    \"Mode\": 0,\n" +
                "    \"OperationCreatedOn\": \"/Date(1697168283896)/\",\n" +
                "    \"OperationId\": \"071864f2-b9ef-46a1-b955-451f8b9a8767\",\n" +
                "    \"OrganizationId\": \"ae89a861-e7ad-41ae-a3b2-2533aa62c095\",\n" +
                "    \"OrganizationName\": \"ae89a861e7ad41aea3b22533aa62c095\",\n" +
                "    \"OutputParameters\": [],\n" +
                "    \"OwningExtension\": {\n" +
                "      \"Id\": \"6acabb1b-ea3e-db11-86a7-000a3a5473e8\",\n" +
                "      \"KeyAttributes\": [],\n" +
                "      \"LogicalName\": \"sdkmessageprocessingstep\",\n" +
                "      \"Name\": \"ObjectModel Implementation\",\n" +
                "      \"RowVersion\": null\n" +
                "    },\n" +
                "    \"ParentContext\": null,\n" +
                "    \"PostEntityImages\": [],\n" +
                "    \"PreEntityImages\": [],\n" +
                "    \"PrimaryEntityId\": \"01b5eeb5-455e-ee11-be6f-000d3a1aaa29\",\n" +
                "    \"PrimaryEntityName\": \"account\",\n" +
                "    \"RequestId\": \"071864f2-b9ef-46a1-b955-451f8b9a8767\",\n" +
                "    \"SecondaryEntityName\": \"none\",\n" +
                "    \"SharedVariables\": [\n" +
                "      {\n" +
                "        \"key\": \"IsAutoTransact\",\n" +
                "        \"value\": true\n" +
                "      },\n" +
                "      {\n" +
                "        \"key\": \"AcceptLang\",\n" +
                "        \"value\": \"en-US,en;q=0.9\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"key\": \"x-ms-app-name\",\n" +
                "        \"value\": \"d365custom\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"key\": \"ChangedEntityTypes\",\n" +
                "        \"value\": [\n" +
                "          {\n" +
                "            \"__type\": \"KeyValuePairOfstringstring:#System.Collections.Generic\",\n" +
                "            \"key\": \"account\",\n" +
                "            \"value\": \"Update\"\n" +
                "          }\n" +
                "        ]\n" +
                "      }\n" +
                "    ],\n" +
                "    \"Stage\": 30,\n" +
                "    \"UserAzureActiveDirectoryObjectId\": \"00000000-0000-0000-0000-000000000000\",\n" +
                "    \"UserId\": \"c852a69d-c65d-4311-9033-6b727da8a6de\"\n" +
                "  },\n" +
                "  \"PostEntityImages\": [],\n" +
                "  \"PreEntityImages\": [],\n" +
                "  \"PrimaryEntityId\": \"01b5eeb5-455e-ee11-be6f-000d3a1aaa29\",\n" +
                "  \"PrimaryEntityName\": \"account\",\n" +
                "  \"RequestId\": \"071864f2-b9ef-46a1-b955-451f8b9a8767\",\n" +
                "  \"SecondaryEntityName\": \"none\",\n" +
                "  \"SharedVariables\": [\n" +
                "    {\n" +
                "      \"key\": \"IsAutoTransact\",\n" +
                "      \"value\": true\n" +
                "    },\n" +
                "    {\n" +
                "      \"key\": \"AcceptLang\",\n" +
                "      \"value\": \"en-US,en;q=0.9\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"key\": \"x-ms-app-name\",\n" +
                "      \"value\": \"d365custom\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"Stage\": 40,\n" +
                "  \"UserAzureActiveDirectoryObjectId\": \"00000000-0000-0000-0000-000000000000\",\n" +
                "  \"UserId\": \"c852a69d-c65d-4311-9033-6b727da8a6de\"\n" +
                "}");
        webhookRequest.setParams(Map.of("code", List.of("code")));
        webhookRequest.setConfig(connector);
        List<EventData> parsedData = service.parseEventData(webhookRequest);
        assertFalse(parsedData.isEmpty());
        assertTrue(parsedData.get(0).getData().getId().equalsIgnoreCase("01b5eeb5-455e-ee11-be6f-000d3a1aaa29"));
        assertTrue(parsedData.get(0).getData().getName().equalsIgnoreCase("account"));
        assertTrue(parsedData.get(0).getData().isDeleted());
    }
}