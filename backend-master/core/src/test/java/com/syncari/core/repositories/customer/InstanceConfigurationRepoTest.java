package com.syncari.core.repositories.customer;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.InstanceConfiguration;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class InstanceConfigurationRepoTest extends AbstractSyncariTest {

    @Autowired
    private InstanceConfigurationRepo instanceConfigurationRepo;

    @Override
    public void setUp() {
        super.setUp();
        // Clean up any existing test configurations
        instanceConfigurationRepo.findByKey("test_key_1").ifPresent(instanceConfigurationRepo::delete);
        instanceConfigurationRepo.findByKey("test_key_2").ifPresent(instanceConfigurationRepo::delete);
        instanceConfigurationRepo.findByKey("test_key_3").ifPresent(instanceConfigurationRepo::delete);
    }

    @Override
    public void tearDown() {
        // Clean up test configurations
        instanceConfigurationRepo.findByKey("test_key_1").ifPresent(instanceConfigurationRepo::delete);
        instanceConfigurationRepo.findByKey("test_key_2").ifPresent(instanceConfigurationRepo::delete);
        instanceConfigurationRepo.findByKey("test_key_3").ifPresent(instanceConfigurationRepo::delete);
    }

    @Test
    public void testFindByKeyIn_SingleKey() {
        // Create test configuration
        InstanceConfiguration config = new InstanceConfiguration();
        config.setKey("test_key_1");
        config.setValue("test_value_1");
        instanceConfigurationRepo.save(config);

        // Query by single key
        List<InstanceConfiguration> results = instanceConfigurationRepo.findByKeyIn(Arrays.asList("test_key_1"));

        assertEquals(1, results.size());
        assertEquals("test_key_1", results.get(0).getKey());
        assertEquals("test_value_1", results.get(0).getValue());
    }

    @Test
    public void testFindByKeyIn_MultipleKeys() {
        // Create multiple test configurations
        InstanceConfiguration config1 = new InstanceConfiguration();
        config1.setKey("test_key_1");
        config1.setValue("test_value_1");
        instanceConfigurationRepo.save(config1);

        InstanceConfiguration config2 = new InstanceConfiguration();
        config2.setKey("test_key_2");
        config2.setValue("test_value_2");
        instanceConfigurationRepo.save(config2);

        InstanceConfiguration config3 = new InstanceConfiguration();
        config3.setKey("test_key_3");
        config3.setValue("test_value_3");
        instanceConfigurationRepo.save(config3);

        // Query by multiple keys
        List<InstanceConfiguration> results = instanceConfigurationRepo.findByKeyIn(
                Arrays.asList("test_key_1", "test_key_2")
        );

        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(c -> "test_key_1".equals(c.getKey())));
        assertTrue(results.stream().anyMatch(c -> "test_key_2".equals(c.getKey())));
        assertFalse(results.stream().anyMatch(c -> "test_key_3".equals(c.getKey())));
    }

    @Test
    public void testFindByKeyIn_NonExistentKeys() {
        // Query for non-existent keys
        List<InstanceConfiguration> results = instanceConfigurationRepo.findByKeyIn(
                Arrays.asList("non_existent_key_1", "non_existent_key_2")
        );

        assertEquals(0, results.size());
    }

    @Test
    public void testFindByKeyIn_MixedExistentAndNonExistent() {
        // Create one test configuration
        InstanceConfiguration config = new InstanceConfiguration();
        config.setKey("test_key_1");
        config.setValue("test_value_1");
        instanceConfigurationRepo.save(config);

        // Query with mix of existent and non-existent keys
        List<InstanceConfiguration> results = instanceConfigurationRepo.findByKeyIn(
                Arrays.asList("test_key_1", "non_existent_key")
        );

        assertEquals(1, results.size());
        assertEquals("test_key_1", results.get(0).getKey());
    }

    @Test
    public void testFindByKeyIn_EmptyList() {
        // Query with empty list
        List<InstanceConfiguration> results = instanceConfigurationRepo.findByKeyIn(Arrays.asList());

        assertEquals(0, results.size());
    }

    @Test
    public void testFindByKeyIn_DebugModeKeys() {
        // Test with actual debug mode keys
        List<InstanceConfiguration> results = instanceConfigurationRepo.findByKeyIn(
                Arrays.asList(InstanceConfiguration.DEBUG_MODE, InstanceConfiguration.DEBUG_MODE_EXPIRY_SECS)
        );

        // Should return existing debug mode configurations
        assertNotNull(results);
        assertTrue(results.size() <= 2);
    }
}
