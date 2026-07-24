package com.syncari.connector.service;

import com.syncari.connector.AbstractConnectorTest;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.DataServiceTest;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.oraclepim.OraclePIMService;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
public class OraclePIMServiceTest extends AbstractConnectorTest implements DataServiceTest {

    private static final String USERNAME = "scott@syncari.com";
    private static final String PASSWORD = System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME");
    private static final String URL = "https://fa-eusl-saasfaprod1.fa.ocs.oraclecloud.com";

    private ConnectorInfo connector;

    @Autowired
    private OraclePIMService oraclePIMService;

    private ConnectorInfo createInvalidConnector() {
        ConnectorInfo connector = new ConnectorInfo();
        connector.setId("0000");
        connector.setName("oraclepim0");
        connector.setEndpoint(URL);
        AuthConfig authConfig = new AuthConfig();
        authConfig.setUserName(USERNAME);
        authConfig.setAccessToken("invalid_password");
        connector.setAuthConfig(authConfig);
        return connector;
    }

    private ConnectorInfo createConnector() {
        ConnectorInfo connector = new ConnectorInfo();
        connector.setId("0001");
        connector.setName("oraclepim");
        connector.setEndpoint("https://fa-eusl-saasfaprod1.fa.ocs.oraclecloud.com");
        AuthConfig authConfig = new AuthConfig();

        authConfig.setUserName(USERNAME);
        authConfig.setAccessToken(PASSWORD);
        authConfig.setPassword(PASSWORD);
        connector.setAuthConfig(authConfig);
        return connector;
    }

    @Override
    public ConnectorInfo getConnector() {
        if (connector == null) connector = createConnector();
        return connector;
    }

    @Override
    public AuthenticationService getAuthenticationService() {
        return oraclePIMService;
    }

    @Override
    public MetadataService getMetadataService() {
        return oraclePIMService;
    }

    @Override
    public CommonDataService getDataService() {
        return oraclePIMService;
    }

    @Override
    public String getDescribeObject() {
        return OraclePIMService.ITEMS_RESOURCE_NAME;
    }

    @Override
    @Test
    public void testConnectionTest() {
        ConnectorInfo conn = createInvalidConnector();
        TestConnectionResponse resp = getAuthenticationService().testConnection(conn, List.of());
        assertFalse(resp.isSuccess());
        assertTrue(resp.getMessage().startsWith("Authentication failed."));
        assertFalse(resp.getErrors().isEmpty());
        assertEquals(resp.getErrors().get(0), "401 Unauthorized");

        ConnectorInfo validConn = getConnector();
        TestConnectionResponse validResponse = getAuthenticationService().testConnection(validConn, List.of());
        assertTrue(validResponse.isSuccess());
    }

    @Override
    public void describeAllTest() {
        describeAll(null);
    }

    @Override
    public void describeTest() {
        for (String ent : OraclePIMService.SUPPORTED_ENTITIES) {
            describe(ent, null);
        }
    }

    @Override
    public void getByWatermarkSinceEpoch() {
        verifyGetByWatermarkSinceEpoch(OraclePIMService.ITEMS_RESOURCE_NAME);
    }

    @Override
    public void getByWatermarkRecent() {
        verifyGetByWatermarkRecent(OraclePIMService.ITEMS_RESOURCE_NAME);
    }

    @Override
    public void getByWatermarkWithLimit() {
        verifyGetByWatermarkWithLimit(OraclePIMService.ITEMS_RESOURCE_NAME, 2);
    }

    @Override
    public void getByWatermarkResultsOrdered() {
        verifyGetByWatermarkResultsOrdered(OraclePIMService.ITEMS_RESOURCE_NAME);
    }

    @Override
    public void getByIds() {
        verifyGetByIds(OraclePIMService.ITEMS_RESOURCE_NAME);
    }

    @Override
    public void getDeletedByWatermark() {
    }

    @Test
    public void testItemsObject() {
        DescribeRequest describeRequest = new DescribeRequest(getConnector(), OraclePIMService.ITEMS_RESOURCE_NAME);
        Optional<EntitySchema> schema = oraclePIMService.describe(describeRequest);
        assertFalse(schema.isEmpty());

        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), schema.get());
        EntityData entity = new EntityData(OraclePIMService.ITEMS_RESOURCE_NAME);
        syncRequest.addData(getConnector().getId(), entity);

        // verify sync
        FetchResponse response = oraclePIMService.getAllRecords(syncRequest);
        assertTrue(response.getIterator().hasNext());
        List<EntityData> records = new ArrayList<>();
        while (response.getIterator().hasNext()) {
            records.addAll(response.getIterator().next());
        }
        assertFalse(records.isEmpty());

        // verify fetchById
        syncRequest = new SyncRequest().Builder(getConnector(), schema.get());
        entity = new EntityData(OraclePIMService.ITEMS_RESOURCE_NAME);
        syncRequest.addData(getConnector().getId(), entity);
        syncRequest.getIds().add("300000009355024");
        records = oraclePIMService.getByIds(syncRequest);
        assertFalse(records.isEmpty());
        assertEquals(records.size(), 1);
    }

    @Override
    @Test
    public void createTest() {
    }

    @Override
    public void updateTest() {
    }

    @Override
    public void deleteTest() {
    }

    @Override
    @Test
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
