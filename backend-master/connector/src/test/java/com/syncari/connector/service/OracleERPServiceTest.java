package com.syncari.connector.service;

import com.syncari.connector.AbstractConnectorTest;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.DataServiceTest;
import com.syncari.connector.EntityData;
import com.syncari.connector.OracleErpSales.OracleERPService;
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
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
public class OracleERPServiceTest extends AbstractConnectorTest implements DataServiceTest {

    private static final String USERNAME = "dev@syncari.com";
    private static final String PASSWORD = System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME");
    private static final String URL = "https://fa-eusl-saasfaprod1.fa.ocs.oraclecloud.com";

    private ConnectorInfo connector;

    @Autowired
    private OracleERPService oracleERPService;

    private ConnectorInfo createInvalidConnector() {
        ConnectorInfo connector = new ConnectorInfo();
        connector.setId("0000");
        connector.setName("oracleErp1");
        AuthConfig authConfig = new AuthConfig();
        authConfig.setEndpoint(URL);
        authConfig.setUserName(USERNAME);
        authConfig.setAccessToken("invalid_password");
        connector.setAuthConfig(authConfig);
        return connector;
    }

    private ConnectorInfo createConnector() {
        ConnectorInfo connector = new ConnectorInfo();
        connector.setId("0001");
        connector.setName("oracleErp");
        AuthConfig authConfig = new AuthConfig();

        authConfig.setEndpoint(URL);
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
        return oracleERPService;
    }

    @Override
    public MetadataService getMetadataService() {
        return oracleERPService;
    }

    @Override
    public CommonDataService getDataService() {
        return oracleERPService;
    }

    @Override
    public String getDescribeObject() {
        return OracleERPService.ACCOUNTS_ENTITY_NAME;
    }

    @Override
    @Test
    public void testConnectionTest() {
//        ConnectorInfo conn = createInvalidConnector();
//        TestConnectionResponse resp = getAuthenticationService().testConnection(conn, List.of());
//        assertFalse(resp.isSuccess());
//        assertTrue(resp.getMessage().startsWith("Authentication failed."));
//        assertFalse(resp.getErrors().isEmpty());
//        assertEquals(resp.getErrors().get(0), "401 Unauthorized");

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
        for (String ent : OracleERPService.PRIMARY_OBJECTS) {
            describe(ent, null);
        }
    }

    @Override
    public void getByWatermarkSinceEpoch() {
        verifyGetByWatermarkSinceEpoch(OracleERPService.ACCOUNTS_ENTITY_NAME);
    }

    @Override
    public void getByWatermarkRecent() {
        verifyGetByWatermarkRecent(OracleERPService.ACCOUNTS_ENTITY_NAME);
    }

    @Override
    public void getByWatermarkWithLimit() {
        verifyGetByWatermarkWithLimit(OracleERPService.ACCOUNTS_ENTITY_NAME, 2);
    }

    @Override
    public void getByWatermarkResultsOrdered() {
        verifyGetByWatermarkResultsOrdered(OracleERPService.ACCOUNTS_ENTITY_NAME);
    }

    @Override
    public void getByIds() {
        verifyGetByIds(OracleERPService.ACCOUNTS_ENTITY_NAME);
    }

    @Test
    public void testObjectSync() {
        DescribeRequest describeRequest = new DescribeRequest(getConnector(), OracleERPService.ACCOUNTS_ENTITY_NAME);
        Optional<EntitySchema> schema = oracleERPService.describe(describeRequest);
        assertFalse(schema.isEmpty());

        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), schema.get());
        WatermarkInfo wm = new WatermarkInfo(0, Instant.now().toEpochMilli(), true, 0);
        syncRequest.setWatermark(wm);
        EntityData entity = new EntityData(OracleERPService.ACCOUNTS_ENTITY_NAME);
        syncRequest.addData(getConnector().getId(), entity);
        FetchResponse response = oracleERPService.getByWatermark(syncRequest);
        assertTrue(response.getIterator().hasNext());
    }

    @Override
    public void getDeletedByWatermark() {
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
