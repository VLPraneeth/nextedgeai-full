package com.syncari.connector;

import static com.syncari.connector.ConnectorHelper.withBackoffAndErrorHandling;
import static java.lang.String.format;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.RetriableException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DefaultAuthTokenHandler {
    public static final String EXPIRES_IN = "expires_in";
    public static final String EXPIRESIN = "expiresIn";
    public static final String REFRESH_TOKEN_EXPIRES_IN = "refresh_token_expires_in";
    public static final String ACCESS_TOKEN = "access_token";
    public static final String ACCESSTOKEN = "accessToken";
    public static final String REFRESHTOKEN = "refreshToken";
    public static final String REFRESH_TOKEN = "refresh_token";
    public static final String CLIENT_CREDENTIALS = "client_credentials";
    public static final String CLIENT_ID = "client_id";
    public static final String CLIENT_SECRET = "client_secret";
    public static final String RESOURCE = "resource";
    public static final String REDIRECT_URI = "redirect_uri";
    public static final String GRANT_TYPE = "grant_type";
    public static final String CODE = "code";
    public static final String CODE_VERIFIER = "code_verifier";
    public static final String CLIENTID = "clientId";
    public static final String CLIENTSECRET = "clientSecret";
    protected static int timeout = 5000;

    @Autowired
    ObjectMapper mapper;

    public AuthConfig getAccessToken(String endpoint, Map<String, String> map) {
        try {
            AuthConfig token = getToken(endpoint, map, Map.of());
            log.info("Successfully returned access token for {}", endpoint);
            return token;
        } catch (Exception e) {
            String message = format("Error in getAccessToken: %s", e.getMessage());
            log.error(message);
            throw new RuntimeException(message, e);
        }
    }

    public AuthConfig getAccessToken(String endpoint, Map<String, String> dataMap, Map<String, String> headersMap) {
        try {
            AuthConfig token = getToken(endpoint, dataMap, headersMap);
            log.info("Successfully returned access token for {}", endpoint);
            return token;
        } catch (Exception e) {
            log.error(format("Error in getAccessToken: %s", e.getMessage()));
            throw new RuntimeException(e);
        }
    }

    private AuthConfig getToken(String endpoint, Map<String, String> map, Map<String, String> headersMap)
            throws IOException, InterruptedException, JsonParseException, JsonMappingException,Exception {

        HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
        clientHttpRequestFactory.setConnectTimeout(timeout);
        RestTemplate restTemplate = new RestTemplate(clientHttpRequestFactory);
        HttpHeaders headers = new HttpHeaders();
        if(headersMap!= null) {
            headersMap.forEach((k, v) -> {
                headers.add(k, v);
            });
        }

        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        MultiValueMap<String, String> dataMap= new LinkedMultiValueMap<>();
        map.forEach((k,v) -> {
            dataMap.add(k, v);
        });
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity(dataMap, headers);
        Exception capturedException = new Exception();
        ResponseEntity<String> response = null;
        try{
            response = withBackoffAndErrorHandling(() -> restTemplate.postForEntity(endpoint, request, String.class));
        }catch (Exception e){
            log.info("Request for this error is {}", request);
            log.error("Eating excepting and capturing it, we need to try different keys, exception is",e);
            capturedException = e;
        }
        try{
            if (null == response){
                log.info("Could not authenticate using last keys and content type, trying client creds keys without underscore");
                response = withBackoffAndErrorHandling(() -> {
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, String> newMap = Map.of(DefaultAuthTokenHandler.GRANT_TYPE, DefaultAuthTokenHandler.CLIENT_CREDENTIALS,
                            DefaultAuthTokenHandler.CLIENTID, map.get(DefaultAuthTokenHandler.CLIENT_ID),
                            DefaultAuthTokenHandler.CLIENTSECRET, map.get(DefaultAuthTokenHandler.CLIENT_SECRET));
                    HttpEntity<MultiValueMap<String, String>> newRequest = new HttpEntity(mapper.writeValueAsString(newMap), headers);
                    return restTemplate.exchange(endpoint, HttpMethod.POST,newRequest,String.class);
                });
            }
        }catch (Exception e){
            log.error("Throwing last exception which was captured, exception occurred for this is ",e);
            throw capturedException;
        }
        log.info(format("Got response code %s with response body %s", response.getStatusCode(), response.getBody()));
        Map<String, Object> responseValues = mapper.readValue(response.getBody(),
            new TypeReference<Map<String, Object>>(){});
        checkResponse(endpoint, response, responseValues);
        AuthConfig token = new AuthConfig();
        if (responseValues.containsKey(ACCESS_TOKEN)) {
            token.setAccessToken(responseValues.get(ACCESS_TOKEN).toString());
        }
        if (responseValues.containsKey(ACCESSTOKEN)) {
            token.setAccessToken(responseValues.get(ACCESSTOKEN).toString());
        }
        if (responseValues.containsKey(REFRESH_TOKEN)) {
            token.setRefreshToken(responseValues.get(REFRESH_TOKEN).toString());
        }
        if (responseValues.containsKey(REFRESHTOKEN)) {
            token.setRefreshToken(responseValues.get(REFRESHTOKEN).toString());
        }
        firstNonNullAsString(responseValues, EXPIRES_IN, EXPIRESIN, REFRESH_TOKEN_EXPIRES_IN)
                .ifPresent(token::setExpiresIn);

        return token;
    }

    private Optional<String> firstNonNullAsString(Map<String, ?> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v != null) {
                log.info("Value for key {} for expires in is {}", k, v);
                if (v instanceof String) return Optional.of(v.toString());
                if (v instanceof Number) return Optional.of(Long.toString(((Number) v).longValue()));
                return Optional.of(v.toString()); // fallback
            }
        }
        return Optional.empty();
    }

    public AuthConfig refreshToken(AuthConfig currentConfig, String endpoint, Map<String, String> map) {
        return refreshToken(currentConfig, endpoint, map, Map.of());
    }

    public AuthConfig refreshToken(AuthConfig currentConfig, String endpoint, Map<String, String> map, Map<String, String> headerMap) {
        try {
            AuthConfig token = getToken(endpoint, map, headerMap);
            if(StringUtils.isBlank(token.getRefreshToken())) {
                token.setRefreshToken(currentConfig.getRefreshToken());
            }
            if(StringUtils.isBlank(token.getExpiresIn())) {
                token.setExpiresIn(currentConfig.getExpiresIn());
            }
            token.setLastRefreshed(Instant.now());
            return token;
        } catch (RetriableException e) {
            log.error(format("RetriableException occurred and Error in refreshToken: %s", e.getMessage()));
            throw e;
        } catch (Exception e) {
            log.error(format("Exception occurred and Error in refreshToken: %s", e.getMessage()));
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    protected void checkResponse(String endpoint, ResponseEntity<String> response, Map responseValues) {
        if (response.getStatusCode() != HttpStatus.OK || (!responseValues.containsKey(ACCESS_TOKEN) && !responseValues.containsKey(ACCESSTOKEN))) {
            String msg = format("Error while authorizing %s: code: %s, details: %s", endpoint, response.getStatusCode(),
                    response.getBody());
            log.error(msg);
            throw new NonRetriableException(ErrorCodes.UNKNOWN_ERROR.name(), msg,
                    String.valueOf(response.getStatusCode()));
        }
    }
}
