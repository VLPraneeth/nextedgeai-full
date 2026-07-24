package com.syncari.api.rest.controllers;

import com.syncari.api.rest.controllers.data.ConnectorRequest;
import com.syncari.api.rest.controllers.data.ConnectorResponse;
import com.syncari.connector.Constants;
import com.syncari.connector.config.AuthConfig;
import com.syncari.core.model.Connector;
import com.syncari.core.service.ConnectorMetadataService;
import com.syncari.core.service.ConnectorService;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import static com.syncari.core.security.Permissions.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class ConnectorControllerTest extends AbstractSyncariTest{
    @Autowired
    ConnectorController connectorController;
    @Autowired
    ConnectorMetaController metaController;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    ConnectorMetadataService metaService;

    @Autowired
    EndSystemConfig config;

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_CONNECTOR})
    public void getCapabilitiesArticle(){
        String body = metaController.getCapabilities(metaService.findByName(Constants.HUBSPOT).get().getId());
        assertTrue(body != null);
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_CONNECTOR})
    public void getDefaultMappings() throws IOException {
        List<Mappings> body = metaController.getDefaultMappings(metaService.findByName(Constants.SALESFORCE).get().getId());
        assertTrue(body != null);
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_CONNECTOR})
    public void getSampleSynapse() {
        ResponseEntity<Resource> body = metaController.getSampleSynapse();
        assertTrue(body != null);
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_CONNECTOR, READ_CONNECTOR})
    public void testListExistingConnectors(){
        String connectorName = "sfdccon2" + "_" + RandomUtils.nextInt(1, 100);
        Connector connector = new Connector(connectorName, connectorService.describe(Constants.SALESFORCE).getId(),
                config.getSalesforceUrl());
        connector.setAuthConfig(new AuthConfig(config.getUser(), config.getPassword(), config.getToken()));
        Connector saved = connectorService.save(connector);
        List<ConnectorResponse> response = connectorController.list();
        assertTrue(response.size()>1);
        List<ConnectorResponse> sfdc2ConResp = response.stream().filter(con -> con.getName().equals(connectorName)).collect(Collectors.toList());
        assertEquals(1, sfdc2ConResp.size());
        assertEquals("*****", sfdc2ConResp.get(0).getAuthenticationConfig().getPassword());
        assertEquals("*****", sfdc2ConResp.get(0).getAuthenticationConfig().getToken());
        assertNotNull(response.stream().filter(con -> "Syncari".equals(con.getName())).findFirst().get().getId());
        connectorService.delete(saved.getId(), true);
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_CONNECTOR, READ_CONNECTOR})
    public void testWithoutEditExistingConnector(){
        String connectorName = "sfdccon2" + "_" + RandomUtils.nextInt(1, 100);
        Connector connector = new Connector(connectorName, connectorService.describe(Constants.SALESFORCE).getId(),
                config.getSalesforceUrl());
        connector.setAuthConfig(new AuthConfig(config.getUser(), config.getPassword(), config.getToken()));
        Connector saved = connectorService.save(connector);
        List<ConnectorResponse> response = connectorController.list();
        assertTrue(response.size()>1);
        List<ConnectorResponse> sfdc2ConResp = response.stream().filter(con -> con.getName().equals(connectorName)).collect(Collectors.toList());
        assertEquals(1, sfdc2ConResp.size());
        ConnectorResponse returnedConnectorResponse = sfdc2ConResp.get(0);
        ConnectorRequest request = new ConnectorRequest(returnedConnectorResponse.getName(), returnedConnectorResponse.getMetadataId(), returnedConnectorResponse.getEndpoint());
        request.setAuthenticationConfig(returnedConnectorResponse.getAuthenticationConfig());
        request.setApiConfig(returnedConnectorResponse.getApiConfig());
        ConnectorResponse editResponse = connectorController.edit(saved.getId(),request);
        assertEquals("*****", editResponse.getAuthenticationConfig().getPassword());
        assertEquals("*****", editResponse.getAuthenticationConfig().getToken());
        Connector conAfterEdit = connectorService.findLite(editResponse.getId());
        assertEquals(saved.getAuthConfig().getPassword(), conAfterEdit.getAuthConfig().getPassword());
        assertEquals(saved.getAuthConfig().getToken(), conAfterEdit.getAuthConfig().getToken());
        connectorService.delete(saved.getId(), true);
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_CONNECTOR, READ_CONNECTOR})
    public void testEditExistingConnectorPassword(){
        String connectorName = "sfdccon2" + "_" + RandomUtils.nextInt(1, 100);
        Connector connector = new Connector(connectorName, connectorService.describe(Constants.SALESFORCE).getId(),
                config.getSalesforceUrl());
        connector.setAuthConfig(new AuthConfig(config.getUser(), config.getPassword(), config.getToken()));
        Connector saved = connectorService.save(connector);
        List<ConnectorResponse> response = connectorController.list();
        assertTrue(response.size()>1);
        List<ConnectorResponse> sfdc2ConResp = response.stream().filter(con -> con.getName().equals(connectorName)).collect(Collectors.toList());
        assertEquals(1, sfdc2ConResp.size());
        ConnectorResponse returnedConnectorResponse = sfdc2ConResp.get(0);
        ConnectorRequest request = new ConnectorRequest(returnedConnectorResponse.getName(), returnedConnectorResponse.getMetadataId(), returnedConnectorResponse.getEndpoint());
        request.setApiConfig(returnedConnectorResponse.getApiConfig());
        AuthConfig newAuthConfig = new AuthConfig("test", "test", config.getToken());
        request.setAuthenticationConfig(newAuthConfig);
        ConnectorResponse editResponse = connectorController.edit(saved.getId(),request);
        Connector conAfterEdit = connectorService.findLite(editResponse.getId());
        assertEquals(newAuthConfig.getPassword(), conAfterEdit.getAuthConfig().getPassword());
        assertEquals(newAuthConfig.getToken(), conAfterEdit.getAuthConfig().getToken());
        assertEquals(newAuthConfig.getUserName(), conAfterEdit.getAuthConfig().getUserName());
        connectorService.delete(saved.getId(), true);
    }
}
