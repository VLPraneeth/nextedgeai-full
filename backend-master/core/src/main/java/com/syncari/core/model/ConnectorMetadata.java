package com.syncari.core.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.validation.constraints.NotNull;

import com.syncari.core.share.SharedItemObject;
import com.syncari.utils.KeyValue;

import org.springframework.data.annotation.Transient;

import com.syncari.connector.Capability;
import com.syncari.connector.ConnectorType;
import com.syncari.connector.Constants;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.AuthField;
import com.syncari.connector.data.AuthMetadata;
import com.syncari.connector.data.AuthType;
import com.syncari.core.http.source.HttpSourceConfig;
import com.syncari.core.model.misc.DraftableModel;
import com.syncari.core.SyncariContext;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ConnectorMetadata extends DraftableModel<ConnectorMetadata> implements SharedItemObject{
	@NotNull(message = "Connector metadata name is required")
	private String name;
	private ConnectorType type;
	private String displayName;
	private String description;
	private String category;
	private String iconUri;
	private String iconUriContentType;
	private String backgroundColor;
	private String helpUrl;
	private String oAuthUri;
	private String idFieldName;
	private String watermarkFieldName;
	private String createdAtFieldName;
	private String updatedAtFieldName;
	private boolean watermarkCustomizable;
	private long defaultApiLimit;
	private List<Capability> capabilities = new ArrayList<>();
	private List<AuthMetadata> supportedAuthTypes;
	private List<AuthField> configureFields = new ArrayList<>();
	//Override watermark field per entity
	private Map<String, String> watermarkFieldOverrides = new HashMap<>();
    private String orgId;
	private String disabledMessage;
    private boolean isCustom;
    private String customSynapseIdentifier;
	private boolean publishToGlobal;
	private String fileName;
	private Integer apiMaxCrudSize;
	private Integer maxInstances = 5;
	private String sourceInstance;
	private boolean isHttpSource;
	private AuthType authType;
	private List<HttpSourceConfig> httpSources = new ArrayList<>();
	private AuthConfig authConfig;
	private String endpoint;
    private String method;
    private String body;
    private Map<String, String> headers;
    private List<KeyValue> variables;
    private List<KeyValue> variableValues;
    private String schema;
    private String recordSelector;
    private String idSelector;
    private boolean isWebhook;
    private Integer responseCode;
    private String responseTemplate;
    private List<String> sharingInstances = new ArrayList<>();

    @Transient
    private List<String> clientIdBasedConnectors = List.of(Constants.HUBSPOT, Constants.SLACK_SYNAPSE, Constants.GOOGLESHEETS);

	public ConnectorMetadata() {
	}

	public boolean isDefaultWatermarkField(String entityName, String attributeName){
		String watermarkField = watermarkFieldOverrides.getOrDefault(entityName, watermarkFieldName);
		return watermarkField==null? false: attributeName.equalsIgnoreCase(watermarkField);
	}

	public ConnectorMetadata(String id) {
		setId(id);
	}

    public boolean isOneClickOauthConnector() {
        return clientIdBasedConnectors.contains(name) ? true : false;
    }

    public boolean supportsCapability(Capability capability) {
		return capabilities != null && capabilities.contains(capability);
	}

    public boolean belongsToSourceOrg() {
        // For cases like webhooks processing, we do not have an organization context, we simply process the events.
		// Also, for cases where the orgId is not found, it is assumed to be a pre-seeded standard synapse.
		if (SyncariContext.getOrganziation() == null || getOrgId() == null) return true;
        return SyncariContext.getOrganziation().getId().equalsIgnoreCase(getOrgId());
    }

    @Override
    public ConnectorMetadata makeCopy() {
		return new ConnectorMetadata()
				.setName(name)
				.setDisplayName(displayName)
				.setCustom(isCustom)
				.setOrgId(orgId)
				.setFileName(fileName)
				.setSourceInstance(sourceInstance)
				.setHttpSource(isHttpSource)
				.setAuthType(authType)
				.setAuthConfig(authConfig)
				.setHttpSources(httpSources)
				.setEndpoint(endpoint)
				.setMethod(method)
				.setBody(body)
				.setHeaders(headers)
				.setVariables(variables)
				.setVariableValues(variableValues)
				.setSchema(schema)
				.setRecordSelector(recordSelector)
				.setIdSelector(idSelector)
				.setWebhook(isWebhook)
				.setResponseCode(responseCode)
				.setResponseTemplate(responseTemplate)
                .setSharingInstances(
                sharingInstances != null ? new ArrayList<String>(sharingInstances) : null);
    }

    @Override
    public void copyValuesFrom(ConnectorMetadata model) {
        this.setDisplayName(model.getDisplayName());
    }
    
    public boolean isNonStandardSynapse() {
      return this.isCustom() || this.isHttpSource() || this.isWebhook();
    }
}
