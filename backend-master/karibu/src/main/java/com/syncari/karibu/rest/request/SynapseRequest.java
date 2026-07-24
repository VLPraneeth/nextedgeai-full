package com.syncari.karibu.rest.request;

import com.google.api.client.util.ArrayMap;
import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.data.AuthField;
import com.syncari.connector.data.AuthMetadata;
import com.syncari.connector.data.AuthType;
import com.syncari.core.utils.ValidationUtils;

import com.syncari.karibu.rest.exceptions.BadRequestException;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.EnumUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

@Data
@AllArgsConstructor
public class SynapseRequest {
    private String name;
    private String synapseTypeId;
    private Map<String, Object> configuration;

    public SynapseRequest(String name, String metadataId, String endpoint) {
        this.name = name;
        this.synapseTypeId = synapseTypeId;
        this.configuration = new ArrayMap<>() {};
    }

    public void validate() throws BadRequestException {
        // validate name
        ValidationUtils.validateCondition(!name.matches("^[A-Za-z][\\w|\\-|_|\\s]*$"),"Synapse names should start with an alphabet and can only contain alphanumerics, spaces, hyphens or underscores");

        // validate authType
        ValidationUtils.validateCondition(!EnumUtils.isValidEnum(AuthType.class, configuration.get("authType").toString()), String.join(configuration.get("authType").toString(), " is not a valid AuthType"));

        // validate authType is allowed for synapse type
        //ConnectorMetadata connectorMetadata = SynapseUtil.getSynapseMetaData(synapseTypeId);

        // validate authConfig values for each authType
        AuthMetadata authMetadata;
        switch (configuration.get("authType").toString()) {
            case "UserPassword":
                authMetadata = ConnectorHelper.getUserPwd();
                validateAuthType(authMetadata);
                break;
            case "UserPasswordToken":
                authMetadata = ConnectorHelper.getUserPwdToken();
                validateAuthType(authMetadata);
                break;
            case "ApiKey":
                authMetadata = ConnectorHelper.getUserApiKey();
                validateAuthType(authMetadata);
                break;
            case "ApiSecretKey":
                authMetadata = ConnectorHelper.getUserPwdClientIdSecret();
                validateAuthType(authMetadata);
                break;
            case "Oauth":
                authMetadata = ConnectorHelper.getAccessTokenOauthType();
                validateAuthType(authMetadata);
                break;
            case "SimpleOAuth":
                authMetadata = ConnectorHelper.getSimpleOAuthType();
                validateAuthType(authMetadata);
                break;
            case "NetSuiteTokenBasedAuthentication":
                authMetadata = ConnectorHelper.getTokenBasedOAuthType();
                validateAuthType(authMetadata);
                break;
        }

    }

    public SynapseRequest() {
    }

    private boolean validateAuthType(AuthMetadata authMetadata) {
        // get expected field names and convert it to a string collection
        Collection<String> authMetadataFields = new ArrayList<>();
        authMetadataFields.add("endpoint");
        authMetadataFields.add("authType");
        for (AuthField field : authMetadata.getFields()) {
            authMetadataFields.add(field.getName());
        }

        // prepare error message
        String errorMessage = String.join("The following fields are required for AuthType = ",
                configuration.get("authType").toString(), ": ",authMetadataFields.toString());

        // validate proper fields have been passed in
        for (String key : authMetadataFields) {
            if (!configuration.containsKey(key)) throw new BadRequestException(errorMessage);
        }
        for (String authConfigKey : configuration.keySet()){
            if (!authMetadataFields.contains(authConfigKey)) throw new BadRequestException(errorMessage);
        }

        return true;
    }
}
