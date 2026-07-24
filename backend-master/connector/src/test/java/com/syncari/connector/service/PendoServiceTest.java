package com.syncari.connector.service;

import com.syncari.connector.AbstractConnectorTest;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.DataServiceTest;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@AutoConfigureDataMongo
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
public class PendoServiceTest extends AbstractConnectorTest implements DataServiceTest {

    private static final String PENDO_INTEGRATION_TOKEN = "test_value_35";

    private ConnectorInfo connector;

    @Autowired
    private PendoService pendoService;

    @Override
    public ConnectorInfo getConnector() {
        if (connector == null) connector = createConnector();
        return connector;
    }

    private ConnectorInfo createConnector(){
        ConnectorInfo connector = new ConnectorInfo();
        connector.setId("1234");
        connector.setName("pendoConnector");
        AuthConfig authConfig = new AuthConfig();
        authConfig.setAccessToken(PENDO_INTEGRATION_TOKEN);
        connector.setAuthConfig(authConfig);
        return connector;
    }

    private ConnectorInfo createTestConnector(){
        ConnectorInfo connector = new ConnectorInfo();
        connector.setId("1234");
        connector.setName("pendoConnector");
        AuthConfig authConfig = new AuthConfig();
        authConfig.setAccessToken(PENDO_INTEGRATION_TOKEN);
        connector.setAuthConfig(authConfig);
        return connector;
    }

    @Test
    public void getAccountById(){
        Optional<EntitySchema> entitySchema = describe("account", null);

        SyncRequest getByIdRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        EntityData ed = new EntityData("account").setId("account-id-fbd6a555-7717-4239-bc44-36ddd1bb0f85");
        getByIdRequest.addData(getConnector().getId(), ed);
        List<EntityData> data = getDataService().getByIds(getByIdRequest);
        assertNotNull(data);
        assertEquals(1, data.size());

        ed = new EntityData("account").setId("PendoAccount0004");
        getByIdRequest.clearData();
        getByIdRequest.addData(getConnector().getId(), ed);
        data = getDataService().getByIds(getByIdRequest);
        assertNotNull(data);
        assertEquals(0, data.size());
    }

    @Test
    public void getVisitorById(){
        Optional<EntitySchema> entitySchema = describe("visitor", null);

        SyncRequest getByIdRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        EntityData ed = new EntityData("visitor").setId("visitor-id-e2949aa2-df42-4619-9993-7d3a757124ef");
        getByIdRequest.addData(getConnector().getId(), ed);
        List<EntityData> data = getDataService().getByIds(getByIdRequest);
        assertNotNull(data);
        assertEquals(1, data.size());

        ed = new EntityData("visitor").setId("testVisitor1");
        getByIdRequest.clearData();
        getByIdRequest.addData(getConnector().getId(), ed);
        data = getDataService().getByIds(getByIdRequest);
        assertNotNull(data);
        assertEquals(1, data.size());

        ed = new EntityData("visitor").setId("PendoVistor0003");
        getByIdRequest.clearData();
        getByIdRequest.addData(getConnector().getId(), ed);
        data = getDataService().getByIds(getByIdRequest);
        assertNotNull(data);
        assertEquals(0, data.size());
    }

    @Test
    public void getGuideById(){
        Optional<EntitySchema> entitySchema = describe("guide", null);

        SyncRequest getByIdRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        EntityData ed = new EntityData("guide").setId("AZ3B91ONpOreLrlnzG_E1vkk8NA");
        getByIdRequest.addData(getConnector().getId(), ed);
        List<EntityData> data = getDataService().getByIds(getByIdRequest);
        assertNotNull(data);
        assertEquals(1, data.size());

        ed = new EntityData("guide").setId("nonExistentGuideId");
        getByIdRequest.clearData();
        getByIdRequest.addData(getConnector().getId(), ed);
        data = getDataService().getByIds(getByIdRequest);
        assertNotNull(data);
        assertEquals(0, data.size());
    }

    @Test
    public void getPageById(){
        Optional<EntitySchema> entitySchema = describe("page", null);

        SyncRequest getByIdRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        EntityData ed = new EntityData("page").setId("GUj96B5YYMAm9ozLVwuHGgN9Nbk");
        getByIdRequest.addData(getConnector().getId(), ed);
        List<EntityData> data = getDataService().getByIds(getByIdRequest);
        assertNotNull(data);
        assertEquals(1, data.size());

        ed = new EntityData("page").setId("nonExistentPageId");
        getByIdRequest.clearData();
        getByIdRequest.addData(getConnector().getId(), ed);
        data = getDataService().getByIds(getByIdRequest);
        assertNotNull(data);
        assertEquals(0, data.size());
    }

    @Test
    public void createUpdateVisitorMetadata() {
        Optional<EntitySchema> entitySchema = describe("visitor", null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        EntityData ed = new EntityData("visitor").setId("visitor-id-" + UUID.randomUUID())
                .addValue("custom_test_metadata_string_field", "test_metadata_value")
                .addValue("custom_test_metadata_int_field", 123)
                .addValue("custom_test_metadata_float_field", 100.5)
                .addValue("custom_test_metadata_date_field", ZonedDateTime.parse("2011-12-03T00:00:05Z", DateTimeFormatter.ISO_DATE_TIME))
                .addValue("custom_test_metadata_boolean_field", false);
        syncRequest.addData(getConnector().getId(), ed);
        SyncResponse syncResponse = getDataService().create(syncRequest);
        assertTrue(syncResponse.isSuccess());
        ed.addValue("custom_test_metadata_string_field", "test_metadata_value_updated");
        syncResponse = getDataService().update(syncRequest);
        assertTrue(syncResponse.isSuccess());
        List<EntityData> data = getDataService().getByIds(syncRequest);
        assertNotNull(data);
        assertEquals(1, data.size());
        assertTrue(data.get(0).getValueAsString("custom_test_metadata_string_field").equalsIgnoreCase("test_metadata_value_updated"));
        assertTrue(data.get(0).getValue("custom_test_metadata_int_field").equals(123));
        assertTrue(data.get(0).getValue("custom_test_metadata_float_field").equals(100.5));
        assertTrue(data.get(0).getValue("custom_test_metadata_date_field").equals(1322870405000L));
        assertTrue(data.get(0).getValue("custom_test_metadata_boolean_field").equals(false));
    }

    @Test
    public void createUpdateAccountMetadata() {
        Optional<EntitySchema> entitySchema = describe("account", null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        EntityData ed = new EntityData("account").setId("account-id-" + UUID.randomUUID())
                .addValue("custom_test_metadata_string_field", "test_metadata_value")
                .addValue("custom_test_metadata_int_field", 123)
                .addValue("custom_test_metadata_float_field", 100.5)
                .addValue("custom_test_metadata_date_field", ZonedDateTime.parse("2011-12-03T00:00:05Z", DateTimeFormatter.ISO_DATE_TIME))
                .addValue("custom_test_metadata_boolean_field", false);
        EntityData ed2 = new EntityData("account").setId("account-id-" + UUID.randomUUID())
                .addValue("custom_test_metadata_string_field", "test_metadata_value")
                .addValue("custom_test_metadata_int_field", 123)
                .addValue("custom_test_metadata_float_field", 100.5)
                .addValue("custom_test_metadata_date_field", ZonedDateTime.parse("2011-12-03T00:00:05Z", DateTimeFormatter.ISO_DATE_TIME))
                .addValue("custom_test_metadata_boolean_field", false);
        syncRequest.addData(getConnector().getId(), ed);
        syncRequest.addData(getConnector().getId(), ed2);
        SyncResponse syncResponse = getDataService().create(syncRequest);
        assertTrue(syncResponse.isSuccess());
        ed.addValue("custom_test_metadata_string_field", "test_metadata_value_updated");
        syncResponse = getDataService().update(syncRequest);
        assertTrue(syncResponse.isSuccess());
        List<EntityData> data = getDataService().getByIds(syncRequest.setData(Map.of(getConnector().getId(), List.of(ed))));
        assertNotNull(data);
        assertEquals(1, data.size());
        assertTrue(data.get(0).getValueAsString("custom_test_metadata_string_field").equalsIgnoreCase("test_metadata_value_updated"));
        assertTrue(data.get(0).getValue("custom_test_metadata_int_field").equals(123));
        assertTrue(data.get(0).getValue("custom_test_metadata_float_field").equals(100.5));
        assertTrue(data.get(0).getValue("custom_test_metadata_date_field").equals(1322870405000L));
        assertTrue(data.get(0).getValue("custom_test_metadata_boolean_field").equals(false));
        data = getDataService().getByIds(syncRequest.setData(Map.of(getConnector().getId(), List.of(ed2))));
        assertNotNull(data);
        assertEquals(1, data.size());
        assertTrue(data.get(0).getValueAsString("custom_test_metadata_string_field").equalsIgnoreCase("test_metadata_value"));
    }

    @Override
    public AuthenticationService getAuthenticationService() {
        return pendoService;
    }

    @Override
    public MetadataService getMetadataService() {
        return pendoService;
    }

    @Override
    public CommonDataService getDataService() {
        return pendoService;
    }

    @Override
    public String getDescribeObject() {
        return null;
    }

    @Override
    @Test
    public void testConnectionTest() {
        verifyTestConnection();
    }

    @Override
    @Test
    public void describeAllTest() {
        DescribeAllRequest request = new DescribeAllRequest(getConnector(), List.of());
        List<EntitySchema> entities = pendoService.describeAll(request);
        assertEquals(6, entities.size());
    }

    @Override
    @Test
    public void describeTest() {
        describe("account", null);
        describe("visitor", null);
        describe("guide", null);
        describe("page", null);
    }

    @Override
    @Test
    public void getByWatermarkSinceEpoch() {
        verifyGetByWatermarkSinceEpoch("account");
        verifyGetByWatermarkSinceEpoch("visitor");
        verifyGetByWatermarkSinceEpoch("guide");
        verifyGetByWatermarkSinceEpoch("page");
        verifyGetByWatermarkSinceEpoch("nps");
    }

    @Override
    @Test
    public void getByWatermarkRecent() {
        verifyGetByWatermarkRecent("account");
        verifyGetByWatermarkRecent("visitor");
        verifyGetByWatermarkRecent("guide");
        verifyGetByWatermarkRecent("page");
        verifyGetByWatermarkRecent("nps");
    }

    @Override
    @Test
    public void getByWatermarkWithLimit() {
        verifyGetByWatermarkWithLimit("account", 1);
        verifyGetByWatermarkWithLimit("visitor", 1);
        verifyGetByWatermarkWithLimit("guide", 1);
        verifyGetByWatermarkWithLimit("page", 1);
        verifyGetByWatermarkWithLimit("nps", 1);
    }

    @Override
    public void getByWatermarkResultsOrdered() {

    }

    @Override
    @Test
    public void getByIds() {
        verifyGetByIds("nps");
    }

    @Override
    public void getDeletedByWatermark() {

    }

    @Override
    public void createTest() {

    }

    @Override
    public void updateTest() {

    }

    @Override
    public void deleteTest() {

    }

    @Override
    public void batchCreateTest() {

    }

    @Override
    public void batchUpdateTest() {

    }

    @Override
    public void batchDeleteTest() {

    }

    @Override
    public void createCustomObjectTest() {

    }

    @Override
    public void updateCustomObjectTest() {

    }

    @Override
    public void deleteCustomObjectTest() {

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

    @Test
    public void getAllVisitors() {
        ConnectorInfo connectorInfo = createTestConnector();
        DescribeRequest describeRequest = new DescribeRequest(connectorInfo, "visitorRaw");
        Optional<EntitySchema> entitySchema = pendoService.describe(describeRequest);
        SyncRequest syncRequest = new SyncRequest().Builder(createTestConnector(), entitySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(Instant.now().minus(14, ChronoUnit.DAYS).toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        watermark.setResync(true);
        syncRequest.setWatermark(watermark);

        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
        List<EntityData> results = new ArrayList<>();
        while (byWatermark.getIterator().hasNext()) {
            List<EntityData> data = byWatermark.getIterator().next();
            results.addAll(data);
        }
        assertFalse(results.isEmpty());
    }
}
