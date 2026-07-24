package com.syncari.connector.rest;



import static com.syncari.connector.ConnectorHelper.withBackoffAndErrorHandling;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.EntityData;
import com.syncari.connector.SalesloftEntityPage;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.SyncRequest;
import com.syncari.utils.DateUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SalesloftRestClient extends SyncariEntityDataRestClient  {
	
	private static final int READ_TIMEOUT = 30000;
	
	private DateUtil dateUtil;

	public SalesloftRestClient(JsonParserConfig parserConfig){
        super(parserConfig);
    }
	
	public SalesloftRestClient(JsonParserConfig parserConfig, ObjectMapper objectMapper, DateUtil dateUtil) {
		super(parserConfig, objectMapper);
		this.dateUtil = dateUtil;
	}
	
	public SalesloftRestClient() {
		super();
	}
	
	public SalesloftEntityPage get(String url, SyncRequest request) {
		ResponseEntity<String> dataResponse = getResponse(url, request.getConnector().getAuthConfig());
		ReadContext dataCtx = JsonPath.parse(dataResponse.getBody());
		Integer nextPageNum = null;
		try {
			nextPageNum = JsonPath.read(dataResponse.getBody(), "$.metadata.paging.next_page");
		} catch (PathNotFoundException e) {
            log.info("Next page not found for ", request.getEntityName());
            nextPageNum = null;
        } 
				
		List<EntityData> results = 	parseEntityDataList(dataCtx, request);	
		
		SalesloftEntityPage entityPage = new SalesloftEntityPage();
		entityPage.setData(results);
		entityPage.setHasMore(nextPageNum != null ? true : false);
		entityPage.setNextPage(nextPageNum);
		log.info("Found {} records for {}",results.size(),request.getEntityName());
        return entityPage;
    }
	
	public ResponseEntity<String> postSingleEntity(String url, String payload, AuthConfig auth) {
		RestTemplate restTemplate = getTemplate();
		
		try {
			log.info("HTTP POST at {}",url);
			log.debug("HTTP POST payload {}", payload);
			ResponseEntity<String> response = withBackoffAndErrorHandling(()-> restTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity(payload, getHeaders(auth)), String.class));
			log.info("POST: HTTP Status {}",response.getStatusCode());
			
			return response;
		}catch(HttpClientErrorException e){
            log.error(e.getMessage(),e);
            log.error(e.getResponseBodyAsString());
            throw e;
        }
	}
	
	public List<EntityData> parseEntityDataList(ReadContext dataCtx, SyncRequest request) {
		List<EntityData> results = new ArrayList<>();
		List rows = dataCtx.read("data");
		
		if (rows != null && rows.size() > 0) {
			 for (int i = 0; i < rows.size(); i++) {
				 EntityData data = new EntityData(request.getEntityName());
				 Map<String, Object> resultMap = (Map) rows.get(i);
				 data.setConnectorId(request.getConnector().getId());
				 data.setId(((Map)rows.get(i)).get("id").toString());
				 data.setDeleted(false);
                 resultMap.forEach((k, v) -> {
					 if(request.getEntitySchema().getWatermarkField().getApiName().equalsIgnoreCase((String)k)) {
                        data.setLastModified(dateUtil.toEpochMilli(v.toString()));
					 } else if ("created_at".equalsIgnoreCase((String)k)) {
                        data.setCreatedAt(dateUtil.toEpochMilli(v.toString()));
                     }
					 if("cadence_membership".equalsIgnoreCase(request.getEntityName()) && v instanceof Map) {
                        data.addValue("views_count", ((Map)v).getOrDefault("views", 0));
                        data.addValue("clicks_count", ((Map)v).getOrDefault("clicks", 0));
                        data.addValue("replies_count", ((Map)v).getOrDefault("replies", 0));
                        data.addValue("calls_count", ((Map)v).getOrDefault("calls", 0));
                        data.addValue("sent_emails_count", ((Map)v).getOrDefault("sent_emails", 0));
                        data.addValue("bounces_count", ((Map)v).getOrDefault("bounces", 0));
                     } else if("email".equalsIgnoreCase(request.getEntityName()) && v instanceof Map && k.equalsIgnoreCase("counts")) {
						 data.addValue("views_count", ((Map)v).getOrDefault("views", 0));
						 data.addValue("clicks_count", ((Map)v).getOrDefault("clicks", 0));
						 data.addValue("replies_count", ((Map)v).getOrDefault("replies", 0));
						 data.addValue("unique_devices_count", ((Map)v).getOrDefault("unique_devices", 0));
						 data.addValue("unique_locations_count", ((Map)v).getOrDefault("unique_locations", 0));
						 data.addValue("attachments_count", ((Map)v).getOrDefault("attachments", 0));
					 } else if("cadence".equalsIgnoreCase(request.getEntityName()) && v instanceof Map) {
                        data.addValue("cadence_people_count", ((Map)v).getOrDefault("cadence_people", 0));
                        data.addValue("target_daily_people_count", ((Map)v).getOrDefault("target_daily_people", 0));
                     } else if(v instanceof Map && ((Map)v).containsKey("id")) {
                        data.addValue(k.toString(), ((Map)v).get("id"));
                     } else if ("custom_fields".equalsIgnoreCase(k.toString())) {
                        ((Map<String, Object>) v).forEach((kc, vc) -> data.addValue(kc.toString(), vc.toString()));
                     } else {
						data.addValue((String)k, v);
					 }					 
				 });
				 results.add(data);
			 }
		}
		
		return results;
	}

	
}
