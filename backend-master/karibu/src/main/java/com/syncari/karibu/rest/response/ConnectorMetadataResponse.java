package com.syncari.karibu.rest.response;

import com.syncari.connector.Capability;
import com.syncari.connector.ConnectorType;
import com.syncari.connector.data.AuthField;
import com.syncari.connector.data.AuthMetadata;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.core.model.UUIDAuditModel;
import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@ToString(callSuper = true)
public class ConnectorMetadataResponse extends BaseKaribuResponse {

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
    private Map<String, String> watermarkFieldOverrides = new HashMap<>();
    private String orgId;
    private String disabledMessage;
    private boolean isCustom;
    private String customSynapseIdentifier;
    private boolean publishToGlobal;
    private String fileName;
    private Integer apiMaxCrudSize;

    @Override
    public <k extends KaribuResponse, h extends UUIDAuditModel> Object populate(h object) {
        ConnectorMetadata connectorMetadataDTO = (ConnectorMetadata) object;
        ConnectorMetadataResponse response = new ConnectorMetadataResponse();

        response.setId(connectorMetadataDTO.getId());
        response.setType(connectorMetadataDTO.getType());
        response.setName(connectorMetadataDTO.getName());
        response.setDisplayName(connectorMetadataDTO.getDisplayName());
        response.setDescription(connectorMetadataDTO.getDescription());
        response.setCategory(connectorMetadataDTO.getCategory());
        response.setIconUri(connectorMetadataDTO.getIconUri());
        response.setBackgroundColor(connectorMetadataDTO.getBackgroundColor());
        response.setHelpUrl(connectorMetadataDTO.getHelpUrl());
        response.setOAuthUri(connectorMetadataDTO.getOAuthUri());
        response.setIdFieldName(connectorMetadataDTO.getIdFieldName());
        response.setWatermarkFieldName(connectorMetadataDTO.getWatermarkFieldName());
        response.setCreatedAtFieldName(connectorMetadataDTO.getCreatedAtFieldName());
        response.setUpdatedAtFieldName(connectorMetadataDTO.getUpdatedAtFieldName());
        response.setWatermarkCustomizable(connectorMetadataDTO.isWatermarkCustomizable());
        response.setDefaultApiLimit(connectorMetadataDTO.getDefaultApiLimit());
        response.setCapabilities(connectorMetadataDTO.getCapabilities());
        response.setSupportedAuthTypes(connectorMetadataDTO.getSupportedAuthTypes());
        response.setConfigureFields(connectorMetadataDTO.getConfigureFields());
        response.setWatermarkFieldOverrides(connectorMetadataDTO.getWatermarkFieldOverrides());
        response.setOrgId(connectorMetadataDTO.getOrgId());
        response.setDisabledMessage(connectorMetadataDTO.getDisabledMessage());
        response.setCustom(connectorMetadataDTO.isCustom());
        response.setCustomSynapseIdentifier(connectorMetadataDTO.getCustomSynapseIdentifier());
        response.setPublishToGlobal(connectorMetadataDTO.isPublishToGlobal());
        response.setFileName(connectorMetadataDTO.getFileName());
        response.setApiMaxCrudSize(connectorMetadataDTO.getApiMaxCrudSize());

        return response;

    }

}
