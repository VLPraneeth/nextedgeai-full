package com.syncari.api.rest.controllers.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.syncari.connector.Capability;
import com.syncari.connector.ConnectorType;
import com.syncari.connector.Constants;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.AuthField;
import com.syncari.connector.data.AuthMetadata;
import com.syncari.connector.data.AuthType;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.http.source.HttpSourceConfig;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.utils.KeyValue;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.*;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class ConnectorMetadataDTO implements Serializable {

    private final static String CUSTOM_SYNAPSE_EXTERNAL_ICON_URL = "/arcade/api/v1/connectormeta/%s/icon";

    private String id;
    private String name;
    private ConnectorType type;
    private String displayName;
    private String description;
    private String category;
    private String iconUri;
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
    @JsonProperty
    private boolean isGlobal;
    private boolean publishToGlobal;
    private DraftStatus draftStatus;
    private String parentId;
    private boolean ready;
    private String createdBy;
    private String updatedBy;
    private Date createdAt;
    private Date updatedAt;
    private String fileName;
    private Integer apiMaxCrudSize;
    private boolean isCreatable = true;
    private boolean hideFromSynapseList;
    private Integer maxInstances = 5;
    private boolean isHttpSource;
	private AuthType authType;
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

    public ConnectorMetadataDTO(ConnectorMetadata connectorMetadata) {
        id = connectorMetadata.getId();
        name = connectorMetadata.getName();
        type = connectorMetadata.getType();
        displayName = connectorMetadata.getDisplayName();
        description = connectorMetadata.getDescription();
        category = connectorMetadata.getCategory();
        iconUri = connectorMetadata.isNonStandardSynapse()
                ? String.format(CUSTOM_SYNAPSE_EXTERNAL_ICON_URL, connectorMetadata.getId())
                : connectorMetadata.getIconUri();
        backgroundColor = connectorMetadata.getBackgroundColor();
        helpUrl = connectorMetadata.getHelpUrl();
        oAuthUri = connectorMetadata.getOAuthUri();
        idFieldName = connectorMetadata.getIdFieldName();
        watermarkFieldName = connectorMetadata.getWatermarkFieldName();
        createdAtFieldName = connectorMetadata.getCreatedAtFieldName();
        updatedAtFieldName = connectorMetadata.getUpdatedAtFieldName();
        watermarkCustomizable = connectorMetadata.isWatermarkCustomizable();
        defaultApiLimit = connectorMetadata.getDefaultApiLimit();
        capabilities = connectorMetadata.getCapabilities();
        supportedAuthTypes = connectorMetadata.getSupportedAuthTypes();
        if(connectorMetadata.isHttpSource() && connectorMetadata.getVariables() != null && !connectorMetadata.getVariables().isEmpty()) {
        	configureFields = new ArrayList<AuthField>();
        	configureFields.addAll(connectorMetadata.getConfigureFields());
        	connectorMetadata.getVariables().stream().forEach(v -> {
        		AuthField additionalConfig = new AuthField().setName(v.get("name")).setLabel(v.get("name"))
                      .setDataType(v.get("dataType")).setHelpSummary("").setRequired(false);
        		configureFields.add(additionalConfig);
        	});
        } else {
        	configureFields = connectorMetadata.getConfigureFields();
        }
        watermarkFieldOverrides = connectorMetadata.getWatermarkFieldOverrides();
        orgId = connectorMetadata.getOrgId();
        disabledMessage = connectorMetadata.getDisabledMessage();
        isCustom = connectorMetadata.isCustom();
        publishToGlobal = connectorMetadata.isPublishToGlobal();
        customSynapseIdentifier = connectorMetadata.getCustomSynapseIdentifier();
        // From draftable model.
        draftStatus = connectorMetadata.getDraftStatus();
        parentId = connectorMetadata.getParentId();
        ready = connectorMetadata.isReady();
        createdBy = connectorMetadata.getCreatedBy();
        updatedBy = connectorMetadata.getUpdatedBy();
        createdAt = connectorMetadata.getCreatedAt();
        updatedAt = connectorMetadata.getUpdatedAt();
        fileName = connectorMetadata.getFileName();
        apiMaxCrudSize = connectorMetadata.getApiMaxCrudSize();
        hideFromSynapseList = connectorMetadata.getName().equalsIgnoreCase(Constants.DATASETS) || connectorMetadata.getName().equalsIgnoreCase(Constants.FILE_DATA);
        maxInstances = connectorMetadata.getMaxInstances();
        isHttpSource = connectorMetadata.isHttpSource();
    	authType = connectorMetadata.getAuthType();
    	authConfig = connectorMetadata.getAuthConfig();
    	endpoint = connectorMetadata.getEndpoint();
        method = connectorMetadata.getMethod();
        body = connectorMetadata .getBody();
        headers = connectorMetadata.getHeaders();
        variables = connectorMetadata.getVariables();
        variableValues = connectorMetadata.getVariableValues();
        isWebhook = connectorMetadata.isWebhook();
        schema = connectorMetadata.getSchema();
        recordSelector = connectorMetadata.getRecordSelector();
        idSelector = connectorMetadata.getIdSelector();
        responseCode = connectorMetadata.getResponseCode();
        responseTemplate = connectorMetadata.getResponseTemplate();
        sharingInstances = connectorMetadata.getSharingInstances();
    }

    public static String getIconURIForDTO(ConnectorMetadata connectorMetadata) {
        return (connectorMetadata.isNonStandardSynapse()) ? String.format(CUSTOM_SYNAPSE_EXTERNAL_ICON_URL, connectorMetadata.getId()) : connectorMetadata.getIconUri();
    }
}
