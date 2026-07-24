package com.syncari.connector.intercom;

import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;


import static org.junit.Assert.*;
import static com.syncari.connector.intercom.IntercomService.*;

@Slf4j
@RunWith(SpringRunner.class)
@AutoConfigureDataMongo
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
@TestPropertySource("classpath:test_application.properties")
public class IntercomServiceTest extends AbstractConnectorTest implements DataServiceTest {

    public static final String TEST_API_TOKEN = System.getenv().getOrDefault("INTERCOM_TEST_API_TOKEN", "REPLACE_ME");
    @Autowired
    IntercomService service;

    private ConnectorInfo connector;



    private Supplier<String> apiTokenSupplier;

    private Supplier<String> endpointSupplier;

    @Before
    public void beforeTest(){
        apiTokenSupplier = () -> {
            return TEST_API_TOKEN;
        };

        endpointSupplier = () -> {
            return IntercomService.API_HOST_URL;
        };
    }


    @Override
    public ConnectorInfo getConnector() {
        if (connector == null) {
            connector = new ConnectorInfo();

            Map<String, Object> meta = new HashMap<>();
            meta.put("authType", AuthType.ApiKey.name());
            connector.setMetaConfig(meta);

            connector.setEndpoint(endpointSupplier.get());

            AuthConfig authConfig = new AuthConfig();
            authConfig.setAccessToken(apiTokenSupplier.get());
            connector.setAuthConfig(authConfig);

            connector.setId(UUID.randomUUID().toString());
        }
        return connector;
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

    public List<String> skipPickListVerificationObjects() { return IntercomService.SUPPORTED_OBJECTS; }
    public List<String> skipPickListVerificationAttributes() { return List.of(); }
    public List<String> skipWatermarkFieldVerificationObjects() {
        return List.of();
    }
    public List<String> skipIdFieldVerificationObjects() { return List.of(); }

    @Override
    public String getDescribeObject() {
        return null;
    }

    @Override
    @Test
    public void testConnectionTest() {
        retryWithBackoff(() -> {
            verifyTestConnection();
        });

    }

    @Test
    public void testConnectionInvalidApiToken() {

        // invalid api-token
        apiTokenSupplier = () -> {
            return "junktoken";
        };

        TestConnectionResponse response = getAuthenticationService().testConnection(getConnector(), List.of());
        assertFalse(response.isSuccess());
        assertEquals("[Incorrect API key provided for authentication]", response.getErrors().toString());
        assertEquals(ConnectorErrorCodes.CONNECTION_ERROR, response.getCode());
        assertTrue(response.getMessage().startsWith("Authentication failed."));

    }

    @Test
    public void testConnectionInvalidEndpointUrl() {

        // invalid endpoint url
        endpointSupplier = () -> {
            return IntercomService.API_HOST_URL+"ERROR";
        };

        TestConnectionResponse response = getAuthenticationService().testConnection(getConnector(), List.of());
        assertFalse(response.isSuccess());
        assertTrue(response.getErrors().toString().contains("java.net.UnknownHostException: api.intercom.ioERROR"));
        assertEquals(ConnectorErrorCodes.CONNECTION_ERROR, response.getCode());
        assertTrue(response.getMessage().startsWith("Authentication failed."));
    }

    @Override
    @Test
    public void describeAllTest() {
        describeAll(null);
    }


    @Override
    @Test
    public void describeTest() {

        Optional<EntitySchema> schemaOptional = describe( IntercomService.CONTACT, null);
        assertTrue(schemaOptional.isPresent());
        EntitySchema schema = schemaOptional.get();
        assertTrue(schema.hasField("custom_attributes.TestDecimal_9659701"));
        Optional<AttributeSchema> attributeSchema = schemaOptional.get().getField("custom_attributes.TestDecimal_9659701");
        assertTrue(attributeSchema.isPresent());
        assertTrue(attributeSchema.get().getDataType().equalsIgnoreCase("double"));
        describe( IntercomService.COMPANY, null);

    }
    @Test
    public void describeCompany() {
        Optional<EntitySchema> companySchema = describe( IntercomService.COMPANY, null);
        assertTrue(companySchema.isPresent());
        assertTrue(CollectionUtils.isNotEmpty(companySchema.get().getAttributes()));
        assertTrue(companySchema.get().getAttributes().stream().filter(f -> f.getApiName().equalsIgnoreCase("company_id")).collect(Collectors.toList()).stream().findFirst().get().isUpdateable());
    }

    @Test
    public void describeAll() {
        List<EntitySchema> schemaOptional = describeAll(null);
    }

        @Override
    @Test
    public void getByWatermarkSinceEpoch() {

        verifyGetByWatermarkSinceEpoch(IntercomService.CONTACT);
        verifyGetByWatermarkSinceEpoch( IntercomService.COMPANY);
        verifyGetByWatermarkSinceEpoch( CONVERSATION);
        verifyGetByWatermarkSinceEpoch( TICKET);

    }

    @Override
    @Test
    public void getByWatermarkRecent() {

        verifyGetByWatermarkRecent( IntercomService.CONTACT);
        verifyGetByWatermarkRecent( IntercomService.COMPANY);
        verifyGetByWatermarkRecent( CONVERSATION);
        verifyGetByWatermarkRecent( TICKET);

    }

    @Override
    @Test
    public void getByWatermarkWithLimit() {

        verifyGetByWatermarkWithLimit( CONTACT, 1);
        verifyGetByWatermarkWithLimit( COMPANY, 1);
        verifyGetByWatermarkWithLimit( CONVERSATION, 1);
        verifyGetByWatermarkWithLimit( TICKET, 1);

    }

    @Override
    @Test
    public void getByWatermarkResultsOrdered() {

        verifyGetByWatermarkResultsOrdered( CONTACT);
        verifyGetByWatermarkResultsOrdered( COMPANY);
        verifyGetByWatermarkResultsOrdered( TICKET);
        verifyGetByWatermarkResultsOrdered( CONVERSATION);

    }

    @Override
    @Test
    public void getByIds() {

        verifyGetByIds( CONTACT,1);
        verifyGetByIds( COMPANY,1);
        verifyGetByIds( TICKET, 1);
        verifyGetByIds( CONVERSATION, 1);

    }

    @Override
    public void getDeletedByWatermark() {
        // N/A
    }

    @Override
    @Test
    public void createTest() {
        int counter = 1;
        int maxRecordsToTest = 2;
        List<Map<String, Object>> data = new ArrayList<>();


        for (int i = 0; i < maxRecordsToTest; i++) {
            String utStr = "ut-create-"+(counter++)+ "-"  + System.currentTimeMillis();

            Map<String, Object> edMap = new HashMap<>();
            edMap.put("role", "user");
//            edMap.put("external_id", "user"); // only if email is blank
            edMap.put("email", utStr+"@gmail.com");
            edMap.put("phone", "12345678");
            edMap.put("name", "test-"+utStr);
            edMap.put("avatar", "https://pickaface.net/gallery/avatar/unr_sample_161118_2054_ynlrg.png");
            edMap.put("signed_up_at", "2024-07-24T00:00Z");
            edMap.put("last_seen_at", null);
//            edMap.put("owner_id", "1"); // need higher plan
            edMap.put("unsubscribed_from_emails", false);
            edMap.put("custom_attributes.CyrilCustomdata_9028449", "CyrilCustomdata sample1");

            edMap.put("tags", List.of( "7430068", "7239657"));

            data.add(edMap);

        }
        verifyCreateTestWithValues(null, CONTACT, data);


        data = new ArrayList<>();
        for (int i = 0; i < maxRecordsToTest; i++) {
            String utStr = "ut-create-"+(counter++)+ "-"  + System.currentTimeMillis();

            Map<String, Object> edMap = new HashMap<>();
            edMap.put("remote_created_at", new Date().getTime());
//            edMap.put("external_id", "user"); // only if email is blank
            edMap.put("company_id", utStr+"-id");
            edMap.put("name", "test-"+utStr);

            edMap.put("monthly_spend", 10);
            edMap.put("plan", "plan1");
            edMap.put("size", 40);

            edMap.put("website", "http://www.tata.com");
            edMap.put("industry", "motor");

            edMap.put("custom_attributes.customdatcyril_9028450", new Date().getTime());

            data.add(edMap);

        }
        verifyCreateTestWithValues(null, COMPANY, data);

    }

    @Ignore
    @Test
    public void createConversationTest() {
        int counter = 1;
        int maxRecordsToTest = 2;
        List<Map<String, Object>> data = new ArrayList<>();

        for (int i = 0; i < maxRecordsToTest; i++) {
            String utStr = "ut-create-"+(counter++)+ "-"  + System.currentTimeMillis();

            Map<String, Object> edMap = new HashMap<>();

            // Required: from object with type and id
            Map<String, Object> from = new HashMap<>();
            from.put("type", "user");
            from.put("id", "636bfe73fc9eae54115b1af0"); // existing user id
            edMap.put("from", from);

            // Required: body of the conversation
            edMap.put("body", "Test conversation message created at " + utStr);

            data.add(edMap);
        }
        verifyCreateTestWithValues(null, CONVERSATION, data);

    }

    @Ignore
    @Test
    public void createTicketTest() {
        int counter = 1;
        int maxRecordsToTest = 2;
        List<Map<String, Object>> data = new ArrayList<>();

        for (int i = 0; i < maxRecordsToTest; i++) {

            Map<String, Object> edMap = new HashMap<>();
            Map<String, Object> contact = new HashMap<>();
            contact.put("id", "636bfe73fc9eae54115b1af0");
            edMap.put("contacts", List.of(contact));

            Map<String, Object> attrs = new HashMap<>();
            attrs.put("_default_description_", "there is a problem");
            edMap.put("ticket_attributes", attrs);
            edMap.put("ticket_type_id", "2985515");
            data.add(edMap);
        }
        verifyCreateTestWithValues(null, TICKET, data);

    }

    @Test
    public void createFailTest() {
        int counter = 1;
        int maxRecordsToTest = 2;
        List<Map<String, Object>> values = new ArrayList<>();


        for (int i = 0; i < maxRecordsToTest; i++) {
            String utStr = "ut-create-"+(counter++)+ "-"  + System.currentTimeMillis();

            Map<String, Object> edMap = new HashMap<>();
            edMap.put("name", "test-"+utStr);
            edMap.put("avatar", "https://pickaface.net/gallery/avatar/unr_sample_161118_2054_ynlrg.png");
            edMap.put("signed_up_at", new Date().getTime());
            edMap.put("last_seen_at", new Date().getTime());
//            edMap.put("owner_id", "1"); // need higher plan
            edMap.put("unsubscribed_from_emails", false);
            edMap.put("custom_attributes.CyrilCustomdata_9028449", "CyrilCustomdata sample1");

            edMap.put("tags", List.of( "7430068", "7239657"));

            values.add(edMap);

        }

        List<EntityData> data = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            EntityData ed = new EntityData(CONTACT).withValues(values.get(i));
            data.add(ed.setSyncariEntityId(UUID.randomUUID().toString()));
        }

        List<String> ids = new ArrayList<>();
        SyncRequest request = getSyncRequest(CONTACT);
        request.setPageSize(2);
        request.setData(Map.of(getConnector().getId(), data));
        SyncResponse response = getDataService().create(request);
        assertFalse(response.isSuccess());
        assertTrue(response.getResults().stream().filter(result -> StringUtils.isNotBlank(result.getSyncariId())).collect(Collectors.toList()).size() == 2);
    }

    @Test
    public void updateContactTest() {

        String entityName = "contact";

        Map values = Map.of("custom_attributes.CyrilCustomdata_9028449", "Rambo @ 8.45 PM",
                "name" , "Ram" ,
                "tags" , List.of("7430068", "7239657"),
                "companies" , List.of("634eacd89f2f7a3049afd020" ,"63448b7d7518c9bca0e6cf9a")

        );

        EntityData ed = new EntityData(entityName).withValues(values);
        ed.setId("636bfe73fc9eae54115b1af0");

        List listOfEntityData = List.of(ed);

        SyncRequest request = getSyncRequest(entityName);
        request.setPageSize(2);
        request.setData(Map.of(getConnector().getId(), listOfEntityData));

        SyncResponse update = getDataService().update(request);
        assertTrue("Failed to update. Response" + update, update.isSuccess());

        ed.setValues( Map.of("custom_attributes.CyrilCustomdata_9028449", "Rambo @ 8.45 PM",
                "name" , "Ram" ,
                "companies" , List.of("634eacd89f2f7a3049afd020" ,"63448b7d7518c9bca0e6cf9a")

        ));
        SyncResponse update2 = getDataService().update(request);

        List<EntityData> getByIds = getDataService().getByIds(request);
        assertEquals(1, getByIds.size());
        EntityData retEd = getByIds.get(0);
        assertNotNull(retEd);
        assertEquals("636bfe73fc9eae54115b1af0", retEd.getId());
        List<String> companies = (List<String>) retEd.getValue("companies");
        assertEquals(2, companies.size());
        assertTrue(companies.containsAll(List.of("634eacd89f2f7a3049afd020" ,"63448b7d7518c9bca0e6cf9a")));
        List<String> tags = (List<String>) retEd.getValue("tags");
        assertEquals(2, tags.size());
        assertTrue(tags.containsAll(List.of("7430068", "7239657")));
    }




    @Test
    public void updateCompanyTest() {

        String entityName = "company";

        Map values = Map.of("custom_attributes.customdatcyril_9028450", new Date().getTime(),
                "name" , "GEupdated On 10 Aug" ,
                "industry" , "Renewable Energy  On 10 Aug "

        );

        EntityData ed = new EntityData(entityName).withValues(values);
        ed.setId("63448b7d7518c9bca0e6cf9a");

        List listOfEntityData = List.of(ed);

        SyncRequest request = getSyncRequest(entityName);
        request.setPageSize(2);
        request.setData(Map.of(getConnector().getId(), listOfEntityData));

        SyncResponse update = getDataService().update(request);
        assertTrue("Failed to update. Response" + update, update.isSuccess());

    }


    @Test
    public void getContactByIdTest() {

        String entityName = "contact";

        Map values = Map.of("custom_attributes.CyrilCustomdata_9028449", new Date());

        EntityData ed = new EntityData(entityName).withValues(values);
        ed.setId("62ea06a0460a7a85ec8cedeb");

        List listOfEntityData = List.of(ed);

        SyncRequest request = getSyncRequest(entityName);
        request.setPageSize(2);
        request.setData(Map.of(getConnector().getId(), listOfEntityData));

        List<EntityData> update = getDataService().getByIds(request);

    }


    @Test
    public void deleteContactByIdTest() {


        String entityName = "contact";
        SyncRequest request = getSyncRequest(entityName);

        List listOfEntityData = List.of(new EntityData(request.getEntityName()).setId("62ea06a0460a7a85ec8cedeb"));


        request.setPageSize(2);
        request.setData(Map.of(getConnector().getId(), listOfEntityData));

        List<EntityData> update = getDataService().getByIds(request);

    }

    @Override
    @Test
    public void updateTest() {
        int counter = 1;
        int maxRecordsToTest = 2;
        List<Map<String, Object>> data = new ArrayList<>();


        for (int i = 0; i < maxRecordsToTest; i++) {
            String utStr = "ut-update-" +(counter++) + "-" + System.currentTimeMillis();
            Map<String, Object> edMap = new HashMap<>();
            edMap.put("role", "user");
//            edMap.put("external_id", "user"); // only if email is blank
            edMap.put("email", utStr+"@gmail.com");
            edMap.put("phone", "12345678");
            edMap.put("name", "test-"+utStr);
            edMap.put("avatar", "https://pickaface.net/gallery/avatar/unr_sample_161118_2054_ynlrg.png");
            edMap.put("signed_up_at", new Date().getTime());


            edMap.put("last_seen_at", new Date().getTime());
//            edMap.put("owner_id", "1"); // need higher plan
            edMap.put("unsubscribed_from_emails", false);
            edMap.put("custom_attributes.CyrilCustomdata_9028449", "CyrilCustomdata sample1");
            data.add(edMap);
        }
        verifyUpdateTestWithValues(null, CONTACT, data, "custom_attributes.CyrilCustomdata_9028449");


        data = new ArrayList<>();
        for (int i = 0; i < maxRecordsToTest; i++) {
            String utStr = "ut-create-"+(counter++)+ "-"  + System.currentTimeMillis();

            Map<String, Object> edMap = new HashMap<>();
            edMap.put("remote_created_at", new Date().getTime());
//            edMap.put("external_id", "user"); // only if email is blank
            edMap.put("company_id", utStr+"-id");
            edMap.put("name", "test-"+utStr);

            edMap.put("monthly_spend", 10);
            edMap.put("plan", "plan1");
            edMap.put("size", 40);

            edMap.put("website", "http://www.tata.com");
            edMap.put("industry", "motor");

//            edMap.put("custom_attributes.customdatcyril", new Date().getTime());

            data.add(edMap);

        }
        verifyUpdateTestWithValues(null, COMPANY, data, "industry");


    }

    @Override
    @Test
    public void deleteTest() {
        int counter = 1;
        List<Map<String, Object>> data = new ArrayList<>();
        int maxRecordsToTest = 2;

        data = new ArrayList<>();
        for (int i = 0; i < maxRecordsToTest; i++) {
            String utStr = "ut-delete-" +(counter++) + "-" + System.currentTimeMillis();
            Map<String, Object> edMap = new HashMap<>();

            edMap.put("role", "user");
//            edMap.put("external_id", "user"); // only if email is blank
            edMap.put("email", utStr+"@gmail.com");
            edMap.put("phone", "12345678");
            edMap.put("name", "test-"+utStr);
            edMap.put("avatar", "https://pickaface.net/gallery/avatar/unr_sample_161118_2054_ynlrg.png");
            edMap.put("signed_up_at", new Date().getTime());
            edMap.put("last_seen_at", new Date().getTime());
//            edMap.put("owner_id", "1"); // need higher plan
            edMap.put("unsubscribed_from_emails", false);
            edMap.put("custom_attributes.CyrilCustomdata_9028449", "CyrilCustomdata sample1");

            data.add(edMap);
        }
        verifyDeleteTestWithValues(null, CONTACT, data);


        data = new ArrayList<>();
        for (int i = 0; i < maxRecordsToTest; i++) {
            String utStr = "ut-create-"+(counter++)+ "-"  + System.currentTimeMillis();
            Map<String, Object> edMap = new HashMap<>();

            edMap.put("remote_created_at", new Date().getTime());
//            edMap.put("external_id", "user"); // only if email is blank
            edMap.put("company_id", utStr+"-id");
            edMap.put("name", "test-"+utStr);

            edMap.put("monthly_spend", 10);
            edMap.put("plan", "plan1");
            edMap.put("size", 40);

            edMap.put("website", "http://www.tata.com");
            edMap.put("industry", "motor");

            edMap.put("custom_attributes.customdatcyril_9028450", new Date().getTime());

            data.add(edMap);

        }
        verifyDeleteTestWithValues(null, COMPANY, data);

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

    @Override
    @Test
    public void mixedBatchCreateFailuresTest() {}

    @Override
    @Test
    public void mixedBatchUpdateFailuresTest() {

    }

    @Override
    public void mixedBatchDeleteFailuresTest() {
        // Covered above.
    }

    @Test
    public void getByNonExistingIds() {
        Optional<EntitySchema> entitySchema = describe(CONTACT, null);
        SyncRequest getByIdRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        getByIdRequest.addData(getConnector().getId(), (new EntityData(CONTACT)).setId("123456"));
        // Get by non-existing ids should nto throw an exception
        List<EntityData> data = getDataService().getByIds(getByIdRequest);
        assertTrue(data.isEmpty());
    }

    @Override
    @Test
    public void allDataTypesTest() {

    }

    @Override
    public void referencesTest() {

    }

    @Override
    public void rateLimitTest() {
        // N/A
    }

}
