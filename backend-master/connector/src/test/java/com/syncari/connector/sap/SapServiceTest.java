package com.syncari.connector.sap;

import com.syncari.connector.AbstractConnectorTest;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.DataServiceTest;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
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

import java.time.Instant;
import java.util.*;

import static org.junit.Assert.*;

@Slf4j
@RunWith(SpringRunner.class)
@AutoConfigureDataMongo
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
@TestPropertySource("classpath:test_application.properties")
@Ignore
public class SapServiceTest extends AbstractConnectorTest implements DataServiceTest {

    @Autowired
    SapService sapService;

    private ConnectorInfo connector;

    @Before
    public void setup() {
        connector = createConnector();
    }

    @Override
    public List<String> skipIdFieldVerificationObjects() {
        List<String> entityNames = new ArrayList<>();
        entityNames.add("ServiceRequestProcessingTypeCollection");
        entityNames.add("ServiceRequestUserLifeCycleStatusCollection");
        entityNames.add("CodeListCollection");
        entityNames.add("ContextualCodeListCollection");
        return entityNames;
    }

    @Override
    public ConnectorInfo getConnector() {
        if (connector == null) connector = createConnector();
        return connector;
    }

    @Override
    public AuthenticationService getAuthenticationService() {
        return sapService;
    }

    @Override
    public MetadataService getMetadataService() {
        return sapService;
    }

    @Override
    public CommonDataService getDataService() {
        return sapService;
    }

    @Override
    public String getDescribeObject() {
        return null;
    }

    @Test
    @Override
    public void testConnectionTest() {
        TestConnectionResponse response = sapService.testConnection(getConnector(), List.of());
        assertTrue(response.isSuccess());
    }

    @Test
    @Override
    public void describeAllTest() {
        describeAll(null);
    }

    @Override
    @Test
    public void describeTest() {
        Optional<EntitySchema> schema = describe("OpportunityCollection", null);
        assertEquals("OpportunityCollection", schema.get().getApiName());
        assertEquals(115, schema.get().getAttributes().size());

        // id field
        assertEquals("ObjectID", schema.get().getIdField().getApiName().toString());
        assertTrue (schema.get().getField("ObjectID").get().isIdField());
        assertTrue (schema.get().getField("ObjectID").get().isUnique());
        assertFalse (schema.get().getField("ObjectID").get().isNillable());
        assertFalse (schema.get().getField("ObjectID").get().isUpdateable());
        assertFalse (schema.get().getField("ObjectID").get().isWatermarkField());

        // watermark field
        assertEquals("EntityLastChangedOn", schema.get().getWatermarkField().getApiName().toString());
        assertFalse (schema.get().getField("EntityLastChangedOn").get().isIdField());
        assertFalse (schema.get().getField("EntityLastChangedOn").get().isUnique());
        assertFalse (schema.get().getField("EntityLastChangedOn").get().isNillable());
        assertFalse (schema.get().getField("EntityLastChangedOn").get().isUpdateable());

        // datatypes
        assertEquals ("string", schema.get().getField("ObjectID").get().getDataType());
        assertEquals ("timestamp", schema.get().getField("EntityLastChangedOn").get().getDataType());
        assertEquals ("boolean", schema.get().getField("AutoCreateContacts").get().getDataType());
        assertEquals ("decimal", schema.get().getField("DealScore").get().getDataType());
        assertEquals ("int", schema.get().getField("HugRank").get().getDataType());

    }

    @Override
    @Test
    public void getByWatermarkSinceEpoch() {
        verifyGetByWatermarkSinceEpoch("OpportunityCollection");
    }

    @Override
    @Test
    public void getByWatermarkRecent() {
        Optional<EntitySchema> entitySchema = describe("OpportunityCollection", null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        syncRequest.setWatermark(watermark);

        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
        assertTrue(byWatermark.getIterator().hasNext());
        List<EntityData> data = byWatermark.getIterator().next();
        assertNotNull(data);
        assertTrue(data.size() > 0);
        int count1 = data.size();
        long lastmodified1 = data.get(0).getLastModified();

        System.out.println(String.format("The lastmodified of the first record: %s; lastmodified of last record: %s ",
                lastmodified1, data.get(count1-1).getLastModified()));

        watermark = new WatermarkInfo(1655905148765L, Instant.now().toEpochMilli(), false, 0);
        syncRequest.setWatermark(watermark);
        byWatermark = getDataService().getByWatermark(syncRequest);
        assertTrue(byWatermark.getIterator().hasNext());
        data = byWatermark.getIterator().next();
        assertNotNull(data);
        assertTrue(data.size() > 0);
        long lastmodified2 = data.get(0).getLastModified();
        // getByWatermark works
        assertTrue(data.size() >= 0);
        // watermark moving works, we got less records.
        assertTrue(lastmodified2 >= lastmodified1);
    }

    @Override
    @Test
    public void getByWatermarkWithLimit() {
        verifyGetByWatermarkWithLimit("OpportunityCollection", 2);
        verifyGetByWatermarkWithLimit("LeadCollection", 2);
    }

    @Override
    @Test
    public void getByWatermarkResultsOrdered() {
        verifyGetByWatermarkResultsOrdered("OpportunityCollection");
        verifyGetByWatermarkResultsOrdered("LeadCollection");
        verifyGetByWatermarkResultsOrdered("CorporateAccountCollection");
        verifyGetByWatermarkResultsOrdered("BusinessUserCollection");
        //verifyGetByWatermarkResultsOrdered("BusinessAttributeCharacteristicCollection");
    }

    @Override
    @Test
    public void getByIds() {
        verifyGetByIds("CorporateAccountCollection");
        verifyGetByIds("BusinessUserCollection", 1);
        verifyGetByIds("LeadCollection");
        verifyGetByIds("ContactCollection", 1);
        verifyGetByIds("OpportunityCollection");
    }

    @Test
    public void getByIdNotFoundTest() {
        EntitySchema schema = describe("CorporateAccountCollection", null).get();
        SyncRequest getByIdRequest = new SyncRequest().Builder(getConnector(), schema);
        EntityData account = new EntityData("CorporateAccountCollection");
        account.setId("123434345345");
        getByIdRequest.addData(getByIdRequest.getConnector().getId(), account);
        List<EntityData> data = sapService.getByIds(getByIdRequest);
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
            edMap.put("Company", "This is test company " + i);
            edMap.put("ContactLastName", "This is test contact last name " + i);
            data.add(edMap);
        }
        verifyCreateTestWithValues(utStr, "LeadCollection", data);
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
            edMap.put("Company", "This is test company " + i);
            edMap.put("ContactLastName", "This is test contact last name " + i);
            data.add(edMap);
        }
        verifyUpdateTestWithValues(utStr, "LeadCollection", data, "Company");
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

    @Override
    public void mixedBatchCreateFailuresTest() {

    }

    @Override
    public void mixedBatchUpdateFailuresTest() {

    }

    @Override
    public void mixedBatchDeleteFailuresTest() {

    }

    @Override
    public void allDataTypesTest() {

    }

    @Override
    public void referencesTest() {

    }

    @Override
    public void rateLimitTest() {

    }


    private List<String> doLeadCreate(){
        List<String> ids = new ArrayList<>();
        SyncRequest request = getSyncRequest("LeadCollection");
        request.setPageSize(2);

        List<EntityData> data = new ArrayList<>();

        data.add(new EntityData("LeadCollection").addValue("Name", "Test Name1")
                .addValue("ContactLastName", "Test Last Name1").addValue("Company", "Test Company1"));
        data.add(new EntityData("LeadCollection").addValue("Name", "Test Name2")
                .addValue("ContactLastName", "Test Last Name2").addValue("Company", "Test Company2"));

       request.setData(Map.of(getConnector().getId(), data));
       SyncResponse response = getDataService().create(request);
       response.getResults().forEach(x -> {
          ids.add(x.getId());
       });

        return ids;
    }

    private void doLeadDelete(List<String> ids) {
        List<EntityData> dataForDelete = new ArrayList<>();
        SyncRequest request = getSyncRequest("LeadCollection");
        ids.forEach(x -> dataForDelete.add(new EntityData(request.getEntityName()).setId(x)));
        if (!CollectionUtils.isEmpty(ids)) {
            request.setData(Map.of(request.getConnector().getId(), dataForDelete));
            getDataService().delete(request);
        }
    }

    private SyncRequest getRequest(String e) {
        EntitySchema schema = sapService.describe(new DescribeRequest(getConnector(), e)).get();
        return new SyncRequest().Builder(getConnector(), schema);
    }

    private ConnectorInfo createConnector() {
        ConnectorInfo sapConnector = new ConnectorInfo();
        AuthConfig authConfig = new AuthConfig();
        sapConnector.setEndpoint("https://my359768.crm.ondemand.com");
        authConfig.addHeader("AuthType", "UserPassword");
        authConfig.setUserName("CASHMANJASON7000000");
        authConfig.setPassword(System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME"));
        authConfig.setEndpoint("https://my359768.crm.ondemand.com");
        sapConnector.setAuthConfig(authConfig);
        UUID uuid = UUID.randomUUID();
        sapConnector.setId(uuid.toString());
        return sapConnector;
    }

}
