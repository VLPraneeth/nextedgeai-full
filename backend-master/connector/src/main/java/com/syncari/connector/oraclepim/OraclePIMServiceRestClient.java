package com.syncari.connector.oraclepim;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Slf4j
public class OraclePIMServiceRestClient extends SyncariEntityDataRestClient {
    private final int WAIT_TIMEOUT_MILLIS = 300000;

    public OraclePIMServiceRestClient(JsonParserConfig parserConfig, ObjectMapper objectMapper) {
        super(parserConfig, objectMapper);
    }

    public List<EntityData> getData(String url, SyncRequest request) {
        ResponseEntity<String> response = getResponse(url, request.getConnector().getAuthConfig());
        return processResponse(response, request);
    }

    public HttpHeaders getHeaders(AuthConfig authConf) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", "application/json");
        HttpHeaders authHeaders = getAuthHeaders(authConf);
        headers.addAll(authHeaders);
        return headers;
    }

    @Override
    public RestTemplate getTemplate() {
        HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
        clientHttpRequestFactory.setConnectTimeout(WAIT_TIMEOUT_MILLIS);
        clientHttpRequestFactory.setReadTimeout(WAIT_TIMEOUT_MILLIS);
        return new RestTemplate(clientHttpRequestFactory);
    }

    protected List<EntityData> processResponse(ResponseEntity<String> response, SyncRequest request) {
        ReadContext ctx = JsonPath.parse(response.getBody());
        List<Map<String, Object>> data = ctx.read("items");
        checkResponse(response);
        String idField = request.getEntitySchema().getIdField().getApiName();
        String wmField = request.getEntitySchema().getWatermarkField().getApiName();
        List<EntityData> result = new ArrayList<>();
        int numberOfRecordsToProcess = data.size();
        IntStream.range(0, numberOfRecordsToProcess).forEach(x -> {
            Map<String, Object> attributes = data.get(x);
            result.add(processOneItem(idField, wmField, request.getEntityName(), attributes));
        });
        return result;
    }

    public EntityData processOneItem(String idField, String wmField, String entityName, Map<String, Object> attributes) {
        var ed = new EntityData();
        if (parserConfig.isFieldKey()) {
            ed.setName(entityName);
            ed.setCreatedAt(ZonedDateTime.parse(attributes.get("CreationDateTime").toString()).toEpochSecond() * 1000);
            attributes.forEach((k, v) -> {
                if (k.equalsIgnoreCase(idField)) {
                    ed.setId(v.toString());
                }
                if (wmField.equalsIgnoreCase(k)) {
                    ed.setLastModified(ZonedDateTime.parse(attributes.get(wmField).toString()).toEpochSecond() * 1000);
                }
                ed.addValue(k, v);
            });
        }
        return ed;
    }
}
