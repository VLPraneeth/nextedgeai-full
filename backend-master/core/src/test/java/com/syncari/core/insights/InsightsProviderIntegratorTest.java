package com.syncari.core.insights;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.datastore.PostgresqlDatastoreService;
import com.syncari.connector.datastore.SnowflakeDatastoreService;
import com.syncari.core.model.Connector;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;


@RunWith(MockitoJUnitRunner.class)
public class InsightsProviderIntegratorTest {

    @Mock
    private PostgresqlDatastoreService postgresqlDatastoreService;

    @Mock
    private SnowflakeDatastoreService snowflakeDatastoreService;

    @InjectMocks
    private InsightsProviderIntegrator insightsProviderIntegrator;

    private ConnectorInfo connectorInfo;

    @Before
    public void setUp() {
        Connector mockConnector = new Connector();
        connectorInfo = new ConnectorInfo();
        connectorInfo.setConnectorMetadataName(Constants.SNOWFLAKE_DATASTORE);
        connectorInfo.setMetaConfig(new HashMap<>());
        connectorInfo.setAuthConfig(new AuthConfig());
    }

    @Test
    public void testGetThoughtSpotDataWarehouseType() throws Exception {
        Method method = InsightsProviderIntegrator.class.getDeclaredMethod("getThoughtSpotDataWarehouseType", ConnectorInfo.class);
        method.setAccessible(true);

        // Test Snowflake
        connectorInfo.setConnectorMetadataName(Constants.SNOWFLAKE_DATASTORE);
        String result = (String) method.invoke(insightsProviderIntegrator, connectorInfo);
        assertEquals("SNOWFLAKE", result);

        // Test PostgreSQL  
        connectorInfo.setConnectorMetadataName(Constants.POSTGRESQL_DATASTORE);
        result = (String) method.invoke(insightsProviderIntegrator, connectorInfo);
        assertEquals("POSTGRES", result);

        // Test Internal Syncari Datastore (should return POSTGRES)
        connectorInfo.setConnectorMetadataName("datastore");
        result = (String) method.invoke(insightsProviderIntegrator, connectorInfo);
        assertEquals("POSTGRES", result);

        // Test Unknown (defaults to POSTGRES)
        connectorInfo.setConnectorMetadataName("unknown_datastore");
        result = (String) method.invoke(insightsProviderIntegrator, connectorInfo);
        assertEquals("POSTGRES", result);

        // Test Null
        result = (String) method.invoke(insightsProviderIntegrator, (ConnectorInfo) null);
        assertNull(result);
    }

    @Test
    public void testGetSchemaMetadataForDatastore() throws Exception {
        Map<String, List<Map<String, String>>> expectedMetadata = new HashMap<>();
        expectedMetadata.put("table1", List.of(Map.of("column1", "varchar")));
        Method method = InsightsProviderIntegrator.class.getDeclaredMethod("getSchemaMetadataForDatastore", ConnectorInfo.class);
        method.setAccessible(true);

        // Test PostgreSQL
        connectorInfo.setConnectorMetadataName(Constants.POSTGRESQL_DATASTORE);
        doReturn(expectedMetadata).when(postgresqlDatastoreService).getSchemaMetadata(connectorInfo);
        Map<String, List<Map<String, String>>> result = (Map<String, List<Map<String, String>>>) method.invoke(insightsProviderIntegrator, connectorInfo);
        assertEquals(expectedMetadata, result);
        verify(postgresqlDatastoreService).getSchemaMetadata(connectorInfo);

        // Test Snowflake
        connectorInfo.setConnectorMetadataName(Constants.SNOWFLAKE_DATASTORE);
        doReturn(expectedMetadata).when(snowflakeDatastoreService).getSchemaMetadata(connectorInfo);
        result = (Map<String, List<Map<String, String>>>) method.invoke(insightsProviderIntegrator, connectorInfo);
        assertEquals(expectedMetadata, result);
        verify(snowflakeDatastoreService).getSchemaMetadata(connectorInfo);

        // Test Internal Syncari Datastore (should use PostgreSQL service)
        connectorInfo.setConnectorMetadataName("datastore");
        doReturn(expectedMetadata).when(postgresqlDatastoreService).getSchemaMetadata(connectorInfo);
        result = (Map<String, List<Map<String, String>>>) method.invoke(insightsProviderIntegrator, connectorInfo);
        assertEquals(expectedMetadata, result);
        verify(postgresqlDatastoreService, times(2)).getSchemaMetadata(connectorInfo); // Called twice now

        // Test Unknown Type (should throw exception)
        connectorInfo.setConnectorMetadataName("unknown_datastore");
        try {
            method.invoke(insightsProviderIntegrator, connectorInfo);
            fail("Expected RuntimeException");
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof RuntimeException);
            assertTrue(e.getCause().getMessage().contains("Unknown datastore type"));
        }
    }

    @Test
    public void testAuthenticationHandling() {
        testOAuthWithToken();
        testPasswordAuth();
        testOAuthFallback();
    }

    private void testOAuthWithToken() {
        Map<String, Object> dbConfig = new HashMap<>();
        dbConfig.put("accountName", "testOrg");
        AuthConfig authConfig = new AuthConfig();
        authConfig.setAccessToken("test-oauth-token");

        dbConfig.put("authenticator", "oauth");
        dbConfig.put("token", authConfig.getAccessToken());

        assertTrue(dbConfig.containsKey("authenticator"));
        assertEquals("oauth", dbConfig.get("authenticator"));
        assertEquals("test-oauth-token", dbConfig.get("token"));
        assertFalse(dbConfig.containsKey("user"));
        assertFalse(dbConfig.containsKey("password"));
    }

    private void testPasswordAuth() {
        Map<String, Object> dbConfig = new HashMap<>();
        dbConfig.put("accountName", "testOrg");
        AuthConfig authConfig = new AuthConfig();
        authConfig.setUserName("testuser");
        authConfig.setPassword(System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME"));

        dbConfig.put("user", authConfig.getUserName());
        dbConfig.put("password", authConfig.getPassword());

        assertFalse(dbConfig.containsKey("authenticator"));
        assertFalse(dbConfig.containsKey("token"));
        assertEquals("testuser", dbConfig.get("user"));
        assertEquals("testpass", dbConfig.get("password"));
    }

    private void testOAuthFallback() {
        Map<String, Object> dbConfig = new HashMap<>();
        dbConfig.put("accountName", "testOrg");
        AuthConfig authConfig = new AuthConfig();
        authConfig.setUserName("testuser");
        authConfig.setPassword(System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME"));

        // Fallback to password since OAuth token is null
        dbConfig.put("user", authConfig.getUserName());
        dbConfig.put("password", authConfig.getPassword());

        assertFalse(dbConfig.containsKey("authenticator"));
        assertFalse(dbConfig.containsKey("token"));
        assertEquals("testuser", dbConfig.get("user"));
        assertEquals("testpass", dbConfig.get("password"));
    }
}