package com.syncari.karibu.rest.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.api.client.util.ArrayMap;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.AuthField;
import com.syncari.connector.data.AuthMetadata;
import com.syncari.connector.data.AuthType;
import com.syncari.core.model.Connector;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.core.model.misc.ApiConfig;
import com.syncari.core.model.misc.ConnectorSetting;
import com.syncari.core.model.misc.ConnectorStatus;
import com.syncari.core.service.ConnectorMetadataService;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.OAuthService;
import com.syncari.karibu.rest.exceptions.BadRequestException;
import com.syncari.karibu.rest.exceptions.NotFoundException;
import com.syncari.karibu.rest.request.SynapseRequest;
import com.syncari.karibu.rest.response.SynapseResponse;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

@Component
public class SynapseUtils {

    @Autowired
    ConnectorService connectorService;

    @Autowired
    ConnectorMetadataService connectorMetadataService;

    @Autowired
    OAuthService oAuthService;


    public Connector validateSynapseNotDeleted(String synapseId, boolean refreshAuth) {
        Connector connector = connectorService.find(synapseId, refreshAuth).orElseThrow(() ->
                new NotFoundException(i18n("synapse_not_found", synapseId)));

        if (connector.getStatus().equals(ConnectorStatus.DELETED))
            throw new NotFoundException(i18n("synapse_not_found", synapseId));

        if (connector.getName().equals("syncari"))
            throw new RuntimeException(i18n("syncari_synapse"));

        return connector;
    }

    public List<SynapseResponse> getSynapseListResponse (List<Connector> connectorList) {
        List<SynapseResponse> responses = new ArrayList<>();
        for (Connector connector : connectorList) {
            responses.add(getResponse(connector));
        }
        return responses;
    }

    public SynapseResponse getSynapseResponse(Connector connector){
        return getResponse(connector);
    }

    public Connector validateCreateSynapseRequest(SynapseRequest synapseRequest) {
        // validate mandatory fields
        validateSynapseMadatoryFields(synapseRequest);

        // validate authType
        validateAuthType(synapseRequest.getConfiguration().get("authType").toString());

        // get connector metadata to validate request against
        ConnectorMetadata connectorMetadata = getConnectorMetadata(synapseRequest.getSynapseTypeId());

        // get expected field names and convert it to a string collection. This will help in building the connector to pass to the service layer
        Map<String, Object> maps = getRequiredFields(connectorMetadata, synapseRequest);
        Collection<String> requiredFields = (Collection<String>) maps.get("requiredFields");
        Map<String, Object> authConfigMap = (Map<String, Object>) maps.get("authConfigMap");
        Map<String, Object> metaConfigMap = (Map<String, Object>) maps.get("metaConfigMap");

        // validate proper fields have been passed in
        validateRequiredFields(synapseRequest, requiredFields);

        // build and return connector to be created off of the synapse request
        return buildConnector(synapseRequest, authConfigMap, metaConfigMap);

    }

    public Connector validateUpdateSynapseRequest(SynapseRequest synapseRequest, Connector connector){
        // new configuration
        boolean newConfiguration = false;

        // validate synapse name
        if(synapseRequest.getName() != null && !synapseRequest.getName().equals(connector.getName())){
            connector.setName(synapseRequest.getName());
        } else {
            synapseRequest.setName(connector.getName());
        }

        // validate synapseTypeId
        if (synapseRequest.getSynapseTypeId() != null && !synapseRequest.getSynapseTypeId().equals(connector.getMetadataId())) {
            throw new BadRequestException(i18n("cannot_update_synapse_type"));
        } else {
            synapseRequest.setSynapseTypeId(connector.getMetadataId());
        }

        if (synapseRequest.getConfiguration() != null) {

            // get connector metadata to validate request against
            ConnectorMetadata connectorMetadata = getConnectorMetadata(synapseRequest.getSynapseTypeId());
            if (!synapseRequest.getConfiguration().containsKey("authType"))
                synapseRequest.getConfiguration().put("authType", connector.getMetaConfig().get("authType"));

            // get expected field names and convert it to a string collection. This will help in building the connector to pass to the service layer
            Map<String, Object> maps = getRequiredFields(connectorMetadata, synapseRequest);
            Collection<String> requiredFields = (Collection<String>) maps.get("requiredFields");
            Map<String, Object> authConfigMap = (Map<String, Object>) maps.get("authConfigMap");
            Map<String, Object> metaConfigMap = (Map<String, Object>) maps.get("metaConfigMap");

            // validate authType
            if (synapseRequest.getConfiguration().containsKey("authType") &&
                    !synapseRequest.getConfiguration().get("authType").equals(connector.getMetaConfig().get("authType"))) {

                // validate authType
                validateAuthType(synapseRequest.getConfiguration().get("authType").toString());

                // validate proper fields have been passed in
                validateRequiredFields(synapseRequest, requiredFields);

                // build and return connector to be created off of the synapse request
                return buildConnector(synapseRequest, authConfigMap, metaConfigMap);
            } else {
                ObjectMapper mapper = new ObjectMapper();
                mapper.registerModule(new JavaTimeModule());

                Map<String, Object> authConfig = mapper.convertValue(connector.getAuthConfig(), Map.class);
                Map<String, Object> metaConfig = connector.getMetaConfig();
                for (String authConfigField : authConfigMap.keySet()) {
                    if (synapseRequest.getConfiguration().get(authConfigField) != null)
                        authConfig.put(authConfigField, synapseRequest.getConfiguration().get(authConfigField));
                }
                for (String metaConfigField : metaConfigMap.keySet()) {
                    if (synapseRequest.getConfiguration().get(metaConfigField) != null)
                        metaConfig.put(metaConfigField, synapseRequest.getConfiguration().get(metaConfigField));
                }
                connector.setAuthConfig(mapper.convertValue(authConfig, AuthConfig.class));
                connector.setMetaConfig(metaConfig);
                if ((null != metaConfig) && (null != metaConfig.get("endpoint"))){
                    connector.setEndpoint(metaConfig.get("endpoint").toString());
                }
                return connector;
            }
        }

        return connector;
    }


    private SynapseResponse getResponse(Connector connector) {
        SynapseResponse response = new SynapseResponse();
        response.setId(connector.getId());
        response.setName(connector.getName());
        response.setSynapseTypeId(connector.getMetadataId());
        response.setStatus(connector.getStatus().toString());
        if(connector.getSchemaRefreshStatus() != null)
            response.setRefreshSchemaStatus(connector.getSchemaRefreshStatus().toString());
        response.setConfiguration(getConfiguration(connector));
        response.setCreatedBy(connector.getCreatedBy());
        response.setCreatedAt(connector.getCreatedAt());
        response.setUpdatedBy(connector.getUpdatedBy());
        response.setUpdatedAt(connector.getUpdatedAt());
        return response;
    }

    private Map<String, Object> getConfiguration(Connector connector) {
        // setup
        Map<String, Object> configuration = new ArrayMap<>() {};
        ObjectMapper oMapper = new ObjectMapper();
        oMapper.registerModule(new JavaTimeModule());
        Map<String, Object> authConfig = new ArrayMap<>() {
        };
        Map<String, Object> metaConfig = new ArrayMap<>() {
        };

        // get authConfig values from the connector
        if (null != connector.getAuthConfig()) {
            authConfig = oMapper.convertValue(connector.getAuthConfig(), Map.class);
            authConfig.values().removeIf(Objects::isNull);
        }
        //Remove sensitive info
        connector.getMetaConfig().remove(ConnectorService.GCP_CRED_KEY);
        // get metadataConfig from the connector
        if (null != connector.getMetaConfig()) {
            metaConfig = oMapper.convertValue(connector.getMetaConfig(), Map.class);
            metaConfig.values().removeIf(Objects::isNull);
        }

        // get connector metadata to determine which fields to return
        ConnectorMetadata connectorMetadata = connectorMetadataService.findById(connector.getMetadataId()).get();

        // Loop through authMetadataList to get fields to return in response
        List<AuthMetadata> authMetadataList = connectorMetadata.getSupportedAuthTypes();
        for (AuthMetadata authMetadata : authMetadataList) {
            if (authMetadata.getAuthType().toString().equals(connector.getMetaConfig().get("authType"))) {
                for (AuthField authField : authMetadata.getFields()) {
                    configuration.put(authField.getName(), authConfig.get(authField.getName()));
                }
            }
        }

        if(connector.getAuthType() != null && connector.getAuthType().name().equals("Oauth")) {
            if (authConfig.get("accessToken") != null)
                configuration.put("accessToken", authConfig.get("accessToken"));
            if (authConfig.get("refreshToken") != null)
                configuration.put("refreshToken", authConfig.get("refreshToken"));
            if (authConfig.get("token") != null)
                configuration.put("token", authConfig.get("token"));
        }
        if(connector.getAuthType() != null && connector.getAuthType().name().equals("Oauth") && connectorMetadata.getName().equals("hubspot")) {
            if (authConfig.get("clientId") != null)
                configuration.put("clientId", authConfig.get("clientId"));
            if (authConfig.get("clientSecret") != null)
                configuration.put("clientSecret", authConfig.get("clientSecret"));
            if (connector.getOAuthRedirectUrl() != null)
                configuration.put("oAuthRedirectUrl", connector.getOAuthRedirectUrl());
        }



        // get additional configuration fields to return in response
        List<AuthField> configurationFields = connectorMetadata.getConfigureFields();
        for (AuthField authField : configurationFields) {
            configuration.put(authField.getName(), metaConfig.get(authField.getName()));
        }
        // return configuration values
        return configuration;
    }

    private void validateSynapseMadatoryFields(SynapseRequest synapseRequest){
        if (synapseRequest.getName() == null || synapseRequest.getSynapseTypeId() == null ||
                synapseRequest.getConfiguration().get("authType") == null)
            throw new BadRequestException(format(i18n("missing_required_fields")));

    }

    private void validateAuthType(String authType){
        if(!EnumUtils.isValidEnum(AuthType.class, authType))
            throw new BadRequestException(format(i18n("invalid_auth_type"), authType));
    }

    private ConnectorMetadata getConnectorMetadata(String synapseTypeId) {
        try {
            return connectorMetadataService.findById(synapseTypeId).get();
        } catch (Exception e) {
            throw new BadRequestException("Invalid synapseTypeId");
        }
    }

    private Map<String, Object> getRequiredFields(ConnectorMetadata connectorMetadata, SynapseRequest synapseRequest){
        Collection<String> requiredFields = new ArrayList<>();
        Map<String, Object> authConfigMap = new ArrayMap<>() {};
        Map<String, Object> metaConfigMap = new ArrayMap<>() {};

        for (AuthMetadata authMetadata : connectorMetadata.getSupportedAuthTypes()) {
            if (authMetadata.getAuthType().toString().equals(synapseRequest.getConfiguration().get("authType").toString())) {
                for (AuthField authField : authMetadata.getFields()) {
                    requiredFields.add(authField.getName());
                    authConfigMap.put(authField.getName(), synapseRequest.getConfiguration().get(authField.getName()));
                }
            }
        }

        for (AuthField authField : connectorMetadata.getConfigureFields()) {
            requiredFields.add(authField.getName());
            metaConfigMap.put(authField.getName(), synapseRequest.getConfiguration().get(authField.getName()));
        }
        authConfigMap.put("endpoint", metaConfigMap.get("endpoint"));

        if(synapseRequest.getConfiguration().get("authType").equals("Oauth")) {
            if (synapseRequest.getConfiguration().get("accessToken") != null) {
                requiredFields.add("accessToken");
                authConfigMap.put("accessToken", synapseRequest.getConfiguration().get("accessToken"));
            }
            if (synapseRequest.getConfiguration().get("refreshToken") != null) {
                requiredFields.add("refreshToken");
                authConfigMap.put("refreshToken", synapseRequest.getConfiguration().get("refreshToken"));
                authConfigMap.put("expiresIn", "600");
            }
            if (synapseRequest.getConfiguration().get("oAuthRedirectUrl") != null) {
                requiredFields.add("oAuthRedirectUrl");
                authConfigMap.put("oAuthRedirectUrl", synapseRequest.getConfiguration().get("oAuthRedirectUrl"));
            }
        }

        if(synapseRequest.getConfiguration().get("authType").equals("Oauth") && connectorMetadata.getName().equals("hubspot")) {
            if (synapseRequest.getConfiguration().get("clientId") != null) {
                requiredFields.add("clientId");
                authConfigMap.put("clientId", synapseRequest.getConfiguration().get("clientId"));
            }
            if (synapseRequest.getConfiguration().get("clientSecret") != null) {
                requiredFields.add("clientSecret");
                authConfigMap.put("clientSecret", synapseRequest.getConfiguration().get("clientSecret"));
            }

            if(synapseRequest.getConfiguration().containsKey("oAuthScopes")) {
                String scopes = String.valueOf(synapseRequest.getConfiguration().get("oAuthScopes"));
                if(StringUtils.isNotBlank(scopes) && !scopes.contains("oauth")) {
                    throw new BadRequestException(format(i18n("missing_required_scope")));
                }
                metaConfigMap.put("oAuthScopes", synapseRequest.getConfiguration().get("oAuthScopes"));
                requiredFields.add("oAuthScopes");
            }
        }

        //I am not sure why required flag check is not performed in existing logic. To be safe excluding only contactAccountMerge.
        requiredFields.remove("contactAccountMerge");
        Map<String, Object> maps=new HashMap<String, Object>();
        maps.put("requiredFields", requiredFields);
        maps.put("authConfigMap", authConfigMap);
        maps.put("metaConfigMap", metaConfigMap);

        return maps;

    }

    private void validateRequiredFields(SynapseRequest synapseRequest, Collection<String> requiredFields){
        for (String key : requiredFields) {
            if (!synapseRequest.getConfiguration().containsKey(key))
                throw new BadRequestException(getConfigurationErrorMessage(synapseRequest, requiredFields));
        }
        for (String configurationKey : synapseRequest.getConfiguration().keySet()){
            if (!requiredFields.contains(configurationKey))
                throw new BadRequestException(getConfigurationErrorMessage(synapseRequest, requiredFields));
        }
    }

    private String getConfigurationErrorMessage(SynapseRequest synapseRequest, Collection<String> requiredFields) {
        // prepare error message
        String[] strings = {"The following fields are required for AuthType = ",
                synapseRequest.getConfiguration().get("authType").toString(), ":", requiredFields.toString()};

        return String.join(" ", strings);

    }

    private Connector buildConnector(SynapseRequest synapseRequest, Map<String, Object> authConfigMap, Map<String, Object> metaConfigMap) {
        Connector transformed = new Connector(synapseRequest.getName(), synapseRequest.getSynapseTypeId(),
                (metaConfigMap.get("endpoint") != null) ? metaConfigMap.get("endpoint").toString() : null);

        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        AuthConfig authConfig = mapper.convertValue(authConfigMap, AuthConfig.class);

        // set connector values
        transformed.setAuthConfig(authConfig);
        ApiConfig apiConfig = new ApiConfig(0);
        transformed.setApiConfig(apiConfig);
        transformed.setAuthType(AuthType.valueOf(synapseRequest.getConfiguration().get("authType").toString()));
        transformed.setMetaConfig(metaConfigMap);
        ConnectorSetting connectorSetting = new ConnectorSetting();
        connectorSetting.setApiQuota(0);
        connectorSetting.setSyncRate(0);
        connectorSetting.setBootstrapWithSyncari(false);
        transformed.setSetting(connectorSetting);

        return transformed;

    }

}
