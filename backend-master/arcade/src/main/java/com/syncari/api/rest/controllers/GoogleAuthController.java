package com.syncari.api.rest.controllers;

import com.syncari.api.core.util.Util;
import com.syncari.api.rest.config.security.SecurityConstants;
import com.syncari.api.rest.config.security.TokenAttributes;
import com.syncari.core.SyncariContext;
import com.syncari.core.SyncariContextHandler;
import com.syncari.core.model.User;
import com.syncari.core.service.UserService;
import com.syncari.core.service.authz.AuthzService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth/google")
public class GoogleAuthController {
    @Autowired
    GoogleIdentityVerifier googleIdentityVerifier;
    @Autowired
    UserService userService;
    @Autowired
    AuthzService authzService;
    @Autowired
    SyncariContextHandler syncariContextHandler;
    @Autowired
    Util util;
    @Value("${NEXTEDGE_GOOGLE_DEMO_EMAIL:}")
    String googleDemoEmail;
    @Value("${NEXTEDGE_TENANT_ADMIN_EMAIL:}")
    String guidedDemoEmail;

    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", googleIdentityVerifier.isEnabled());
        if (googleIdentityVerifier.isEnabled()) {
            config.put("clientId", googleIdentityVerifier.getClientId());
        }
        return config;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> authenticate(@RequestBody GoogleAuthRequest authRequest,
                                                             HttpServletRequest request,
                                                             HttpServletResponse response) {
        try {
            String verifiedEmail = googleIdentityVerifier.verifyEmail(authRequest == null ? null : authRequest.getCredential())
                    .orElseThrow(() -> new IllegalArgumentException("Google credential validation failed"));
            String provisionedEmail = resolveProvisionedEmail(verifiedEmail);
            User user = userService.findActiveUserByEmail(provisionedEmail)
                    .orElseThrow(() -> new IllegalArgumentException("Google account is not provisioned"));
            if (user.isRestrictedFromLogin() || StringUtils.isBlank(user.getCurrentInstanceId())) {
                throw new IllegalArgumentException("Google account cannot access a workspace");
            }
            if (!user.isSuperAdmin() && !user.hasAccess(user.getCurrentInstanceId())) {
                throw new IllegalArgumentException("Google account is outside its tenant boundary");
            }

            SyncariContext.setUser(user);
            syncariContextHandler.setContext(user.getCurrentInstanceId());
            if (SyncariContext.getInstance() != null) {
                util.setInsightsProviderContext(SyncariContext.getInstance());
            }
            user.setLastLoggedIn(Instant.now());
            userService.saveUser(user);

            var permissions = authzService.listPrivileges(user.getEmail()).collect(Collectors.toList());
            Optional<String> previousToken = Optional.ofNullable(request.getHeader(SecurityConstants.TOKEN_HEADER));
            String tokenId = previousToken.map(token -> {
                TokenAttributes attributes = util.parseTokenExpiredOrNot(token.replace(SecurityConstants.TOKEN_PREFIX, ""));
                return attributes.getTokenId() == null ? UUID.randomUUID().toString() : attributes.getTokenId();
            }).orElse(UUID.randomUUID().toString());
            String token = util.getTokenAndPersistLoginDetails(
                    user, permissions, false, user.getCurrentInstanceId(), tokenId);
            response.addHeader(SecurityConstants.TOKEN_HEADER, SecurityConstants.TOKEN_PREFIX + token);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception authenticationError) {
            log.warn("Google sign-in rejected: {}", authenticationError.getClass().getSimpleName());
            return ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "This Google account is not authorized for NextEdge AI. Ask an administrator to provision it first."));
        } finally {
            syncariContextHandler.resetSyncariContext();
        }
    }

    private String resolveProvisionedEmail(String verifiedEmail) {
        if (StringUtils.isNotBlank(googleDemoEmail)
                && StringUtils.isNotBlank(guidedDemoEmail)
                && googleDemoEmail.equalsIgnoreCase(verifiedEmail)) {
            return guidedDemoEmail;
        }
        return verifiedEmail;
    }

    public static class GoogleAuthRequest {
        private String credential;

        public String getCredential() {
            return credential;
        }

        public void setCredential(String credential) {
            this.credential = credential;
        }
    }
}
