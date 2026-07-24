package com.syncari.api.rest.controllers.data;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SSOAuthConfigDTO {

    String provider;
    // SAML config
    private String entityId;
    private String ssoUrl;
    private String certificate;
}
