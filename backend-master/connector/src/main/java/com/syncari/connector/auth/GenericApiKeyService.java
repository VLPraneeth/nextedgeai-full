package com.syncari.connector.auth;

import com.syncari.connector.ConnectorErrorCodes;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.DefaultAuthTokenHandler;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.OAuthRequest;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.OauthAuthenticationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static java.lang.String.format;

@Slf4j
@Component(Constants.GENERIC_API_KEY)
public class GenericApiKeyService implements AuthenticationService {

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo connector, List<String> entityNames) {
        // Do nothing for now.
        var testConnectionResponse = new TestConnectionResponse();
        testConnectionResponse.setAuthConfig(connector.getAuthConfig());
        return testConnectionResponse;
    }

}
