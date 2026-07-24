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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@AutoConfigureDataMongo
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
@Ignore
public class PendoFeedbackServiceTest extends AbstractConnectorTest implements DataServiceTest {

    private static final String PENDO_INTEGRATION_TOKEN = "test_value_39";

    private ConnectorInfo connector;

    @Autowired
    private PendoFeedbackService pendoService;

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

    @Test
    public void getAccountById(){
        Optional<EntitySchema> entitySchema = describe("account", null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        syncRequest.setWatermark(watermark);

        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
        assertTrue(byWatermark.getIterator().hasNext());
        List<EntityData> data = byWatermark.getIterator().next();
        assertNotNull(data);
        assertTrue(data.size() > 0);

        SyncRequest getByIdRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        EntityData ed = new EntityData("account").setId(data.get(0).getId());
        getByIdRequest.addData(getConnector().getId(), ed);
        data = getDataService().getByIds(getByIdRequest);
        assertNotNull(data);
        assertEquals(1, data.size());
        assertNotNull(data.get(0).getId());
        assertNotNull(data.get(0).getLastModified());
        assertNotNull(data.get(0).getValue("last_seen"));
        assertNotNull(data.get(0).getValue("status"));
        assertNotNull(data.get(0).getValue("external_id"));
        assertNotNull(data.get(0).getValue("churned"));
        assertNotNull(data.get(0).getValue("vendor_id"));
        assertNotNull(data.get(0).getValue("created_at"));
        assertNotNull(data.get(0).getValue("updated_at"));
        assertNotNull(data.get(0).getValue("source"));
        assertNotNull(data.get(0).getValue("type"));
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
        pendoService.testConnection(getConnector(), List.of());
    }

    @Override
    @Test
    public void describeAllTest() {
        DescribeAllRequest request = new DescribeAllRequest(getConnector(), List.of());
        List<EntitySchema> entities = pendoService.describeAll(request);
        assertEquals(3, entities.size());
    }

    @Override
    @Test
    public void describeTest() {
        describe("account", null);
        describe("vote", null);
        describe("feature", null);
    }

    @Override
    @Test
    public void getByWatermarkSinceEpoch() {
        verifyGetByWatermarkSinceEpoch("account");
        List<EntityData> data = verifyGetByWatermarkSinceEpoch("vote");
        assertNotNull(data.get(0).getValue("feature_id"));
        assertNotNull(data.get(0).getValue("user_id"));
        assertNotNull(data.get(0).getValue("quantity"));
        assertNotNull(data.get(0).getValue("created_at"));
        assertNotNull(data.get(0).getValue("updated_at"));
        verifyGetByWatermarkSinceEpoch("feature");
    }

    @Override
    @Test
    @Ignore
    public void getByWatermarkRecent() {
        verifyGetByWatermarkRecent("account");
        List<EntityData> data = verifyGetByWatermarkRecent("vote");
        assertNotNull(data.get(0).getId());
        assertNotNull(data.get(0).getValue("feature_id"));
        assertNotNull(data.get(0).getValue("user_id"));
        assertNotNull(data.get(0).getValue("quantity"));
        assertNotNull(data.get(0).getValue("created_at"));
        assertNotNull(data.get(0).getValue("updated_at"));
        verifyGetByWatermarkRecent("feature");
    }

    @Override
    @Test
    public void getByWatermarkWithLimit() {
        verifyGetByWatermarkWithLimit("account", 1);
        verifyGetByWatermarkWithLimit("vote", 1);
        verifyGetByWatermarkWithLimit("feature", 1);
    }

    @Override
    public void getByWatermarkResultsOrdered() {

    }

    @Override
    @Test
    public void getByIds() {
        verifyGetByIds("account");
        verifyGetByIds("feature");
        try {
            verifyGetByIds("vote");
            fail();
        } catch (Exception e) {
            assertEquals("Get by ids not supported for vote", e.getMessage());
        }
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
}
