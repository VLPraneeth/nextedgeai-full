package com.syncari.connector.service.def;

import java.util.List;
import java.util.Map;

import com.syncari.connector.Capability;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.ConnectorType;
import com.syncari.connector.custom.CloudFunctionInfo;
import com.syncari.connector.data.AuthField;
import com.syncari.connector.data.AuthMetadata;
import com.syncari.connector.data.EntityParams;
import com.syncari.connector.data.SynapseInfo;
import com.syncari.connector.data.UIMetadata;

public interface SynapseInfoService {

    String helpArticlesBaseUrl = "https://support.syncari.com/hc/en-us/articles";
    String helpSectionsBaseUrl = "https://support.syncari.com/hc/en-us/sections";
    String SYNAPSE_COMING_SOON_ARTICLE = "/360056102571-Synapse-Coming-Soon-";
	
    List<AuthMetadata> getSupportedAuthTypes();

    List<AuthField> getConfigureFields();
    
    // Syncari entity apiName to end system entity api name
    Map<String, String> getEntityMappings();

    // Syncari field apiName to end system field api name
    Map<String, String> getAttributeMappings(String entityApiName);

    String getName();


    String getCategory();

    UIMetadata getUIMetadata();

    default String getDisabledMessage() {
        return null;
    }

    default ConnectorType getType() {
        return ConnectorType.Synapse;
    }
    
    default boolean isSource() {
        return true;
    }
    
    default boolean isSink() {
        return true;
    }
    
    default List<Capability> getCapabilities() {
    	return List.of();
    }
    String getCapabilitiesArticleId();

    default boolean validate(ConnectorInfo connector) {
        return true;
    }
    
    default boolean validateEntityConfig(EntityParams params) {
    	return true;
    }

    default boolean supportsNoWatermark(ConnectorInfo connectorInfo) { return false; }

    default SynapseInfo about(CloudFunctionInfo cloudFunctionInfo) {
        SynapseInfo synapseInfo = new SynapseInfo();
        synapseInfo.setName(getName());
        synapseInfo.setSupportedAuthTypes(getSupportedAuthTypes());
        synapseInfo.setConfiguredFields(getConfigureFields());
        synapseInfo.setDisabledMessage(getDisabledMessage());
        synapseInfo.setCategory(getCategory());
        synapseInfo.setMetadata(getUIMetadata());
        synapseInfo.setType(getType());
        synapseInfo.setCapabilities(getCapabilities());
        return synapseInfo;
    }

}
