package com.syncari.core.enrich.insideview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.rest.SyncariEntityDataRestClient;

import org.springframework.http.HttpHeaders;

public class InsideviewRestClient extends SyncariEntityDataRestClient {

    String accessToken;

    public InsideviewRestClient(String accessToken, JsonParserConfig parserConfig, ObjectMapper mapper) {
        super(parserConfig, mapper);
        this.accessToken = accessToken;
    }

    public HttpHeaders getHeaders(AuthConfig authConf) {
		HttpHeaders headers = super.getHeaders(authConf);
        headers.add("AccessToken", accessToken);
        return headers;
    }
    
}
