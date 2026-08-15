package com.syncari.api.rest.controllers;

import com.syncari.api.core.util.Util;
import com.syncari.api.rest.config.security.SecurityConstants;
import com.syncari.core.SyncariContext;
import com.syncari.core.SyncariContextHandler;
import com.syncari.core.model.User;
import com.syncari.core.model.util.Status;
import com.syncari.core.service.UserService;
import com.syncari.core.service.authz.AuthzService;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class GoogleAuthControllerTest {
    @Mock
    private GoogleIdentityVerifier googleIdentityVerifier;
    @Mock
    private UserService userService;
    @Mock
    private AuthzService authzService;
    @Mock
    private SyncariContextHandler syncariContextHandler;
    @Mock
    private Util util;
    @InjectMocks
    private GoogleAuthController controller;

    @After
    public void tearDown() {
        SyncariContext.resetAll();
    }

    @Test
    public void configExposesOnlyPublicClientId() {
        when(googleIdentityVerifier.isEnabled()).thenReturn(true);
        when(googleIdentityVerifier.getClientId()).thenReturn("public-client-id");

        Map<String, Object> config = controller.config();

        assertEquals(true, config.get("enabled"));
        assertEquals("public-client-id", config.get("clientId"));
        assertEquals(2, config.size());
    }

    @Test
    public void existingTenantUserReceivesNextEdgeSession() throws Exception {
        GoogleAuthController.GoogleAuthRequest authRequest = new GoogleAuthController.GoogleAuthRequest();
        authRequest.setCredential("verified-google-token");
        User user = new User("user@example.com", "unused-password", Status.ACTIVE, "tenant-a");
        user.addAvailableInstance("tenant-a");
        when(googleIdentityVerifier.verifyEmail("verified-google-token")).thenReturn(Optional.of("user@example.com"));
        when(userService.findActiveUserByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(authzService.listPrivileges("user@example.com")).thenReturn(Stream.of("READ_PROFILE"));
        when(util.getTokenAndPersistLoginDetails(any(User.class), anyList(), anyBoolean(), anyString(), anyString()))
                .thenReturn("nextedge-jwt");
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        ResponseEntity<Map<String, Object>> response = controller.authenticate(
                authRequest, new MockHttpServletRequest(), servletResponse);

        assertEquals(200, response.getStatusCodeValue());
        assertTrue((Boolean) response.getBody().get("success"));
        assertEquals(SecurityConstants.TOKEN_PREFIX + "nextedge-jwt",
                servletResponse.getHeader(SecurityConstants.TOKEN_HEADER));
    }

    @Test
    public void unprovisionedGoogleAccountIsRejectedWithoutSession() throws Exception {
        GoogleAuthController.GoogleAuthRequest authRequest = new GoogleAuthController.GoogleAuthRequest();
        authRequest.setCredential("verified-google-token");
        when(googleIdentityVerifier.verifyEmail("verified-google-token")).thenReturn(Optional.of("unknown@example.com"));
        when(userService.findActiveUserByEmail("unknown@example.com")).thenReturn(Optional.empty());
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        ResponseEntity<Map<String, Object>> response = controller.authenticate(
                authRequest, new MockHttpServletRequest(), servletResponse);

        assertEquals(401, response.getStatusCodeValue());
        assertNotNull(response.getBody().get("message"));
        assertEquals(null, servletResponse.getHeader(SecurityConstants.TOKEN_HEADER));
    }
}
