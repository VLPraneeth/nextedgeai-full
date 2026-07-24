package com.syncari.connector.service;

import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.database.SnowflakeService;
import com.syncari.connector.exception.NonRetriableException;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.time.Instant;
import java.util.List;

import static org.junit.Assert.*;
import static org.junit.Assume.*;

/**
 * Test class to reproduce the Snowflake SQL compilation error from production:
 * "syntax error line 1 at position 212 unexpected 'donnell'"
 * 
 * Root cause: Emails with apostrophes used as ID fields in cursor-based pagination
 * generate malformed SQL due to unescaped single quotes in the formatId method.
 */
@Slf4j
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {ConnectorConfig.class, TestConfig.class})
@ComponentScan(basePackages = "com.syncari")
public class SnowflakeApostropheErrorTest {

    @Autowired
    @Qualifier(Constants.SNOWFLAKE)
    SnowflakeService service;
    
    private ConnectorInfo connector;
    private EntitySchema entitySchema;

    @Before
    public void setUp() {
        connector = createTestConnector();
        entitySchema = createEmailIdEntitySchema();
    }

    /**
     * Test that emails with apostrophes in ID fields should work correctly in cursor-based pagination.
     * 
     * This test will FAIL when the bug is present (unescaped quotes cause SQL compilation error)
     * and PASS when the bug is fixed (quotes are properly escaped in the formatId method).
     * 
     * Production error: "syntax error line 1 at position 212 unexpected 'donnell'"
     */
    @Test
    public void testEmailsWithApostrophesInIdFieldsShouldWork() {
        String problematicEmail = "o'donnell@company.com";  // Regular ASCII single quote
        String nextID = "1723605574899#" + problematicEmail;
        
        log.info("Testing regular single quote email: {} (char code: {})", problematicEmail, (int) problematicEmail.charAt(1));
        
        // Create request with the problematic nextID in the changeStream
        WatermarkInfo watermark = new WatermarkInfo(
            Instant.parse("2025-01-01T00:00:00Z").toEpochMilli(),
            Instant.parse("2025-08-28T23:59:59Z").toEpochMilli(),
            false, 0
        );
        watermark.setChangeStream(nextID);
        
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(watermark);
        request.setPageSize(100);
        
        try {
            // Generate the watermark condition (this creates malformed SQL)
            String watermarkCondition = service.getCursorWatermarkCondition(request, nextID, 1000);
            log.info("Generated SQL: {}", watermarkCondition);
            
            try {
                log.info("CURSOR_TEST: About to call service.getByCursorBasedWatermark()");
                FetchResponse response = service.getByCursorBasedWatermark(request);
                log.info("CURSOR_TEST: getByCursorBasedWatermark() returned successfully");
                
                // CRITICAL: The SQL is only executed when we iterate through the results!
                log.info("CURSOR_TEST: About to iterate through results to trigger SQL execution");
                if (response.getIterator().hasNext()) {
                    log.info("CURSOR_TEST: Iterator has data, calling next()");
                    List<EntityData> dataList = response.getIterator().next();
                    log.info("CURSOR_TEST: Retrieved {} records successfully", dataList.size());
                } else {
                    log.info("CURSOR_TEST: Iterator is empty but SQL executed successfully");
                }
                
                // If we reach here, the query executed successfully (this is what we want after the fix)
                log.info("SUCCESS: Query executed without SQL compilation error - bug is fixed!");
                assertTrue("Email with apostrophe processed successfully", true);
                
            } catch (NonRetriableException e) {
                // This indicates the bug is still present
                if (e.getMessage().contains("SQL compilation error") && 
                    (e.getMessage().contains("unexpected") || e.getMessage().contains("donnell"))) {
                    
                    log.error("FAILURE: SQL compilation error still occurs - bug is present!");
                    log.error("Error: {}", e.getMessage());
                    fail("BUG DETECTED: Emails with apostrophes cause SQL compilation error. " +
                         "The formatId method needs to properly escape single quotes. Error: " + e.getMessage());
                } else {
                    // Different type of NonRetriableException - re-throw
                    throw e;
                }
                
            } catch (Exception e) {
                log.error("Different error type: {} - {}", e.getClass().getSimpleName(), e.getMessage());
                
                if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                    // Table doesn't exist - this is a test setup issue, not the bug we're testing
                    log.warn("Table doesn't exist - test cannot verify the fix without actual table");
                    // Skip this test if table doesn't exist
                    assumeTrue("Test requires existing table", false);
                    return;
                }
                throw e;
            }
            
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
            throw e;
        }
    }

    //query based on cursor iteration is revised to fetch results form snowflake as expected
    @Test
    public void watermarkQueryConditionTest() {
        ConnectorInfo connectorInfo = createTestConnector();
        EntitySchema entitySchema = new EntitySchema("ORGANIZATION");
        entitySchema.setAttributes(List.of(
                new AttributeSchema("ORGANIZATION_ID", "string").setIdField(true)
                , new AttributeSchema("NAME", "string")
                , new AttributeSchema("UPDATED_AT", "datetime").setWatermarkField(true)
        ));
        SyncRequest syncRequest = new SyncRequest().Builder(connectorInfo, entitySchema).setWatermark(
                new WatermarkInfo(1723586965000L, 1723610573556L, false, 0)
                        .setChangeStream("1723605574899#5080")
                        .setStreamState(new StreamState().setLastModified(1718298685679L))
        );
        String cursorBasedQuery = service.getCursorWatermarkCondition(syncRequest, "1723605574899#5080", 1000);
        assertTrue(cursorBasedQuery
                .contains("\"UPDATED_AT\" > '2024-06-13 17:11:25.679 +0000' AND \"UPDATED_AT\" <= '2024-08-14 04:42:53.556 +0000' " +
                        "AND \"UPDATED_AT\" >= '2024-08-14 03:19:34.899 +0000' AND \"ORGANIZATION_ID\" > '5080' " +
                        "ORDER BY \"UPDATED_AT\",\"ORGANIZATION_ID\" LIMIT 1000 "));
    }

    /**
     * Create test connector with placeholder credentials (to be replaced with actual credentials)
     */
    private ConnectorInfo createTestConnector() {
        ConnectorInfo connector = new ConnectorInfo();
        connector.setId("test-connector-123");
        
        // TODO: Replace with actual Snowflake credentials
        AuthConfig authConfig = new AuthConfig("sibinsv", "UfapCwxXhH4Ket6", null);
        // OR for OAuth:
        // authConfig.setAccessToken("YOUR_ACCESS_TOKEN_HERE");
        // authConfig.setRefreshToken(System.getenv().getOrDefault("TEST_REFRESH_TOKEN", "REPLACE_ME"));
        // authConfig.setClientId("YOUR_CLIENT_ID_HERE");
        // authConfig.setClientSecret(System.getenv().getOrDefault("TEST_CLIENT_SECRET", "REPLACE_ME"));
        authConfig.setEndpoint("https://YLXYSBA-YK15570.snowflakecomputing.com");
        
        connector.setAuthConfig(authConfig);
        
        // TODO: Replace with actual Snowflake configuration
        connector.getMetaConfig().put("accountName", "YLXYSBA-YK15570");
        connector.getMetaConfig().put("warehouseName", "COMPUTE_WH");
        connector.getMetaConfig().put("dbName", "SIBIN1");
        connector.getMetaConfig().put("schemaName", "PUBLIC");
        connector.getMetaConfig().put("role", "ACCOUNTADMIN");
        connector.getMetaConfig().put("timeZoneId", "UTC");
        
        return connector;
    }

    /**
     * Create entity schema with email as ID field and timestamp as watermark
     */
    private EntitySchema createEmailIdEntitySchema() {
        EntitySchema schema = new EntitySchema("TEST_USERS");
        schema.setAttributes(List.of(
            new AttributeSchema("ID", "number"),
            new AttributeSchema("USERNAME", "string"),
            new AttributeSchema("EMAIL", "string").setIdField(true),  // Email as ID field
            new AttributeSchema("CREATED", "timestamp").setWatermarkField(true),
            new AttributeSchema("UPDATED", "timestamp")
        ));
        return schema;
    }

    /**
     * Create sync request for testing cursor-based watermark conditions
     */
    private SyncRequest createSyncRequest(String nextID) {
        WatermarkInfo watermark = new WatermarkInfo(
            Instant.parse("2025-01-01T00:00:00Z").toEpochMilli(),
            Instant.parse("2025-08-28T23:59:59Z").toEpochMilli(),
            false, // not a resync
            0
        );
        watermark.setChangeStream(nextID);
        
        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        request.setWatermark(watermark);
        return request;
    }
}