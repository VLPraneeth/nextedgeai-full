package com.syncari.core.insights;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.config.AuthConfig;
import com.syncari.core.config.AppConfig;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DatastoreConnectionHelperTest {

    @Test
    public void testCreateSnowflakeConnectionConfig_ValidData() {
        ConnectorInfo connectorInfo = createSnowflakeConnector("https://test.snowflakecomputing.com/", "test-account", "TEST_DB");
        AppConfig appConfig = mock(AppConfig.class);

        Map<String, Object> result = DatastoreConnectionHelper.createConnectionConfig(connectorInfo, "test-org", appConfig);

        Map<String, Object> dbConfig = getDbConfig(result);
        assertEquals("test.snowflakecomputing.com", dbConfig.get("host"));
        assertEquals("443", dbConfig.get("port"));
        assertEquals("TEST_DB", dbConfig.get("database"));
        assertEquals("test-account", dbConfig.get("accountName"));
    }

    @Test(expected = RuntimeException.class)
    public void testCreateSnowflakeConnectionConfig_MissingFields() {
        ConnectorInfo connectorInfo = createSnowflakeConnector(null, "test-account", "TEST_DB");
        AppConfig appConfig = mock(AppConfig.class);

        DatastoreConnectionHelper.createConnectionConfig(connectorInfo, "test-org", appConfig);
    }

    @Test
    public void testCreatePostgresConnectionConfig_ValidData() {
        ConnectorInfo connectorInfo = createPostgresConnector("5432", "test_db", "localhost:5432");
        AppConfig appConfig = mock(AppConfig.class);

        Map<String, Object> result = DatastoreConnectionHelper.createConnectionConfig(connectorInfo, "test-org", appConfig);

        Map<String, Object> dbConfig = getDbConfig(result);
        assertEquals("localhost", dbConfig.get("host"));
        assertEquals("5432", dbConfig.get("port"));
        assertEquals("test_db", dbConfig.get("database"));
        assertEquals("test-org", dbConfig.get("accountName"));
    }

    @Test(expected = RuntimeException.class)
    public void testCreatePostgresConnectionConfig_MissingFields() {
        ConnectorInfo connectorInfo = createPostgresConnector(null, "test_db", "localhost:5432");
        AppConfig appConfig = mock(AppConfig.class);

        DatastoreConnectionHelper.createConnectionConfig(connectorInfo, "test-org", appConfig);
    }


    private ConnectorInfo createSnowflakeConnector(String endpoint, String accountName, String dbName) {
        ConnectorInfo connectorInfo = mock(ConnectorInfo.class);
        Map<String, Object> metaConfig = new HashMap<>();
        metaConfig.put("endpoint", endpoint);
        metaConfig.put("accountName", accountName);
        metaConfig.put("dbName", dbName);
        metaConfig.put("schemaName", "TEST_SCHEMA");

        when(connectorInfo.getConnectorMetadataName()).thenReturn(Constants.SNOWFLAKE_DATASTORE);
        when(connectorInfo.getMetaConfig()).thenReturn(metaConfig);
        when(connectorInfo.getName()).thenReturn("Test Connector");
        when(connectorInfo.getAuthConfig()).thenReturn(mock(AuthConfig.class));
        return connectorInfo;
    }

    private ConnectorInfo createPostgresConnector(String port, String dbName, String clusterName) {
        ConnectorInfo connectorInfo = mock(ConnectorInfo.class);
        Map<String, Object> metaConfig = new HashMap<>();
        metaConfig.put("port", port);
        metaConfig.put("dbName", dbName);
        metaConfig.put("clusterName", clusterName);

        when(connectorInfo.getConnectorMetadataName()).thenReturn("postgres");
        when(connectorInfo.getMetaConfig()).thenReturn(metaConfig);
        when(connectorInfo.getName()).thenReturn("Test Connector");
        when(connectorInfo.getAuthConfig()).thenReturn(mock(AuthConfig.class));
        return connectorInfo;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getDbConfig(Map<String, Object> result) {
        return (Map<String, Object>) result.get("configuration");
    }
}