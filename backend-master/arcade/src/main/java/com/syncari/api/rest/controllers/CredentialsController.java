package com.syncari.api.rest.controllers;


import com.syncari.api.core.util.ObjectTransformer;
import com.syncari.api.rest.controllers.data.ConnectorResponse;
import com.syncari.api.rest.controllers.data.CredentialRequest;
import com.syncari.api.rest.controllers.data.CredentialResponse;
import com.syncari.connector.ConnectorType;
import com.syncari.core.credentials.AuthCredentialsSeed;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Connector;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.CredentialsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.READ_CONNECTOR;
import static com.syncari.core.security.Permissions.WRITE_CONNECTOR;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/credentials")
public class CredentialsController {
    @Autowired
    ConnectorService connectorService;

    @Autowired
    CredentialsService credentialsService;

    @Autowired
    ObjectTransformer transformer;


    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/describe")
    public List<ConnectorMetadata> describe() {
        var credentials = connectorService.listMetadataByConnectorType(ConnectorType.Credential);
        credentials.stream().forEach(c -> c.setSupportedAuthTypes(List.of(AuthCredentialsSeed.getAuthMetadata(c.getName()))));
        return credentials;
    }

    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST)
    public ConnectorResponse save(@RequestBody CredentialRequest credential) {
        Connector toBeSaved = transformer.toConnector(credential);
        var saved = credentialsService.save(toBeSaved);
        return transformer.toConnectorResponse(saved, List.of());
    }

    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST, value = "/{connectorId}")
    public ConnectorResponse edit(@PathVariable String connectorId, @RequestBody CredentialRequest credential) {
        if (StringUtils.isBlank(connectorId))
            throw new RuntimeException("Connector id cannot be null for edits");
        Connector c = transformer.toConnector(credential);
        c.setId(connectorId);
        c = connectorService.save(c);
        return transformer.toConnectorResponse(c, List.of());
    }

    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET)
    public List<ConnectorResponse> list(@RequestParam(value = "type", required = false) String type) {

        List<Connector> connectorList = new ArrayList<>();
        if (StringUtils.isBlank(type)) {
            // get synapses + credentials
            connectorList.addAll(connectorService.list());
            connectorList.addAll(connectorService.listByConnectorType(ConnectorType.Credential));
        } else if (type.equalsIgnoreCase(ConnectorType.Credential.name())) {
            connectorList.addAll(connectorService.listByConnectorType(ConnectorType.Credential));
        } else if (type.equalsIgnoreCase(ConnectorType.Synapse.name())) {
            connectorList.addAll(connectorService.list());
        }

        return connectorList.stream().map(c ->
                transformer.toConnectorResponse(c,  connectorService.getSetting(c.getId()))).collect(Collectors.toList());
    }
}
