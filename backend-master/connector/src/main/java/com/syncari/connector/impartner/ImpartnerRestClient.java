package com.syncari.connector.impartner;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.AuthType;
import com.syncari.connector.data.DataWithOffset;
import com.syncari.connector.data.Result;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.SyncResponse;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.rest.SyncariEntityDataRestClient;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import net.snowflake.client.jdbc.ErrorCode;

@Slf4j
public class ImpartnerRestClient extends SyncariEntityDataRestClient  {
    
    private static final int READ_TIMEOUT = 30000;
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(DateTimeFormatter.ISO_OFFSET_DATE_TIME, 
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
    
    public ImpartnerRestClient(JsonParserConfig parserConfig){
        super(parserConfig);
    }
    
    public ImpartnerRestClient(JsonParserConfig parserConfig, ObjectMapper objectMapper) {
        super(parserConfig, objectMapper);
    }

    protected String getAccessToken(ConnectorInfo connector) {
        String authType = connector.getMetaConfig().getOrDefault("authType", "").toString();

        if (StringUtils.isEmpty(authType)) {
            String msg = String.format("Failed to acquire apikey. No authentication type provided.");
            log.error(msg);
            throw new RuntimeException(msg);
        }

        // If authType is apikey based, return the connector apikey.
        if (authType.equalsIgnoreCase(AuthType.ApiKey.name())) {
            return connector.getAuthConfig().getAccessToken();
        }
        AuthConfig config = connector.getAuthConfig();
        String credsBody = String.format("{\"userName\": \"%s\",\"password\": \"%s\"}", config.getUserName(), config.getPassword());

        // For the auth token call, we do not want to pass authorization, impartner APIs fail if we pass those info.
        // So we just send the content-type here.
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);

        ResponseEntity<String> response = postRaw(headers, String.format(ImpartnerService.GET_TOKEN_ENDPOINT, getHost(config)), 
            credsBody, config);
        ReadContext ctx = JsonPath.parse(response.getBody());
        boolean success = (boolean) ctx.read("success");
        if (success) {
            return ctx.read("data").toString();
        }
        return "";
    }

    private Object getHost(AuthConfig config) {
        return StringUtils.isBlank(config.getEndpoint()) ? ImpartnerService.API_HOST_URL : config.getEndpoint();
    }
    
    public DataWithOffset getDataWithOffset(String url, String postBody, Long prevOffset, SyncRequest request) {
        ResponseEntity<String> response = postRaw(url, postBody, request.getConnector().getAuthConfig());
        return processResponse(response, prevOffset, request);
    }

    public DataWithOffset getData(String url, Long prevOffset, SyncRequest request, boolean isSingleObject) {
        ResponseEntity<String> response = getResponse(url, request.getConnector().getAuthConfig());
        return isSingleObject ? processSingleResponse(response, request) : processResponse(response, prevOffset, request);
    }

    public SyncResponse upsertRecords(String url, HttpMethod method, SyncRequest request) {
        SyncResponse response = new SyncResponse();
        for (EntityData ed : request.getData().get(request.getConnector().getId())) {
            try {
                doPostRecords(url, method, ed, request, response);
            } catch (Exception e) {
                Result errResult = new Result(false, ed.getId(), ed.getSyncariEntityId());
                if(e.getMessage().contains("404 NOT_FOUND")) {
                    errResult.setErrorCode(ErrorCodes.DATA_NOT_FOUND.name());
                }
                response.getResults().add(errResult.addError(e.getMessage()));
            }
        }
        return response;
    }

    private void doPostRecords(String url, HttpMethod method, EntityData ed, SyncRequest request, SyncResponse response) {
        Map<String, Object> edMap = ed.getValues();
        // For updates we need to explicitly set the id column.
        if (HttpMethod.PATCH == method) {
            edMap.put(request.getEntitySchema().getIdField().getApiName(), ed.getId());
        }
        String json = "";
        try {
            json = objectMapper.writeValueAsString(edMap);
        } catch (JsonProcessingException ex) {
            throw new NonRetriableException(ErrorCode.INVALID_PARAMETER_VALUE.toString(), 
                String.format("Failed to serialize payload for %s operation. Payload: %s ", method, edMap), 
                ErrorCode.INVALID_PARAMETER_VALUE.toString());
        }
        ResponseEntity<String> cuResponse;
        if (HttpMethod.PUT == method) {
            cuResponse = put(url, json, request.getConnector().getAuthConfig());
        } else if (HttpMethod.PATCH == method) {
            cuResponse = patch(url + "/" + ed.getId(), json, request.getConnector().getAuthConfig());
        } else {
            throw new NonRetriableException(ErrorCodes.BAD_ENDPOINT.toString(), String.format("Unsupported httpmethod %s", method), 
                ErrorCodes.BAD_ENDPOINT.toString());
        }
        DataWithOffset data = processSingleResponse(cuResponse, request);
        if (CollectionUtils.isNotEmpty(data.getData())) {
            // Just one record, but if we use batch api in future, this will be generic.
            for(int i = 0; i < data.getData().size(); i++) {
                response.getResults().add(new Result(true, data.getData().get(i).getId(), ed.getSyncariEntityId()));
            }
        }
        if (CollectionUtils.isNotEmpty(data.getErrors())) {
            for(int i = 0; i < data.getErrors().size(); i++) {
                Result errResult = new Result(false, ed.getId(), ed.getSyncariEntityId());
                response.getResults().add(errResult.addError(data.getErrors().get(i)));
            }
        }
    }

    protected DataWithOffset processResponse(ResponseEntity<String> response, Long prevOffset, SyncRequest request) {
        log.debug("Data received " + response);
        checkResponse(response);
        ReadContext ctx = JsonPath.parse(response.getBody());
        LinkedHashMap data = ctx.read("data");
        if (data.isEmpty() || !data.containsKey("results")) {
            return DataWithOffset.emptyWithOffsets(prevOffset, prevOffset);
        }
        
        List<String> errors = new ArrayList<>();
        if (data.containsKey("errors") && CollectionUtils.isNotEmpty((List) data.get("errors"))) {
            JSONArray results = (JSONArray) data.get("errors");
            log.error("Received errors for request entity {}, FullResponse: {} ", request.getEntityName(), response);
            for (Object r : results) {
                Map row = (Map) r;
                String rowErrString = "";
                JSONArray rowErrors = (JSONArray) row.get("errors");
                for (Object err: rowErrors) {
                    Map fieldError = (Map) err;
                    rowErrString += fieldError.get("fieldName") + ":" + fieldError.get("errorMessage") + "; ";
                }
                errors.add(rowErrString);
            }
            return DataWithOffset.emptyWithErrors(prevOffset, prevOffset, errors);
        }

        String zoneId = request.getConnector().getMetaConfig().getOrDefault(ImpartnerService.TIME_ZONE_ID, "").toString();

        JSONArray results = (JSONArray) data.get("results");
        if (results.isEmpty()) {
            return DataWithOffset.emptyWithOffsets(prevOffset, prevOffset);
        }
        List<EntityData> result = new ArrayList<>();
        String idField = request.getEntitySchema().getIdField().getApiName();
        String wmField = request.getEntitySchema().getWatermarkField().getApiName();
        for (Object r : results) {
            result.add(processResponseRow(r, zoneId, idField, wmField, request));
        }
        
        return new DataWithOffset(prevOffset, prevOffset + result.size(), result, errors);
    }

    public DataWithOffset processSingleResponse(ResponseEntity<String> response, SyncRequest request) {
        log.debug("Data received " + response);
        checkResponse(response);
        Map singleObjResponse = parseSingleObjectResponse(response);
        
        List<String> errors = new ArrayList<>();
        if (singleObjResponse.containsKey("errors") && CollectionUtils.isNotEmpty((List) singleObjResponse.get("errors"))) {
            log.error("Received errors for request entity {}, FullResponse: {} ", request.getEntityName(), response);
            for (Object r : (List) singleObjResponse.get("errors")) {
                Map row = (Map) r;
                errors.add(
                    String.format("ErrorCode: %s, ErrorFields: %s, ErrorMessage: %s ", row.get("code"), row.get("fields"), row.get("message")));
            }
            return DataWithOffset.emptyWithErrors(0l, 0l, errors);
        }

        String zoneId = request.getConnector().getMetaConfig().getOrDefault(ImpartnerService.TIME_ZONE_ID, "").toString();
        if (!singleObjResponse.containsKey("data") || MapUtils.isEmpty((Map) singleObjResponse.get("data"))) {
            return DataWithOffset.emptyWithOffsets(0l, 0l);
        }
        List<EntityData> result = new ArrayList<>();
        result.add(processResponseRow((Map) singleObjResponse.get("data"), zoneId, request.getEntitySchema().getIdField().getApiName(), 
            request.getEntitySchema().getWatermarkField().getApiName(), request));
        return new DataWithOffset(0l, 0l + result.size(), result, errors);
    }

    public EntityData processResponseRow(Object r, String zoneId, String idField, String wmField, SyncRequest request) {
        Map row = (LinkedHashMap) r;
        var e = new EntityData();

        List<String> referenceFieldsNotCustom = request.getEntitySchema().getAttributes().stream()
            .filter(x -> ((x.isReference() || x.getDataType().equalsIgnoreCase("picklist")) && (!x.isCustom())))
            .map(x -> x.getApiName()).collect(Collectors.toList());

        List<String> customReferenceFields = request.getEntitySchema().getAttributes().stream()
                .filter(x -> ((x.isReference() || x.getDataType().equalsIgnoreCase("picklist")) && (x.isCustom())))
                .map(x -> x.getApiName()).collect(Collectors.toList());

        List<String> dateTimeFields = request.getEntitySchema().getAttributes().stream()
            .filter(x -> x.getDataType().equalsIgnoreCase("datetime"))
            .map(x -> x.getApiName().toLowerCase()).collect(Collectors.toList());

        List<String> dateFields = request.getEntitySchema().getAttributes().stream()
            .filter(x -> x.getDataType().equalsIgnoreCase("date"))
            .map(x -> x.getApiName().toLowerCase()).collect(Collectors.toList());

        if (parserConfig.isFieldKey()) {
            e.setName(request.getEntityName());
            if (row.containsKey("created")) {
                e.setCreatedAt(convertToZonedDateTime(row.get("created").toString(), zoneId).toInstant().toEpochMilli());
            }
            row.forEach((k, v) -> {
                if (dateTimeFields.contains(k.toString().toLowerCase()) && v != null) {
                    e.addValue(k.toString(), convertToZonedDateTime(v.toString(), zoneId).toInstant().toEpochMilli());
                } else if (dateFields.contains(k.toString().toLowerCase()) && v != null) {
                    // for date disregard the zone id as input from Impartner does not have time zone
                    // and we should discard the time component anyway
                    e.addValue(k.toString(), convertToLocalDate(v.toString()));
                } else {
                    e.addValue(k.toString(), v);
                }
                if (k.toString().equalsIgnoreCase(idField)) {
                    e.setId(v.toString());
                }
                if (wmField.equalsIgnoreCase(k.toString()) && !ImpartnerService.SUPPORTED_OBJECTS_WITHOUT_WATERMARK.contains(request.getEntityName())) {
                    e.setLastModified(convertToZonedDateTime(row.get(wmField.toLowerCase()).toString(), zoneId).toInstant().toEpochMilli());
                }
            });
            referenceFieldsNotCustom.forEach(refFieldName -> {
                // For cases like references/picklists, the value comes in the apiName`Id` (Example, statusId for status field).
                row.keySet().stream()
                    .filter(x -> x.toString().equalsIgnoreCase(refFieldName.toLowerCase() + "id")).findFirst().ifPresent(x -> {
                    e.addValue(refFieldName, row.get(x));
                });
            });

            customReferenceFields.forEach(refFieldName -> {
                // For cases like custom references, the value comes in the apiName`Id__cf' (Example, statusId__cf for status custome field).
                row.keySet().stream()
                        .filter(x -> {
                            String keyFromResponse = refFieldName.substring(0,refFieldName.indexOf("__cf"));
                            return x.toString().equalsIgnoreCase(keyFromResponse.toLowerCase() + "id__cf");
                        }).findFirst().ifPresent(x -> {
                    e.addValue(refFieldName, row.get(x));
                });
            });
        }
        return e;
    }

    public Map parseSingleObjectResponse(ResponseEntity<String> response) {
        Map singleObjResponse;
        try {
            singleObjResponse = objectMapper.readValue(response.getBody(), Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to process single object response due to " + e.getMessage(), e);
        }
        return singleObjResponse;
    }

    public SyncResponse deleteRecords(String crudURL, SyncRequest request) {
        SyncResponse response = new SyncResponse();
        request.getData().get(request.getConnector().getId()).forEach(ed -> {
            String url = String.format(crudURL, getHost(request.getConnector().getAuthConfig()), request.getEntityName(), ed.getId());
            try {
                ResponseEntity<String> dResp = delete(url, request.getConnector().getAuthConfig());
                DataWithOffset data = processSingleResponse(dResp, request);
                if (CollectionUtils.isNotEmpty(data.getData())) {
                    // Just one record, but if we use batch api in future, this will be generic.
                    for(int i = 0; i < data.getData().size(); i++) {
                        response.getResults().add(new Result(true, data.getData().get(i).getId(), ed.getSyncariEntityId()));
                    }
                }
                if (CollectionUtils.isNotEmpty(data.getErrors())) {
                    for(int i = 0; i < data.getErrors().size(); i++) {
                        Result errResult = new Result(false, ed.getId(), ed.getSyncariEntityId());
                        response.getResults().add(errResult.addError(data.getErrors().get(i)));
                    }
                }
            } catch (NonRetriableException e) {
                if(e.getMessage().contains("404")) {
                    response.getResults().add(new Result(true, ed.getId(), ed.getSyncariEntityId()));
                } else {
                    response.getResults().add(new Result(false, ed.getId(), ed.getSyncariEntityId()));
                }
            }
        });
        return response;
    }
    
    private ZonedDateTime convertToZonedDateTime(Object date, String zoneId) {
        if (date == null) return null;
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                LocalDateTime ltc = LocalDateTime.parse(date.toString(), format);
                return ltc.atZone(StringUtils.isEmpty(zoneId) ? ZoneId.systemDefault() : ZoneId.of(zoneId));
            } catch (DateTimeParseException ex) {
            }
        }
        log.error("Could not parse date {}", date);
        throw new NonRetriableException(ErrorCodes.UNKNOWN_ERROR, "Could not parse date " + date, ErrorCodes.UNKNOWN_ERROR.toString());
    }

    private String convertToLocalDate(Object date) {
        if (date == null) return null;
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(date.toString(), format).toString();
            } catch (DateTimeParseException ex) {
            }
        }
        log.error("Could not parse date {}", date);
        throw new NonRetriableException(ErrorCodes.UNKNOWN_ERROR, "Could not parse date " + date, ErrorCodes.UNKNOWN_ERROR.toString());
    }
}
