package com.syncari.karibu.rest.util;

import com.syncari.karibu.rest.config.KaribuConfig;
import com.syncari.utils.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@Slf4j
public class ViperUtils {
    @Autowired
    RestTemplate viperRestTemplate;

    @Autowired
    KaribuConfig karibuConfig;

    @Retryable(value = {HttpServerErrorException.ServiceUnavailable.class}, maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public ResponseEntity<String> callRealtimeSync(String targetEntityId, String instanceId, String connectorId, String requestId, String body){
        HttpHeaders headers = getHttpHeaders();
        log.debug(" Karibu Payload to viper {}", body);
        HttpEntity httpEntity = new HttpEntity(body, headers);
        var timer = new Timer(60000, String.format("Real time Sync call took more than 1 minute"), log);
        var response = viperRestTemplate.exchange(getViperEndpoint(connectorId, requestId), HttpMethod.POST, httpEntity, String.class,instanceId, targetEntityId);
        timer.close(String.format("Instance Id %s Entity Id %s Connector Id %s", instanceId, targetEntityId, connectorId));
        return response;
    }

    public ResponseEntity<Map<String, Object>> health() {
        HttpHeaders headers = getHttpHeaders();
        HttpEntity httpEntity = new HttpEntity("", headers);
        return viperRestTemplate.exchange(getHealthEndpoint(), HttpMethod.GET, httpEntity, ParameterizedTypeReference.forType(Map.class));
    }

    private String getViperEndpoint(String connectorId, String requestId) {
        return  String.format("%s/api/v1/pipeline/sync/{instanceId}/{targetEntityId}?connectorId=%s&requestId=%s", karibuConfig.getViperApiEndpoint(), connectorId, requestId);
    }

    private String getHealthEndpoint() {
        return  String.format("%s/api/v1/pipeline/health", karibuConfig.getViperApiEndpoint());
    }

    private static HttpHeaders getHttpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, "karibu");
        headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
        headers.set(HttpHeaders.ACCEPT, "*/*");
        return headers;
    }

}
