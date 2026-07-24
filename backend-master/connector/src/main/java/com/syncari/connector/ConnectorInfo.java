package com.syncari.connector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.custom.CloudFunctionInfo;
import com.syncari.connector.data.AuthType;

import com.syncari.connector.data.Pipeline;
import lombok.*;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
@Accessors(chain = true)
public class ConnectorInfo {
	static HashFunction HASH = Hashing.murmur3_128();
    @EqualsAndHashCode.Include
    private String id;
    private String name;
    private String endpoint;
    private AuthConfig authConfig;
    private String idFieldName;
    private String watermarkFieldName;
    private String createdAtFieldName;
    private String updatedAtFieldName;
    private String oAuthRedirectUrl;
    @EqualsAndHashCode.Include
    private String instanceId;
    private boolean alterLengthIfRequired;
    private Map<String, Object> metaConfig = new HashMap<String, Object>();
    private DatastoreType datastoreType;
    private boolean isCustom;
    private CloudFunctionInfo cloudFunctionInfo;
    private String connectorMetadataName;
    private Map<String, Object> internalConfig = new HashMap<>();
    List<String> requiredScopes;
    List<String> optionalScopes;
    private Integer apiMaxCrudSize;
    private Integer maxInstances = 5;
    private AuthType authType;
    private List<HttpSourceConfigInfo> httpSourceConfig;
    private WebhookConfigInfo webhookConfig;

    public ConnectorInfo(String id, String name, String endpoint, String instanceId) {
        this.name = name;
        this.id = id;
        this.endpoint = endpoint;
        this.instanceId = instanceId;
        this.authConfig = new AuthConfig();
    }

    public ConnectorInfo(String id, String name, String endpoint, String instanceId,String userName, String password) {
        this(id, name, endpoint,instanceId);
        this.authConfig = new AuthConfig(userName, password, null);
    }

    public ConnectorInfo() {
    }

    public String connectionHash() {
        return HASH.hashBytes((instanceId + id + name + endpoint + authConfig + idFieldName + watermarkFieldName +
                createdAtFieldName + updatedAtFieldName +
                oAuthRedirectUrl + new TreeMap<>(metaConfig)).getBytes()).toString();
    }

    public String getMetaConfigValue(String key, String defaultValue) {
        if (metaConfig != null && metaConfig.containsKey(key)) {
            if(metaConfig.get(key) == null) return defaultValue;
            return metaConfig.get(key).toString();
        }

        return defaultValue;
    }
}
