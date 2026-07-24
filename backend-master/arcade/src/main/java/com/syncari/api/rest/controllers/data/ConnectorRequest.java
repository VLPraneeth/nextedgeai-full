package com.syncari.api.rest.controllers.data;

import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.AuthType;
import com.syncari.core.model.misc.ApiConfig;
import com.syncari.core.model.misc.ConnectorSetting;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

import java.util.*;

@Data
@AllArgsConstructor
public class ConnectorRequest {
    private String name;
    private String metadataId;
    private String endpoint;
    @ToString.Exclude
    private Map<String,String> authConfig = new HashMap<>();
    private ApiConfig apiConfig;
    // TODO when the UI is changed, moved apiLimit and bootstrapWithSyncari to settings
    private long apiLimit;
    private boolean bootstrapWithSyncari;
    private AuthType authType;
    private ConnectorSetting setting;
    private Map<String, Object> metaConfig = new HashMap<String, Object>();
    private static final Set<String> keys = Set.of("userName", "password", "token", "clientId", "clientSecret", "accessToken", "refreshToken",
            "expiresIn", "endpoint", "redirectUri", "tokenId" ,"tokenSecret", "consumerKey", "consumerSecret", "codeVerifier", "codeChallenge");

    public ConnectorRequest(String name, String metadataId, String endpoint) {
        this.name = name;
        this.metadataId = metadataId;
        this.endpoint = endpoint;
        this.apiConfig = new ApiConfig();
    }

    public AuthConfig getAuthenticationConfig(){
        Map<String, String> additionalHeaders=new HashMap<>();
        authConfig.forEach((k,v)-> {
            if(!keys.contains(k)){
                additionalHeaders.put(k,v);
            }
        });

        AuthConfig result = new AuthConfig(authConfig.get("userName"),authConfig.get("password"),
                authConfig.get("token"),authConfig.get("clientId"),authConfig.get("clientSecret"),
                authConfig.get("accessToken"),authConfig.get("refreshToken"),authConfig.get("expiresIn"),null,
                authConfig.get("endpoint"),authConfig.get("redirectUri"),authConfig.get("tokenId"),
                authConfig.get("tokenSecret"),authConfig.get("consumerKey"),authConfig.get("consumerSecret"), 
                authConfig.get("codeVerifier"), authConfig.get("codeChallenge"), additionalHeaders,authConfig.get("signatureHeader"), 
                authConfig.get("hashAlgorithm"), authConfig.get("apiKeyHeader"), authConfig.get("accessTokenEndpoint"));
        return result;
    }

    public void setAuthenticationConfig(AuthConfig config){
        authConfig.put("userName",config.getUserName());
        authConfig.put("password",config.getPassword());
        authConfig.put("token",config.getToken());
        authConfig.put("clientId",config.getClientId());
        authConfig.put("clientSecret",config.getClientSecret());
        authConfig.put("accessToken",config.getAccessToken());
        authConfig.put("refreshToken",config.getRefreshToken());
        authConfig.put("expiresIn",config.getExpiresIn());
        authConfig.put("endpoint",config.getEndpoint());
        authConfig.put("redirectUri",config.getRedirectUri());
        authConfig.put("tokenId",config.getTokenId());
        authConfig.put("consumerKey",config.getConsumerKey());
        authConfig.put("consumerSecret",config.getConsumerSecret());
        authConfig.put("codeVerifier",config.getCodeVerifier());
        authConfig.put("codeChallenge",config.getCodeChallenge());
        if(config.getAdditionalHeaders()!=null) {
            authConfig.putAll(config.getAdditionalHeaders());
        }
        authConfig.put("signatureHeader",config.getSignatureHeader());
        authConfig.put("hashAlgorithm",config.getHashAlgorithm());
        authConfig.put("apiKeyHeader",config.getApiKeyHeader());
        authConfig.put("accessTokenEndpoint",config.getAccessTokenEndpoint());
    }
    public ConnectorRequest() {
    }
}
