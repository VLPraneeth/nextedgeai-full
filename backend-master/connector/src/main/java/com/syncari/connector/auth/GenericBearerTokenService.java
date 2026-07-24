package com.syncari.connector.auth;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.connector.service.def.AuthenticationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component(Constants.GENERIC_BEARER_TOKEN)
public class GenericBearerTokenService implements AuthenticationService {

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo connector, List<String> entityNames) {
        // Do nothing for now.
        var testConnectionResponse = new TestConnectionResponse();
        testConnectionResponse.setAuthConfig(connector.getAuthConfig());
        return testConnectionResponse;
    }

}
