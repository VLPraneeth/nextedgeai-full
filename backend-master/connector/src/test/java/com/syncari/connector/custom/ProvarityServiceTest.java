package com.syncari.connector.custom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.sql.Date;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.DescribeAllRequest;
import com.syncari.connector.data.DescribeRequest;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.EventData;
import com.syncari.connector.data.FetchResponse;
import com.syncari.connector.data.SynapseInfo;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.connector.data.WebhookRequest;

import org.junit.Ignore;
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

@Ignore
@RunWith(SpringRunner.class)
@AutoConfigureDataMongo
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
@TestPropertySource("classpath:test_application.properties")
public class ProvarityServiceTest {

    @Value("${cloud.function.endpoint}")
    String cloudFunctionEndpoint;

    @Value("${spring.cloud.gcp.credentials.encoded-key}")
    String gcpCredentialsKey;

    @Autowired
    CustomService service;

    ConnectorInfo connectorInfo;

    public ConnectorInfo getConnector() {
        if (connectorInfo == null) {
            connectorInfo = new ConnectorInfo();
            connectorInfo.setId("123456");
            connectorInfo.setName("provarity_test_client");
            CloudFunctionInfo cfInfo = new CloudFunctionInfo(cloudFunctionEndpoint, "provarity_test_client", gcpCredentialsKey, gcpCredentialsKey,
                Date.from(Instant.now()),"localhost","xyz123");
            connectorInfo.setCloudFunctionInfo(cfInfo);
            AuthConfig authConfig = new AuthConfig();
            authConfig.setEndpoint("https://api.provarity.com/");
            authConfig.setUserName("customer+syncari_admin@provarity.com");
            authConfig.setPassword(System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME"));
            connectorInfo.setMetaConfig(Map.of("gid",12346));
            connectorInfo.setAuthConfig(authConfig);

            // Refresh token
            TestConnectionResponse testConnectionResponse = service.testConnection(connectorInfo, List.of());
            assertTrue(testConnectionResponse.isSuccess());
            connectorInfo.setAuthConfig(testConnectionResponse.getAuthConfig());
        }
        return connectorInfo;
    }

    @Test
    public void about() {
        SynapseInfo synapseInfo = service.about(getConnector().getCloudFunctionInfo());
        assertNotNull(synapseInfo);
        assertEquals("provarity", synapseInfo.getName());
    }
    
    @Test
    public void describe() {
        long start = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            Optional<EntitySchema> schema = service.describe(new DescribeRequest(getConnector(), "account"));
            assertTrue(schema.isPresent());
            assertEquals("account", schema.get().getApiName());
            System.out.println("Call time: " + (System.currentTimeMillis() - start) + " ms");
        }
        System.out.println("Total time: " + (System.currentTimeMillis() - start) + " ms");
    }

    @Test
    public void describeAll() {
        long start = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            List<EntitySchema> schemas = service.describeAll(new DescribeAllRequest(getConnector(), List.of("account")));
            assertTrue(schemas.size() > 0);
            assertEquals("account", schemas.get(0).getApiName());
            System.out.println("Call time: " + (System.currentTimeMillis() - start) + " ms");
        }
        System.out.println("Total time: " + (System.currentTimeMillis() - start) + " ms");
    }

    @Test
    @Ignore("broken and needs to be fixed")
    public void getByWatermark() {
        long start = System.currentTimeMillis();
        Optional<EntitySchema> schema = service.describe(new DescribeRequest(getConnector(), "poc"));
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), schema.get());
        syncRequest.setPageSize(10);
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0).setResync(true);
        syncRequest.setWatermark(watermark);
        FetchResponse byWatermark = service.getByWatermark(syncRequest);
        assertTrue(byWatermark.getIterator().hasNext());
        List<EntityData> eds = byWatermark.getIterator().next();
        assertFalse(byWatermark.getIterator().hasNext());
    }

    @Test
    @Ignore("broken and needs to be fixed")
    public void extractIdentifier() throws Exception {
        String json = "[{\"id\":\"100\",\"name\":\"account\",\"industry\":\"test_industry\"}]";
        WebhookRequest request = new WebhookRequest();
        CloudFunctionInfo cfInfo = new CloudFunctionInfo(cloudFunctionEndpoint, "provarity_client", gcpCredentialsKey, gcpCredentialsKey, Date.from(Instant.now()),
                "localhost","xyz123");
        request.setCloudFunctionInfo(cfInfo);
        request.setBody(json);
        String identifier = service.extractIdentifier(request);
        assertEquals("100", identifier);
    }

    @Test
    @Ignore("broken and needs to be fixed")
    public void processWebhook() throws Exception {
        //String resp = "{\"data\": {\"name\": \"test_name\", \"id\": \"100\", \"syncariEntityId\": null," +
        //    " \"deleted\": false, \"values\": {\"industry\": \"test_industry\"}, \"lastModified\": null, "+
        //    "\"createdAt\": null}, \"operation\": \"update\", \"eventId\": null}";
        //String resp = "{\"data\": {\"name\": \"test_name\", \"id\": \"100\", \"syncariEntityId\": null, \"deleted\": false, \"values\": {\"industry\": \"test_industry\"}, \"lastModified\": null, \"createdAt\": null}, \"operation\": \"update\", \"eventId\": null}";
        //EventData eventData = (new ObjectMapper()).readValue(resp, EventData.class);
        //assertNotNull(eventData);
        
        String json = "[{\"id\":\"100\",\"name\":\"account\",\"industry\":\"test_industry\"}]";
        WebhookRequest request = new WebhookRequest();
        request.setConfig(getConnector());
        request.setBody(json);
        List<EventData> eventDatas = service.parseEventData(request);
        eventDatas.forEach(x -> {
            assertNotNull(x.getConnectorId());
            assertNotNull(x.getOperation());
            assertNotNull(x.getData());
        });
        assertEquals("100", eventDatas.get(0).getData().getId());
        assertEquals("account", eventDatas.get(0).getData().getName());
        assertEquals("test_industry", eventDatas.get(0).getData().getValueAsString("industry"));
    }
}
