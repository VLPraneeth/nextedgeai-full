package com.syncari.connector.gainsightcsnxt;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.DataWithOffset;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.Result;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.SyncResponse;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.rest.SyncariEntityDataRestClient;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import net.snowflake.client.jdbc.ErrorCode;

@Slf4j
public class GainsightRestClient extends SyncariEntityDataRestClient  {
    
    private static final int READ_TIMEOUT = 30000;
    
    public GainsightRestClient(JsonParserConfig parserConfig){
        super(parserConfig);
    }
    
    public GainsightRestClient(JsonParserConfig parserConfig, ObjectMapper objectMapper) {
        super(parserConfig, objectMapper);
    }
    
    public DataWithOffset getDataWithOffset(String url, String postBody, Long prevOffset, SyncRequest request) {
        ResponseEntity<String> response = postRaw(url, postBody, request.getConnector().getAuthConfig());
        return processResponse(response, prevOffset, request);
    }

    public DataWithOffset getData(String url, Long prevOffset, SyncRequest request) {
        ResponseEntity<String> response = getResponse(url, request.getConnector().getAuthConfig());
        return processResponse(response, prevOffset, request);
    }

    public SyncResponse postRecords(String url, HttpMethod method, SyncRequest request) {
        SyncResponse response = new SyncResponse();
        var partitioned = Lists.partition(request.getData().get(request.getConnector().getId()), GainsightService.CUD_API_MAX_RECORDS);
        for (List<EntityData> part : partitioned) {
            try {
                doPostRecords(url, method, part, request, response);
            } catch (NonRetriableException e) {
                // All errorcodes from Gainsight is 400. we want to isolate the error, so try one by one.
                if (e.getErrorCode() == ErrorCodes.BAD_REQUEST.toString()) {
                    doPostRecordsOneByOne(url, method, part, request, response);
                } else {
                    throw e;
                }
            } catch (Exception e) {
                doPostRecordsOneByOne(url, method, part, request, response);
            }
        }
        return response;
    }

    private void doPostRecords(String url, HttpMethod method, List<EntityData> part, SyncRequest request, SyncResponse response) {
        String json = "{ \"records\":" + toBatchedCUPayLoad(request.getEntitySchema(), part, method) + " }";
        ResponseEntity<String> cuResponse;
        if (HttpMethod.POST == method) {
            cuResponse = postRaw(url, json, request.getConnector().getAuthConfig());
        } else if (HttpMethod.PUT == method) {
            if(request.getEntityName().equalsIgnoreCase("gsuser")) {
                cuResponse = put(url + "?key=Gsid", json, request.getConnector().getAuthConfig());
            } else {
                cuResponse = put(url + "?keys=Gsid", json, request.getConnector().getAuthConfig());
            }
        } else {
            throw new NonRetriableException(ErrorCodes.BAD_ENDPOINT.toString(), String.format("Unsupported httpmethod for postData %s", method), 
                ErrorCodes.BAD_ENDPOINT.toString());
        }
        DataWithOffset data = processResponse(cuResponse, 0l, request);
        if (CollectionUtils.isNotEmpty(data.getData())) {
            for(int i = 0; i < data.getData().size(); i++) {
                response.getResults().add(new Result(true, data.getData().get(i).getId(), part.get(i).getSyncariEntityId()));
            }
        }
        if (CollectionUtils.isNotEmpty(data.getErrors())) {
            for(int i = 0; i < data.getErrors().size(); i++) {
                Result errResult = new Result(false, part.get(i).getId(), part.get(i).getSyncariEntityId());
                response.getResults().add(errResult.addError(data.getErrors().get(i)));
            }
        }
    }

    private void doPostRecordsOneByOne(String url, HttpMethod method, List<EntityData> part, SyncRequest request, SyncResponse response) {
        for (EntityData ed: part) {
            try {
                List<EntityData> oneRec = new ArrayList<>();
                oneRec.add(ed);
                doPostRecords(url, method, oneRec, request, response);
            } catch (Exception e) {
                Result errResult = new Result(false, ed.getId(), ed.getSyncariEntityId());
                response.getResults().add(errResult.addError(e.getMessage()));
            }
        }
        
    }

    private String toBatchedCUPayLoad(EntitySchema es, List<EntityData> entities, HttpMethod method) {
        List<Map<String, Object>> recordList = Lists.newArrayList();
        for (EntityData ed: entities) {
            Map<String, Object> edMap = ed.getValues();
            // For updates we need to explicitly set the id column.
            if (HttpMethod.PUT == method) {
                edMap.put(es.getIdField().getApiName(), ed.getId());
            }
            recordList.add(edMap);
        }
        try {
            return objectMapper.writeValueAsString(recordList);
        } catch (JsonProcessingException ex) {
            throw new NonRetriableException(ErrorCode.INVALID_PARAMETER_VALUE.toString(), 
                String.format("Failed to serialize payload for %s operation. Payload: %s ", method, recordList), 
                ErrorCode.INVALID_PARAMETER_VALUE.toString());
        }
    }

    protected DataWithOffset processResponse(ResponseEntity<String> response, Long prevOffset, SyncRequest request) {
        log.debug("Data received " + response);
        checkResponse(response);
        ReadContext ctx = JsonPath.parse(response.getBody());
        LinkedHashMap data = ctx.read("data");
        if (data.isEmpty() || !data.containsKey("records")) {
            return DataWithOffset.emptyWithOffsets(prevOffset, prevOffset);
        }
        List<String> errors = new ArrayList<>();
        if (data.containsKey("errors") && CollectionUtils.isNotEmpty((List) data.get("errors"))) {
            JSONArray results = (JSONArray) data.get("errors");
            log.error("Received errors for request entity {}, FullResponse: {} ", request.getEntityName(), response);
            for (Object r : results) {
                Map row = (Map) r;
                String rowErrString = "";
                if(request.getEntityName().equalsIgnoreCase("gsuser")) {
                    rowErrString = (String)row.get("errorMessage");
                } else {
                    JSONArray rowErrors = (JSONArray) row.get("errors");
                    for (Object err: rowErrors) {
                        Map fieldError = (Map) err;
                        rowErrString += fieldError.get("fieldName") + ":" + fieldError.get("errorMessage") + "; ";
                    }
                }
                errors.add(rowErrString);
            }
            return DataWithOffset.emptyWithErrors(prevOffset, prevOffset, errors);
        }

        JSONArray results = (JSONArray) data.get("records");
        List<EntityData> result = new ArrayList<>();
        String idField = request.getEntitySchema().getIdField().getApiName();
        String wmField = request.getEntitySchema().getWatermarkField().getApiName();
        // date fields cannot be set to empty string as it results in incorrect change detection between syncari and source.
        List<String> dateFields = request.getEntitySchema().getAttributes().stream().filter(x -> "datetime".equalsIgnoreCase(x.getDataType()))
            .map(x -> x.getApiName()).collect(Collectors.toList());
        for (Object r : results) {
            Map row = (Map) r;
            var e = new EntityData();
            if (parserConfig.isFieldKey()) {
                e.setName(request.getEntityName());
                if(row.containsKey("CreatedDate")) {
                    e.setCreatedAt(ZonedDateTime.parse(row.get("CreatedDate").toString()).toEpochSecond() * 1000);
                }
                row.forEach((k, v) -> {
                    if (k.toString().equalsIgnoreCase(idField)) {
                        e.setId(v.toString());
                    }
                    if (wmField.equalsIgnoreCase(k.toString())) {
                        try {
                            e.setLastModified(ZonedDateTime.parse(row.get(wmField).toString()).toEpochSecond() * 1000);
                        } catch (DateTimeParseException e1) {
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S");
                            ZonedDateTime dateTime = ZonedDateTime.parse(row.get(wmField).toString(), formatter.withZone(ZoneId.systemDefault()));
                            e.setLastModified(dateTime.toEpochSecond() * 1000);
                        }
                    }
                    if (dateFields.contains(k.toString()) && v != null && StringUtils.isNotEmpty(v.toString())) {
                        e.addValue(k.toString(), v);
                    } else {
                        e.addValue(k.toString(), v);
                    }
                });
            }
            result.add(e);
        }
        
        return new DataWithOffset(prevOffset, prevOffset + result.size(), result, errors);
    }

    public SyncResponse deletedRecords(String crudURL, SyncRequest request) {
        SyncResponse syncResponse = new SyncResponse();
        if(request.getEntityName().equalsIgnoreCase("gsuser")) {
            String url = String.format(crudURL, request.getConnector().getEndpoint()) + "/status?status=false";
            List<List<EntityData>> partitions = Lists.partition(request.getData().get(request.getConnector().getId()), 50);
            partitions.forEach(partition -> {
                String payload = buildPayload(partition);
                try {
                    put(url, payload, request.getConnector().getAuthConfig());
                } catch (Exception e) {
                    syncResponse.appendError(String.format("Failed to delete users. Error - %s", e.getMessage()));
                }
            });
        } else {
            request.getData().get(request.getConnector().getId()).forEach(ed -> {
                String url = String.format(crudURL, request.getConnector().getEndpoint(), request.getEntityName().toLowerCase(), ed.getId());
                ResponseEntity<String> dResp = delete(url, request.getConnector().getAuthConfig());
                ReadContext ctx = JsonPath.parse(dResp.getBody());
                String errorCode = ctx.read("errorCode");
                if (StringUtils.isNotEmpty(errorCode)) {
                    syncResponse.appendError(String.format("Failed to delete record with id %s due to %s", ed.getId(), ctx.read("errorDesc")));
                }
            });
        }
        return syncResponse;
    }

    private String buildPayload(List<EntityData> partition) {
        return partition.stream().map(ed -> "\"" + ed.getId() + "\"").collect(Collectors.toList()).toString();
    }

}
