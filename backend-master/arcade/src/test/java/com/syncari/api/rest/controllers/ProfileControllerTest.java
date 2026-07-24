package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.*;
import static org.junit.Assert.*;

import org.apache.commons.collections4.CollectionUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import com.syncari.core.repositories.customer.UserPreferenceRepo;
import com.syncari.utils.KeyValue;

import java.util.Map;

public class ProfileControllerTest extends AbstractSyncariTest {

    @Autowired 
    private ProfileController controller;

    @Autowired
    UserPreferenceRepo userPreferenceRepo;

    @Override
    public void setUp() {
        super.setUp();
        userPreferenceRepo.deleteAll();
        pushContext();
    }
    
    @Override
    public void tearDown() {
        restoreContext();
        userPreferenceRepo.deleteAll();
        super.tearDown();
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_PROFILE, WRITE_PROFILE})
    public void setGetCustomPreference() throws Exception {
        var customPreference = controller.updateCustomPreference(KeyValue.of("MyCustomPreference", "customPreference"));
        assertEquals("customPreference", customPreference.get("MyCustomPreference"));

        assertEquals("customPreference", controller.getCustomPreference().get("MyCustomPreference"));

        assertEquals("newCustomPreference", 
            controller.updateCustomPreference(KeyValue.of("MyCustomPreference", "newCustomPreference")).get("MyCustomPreference")
        );

        assertNull(controller.updateCustomPreference(KeyValue.of("MyCustomPreference", null)).get("MyCustomPreference"));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_STUDIO, WRITE_STUDIO})
    public void testAddOrUpdateAllowedRealtimeIps() throws Exception {
        var allowedRealtimeIps = controller.addOrUpdateAllowedRealtimeIps("{\"ipWhitelist\": \"10.1.1.1 \\n10.2.2.2\"}");
        assertNotNull(allowedRealtimeIps);
        assertEquals(Map.of("ipWhitelist","10.1.1.1 \n10.2.2.2").get("ipWhitelist"), allowedRealtimeIps.get("ipWhitelist"));
        assertEquals(Map.of("ipWhitelist","10.1.1.1 \n10.2.2.2").get("ipWhitelist"),controller.listAllowedRealtimeIps().get("ipWhitelist"));

    }
}
