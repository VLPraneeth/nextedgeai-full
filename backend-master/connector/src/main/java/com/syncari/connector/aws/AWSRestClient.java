package com.syncari.connector.aws;

import com.amazonaws.auth.AWSCredentials;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.DataWithCursor;
import com.syncari.connector.data.DataWithOffset;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class AWSRestClient extends SyncariEntityDataRestClient {

    private List<String> supportedRegionList = List.of("us-east-1","us-east-2","us-west-1","ap-northeast-2",
            "af-south-1","ap-east-1","ap-south-1","ap-northeast-3","ap-southeast-1","ap-southeast-2","ap-northeast-1",
            "ca-central-1","eu-central-1","eu-west-1","eu-west-2","eu-south-1","eu-west-3",
            "eu-north-1","me-south-1", "sa-east-1","us-gov-east-1", "us-gov-west-1");

    private static final String DYNAMODB_ENDPOINT = "https://dynamodb.%s.amazonaws.com/";
    private static final String SERVICENAME = "dynamodb";

    public AWSRestClient(JsonParserConfig parserConfig){
        super(parserConfig);
    }

    public AWSRestClient(JsonParserConfig parserConfig, ObjectMapper objectMapper) {
        super(parserConfig, objectMapper);
    }

    public String buildAWSDynamoDbEndpoint(String regionName){
        if (!isRegionSupported(regionName)){
            throw new IllegalArgumentException(String.format("Region Name %s is not valid", regionName));
        }
        return String.format(DYNAMODB_ENDPOINT,regionName);
    }

    public boolean isRegionSupported(String regionName){
        return supportedRegionList.contains(regionName);
    }

    public HttpHeaders getHeaders(AWSCredentials credentials, String requestBody, String targetAPI, String serviceName, String region, String apiName) {
        URI uri = null;
        try {
            uri = new URI(this.buildAWSDynamoDbEndpoint(region)+apiName);
        } catch (URISyntaxException e) {
            log.error("Not able to parse the uri built for region {}",region);
        }
        SyncariAWSSigner awsSigner = new SyncariAWSSigner(region,serviceName);
        return awsSigner.buildHttpHeaders(uri, credentials, requestBody, targetAPI);
    }

    public ResponseEntity<String> postRequest(String requestBody, AWSCredentials creds, String targetAPI, String url, AuthConfig config, String region, String apiName){
        ResponseEntity<String> response = postRaw(this.getHeaders(creds, requestBody, targetAPI, SERVICENAME, region, apiName),url,
                requestBody, config);
        log.debug("Response status code is 200 {}", response.getStatusCode() == HttpStatus.OK);
        return response;
    }

    public DataWithCursor processResponse(ResponseEntity<String> response, String prevEvalKey, SyncRequest request) {
        log.debug("Data received " + response);
        new SyncariEntityDataRestClient().checkResponse(response);
        Map respMap;
        String lastEvaluatedKey="";
        try {
            respMap = objectMapper.readValue(response.getBody(), Map.class);
            Map<String, Object> lastEvaluatedKeyMap = (Map<String, Object>)respMap.get("LastEvaluatedKey");
            if(MapUtils.isNotEmpty(lastEvaluatedKeyMap)){
                lastEvaluatedKey =  objectMapper.writeValueAsString(lastEvaluatedKeyMap);
            }

        } catch (JsonProcessingException e1) {
            throw new RuntimeException("Failed to read entities.", e1);
        }
        ReadContext ctx = JsonPath.parse(response.getBody());

        if (MapUtils.isEmpty(respMap)) {
            // return empty
            return new DataWithCursor("", "", null);
        }


        List<Map<String, Object>> items = (List<Map<String, Object>>) respMap.get("Items");
        List<EntityData> result = new ArrayList<>();
        String idField = request.getEntitySchema().getIdField().getApiName();
        String wmField = request.getEntitySchema().getWatermarkField().getApiName();
        for (Object r : items) {
            Map row = (Map) r;
            var e = new EntityData();
            if (parserConfig.isFieldKey()) {
                e.setName(request.getEntityName());
                //e.setCreatedAt(ZonedDateTime.parse(row.get("CreatedDate").toString()).toEpochSecond()*1000);
                row.forEach((k, v) -> {
                    Map<String, Object> valueMap = (Map<String, Object>)v;
                    if (k.toString().equalsIgnoreCase(idField)) {
                        e.setId((String)valueMap.get("S"));
                    }
                    if (wmField.equalsIgnoreCase(k.toString())) {
                        e.setLastModified(ZonedDateTime.parse((String)valueMap.get("S")).toEpochSecond()*1000);
                    } else {
                        valueMap.forEach((key,value) -> {
                            switch (key){
                                case "S":
                                    e.addValue(k.toString(), (String)value);
                                    break;
                                case "N":
                                    e.addValue(k.toString(), (Long)value);
                                    break;
                                case "BOOL":
                                    e.addValue(k.toString(), (Boolean)value);
                                    break;
                                default:
                                    e.addValue(k.toString(), (String)value);
                                    break;
                            }
                        });
                    }
                });
            }
            result.add(e);
        }
        return new DataWithCursor(prevEvalKey,lastEvaluatedKey,result);
    }



}
