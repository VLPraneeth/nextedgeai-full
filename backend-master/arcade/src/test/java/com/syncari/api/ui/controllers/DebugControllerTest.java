package com.syncari.api.ui.controllers;

import com.syncari.api.rest.controllers.AbstractSyncariTest;
import com.syncari.core.model.DebugConfig;
import com.syncari.core.service.InstanceConfigurationService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static com.syncari.core.security.Permissions.EDIT_DEBUG_MODE;
import static com.syncari.core.security.Permissions.READ_DEBUG_MODE;
import static org.junit.Assert.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class DebugControllerTest extends AbstractSyncariTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DebugController controller;

    @Autowired
    private InstanceConfigurationService instanceConfigurationService;

    @Override
    public void setUp() {
        super.setUp();
        // Ensure debug mode is disabled before each test
        DebugConfig config = DebugConfig.builder()
                .enabled(false)
                .expirySeconds(60)
                .build();
        instanceConfigurationService.updateDebugConfig(config);
        pushContext();
    }

    @Override
    public void tearDown() {
        restoreContext();
        // Clean up debug config after tests
        DebugConfig config = DebugConfig.builder()
                .enabled(false)
                .expirySeconds(60)
                .build();
        instanceConfigurationService.updateDebugConfig(config);
        super.tearDown();
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DEBUG_MODE})
    public void testGetDebug_WithPermission() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/ui/debug"))
                .andExpect(status().isOk())
                .andExpect(view().name("debug"))
                .andExpect(model().attributeExists("debugEnabled"))
                .andExpect(model().attributeExists("remainingSeconds"))
                .andExpect(model().attributeExists("now"));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {})
    public void testGetDebug_WithoutPermission() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/ui/debug"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DEBUG_MODE})
    public void testGetDebug_ReturnsCorrectState() throws Exception {
        // Enable debug mode first
        DebugConfig enabledConfig = DebugConfig.builder()
                .enabled(true)
                .expirySeconds(900)
                .build();
        instanceConfigurationService.updateDebugConfig(enabledConfig);

        mockMvc.perform(MockMvcRequestBuilders.get("/ui/debug"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("debugEnabled", true))
                .andExpect(model().attributeExists("remainingSeconds"));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {EDIT_DEBUG_MODE})
    public void testPostDebug_EnableDebugMode() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/ui/debug")
                        .with(csrf())
                        .param("debugMode", "true")
                        .param("expiryMinutes", "30"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("messageType", "success"))
                .andExpect(model().attribute("debugEnabled", true))
                .andExpect(model().attributeExists("message"));
        withContext(() -> {
            // Verify debug mode is enabled
            DebugConfig config = instanceConfigurationService.getDebugConfig();
            assertTrue(config.isEnabled());
            assertEquals(1800, config.getExpirySeconds()); // 30 minutes * 60

        });
    }


    @Test
    @WithMockUser(username = "test@email.com", authorities = {EDIT_DEBUG_MODE})
    public void testPostDebug_DisableDebugMode() throws Exception {
        // First enable debug mode
        DebugConfig enabledConfig = DebugConfig.builder()
                .enabled(true)
                .expirySeconds(900)
                .build();
        instanceConfigurationService.updateDebugConfig(enabledConfig);

        // Now disable it
        mockMvc.perform(MockMvcRequestBuilders.post("/ui/debug")
                        .with(csrf())
                        .param("expiryMinutes", "15"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("messageType", "success"))
                .andExpect(model().attribute("debugEnabled", false))
                .andExpect(model().attributeExists("message"));
        withContext(() -> {

            // Verify debug mode is disabled
            DebugConfig config = instanceConfigurationService.getDebugConfig();
            assertFalse(config.isEnabled());
        });
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {})
    public void testPostDebug_WithoutPermission() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/ui/debug")
                        .with(csrf())
                        .param("debugMode", "true")
                        .param("expiryMinutes", "15"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {EDIT_DEBUG_MODE})
    public void testPostDebug_MaxExpiryLimitEnforced() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/ui/debug")
                        .with(csrf())
                        .param("debugMode", "true")
                        .param("expiryMinutes", "300")) // 5 hours, exceeds 4 hour max
                .andExpect(status().isOk())
                .andExpect(model().attribute("debugEnabled", true));
        withContext(() -> {

            // Verify expiry is capped at MAX_EXPIRY_SECS (4 hours = 14400 seconds)
            DebugConfig config = instanceConfigurationService.getDebugConfig();
            assertTrue(config.isEnabled());
            assertEquals(14400, config.getExpirySeconds()); // Capped at 4 hours
        });
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DEBUG_MODE})
    public void testGetDebug_WithHXTargetHeader() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/ui/debug")
                        .header("HX-Target", "content"))
                .andExpect(status().isOk())
                .andExpect(view().name("debug :: content"));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {EDIT_DEBUG_MODE})
    public void testPostDebug_WithHXTargetHeader() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/ui/debug")
                        .with(csrf())
                        .header("HX-Target", "content")
                        .param("debugMode", "true")
                        .param("expiryMinutes", "15"))
                .andExpect(status().isOk())
                .andExpect(view().name("debug :: content"));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {EDIT_DEBUG_MODE})
    public void testPostDebug_RemainingTimeCalculation() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/ui/debug")
                        .with(csrf())
                        .param("debugMode", "true")
                        .param("expiryMinutes", "15"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("debugEnabled", true));
        withContext(() -> {

            // Verify remaining seconds is greater than 0
            DebugConfig config = instanceConfigurationService.getDebugConfig();
            assertTrue(config.getRemainingSeconds() > 0);
            assertTrue(config.getRemainingSeconds() <= 900); // 15 minutes
        });
    }
}
