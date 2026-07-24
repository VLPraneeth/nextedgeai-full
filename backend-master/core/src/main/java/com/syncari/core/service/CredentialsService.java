package com.syncari.core.service;

import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.core.DataTransformer;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Connector;
import com.syncari.core.model.misc.ConnectorStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
public class CredentialsService {

    @Autowired
    private DataServiceFactory factory;

    @Autowired
    private ConnectorService connectorService;

    @Autowired
    DataTransformer transformer;


    public Connector save(Connector connector) {
        var toSave = connectorService.save(connector);
        // test this
        var response = test(toSave);
        if (response.isSuccess()) {
            AuthConfig updatedConfig = response.getAuthConfig();
            if(updatedConfig != null) {
                updatedConfig.setLastRefreshed(Instant.now());
                connector.setAuthConfig(updatedConfig);
                connector.setStatus(ConnectorStatus.ACTIVE);
                return connectorService.findAndSave(connectorService.encrypt(connector));
            }
        } else {
            // cleanup
            connectorService.delete(toSave.getId(), true);
            throw new SyncariValidationException("Invalid Auth Credential " + connector.getName());
        }
        return null;
    }

    public TestConnectionResponse test(Connector connector) {
        var authenticationService = factory.getAuthenticationService(connector.getMetadata());

        return authenticationService.testConnection(transformer.toConnectorInfo(connector),
                List.of());
    }


}
