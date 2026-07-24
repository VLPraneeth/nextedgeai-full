package com.syncari.api.rest.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.api.core.util.Util;
import com.syncari.api.rest.controllers.data.ClientRegistrationRequest;
import com.syncari.api.rest.controllers.data.ClientRegistrationResponse;
import com.syncari.api.rest.controllers.data.OauthTokenResponse;
import com.syncari.core.model.User;
import com.syncari.core.model.misc.UserOAuthDetails;
import com.syncari.core.service.EncryptionService;
import com.syncari.core.service.UserService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static com.syncari.core.security.Permissions.READ_STUDIO;
import static com.syncari.core.security.Permissions.WRITE_STUDIO;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test class for OAuth2Controller that exercises the full OAuth 2.1 lifecycle:
 * - Dynamic client registration
 * - Authorization endpoint
 * - Consent redirect
 * - Token exchange (authorization_code grant)
 * - Refresh token flow
 */
public class OAuth2ControllerTest extends AbstractSyncariTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private UserService userService;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private Util util;

    @Override
    public void setUp() {
        super.setUp();
        pushContext();
    }

    @Override
    public void tearDown() {
        restoreContext();
        final User user = userRepo.findByEmail("test@email.com").get();
        user.setOauthServices(null);
        userRepo.save(user);
        super.tearDown();
    }

    /**
     * Test dynamic client registration endpoint
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void testDynamicClientRegistration() throws Exception {
        // Arrange
        ClientRegistrationRequest request = new ClientRegistrationRequest()
                .setClient_name("Test OAuth Client")
                .setRedirect_uris(List.of("https://example.com/callback"))
                .setGrant_types(List.of("authorization_code", "refresh_token"))
                .setResponse_types(List.of("code"))
                .setToken_endpoint_auth_method("none");

        // Act
        MvcResult result = mvc.perform(
                        post("/api/v1/oauth2/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        ClientRegistrationResponse response = mapper.readValue(
                result.getResponse().getContentAsString(),
                ClientRegistrationResponse.class
        );

        assertNotNull(response.getClient_id());
        assertNotNull(response.getClient_secret());
        assertEquals(0, response.getClient_secret_expires());
        assertEquals(request.getRedirect_uris(), response.getRedirect_uris());
    }

    /**
     * Test authorization endpoint with PKCE
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void testAuthorizationEndpoint() throws Exception {
        // Arrange
        String clientId = UUID.randomUUID().toString();
        String redirectUri = "https://example.com/callback";
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);
        String state = "test-state-123";
        String scope = "read write";

        // Act
        MvcResult result = mvc.perform(
                        get("/api/v1/oauth2/authorize")
                                .param("response_type", "code")
                                .param("client_id", clientId)
                                .param("redirect_uri", redirectUri)
                                .param("code_challenge", codeChallenge)
                                .param("code_challenge_method", "S256")
                                .param("state", state)
                                .param("scope", scope)
                                .header("Authorization", "Bearer dummy-token")
                )
                .andExpect(status().isFound())
                .andReturn();

        // Assert
        String location = result.getResponse().getHeader("Location");
        assertNotNull(location);
        assertTrue(location.contains("/arcade/api/v1/oauth2/consent"));
        assertTrue(location.contains("redirect_uri="));
        assertTrue(location.contains("code="));

        // Verify OAuth details were saved
        User user = userRepo.findByOAuthClientId(clientId).get(0);
        assertFalse(user.getOauthServices().isEmpty());

        UserOAuthDetails oauthDetails = user.getOauthServices().iterator().next();
        assertEquals(clientId, oauthDetails.getClientId());
        assertEquals(redirectUri, oauthDetails.getRedirectURL());
        assertEquals(scope, oauthDetails.getScope());
        assertNotNull(oauthDetails.getAuthorizationCode());
    }

    /**
     * Test consent endpoint redirect
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void testConsentEndpoint() throws Exception {
        // Arrange
        String redirectUri = "https://example.com/callback?code=test-code&state=test-state";

        // Act & Assert
        mvc.perform(
                        get("/api/v1/oauth2/consent")
                                .param("redirect_uri", redirectUri)
                )
                .andExpect(status().isFound())
                .andExpect(header().string("Location", redirectUri));
    }

    /**
     * Test token endpoint with authorization_code grant type
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void testTokenEndpointWithAuthorizationCode() throws Exception {
        // Arrange - First create authorization
        String clientId = UUID.randomUUID().toString();
        String redirectUri = "https://example.com/callback";
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);
        String scope = "read write";

        MvcResult authResult = mvc.perform(
                get("/api/v1/oauth2/authorize")
                        .param("response_type", "code")
                        .param("client_id", clientId)
                        .param("redirect_uri", redirectUri)
                        .param("code_challenge", codeChallenge)
                        .param("code_challenge_method", "S256")
                        .param("scope", scope)
                        .header("Authorization", "Bearer dummy-token")
        ).andReturn();

        // Extract authorization code from redirect URL
        String location = authResult.getResponse().getHeader("Location");
        String code = extractCodeFromLocation(location);

        // Act - Exchange code for tokens
        MvcResult tokenResult = mvc.perform(
                        post("/api/v1/oauth2/token")
                                .param("grant_type", "authorization_code")
                                .param("code", code)
                                .param("redirect_uri", redirectUri)
                                .param("client_id", clientId)
                                .param("code_verifier", codeVerifier)
                )
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        // Assert
        OauthTokenResponse tokenResponse = mapper.readValue(
                tokenResult.getResponse().getContentAsString(),
                OauthTokenResponse.class
        );

        assertNotNull(tokenResponse.getAccess_token());
        assertNotNull(tokenResponse.getRefresh_token());
        assertEquals("Bearer", tokenResponse.getToken_type());
        assertEquals(Long.valueOf(3600L), tokenResponse.getExpires_in());
        assertEquals(scope, tokenResponse.getScope());

        // Verify refresh token was saved
        User user = userService.getUserByEmail("test@email.com").get();
        UserOAuthDetails oauthDetails = user.getOauthServices().stream()
                .filter(d -> d.getClientId().equals(clientId))
                .findFirst()
                .orElseThrow();
        assertEquals(tokenResponse.getRefresh_token(), encryptionService.decrypt(oauthDetails.getRefreshToken()));
    }

    /**
     * Test token endpoint with invalid code verifier
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void testTokenEndpointWithInvalidCodeVerifier() throws Exception {
        // Arrange
        String clientId = UUID.randomUUID().toString();
        String redirectUri = "https://example.com/callback";
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);

        MvcResult authResult = mvc.perform(
                get("/api/v1/oauth2/authorize")
                        .param("response_type", "code")
                        .param("client_id", clientId)
                        .param("redirect_uri", redirectUri)
                        .param("code_challenge", codeChallenge)
                        .param("code_challenge_method", "S256")
                        .header("Authorization", "Bearer dummy-token")
        ).andReturn();

        String location = authResult.getResponse().getHeader("Location");
        String code = extractCodeFromLocation(location);

        // Act - Use wrong code verifier
        String wrongCodeVerifier = generateCodeVerifier();

        // Assert
        mvc.perform(
                        post("/api/v1/oauth2/token")
                                .param("grant_type", "authorization_code")
                                .param("code", code)
                                .param("redirect_uri", redirectUri)
                                .param("client_id", clientId)
                                .param("code_verifier", wrongCodeVerifier)
                )
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test token endpoint with missing parameters
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void testTokenEndpointWithMissingParameters() throws Exception {
        // Act & Assert - Missing code
        mvc.perform(
                        post("/api/v1/oauth2/token")
                                .param("grant_type", "authorization_code")
                                .param("redirect_uri", "https://example.com/callback")
                                .param("client_id", "test-client-id")
                )
                .andExpect(status().isUnauthorized());

        // Act & Assert - Missing redirect_uri
        mvc.perform(
                        post("/api/v1/oauth2/token")
                                .param("grant_type", "authorization_code")
                                .param("code", "test-code")
                                .param("client_id", "test-client-id")
                )
                .andExpect(status().isUnauthorized());

        // Act & Assert - Missing code_verifier
        mvc.perform(
                        post("/api/v1/oauth2/token")
                                .param("grant_type", "authorization_code")
                                .param("code", "test-code")
                                .param("redirect_uri", "https://example.com/callback")
                                .param("client_id", "test-client-id")
                )
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test refresh token endpoint
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void testRefreshTokenFlow() throws Exception {
        // Arrange - First get initial tokens
        String clientId = UUID.randomUUID().toString();
        String redirectUri = "https://example.com/callback";
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);
        String scope = "read write";

        MvcResult authResult = mvc.perform(
                get("/api/v1/oauth2/authorize")
                        .param("response_type", "code")
                        .param("client_id", clientId)
                        .param("redirect_uri", redirectUri)
                        .param("code_challenge", codeChallenge)
                        .param("code_challenge_method", "S256")
                        .param("scope", scope)
                        .header("Authorization", "Bearer dummy-token")
        ).andReturn();

        String location = authResult.getResponse().getHeader("Location");
        String code = extractCodeFromLocation(location);

        MvcResult tokenResult = mvc.perform(
                post("/api/v1/oauth2/token")
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", redirectUri)
                        .param("client_id", clientId)
                        .param("code_verifier", codeVerifier)
        ).andReturn();

        OauthTokenResponse initialTokenResponse = mapper.readValue(
                tokenResult.getResponse().getContentAsString(),
                OauthTokenResponse.class
        );
        String refreshToken = initialTokenResponse.getRefresh_token();

        // Act - Use refresh token to get new access token
        MvcResult refreshResult = mvc.perform(
                        post("/api/v1/oauth2/token")
                                .param("grant_type", "refresh_token")
                                .param("refresh_token", refreshToken)
                                .param("client_id", clientId)
                )
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        // Assert
        OauthTokenResponse refreshedTokenResponse = mapper.readValue(
                refreshResult.getResponse().getContentAsString(),
                OauthTokenResponse.class
        );

        assertNotNull(refreshedTokenResponse.getAccess_token());
        assertEquals(refreshToken, refreshedTokenResponse.getRefresh_token());
        assertEquals("Bearer", refreshedTokenResponse.getToken_type());
        assertEquals(Long.valueOf(3600L), refreshedTokenResponse.getExpires_in());
        assertEquals(scope, refreshedTokenResponse.getScope());
    }

    /**
     * Test refresh token with invalid token
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void testRefreshTokenWithInvalidToken() throws Exception {
        // Act & Assert
        mvc.perform(
                        post("/api/v1/oauth2/token")
                                .param("grant_type", "refresh_token")
                                .param("refresh_token", "invalid-refresh-token")
                                .param("client_id", "test-client-id")
                )
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test refresh token with missing parameters
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void testRefreshTokenWithMissingParameters() throws Exception {
        // Act & Assert - Missing refresh_token
        mvc.perform(
                        post("/api/v1/oauth2/token")
                                .param("grant_type", "refresh_token")
                                .param("client_id", "test-client-id")
                )
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test unsupported grant type
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void testUnsupportedGrantType() throws Exception {
        // Act & Assert
        mvc.perform(
                        post("/api/v1/oauth2/token")
                                .param("grant_type", "client_credentials")
                )
                .andExpect(status().isUnauthorized());
    }

    /**
     * Full OAuth 2.1 lifecycle integration test
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void testFullOAuth21Lifecycle() throws Exception {
        // Step 1: Dynamic client registration
        ClientRegistrationRequest registrationRequest = new ClientRegistrationRequest()
                .setClient_name("Integration Test Client")
                .setRedirect_uris(List.of("https://example.com/callback"))
                .setGrant_types(List.of("authorization_code", "refresh_token"))
                .setResponse_types(List.of("code"))
                .setToken_endpoint_auth_method("none");

        MvcResult registrationResult = mvc.perform(
                post("/api/v1/oauth2/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(registrationRequest))
        ).andExpect(status().isOk()).andReturn();

        ClientRegistrationResponse registrationResponse = mapper.readValue(
                registrationResult.getResponse().getContentAsString(),
                ClientRegistrationResponse.class
        );
        String clientId = registrationResponse.getClient_id();

        // Step 2: Authorization request with PKCE
        String redirectUri = "https://example.com/callback";
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);
        String state = "integration-test-state";
        String scope = "read write delete";

        MvcResult authResult = mvc.perform(
                        get("/api/v1/oauth2/authorize")
                                .param("response_type", "code")
                                .param("client_id", clientId)
                                .param("redirect_uri", redirectUri)
                                .param("code_challenge", codeChallenge)
                                .param("code_challenge_method", "S256")
                                .param("state", state)
                                .param("scope", scope)
                                .header("Authorization", "Bearer dummy-token")
                )
                .andExpect(status().isFound())
                .andReturn();

        String authLocation = authResult.getResponse().getHeader("Location");
        assertTrue(authLocation.contains("code="));
        assertTrue(authLocation.contains("state=" + state));

        // Step 3: Extract authorization code from consent redirect
        String code = extractCodeFromLocation(authLocation);

        // Step 4: Exchange authorization code for tokens
        MvcResult tokenResult = mvc.perform(
                        post("/api/v1/oauth2/token")
                                .param("grant_type", "authorization_code")
                                .param("code", code)
                                .param("redirect_uri", redirectUri)
                                .param("client_id", clientId)
                                .param("code_verifier", codeVerifier)
                )
                .andExpect(status().isOk())
                .andReturn();

        OauthTokenResponse tokenResponse = mapper.readValue(
                tokenResult.getResponse().getContentAsString(),
                OauthTokenResponse.class
        );

        assertNotNull(tokenResponse.getAccess_token());
        assertNotNull(tokenResponse.getRefresh_token());
        assertEquals("Bearer", tokenResponse.getToken_type());
        assertEquals(scope, tokenResponse.getScope());

        // Step 5: Use refresh token to get new access token
        String refreshToken = tokenResponse.getRefresh_token();

        MvcResult refreshResult = mvc.perform(
                        post("/api/v1/oauth2/token")
                                .param("grant_type", "refresh_token")
                                .param("refresh_token", refreshToken)
                                .param("client_id", clientId)
                )
                .andExpect(status().isOk())
                .andReturn();

        OauthTokenResponse refreshedTokenResponse = mapper.readValue(
                refreshResult.getResponse().getContentAsString(),
                OauthTokenResponse.class
        );

        assertNotNull(refreshedTokenResponse.getAccess_token());
        assertEquals(refreshToken, refreshedTokenResponse.getRefresh_token());
        assertEquals("Bearer", refreshedTokenResponse.getToken_type());
        assertEquals(scope, refreshedTokenResponse.getScope());

        // Step 6: Verify all OAuth details are persisted correctly
        User user = userService.getUserByEmail("test@email.com").get();
        UserOAuthDetails oauthDetails = user.getOauthServices().stream()
                .filter(d -> d.getClientId().equals(clientId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("OAuth details not found"));

        assertEquals(clientId, oauthDetails.getClientId());
        assertEquals(refreshToken, encryptionService.decrypt(oauthDetails.getRefreshToken()));
        assertEquals(scope, oauthDetails.getScope());
        assertEquals(redirectUri, oauthDetails.getRedirectURL());
        assertNotNull(oauthDetails.getInstanceId());
    }

    // Helper methods

    /**
     * Generate a PKCE code verifier (43-128 characters)
     */
    private String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Generate a PKCE code challenge from the verifier using S256 method
     */
    private String generateCodeChallenge(String verifier) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(verifier.getBytes("UTF-8"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    /**
     * Extract the authorization code from the redirect location URL
     */
    private String extractCodeFromLocation(String location) {
        // Location format: /arcade/api/v1/oauth2/consent?redirect_uri=...?code=...
        String[] parts = location.split("code=");
        if (parts.length < 2) {
            throw new IllegalArgumentException("No code found in location: " + location);
        }
        String codeWithParams = parts[1];
        // Remove any additional parameters (like &state=...)
        int ampersandIndex = codeWithParams.indexOf("&");
        if (ampersandIndex > 0) {
            return codeWithParams.substring(0, ampersandIndex);
        }
        return codeWithParams;
    }


}
