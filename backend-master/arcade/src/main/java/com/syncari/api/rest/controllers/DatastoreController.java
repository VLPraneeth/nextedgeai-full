package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.*;
import static com.syncari.utils.I18n.i18n;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.syncari.api.core.util.ConnectorMetadataTransformer;
import com.syncari.api.rest.controllers.data.ConnectorMetadataDTO;
import com.syncari.api.rest.controllers.data.ConnectorRequest;
import com.syncari.connector.config.AuthConfig;
import org.apache.commons.lang3.StringUtils;
import com.syncari.core.model.DatastoreLag;
import com.syncari.core.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.syncari.api.core.util.ObjectTransformer;
import com.syncari.api.rest.controllers.data.ConnectorResponse;
import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Connector;
import com.syncari.core.model.Instance;
import com.syncari.core.model.ResourceType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/datastore")
public class DatastoreController {
    @Autowired
    DatastoreService datastoreService;
    @Autowired
    DatastoreLagService datastoreLagService;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    ObjectTransformer transformer;
    @Autowired
    AppConfig appConfig;
    @Autowired
    EncryptionService encrypService;
    @Autowired
    FeatureService featureService;
    @Autowired
    ConnectorMetadataTransformer connectorMetadataTransformer;

    @Secured(PROVISION_DATASTORE)
    @RequestMapping(method = RequestMethod.POST, value = "/provision")
    public ConnectorResponse provisionSyncariDatastore() {
        featureService.enableFeature(Features.Datastore);
        try {
            datastoreService.provision(SyncariContext.getSyncariId());
        } catch (Exception e) {
            log.error("Error while provisioning datastore : ",e);
            featureService.disableFeature(Features.Datastore);
            throw new RuntimeException(e);
        }
        return getSyncariDatastore();
    }

    @Secured(READ_DATASTORE)
    @RequestMapping(method = RequestMethod.GET, value = "/metadata/describe")
    public List<ConnectorMetadataDTO> describe() {
        List<ConnectorMetadataDTO> dtos = new ArrayList<>();
        datastoreService.describeDatastore().forEach(x -> {
            dtos.add(connectorMetadataTransformer.toConnectorMetadata(x));
        });
        return dtos;
    }

    //@Secured(READ_DATASTORE)
    @PreAuthorize("hasAnyAuthority('READ_DATASTORE', 'READ_DATA_STUDIO')")
    @RequestMapping(method = RequestMethod.GET)
    public List<ConnectorResponse> getAllDatastoreConnection() {
        return datastoreService.getAllDatastores()
                .stream()
                .map(d -> {
                    if(d.isSyncariDatastore()){
                        return populateSyncariDatastore(d);
                    } else {
                        return transformer.toConnectorResponse(d, List.of());
                    }
                })
                .collect(Collectors.toList());
    }

    @Secured(CREATE_DATASTORE)
    @RequestMapping(method = RequestMethod.POST)
    public ConnectorResponse createDatastoreConnection(@RequestBody ConnectorRequest connector) {

        Connector datastore = transformer.toConnector(connector);
        Connector saved = datastoreService.createExternalDatastoreConnection(datastore);
        return transformer.toConnectorResponse(datastoreService.get(saved.getId()), List.of());
    }

    @Secured(READ_DATASTORE)
    @RequestMapping(method = RequestMethod.GET, value = "/{datastoreId}")
    public ConnectorResponse getDatastoreConnection(@PathVariable String datastoreId) {
        // Backward compatibility - since we removed 'GET /syncari' endpoint
        if("syncari".equals(datastoreId)){
            return getSyncariDatastore();
        }
        return transformer.toConnectorResponse(datastoreService.get(datastoreId), List.of());
    }

    @Secured(UPDATE_DATASTORE)
    @RequestMapping(method = RequestMethod.PUT, value = "/{datastoreId}")
    public ConnectorResponse updateDatastoreConnection(@PathVariable String datastoreId, @RequestBody ConnectorRequest connector) {
        // We only allow credentials to change for an existing datatstore
        Connector existing = datastoreService.get(datastoreId);
        Connector updated = transformer.toConnector(connector);

        // validate if user is trying to update any non credential field and throw an error
        validateUpdates(existing, updated);

        // check for incoming stars **** and replace the values from existing datastore entry
        updated = checkStarsAndUpdateFields(updated, Optional.of(existing));

        Connector saved = datastoreService.updateExternalDatastoreConnection(existing.getId(), updated);
        return transformer.toConnectorResponse(datastoreService.get(saved.getId()), List.of());
    }

    @Secured(DELETE_DATASTORE)
    @RequestMapping(method = RequestMethod.DELETE, value = "/{datastoreId}")
    public void deleteDatastoreConnection(@PathVariable String datastoreId) {
        datastoreService.deleteDatastore(datastoreId);
    }

    @Secured(ACTIVATE_DATASTORE)
    @RequestMapping(method = RequestMethod.PATCH, value = "/{datastoreId}/activate")
    public ConnectorResponse activateDatastore(@PathVariable String datastoreId) {
        datastoreService.activate(datastoreId);
        return transformer.toConnectorResponse(datastoreService.get(datastoreId), List.of());
    }

    @Secured(DEACTIVATE_DATASTORE)
    @RequestMapping(method = RequestMethod.PATCH, value = "/{datastoreId}/deactivate")
    public ConnectorResponse deactivateDatastore(@PathVariable String datastoreId, @RequestParam(value = "backupDatastoreId", required = false) String backupDatastoreId) {
        datastoreService.deactivate(datastoreId);
        if(!StringUtils.isBlank(backupDatastoreId)) {
            Connector backupDatastore = datastoreService.get(backupDatastoreId);
            datastoreService.activate(backupDatastore.getId());
        }
        return transformer.toConnectorResponse(datastoreService.get(datastoreId), List.of());
    }

    @RequestMapping(method = RequestMethod.GET, value = "/lag")
    public List<DatastoreLag> lag() {
        return datastoreLagService.lagForAllEntities();
    }

    @RequestMapping(method = RequestMethod.POST, value = "/deprovision")
    public void deprovision() {
        if(!SyncariContext.getUser().isSuperAdmin()) {
            throw new SyncariValidationException(i18n("no_permission_datastore_deprovisioning"));
        }
        datastoreService.deprovision(SyncariContext.getSyncariId());
        featureService.disableFeature(Features.Datastore);
        featureService.disableFeature(Features.Insights);
        featureService.disableFeature(Features.InsightsProvider);
    }

    private ConnectorResponse populateSyncariDatastore(Connector syncariDatastore) {
        syncariDatastore.setEndpoint(appConfig.getDatastorePublicHost());
        ConnectorResponse connectorResponse = transformer.toConnectorResponse(syncariDatastore, List.of());
        Instance instance = SyncariContext.getInstance();
        Optional<String> resourceConfig = instance.getResourceConfig(ResourceType.DATASTORE, DatastoreService.DATASTORE_USER_NAME);
        if (resourceConfig.isPresent()) {
            String userName = resourceConfig.get();
            connectorResponse.getAuthenticationConfig().setUserName(userName);
            connectorResponse.getAuthConfig().put("userName", userName);
            String password = encrypService.decrypt(instance.getResourceConfig(ResourceType.DATASTORE, DatastoreService.DATASTORE_PASSWORD).get());
            connectorResponse.getAuthenticationConfig().setPassword(password);
            connectorResponse.getAuthConfig().put("password", password);
            if (!connectorResponse.getMetaConfig().containsKey("port")) {
                connectorResponse.getMetaConfig().put("port", appConfig.getDatastorePort());
            }
        }
        return connectorResponse;
    }

    private void validateUpdates(Connector existing, Connector updated) {
        var updatedMetaConfig = updated.getMetaConfig();
        var existingMetaConfig = existing.getMetaConfig();
        updatedMetaConfig.forEach((k, v) -> {
            if(!Objects.equals(v, existingMetaConfig.get(k))){
                throw new RuntimeException("Cannot update "+k);
            }
        });
    }

    private ConnectorResponse getSyncariDatastore() {
        Optional<Connector> syncariDatastore = connectorService.getSyncariDatastore();
        return syncariDatastore.map(d -> populateSyncariDatastore(d)).orElse(null);
    }

    private Connector checkStarsAndUpdateFields(Connector requestConnector, Optional<Connector> existingConnectorOpt){
        Connector result = new Connector(requestConnector.getName(), requestConnector.getMetadataId(), requestConnector.getEndpoint());
        existingConnectorOpt.ifPresent(existingConnector -> {
            AuthConfig existingAuthConfig = existingConnector.getAuthConfig();
            AuthConfig requestAuthConfig = requestConnector.getAuthConfig();
            if (requestConnector.getAuthConfig() != null) {
                if (StringUtils.isNotBlank(requestAuthConfig.getPassword()) && (!"*****".equals(requestAuthConfig.getPassword()))) {
                    existingAuthConfig.setPassword(requestAuthConfig.getPassword());
                }
                if (StringUtils.isNotBlank(requestAuthConfig.getClientSecret()) && (!"*****".equals(requestAuthConfig.getClientSecret()))) {
                    existingAuthConfig.setClientSecret(requestAuthConfig.getClientSecret());
                }
                if (StringUtils.isNotBlank(requestAuthConfig.getToken()) && (!"*****".equals(requestAuthConfig.getToken()))) {
                    existingAuthConfig.setToken(requestAuthConfig.getToken());
                }
                if (StringUtils.isNotBlank(requestAuthConfig.getAccessToken()) && (!"*****".equals(requestAuthConfig.getAccessToken()))) {
                    existingAuthConfig.setAccessToken(requestAuthConfig.getAccessToken());
                }
                if (StringUtils.isNotBlank(requestAuthConfig.getRefreshToken()) && (!"*****".equals(requestAuthConfig.getRefreshToken()))) {
                    existingAuthConfig.setRefreshToken(requestAuthConfig.getRefreshToken());
                }
                if (StringUtils.isNotBlank(requestAuthConfig.getClientId()) && (!"*****".equals(requestAuthConfig.getClientId()))) {
                    existingAuthConfig.setClientId(requestAuthConfig.getClientId());
                }
                if (StringUtils.isNotBlank(requestAuthConfig.getConsumerSecret()) && (!"*****".equals(requestAuthConfig.getConsumerSecret()))) {
                    existingAuthConfig.setConsumerSecret(requestAuthConfig.getConsumerSecret());
                }
                if (StringUtils.isNotBlank(requestAuthConfig.getConsumerKey()) && (!"*****".equals(requestAuthConfig.getConsumerKey()))) {
                    existingAuthConfig.setConsumerKey(requestAuthConfig.getConsumerKey());
                }
                if (StringUtils.isNotBlank(requestAuthConfig.getTokenSecret()) && (!"*****".equals(requestAuthConfig.getTokenSecret()))) {
                    existingAuthConfig.setTokenSecret(requestAuthConfig.getTokenSecret());
                }
                if (StringUtils.isNotBlank(requestAuthConfig.getTokenId()) && (!"*****".equals(requestAuthConfig.getTokenId()))) {
                    existingAuthConfig.setTokenId(requestAuthConfig.getTokenId());
                }

                if (requestAuthConfig.getAdditionalHeaders() != null) {
                    Map<String, String> updatedAdditionalHeaders = new HashMap<>();
                    Map<String, String> existingAdditionalHeader = existingAuthConfig.getAdditionalHeaders();
                    Map<String, String> requestAdditionalHeaders = requestAuthConfig.getAdditionalHeaders();
                    // Filter new headers or existing headers and set those accordingly
                    List<String> starAdditionalHeaders = requestAdditionalHeaders.keySet().stream().filter(key -> requestAdditionalHeaders.get(key) != null && "*****".equals(requestAdditionalHeaders.get(key))).collect(Collectors.toList());
                    List<String> newAdditionalHeaders = requestAdditionalHeaders.keySet().stream().filter(key -> requestAdditionalHeaders.get(key) != null && !"*****".equals(requestAdditionalHeaders.get(key))).collect(Collectors.toList());
                    starAdditionalHeaders.forEach(key -> {
                        updatedAdditionalHeaders.put(key, existingAdditionalHeader.get(key));
                    });
                    newAdditionalHeaders.forEach(key -> {
                        updatedAdditionalHeaders.put(key, requestAdditionalHeaders.get(key));
                    });
                    existingAuthConfig.setAdditionalHeaders(updatedAdditionalHeaders);
                }
            }
            existingAuthConfig.setUserName(requestAuthConfig.getUserName());
            existingAuthConfig.setEndpoint(requestAuthConfig.getEndpoint());
            existingAuthConfig.setExpiresIn(requestAuthConfig.getConsumerKey());
            existingAuthConfig.setRedirectUri(requestAuthConfig.getRedirectUri());
            existingAuthConfig.setLastRefreshed(requestAuthConfig.getLastRefreshed());
            result.setAuthConfig(existingAuthConfig);
            result.setMetaConfig(existingConnector.getMetaConfig());
            result.setDatastoreType(existingConnector.getDatastoreType());
        });
        result.setApiConfig(requestConnector.getApiConfig());
        result.setDailyQuota(requestConnector.getDailyQuota());
        result.setAuthType(requestConnector.getAuthType());
        result.setSetting(requestConnector.getSetting());
        result.setId(requestConnector.getId());
        return result;
    }

}
