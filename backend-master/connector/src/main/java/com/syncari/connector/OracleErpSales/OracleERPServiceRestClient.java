package com.syncari.connector.OracleErpSales;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.DataWithOffset;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.rest.SyncariEntityDataRestClient;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class OracleERPServiceRestClient extends SyncariEntityDataRestClient {
    private final int WAIT_TIMEOUT_MILLIS = 300000;

    public OracleERPServiceRestClient(JsonParserConfig parserConfig, ObjectMapper objectMapper) {
        super(parserConfig, objectMapper);
    }

    public List<EntityData> getData(String url, SyncRequest request) {
        ResponseEntity<String> response = getResponse(url, request.getConnector().getAuthConfig());
        return processResponse(response, request);
    }

    public List<EntityData> getChildObjectData(String url, String parentIdField, String parentIdValue, SyncRequest request) {
        ResponseEntity<String> response = getResponse(url, request.getConnector().getAuthConfig());
        return processChildResponse(response, parentIdField, parentIdValue, request);
    }

    public HttpHeaders getHeaders(AuthConfig authConf) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", "application/json");
        HttpHeaders authHeaders = getAuthHeaders(authConf);
        headers.addAll(authHeaders);
        return headers;
    }

    public List<String> getUniqueIds(String url, String key, AuthConfig config) {
        ResponseEntity<String> response = getResponse(url, config);
        ReadContext ctx = JsonPath.parse(response.getBody());
        List<Map<String, Object>> data = ctx.read("items");
        return data.stream().filter(d -> d.containsKey(key)).map(d -> (String) d.get(key)).collect(Collectors.toList());
    }

    public List<EntityData> getChildObjectsWithOffset(String childObjectUrl, String parentIdField, String parentIdValue, SyncRequest request) {
        int currOffset = 0;
        while(true) {
            String url = childObjectUrl+"&offset="+currOffset;
            ResponseEntity<String> response = getResponse(url, request.getConnector().getAuthConfig());
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new RuntimeException(String.format("Api call failed for url %s with status code %s with message %s",
                        url, response.getStatusCode(), response.getBody()));
            }
            return processChildResponse(response, parentIdField, parentIdValue, request);
        }
    }

    public DataWithOffset getDataWithOffset(String url, Long prevOffset, SyncRequest request) {
        try {
            ResponseEntity<String> response = getResponse(url, request.getConnector().getAuthConfig());
            ReadContext ctx = JsonPath.parse(response.getBody());
            List<Map<String, Object>> data = ctx.read("items");
            if (data.isEmpty()) {
                return DataWithOffset.emptyWithOffsets(prevOffset, prevOffset);
            }
            List<String> errors = new ArrayList<>();
            List<EntityData> listOfEntityData = processResponse(response, request);
            return new DataWithOffset(prevOffset, prevOffset + listOfEntityData.size(), listOfEntityData, errors);
        } catch (NonRetriableException e) {
            if(ErrorCodes.valueOf(e.getErrorCode()).equals(ErrorCodes.BAD_REQUEST) && StringUtils.isBlank(e.getMessage())) {
                throw new RetriableException(e.getErrorCode(), "No error message in response", e.getStatusCode());
            } else throw e;
        }
    }

    @Override
    public RestTemplate getTemplate() {
        HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
        clientHttpRequestFactory.setConnectTimeout(WAIT_TIMEOUT_MILLIS);
        clientHttpRequestFactory.setReadTimeout(WAIT_TIMEOUT_MILLIS);
        return new RestTemplate(clientHttpRequestFactory);
    }

    protected List<EntityData> processChildResponse(ResponseEntity<String> response, String parentIdField, String parentIdValue, SyncRequest request) {
        ReadContext ctx = JsonPath.parse(response.getBody());
        List<Map<String, Object>> data = ctx.read("items");
        checkResponse(response);
        String idField = request.getEntitySchema().getIdField().getApiName();
        String wmField = request.getEntitySchema().getWatermarkField().getApiName();
        List<EntityData> result = new ArrayList<>();
        int numberOfRecordsToProcess = data.size();
        IntStream.range(0, numberOfRecordsToProcess).forEach(x -> {
            Map<String,Object> attributes = data.get(x);
            result.add(processOneChildItem(idField, wmField, parentIdField, parentIdValue, request.getEntityName(), attributes));
        });
        return result;
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
            Map<String,Object> attributes = data.get(x);
            result.add(processOneItem(idField, wmField, request.getEntityName(), attributes));
        });
        return result;
    }

    public EntityData processOneChildItem(String idField, String wmField, String parentIdField, String parentIdValue,  String entityName,  Map<String, Object> attributes){
        var ed = new EntityData();
        if (parserConfig.isFieldKey()) {
            ed.setName(entityName);
            ed.setCreatedAt(ZonedDateTime.parse(attributes.get("CreationDate").toString()).toEpochSecond()*1000);
            attributes.forEach((k, v) -> {
                if (k.equalsIgnoreCase(idField)) {
                    ed.setId(parentIdValue+"|"+v.toString());
                }
                if (wmField.equalsIgnoreCase(k)) {
                    ed.setLastModified(ZonedDateTime.parse(attributes.get(wmField).toString()).toEpochSecond()*1000);
                }
                ed.addValue(k, v);
            });
        }
        ed.addValue(parentIdField, parentIdValue);
        return ed;
    }

    public EntityData processOneItem(String idField, String wmField, String entityName,  Map<String, Object> attributes){
        var ed = new EntityData();
        if (parserConfig.isFieldKey()) {
            ed.setName(entityName);
            ed.setCreatedAt(ZonedDateTime.parse(attributes.get("CreationDate").toString()).toEpochSecond()*1000);
            attributes.forEach((k, v) -> {
                if (k.equalsIgnoreCase(idField)) {
                    ed.setId(v.toString());
                }
                if (wmField.equalsIgnoreCase(k)) {
                    ed.setLastModified(ZonedDateTime.parse(attributes.get(wmField).toString()).toEpochSecond()*1000);
                }
                ed.addValue(k, v);
            });
        }
        return ed;
    }
}
