package com.syncari.api.rest.controllers.data;

import com.syncari.connector.data.AuthMetadata;
import com.syncari.core.model.ConnectorMetadata;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class ServiceCredentialDTO implements Serializable {

    private String id;
    private String name;
    private String displayName;
    private String description;
    private AuthMetadata supportedAuthType;

    public ServiceCredentialDTO(ConnectorMetadata connectorMetadata) {
        id = connectorMetadata.getId();
        name = connectorMetadata.getName();
        displayName = connectorMetadata.getDisplayName();
        description = connectorMetadata.getDescription();
    }

}
