package com.syncari.connector.pardot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.*;

import com.darksci.pardot.api.request.list.ListQueryRequest;
import com.darksci.pardot.api.request.listmembership.ListMembershipQueryRequest;
import com.darksci.pardot.api.request.prospect.ProspectReadRequest;
import com.darksci.pardot.api.response.list.ListMembership;
import com.darksci.pardot.api.response.prospect.Prospect;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.EntityDataBatchIterator;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.utils.DateUtil;

import com.syncari.utils.Sleeper;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
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
@Ignore
public class PardotServiceTest extends AbstractConnectorTest implements DataServiceTest {

    @Autowired
    private PardotService pardotService;

    @Autowired
    DateUtil dateUtil;

    @Autowired
    ObjectMapper mapper;

    @Value("${pardot.username}")
    String pardotUsername;
    @Value("${pardot.password}")
    String pardotPassword;
    @Value("${pardot.sf.connectedapp.client.id}")
    String pardotSfConnectedAppClientId;
    @Value("${pardot.sf.connectedapp.client.secret}")
    String pardotSfConnectedAppClientSecret;
    @Value("${pardot.sf.connectedapp.token}")
    String pardotSfConnectedAppToken;
    @Value("${pardot.business.id}")
    String pardotBusinessId;

    private ConnectorInfo connector;

    @Before
    public void before() throws IOException {
        connector = createConnector();
    }

    private ConnectorInfo createConnector() {
        ConnectorInfo c = new ConnectorInfo();
        AuthConfig authConfig = new AuthConfig();
        authConfig.setUserName(pardotUsername);
        authConfig.setPassword(pardotPassword);
        authConfig.setClientId(pardotSfConnectedAppClientId);
        authConfig.setClientSecret(pardotSfConnectedAppClientSecret);
        authConfig.setRedirectUri("https://localhost/postman");
        authConfig.setAccessToken(pardotSfConnectedAppToken);
        c.setMetaConfig(Map.of("businessId", pardotBusinessId, PardotService.TIME_ZONE_ID, "America/Los_Angeles"));
        c.setAuthConfig(authConfig);
        c.setId(UUID.randomUUID().toString());
        return c;
    }

    @Override
    public ConnectorInfo getConnector() {
        if (connector == null) connector = createConnector();
        return connector;
    }
    @Override
    public MetadataService getMetadataService() { return pardotService; }
    @Override
    public AuthenticationService getAuthenticationService() { return pardotService; }
    @Override
    public CommonDataService getDataService() { return pardotService; }
    @Override
    public String getDescribeObject() {
        return "prospects";
    }

    @Test
    public void testConnectionTest() {
        verifyTestConnection();
    }

    @Test
    public void describeAllTest() {
        describeAll(null);
    }

    @Test
    public void describeTest() {
        describe(null, null);
    }

    @Test
    public void describeProspectDetailed() {
        DescribeRequest describeRequest = new DescribeRequest(connector, "prospects");
        Optional<EntitySchema> prospectSchema = pardotService.describe(describeRequest);
        assertTrue("Prospect".equals(prospectSchema.get().getDisplayName()));
        AttributeSchema id = prospectSchema.get().getAttributes().stream()
                .filter(v -> "id".equalsIgnoreCase(v.getApiName())).findAny().orElse(null);
        assertNotNull(id);
        assertTrue(id.isSystem());
        assertTrue(id.isIdField());
        AttributeSchema updatedAt = prospectSchema.get().getAttributes().stream()
                .filter(v -> "updated_at".equalsIgnoreCase(v.getApiName())).findAny().orElse(null);
        assertNotNull(updatedAt);
        assertTrue(updatedAt.isSystem());
        assertTrue(updatedAt.isWatermarkField());
        assertEquals("datetime", updatedAt.getDataType());
        AttributeSchema accountId = prospectSchema.get().getAttributes().stream()
                .filter(v -> "prospect_account_id".equalsIgnoreCase(v.getApiName())).findAny().orElse(null);
        assertNotNull(accountId);
        assertEquals("integer", accountId.getDataType());
        assertEquals("prospectAccount", accountId.getReferenceTo());
        assertEquals("id", accountId.getReferenceTargetField());
        AttributeSchema customFld = prospectSchema.get().getAttributes().stream()
                .filter(v -> "MyCustom_Field".equalsIgnoreCase(v.getApiName())).findAny().orElse(null);
        assertNotNull(customFld);
        assertFalse(customFld.isSystem());
        assertTrue(customFld.isCustom());
        assertEquals("Text", customFld.getDataType());
        customFld = prospectSchema.get().getAttributes().stream()
                .filter(v -> "full_name".equalsIgnoreCase(v.getApiName())).findAny().orElse(null);
        assertNotNull(customFld);
        assertFalse(customFld.isSystem());
        assertTrue(customFld.isCustom());
        assertEquals("full-name", customFld.getDisplayName());
        assertEquals("Text", customFld.getDataType());

        // Check custom field with name spaced.
        customFld = prospectSchema.get().getAttributes().stream()
                .filter(v -> "Custom_Field_Spaced".equalsIgnoreCase(v.getApiName())).findAny().orElse(null);
        assertNotNull(customFld);
        assertFalse(customFld.isSystem());
        assertTrue(customFld.isCustom());
        assertEquals("Custom_Field_Spaced", customFld.getApiName());
        assertEquals("Custom Field Spaced", customFld.getDisplayName());

        for (AttributeSchema attr : prospectSchema.get().getAttributes()) {
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

    @Test
    public void describeSome() {
        DescribeAllRequest describeRequest = new DescribeAllRequest(connector,
                new ArrayList<>(List.of("campaigns", "prospect-accounts","visits","visitors","visitor-activities")));
        List<EntitySchema> pardotSchema = pardotService.describeAll(describeRequest);
        assertNotNull(pardotSchema);
        assertEquals(5, pardotSchema.size());
    }

    @Test
    public void describeListAndMembership() {
        DescribeAllRequest describeRequest = new DescribeAllRequest(connector,
                new ArrayList<>(List.of("lists", "listMemberships")));
        List<EntitySchema> pardotSchema = pardotService.describeAll(describeRequest);
        assertNotNull(pardotSchema);
        assertEquals(2, pardotSchema.size());

        EntitySchema listSchema = pardotSchema.get(0);
        EntitySchema listMembershipSchema = pardotSchema.get(1);

        assertTrue("Lists".equals(listSchema.getDisplayName()));
        assertEquals(9, listSchema.getAttributes().size());
        AttributeSchema id = listSchema.getAttributes().stream().filter(v -> "id".equalsIgnoreCase(v.getApiName()))
                .findAny().orElse(null);
        assertNotNull(id);
        assertTrue(id.isSystem());
        assertTrue(id.isIdField());
        AttributeSchema updatedAt = listSchema.getAttributes().stream()
                .filter(v -> "updated_at".equalsIgnoreCase(v.getApiName())).findAny().orElse(null);
        assertNotNull(updatedAt);
        assertTrue(updatedAt.isSystem());
        assertTrue(updatedAt.isWatermarkField());

        assertTrue("List Memberships".equals(listMembershipSchema.getDisplayName()));
        assertEquals(6, listMembershipSchema.getAttributes().size());
        id = listMembershipSchema.getAttributes().stream().filter(v -> "id".equalsIgnoreCase(v.getApiName())).findAny()
                .orElse(null);
        assertNotNull(id);
        assertTrue(id.isSystem());
        assertTrue(id.isIdField());
        updatedAt = listMembershipSchema.getAttributes().stream()
                .filter(v -> "updated_at".equalsIgnoreCase(v.getApiName())).findAny().orElse(null);
        assertNotNull(updatedAt);
        assertTrue(updatedAt.isSystem());
        assertTrue(updatedAt.isWatermarkField());

        AttributeSchema listId = listMembershipSchema.getAttributes().stream()
                .filter(v -> "list_id".equalsIgnoreCase(v.getApiName())).findAny().orElse(null);
        assertNotNull(listId);
        assertEquals("integer", listId.getDataType());
        assertEquals("list", listId.getReferenceTo());
        assertEquals("id", listId.getReferenceTargetField());

        AttributeSchema prospectId = listMembershipSchema.getAttributes().stream()
                .filter(v -> "prospect_id".equalsIgnoreCase(v.getApiName())).findAny().orElse(null);
        assertNotNull(listId);
        assertEquals("integer", prospectId.getDataType());
        assertEquals("prospect", prospectId.getReferenceTo());
        assertEquals("id", prospectId.getReferenceTargetField());
    }

    @Override
    @Test
    public void getByWatermarkSinceEpoch() {
        verifyGetByWatermarkSinceEpoch("visitors");
    }

    @Override
    @Test
    public void getByWatermarkWithLimit() {
        verifyGetByWatermarkWithLimit("prospects", 2);
    }

    @Override
    @Test
    public void getByWatermarkRecent() {
        retryWithBackoff(() -> {
            verifyGetByWatermarkRecent("prospects");
        });
    }

    @Override
    @Test
    public void getByWatermarkResultsOrdered() {
        retryWithBackoff(() -> {
            verifyGetByWatermarkResultsOrdered("prospects");
        });
    }

    @Override
    @Test
    public void getDeletedByWatermark() {
        verifyGetDeletedByWatermark("prospects");
        verifyGetDeletedByWatermark("listMemberships");
    }

    @Override
    @Test
    public void createTest() {
        // batch test covers this
    }

    @Override
    public void updateTest() {
        // TODO Auto-generated method stub
        // batch update should test this
        
    }

    @Override
    public void deleteTest() {
        // TODO Auto-generated method stub
        // batch delete test the scenario
    }

    @Override
    @Test
    public void batchCreateTest() {
        //retryWithBackoff(() -> {
            String utStr = "ut-cust-create-" + System.currentTimeMillis();
            List<EntityData> data = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                data.add(createTestProspectByEmail(utStr + i + "@syncari.com"));
            }
            verifyCreateTest(utStr, "prospect", data);
        //});
    }

    @Override
    @Test
    public void batchUpdateTest() {
        // TODO Auto-generated method stub
        retryWithBackoff(() -> {
            String utStr = "ut-update-" + System.currentTimeMillis();
            List<EntityData> data = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                data.add(createTestProspectByEmail(utStr + i + "@syncari.com"));
            }
            verifyUpdateTest(utStr, "prospect", data, "Last_Name");
        });
    }

    @Override
    @Test
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

        String email = "testalldatatypes@syncari.com";
        try {
            SyncRequest request = getSyncRequest(PardotV4Client.PROSPECT);
            EntityData ed = getProspectEntity(email);

            SyncRequest createRequest = getSyncRequest(PardotV4Client.PROSPECT);
            request.setData(Map.of(connector.getId(), List.of(ed)));
            SyncResponse response = pardotService.create(request);
            assertTrue(response.isSuccess());
            String idCreated = response.getResults().get(0).getId();
            Optional<EntitySchema> prospectSchema = describe("prospect",null);

            EntityData edata = new EntityData();
            edata.setId(idCreated);
            SyncRequest getRequest = getSyncRequest(PardotV4Client.PROSPECT);
            getRequest.setData(Map.of(connector.getId(), List.of(edata)));

            List<EntityData> data = pardotService.getByIds(getRequest);
            int count = data.size();
            assertNotNull(data);
            assertTrue(count > 0);
            assertTrue(((Object)data.get(count-1).getValue("id")) instanceof  Integer);

            assertTrue(((Object)data.get(count-1).getValue("jobTitle")) instanceof  String);
            assertTrue(((Object)data.get(count-1).getValue("department")) instanceof  String);
            assertTrue(((Object)data.get(count-1).getValue("campaignId")) instanceof  Integer);
            assertTrue(((Object)data.get(count-1).getValue("salutation")) instanceof  String);
            assertTrue(((Object)data.get(count-1).getValue("firstName")) instanceof  String);
            assertTrue(((Object)data.get(count-1).getValue("lastName")) instanceof  String);
            assertTrue(((Object)data.get(count-1).getValue("email")) instanceof  String);
            assertTrue(((Object)data.get(count-1).getValue("company")) instanceof  String);
            assertTrue(((Object)data.get(count-1).getValue("website")) instanceof  String);
            assertTrue(((Object)data.get(count-1).getValue("country")) instanceof  String);
            assertTrue(((Object)data.get(count-1).getValue("addressOne")) instanceof  String);
            assertTrue(((Object)data.get(count-1).getValue("addressTwo")) instanceof  String);
            assertTrue(((Object)data.get(count-1).getValue("city")) instanceof  String);
            assertTrue(((Object)data.get(count-1).getValue("state")) instanceof  String);
            assertTrue(((Object)data.get(count-1).getValue("zip")) instanceof  Integer);
            assertTrue(((Object)data.get(count-1).getValue("phone")) instanceof  Integer);
            assertTrue(((Object)data.get(count-1).getValue("fax")) instanceof  Integer);
            assertTrue(((Object)data.get(count-1).getValue("source")) instanceof  String);
            assertTrue(((Object)data.get(count-1).getValue("annualRevenue")) instanceof  String);
            assertTrue(((Object)data.get(count-1).getValue("employees")) instanceof  String);
            assertTrue(((Object)data.get(count-1).getValue("industry")) instanceof  String);
            assertTrue(((Object)data.get(count-1).getValue("yearsInBusiness")) instanceof  String);
            assertTrue(((Object)data.get(count-1).getValue("comments")) instanceof  String);
            assertTrue(((Object)data.get(count-1).getValue("notes")) instanceof  String);
            assertTrue(((Object)data.get(count-1).getValue("score")) instanceof  Integer);
            assertTrue(((Object)data.get(count-1).getValue("recentInteraction")) instanceof  String);
            assertTrue(((Object)data.get(count-1).getValue("isDoNotEmail")) instanceof  Integer);
            assertTrue(((Object)data.get(count-1).getValue("isDoNotCall")) instanceof  Integer);
            assertTrue(((Object)data.get(count-1).getValue("createdAt")) instanceof ZonedDateTime);
            assertTrue(((Object)data.get(count-1).getValue("updatedAt")) instanceof  ZonedDateTime);
        } finally {
            cleanupProspectByEmail(email);
        }
    }

    @Override
    public void referencesTest() {
        // TODO Auto-generated method stub
        
    }

    @Override
    @Test
    public void rateLimitTest() {
        // TODO
    }

    @Override
    @Test
    public void getByIds() {
        String email1 = "getByIdsUT1@syncari.com";
        String email2 = "getByIdsUT2@syncari.com";
        try {
            EntityData ed1 = createTestProspectByEmail(email1);
            EntityData ed2 = createTestProspectByEmail(email2);
            SyncRequest request = getSyncRequest(PardotV4Client.PROSPECT);
            request.setData(Map.of(connector.getId(), List.of(ed1, ed2)));
            List<EntityData> getEntities = pardotService.getByIds(request);
            assertFalse(getEntities.isEmpty());
            assertEquals(2, getEntities.size());
            for (EntityData et : getEntities){
                assertNotNull(et.getLastModified());
                assertNotNull(et.getId());
                assertNotNull(et.getCreatedAt());
            }

        } finally {
            cleanupProspectByEmail(email1);
            cleanupProspectByEmail(email2);
        }
    }

    @Test
    public void getByWatermark() {
        EntitySchema prospectSchema = getEntitySchema(PardotV4Client.PROSPECT);

        SyncRequest request = new SyncRequest().Builder(connector, prospectSchema);
        request.setWatermark(new WatermarkInfo(-1, -1, true, 0));
        List<EntityData> entities = iterateRecords(pardotService.getByWatermark(request).getIterator());
        assertTrue(entities.isEmpty());
        request = new SyncRequest().Builder(connector, prospectSchema);
        request.setWatermark(new WatermarkInfo(Instant.now().minus(5, ChronoUnit.DAYS).toEpochMilli(), -1, true, 0));
        entities = iterateRecords(pardotService.getByWatermark(request).getIterator());
        assertFalse(entities.isEmpty());

        // Just try some random date in future which wont return results.
        long randomFutureTime = Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli();
        request = new SyncRequest().Builder(connector, prospectSchema);
        request.setWatermark(new WatermarkInfo(randomFutureTime, randomFutureTime + 10, true, 0));
        entities = iterateRecords(pardotService.getByWatermark(request).getIterator());
        assertTrue(entities.isEmpty());
    }

    @Test
    public void getByWatermarkPaginated() {
        Instant start = Instant.now().minus(2, ChronoUnit.DAYS);
        EntitySchema prospectSchema = getEntitySchema(PardotV4Client.PROSPECT);

        PardotService spy = spy(pardotService);
        doReturn(200).when(spy).getPageSize();

        SyncRequest request = new SyncRequest().Builder(connector, prospectSchema);
        request.setWatermark(new WatermarkInfo(start.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        List<EntityData> entities = iterateRecords(spy.getByWatermark(request).getIterator());
        assertFalse(entities.isEmpty());
        assertTrue(entities.size() > 0);
        
        for (EntityData et : entities){
            assertNotNull(et.getLastModified());
            assertNotNull(et.getId());
            assertNotNull(et.getCreatedAt());
        }
    }

    @Test
    public void getByWatermarkProspectAccount() {
        EntitySchema prospectAccountSchema = getEntitySchema(PardotV4Client.PROSPECT_ACCOUNT);

        SyncRequest request = new SyncRequest().Builder(connector, prospectAccountSchema);
        request.setWatermark(new WatermarkInfo(-1, -1, true, 0));
        List<EntityData> entities = iterateRecords(pardotService.getByWatermark(request).getIterator());
        assertTrue(entities.isEmpty());

        request = new SyncRequest().Builder(connector, prospectAccountSchema);
        request.setWatermark(new WatermarkInfo(0, -1, true, 0));
        entities = iterateRecords(pardotService.getByWatermark(request).getIterator());
        assertFalse(entities.isEmpty());

        // Just try some random date in future which wont return results.
        long randomFutureTime = Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli();
        request = new SyncRequest().Builder(connector, prospectAccountSchema);
        request.setWatermark(new WatermarkInfo(randomFutureTime, randomFutureTime + 10, true, 0));
        entities = iterateRecords(pardotService.getByWatermark(request).getIterator());
        assertTrue(entities.isEmpty());
    }

    @Test
    public void getByWatermarkCampaign() {
        EntitySchema campaignSchema = getEntitySchema(PardotV4Client.CAMPAIGN);

        SyncRequest request = new SyncRequest().Builder(connector, campaignSchema);
        request.setWatermark(new WatermarkInfo(-1, -1, true, 0));
        List<EntityData> entities = iterateRecords(pardotService.getByWatermark(request).getIterator());
        assertTrue(entities.isEmpty());

        long now = Instant.now().toEpochMilli();
        request = new SyncRequest().Builder(connector, campaignSchema);
        request.setWatermark(new WatermarkInfo(0, now, true, 0));
        entities = iterateRecords(pardotService.getByWatermark(request).getIterator());
        assertFalse(entities.isEmpty());
        // Pardot campaign API do not have support for updated_at,created_at. Here we force use the end watermark.
        assertEquals(now, entities.get(0).getLastModified());
        assertEquals(now, entities.get(0).getCreatedAt());

        // Just try some random date in future which wont return results.
        long randomFutureTime = Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli();
        request = new SyncRequest().Builder(connector, campaignSchema);
        request.setWatermark(new WatermarkInfo(randomFutureTime, randomFutureTime + 10, true, 0));
        entities = iterateRecords(pardotService.getByWatermark(request).getIterator());
        assertTrue(entities.isEmpty());
    }


    @Test
    public void getByWatermarkList() {
        EntitySchema listSchema = getEntitySchema(PardotV4Client.LIST);

        SyncRequest request = new SyncRequest().Builder(connector, listSchema);
        request.setWatermark(new WatermarkInfo(-1, -1, true, 0));
        List<EntityData> entities = iterateRecords(pardotService.getByWatermark(request).getIterator());
        assertTrue(entities.isEmpty());

        request = new SyncRequest().Builder(connector, listSchema);
        request.setWatermark(new WatermarkInfo(0, -1, true, 0));
        entities = iterateRecords(pardotService.getByWatermark(request).getIterator());
        assertFalse(entities.isEmpty());

        // Just try some random date in future which wont return results.
        long randomFutureTime = Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli();
        request = new SyncRequest().Builder(connector, listSchema);
        request.setWatermark(new WatermarkInfo(randomFutureTime, randomFutureTime + 10, true, 0));
        entities = iterateRecords(pardotService.getByWatermark(request).getIterator());
        assertTrue(entities.isEmpty());
    }

    @Test
    @Ignore("Incomplete and needs CRUD")
    public void getByWatermarkListMembership() {
        EntitySchema listMembershipSchema = getEntitySchema(PardotV4Client.LIST_MEMBERSHIP);

        SyncRequest request = new SyncRequest().Builder(connector, listMembershipSchema);
        request.setWatermark(new WatermarkInfo(-1, -1, true, 0));
        List<EntityData> entities = iterateRecords(pardotService.getByWatermark(request).getIterator());
        assertTrue(entities.isEmpty());

        request = new SyncRequest().Builder(connector, listMembershipSchema);
        request.setWatermark(new WatermarkInfo(0, -1, true, 0));
        entities = iterateRecords(pardotService.getByWatermark(request).getIterator());
        assertFalse(entities.isEmpty());

        // Just try some random date in future which wont return results.
        long randomFutureTime = Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli();
        request = new SyncRequest().Builder(connector, listMembershipSchema);
        request.setWatermark(new WatermarkInfo(randomFutureTime, randomFutureTime + 10, true, 0));
        entities = iterateRecords(pardotService.getByWatermark(request).getIterator());
        assertTrue(entities.isEmpty());
    }

    @Test
    public void getFirstCreatedTime() {
        SyncRequest request = new SyncRequest().Builder(connector, getEntitySchema(PardotV4Client.PROSPECT));
        long time = pardotService.getFirstCreatedTime(request);
        assertTrue(time > Instant.EPOCH.toEpochMilli());
    }

    @Test
    public void getFirstCreatedTimeForCampaign() {
        SyncRequest request = new SyncRequest().Builder(connector, getEntitySchema(PardotV4Client.CAMPAIGN));
        long time = pardotService.getFirstCreatedTime(request);
        assertEquals(Instant.EPOCH.toEpochMilli(), time);
    }

    @Test
    public void customFieldProspectTest() {
        String email = "customFieldProspectTest@syncari.com";
        try {
            Map<String, Object> customFieldValues = new HashMap<>();
            customFieldValues.put("MyCustom_Field", "My Custom Field Value");
            customFieldValues.put("Custom_Field_Spaced", "Custom Field Spaced Value");
            EntityData ed = createTestProspectByEmail(email, customFieldValues);
            SyncRequest request = getSyncRequest(PardotV4Client.PROSPECT);
            request.setData(Map.of(connector.getId(), List.of(ed)));
            List<EntityData> getEntities = pardotService.getByIds(request);
            assertFalse(getEntities.isEmpty());
            assertEquals(1, getEntities.size());
            assertEquals("My Custom Field Value", getEntities.get(0).getValueAsString("MyCustom_Field"));
            assertEquals("Custom Field Spaced Value", getEntities.get(0).getValueAsString("Custom_Field_Spaced"));
        } finally {
            cleanupProspectByEmail(email);
        }
    }

    @Test
    public void createProspect() {
        String email = "createProspectUT@syncari.com";
        try {
            SyncRequest request = getSyncRequest(PardotV4Client.PROSPECT);
            request.setData(Map.of(connector.getId(), List.of(getProspectEntity(email))));
            SyncResponse response = pardotService.create(request);
            assertTrue(response.isSuccess());
            assertEquals(1, response.getResults().size());
            assertNotNull(response.getResults().get(0).getId());
        } finally {
            cleanupProspectByEmail(email);
        }
    }

    @Test
    public void createProspectFailure() {
        String email = "createProspectUTFailure@syncari.com";
        try {
            SyncRequest request = getSyncRequest(PardotV4Client.PROSPECT);
            EntityData prospectED = getProspectEntity(email);
            prospectED.getValues().put("email", "");
            request.setData(Map.of(connector.getId(), List.of(prospectED)));
            try {
                SyncResponse response = pardotService.create(request);
                fail();
            } catch (Exception e) {
                assertTrue(e.getMessage().contains("Invalid prospect email address"));
            }
        } finally {
            cleanupProspectByEmail(email);
        }
    }

    @Test
    public void updateProspect() {
        String email = "updateProspectUT@syncari.com";
        try {
            SyncRequest request = getSyncRequest(PardotV4Client.PROSPECT);
            EntityData ed = createTestProspectByEmail(email);
            ed.addValue("score", 200);
            request.setData(Map.of(connector.getId(), List.of(ed)));
            SyncResponse updateResponse = pardotService.update(request);
            assertTrue(updateResponse.isSuccess());
            List<EntityData> entities = pardotService.getByIds(request);
            assertEquals(ed.getId(), String.valueOf(entities.get(0).getId()));
            assertEquals(200, Integer.parseInt(entities.get(0).getValue("score").toString()));
        } finally {
            cleanupProspectByEmail(email);
        }
    }

    @Test
    public void deleteProspectComesBackAsDeleted() {
        Instant begin = Instant.now();
        String email1 = "deleteProspectComesBackAsDeleted1@syncari.com";
        String email2 = "deleteProspectComesBackAsDeleted2@syncari.com";
        try {
            SyncRequest request = getSyncRequest(PardotV4Client.PROSPECT);
            EntityData ed1 = createTestProspectByEmail(email1);
            Thread.sleep(2000);
            Instant mid = Instant.now();
            // Delete after the mid watermark.
            request.setData(Map.of(connector.getId(), List.of(ed1)));
            SyncResponse deleteResponse = pardotService.delete(request);
            EntityData ed2 = createTestProspectByEmail(email2);
            assertTrue(deleteResponse.isSuccess());
            Thread.sleep(2000);

            // Now process in steps of watermark.
            // First watermark should not return anything since the first record was deleted post 'mid' time range.
            SyncRequest pRequest = new SyncRequest().Builder(connector, getEntitySchema(PardotV4Client.PROSPECT));
            pRequest.setWatermark(new WatermarkInfo(begin.toEpochMilli(), mid.toEpochMilli(), true, 0));
            List<EntityData> entities = iterateRecords(pardotService.getByWatermark(pRequest).getIterator());
            assertTrue(entities.size() == 0);

            // Set user preference to GMT to reproduce the issue where our production server mismatches the user's preference in Pardot.
            ConnectorInfo conn = connector;
            conn.setMetaConfig(Map.of(PardotService.TIME_ZONE_ID, "GMT", "businessId", pardotBusinessId));
            pRequest = new SyncRequest().Builder(conn, getEntitySchema(PardotV4Client.PROSPECT));
            pRequest.setWatermark(new WatermarkInfo(mid.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            entities = iterateRecords(pardotService.getByWatermark(pRequest).getIterator());
            assertTrue(entities.size() == 0);

            // Second watermark should return both new record and the deleted one 
            // since the first record was deleted post 'mid' time range.
            conn.setMetaConfig(Map.of(PardotService.TIME_ZONE_ID, "America/Los_Angeles", "businessId", pardotBusinessId));
            pRequest = new SyncRequest().Builder(conn, getEntitySchema(PardotV4Client.PROSPECT));
            pRequest.setWatermark(new WatermarkInfo(mid.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            entities = iterateRecords(pardotService.getDeletedByWatermark(pRequest).getIterator());
            entities.addAll(iterateRecords(pardotService.getByWatermark(pRequest).getIterator()));
            assertTrue(entities.size() >= 2);

            Optional<EntityData> ed = entities.stream().filter(x -> x.getId().equalsIgnoreCase(ed1.getId())).findFirst();
            assertTrue(ed.isPresent());
            assertTrue(ed.get().isDeleted());
        } catch (InterruptedException e) {
        } finally {
            cleanupProspectByEmail(email1);
            cleanupProspectByEmail(email2);
        }
    }

    @Test
    public void updateProspectFailures() {
        String email1 = "updateProspectUTFailures1@syncari.com";
        String email2 = "updateProspectUTFailures2@syncari.com";
        String email3 = "updateProspectUTFailures3@syncari.com";
        try {
            SyncRequest request = getSyncRequest(PardotV4Client.PROSPECT);
            EntityData ed = createTestProspectByEmail(email1);
            ed.addValue("score", 200);
            SyncResponse updateResponse = pardotService.update(request);
            assertFalse(updateResponse.isSuccess());
            assertEquals("No records passed for UPDATE for entity prospect.", updateResponse.getErrors().get(0));

            ed.getValues().put("email", "");
            request.setData(Map.of(connector.getId(), List.of(ed)));
            updateResponse = pardotService.update(request);
            assertTrue(updateResponse.isSuccess());

            EntityData ed2 = createTestProspectByEmail(email2);
            ed2.addValue("score", 200);
            EntityData ed3 = createTestProspectByEmail(email3);
            ed3.addValue("score", 200);
            ed.getValues().put("campaign_id", "random");
            request.setData(Map.of(connector.getId(), List.of(ed2, ed, ed3)));
            updateResponse = pardotService.update(request);
            assertTrue(updateResponse.isSuccess());

            // Assert success records.
            assertTrue(updateResponse.getResults().get(1).isSuccess());
            assertTrue(updateResponse.getResults().get(2).isSuccess());
        } finally {
            cleanupProspectByEmail(email1);
            cleanupProspectByEmail(email2);
            cleanupProspectByEmail(email3);
        }
    }

    private EntityData createTestProspectByEmail(String email) {
        return createTestProspectByEmail(email, null);
    }

    private EntityData createTestProspectByEmail(String email, Map<String, Object> customFieldValues) {
        SyncRequest request = getSyncRequest(PardotV4Client.PROSPECT);
        EntityData ed = getProspectEntity(email, customFieldValues);
        request.setData(Map.of(connector.getId(), List.of(ed)));
        SyncResponse response = pardotService.create(request);
        Optional<Prospect> prospect = getProspectByEmail(email);
        assertTrue(prospect.isPresent());
        // Update values
        ed.setId(String.valueOf(prospect.get().getId()));
        ed.getValues().put("id", ed.getId());
        ed.setSyncariEntityId(UUID.randomUUID().toString());
        return ed;
    }

    private EntityData getProspectEntity(String email) {
        return getProspectEntity(email, null);
    }

    private EntityData getAllAttribsProspect(String email){
        Map<String, Object> edMap = new HashMap<>();
        edMap.put("email", email);
        edMap.put("first_name", "Prospect Unit Test");
        edMap.put("last_name", "Syncari");
        edMap.put("campaign_id", 212);
        edMap.put("score", 200);
        edMap.put("last_name", "si");
        edMap.put("department", "test");
        edMap.put("city", "manager");
        edMap.put("salutation", "a");
        edMap.put("job_title", "manager");
        edMap.put("address_two", "manager");
        edMap.put("company", "sync");
        edMap.put("website", "sy.com");
        edMap.put("country", "manager");
        edMap.put("password", "test");
        edMap.put("address_one", "test");
        edMap.put("city", "test");
        edMap.put("state", "test");
        edMap.put("teritorry", "test");
        edMap.put("zip", "1234");
        edMap.put("phone", "12345");
        edMap.put("fax", "1234");
        edMap.put("source", "test");
        edMap.put("annual_revenue", "manager");
        edMap.put("employees", "manager");
        edMap.put("industry", "manager");
        edMap.put("years_in_business", "manager");
        edMap.put("comments", "manager");
        edMap.put("notes", "manager");
        edMap.put("last_activity_at", "manager");
        edMap.put("grade", "manager");
        edMap.put("recent_interaction", "manager");
        edMap.put("crm_lead_fid", "123");
        edMap.put("crm_contact_fid", "456");
        edMap.put("crm_account_fid", "123");
        edMap.put("crm_owner_fid", "123");
        edMap.put("crm_url", "manager");
        edMap.put("opted_out", "false");
        edMap.put("is_do_not_call", "true");
        edMap.put("is_do_not_email", "true");
        //edMap.put("crm_last_sync", "true");
        //edMap.put("created_at", "true");
        //edMap.put("updated_at", "manager");
        return new EntityData().withValues(edMap);
    }
    private EntityData getProspectEntity(String email, Map<String, Object> customFieldValues) {
        Map<String, Object> edMap = new HashMap<>();
        edMap.put("email", email);
        edMap.put("firstName", "Prospect Unit Test");
        edMap.put("lastName", "Syncari");
        edMap.put("addressOne", "");
        edMap.put("addressTwo", "");
        edMap.put("annualRevenue","");
        edMap.put("campaignId",212);
        edMap.put("city","");
        edMap.put("comments","");
        edMap.put("company","");
        edMap.put("country","");
        edMap.put("department","");
        edMap.put("employees","");
        edMap.put("fax","");
        edMap.put("industry","");
        edMap.put("isDoNotCall",false);
        edMap.put("isDoNotEmail",false);
        edMap.put("isReviewed",false);
        edMap.put("isStarred",false);
        edMap.put("jobTitle","");
        edMap.put("notes","");
        edMap.put("phone","");
        edMap.put("source","");
        edMap.put("prospectAccountId",0);
        edMap.put("salutation","");
        edMap.put("score",0);
        edMap.put("state","");
        edMap.put("territory","");
        edMap.put("website","");
        edMap.put("yearsInBusiness","");
        edMap.put("zip","");
        if (customFieldValues != null) {
            edMap.putAll(customFieldValues);
        }
        return new EntityData().withValues(edMap);
    }

    private void cleanupProspectByEmail(String email) {
        Optional<Prospect> pResponse = getProspectByEmail(email);
        while (pResponse.isPresent()) {
            SyncRequest request = getSyncRequest(PardotV4Client.PROSPECT);
            EntityData ed = new EntityData().withId(String.valueOf(pResponse.get().getId()))
                .addValue("id", String.valueOf(pResponse.get().getId()));
            request.addData(connector.getId(), ed);
            SyncResponse resp = pardotService.delete(request);
            pResponse = getProspectByEmail(email);
        }
    }

    private Optional<Prospect> getProspectByEmail(String email) {
        return pardotService.getPardotClient(connector).prospectRead(
                new ProspectReadRequest().selectByEmail(email));
    }
    
    private EntitySchema getEntitySchema(String entityName) {
        DescribeRequest describeRequest = new DescribeRequest(connector, entityName);
        Optional<EntitySchema> entitySchema = pardotService.describe(describeRequest);
        assertTrue(entitySchema.isPresent());
        return entitySchema.get();
    }

    private List<EntityData> iterateRecords(EntityDataBatchIterator iterator) {
        List<EntityData> records = Lists.newArrayList(); 
        while (iterator.hasNext()) {
            List<EntityData> batch = iterator.next();
            for (EntityData data: batch) {
                assertNotNull(data);
            }
            records.addAll(batch);
        }
        return records;
    }

    @Ignore("This is invalid and created a list with null name")
    @Test(expected = NonRetriableException.class)
    public void createListNoName() {
        SyncRequest request = getSyncRequest(PardotV4Client.LIST);
        request.setData(Map.of(connector.getId(), List.of(new EntityData())));
        SyncResponse response = pardotService.create(request);
        assertFalse(response.isSuccess());
    }

    @Test
    public void listCRUD() {
        String name = "listCRUD_test::" + Instant.now().toEpochMilli();
        try {
            EntityData ed = createAndGetListEd(name,false);
            ed = ed.addValue("isPublic", true);
            ed = ed.addValue("title", "A Public List");
            SyncRequest request = getSyncRequest(PardotV4Client.LIST);
            request.setData(Map.of(connector.getId(), List.of(ed)));
            SyncResponse response = pardotService.update(request);

            SyncRequest getRequest = getSyncRequest(PardotV4Client.LIST);
            getRequest.setData(Map.of(connector.getId(), List.of(ed)));
            List<EntityData> getEntities = pardotService.getByIds(getRequest);
            assertEquals(1, getEntities.size());
            // is_public is updateable according to Pardot V5 API.
            //assertFalse(Boolean.parseBoolean(getEntities.get(0).getValues().get("isPublic").toString()));
            assertEquals(true,getEntities.get(0).getValues().get("isPublic"));
            assertEquals("A Public List", getEntities.get(0).getValues().get("title").toString());
        } finally {
            cleanupListByName(name);
        }
    }

    private EntityData createAndGetListEd(String name, boolean isPublic) {
        Map<String, Object> edMap = new HashMap<>();
        edMap.put("name", name);
        edMap.put("isPublic", isPublic);
        EntityData ed = new EntityData().withValues(edMap);
        SyncRequest request = getSyncRequest(PardotV4Client.LIST);
        request.setData(Map.of(connector.getId(), List.of(ed)));
        SyncResponse response = pardotService.create(request);
        assertTrue(response.isSuccess());

        List<com.darksci.pardot.api.response.list.List> lResp = pardotService.getPardotClient(connector).listQuery(
            new ListQueryRequest().withName(name)).getLists();
        ed.setId(String.valueOf(lResp.get(0).getId()));
        SyncRequest getRequest = getSyncRequest(PardotV4Client.LIST);
        getRequest.setData(Map.of(connector.getId(), List.of(ed)));
        List<EntityData> getEntities = pardotService.getByIds(getRequest);
        assertEquals(1, getEntities.size());
        return getEntities.get(0);
    }

    private void cleanupListByName(String name) {
        List<com.darksci.pardot.api.response.list.List> lResp = pardotService.getPardotClient(connector).listQuery(
            new ListQueryRequest().withName(name)).getLists();
        SyncRequest request = getSyncRequest(PardotV4Client.LIST);
        for (com.darksci.pardot.api.response.list.List l: lResp) {
            EntityData ed = new EntityData().withId(String.valueOf(l.getId()))
                .addValue("id", String.valueOf(l.getId()));
            request.addData(connector.getId(), ed);
        }
        SyncResponse resp = pardotService.delete(request);
    }

    @Test
    public void listMembershipCRUD() {
        Instant begin = Instant.now().minusMillis(1000);
        String name = "createListMembershipTest::" + Instant.now().toEpochMilli();
        String email = "listMembershipCRUD@syncari.com";            
        long listId = 0;
        try {
            EntityData listEd = createAndGetListEd(name,false);
            listId = Long.valueOf(listEd.getId());
            EntityData prospectEd = createTestProspectByEmail(email);
        
            EntityData listMemEd = new EntityData().withValues(
                Map.of("listId", listId, "prospectId", prospectEd.getId())
            );

            // Now list memberships.
            SyncRequest request = getSyncRequest(PardotV4Client.LIST_MEMBERSHIP);
            request.setData(Map.of(connector.getId(), List.of(listMemEd)));
            SyncResponse response = pardotService.create(request);
            assertTrue(response.isSuccess());

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            List<ListMembership> lMemResp = pardotService.getPardotClient(connector).listMembershipQuery(
                new ListMembershipQueryRequest().withListId(listId)).getListMemberships();
            listMemEd.setId(String.valueOf(lMemResp.get(0).getId()));
            SyncRequest getRequest = getSyncRequest(PardotV4Client.LIST_MEMBERSHIP);
            getRequest.setData(Map.of(connector.getId(), List.of(listMemEd)));
            List<EntityData> getEntities = pardotService.getByIds(getRequest);
            assertEquals(1, getEntities.size());
            assertEquals(listId, Long.parseLong(getEntities.get(0).getValue("listId").toString()));
            assertEquals(false, Boolean.parseBoolean(getEntities.get(0).getValue("optedOut").toString()));

            request = getSyncRequest(PardotV4Client.LIST_MEMBERSHIP);
            request.setWatermark(new WatermarkInfo(begin.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            List<EntityData> entities = iterateRecords(pardotService.getByWatermark(request).getIterator());
            assertFalse(entities.isEmpty());

            listMemEd = getEntities.get(0);
            listMemEd = listMemEd.addValue("optedOut", 1);
            request.setData(Map.of(connector.getId(), List.of(listMemEd)));
            response = pardotService.update(request);

            getRequest.setData(Map.of(connector.getId(), List.of(listMemEd)));
            getEntities = pardotService.getByIds(getRequest);
            assertEquals(1, getEntities.size());
            assertEquals(listId, Long.parseLong(getEntities.get(0).getValue("listId").toString()));
            assertEquals(prospectEd.getId(), getEntities.get(0).getValue("prospectId").toString());
            assertEquals(true, Boolean.parseBoolean(getEntities.get(0).getValue("optedOut").toString()));
        } finally {
            cleanupListMembershipByListId(listId);
            cleanupListByName(name);
            cleanupProspectByEmail(email);
        }
    }

    @Test
    public void testDescribeAllRetries(){
        String entityName = "prospect";
        List<EntitySchema> entitySchemas = List.of(new EntitySchema("prospect", "Prospect"));
        PardotService mockService = spy(PardotService.class);
        PardotV4Client mockClient = mock(PardotV4Client.class);
        doReturn(entitySchemas).when(mockClient).getSeededEntitySchemas();
        doThrow(new RuntimeException("Connection timed out")).when(mockClient).getProspectCustomFields();
        doReturn(mockClient).when(mockService).getClient(any(ConnectorInfo.class));
        Sleeper sleeper = (minBackoffMillis, maxBackOffMillis) -> 1;
        doReturn(sleeper).when(mockService).getSleeper();
        DescribeAllRequest request = new DescribeAllRequest(getConnector(), List.of(entityName));
        try {
            mockService.describeAll(request);
        } catch (RetriableException e) {
            String code = e.getErrorCode();
            assertTrue(e.getStatusCode().equals(ErrorCodes.TOO_MANY_REQUESTS.name()));
        }
        verify(mockClient, times(5)).getProspectCustomFields();
    }

    private void cleanupListMembershipByListId(long id) {
        List<ListMembership> lResp = pardotService.getPardotClient(connector).listMembershipQuery(
            new ListMembershipQueryRequest().withListId(id)).getListMemberships();
        SyncRequest request = getSyncRequest(PardotV4Client.LIST_MEMBERSHIP);
        for (ListMembership l: lResp) {
            EntityData ed = new EntityData().withId(String.valueOf(l.getId()))
                .addValue("id", String.valueOf(l.getId()));
            request.addData(connector.getId(), ed);
        }
        SyncResponse resp = pardotService.delete(request);
    }
}