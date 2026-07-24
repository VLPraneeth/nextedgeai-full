package com.syncari.core.model;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.DatastoreType;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.AuthType;
import com.syncari.core.model.misc.ApiConfig;
import com.syncari.core.model.misc.AsyncStatus;
import com.syncari.core.model.misc.ConnectorSetting;
import com.syncari.core.model.misc.ConnectorStatus;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.EncryptionService;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.Transient;

import javax.validation.constraints.NotNull;

import java.util.HashMap;
import java.util.Map;

@Data
@Accessors(chain = true)
public class Connector extends  UUIDAuditModel {
	@NotNull(message = "Connector name is required")
	private String name;
	private String endpoint;
	@NotNull(message = "Connector status is required")
	private ConnectorStatus status;
	private String errorMessage;
	private String errorDetail;
	private ApiConfig apiConfig;
	private AuthConfig authConfig;
	private String oAuthRedirectUrl;
	private boolean isSystem;
	@NotNull(message = "Connector type is required")
	private String metadataId;
	private boolean bootstrap;
	@Transient
	private ConnectorMetadata metadata;
	private AuthType authType;
	private ConnectorSetting setting = new ConnectorSetting();
	private Map<String, Object> metaConfig = new HashMap<String, Object>();
	private AsyncStatus schemaRefreshStatus;
	private DatastoreType datastoreType;

	public Connector(String name, String metadataId, String endpoint) {
		this.name = name;
		this.metadataId = metadataId;
		this.endpoint = endpoint;
		this.authConfig = new AuthConfig();
	}

	public Connector(String name, String metadataId, String endpoint, String userName, String password) {
		this(name, metadataId, endpoint);
		this.authConfig = new AuthConfig(userName, password, null);
	}
	
	public Connector(String name, ConnectorMetadata metadata, String endpoint, String userName, String password) {
	    this(name, metadata.getId(), endpoint);
	    this.authConfig = new AuthConfig(userName, password, null);
	    this.metadata = metadata;
	}

    public Connector(String name, ConnectorMetadata metadata, String endpoint, String clientId, String clientSecret, boolean isClientIdSecret) {
	    this(name, metadata.getId(), endpoint);
	    this.authConfig = new AuthConfig(clientId, clientSecret);
	    this.metadata = metadata;
	}

	public Connector() {
	}
	
	public Connector(String id) {
		setId(id);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		Connector other = (Connector) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		return true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	public Connector makeCopy() {
		Connector newC = new Connector(name, metadataId, endpoint);
		newC.setStatus(status);
		newC.setApiConfig(apiConfig);
		newC.setAuthConfig(authConfig);
		newC.setOAuthRedirectUrl(oAuthRedirectUrl);
		newC.setCreatedAt(getCreatedAt());
		newC.setCreatedBy(getCreatedBy());
		newC.setUpdatedAt(getUpdatedAt());
		newC.setUpdatedBy(getUpdatedBy());
		return newC;
	}

	public void copyValuesFrom(Connector other) {
		this.apiConfig = other.apiConfig.clone();
		this.authConfig = other.authConfig.clone();
		this.name = other.name;
		this.endpoint = other.endpoint;
		this.metadataId = other.metadataId;
	}

	public void setDailyQuota(long dailyQuota) {
	    if(apiConfig == null) {
	        apiConfig = new ApiConfig();
	    }
	    apiConfig.setDailyQuota(dailyQuota);
	}

	public long getDailyQuota() {
	    if(apiConfig != null) {
	        return apiConfig.getDailyQuota();
	    }
	    return 0;
	}

	public boolean isError() {
		return status == ConnectorStatus.ERROR;
	}

	public boolean isActive() {
	    return status == ConnectorStatus.ACTIVE;
	}

	public boolean isDeleted() {
		return status == ConnectorStatus.DELETED;
	}
	
	public void setMetaConfig(Map<String, Object> metaConfig) {
	    if(metaConfig != null) {
	        this.metaConfig = metaConfig;
	    }
	}
	
	public void setSetting(ConnectorSetting setting) {
	    if(setting != null) {
	        this.setting = setting;
	    }
	}

	public boolean isSyncariConnector(){
		return "syncari".equalsIgnoreCase(name);
	}

	public boolean isSyncariDatastore(){
		return "Syncari Datastore".equalsIgnoreCase(name);
	}

	public Map<String, Object> transformConnectorData(EncryptionService encryptionService) {
		Map<String, Object> connectorMap = new HashMap<>();
		connectorMap.put("id", this.getId());
		connectorMap.put("name", this.getName());
		connectorMap.put("status", this.getStatus());
		connectorMap.put("authType", this.getAuthType());
		connectorMap.put("oAuthRedirectUrl", this.getOAuthRedirectUrl());
		connectorMap.put("createdBy", this.getCreatedBy());
		connectorMap.put("createdAt", this.getCreatedAt());
		connectorMap.put("updatedBy", this.getUpdatedBy());
		connectorMap.put("updatedAt", this.getUpdatedAt());
		ConnectorMetadata connectorMetadata = this.getMetadata();
		connectorMap.put("synapseTypeId", connectorMetadata.getName());
		final HashMap<String, Object> metaConfig = new HashMap<>(this.getMetaConfig());
		metaConfig.remove(ConnectorService.GCP_CRED_KEY);
		connectorMap.put("metaConfig", metaConfig);
		// Use the copy of the authconfig if the original is changed.
		connectorMap.put("authConfig", this.getAuthConfig().clone());
		return connectorMap;
	}

	public String getValue(String key) {
		Object schema = getMetaConfig().get(key);
		return schema == null ? "" : schema.toString();
	}

}
