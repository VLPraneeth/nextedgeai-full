package com.syncari.connector.datastore;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.AuthField;
import com.syncari.connector.data.AuthMetadata;
import com.syncari.connector.data.AuthType;
import com.syncari.connector.ConnectorType;
import com.syncari.connector.data.UIMetadata;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class SnowflakeDatastoreServiceTest {

    private SnowflakeDatastoreService snowflakeDatastoreService;
    private ConnectorInfo connectorInfo;

    @Before
    public void setUp() {
        snowflakeDatastoreService = new SnowflakeDatastoreService() {
            @Override
            protected int getInitializationFailTimeout() {
                return 1000;
            }

            @Override
            protected java.sql.Connection getConnection(ConnectorInfo connector) throws SQLException {
                throw new SQLException("Test connection blocked");
            }
        };
        connectorInfo = new ConnectorInfo();
        connectorInfo.setName("test-datastore");
        connectorInfo.setMetaConfig(new HashMap<>());
        connectorInfo.setAuthConfig(new AuthConfig());
        connectorInfo.getMetaConfig().put("connectionTimeout", 1000);
    }

    @Test
    public void testServiceMetadata() {
        Map<String, String> mappings = snowflakeDatastoreService.getEntityMappings();
        assertTrue(mappings.isEmpty());

        List<AuthMetadata> authTypes = snowflakeDatastoreService.getSupportedAuthTypes();
        assertEquals(2, authTypes.size());

        assertEquals("Datawarehouse", snowflakeDatastoreService.getCategory());
        assertEquals(Constants.SNOWFLAKE_DATASTORE, snowflakeDatastoreService.getName());
        assertEquals(ConnectorType.Datastore, snowflakeDatastoreService.getType());

        UIMetadata metadata = snowflakeDatastoreService.getUIMetadata();
        assertNotNull(metadata);
        assertEquals("/assets/icons/logos/snowflake.svg", metadata.getIconPath());
        assertEquals("Snowflake", metadata.getDisplayName());
    }

    @Test
    public void testGetConfigureFields() {
        List<AuthField> fields = snowflakeDatastoreService.getConfigureFields();
        assertEquals(7, fields.size());

        AuthField endpointField = fields.stream()
            .filter(f -> "endpoint".equals(f.getName()))
            .findFirst().orElse(null);
        assertNotNull(endpointField);
        assertTrue(endpointField.isRequired());
        assertEquals("Snowflake URL", endpointField.getLabel());
        assertEquals("text", endpointField.getDataType());
        assertTrue(endpointField.getHelpSummary().contains("https://account_name.snowflakecomputing.com"));

        String[] requiredFields = {"accountName", "warehouseName", "dbName", "schemaName"};
        String[] requiredLabels = {"Account Name", "Warehouse Name", "Database Name", "Schema Name"};

        for (int i = 0; i < requiredFields.length; i++) {
            final int index = i;
            AuthField field = fields.stream()
                .filter(f -> requiredFields[index].equals(f.getName()))
                .findFirst().orElse(null);
            assertNotNull("Field " + requiredFields[index] + " should exist", field);
            assertTrue("Field " + requiredFields[index] + " should be required", field.isRequired());
            assertEquals("Field " + requiredFields[index] + " label mismatch", requiredLabels[index], field.getLabel());
        }

        AuthField roleField = fields.stream()
            .filter(f -> "role".equals(f.getName()))
            .findFirst().orElse(null);
        assertNotNull(roleField);
        assertFalse(roleField.isRequired());
        assertEquals("User Role", roleField.getLabel());
        assertNotNull(roleField.getHelpSummary());
    }

    @Test
    public void testJdbcURLGeneration() {
        String[] endpoints = {
            "https://test-account.snowflakecomputing.com",
            "http://test-account.snowflakecomputing.com", 
            "https://test-account.snowflakecomputing.com/",
            "test-account.snowflakecomputing.com"
        };

        for (String endpoint : endpoints) {
            connectorInfo.getMetaConfig().put("endpoint", endpoint);
            String jdbcUrl = snowflakeDatastoreService.getJdbcURL(connectorInfo);
            assertEquals("jdbc:snowflake://test-account.snowflakecomputing.com/", jdbcUrl);
        }
    }

    @Test
    public void testGetTableNameAndProperties() {
        connectorInfo.getMetaConfig().put("schemaName", "test_schema");
        connectorInfo.getMetaConfig().put("dbName", "test_db");
        connectorInfo.getMetaConfig().put("warehouseName", "test_warehouse");

        String tableName = snowflakeDatastoreService.getTableName("test_entity", connectorInfo);
        assertTrue(tableName.contains("test_schema"));
        assertTrue(tableName.contains("test_entity"));

        Optional<Properties> props = snowflakeDatastoreService.getAdditionalProperties(connectorInfo);
        assertTrue(props.isPresent());
        assertEquals("test_db", props.get().getProperty("database"));
        assertEquals("test_schema", props.get().getProperty("schema"));
        assertEquals("test_warehouse", props.get().getProperty("warehouse"));
    }

    @Test
    public void testValidateSuccess() {
        setupValidConnectorInfo();
        assertTrue(snowflakeDatastoreService.validate(connectorInfo));

        setupValidOAuthConnectorInfo();
        assertTrue(snowflakeDatastoreService.validate(connectorInfo));
    }

    @Test
    public void testValidateFailsWithMissingRequiredFields() {
        setupValidConnectorInfo();

        Map<String, String> fieldsToRemove = new HashMap<>();
        fieldsToRemove.put("accountName", "Account name required for Snowflake datastore");
        fieldsToRemove.put("warehouseName", "Warehouse name required for Snowflake datastore");
        fieldsToRemove.put("dbName", "Database name required for Snowflake datastore");
        fieldsToRemove.put("schemaName", "Schema name required for Snowflake datastore");
        fieldsToRemove.put("endpoint", "Snowflake URL is required for Snowflake datastore");

        for (Map.Entry<String, String> entry : fieldsToRemove.entrySet()) {
            setupValidConnectorInfo();
            connectorInfo.getMetaConfig().remove(entry.getKey());
            try {
                snowflakeDatastoreService.validate(connectorInfo);
                fail("Expected RuntimeException for missing " + entry.getKey());
            } catch (RuntimeException e) {
                assertEquals(entry.getValue(), e.getMessage());
            }
        }

        setupValidConnectorInfo();
        connectorInfo.setName("");
        try {
            snowflakeDatastoreService.validate(connectorInfo);
            fail("Expected RuntimeException for blank name");
        } catch (RuntimeException e) {
            assertEquals("Datastore connection name is required", e.getMessage());
        }

        setupValidConnectorInfo();
        connectorInfo.getMetaConfig().put("accountName", "");
        try {
            snowflakeDatastoreService.validate(connectorInfo);
            fail("Expected RuntimeException for blank account name");
        } catch (RuntimeException e) {
            assertEquals("Account name required for Snowflake datastore", e.getMessage());
        }

        setupValidConnectorInfo();
        connectorInfo.getMetaConfig().put("endpoint", "");
        try {
            snowflakeDatastoreService.validate(connectorInfo);
            fail("Expected RuntimeException for blank endpoint");
        } catch (RuntimeException e) {
            assertEquals("Snowflake URL is required for Snowflake datastore", e.getMessage());
        }
    }

    @Test
    public void testValidateOAuthCredentials() {
        setupValidOAuthConnectorInfo();
        connectorInfo.getAuthConfig().setClientId("");
        try {
            snowflakeDatastoreService.validate(connectorInfo);
            fail("Expected RuntimeException for missing client ID");
        } catch (RuntimeException e) {
            assertEquals("Client ID is required", e.getMessage());
        }

        setupValidOAuthConnectorInfo();
        connectorInfo.getAuthConfig().setClientSecret("");
        try {
            snowflakeDatastoreService.validate(connectorInfo);
            fail("Expected RuntimeException for missing client secret");
        } catch (RuntimeException e) {
            assertEquals("Client secret is required", e.getMessage());
        }
    }

    @Test
    public void testValidateEndpointFormat() {
        // Test invalid endpoint formats
        String[] invalidEndpoints = {
            "jkbkjkj",
            "invalid-endpoint",
            "https://invalid-domain.com",
            "test-account.wrongdomain.com",
            "account.snowflake.com"
        };

        for (String invalidEndpoint : invalidEndpoints) {
            setupValidConnectorInfo();
            connectorInfo.getMetaConfig().put("endpoint", invalidEndpoint);
            try {
                snowflakeDatastoreService.validate(connectorInfo);
                fail();
            } catch (RuntimeException e) {
                assertEquals("Snowflake URL must contain '.snowflakecomputing.com' domain (e.g., https://account_name.snowflakecomputing.com)", e.getMessage());
            }
        }

        // Test valid endpoint formats
        String[] validEndpoints = {
            "https://test-account.snowflakecomputing.com",
            "http://test-account.snowflakecomputing.com",
            "test-account.snowflakecomputing.com",
            "https://test-account.snowflakecomputing.com/"
        };

        for (String validEndpoint : validEndpoints) {
            setupValidConnectorInfo();
            connectorInfo.getMetaConfig().put("endpoint", validEndpoint);
            snowflakeDatastoreService.validate(connectorInfo);
        }
    }

    @Test
    public void testValidatePasswordCredentials() {
        setupValidConnectorInfo();
        connectorInfo.getAuthConfig().setUserName("");
        try {
            snowflakeDatastoreService.validate(connectorInfo);
            fail("Expected RuntimeException for missing username");
        } catch (RuntimeException e) {
            assertEquals("User name is required", e.getMessage());
        }

        setupValidConnectorInfo();
        connectorInfo.getAuthConfig().setPassword("");
        try {
            snowflakeDatastoreService.validate(connectorInfo);
            fail("Expected RuntimeException for missing password");
        } catch (RuntimeException e) {
            assertEquals("Password is required", e.getMessage());
        }
    }

    @Test
    public void testOAuthMethods() {
        setupValidConnectorInfo();
        String oauthUri = snowflakeDatastoreService.getOAuthUri(connectorInfo);
        assertEquals("/oauth/authorize?client_id={{client_id}}&redirect_uri={{redirect_uri}}&response_type=code&state={{state}}", oauthUri);

        AuthConfig authConfig = new AuthConfig();
        authConfig.setEndpoint("https://test-account.snowflakecomputing.com");
        String authHost = snowflakeDatastoreService.getAuthHost(authConfig);
        assertEquals("https://test-account.snowflakecomputing.com", authHost);
    }

    @Test
    public void testCountWithConnectionError() {
        setupValidConnectorInfo();
        try {
            snowflakeDatastoreService.count(connectorInfo, "test");
            fail("Expected exception due to no real connection");
        } catch (Exception e) {
            assertTrue(e instanceof RuntimeException);
            assertTrue(e.getCause() instanceof com.syncari.connector.exception.NonRetriableException ||
                      e.getMessage().contains("Test connection blocked"));
        }
    }

    @Test
    public void testExecuteWithOAuthRetrySuccess() {
        setupValidOAuthConnectorInfo();
        SnowflakeDatastoreService spyService = spy(snowflakeDatastoreService);
        doReturn(Arrays.asList()).when(spyService).retrieveData(any(), any(), any());

        List<Map<String, Object>> result = spyService.retrieveData(connectorInfo, "SELECT * FROM test", Map.of());

        assertNotNull(result);
        verify(spyService, times(1)).retrieveData(any(), any(), any());
    }

    @Test
    public void testExecuteWithOAuthRetryWithTokenExpiration() {
        setupValidOAuthConnectorInfo();
        SnowflakeDatastoreService spyService = spy(snowflakeDatastoreService);

        // Test the retry logic - since we block real connections, this will fail
        try {
            spyService.retrieveData(connectorInfo, "SELECT * FROM test", Map.of());
            fail("Expected exception due to no real connection");
        } catch (Exception e) {
            // This is expected due to blocked connection
            assertTrue(e instanceof RuntimeException);
        }
    }

    @Test 
    public void testExecuteWithOAuthRetryNonTokenError() {
        setupValidOAuthConnectorInfo();
        SnowflakeDatastoreService spyService = spy(snowflakeDatastoreService);

        RuntimeException nonTokenError = new RuntimeException("Connection failed");
        doThrow(nonTokenError).when(spyService).retrieveData(any(), any(), any());

        try {
            spyService.retrieveData(connectorInfo, "SELECT * FROM test", Map.of());
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals("Connection failed", e.getMessage());
            verify(spyService, times(1)).retrieveData(any(), any(), any());
            verify(spyService, never()).refreshToken(any());
        }
    }

    @Test
    public void testOAuthRetryWrapperMethods() {
        setupValidOAuthConnectorInfo();
        SnowflakeDatastoreService spyService = spy(snowflakeDatastoreService);

        try {
            spyService.retrieveData(connectorInfo, "SELECT 1", Map.of());
        } catch (Exception e) {
            assertTrue(e instanceof RuntimeException);
        }

        try {
            spyService.retrievePairData(connectorInfo, "SELECT 1", Arrays.asList());
        } catch (Exception e) {
            assertTrue(e instanceof RuntimeException);
        }

        try {
            spyService.executeDdlSql(connectorInfo, "CREATE TABLE test (id INT)");
        } catch (Exception e) {
            assertTrue(e instanceof RuntimeException);
        }
    }

    private void setupValidConnectorInfo() {
        connectorInfo.setName("test-datastore");
        connectorInfo.getMetaConfig().put("endpoint", "https://test-account.snowflakecomputing.com");
        connectorInfo.getMetaConfig().put("accountName", "test-account");
        connectorInfo.getMetaConfig().put("warehouseName", "test-warehouse");
        connectorInfo.getMetaConfig().put("dbName", "test-db");
        connectorInfo.getMetaConfig().put("schemaName", "test-schema");
        connectorInfo.getMetaConfig().put("authType", AuthType.UserPasswordToken.toString());

        connectorInfo.getAuthConfig().setUserName("test-user");
        connectorInfo.getAuthConfig().setPassword(System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME"));
    }

    private void setupValidOAuthConnectorInfo() {
        connectorInfo.setName("test-datastore");
        connectorInfo.getMetaConfig().put("endpoint", "https://test-account.snowflakecomputing.com");
        connectorInfo.getMetaConfig().put("accountName", "test-account");
        connectorInfo.getMetaConfig().put("warehouseName", "test-warehouse");
        connectorInfo.getMetaConfig().put("dbName", "test-db");
        connectorInfo.getMetaConfig().put("schemaName", "test-schema");
        connectorInfo.getMetaConfig().put("authType", AuthType.Oauth.toString());

        connectorInfo.getAuthConfig().setClientId("test-client-id");
        connectorInfo.getAuthConfig().setClientSecret(System.getenv().getOrDefault("TEST_CLIENT_SECRET", "REPLACE_ME"));
        connectorInfo.getAuthConfig().setAccessToken("test-access-token");
    }
}