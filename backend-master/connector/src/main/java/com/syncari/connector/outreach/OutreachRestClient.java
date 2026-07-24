package com.syncari.connector.outreach;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.syncari.connector.exception.RetriableException;
import org.apache.commons.collections4.MapUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.DataWithCursor;
import com.syncari.connector.rest.SyncariEntityDataRestClient;

import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;

@Slf4j
public class OutreachRestClient extends SyncariEntityDataRestClient  {
	
	private static final int READ_TIMEOUT = 30000;
	
	public OutreachRestClient(JsonParserConfig parserConfig){
        super(parserConfig);
    }
	
	public OutreachRestClient(JsonParserConfig parserConfig, ObjectMapper objectMapper) {
		super(parserConfig, objectMapper);
	}
	
	public DataWithCursor getDataWithCursor(String url, AuthConfig auth) {
		ResponseEntity<String> response = getResponse(url, auth);
		checkResponse(response);
		ReadContext ctx = JsonPath.parse(response.getBody());
		JSONArray results = new JSONArray();
		try{
			results = ctx.read("data");
		} catch (JsonPathException e) {
			log.error("data not found in the outreach API response statusCode: {}, body : {}", response.getStatusCode(), response.getBody());
			throw new RetriableException(response.getStatusCode().name(), response.getBody(),
					String.valueOf(response.getStatusCode()));
		}
        Map links = new HashMap<>();
        try {
            links = (Map) ctx.read("links");
        } catch (JsonPathException e) {
            // Nothing, links not found in the response.
        }
		
        String nextPageURL = "";
        String prevPageURL = "";
        if (MapUtils.isNotEmpty(links)) {
            if (links.containsKey("prev")) {
                try {
                    prevPageURL = URLDecoder.decode(links.get("prev").toString(), StandardCharsets.UTF_8.name());
                } catch (UnsupportedEncodingException e) {
                }
            }

            if (links.containsKey("next")) {
                try {
                    nextPageURL = URLDecoder.decode(links.get("next").toString(), StandardCharsets.UTF_8.name());
                } catch (UnsupportedEncodingException e) {
                }
            }
        }
		List<EntityData> result = new ArrayList<>();
		for (Object r : results) {
			Map row = (Map) r;
			var e = new EntityData();
			if (parserConfig.isFieldKey()) {
				e.setName(row.get("type").toString());
				e.setId(row.get("id").toString());
				Map attrs = (Map) row.get("attributes");
				attrs.forEach((k, v) -> {
					e.addValue(k.toString(), v);
					if (k.toString().equalsIgnoreCase(parserConfig.getIdFieldName())) {
						e.setId(v.toString());
					}
					if ("updatedAt".equalsIgnoreCase(k.toString())) {
						e.setLastModified(ZonedDateTime.parse(attrs.get("updatedAt").toString()).toEpochSecond()*1000);
					}
				});
				Map relationships = (Map) row.get("relationships");
				relationships.forEach((k, v) -> {
					Object d = ((Map)v).get("data");
					if(d == null || !(d instanceof Map)) return;
					e.addValue(k.toString()+"Id", ((Map)d).get("id"));
				});
			}
			result.add(e);
		}
		
        return new DataWithCursor(prevPageURL, nextPageURL, result);
    }
	
}
