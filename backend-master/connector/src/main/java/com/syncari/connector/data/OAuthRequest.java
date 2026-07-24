package com.syncari.connector.data;

import java.util.Map;

import com.syncari.connector.config.AuthConfig;

import com.syncari.connector.custom.CloudFunctionInfo;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class OAuthRequest {
	String code;
	String endpoint;
	String redirectUri;
	AuthConfig config;
	Map<String, Object> metaConfig;
	CloudFunctionInfo cloudFunctionInfo;
	
	public OAuthRequest() {}
	
	public OAuthRequest(String code, String endpoint, String redirectUri, AuthConfig config, Map<String, Object> metaConfig, CloudFunctionInfo cloudFunctionInfo) {
		this(endpoint, redirectUri, config, metaConfig);
		this.code = code;
		this.cloudFunctionInfo = cloudFunctionInfo;
	}
	
	public OAuthRequest(String endpoint, String redirectUri, AuthConfig config, Map<String, Object> metaConfig) {
		this.endpoint = endpoint;
		this.redirectUri = redirectUri;
		this.config = config;
		this.metaConfig = metaConfig;
	}
}
