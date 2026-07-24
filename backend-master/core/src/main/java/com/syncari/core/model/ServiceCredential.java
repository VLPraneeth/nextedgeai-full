package com.syncari.core.model;

import com.syncari.core.model.misc.ServiceCredentialType;
import com.syncari.core.model.misc.ServiceType;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;

@Data
@Accessors(chain = true)
public class ServiceCredential extends UUIDAuditModel {
	@NotNull(message = "Name is required")
	public String name;
	public ServiceCredentialType credentialType;
	public ServiceType serviceType;
	public String apiKey;
	public String username;
	public String password;
    public String clientId;
	public String clientSecret;
	public String endPoint;
	public String tenantId;
	public String oauthUrl;

	public Map<String, Object> transformCredentials() {
		Map<String, Object> credsMap = new HashMap<>();
		credsMap.put("id", this.getId());
		credsMap.put("name", this.getName());
		credsMap.put("credentialType", this.getCredentialType());
		credsMap.put("serviceType", this.getServiceType());
		credsMap.put("apiKey", this.getApiKey());
		credsMap.put("createdBy", this.getCreatedBy());
		credsMap.put("createdAt", this.getCreatedAt());
		credsMap.put("updatedBy", this.getUpdatedBy());
		credsMap.put("updatedAt", this.getUpdatedAt());
		credsMap.put("username", this.getUsername());
		credsMap.put("password", this.getPassword());
		credsMap.put("clientId", this.getClientId());
		credsMap.put("clientSecret", this.getClientSecret());
		credsMap.put("endPoint", this.getEndPoint());
		credsMap.put("tenantId", this.getTenantId());
		credsMap.put("oauthUrl", this.getOauthUrl());
		return credsMap;
	}
}
