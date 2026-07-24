package com.syncari.connector.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.config.ProxyConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;

import static com.syncari.connector.ConnectorHelper.withBackoff;
import static com.syncari.connector.ConnectorHelper.withHttpErrorHandling;

@Slf4j
public class PendoRestClient extends SyncariEntityDataRestClient {

    private static final String APPLICATION_JSON = "application/json";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final int READ_TIMEOUT = 10 * 60 * 1000; // Set timeout to 10 mins

    public PendoRestClient(JsonParserConfig parserConfig) {
        super(parserConfig);
    }

    public PendoRestClient(JsonParserConfig parserConfig, ObjectMapper objectMapper){
        super(parserConfig, objectMapper);
    }

    public PendoRestClient(){
        super();
    }

    public PendoRestClient(ProxyConfig proxy) {
        super(proxy);
    }

    @Override
    public HttpHeaders getHeaders(AuthConfig authConf) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-pendo-integration-key", authConf.getAccessToken());
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }

    @Override
    public RestTemplate getTemplate() {
        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
        httpClientBuilder = httpClientBuilder.setRedirectStrategy(new RedirectStrategyWithoutOriginalHeaders());
        CloseableHttpClient client =
                httpClientBuilder.build();
        HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory(client);
        clientHttpRequestFactory.setConnectTimeout(READ_TIMEOUT);
        clientHttpRequestFactory.setReadTimeout(READ_TIMEOUT);
        return new RestTemplate(clientHttpRequestFactory);
    }

    public ResponseEntity<String> postWithBackOff(String url, String body, AuthConfig auth) {
        HttpHeaders headers = getHeaders(auth);
        RestTemplate restTemplate = getTemplate();
        return withBackoff(() -> withHttpErrorHandling(() -> {
            log.info("postRaw URL {}", url);
            log.debug("postRaw body {}", body);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity(body, headers), String.class);
            log.info("HTTP Status {}", response.getStatusCode());
            log.debug("HTTP Response {}", response.getBody());
            if (response.getStatusCode().isError()) {
                log.error("Payload generating error {}", body);
                log.error("HTTP Error Body {}", response.getBody());
            }
            return response;
        }), 500, 1000, 3);
    }
}
