package com.syncari.api.rest.controllers.data;

import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.AuthType;
import com.syncari.core.model.misc.ApiConfig;
import com.syncari.core.model.misc.ConnectorSetting;
import com.syncari.core.utils.ValidationUtils;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
@AllArgsConstructor
public class CredentialRequest {
    private String name;
    private String metadataId;
    private AuthConfig authConfig;
    private AuthType authType;

    public CredentialRequest() {
    }
}
