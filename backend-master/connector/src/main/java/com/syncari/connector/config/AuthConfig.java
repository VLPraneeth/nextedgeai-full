package com.syncari.connector.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Data
@AllArgsConstructor
@Accessors(chain = true)
@ToString(exclude = {"password", "token", "clientSecret", "accessToken", "refreshToken",
        "tokenId", "tokenSecret", "consumerKey", "consumerSecret", "codeVerifier",
        "codeChallenge", "additionalHeaders"})
public class AuthConfig {
    private String userName;
    private String password;
    private String token;
    String clientId;
    String clientSecret;
    String accessToken;
    String refreshToken;
    String expiresIn = "0";
    Instant lastRefreshed;
    String endpoint;
    String redirectUri;

    String tokenId;
    String tokenSecret;
    String consumerKey;
    String consumerSecret;
    String codeVerifier;
    String codeChallenge;
    Map<String, String> additionalHeaders = new HashMap<>();
    String signatureHeader;
    String hashAlgorithm;
    String apiKeyHeader;
    String accessTokenEndpoint;

    private static final long REFRESH_THRESHOLD_SECONDS = 5 * 60 ; // 5 minutes before token expires

    public AuthConfig() {
    }

    public AuthConfig(String userName, String password, String token) {
        this.userName = userName;
        this.password = password;
        this.token = token;
    }
    
    public AuthConfig(String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public AuthConfig(AuthConfig config) {
        this.userName = config.userName;
        this.password = config.password;
        this.token = config.token;
        this.clientId = config.clientId;
        this.clientSecret = config.clientSecret;
        this.accessToken = config.accessToken;
        this.refreshToken = config.refreshToken;
        this.expiresIn = config.expiresIn;
        this.lastRefreshed = config.lastRefreshed;
        this.endpoint = config.endpoint;
        this.redirectUri = config.redirectUri;

        this.tokenId = config.tokenId;
        this.tokenSecret = config.tokenSecret;
        this.consumerKey = config.consumerKey;
        this.consumerSecret = config.consumerSecret;
        this.additionalHeaders = config.additionalHeaders;
        this.signatureHeader = config.signatureHeader;
        this.hashAlgorithm = config.hashAlgorithm;
        this.apiKeyHeader = config.apiKeyHeader;
        this.accessTokenEndpoint = config.accessTokenEndpoint;
    }

    @Override
    public AuthConfig clone() {
      return new AuthConfig(userName, password, token, clientId, clientSecret, accessToken,
          refreshToken, expiresIn, lastRefreshed, endpoint, redirectUri, tokenId, tokenSecret,
          consumerKey, consumerSecret, codeVerifier, codeChallenge, additionalHeaders,
          signatureHeader, hashAlgorithm, apiKeyHeader, accessTokenEndpoint);
    }

    /**
     * Is this token going to expire soon? (soon == within 10% of token life or REFRESH_THRESHOLD_SECONDS, whichever is larger)
     * @return boolean true if token expires within threshold
     */
    public boolean expiresSoon(){
        if(refreshToken == null || expiresIn == null) return false;
        log.debug("lastRefreshed {}, expiresIn {}", lastRefreshed, expiresIn);
        final Long expiryTime = Long.valueOf(expiresIn);
        long last = (lastRefreshed == null ? Instant.EPOCH : lastRefreshed).getEpochSecond() + expiryTime - Instant.now().getEpochSecond();
        log.debug("computed lastRefreshed {}", last);
        return last < Math.min(expiryTime / 10, REFRESH_THRESHOLD_SECONDS);
    }

    public AuthConfig addHeader(String key, String value) {
        if (additionalHeaders == null) {
            additionalHeaders = new HashMap<>();
        }
        additionalHeaders.put(key, value);
        return this;
    }

    public String getHeader(String key) {
        return additionalHeaders == null ? null : additionalHeaders.get(key);
    }

    public boolean hasTokenChanges(AuthConfig other) {
        return !(Objects.equals(refreshToken, other.refreshToken)
                && Objects.equals(accessToken, other.accessToken)
                && Objects.equals(lastRefreshed, other.getLastRefreshed()));
    }
}
