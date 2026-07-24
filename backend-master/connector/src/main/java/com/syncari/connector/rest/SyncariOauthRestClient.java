package com.syncari.connector.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
public class SyncariOauthRestClient extends SyncariEntityDataRestClient{

    public SyncariOauthRestClient(JsonParserConfig singleJsonConfig, ObjectMapper mapper) {
        super(singleJsonConfig, mapper);
    }

    public ResponseEntity<String> getOauthResponse(String url, ConnectorInfo connector, Supplier<AuthConfig> tokenHandler, List<ErrorCodes> retryErrorCodes) {
        try {
            return getResponse(getHeaders(connector.getAuthConfig()), url, connector.getAuthConfig());
        } catch (NonRetriableException e){
            if(refreshAccessToken(e, connector, tokenHandler, retryErrorCodes)) {
                return getResponse(getHeaders(connector.getAuthConfig()), url, connector.getAuthConfig());
            }
            throw e;
        }
    }

    public ResponseEntity<String> postRaw(String url, String body, ConnectorInfo connector, Supplier<AuthConfig> tokenHandler, List<ErrorCodes> retryErrorCodes) {
        try {
            return postRaw(url, body, connector.getAuthConfig());
        } catch (NonRetriableException e){
            if(refreshAccessToken(e, connector, tokenHandler, retryErrorCodes)) {
                return postRaw(url, body, connector.getAuthConfig());
            }
            throw e;
        }
    }

    public EntityData post(String url, Map<String, Object> body, ConnectorInfo connector, Supplier<AuthConfig> tokenHandler, List<ErrorCodes> retryErrorCodes) {
        try {
            return post(url, body, connector.getAuthConfig());
        } catch (NonRetriableException e){
            if(refreshAccessToken(e, connector, tokenHandler, retryErrorCodes)) {
                return post(url, body, connector.getAuthConfig());
            }
            throw e;
        }
    }

    public EntityData post(String url, String body, ConnectorInfo connector, Supplier<AuthConfig> tokenHandler, List<ErrorCodes> retryErrorCodes) {
        try {
            return post(url, body, connector.getAuthConfig());
        } catch (NonRetriableException e){
            if(refreshAccessToken(e, connector, tokenHandler, retryErrorCodes)) {
                return post(url, body, connector.getAuthConfig());
            }
            throw e;
        }
    }

    public ResponseEntity<String> delete(String url, ConnectorInfo connector, Supplier<AuthConfig> tokenHandler, List<ErrorCodes> retryErrorCodes) {
        try {
            return delete(getHeaders(connector.getAuthConfig()), url, connector.getAuthConfig());
        }catch (NonRetriableException e){
            if(refreshAccessToken(e, connector, tokenHandler, retryErrorCodes)) {
                return delete(getHeaders(connector.getAuthConfig()), url, connector.getAuthConfig());
            }
            throw e;
        }
    }

    private boolean refreshAccessToken(NonRetriableException nre, ConnectorInfo connector, Supplier<AuthConfig> tokenHandler, List<ErrorCodes> retryErrorCodes){

        try {
            if (tokenHandler != null && retryErrorCodes.stream().anyMatch(a -> a.name().equals(nre.getErrorCode()))) {
                AuthConfig updatedAuth = tokenHandler.get();
                connector.setAuthConfig(updatedAuth);
                return true;
            }
        } catch (Exception e){
            log.error(String.format("SyncariOauthRestClient::refreshAccessToken failed with error", e.getMessage()), e);
            // throw the original exception back
            throw nre;
        }

        return false;
    }
}
