package com.syncari.connector.auth;

import com.syncari.connector.ConnectorErrorCodes;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.DefaultAuthTokenHandler;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.OAuthRequest;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.connector.service.def.OauthAuthenticationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static java.lang.String.format;

@Slf4j
@Component(Constants.GENERIC_SIMPLE_OAUTH)
public class GenericSimpleOAuthService implements OauthAuthenticationService {

    @Autowired
    DefaultAuthTokenHandler tokenHandler;

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo connector, List<String> entityNames) {

        TestConnectionResponse response = new TestConnectionResponse();
        try{
            AuthConfig updatedConfig = refreshToken(connector);

            response.setAuthConfig(updatedConfig);
            log.info(format("Successfully authenticated OAuth credentials for %s", connector.getName()));
            return response;
        } catch (Exception e) {
            response.setCode(ConnectorErrorCodes.CONNECTION_ERROR);
            response.setMessage(StringUtils.isBlank(e.getMessage()) ? ConnectorErrorCodes.CONNECTION_ERROR : e.getMessage());
        }
        return response;
    }

    @Override
    public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
       throw new RuntimeException("Access Token flow not supported");
    }

    @Override
    public AuthConfig refreshToken(ConnectorInfo connector) {
        AuthConfig config = connector.getAuthConfig();
        String oauthUrl = config.getEndpoint();
        Map<String, String> map = Map.of(DefaultAuthTokenHandler.GRANT_TYPE, DefaultAuthTokenHandler.CLIENT_CREDENTIALS,
                DefaultAuthTokenHandler.CLIENT_ID, config.getClientId(),
                DefaultAuthTokenHandler.CLIENT_SECRET, config.getClientSecret());

        var authConfig = tokenHandler.refreshToken(config, oauthUrl, map);

        config.setAccessToken(authConfig.getAccessToken());
        if (StringUtils.isEmpty(authConfig.getRefreshToken())) {
            config.setRefreshToken(authConfig.getAccessToken());
        }else{
            config.setRefreshToken(authConfig.getRefreshToken());
        }
        config.setExpiresIn(authConfig.getExpiresIn());
        config.setLastRefreshed(authConfig.getLastRefreshed());
        config.setAccessToken(authConfig.getAccessToken());
        return config;
    }

    @Override
    public String getOAuthUri(ConnectorInfo connector) {
        return null;
    }
}
