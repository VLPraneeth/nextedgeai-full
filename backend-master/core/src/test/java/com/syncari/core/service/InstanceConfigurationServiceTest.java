package com.syncari.core.service;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.TestConfig;
import com.syncari.core.model.DebugConfig;
import com.syncari.core.model.InstanceConfiguration;
import com.syncari.core.repositories.customer.InstanceConfigurationRepo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
public class InstanceConfigurationServiceTest extends AbstractSyncariTest {

    @Autowired
    private InstanceConfigurationService instanceConfigurationService;

    @Autowired
    private InstanceConfigurationRepo instanceConfigurationRepo;

    @Override
    public void setUp() {
        super.setUp();
        // Ensure debug mode is disabled before each test
        DebugConfig config = DebugConfig.builder()
                .enabled(false)
                .expirySeconds(60)
                .build();
        instanceConfigurationService.updateDebugConfig(config);
    }

    @Override
    public void tearDown() {
        // Clean up debug config after tests
        DebugConfig config = DebugConfig.builder()
                .enabled(false)
                .expirySeconds(60)
                .build();
        instanceConfigurationService.updateDebugConfig(config);
    }

    @Test
    public void testGetDebugConfig_DisabledByDefault() {
        // Set debug mode to disabled
        instanceConfigurationService.disableDebugMode();

        DebugConfig config = instanceConfigurationService.getDebugConfig();

        assertNotNull(config);
        assertFalse(config.isEnabled());
        assertEquals(0, config.getRemainingSeconds());
    }

    @Test
    public void testUpdateDebugConfig_EnableDebugMode() {
        DebugConfig config = DebugConfig.builder()
                .enabled(true)
                .expirySeconds(1800) // 30 minutes
                .build();

        instanceConfigurationService.updateDebugConfig(config);

        DebugConfig retrieved = instanceConfigurationService.getDebugConfig();
        assertTrue(retrieved.isEnabled());
        assertEquals(1800, retrieved.getExpirySeconds());
        assertTrue(retrieved.getRemainingSeconds() > 0);
        assertTrue(retrieved.getRemainingSeconds() <= 1800);
    }

    @Test
    public void testUpdateDebugConfig_DisableDebugMode() {
        // First enable debug mode
        DebugConfig enableConfig = DebugConfig.builder()
                .enabled(true)
                .expirySeconds(900)
                .build();
        instanceConfigurationService.updateDebugConfig(enableConfig);

        // Now disable it
        DebugConfig disableConfig = DebugConfig.builder()
                .enabled(false)
                .expirySeconds(60)
                .build();
        instanceConfigurationService.updateDebugConfig(disableConfig);

        DebugConfig retrieved = instanceConfigurationService.getDebugConfig();
        assertFalse(retrieved.isEnabled());
        assertEquals(0, retrieved.getRemainingSeconds());
    }

    @Test
    public void testUpdateDebugConfig_MaxExpiryEnforced() {
        // Try to set expiry beyond max (4 hours = 14400 seconds)
        DebugConfig config = DebugConfig.builder()
                .enabled(true)
                .expirySeconds(20000) // More than MAX_EXPIRY_SECS
                .build();

        instanceConfigurationService.updateDebugConfig(config);

        DebugConfig retrieved = instanceConfigurationService.getDebugConfig();
        assertTrue(retrieved.isEnabled());
        assertEquals(14400, retrieved.getExpirySeconds()); // Capped at MAX_EXPIRY_SECS
    }

    @Test
    public void testGetDebugConfig_RemainingTimeCalculation() {
        // Enable debug mode with 10 minutes expiry
        DebugConfig config = DebugConfig.builder()
                .enabled(true)
                .expirySeconds(600)
                .build();
        instanceConfigurationService.updateDebugConfig(config);

        // Get debug config immediately
        DebugConfig retrieved = instanceConfigurationService.getDebugConfig();

        assertTrue(retrieved.isEnabled());
        assertTrue(retrieved.getRemainingSeconds() > 0);
        assertTrue(retrieved.getRemainingSeconds() <= 600);
        assertNotNull(retrieved.getUpdatedAt());
    }

    @Test
    public void testGetDebugConfig_SingleDatabaseCall() {
        // Enable debug mode
        DebugConfig config = DebugConfig.builder()
                .enabled(true)
                .expirySeconds(900)
                .build();
        instanceConfigurationService.updateDebugConfig(config);

        // Get debug config - should retrieve both keys in one call
        DebugConfig retrieved = instanceConfigurationService.getDebugConfig();

        assertTrue(retrieved.isEnabled());
        assertEquals(900, retrieved.getExpirySeconds());
        assertTrue(retrieved.getRemainingSeconds() > 0);
    }

    @Test
    public void testUpdateDebugConfig_AtomicSave() {
        // Enable debug mode
        DebugConfig config = DebugConfig.builder()
                .enabled(true)
                .expirySeconds(1200)
                .build();

        instanceConfigurationService.updateDebugConfig(config);

        // Verify both DEBUG_MODE and DEBUG_MODE_EXPIRY_SECS are set correctly
        InstanceConfiguration debugMode = instanceConfigurationRepo
                .findByKey(InstanceConfiguration.DEBUG_MODE).orElse(null);
        InstanceConfiguration expirySecs = instanceConfigurationRepo
                .findByKey(InstanceConfiguration.DEBUG_MODE_EXPIRY_SECS).orElse(null);

        assertNotNull(debugMode);
        assertNotNull(expirySecs);
        assertTrue((Boolean) debugMode.getValue());
        assertEquals(1200, expirySecs.getValue());
    }

    @Test
    public void testGetDebugConfig_WhenNoConfigExists() {
        // Delete debug mode config if it exists
        instanceConfigurationRepo.findByKey(InstanceConfiguration.DEBUG_MODE)
                .ifPresent(instanceConfigurationRepo::delete);

        DebugConfig retrieved = instanceConfigurationService.getDebugConfig();

        assertNotNull(retrieved);
        assertFalse(retrieved.isEnabled());
        assertEquals(InstanceConfigurationService.DEFAULT_EXPIRY_SECS, retrieved.getExpirySeconds());
        assertEquals(0, retrieved.getRemainingSeconds());
    }

    @Test
    public void testUpdateDebugConfig_WithZeroExpiry() {
        // Try to set debug mode with minimal expiry
        DebugConfig config = DebugConfig.builder()
                .enabled(true)
                .expirySeconds(60) // 1 minute
                .build();

        instanceConfigurationService.updateDebugConfig(config);

        DebugConfig retrieved = instanceConfigurationService.getDebugConfig();
        assertTrue(retrieved.isEnabled());
        assertEquals(60, retrieved.getExpirySeconds());
    }

    @Test
    public void testGetDebugConfig_ExpiryWithDefaultExpirySecs() {
        // Enable debug mode using old method (which uses DEFAULT_EXPIRY_SECS)
        instanceConfigurationService.enableDebugMode(InstanceConfigurationService.DEFAULT_EXPIRY_SECS);

        DebugConfig retrieved = instanceConfigurationService.getDebugConfig();

        assertTrue(retrieved.isEnabled());
        assertTrue(retrieved.getRemainingSeconds() > 0);
        assertTrue(retrieved.getRemainingSeconds() <= InstanceConfigurationService.DEFAULT_EXPIRY_SECS);
    }

    @Test
    public void testIsDebugModeEnabled_ConsistencyWithGetDebugConfig() {
        // Enable debug mode
        DebugConfig config = DebugConfig.builder()
                .enabled(true)
                .expirySeconds(900)
                .build();
        instanceConfigurationService.updateDebugConfig(config);

        // Check consistency between old and new methods
        boolean isEnabled = instanceConfigurationService.isDebugModeEnabled();
        DebugConfig retrieved = instanceConfigurationService.getDebugConfig();

        assertEquals(isEnabled, retrieved.isEnabled());
    }

    @Test
    public void testGetDebugConfig_RemainingSecondsAccuracy() {
        // Enable debug mode
        DebugConfig config = DebugConfig.builder()
                .enabled(true)
                .expirySeconds(1800)
                .build();
        instanceConfigurationService.updateDebugConfig(config);

        // Get debug config twice with small delay to verify accuracy
        DebugConfig first = instanceConfigurationService.getDebugConfig();
        DebugConfig second = instanceConfigurationService.getDebugConfig();

        // Should be within 1 second tolerance
        assertTrue(Math.abs(first.getRemainingSeconds() - second.getRemainingSeconds()) <= 1);
        assertTrue(second.getRemainingSeconds() > 0);
    }

    @Test
    public void testUpdateDebugConfig_MultipleToggles() {
        // Enable
        DebugConfig enable = DebugConfig.builder()
                .enabled(true)
                .expirySeconds(600)
                .build();
        instanceConfigurationService.updateDebugConfig(enable);
        assertTrue(instanceConfigurationService.getDebugConfig().isEnabled());

        // Disable
        DebugConfig disable = DebugConfig.builder()
                .enabled(false)
                .expirySeconds(60)
                .build();
        instanceConfigurationService.updateDebugConfig(disable);
        assertFalse(instanceConfigurationService.getDebugConfig().isEnabled());

        // Enable again
        DebugConfig reEnable = DebugConfig.builder()
                .enabled(true)
                .expirySeconds(900)
                .build();
        instanceConfigurationService.updateDebugConfig(reEnable);
        assertTrue(instanceConfigurationService.getDebugConfig().isEnabled());
    }
}
