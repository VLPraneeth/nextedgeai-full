package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.*;
import static com.syncari.core.service.OAuthService.SIMPLE_AUTHORIZE_CALLBACK;
import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.syncari.api.rest.controllers.data.FeatureDTO;
import com.syncari.connector.ConnectorType;
import com.syncari.connector.data.AuthType;
import com.syncari.core.model.Feature;
import com.syncari.core.model.misc.ConnectorStatus;
import com.syncari.core.service.*;
import com.syncari.core.service.messagingservice.MSTeamsService;
import com.syncari.core.service.messagingservice.SlackService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.syncari.api.core.util.ObjectTransformer;
import com.syncari.api.core.util.Util;
import com.syncari.api.rest.controllers.data.Credential;
import com.syncari.connector.Constants;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.core.Features;
import com.syncari.core.config.AppConfig;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Connector;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.core.model.ServiceCredential;
import com.syncari.core.model.misc.ServiceCredentialType;
import com.syncari.core.model.misc.ServiceType;
import com.syncari.core.service.authz.AuthzService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1")
public class ProvisioningController {
    @Autowired
    ProvisioningService provisioningService;
    @Autowired
    ServiceCredentialService serviceCredentialService;
    @Autowired
    FeatureService featureService;
    @Autowired
    ObjectTransformer transformer;
    @Autowired
    Util util;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    ConnectorMetadataService metadataService;
    @Autowired
    AppConfig appConfig;
    @Autowired
    OAuthService oAuthService;
    @Autowired
    AuthzService authzService;
    @Autowired
    CredentialsService credentialsService;

    @Autowired
    InsightsService insightsService;

    private static final List<String> connectorCreds = List.of(ServiceType.genericApiKey.name().toLowerCase(),
            ServiceType.genericBearerToken.name().toLowerCase(),
            ServiceType.genericSimpleOAuth.name().toLowerCase());

    private static final Map<ServiceType, String> connectorMap = Map.of(
            ServiceType.Salesintel, Constants.SALESINTEL,
            ServiceType.Apexanalytix, Constants.APEX_ANALYTIX,
            ServiceType.Aidentified, Constants.AIDENTIFIED
    );

    @Secured(LIST_INSTANCE)
    @RequestMapping(method = RequestMethod.GET, value = "/role")
    public List<String> listRoles() {
        return authzService.listRoles().stream().map(r -> r.getName()).collect(Collectors.toList());
    }

    @Secured(ENABLE_FEATURE)
    @RequestMapping(method = RequestMethod.POST, value = "/instance/feature/{featureName}/enable")
    public FeatureDTO enableFeature(@PathVariable String featureName) {
        Feature feature = featureService.enableFeature(Features.valueOf(featureName));
        return transformer.toFeatureDTO(feature);
    }

    @Secured(ENABLE_INSIGHTS)
    @RequestMapping(method = RequestMethod.POST, value = "/instance/insights/enable")
    public FeatureDTO enableFeature() {
        Feature feature = featureService.enableFeature(Features.valueOf(Features.Insights.name()));
        return transformer.toFeatureDTO(feature);
    }

    @Secured(DISABLE_FEATURE)
    @RequestMapping(method = RequestMethod.POST, value = "/instance/feature/{featureName}/disable")
    public FeatureDTO disableFeature(@PathVariable String featureName) {
        featureService.disableFeature(Features.valueOf(featureName));
        return transformer.toFeatureDTO(featureService.getFeatureByName(Features.valueOf(featureName)));
    }

    @Secured(GET_FEATURE_STATUS)
    @RequestMapping(method = RequestMethod.GET, value = "/instance/feature/{featureName}")
    public FeatureDTO getFeature(@PathVariable String featureName) {
        Feature f = featureService.getOrCreateFeatureByName(Features.valueOf(featureName));
        return transformer.toFeatureDTO(f);
    }

    @Secured(GET_FEATURE_STATUS)
    @RequestMapping(method = RequestMethod.GET, value = "/instance/features")
    public List<FeatureDTO> getFeatures() {
        List<Feature>  f = featureService.getAllFeatures();
        return f.stream().map(feature -> transformer.toFeatureDTO(feature)).collect(Collectors.toList());
    }

    @Secured(SERVICE_CREDENTIAL)
    @RequestMapping(method = RequestMethod.POST, value = "/service/credential")
    public String upsertServiceCredential(@RequestBody Credential request) {
        ServiceCredential credential = new ServiceCredential();
        boolean testConnection = false;
        Connector connector = null;

        Boolean isConnector;
        try {
            credential.setServiceType(getType(request.getType()));
            isConnector = credential.getServiceType().isConnector();
        } catch (Exception e) {
            throw new SyncariValidationException(i18n("invalid_credential_type"));
        }

        if (isConnector) {
            if (credential.serviceType == ServiceType.Slack) {
                connector = new Connector(
                        request.getName(),
                        connectorService.describe(Constants.SLACK).getId(),
                        SlackService.END_POINT
                );
                connector.setId(request.getId());
                connector.setAuthConfig(new AuthConfig(appConfig.getSlackClientId(), appConfig.getSlackClientSecret()));
                connectorService.save(connector);
                connector = connectorService.find(connector.getId()).get();

                //  TODO remove this once the slack app is approved
                connector.setOAuthRedirectUrl(oAuthService.generateSlackCallbackUrl());
                connector = connectorService.save(connector);
                String redirectUrl = String.format(
                        SlackService.INIT_OAUTH_URL,
                        appConfig.getSlackClientId(),
                        connector.getOAuthRedirectUrl()
                );
                log.info(redirectUrl);
                return redirectUrl;
            } if (credential.serviceType == ServiceType.Msteams) {
                connector = new Connector();
                connector.setName(request.getName());
                connector.setMetadataId(connectorService.describe(Constants.MS_TEAMS).getId());
                connector.setId(request.getId());
                connector.setAuthConfig(new AuthConfig(appConfig.getMsTeamsClientId(), appConfig.getMsTeamsClientSecret()));
                connector = connectorService.save(connector);
                connector.getMetadata().setOAuthUri(String.format(MSTeamsService.INIT_OAUTH_URL, MSTeamsService.scopes));
                connector.setOAuthRedirectUrl(format(SIMPLE_AUTHORIZE_CALLBACK, appConfig.getSpectrumServerHost()));
                connector = connectorService.save(connector);
                String redirectURL = oAuthService.initiate(connector.getId());
                return redirectURL;
            } else if (credential.serviceType == ServiceType.Zoominfo) {
                if (StringUtils.isBlank(request.getUsername()) || StringUtils.isBlank(request.getPassword())) {
                    throw new RuntimeException(i18n("missing_zoominfo_credentials_create"));
                }

                ConnectorMetadata metadata = connectorService.describe(Constants.ZOOMINFO);

                connector = new Connector(
                        request.getName(),
                        metadata,
                        null,
                        request.getUsername(),
                        request.getPassword()
                );

                connector.setId(request.getId());

                connector = connectorService.save(connector);

                var testConnResp = connectorService.testConnection(connector.getId());
                if (!testConnResp.isSuccess()) {
                    throw new RuntimeException(i18n("invalid_zoominfo_credentials_create"));
                }
                return null;
            } else if (credential.serviceType == ServiceType.Similarweb) {
                String apiKey = request.getKey();

                if (StringUtils.isBlank(apiKey)) {
                    throw new RuntimeException(i18n("missing_similarweb_credentials_create"));
                }

                AuthConfig authConfig = new AuthConfig().setToken(apiKey);
                connector = new Connector(
                        request.getName(),
                        connectorService.describe(Constants.SIMILAR_WEB),
                        null,
                        null,
                        null
                );

                connector.setId(request.getId());
                connector.setAuthConfig(authConfig);

                connector = connectorService.save(connector);

                try {
                    connectorService.testConnection(connector.getId());
                } catch (Exception e) {
                    throw new SyncariValidationException(i18n("invalid_similarweb_credentials_create"));
                }
                return null;
            } else if (credential.serviceType == ServiceType.Insideview) {
                if (StringUtils.isBlank(request.getClientId()) || StringUtils.isBlank(request.getClientSecret())) {
                    throw new RuntimeException(i18n("missing_insideview_credentials_create"));
                }
                connector = new Connector(
                        request.getName(),
                        connectorService.describe(Constants.INSIDEVIEW),
                        null,
                        request.getClientId(),
                        request.getClientSecret(),
                        true
                );
                connector.setId(request.getId());
                connector = connectorService.save(connector);
                try {
                    connectorService.ObtainAccessToken(connector);
                } catch (Exception e) {
                    throw new RuntimeException(i18n("invalid_insideview_credentials_create"));
                }
                var testConnResp = connectorService.testConnection(connector.getId());
                if (!testConnResp.isSuccess()) {
                    throw new RuntimeException(i18n("invalid_insideview_credentials_create"));
                }
                return null;
            } else if (credential.serviceType == ServiceType.genericSimpleOAuth){
                connector = new Connector(
                        request.getName(),
                        connectorService.describe(Constants.GENERIC_SIMPLE_OAUTH),
                        request.getEndPoint(),
                        request.getClientId(),
                        request.getClientSecret(),
                        true
                );
                connector.setId(request.getId());
                connector = connectorService.save(connector);
                testConnection = true;
            } else if (credential.serviceType == ServiceType.genericApiKey){
                connector = new Connector(
                        request.getName(),
                        connectorService.describe(Constants.GENERIC_API_KEY),
                        null,
                        null, null);
                connector.setId(request.getId());
                connector.setAuthType(AuthType.ApiKey);
                connector.setAuthConfig(new AuthConfig().setToken(request.getKey()));
                connector = connectorService.save(connector);
                testConnection = true;
            }  else if (credential.serviceType == ServiceType.genericBearerToken){
                connector = new Connector(
                        request.getName(),
                        connectorService.describe(Constants.GENERIC_BEARER_TOKEN),
                        null,
                        null, null);
                connector.setId(request.getId());
                connector.setAuthType(AuthType.BearerToken);
                connector.setAuthConfig(new AuthConfig().setAccessToken(request.getKey()));
                connector = connectorService.save(connector);
                testConnection = true;
            } else if (connectorMap.containsKey(credential.serviceType)){
                createConnector(request, connectorMap.get(credential.serviceType));
                return null;
            }
        }  else {
            credential.setId(request.getId());
            credential.setApiKey(request.getKey());
            credential.setName(request.getName());

            serviceCredentialService.addServiceCredential(credential);
            return null;
        }
        if (testConnection && (null != connector)){
            var response = credentialsService.test(connector);
            if (response.isSuccess()) {
                AuthConfig updatedConfig = response.getAuthConfig();
                if(updatedConfig != null) {
                    updatedConfig.setLastRefreshed(Instant.now());
                    connector.setAuthConfig(updatedConfig);
                    connector.setStatus(ConnectorStatus.ACTIVE);
                    connectorService.findAndSave(connectorService.encrypt(connector));
                }
            } else {
                // cleanup
                connectorService.delete(connector.getId(), true);
                throw new SyncariValidationException("Invalid Auth Credential " + connector.getName());
            }
        }
        return null;
    }
    @Secured(SERVICE_CREDENTIAL)
    @RequestMapping(method = RequestMethod.DELETE, value = "/service/credential/{credentialId}")
    public void deleteServiceCredential(@PathVariable String credentialId) {
        provisioningService
                .getCredentials(credentialId)
                .ifPresentOrElse(
                        cred -> {
                            serviceCredentialService.delete(cred.getId());
                        },
                        () -> {
                            connectorService.delete(credentialId, true);
                        }
                );

        log.info(String.format("Credential: %s has been hard deleted.", credentialId));
    }

    @Secured(SERVICE_CREDENTIAL)
    @RequestMapping(method = RequestMethod.GET, value = "/service/credential")
    public List<Credential> getCredentials() {
        List<ServiceCredential> credentials = provisioningService.getCredentials();
        Map<String, ConnectorMetadata> metadataMap = metadataService.findByType(ConnectorType.Enrich.name()).stream().collect(Collectors.toMap(ConnectorMetadata::getId, Function.identity()));

        connectorService.listEnrichment().stream().forEach(c -> {
            if(metadataMap.containsKey(c.getMetadataId())) {
                ServiceCredential cred = new ServiceCredential();
                cred.setName(c.getName());
                cred.setServiceType(getType(metadataMap.get(c.getMetadataId()).getName()));
                cred.setCredentialType(ServiceCredentialType.ENRICH);
                cred.setId(c.getId());
                cred.setUsername(c.getAuthConfig().getUserName());
                cred.setPassword(c.getAuthConfig().getPassword());
                cred.setEndPoint(c.getEndpoint());
                cred.setApiKey(c.getAuthConfig().getToken());
                cred.setClientId(c.getAuthConfig().getClientId());
                cred.setClientSecret(c.getAuthConfig().getClientSecret());
                credentials.add(cred);
            }
        });

        collect(ConnectorType.Service, ServiceCredentialType.SERVICE, credentials);
        collect(ConnectorType.Credential, ServiceCredentialType.SERVICE, credentials);
        return transformer.toCred(credentials);
    }

    private void collect(ConnectorType type, ServiceCredentialType credentialType, List<ServiceCredential> credentials) {
        Map<String, ConnectorMetadata> serviceMetadataMap = metadataService.findByType(type.name()).stream().collect(Collectors.toMap(ConnectorMetadata::getId, Function.identity()));
        List<Connector> connectors = connectorService.listCredential();
        if(type == ConnectorType.Service) connectors = connectorService.listService();
        connectors.stream().forEach(c -> {
            if(serviceMetadataMap.containsKey(c.getMetadataId())) {
                ServiceCredential cred = new ServiceCredential();
                cred.setName(c.getName());
                cred.setServiceType(getType(serviceMetadataMap.get(c.getMetadataId()).getName()));
                cred.setCredentialType(credentialType);
                cred.setEndPoint(c.getEndpoint());
                cred.setId(c.getId());
                cred.setUsername(c.getAuthConfig().getUserName());
                cred.setPassword(c.getAuthConfig().getPassword());
                if(cred.getServiceType() == ServiceType.genericApiKey) {
                    cred.setApiKey(c.getAuthConfig().getToken());
                } else {
                    cred.setApiKey(c.getAuthConfig().getAccessToken());
                }
                cred.setClientId(c.getAuthConfig().getClientId());
                cred.setClientSecret(c.getAuthConfig().getClientSecret());
                credentials.add(cred);
            }
        });
    }

    private void createConnector(Credential request, String name) {
        String apiKey = request.getKey();

        if (StringUtils.isBlank(apiKey)) {
            throw new RuntimeException(i18n("missing_credentials_create"));
        }

        AuthConfig authConfig = new AuthConfig().setToken(apiKey);
        Connector connector = new Connector(
                request.getName(),
                connectorService.describe(name),
                null,
                null,
                null
        );
        connector.setId(request.getId());
        connector.setAuthConfig(authConfig);
        connector = connectorService.save(connector);
        TestConnectionResponse response =  connectorService.testConnection(connector.getId());
        if (!response.isSuccess()){
            log.warn(i18n("invalid_credentials_create"));
            connectorService.delete(connector.getId(), true);
            throw new SyncariValidationException(i18n("invalid_credentials_create"));
        }
    }

    private ServiceType getType(String serviceType) {
        // TODO, there is inconsistency in casing between cconnector and service credential
        return ServiceType.valueOf(connectorCreds.contains(serviceType.toLowerCase()) ? serviceType : StringUtils.capitalize(serviceType.toLowerCase()));
    }
}