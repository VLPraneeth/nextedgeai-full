package com.syncari.connector.custom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.sql.Date;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.DescribeAllRequest;
import com.syncari.connector.data.DescribeRequest;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.FetchResponse;
import com.syncari.connector.data.SynapseInfo;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.SyncResponse;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.connector.data.iterator.Offset.OffsetType;

import com.syncari.connector.exception.ErrorCodes;
import org.apache.commons.lang3.StringUtils;
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

//@Ignore
@RunWith(SpringRunner.class)
@AutoConfigureDataMongo
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
@TestPropertySource("classpath:test_application.properties")
@Ignore
public class PipedriveServiceTest {

    @Value("${cloud.function.endpoint}")
    String cloudFunctionEndpoint;

    @Value("${spring.cloud.gcp.cfdeployer.credentials.encoded-key}")
    String cfDeployerCredentialsKey;

    @Autowired
    CustomService service;

    ConnectorInfo connectorInfo;

    public ConnectorInfo getConnector() {
        if (connectorInfo == null) {
            connectorInfo = new ConnectorInfo();
            connectorInfo.setName("pipedrive_test_client");
            connectorInfo.setEndpoint("https://syncariinc-sandbox.pipedrive.com/v1/");
            CloudFunctionInfo cfInfo = new CloudFunctionInfo(cloudFunctionEndpoint, "pipedrive_test_client",
                    cfDeployerCredentialsKey, cfDeployerCredentialsKey, Date.from(Instant.now()), "https://localhost","xyz123");
            connectorInfo.setCloudFunctionInfo(cfInfo);
            AuthConfig authConfig = new AuthConfig();
            authConfig.setEndpoint("https://syncariinc-sandbox.pipedrive.com/v1");
            authConfig.setAccessToken("6bc6434af5d1dcba23188e69abcb2ed00f460853");
            connectorInfo.setAuthConfig(authConfig);
            connectorInfo.setMetaConfig(Map.of("endpoint","https://syncariinc-sandbox.pipedrive.com/v1/"));

            // Refresh token
            TestConnectionResponse testConnectionResponse = service.testConnection(connectorInfo, List.of());
            assertTrue(testConnectionResponse.isSuccess());
            // Just for testing purpose, we include the metaconfig. TODO: replace this with a proper value.
            assertFalse(testConnectionResponse.getMetaConfig().isEmpty());
            assertNotNull(testConnectionResponse.getMetaConfig().get("dummy"));
            assertNotNull(testConnectionResponse.getMetaConfig().get("endpoint"));
            connectorInfo.setAuthConfig(testConnectionResponse.getAuthConfig());
        }
        return connectorInfo;
    }

    @Test
    public void about() {
        SynapseInfo synapseInfo = service.about(getConnector().getCloudFunctionInfo());
        assertNotNull(synapseInfo);
        assertEquals("pipedrive", synapseInfo.getName());
    }

    @Test
    public void errorResponseTest() {
        // Try to force an error
        ConnectorInfo connectorInfo = getConnector();
        connectorInfo.setEndpoint("https://syncariinc-sandbox.pipedrive.com/v1blah");
        CloudFunctionInfo cfInfo = new CloudFunctionInfo(cloudFunctionEndpoint, "pipedrive_test_client",
                cfDeployerCredentialsKey, cfDeployerCredentialsKey, Date.from(Instant.now()), "https://localhost","xyz123");
        connectorInfo.setCloudFunctionInfo(cfInfo);
        connectorInfo.getAuthConfig().setEndpoint("https://syncariinc-sandbox.pipedrive.com/v1blah");
        TestConnectionResponse testConnectionResponse = service.testConnection(connectorInfo, List.of());
        assertEquals(ErrorCodes.LOGIN_ERROR.toString(), testConnectionResponse.getCode());
    }

    @Test
    public void describe() {
        long start = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            Optional<EntitySchema> schema = service.describe(new DescribeRequest(getConnector(), "person"));
            assertTrue(schema.isPresent());
            assertEquals("person", schema.get().getApiName());
            System.out.println("Call time: " + (System.currentTimeMillis() - start) + " ms");
        }
        System.out.println("Total time: " + (System.currentTimeMillis() - start) + " ms");
    }

    @Test
    public void describeAll() {
        long start = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            List<EntitySchema> schemas = service.describeAll(new DescribeAllRequest(getConnector(), List.of("person")));
            assertTrue(schemas.size() > 0);
            assertEquals("person", schemas.get(0).getApiName());
            System.out.println("Call time: " + (System.currentTimeMillis() - start) + " ms");
        }
        System.out.println("Total time: " + (System.currentTimeMillis() - start) + " ms");
    }

    @Test
    public void getByWatermark() {
        long start = System.currentTimeMillis();
        Optional<EntitySchema> schema = service.describe(new DescribeRequest(getConnector(), "person"));
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), schema.get());
        syncRequest.setPageSize(10);
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0).setResync(true);
        syncRequest.setWatermark(watermark);
        FetchResponse byWatermark = service.getByWatermark(syncRequest);
        assertTrue(byWatermark.getIterator().hasNext());
        assertEquals(OffsetType.RECORD_COUNT, byWatermark.getIterator().getOffsetInfo().getType());
        /*
        List<EntityData> eds = new ArrayList();
        while (byWatermark.getIterator().hasNext()) {
            eds.addAll(byWatermark.getIterator().next());
        }
        assertEquals(5043, eds.size());
        */
    }

    @Test
    public void getByIds() {
        long start = System.currentTimeMillis();
        Optional<EntitySchema> schema = service.describe(new DescribeRequest(getConnector(), "user"));
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), schema.get());
        syncRequest.setPageSize(10);
        List<EntityData> eds = new ArrayList<>();
        EntityData ed = new EntityData("user");
        ed.setId("14434274");
        ed.setSyncariEntityId("");
        ed.setValues(new HashMap<>());
        eds.add(ed);
        Map data = new HashMap<>();
        data.put(getConnector().getId(), eds);
        syncRequest.setData(data);
        List<EntityData> byIds = service.getByIds(syncRequest);
        assertTrue(byIds.size() == 1);
        assertEquals("14434274", byIds.get(0).getId());
    }

    @Test
    public void crud() {
        Optional<EntitySchema> schema = service.describe(new DescribeRequest(getConnector(), "person"));
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), schema.get());
        syncRequest.setPageSize(10);
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0).setResync(true);
        syncRequest.setWatermark(watermark);
        List<EntityData> eds = new ArrayList<>();
        EntityData ed = new EntityData("person");
        ed.setId("");
        ed.setSyncariEntityId("");
        Map values = new HashMap<>();
        values.put("name", "Test Person");
        values.put("email", "testperson1@test.com");
        ed.setValues(values);
        eds.add(ed);
        Map data = new HashMap<>();
        data.put(getConnector().getId(), eds);
        syncRequest.setData(data);

        String id = "";

        try {
            SyncResponse created = service.create(syncRequest);
            assertTrue(created.isSuccess());
            id = created.getResults().get(0).getId();
            syncRequest.getData().get(getConnector().getId()).get(0).setId(id);
            
            // Update
            syncRequest.getData().get(getConnector().getId()).get(0).getValues().put("first_name", "Test first name");
            SyncResponse updated = service.update(syncRequest);
            assertTrue(updated.isSuccess());

            // getByIds
            List<EntityData> byIds = service.getByIds(syncRequest);
            assertTrue(byIds.size() == 1);
            assertTrue(StringUtils.isNotEmpty(byIds.get(0).getId()));
        } finally {
            if (StringUtils.isNotEmpty(id)) {
                SyncResponse deleted = service.delete(syncRequest);
                assertTrue(deleted.isSuccess());
            }
        }
        
    }

    @Test
    public void testInternalInfoWhenPipeDriveClientHasNoHelpUrlSetThenReturnDefaultUrl() {
        String expectedUrl = "https://support.syncari.com/hc/en-us/sections/4578749288980-Custom-Synapse";

        SynapseInfo synapseInfo = service.internalAbout(getConnector().getCloudFunctionInfo());

        assertEquals(expectedUrl, synapseInfo.getMetadata().getHelpUrl());
    }
}
