package com.syncari.api.rest.controllers.data;

import java.util.*;

import com.syncari.connector.config.AuthConfig;
import com.syncari.core.model.ConnectorSchemaSetting;
import com.syncari.core.model.misc.ApiConfig;
import com.syncari.core.model.misc.AsyncStatus;
import com.syncari.core.model.misc.ConnectorSetting;
import com.syncari.core.model.misc.ConnectorStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ConnectorResponse {
	private String id;
	private String name;
	private String metadataId;
	private String endpoint;
	private ConnectorStatus status;
	private String errorMessage;
	private String errorDetails;
	private ApiConfig apiConfig;
	private Map<String,String> authConfig = new HashMap<>();
    private ConnectorSetting setting;
	private String oAuthRedirectUrl;
	protected String createdBy;
	protected String updatedBy;
	protected Date createdAt;
	protected Date updatedAt;
    private List<ConnectorSchemaSetting> autoSchemaSyncEntities;
    private Map<String, Object> metaConfig;
    private AsyncStatus schemaRefreshStatus;
    private String iconUri;
    private String displayName;
    private String backgroundColor;
	private static final Set<String> keys = Set.of("userName", "password", "token", "clientId", "clientSecret", "accessToken", "refreshToken",
			"expiresIn", "endpoint", "redirectUri", "tokenId" ,"tokenSecret", "consumerKey", "consumerSecret");

	public AuthConfig getAuthenticationConfig(){
		Map<String, String> additionalHeaders=new HashMap<>();
		authConfig.forEach((k,v)-> {
			if(!keys.contains(k)){
				additionalHeaders.put(k,v);
			}
		});

		AuthConfig result = new AuthConfig(authConfig.get("userName"),authConfig.get("password"),
				authConfig.get("token"),authConfig.get("clientId"),authConfig.get("clientSecret")
				,authConfig.get("accessToken"),authConfig.get("refreshToken"),authConfig.get("expiresIn"),null,
				authConfig.get("endpoint"),authConfig.get("redirectUri"),authConfig.get("tokenId"),
				authConfig.get("tokenSecret"),authConfig.get("consumerKey"),authConfig.get("consumerSecret"), 
				authConfig.get("codeVerifier"), authConfig.get("codeChallenge"), additionalHeaders, authConfig.get("signatureHeader"), 
				authConfig.get("hashAlgorithm"), authConfig.get("apiKeyHeader"), authConfig.get("accessTokenEndpoint"));
		return result;
	}
	public ConnectorResponse setAuthenticationConfig(AuthConfig config){
		authConfig = new HashMap<>();
		if(config == null) return this;
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
		authConfig.put("tokenSecret",config.getTokenSecret());
		authConfig.put("codeVerifier",config.getCodeVerifier());
		authConfig.put("codeChallenge",config.getCodeChallenge());
		if(config.getAdditionalHeaders()!=null) {
			authConfig.putAll(config.getAdditionalHeaders());
		}
		authConfig.put("signatureHeader",config.getSignatureHeader());
		authConfig.put("hashAlgorithm",config.getHashAlgorithm());
		authConfig.put("apiKeyHeader",config.getApiKeyHeader());
		authConfig.put("accessTokenEndpoint",config.getAccessTokenEndpoint());
		return this;
	}
}
