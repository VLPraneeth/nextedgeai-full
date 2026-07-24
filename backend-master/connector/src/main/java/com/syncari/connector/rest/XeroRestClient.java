package com.syncari.connector.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import static com.syncari.connector.ConnectorHelper.withBackoffAndErrorHandling;

@Slf4j
public class XeroRestClient extends SyncariEntityDataRestClient{

    public XeroRestClient(JsonParserConfig parserConfig, ObjectMapper objectMapper) {
        super(parserConfig, objectMapper);
    }

    public ResponseEntity<String> getResponse(String url, AuthConfig auth, Map<String, Object> metaConfig, Object... uriArgs) {
        RestTemplate restTemplate = getTemplate();
        return withBackoffAndErrorHandling(()-> uriArgs == null
                ? restTemplate.exchange(url, HttpMethod.GET, new HttpEntity(getHeaders(auth, metaConfig)), String.class)
                : restTemplate.exchange(url, HttpMethod.GET, new HttpEntity(getHeaders(auth, metaConfig)), String.class, uriArgs)
        );
    }

    public HttpHeaders getHeaders(AuthConfig authConf, Map<String, Object> metaConfig) {
        HttpHeaders headers = getHeaders(authConf);
        headers.set("Xero-tenant-id", (String) metaConfig.get("tenantId"));
        return headers;
    }
}
