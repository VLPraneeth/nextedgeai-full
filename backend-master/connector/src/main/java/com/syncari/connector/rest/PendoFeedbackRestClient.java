package com.syncari.connector.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.service.PendoFeedbackService;
import com.syncari.connector.service.seed.PendoFeedbackSeed;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class PendoFeedbackRestClient extends SyncariEntityDataRestClient {
    DateUtil dateUtil;
    public PendoFeedbackRestClient(JsonParserConfig parserConfig, DateUtil dateUtil){
        super(parserConfig);
    }

    public PendoFeedbackRestClient(JsonParserConfig parserConfig, ObjectMapper objectMapper, DateUtil dateUtil){
        super(parserConfig, objectMapper);
    }

    public PendoFeedbackRestClient(){
        super();
    }

    public HttpHeaders getHeaders(AuthConfig authConf) {
        HttpHeaders headers = super.getHeaders(authConf);
        headers.set("auth-token", authConf.getAccessToken());
        return headers;
    }

    public List<EntityData> get(String url, AuthConfig auth, SyncRequest request) {
        ResponseEntity<String> response = getResponse(url, auth);
        log.debug("Batch Response body: {}", response.getBody());
        checkResponse(response);
        List<EntityData> data = new ArrayList<>();
        try {
            List results = objectMapper.readValue(response.getBody(), List.class);
            for (int j = 0; j < results.size(); j++) {
                Map r = (Map) results.get(j);
                data.add(extractRow(request, r, auth));
            }
            return data;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
    public EntityData extractRow(SyncRequest request, Map<String, Object> row, AuthConfig auth){
        EntityData data = new EntityData(request.getEntityName());
        data.setConnectorId(request.getConnector().getId());
        data.setId(row.get("id").toString());
        data.setValues(row);
        if(row.containsKey("last_seen")) {
            data.setLastModified(dateUtil.parse(row.get("last_seen").toString(), DateUtil.dateFormatMillis).toInstant().toEpochMilli());
        } else if(row.containsKey("updated_at")) {
            data.setLastModified(dateUtil.parse(row.get("updated_at").toString(), DateUtil.dateFormatMillis).toInstant().toEpochMilli());
        }
        if("account".equalsIgnoreCase(request.getEntityName())) {
            boolean fetchTags = (boolean)request.getSourceParams().getOrDefault(PendoFeedbackSeed.ACCOUNT_TAG, false);
            if(fetchTags) {
                ResponseEntity<String> response = getResponse(PendoFeedbackService.PENDO_URL + "accounts/"+data.getId()+"/tags", auth);
                log.debug("Batch Response body: {}", response.getBody());
                checkResponse(response);
                try {
                    List tags = objectMapper.readValue(response.getBody(), List.class);
                    data.addValue("tags", tags);
                } catch (JsonProcessingException e) {
                    log.error("Error {} fetching tags for account {}", e.getMessage(), data.getId());
                }
            }
        }
        return data;
    }
}
