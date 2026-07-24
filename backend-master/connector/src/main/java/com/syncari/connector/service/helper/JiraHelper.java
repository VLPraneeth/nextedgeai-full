package com.syncari.connector.service.helper;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.DefaultAuthTokenHandler;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.AuthMetadata;
import com.syncari.connector.data.OAuthRequest;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JiraHelper {
    @Autowired
    DefaultAuthTokenHandler tokenHandler;
    private static String OAUTH_URL = "https://auth.atlassian.com/oauth/token";
    private static String OAUTH_HOST = "https://auth.atlassian.com";

    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getUserApiKey());
    }

    public AuthConfig refreshToken(ConnectorInfo connector) {
        AuthConfig config = connector.getAuthConfig();
        Map<String, String> map = Map.of(DefaultAuthTokenHandler.GRANT_TYPE, DefaultAuthTokenHandler.REFRESH_TOKEN,
                DefaultAuthTokenHandler.REFRESH_TOKEN, config.getRefreshToken(), DefaultAuthTokenHandler.CLIENT_ID,
                config.getClientId(), DefaultAuthTokenHandler.CLIENT_SECRET, config.getClientSecret());

        return tokenHandler.refreshToken(config, OAUTH_URL, map);
    }

    public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
        Map<String, String> map = Map.of(DefaultAuthTokenHandler.GRANT_TYPE, "authorization_code",
                DefaultAuthTokenHandler.CODE, oAuthRequest.getCode(), DefaultAuthTokenHandler.CLIENT_ID,
                oAuthRequest.getConfig().getClientId(), DefaultAuthTokenHandler.CLIENT_SECRET,
                oAuthRequest.getConfig().getClientSecret(), DefaultAuthTokenHandler.REDIRECT_URI,
                oAuthRequest.getRedirectUri());

        return tokenHandler.getAccessToken(OAUTH_URL, map);
    }

    public String getOAuthUri() {
        return "/authorize?audience=api.atlassian.com&client_id={{client_id}}&scope=read:servicedesk-request%20offline_access&redirect_uri={{redirect_uri}}&"
                + "state={{client_id}}&response_type=code&prompt=consent";
    }

    public String getAuthHost(AuthConfig config) {
        return OAUTH_HOST;
    }

}
