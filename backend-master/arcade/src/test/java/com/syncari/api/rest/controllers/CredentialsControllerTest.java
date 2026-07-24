package com.syncari.api.rest.controllers;

import com.syncari.api.rest.controllers.data.ConnectorRequest;
import com.syncari.api.rest.controllers.data.ConnectorResponse;
import com.syncari.connector.ConnectorType;
import com.syncari.connector.Constants;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.AuthType;
import com.syncari.core.model.Connector;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.core.service.ConnectorService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.READ_CONNECTOR;
import static com.syncari.core.security.Permissions.WRITE_CONNECTOR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CredentialsControllerTest extends AbstractSyncariTest{
    @Autowired
    CredentialsController credentialsController;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    EndSystemConfig config;

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_CONNECTOR})
    public void testDescribeCredentials() {

        var connectorsMetadata = credentialsController.describe();

        var conn = connectorsMetadata.stream().filter(c -> c.getName().equals(Constants.GENERIC_API_KEY)).findFirst();
        assertTrue(conn.isPresent());
        assertEquals(ConnectorType.Credential, conn.get().getType());
        assertEquals(AuthType.ApiKey, conn.get().getSupportedAuthTypes().get(0).getAuthType());

        conn = connectorsMetadata.stream().filter(c -> c.getName().equals(Constants.GENERIC_BEARER_TOKEN)).findFirst();
        assertTrue(conn.isPresent());
        assertEquals(ConnectorType.Credential, conn.get().getType());
        assertEquals(AuthType.ApiSecretKey, conn.get().getSupportedAuthTypes().get(0).getAuthType());


        conn = connectorsMetadata.stream().filter(c -> c.getName().equals(Constants.GENERIC_SIMPLE_OAUTH)).findFirst();
        assertTrue(conn.isPresent());
        assertEquals(ConnectorType.Credential, conn.get().getType());
        assertEquals(AuthType.SimpleOAuth, conn.get().getSupportedAuthTypes().get(0).getAuthType());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_CONNECTOR})
    public void list() {

        Connector connector = new Connector("sfdccon2", connectorService.describe(Constants.SALESFORCE).getId(),
                config.getSalesforceUrl());
        connector.setAuthConfig(new AuthConfig(config.getUser(), config.getPassword(), config.getToken()));
        var saved = connectorService.save(connector);

        Connector credential = new Connector("OAuth Credential",
                connectorService.listMetadataByConnectorType(ConnectorType.Credential).stream().filter(c -> c.getName().equals(Constants.GENERIC_SIMPLE_OAUTH)).findFirst().get(),
                "http://www.example.com/oauth", "qwqewe", "wqeqeqweqe", false);

        saved = connectorService.save(credential);

        var connectors = credentialsController.list(null);
        // list of connectors contains both synapse and credentials and does not contain syncari connector
        assertTrue(!connectors.stream().anyMatch(c -> c.getName().equals("syncari")));
        assertTrue(connectors.stream().anyMatch(c -> c.getName().equals("OAuth Credential")));
        connectorService.delete(saved.getId(), true);
    }

}
