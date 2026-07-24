package com.syncari.core.service;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.syncari.connector.custom.CloudFunctionInfo;
import com.syncari.connector.data.AuthType;
import com.syncari.core.DataTransformer;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.connector.Constants;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.OAuthRequest;
import com.syncari.connector.service.def.OauthAuthenticationService;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.model.Connector;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.core.model.Organization;
import com.syncari.core.model.security.OAuthConfig;
import com.syncari.core.repositories.syncari.OrganizationRepo;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OAuthService {
    public static final String SIMPLE_AUTHORIZE_CALLBACK = "%s/oauth/authorize";
	private static final String AUTHORIZE_CALLBACK = "%s/oauth/authorize?state=%s";
    private static final String AUTHORIZE_GUID_CALLBACK = "%s/oauth/authorize?guid=%s";
    private static final String ONECLICK_AUTH_CALLBACK = "%s/oauth/authorize?%s=%s";
	@Autowired
	DataServiceFactory factory;
	@Autowired
	OrganizationRepo orgRepo;
	@Autowired
	EncryptionService encryptionService;
	@Autowired
	AppConfig appConfig;
	@Autowired
	ConnectorService connectorService;
    @Autowired
    ConnectorMetadataService connectorMetadataService;
	@Autowired
	DataServiceFactory dataServiceFactory;
    @Autowired
    DataTransformer transformer;

	public String initiate(String connectorId) {
		Connector connector = connectorService.get(connectorId);
		if (StringUtils.isBlank(connector.getOAuthRedirectUrl())) {
			throw new RuntimeException("Connector redirect url is empty");
		}
		String url = connector.getMetadata().getOAuthUri();
		log.info(format("Got oauth url from metadata : %s", url));
        ConnectorMetadata connectorMetadata = connectorService.getOrFindConnectorMetadata(connector);
        CloudFunctionInfo cfInfo = null;
        if (connector.getMetadata() != null) {
            if (connector.getMetadata().isCustom()) {
                cfInfo = connectorMetadataService.getCloudFunctionInfo(connector.getMetadata());
            }
        }
		try {
            String redirectURL = connector.getOAuthRedirectUrl();
            // For new one-click auth mechanisms force use new method. This will take care of existing connectors to migrate to new way.
            if (isOneClickOAuthConnector(connector)) {
                redirectURL = generateCallbackUrl(connector);
            }
            if(!connector.getMetadata().getOAuthUri().contains("{{redirect_uri}}") && connector.getMetadata().isCustom()) {
                url = url + "&redirect_uri=" + URLEncoder.encode(redirectURL, StandardCharsets.UTF_8.toString());
            } else {
                url = url.replace("{{redirect_uri}}", URLEncoder.encode(redirectURL, StandardCharsets.UTF_8.toString()));
            }
            log.info(format("Url with redirect %s", url));
        } catch (UnsupportedEncodingException e) {
            log.error(ExceptionUtils.getStackTrace(e));
            throw new RuntimeException(e);
        }
        if(connector.getAuthConfig().getCodeChallenge() != null) {
            url = url.replace("{{code_challenge}}", connector.getAuthConfig().getCodeChallenge());
        }
        if (isOneClickOAuthConnector(connector)) {
            url = url.replace("{{client_id}}", getOneClickOAuthConfig(connectorMetadata).getClientId());
        } else {
            String clientId = connector.getAuthConfig().getClientId();
            if(clientId.contains("=")) {
                try {
                    clientId = URLEncoder.encode(clientId, StandardCharsets.UTF_8.toString());
                } catch (UnsupportedEncodingException e) {
                    log.error(ExceptionUtils.getStackTrace(e));
                    throw new RuntimeException(e);
                }
            }
            url = url.replace("{{client_id}}", clientId);
        }
		
        // TODO: generalize with connector callback and move to individual connectors.
        if (url.contains("{{state}}")) {
            url = url.replace("{{state}}", connector.getId());
        }

		OauthAuthenticationService service = dataServiceFactory.getOauthAuthenticationService(connector.getMetadata());
		return format("%s%s", service.getAuthHost(connector.getAuthConfig(), cfInfo), url).replace(" ", "%20");
	}

    public void authorizeWithConnectorId(String connectorId, String code) {
        if(StringUtils.isBlank(code)) throw new RuntimeException(i18n("blank_oauth_code"));
        log.info(String.format("Instance name %s", SyncariContext.getSyncariId()));
        Optional<Connector> connectorOptional = connectorService.find(connectorId);
        Connector connector;
        if(connectorOptional.isPresent()) {
            connector = connectorOptional.get();
            if (isOneClickOAuthConnector(connector)) {
                // For old way of authentication, the redirect URL will still be old one, we need to refresh before using the one-click auth.
                connector.setOAuthRedirectUrl(generateCallbackUrl(connector));
                connector.getAuthConfig().setClientId(getOneClickOAuthConfig(connector.getMetadata()).getClientId());
                connector.getAuthConfig().setClientSecret(getOneClickOAuthConfig(connector.getMetadata()).getClientSecret());
            }
        } else {
            connector = connectorService.findByOauthState(connectorId).orElseThrow(() -> new RuntimeException(format(i18n("connector_not_found"), connectorId)));
        }
        authorizeInternal(connector, code);
    }

	public void authorize(String guid, String code) {
        if(StringUtils.isBlank(code)) throw new RuntimeException(i18n("blank_oauth_code"));
        log.info(String.format("Instance name %s", SyncariContext.getSyncariId()));
        Connector connector = connectorService.findByOauthGuid(guid).orElseThrow(() -> new RuntimeException(format(i18n("connector_not_found"), guid)));
        authorizeInternal(connector, code);
	}

    public void authorizeWithoutCode(Connector connector) {
        authorizeInternal(connector, "");
    }

    public void authorizeInternal(Connector connector, String code) {
        OauthAuthenticationService service = factory.getOauthAuthenticationService(connector.getMetadata());
        CloudFunctionInfo cfInfo = null;
        if (connector.getMetadata() != null) {
            if (connector.getMetadata().isCustom()) {
                cfInfo = connectorMetadataService.getCloudFunctionInfo(connector.getMetadata());
            }
        }
        OAuthRequest oAuthRequest = new OAuthRequest(code,
                service.getAuthHost(connector.getAuthConfig(), cfInfo),
                connector.getOAuthRedirectUrl(), connector.getAuthConfig(), connector.getMetaConfig(), cfInfo);

        AuthConfig oAuthConfig = service.getAccessToken(oAuthRequest);
        connector.getAuthConfig().setAccessToken(oAuthConfig.getAccessToken());
        if(oAuthConfig.getRefreshToken() != null) {
            connector.getAuthConfig().setRefreshToken(oAuthConfig.getRefreshToken());
            connector.getAuthConfig().setLastRefreshed(Instant.now());
        }
        connector.getAuthConfig().setExpiresIn(oAuthConfig.getExpiresIn());
        connectorService.save(connector);
        connectorService.authenticated(connector.getId());
        log.info(String.format("Oauth completed successfully for %s", connector.getName()));
	}

	public String generateCallbackUrl(Connector connector) {
		if (isOneClickOAuthConnector(connector)) {
            ConnectorMetadata connectorMetadata = connector.getMetadata();
            if (connectorMetadata == null) {
                connectorMetadata = connectorService.describeById(connector.getMetadataId());
            }
            return format(ONECLICK_AUTH_CALLBACK, appConfig.getSpectrumServerHost(), 
                getOneClickOAuthConfig(connectorMetadata).getSyncariRedirectIdentifier(),
                getOneClickOAuthConfig(connectorMetadata).getClientId());
        }
        String oauthCallback = AUTHORIZE_CALLBACK;
        if(connector.getMetadata() != null && connector.getMetadata().getOAuthUri() != null &&
                connector.getMetadata().getOAuthUri().contains("{{state}}")) {
            if(!connector.getMetadata().getOAuthUri().contains("{{redirect_uri}}") && connector.getMetadata().isCustom()) {
                return format(SIMPLE_AUTHORIZE_CALLBACK, appConfig.getSpectrumServerHost());
            }
            oauthCallback = AUTHORIZE_GUID_CALLBACK;
        }
        return format(oauthCallback, appConfig.getSpectrumServerHost(), UUID.randomUUID().toString());
	}
	
	@Deprecated
	//  TODO remove this once the slack app is approved
	public String generateSlackCallbackUrl() {
	    return format(AUTHORIZE_GUID_CALLBACK, appConfig.getSpectrumServerHost()+"/arcade/api/v1", UUID.randomUUID().toString());
	}

    public boolean isOneClickOAuthConnector(Connector connector) {
        return isSpecificOneClickAuthType(connector) && connector.getMetadata().isOneClickOauthConnector();
    }

    public boolean isSpecificOneClickAuthType(Connector connector) {
        // Currently, the AuthType.OneClickOAuth is supported only for googlesheets.
        if (connector.getMetadata() != null && !connector.getMetadata().getName().equalsIgnoreCase(Constants.GOOGLESHEETS)) return true;
        return connector.getMetaConfig() != null && connector.getMetaConfig().containsKey("authType") &&
            connector.getMetaConfig().get("authType").toString().equalsIgnoreCase(AuthType.OneClickOAuth.toString());
    }

    public OAuthConfig getOneClickOAuthConfig(ConnectorMetadata connectorMetadata) {
        Organization org = SyncariContext.getOrganziation();
        if (org.isPartner() && org.getOauthConfigs() != null && !org.getOauthConfigs().isEmpty()) {
            if (org.getOauthConfigs().containsKey(connectorMetadata.getName())) {
                OAuthConfig oAuthConfig = org.getOauthConfigs().get(connectorMetadata.getName()).copy();
                oAuthConfig.setClientId(encryptionService.decrypt(oAuthConfig.getClientId()));
                oAuthConfig.setClientSecret(encryptionService.decrypt(oAuthConfig.getClientSecret()));
                return oAuthConfig;
            }
        }
        if (Constants.HUBSPOT.equalsIgnoreCase(connectorMetadata.getName())) {
            return new OAuthConfig(Constants.HUBSPOT, appConfig.hubspotClientId, appConfig.hubspotClientSecret);
        }
        if (Constants.SLACK_SYNAPSE.equalsIgnoreCase(connectorMetadata.getName())) {
            return new OAuthConfig(Constants.SLACK_SYNAPSE, appConfig.getSlackClientId(), appConfig.getSlackClientSecret());
        }
        if (Constants.GOOGLESHEETS.equalsIgnoreCase(connectorMetadata.getName())) {
            // Google sheets does not like client_id as a param in the redirect_url.
            return new OAuthConfig(Constants.GOOGLESHEETS, appConfig.gsuiteClientId, appConfig.gsuiteClientSecret, "clientid");
        }
        return new OAuthConfig("", "", "");
    }

}
