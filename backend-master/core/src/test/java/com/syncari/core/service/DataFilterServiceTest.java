package com.syncari.core.service;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Map;

import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.DataFilter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DataFilterServiceTest extends AbstractSyncariTest {
    @Autowired
    DataFilterService service;
    
    @Override
    public void setUp() {
        super.setUp();
    }

    @After
    public void tearDown() {
        super.tearDown();
    }

    @Test
    public void validations() {
        try {
            service.create(null);
            fail();
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Filter is required"));
        }
        try {
            service.create(new DataFilter());
            fail();
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Filter name is required"));
        }
        try {
            service.create(new DataFilter().setName("test").setCriteria(Map.of()));
            fail();
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Filter entity is required"));
        }
        try {
            service.create(new DataFilter().setName("test").setSyncariEntityId("123"));
            fail();
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Filter criteria is required"));
        }
    }

}
