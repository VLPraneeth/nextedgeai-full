package com.syncari.core.service.messagingservice;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.OAuthRequest;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.rest.RestClient;
import com.syncari.connector.service.def.OauthAuthenticationService;
import com.syncari.core.config.AppConfig;
import com.syncari.core.model.Connector;
import com.syncari.core.repositories.customer.ServiceCredentialRepo;
import com.syncari.core.service.ConnectorService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;

@Slf4j
@Component(Constants.SLACK)
public class SlackService implements OauthAuthenticationService {
    @Autowired
    AppConfig appConfig;
    @Autowired
    RestClient restClient;
    ObjectMapper mapper = new ObjectMapper();
    @Autowired
    ServiceCredentialRepo credRepo;
    @Autowired
    ConnectorService connectorService;
    public static final String END_POINT = "https://slack.com";
    public static final String INIT_OAUTH_URL =  "https://slack.com/oauth/v2/authorize?client_id=%s&scope=chat:write&redirect_uri=%s";
    private static final String SEND_MSG_URL = "https://slack.com/api/chat.postMessage";
    private static final String GET_ACCESS_TOKEN_URL = "https://slack.com/api/oauth.v2.access";

    public void sendMessage(String block, String thread, String message, String channel, String serviceId) {
        Connector connector = connectorService.get(serviceId);
        if(StringUtils.isBlank(channel)) {
            throw new RuntimeException("Slack channel name cannot be empty");
        }
        if(channel.startsWith("#")) {
            channel = channel.replaceFirst("#", "");
        }

        String newLine = System.getProperty("line.separator");
        SlackMessage msg = new SlackMessage(message.replaceAll("\\\\n", newLine), channel, block, thread);
        ResponseEntity<String> responseEntity;
        try {
            mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
            responseEntity = restClient.getTemplate().exchange(SEND_MSG_URL, HttpMethod.POST,
                    new HttpEntity(mapper.writeValueAsString(msg),
                            restClient.getHeaders(connector.getAuthConfig().getAccessToken())),
                    String.class);
            // TODO parse the response to check 
            // {"ok" :  false, "error_message" : ""}
            if (responseEntity.getStatusCode() != HttpStatus.OK) {
                log.error("Error while sending slack message");
                log.error(responseEntity.getStatusCodeValue() + " " + responseEntity.getStatusCode().name());
                log.error(responseEntity.getBody());
            }
            Map<String, Object> respBody = mapper.readValue(responseEntity.getBody(), Map.class);
            if (respBody.containsKey("ok") && "false".equalsIgnoreCase(respBody.get("ok").toString())) {
                String errMsg = String.format("Failed to send slack message. Received error response: %s", responseEntity.getBody()); 
                log.error(errMsg);
                throw new IOException(errMsg);
            }
        } catch (RestClientException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getOAuthUri(ConnectorInfo connector) {
        return "";
    }
    
    @Override
    public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            MultiValueMap<String, String> map = new LinkedMultiValueMap<String, String>();
            map.add("code", oAuthRequest.getCode());
            map.add("client_id", oAuthRequest.getConfig().getClientId());
            map.add("client_secret", oAuthRequest.getConfig().getClientSecret());
            map.add("redirect_uri", oAuthRequest.getRedirectUri());
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<MultiValueMap<String, String>>(map,
                    headers);
            ResponseEntity<String> response = restClient.getTemplate().postForEntity(GET_ACCESS_TOKEN_URL, request,
                    String.class);

            log.info(format("Got response code %s", response.getStatusCode()));
            Map<String, String> responseValues = mapper.readValue(response.getBody(), Map.class);
            if (response.getStatusCode() != HttpStatus.OK || !responseValues.containsKey("access_token")) {
                String msg = format("Error in getAccessToken: code: %s, details:%s", response.getStatusCode(),
                        response.getBody());
                log.error(msg);
                throw new NonRetriableException(ErrorCodes.UNKNOWN_ERROR.name(), msg,
                        String.valueOf(response.getStatusCode()));
            }
            AuthConfig token = new AuthConfig();
            token.setAccessToken(responseValues.get("access_token").toString());
            log.info(format("Successfully returned access token for slack %s", oAuthRequest.getConfig().getClientId()));
            return token;
        } catch (Exception e) {
            log.error(format("Error in getAccessToken: %s", e.getMessage()));
            throw new RuntimeException(e);
        }
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public AuthConfig refreshToken(ConnectorInfo connector) {
        throw new RuntimeException("Refresh token not supported by slack");
    }

}

@Data
class SlackMessage {
    String channel;
    String text;
    String blocks;
    String thread_ts;

    public SlackMessage(String message, String channel, String blocks, String thread) {
        this.text = message;
        this.channel = channel;
        this.blocks = blocks;
        this.thread_ts = thread;
    }
}
