package com.syncari.core.actions.http;

import com.syncari.connector.ConnectorType;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class AuthenticationInfo implements Serializable  {
    private ConnectorType credentialType;
    private String credentialId;
    private String metadataId;
}
