package com.syncari.connector.oracle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class OracleSalesCrmRestClient extends SyncariEntityDataRestClient {

    private final int WAIT_TIMEOUT_MILLIS = 600000;

    public OracleSalesCrmRestClient(JsonParserConfig parserConfig, ObjectMapper objectMapper) {
        super(parserConfig, objectMapper);
    }

    public List<EntityData> getData(String url, SyncRequest request) {
        ResponseEntity<String> response = getResponse(url, request.getConnector().getAuthConfig());
        List<String> errors = new ArrayList<>();
        return processResponse(response, request,errors);
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

            List<EntityData> listOfEntityData = processResponse(response, request, errors);
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

    public SyncResponse postRecords(String url, HttpMethod method, SyncRequest request, String operation, String path) {
        SyncResponse response = new SyncResponse();
        var partitioned = Lists.partition(request.getData().get(request.getConnector().getId()), OracleSalesCrmService.CUD_API_MAX_RECORDS);
        for (List<EntityData> part : partitioned) {
            try {
                doPostRecords(url, method, part, request, response, path, operation);
            } catch (NonRetriableException e) {
                log.error("Non retriable exception occurred for request for path {} and operation {}", path, operation);
                handleException(e.getMessage(), response, part);
            } catch (Exception e) {
                log.error("Exception occurred for request for path {} and operation {}", path, operation);
                handleException(e.getMessage(), response, part);
            }
        }
        return response;
    }

    private void handleException(String errorMessage, SyncResponse response, List<EntityData> part) {
        for(EntityData entityData: part) {
            Result errResult = new Result(false, entityData.getId(), entityData.getSyncariEntityId());
            response.getResults().add(errResult.addError(errorMessage));
        }
    }


    public HttpHeaders getHeaders(AuthConfig authConf) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", "application/vnd.oracle.adf.batch+json");
        headers.set("REST-Framework-Version","3");
        HttpHeaders authHeaders = getAuthHeaders(authConf);
        headers.addAll(authHeaders);
        return headers;
    }

    private void doPostRecords(String url, HttpMethod method, List<EntityData> part, SyncRequest request, SyncResponse response, String path, String operation) {
        String json = getPayload(method, part, request, path, operation);
        ResponseEntity<String> cuResponse = null;
        List<EntityData> listOfEntityData = new ArrayList<>();
        try{
            cuResponse = postRaw(getHeaders(request.getConnector().getAuthConfig()), url, json,request.getConnector().getAuthConfig());
            listOfEntityData.addAll(processCUDResponse(cuResponse, request));
            if (CollectionUtils.isNotEmpty(listOfEntityData)) {
                for(int i = 0; i < listOfEntityData.size(); i++) {
                    response.getResults().add(new Result(true, listOfEntityData.get(i).getId(), part.get(i).getSyncariEntityId()));
                }
            }
            if(method == HttpMethod.DELETE && cuResponse.getStatusCode() == HttpStatus.OK) {
                for(int i = 0; i < part.size(); i++) {
                    response.getResults().add(new Result(true, part.get(i).getId(), part.get(i).getSyncariEntityId()));
                }
            }
        }catch (Exception exception){
            String message = String.format("Exception occurred while posting a request for path %s and operation %s and exception %s", path, operation,exception.getMessage());
            log.error(message, exception);
            log.error("Failed payload: {}", json);
            log.info("Retrying batch per record");
            for(EntityData entityData: part) {
                json = getPayload(method, List.of(entityData), request, path, operation);
                try {
                    cuResponse = postRaw(getHeaders(request.getConnector().getAuthConfig()), url, json, request.getConnector().getAuthConfig());
                    List<EntityData> results = processCUDResponse(cuResponse, request);
                    if (!results.isEmpty()) {
                        response.getResults().add(new Result(true, results.get(0).getId(), entityData.getSyncariEntityId()));
                        listOfEntityData.addAll(results);
                    } else if(method == HttpMethod.DELETE && cuResponse.getStatusCode() == HttpStatus.OK) {
                        response.getResults().add(new Result(true, entityData.getId(), entityData.getSyncariEntityId()));
                    } else {
                        response.getResults().add(new Result(false, entityData.getId(), entityData.getSyncariEntityId()));
                    }
                } catch (Exception e1) {
                    response.getResults().add(new Result(false, entityData.getId(), entityData.getSyncariEntityId()).addError(e1.getMessage()));
                }
            }
        }
    }

    private String getPayload(HttpMethod method, List<EntityData> part, SyncRequest request, String path, String operation) {
        return "{\"parts\":" + toBatchedCUPayLoad(request.getEntitySchema(), part, method, path, operation) + " }";
    }

    private String toBatchedCUPayLoad(EntitySchema es, List<EntityData> entities, HttpMethod method, String path, String operation) {
        StringBuilder parts = new StringBuilder();
        parts.append("[");
        int i = 0;
        for (EntityData ed: entities) {
            // For updates we need to explicitly set the id column.
            String pathToUse = path;
            if ((HttpMethod.PATCH == method) || (HttpMethod.DELETE == method)) {
                pathToUse = String.format(path,ed.getId());
            }
            parts.append("{\"id\": \"part"+i + "\", \"path\": \"" + pathToUse+ "\", \"operation\": \""+operation+"\",");
            parts.append("\"payload\": ");
            Map<String, Object> edMap = ed.getValues();
            try {
                objectMapper.getFactory().configure(JsonWriteFeature.ESCAPE_NON_ASCII.mappedFeature(), true);
                parts.append(objectMapper.writeValueAsString(edMap));
            } catch (JsonProcessingException ex) {
                throw new NonRetriableException(ErrorCodes.SCHEMA_ERROR.toString(),
                        String.format("Failed to serialize payload for %s operation. Payload: %s ", method, edMap),
                        ErrorCodes.SCHEMA_ERROR.toString());
            }
            i++;
            parts.append("}");
            if (i != entities.size()){
                parts.append(",");
            }
        }
        parts.append("]");
        return parts.toString();
    }

    protected List<EntityData> processCUDResponse(ResponseEntity<String>response, SyncRequest request) {
        ReadContext ctx = JsonPath.parse(response.getBody());
        checkResponse(response);
        List<Map<String, Object>> data = ctx.read("parts");
        String idField = request.getEntitySchema().getIdField().getApiName();
        String wmField = request.getEntitySchema().getWatermarkField().getApiName();
        List<String> dateFields = request.getEntitySchema().getAttributes().stream().filter(x -> "datetime".equalsIgnoreCase(x.getDataType()))
                .map(x -> x.getApiName()).collect(Collectors.toList());
        List<EntityData> result = new ArrayList<>();
        int numberOfRecordsToProcess = data.size();
        IntStream.range(0, numberOfRecordsToProcess).forEach(x -> {
            Map<String, Object> payload = data.get(x).containsKey("payload") ? (Map<String, Object>)data.get(x).get("payload") : Map.of();
            String partId = data.get(x).containsKey("id") ? (String)data.get(x).get("id") : "EmptyPartId";
            if (MapUtils.isNotEmpty(payload)){
                result.add(processOneItem(idField, wmField, request.getEntityName(), payload));
            }else{
                log.info("Did not get an entity data back for part id {}",partId);
            }
        });
        return result;
    }


    // Implementation to process response
    protected List<EntityData> processResponse(ResponseEntity<String>response, SyncRequest request, List<String> errors) {
        ReadContext ctx = JsonPath.parse(response.getBody());
        List<Map<String, Object>> data = ctx.read("items");
        checkResponse(response);
        String idField = request.getEntitySchema().getIdField().getApiName();
        String wmField = request.getEntitySchema().getWatermarkField().getApiName();
        List<String> dateFields = request.getEntitySchema().getAttributes().stream().filter(x -> "datetime".equalsIgnoreCase(x.getDataType()))
                .map(x -> x.getApiName()).collect(Collectors.toList());
        List<EntityData> result = new ArrayList<>();
        int numberOfRecordsToProcess = data.size();
        IntStream.range(0, numberOfRecordsToProcess).forEach(x -> {
            Map<String,Object> attributes = data.get(x);
            result.add(processOneItem(idField, wmField, request.getEntityName(), attributes));
        });
        return result;
    }


    public EntityData processOneItem(String idField,String wmField,String entityName,  Map<String, Object> attributes){
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
