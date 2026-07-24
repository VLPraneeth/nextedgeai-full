package com.syncari.core.service.messagingservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitfox.svg.A;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.DefaultAuthTokenHandler;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.OAuthRequest;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.connector.service.def.OauthAuthenticationService;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.model.Connector;
import com.syncari.core.service.ConnectorService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.syncari.connector.ConnectorHelper.withBackoffAndErrorHandling;
import static com.syncari.connector.DefaultAuthTokenHandler.*;
import static java.lang.String.format;

@Slf4j
@Component(Constants.MS_TEAMS)
public class MSTeamsService implements OauthAuthenticationService {

    private static final String OAUTH_HOST = "https://login.microsoftonline.com/common";
    private static final String OAUTH_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/token";
    private static final String API_ENDPOINT = "https://graph.microsoft.com/beta/teams/%s/channels";
    public static final String scopes = "offline_access https://graph.microsoft.com/ChannelMessage.Send https://graph.microsoft.com/Channel.Create";
    public static final String INIT_OAUTH_URL = "/oauth2/v2.0/authorize?" +
            "client_id={{client_id}}" +
            "&response_type=code" +
            "&redirect_uri={{redirect_uri}}" +
            "&response_mode=query" +
            "&scope=%s" +
            "&state={{state}}" +
            "&code_challenge={{code_challenge}}" +
            "&code_challenge_method=S256";


    @Autowired
    ConnectorService connectorService;

    @Autowired
    DefaultAuthTokenHandler tokenHandler;

    SyncariEntityDataRestClient syncariEntityDataRestClient = new SyncariEntityDataRestClient();

    ObjectMapper objectMapper = new ObjectMapper();

    public void sendMessage(String teamId, String channelId, String messageContent, String serviceId) {

        Optional<Connector> connectorOpt = connectorService.find(serviceId);
        if(connectorOpt.isEmpty()) {
            throw new NotFoundException(Connector.class, "Id", serviceId);
        }
        Connector connector = connectorOpt.get();

        if(StringUtils.isBlank(channelId)) {
            throw new RuntimeException("Teams channel name cannot be empty");
        }

        String url = String.format(API_ENDPOINT, teamId) + String.format("/%s/messages", channelId);
        Map<String, Object> payload = Map.of(
                "body",Map.of("content", messageContent)
        );

        ResponseEntity<String> response = post(url, payload, connector);
        if(!response.getStatusCode().is2xxSuccessful()) {
            log.error("Failed to send message to channel {}", channelId);
            throw new RuntimeException(response.getBody());
        }
    }

    public void createChannel(String teamId, String channelName, String serviceId) {
        Optional<Connector> connectorOpt = connectorService.find(serviceId);
        if(connectorOpt.isEmpty()) {
            throw new NotFoundException(Connector.class, "Id", serviceId);
        }
        Connector connector = connectorOpt.get();

        String url = String.format(API_ENDPOINT, teamId);
        Map<String, Object> payload = Map.of(
                "displayName", channelName,
                "membershipType", "standard"
        );

        ResponseEntity<String> response = post(url, payload, connector);
        if(!response.getStatusCode().is2xxSuccessful()) {
            log.error("Failed to create channel {}", channelName);
            throw new RuntimeException(response.getBody());
        }
    }

    private ResponseEntity<String> post(String url, Map<String, Object> payload, Connector connector) {
        try {
            return syncariEntityDataRestClient.postRaw(url, objectMapper.writeValueAsString(payload), connector.getAuthConfig());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        return new TestConnectionResponse("success", "", List.of());
    }

    @Override
    public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
        Map<String, String> map = Map.of(
                DefaultAuthTokenHandler.CLIENT_ID, oAuthRequest.getConfig().getClientId(),
                DefaultAuthTokenHandler.CLIENT_SECRET, oAuthRequest.getConfig().getClientSecret(),
                DefaultAuthTokenHandler.GRANT_TYPE, "authorization_code",
                DefaultAuthTokenHandler.CODE, oAuthRequest.getCode(),
                DefaultAuthTokenHandler.REDIRECT_URI, oAuthRequest.getRedirectUri(),
                DefaultAuthTokenHandler.CODE_VERIFIER, oAuthRequest.getConfig().getCodeVerifier());

        return tokenHandler.getAccessToken(OAUTH_URL, map, Map.of());
    }

    @Override
    public AuthConfig refreshToken(ConnectorInfo connector) {
        AuthConfig config = connector.getAuthConfig();
        Map<String, String> map = Map.of(DefaultAuthTokenHandler.GRANT_TYPE, REFRESH_TOKEN,
                REFRESH_TOKEN, config.getRefreshToken(),
                DefaultAuthTokenHandler.CLIENT_ID, config.getClientId(),
                DefaultAuthTokenHandler.CLIENT_SECRET, config.getClientSecret());

        return tokenHandler.refreshToken(config, OAUTH_URL, map);
    }

    @Override
    public String getOAuthUri(ConnectorInfo connector) {
        String scopes = "offline_access https://graph.microsoft.com/Chat.Create https://graph.microsoft.com/Channel.Create";
        return String.format("https://login.microsoftonline.com/common/oauth2/v2.0/authorize?\n" +
                "client_id={{client_id}}\n" +
                "&response_type=code\n" +
                "&redirect_uri={{redirect_uri}}\n" +
                "&response_mode=query\n" +
                "&scope=%s\n" +
                "&state={{state}}}}\n" +
                "&code_challenge={{code_challenge}}\n" +
                "&code_challenge_method=S256", connector.getMetaConfig().get("tenantId"), scopes);
    }

    @Override
    public String getCodeVerifier() {
        SecureRandom sr = new SecureRandom();
        byte[] code = new byte[32];
        sr.nextBytes(code);
        return Base64.encodeBase64URLSafeString(code);
    }

    @Override
    public String getCodeChallenge(String verifier) {
        byte[] bytes = new byte[0];
        try {
            bytes = verifier.getBytes("US-ASCII");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        md.update(bytes, 0, bytes.length);
        byte[] digest = md.digest();
        return Base64.encodeBase64URLSafeString(digest);
    }

    @Override
    public String getAuthHost(AuthConfig config) {
        return OAUTH_HOST;
    }
}
