package com.syncari.api.rest.controllers.data;

import lombok.Data;

import java.time.Instant;

@Data
public class AuthCode {
    private final String code;
    private final String clientId;
    private final String redirectUri;
    private final String userId;
    private final String codeChallenge;
    private final Instant expiresAt;

    public AuthCode(String code, String clientId, String redirectUri, String userId, String codeChallenge, Instant expiresAt) {
        this.code = code;
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.userId = userId;
        this.codeChallenge = codeChallenge;
        this.expiresAt = expiresAt;
    }
}
