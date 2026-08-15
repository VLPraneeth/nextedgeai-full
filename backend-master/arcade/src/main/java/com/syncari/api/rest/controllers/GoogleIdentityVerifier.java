package com.syncari.api.rest.controllers;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Optional;

@Component
public class GoogleIdentityVerifier {
    private static final int MAX_CREDENTIAL_LENGTH = 8192;

    @Value("${NEXTEDGE_GOOGLE_CLIENT_ID:}")
    private String clientId;

    public boolean isEnabled() {
        return StringUtils.isNotBlank(clientId);
    }

    public String getClientId() {
        return isEnabled() ? clientId : "";
    }

    public Optional<String> verifyEmail(String credential) throws GeneralSecurityException, IOException {
        if (!isEnabled() || StringUtils.isBlank(credential) || credential.length() > MAX_CREDENTIAL_LENGTH) {
            return Optional.empty();
        }
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
        GoogleIdToken token = verifier.verify(credential);
        if (token == null || !Boolean.TRUE.equals(token.getPayload().getEmailVerified())) {
            return Optional.empty();
        }
        String email = token.getPayload().getEmail();
        return StringUtils.isBlank(email) ? Optional.empty() : Optional.of(email.toLowerCase());
    }
}
