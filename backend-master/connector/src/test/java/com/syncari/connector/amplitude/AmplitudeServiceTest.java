package com.syncari.connector.amplitude;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import com.syncari.connector.*;
import org.apache.commons.lang3.StringUtils;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import com.sforce.ws.ConnectionException;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.BatchJob;
import com.syncari.connector.data.DescribeRequest;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.FetchResponse;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.SyncResponse;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.connector.data.iterator.EntityDataBatchIterator;
import com.syncari.utils.Retry;

@RunWith(SpringRunner.class)
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
public class AmplitudeServiceTest {
    private static final String API_KEY = "test_value_43";
    private static final String SECRET_KEY = "test_value_44";
    @Autowired
    AmplitudeService service;

    @Rule
    public RetryRule retryRule = new RetryRule();

    @Test
    public void testConnection() throws ConnectionException {
        ConnectorInfo connector = new ConnectorInfo();
        AuthConfig authConfig = new AuthConfig();
        connector.setAuthConfig(authConfig);
        TestConnectionResponse testConnection = service.testConnection(connector, List.of());
        assertFalse(testConnection.isSuccess());
        assertEquals("Api Key is required", testConnection.getMessage());

        connector.getAuthConfig().setToken(API_KEY);
        testConnection = service.testConnection(connector, List.of());
        assertFalse(testConnection.isSuccess());
        assertEquals("Secret Key is required", testConnection.getMessage());

        connector.getAuthConfig().setClientSecret(SECRET_KEY);
        testConnection = service.testConnection(connector, List.of());
        assertTrue(testConnection.isSuccess());
    }

    @Test
    public void createEvents() throws ConnectionException {
        SyncResponse response = createEvent("test@test.com", "Interesting Moment", "6666");
        assertTrue(response.isSuccess());
        assertEquals(1, response.getResults().size());
        assertNotNull(response.getResults().get(0).getId());
        assertEquals("6666", response.getResults().get(0).getSyncariId());
    }

    @Test
    public void createMultiEvents() {
        SyncResponse response = createEvent("user1,user2,user3", "Interesting Moment", "6666");
        assertTrue(response.isSuccess());
        assertEquals(1, response.getResults().size());
        assertNotNull(response.getResults().get(0).getId());
        assertEquals(3,response.getResults().get(0).getId().split(",").length);
        assertEquals("6666", response.getResults().get(0).getSyncariId());
    }

    public SyncResponse createEvent(String userId, String eventType, String syncariRecordId) {
        List<String> userIds = Arrays.asList(userId.split(","));
        ConnectorInfo connector = new ConnectorInfo();
        AuthConfig authConfig = new AuthConfig().setToken(API_KEY).setClientSecret(SECRET_KEY);
        connector.setAuthConfig(authConfig);
        DescribeRequest describeReq = new DescribeRequest(connector, "event");
        EntitySchema entitySchema = service.describe(describeReq).get();
        entitySchema.getField(AmplitudeSeed.USER_PROPERTIES).get().setId("123");
        entitySchema.getField(AmplitudeSeed.EVENT_PROPERTIES).get().setId("222");
        entitySchema.addField(new AttributeSchema("Age", "integer").setParentAttributeId("123"));
        entitySchema.addField(new AttributeSchema("Address", "string").setParentAttributeId("123"));
        entitySchema.addField(new AttributeSchema("company_id", "string").setParentAttributeId("234"));
        entitySchema.addField(new AttributeSchema("event_loc", "string").setParentAttributeId("222"));
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        EntityData entityData = new EntityData("user");
        entityData.setSyncariEntityId(syncariRecordId);
        entityData.addValue("user_id", userIds);
        entityData.addValue("event_type", eventType);
        entityData.addValue("Age", 10);
        entityData.addValue("Address", "221b Baker Street, Wonderland");
        entityData.addValue("event_loc", "221b Baker Street, Wonderland");
        entityData.addValue("company_id", "123");
        request.addData(connector.getId(), entityData);
        return service.create(request);
    }

    @Test
    public void createEventsFailure() throws ConnectionException {
        ConnectorInfo connector = new ConnectorInfo();
        AuthConfig authConfig = new AuthConfig();
        connector.setAuthConfig(authConfig);
        DescribeRequest describeReq = new DescribeRequest(connector, "event");
        EntitySchema entitySchema = service.describe(describeReq).get();
        entitySchema.getField(AmplitudeSeed.USER_PROPERTIES).get().setId("123");
        entitySchema.getField(AmplitudeSeed.EVENT_PROPERTIES).get().setId("222");
        entitySchema.addField(new AttributeSchema("Age", "integer").setParentAttributeId("123"));
        entitySchema.addField(new AttributeSchema("Address", "string").setParentAttributeId("123"));
        entitySchema.addField(new AttributeSchema("company_id", "string").setParentAttributeId("234"));
        entitySchema.addField(new AttributeSchema("event_loc", "string").setParentAttributeId("222"));
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        EntityData entityData = new EntityData("user");
        entityData.addValue("user_id", "test@test.com");
        entityData.addValue("event_type", "Interesting Moment");
        entityData.addValue("Age", 10);
        entityData.addValue("Address", "221b Baker Street, Wonderland");
        entityData.addValue("company_id", "123");
        request.addData(connector.getId(), entityData);
        SyncResponse response = service.create(request);
        assertFalse(response.isSuccess());
        assertTrue(response.getErrors().get(0).contains("missing_field"));
        assertTrue(response.getErrors().get(0).contains("api_key"));
        assertTrue(response.getErrors().get(0).contains("400"));
    }

    @Test
    public void createUsers() throws ConnectionException {
        ConnectorInfo connector = new ConnectorInfo();
        AuthConfig authConfig = new AuthConfig().setToken(API_KEY).setClientSecret(SECRET_KEY);
        connector.setAuthConfig(authConfig);
        DescribeRequest describeReq = new DescribeRequest(connector, "user");
        EntitySchema entitySchema = service.describe(describeReq).get();
        entitySchema.getField(AmplitudeSeed.USER_PROPERTIES).get().setId("123");
        entitySchema.getField(AmplitudeSeed.GROUPS).get().setId("234");
        entitySchema.addField(new AttributeSchema("Age", "integer").setParentAttributeId("123"));
        entitySchema.addField(new AttributeSchema("Address", "string").setParentAttributeId("123"));
        entitySchema.addField(new AttributeSchema("company_id", "string").setParentAttributeId("234"));
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        EntityData entityData = new EntityData("user");
        entityData.setSyncariEntityId("6666");
        entityData.addValue("user_id", "test@test.com");
        entityData.addValue("city", "Wonderland");
        entityData.addValue("Age", 10);
        entityData.addValue("Address", "221b Baker Street, Wonderland");
        entityData.addValue("company_id", "123");
        request.addData(connector.getId(), entityData);
        SyncResponse response = service.create(request);
        assertTrue(response.isSuccess());
        assertEquals(1, response.getResults().size());
        assertEquals("test@test.com", response.getResults().get(0).getId());
        assertEquals("6666", response.getResults().get(0).getSyncariId());
    }

    @Test
    public void createAndUpdateMultipleUsers() throws ConnectionException {
        ConnectorInfo connector = new ConnectorInfo();
        AuthConfig authConfig = new AuthConfig().setToken(API_KEY).setClientSecret(SECRET_KEY);
        connector.setAuthConfig(authConfig);
        DescribeRequest describeReq = new DescribeRequest(connector, "user");
        EntitySchema entitySchema = service.describe(describeReq).get();
        entitySchema.getField(AmplitudeSeed.USER_PROPERTIES).get().setId("123");
        entitySchema.getField(AmplitudeSeed.GROUPS).get().setId("234");
        entitySchema.addField(new AttributeSchema("Age", "integer").setParentAttributeId("123"));
        entitySchema.addField(new AttributeSchema("Address", "string").setParentAttributeId("123"));
        entitySchema.addField(new AttributeSchema("company_id", "string").setParentAttributeId("234"));
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        EntityData entityData = new EntityData("user");
        entityData.setSyncariEntityId("6666");

        entityData.addValue("user_id", "user1,user2,user3");
        entityData.addValue("city", "Wonderland");
        entityData.addValue("Age", 10);
        entityData.addValue("Address", "221b Baker Street, Wonderland");
        entityData.addValue("company_id", "123");
        request.addData(connector.getId(), entityData);
        SyncResponse response = service.create(request);
        assertTrue(response.isSuccess());
        assertEquals(1, response.getResults().size());
        assertEquals("user1,user2,user3", response.getResults().get(0).getId());
        assertEquals("6666", response.getResults().get(0).getSyncariId());
        entityData.setId("user1,user2,user3");
        entityData.addValue("Age", 20);
        SyncResponse updateResponse = service.update(request);
        assertTrue(updateResponse.isSuccess());
        assertEquals(1, updateResponse.getResults().size());
        assertEquals("user1,user2,user3", updateResponse.getResults().get(0).getId());
        assertEquals("6666", updateResponse.getResults().get(0).getSyncariId());
    }

    @Test
    public void createUsersFailure() throws ConnectionException {
        ConnectorInfo connector = new ConnectorInfo();
        AuthConfig authConfig = new AuthConfig();
        connector.setAuthConfig(authConfig);
        DescribeRequest describeReq = new DescribeRequest(connector, "user");
        EntitySchema entitySchema = service.describe(describeReq).get();
        entitySchema.getField(AmplitudeSeed.USER_PROPERTIES).get().setId("123");
        entitySchema.addField(new AttributeSchema("Age", "integer").setParentAttributeId("123"));
        entitySchema.addField(new AttributeSchema("Address", "string").setParentAttributeId("123"));
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        EntityData entityData = new EntityData("user");
        entityData.addValue("user_id", "test@test.com");
        entityData.addValue("city", "Wonderland");
        entityData.addValue("Age", 10);
        entityData.addValue("Address", "221b Baker Street, Wonderland");
        request.addData(connector.getId(), entityData);
        SyncResponse response = service.create(request);
        assertFalse(response.isSuccess());
        assertEquals("missing_api_key_and_write_key", response.getErrors().get(0));
    }

    @Test
    public void chortMemberSchemaIgnoresBlankFields() throws ConnectionException {
        ConnectorInfo connector = new ConnectorInfo();
        AuthConfig authConfig = new AuthConfig().setToken(API_KEY).setClientSecret(SECRET_KEY);
        connector.setAuthConfig(authConfig);
        Optional<EntitySchema> cohortmembership = service.describe(new DescribeRequest(connector, "cohortmembership"));
        assertTrue(cohortmembership.isPresent());
        assertTrue(!cohortmembership.get().getAttributes().isEmpty());
        cohortmembership.get().getAttributes().forEach(a->{
            assertTrue(!StringUtils.isBlank(a.getApiName()));
            assertTrue(!StringUtils.isBlank(a.getDisplayName()));
        });
    }

    @Test
    @Ignore
    @Retry(maxRetries=3, retryDelay=10)
    public void downloadCohorts() throws InterruptedException {
        createEvent("user1","jenkins_test_event1","1");
        createEvent("user2","jenkins_test_event1","2");
        createEvent("user3","jenkins_test_event2","3");
        createEvent("user4","jenkins_test_event3","4");
        createEvent("user5","jenkins_test_event3","5");
        ConnectorInfo connector = new ConnectorInfo();
        AuthConfig authConfig = new AuthConfig().setToken(API_KEY).setClientSecret(SECRET_KEY);
        connector.setAuthConfig(authConfig);
        connector.setMetaConfig(Map.of("cohorts","s9zi1cd,k7u07oo,e7bv1b5","userFields","app,country,city"));
        SyncRequest request = new SyncRequest();
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0));
        request.setEntitySchema(AmplitudeSeed.getSeedEntitySchema("cohortmembership",connector));
        request.setConnector(connector);
        request.setStorage(new TestFileStorage());
        FetchResponse byWatermark = service.getByWatermark(request);
        List<BatchJob> batchJobs = byWatermark.getBatchJobs();
        assertEquals(3, batchJobs.size());
        assertTrue(batchJobs.get(0).isPending());
        BatchJob batchJob = batchJobs.get(0);
        Instant stopAt = Instant.now().plus(5, ChronoUnit.MINUTES);
        while (!batchJob.isCompleted()) {
            Thread.sleep(1000);
            request.setBatchJobs(batchJobs);
            byWatermark = service.getByWatermark(request);
            batchJobs = byWatermark.getBatchJobs();
            batchJob = batchJobs.get(0);
            if (Instant.now().toEpochMilli() >= stopAt.toEpochMilli()) {
                fail("Failed to download cohorts in 5 minutes, aborting.");
                break;
            }
        }
        assertEquals(3, batchJobs.size());
        assertTrue(batchJobs.get(0).isCompleted());
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        assertTrue(iterator.hasNext());
        List<EntityData> next = iterator.next();
        assertTrue(next.size() >= 5);
        assertTrue(iterator.hasNext());
        next = iterator.next();
        assertTrue(next.size() >= 5);
    }

    @Test
    public void schemaTest() {
        ConnectorInfo connector = new ConnectorInfo();
        AuthConfig authConfig = new AuthConfig().setToken(API_KEY).setClientSecret(SECRET_KEY);
        connector.setAuthConfig(authConfig);
        assertTrue(service.describe(new DescribeRequest(connector, AmplitudeSeed.EVENT)).isPresent());
        assertTrue(service.describe(new DescribeRequest(connector, AmplitudeSeed.EVENT)).get().hasWatermarkField());
        assertTrue(service.describe(new DescribeRequest(connector, AmplitudeSeed.EVENT)).get().getWatermarkField().getApiName().equalsIgnoreCase("event_time"));
    }

    @Test
    public void capabilitiesTest() {
        assertEquals(service.getCapabilities().size(), 1);
        assertTrue(service.getCapabilities().contains(Capability.schemaEditInSyncari));
    }

}
