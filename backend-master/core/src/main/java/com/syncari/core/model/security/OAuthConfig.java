package com.syncari.core.model.security;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.annotation.PersistenceConstructor;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Data
@AllArgsConstructor
@Accessors(chain = true)
public class OAuthConfig {
    private String oAuthProvider;
    private String clientId;
    private String clientSecret;
    private String syncariRedirectIdentifier;
    private List<String> additionalScopes = new ArrayList<>();
    private List<String> optionalScopes = new ArrayList<>();

    @PersistenceConstructor
    public OAuthConfig(String oAuthProvider, String clientId, String clientSecret) {
        this.oAuthProvider = oAuthProvider;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        // by default the identifier is client_id
        this.syncariRedirectIdentifier = "client_id";
    }

    @PersistenceConstructor
    public OAuthConfig(String oAuthProvider, String clientId, String clientSecret, String syncariRedirectIdentifier) {
        this.oAuthProvider = oAuthProvider;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.syncariRedirectIdentifier = syncariRedirectIdentifier;
    }

    public void validate(){
        validateCondition(StringUtils.isEmpty(oAuthProvider), i18n("invalid_oauth_config_property", "OAuth Provider"));
        validateCondition(StringUtils.isEmpty(clientId), i18n("invalid_oauth_config_property", "Client Id"));
        validateCondition(StringUtils.isEmpty(clientSecret), i18n("invalid_oauth_config_property", "Client Secret"));
        validateCondition(StringUtils.isEmpty(syncariRedirectIdentifier), i18n("invalid_oauth_config_property", "Syncari Redirect Identifier"));
    }

    public OAuthConfig copy() {
        return new OAuthConfig(oAuthProvider, clientId, clientSecret, syncariRedirectIdentifier, additionalScopes, optionalScopes);
    }
}
