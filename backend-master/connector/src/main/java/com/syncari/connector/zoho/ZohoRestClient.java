package com.syncari.connector.zoho;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.utils.DateUtil;

import net.minidev.json.JSONArray;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ZohoRestClient extends SyncariEntityDataRestClient  {
    
    private static final int READ_TIMEOUT = 30000;
    // To be sent as part of headers
    public static final String WATERMARK_FIELD = "If-Modified-Since";

    private DateUtil dateUtil;

    public ZohoRestClient(JsonParserConfig parserConfig){
        super(parserConfig);
    }
    
    public ZohoRestClient(JsonParserConfig parserConfig, ObjectMapper objectMapper, DateUtil dateUtil) {
        super(parserConfig, objectMapper);
        this.dateUtil = dateUtil;
    }
    
    public ZohoRestClient() {
        super();
    }
    
    // Just an overload for test intercepting.
    public ResponseEntity<String> getResponse(String url, AuthConfig auth) {
        return super.getResponse(url, auth);
    }

    public ZohoEntityPage get(String url, SyncRequest request) {
        ZohoEntityPage entityPage = new ZohoEntityPage();
        AuthConfig auth = request.getConnector().getAuthConfig().clone();
        if (request.getWatermark() != null && request.getWatermark().getStart() > 0) {
            // The WATERMARK_FIELD defined does not work well with ms precision
            // As per documentation https://www.zoho.com/crm/developer/docs/api/v2.1/get-deleted-records.html
            // The correct format is Example: 2019-07-25T15:26:49+05:30
            auth.addHeader(WATERMARK_FIELD, getIfModifiedSince(request.getWatermark().getStart()));
        }
        ResponseEntity<String> dataResponse = getResponse(url, auth);
        if (StringUtils.isEmpty(dataResponse.getBody())) {
            entityPage.setHasMore(false);
            return entityPage;
        }
        ReadContext dataCtx = JsonPath.parse(dataResponse.getBody());
        Boolean hasMore = false;
        Integer currentPage = null;
        try {
            hasMore = JsonPath.read(dataResponse.getBody(), "$.info.more_records");
            currentPage = JsonPath.read(dataResponse.getBody(), "$.info.page");
        } catch (PathNotFoundException e) {
            log.info("Page Information not found ", request.getEntityName());
            hasMore = false;
            currentPage = null;
        } 
                
        List<EntityData> results = 	parseEntityDataList(dataCtx, request);
        
        entityPage.setData(results);
        entityPage.setHasMore(hasMore != null ? true : false);
        entityPage.setNextPage(currentPage + 1);
        log.info("Found {} records for {}",results.size(),request.getEntityName());
        return entityPage;
    }

    public String getIfModifiedSince(long wm) {
        long wmValue = TimeUnit.MILLISECONDS.toSeconds(wm);
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.ofInstant(Instant.ofEpochSecond(wmValue), ZoneId.systemDefault()));
    }
    
    public List<EntityData> parseEntityDataList(ReadContext dataCtx, SyncRequest request) {
        List<EntityData> results = new ArrayList<>();
        List<Map<String, Object>> rows = "users".equalsIgnoreCase(request.getEntityName()) ? 
            dataCtx.read("users") : dataCtx.read("data");

        List<String> referenceFields = request.getEntitySchemaWithMappedFields().getAttributes()
            .stream().filter(x -> x.isReference()).map(x -> x.getApiName()).collect(Collectors.toList());

        List<String> multiValueFields = request.getEntitySchemaWithMappedFields().getAttributes()
                .stream().filter(x -> x.isMultiValueField()).map(x -> x.getApiName()).collect(Collectors.toList());

        final String wmField = request.getEntitySchema().getWatermarkField().getApiName();
        List<String> mappedAttributesApiNames = request.getEntitySchemaWithMappedFields().getAttributes().stream().map(x -> x.getApiName()).collect(Collectors.toList());
        if (rows != null && rows.size() > 0) {
            for (int i = 0; i < rows.size(); i++) {
                EntityData data = new EntityData(request.getEntityName());
                Map<String, Object> resultMap = rows.get(i);
                data.setConnectorId(request.getConnector().getId());
                data.setId((rows.get(i)).get("id").toString());
                if (resultMap.containsKey("deleted_time")) {
                    data.setDeleted(true);
                    data.setLastModified(dateUtil.toEpochMilli(resultMap.get("deleted_time").toString()));
                } else {
                    data.setDeleted(false);
                }
                resultMap.forEach((k, v) -> {
                    if(wmField != null && wmField.equalsIgnoreCase((String)k)) {
                        data.setLastModified(dateUtil.toEpochMilli(v.toString()));
                    } else if (k.toString().equalsIgnoreCase("Created_Time")) {
                        data.setCreatedAt(dateUtil.toEpochMilli(v.toString()));
                    }
                    if (!k.toString().startsWith("$") && mappedAttributesApiNames.contains(k)) {
                        if (referenceFields.contains(k.toString()) && v instanceof LinkedHashMap) {
                            Map<String, Object> refValue = (LinkedHashMap) v;
                            data.addValue((String)k, refValue.get("id"));
                        } else if (multiValueFields.contains(k.toString()) && v instanceof JSONArray) {
                            JSONArray jsonArray = (JSONArray)v;
                            List<String> jsonList = new ArrayList<>();
                            if(!jsonArray.isEmpty()) {
                                if(String.class.isAssignableFrom(jsonArray.get(0).getClass())) {
                                    jsonArray.forEach(row -> jsonList.add((String) row));
                                } else if (Map.class.isAssignableFrom(jsonArray.get(0).getClass())) {
                                    jsonArray.forEach(row -> {
                                        Map<String, String> map = (Map<String, String>) row;
                                        if(map.containsKey("value")) {
                                            jsonList.add(map.get("value"));
                                        }
                                    });
                                }
                            }
                            data.addValue(k, jsonList);
                        } else {
                            data.addValue((String)k, v);
                        }
                    }					 
                });
                results.add(data);
            }
        }
        long maxLastModified = results.stream().max(Comparator.comparingLong(EntityData::getLastModified))
            .map(e -> e.getLastModified()).orElse(results.get(results.size()-1).getLastModified());
        long minLastModified = results.stream().min(Comparator.comparingLong(EntityData::getLastModified))
            .map(e -> e.getLastModified()).orElse(results.get(results.size()-1).getLastModified());
        log.info("Found maxLastModified as: {}; found minLastModified as: {} ", maxLastModified, minLastModified);
        return results;
    }
    
}
