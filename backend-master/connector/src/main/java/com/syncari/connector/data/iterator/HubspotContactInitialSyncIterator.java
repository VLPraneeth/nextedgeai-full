package com.syncari.connector.data.iterator;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.Stats;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.rest.SyncariEntityDataRestClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HubspotContactInitialSyncIterator implements EntityDataBatchIterator {
	private SyncRequest request;
	private SyncariEntityDataRestClient restClient;
	private ObjectMapper objectMapper;
	private boolean hasNext = true;
	private boolean consumed = true;
	private List<EntityData> currentPage = null;
	private long vidOffset =0l;
	String url = "https://api.hubapi.com/contacts/v1/lists/all/contacts/all?count=100";
	private long lastModified = 0l;
	private Stats stats = new Stats();

	public HubspotContactInitialSyncIterator(SyncRequest request, SyncariEntityDataRestClient restClient,ObjectMapper objectMapper){
		this.request = request;
		this.restClient = restClient;
		this.objectMapper = objectMapper;
		List<String> fieldNames = request.getEntitySchema().getAttributes().stream().map(a -> a.getApiName()).collect(Collectors.toList());
		String fieldParams =String.join("&property=", fieldNames);
		url = url + fieldParams;
	}

	public long getLastWatermark() {
		return lastModified;
	}

	@Override
	public Stats getStats() {
		return null;
	}

	@Override
	public boolean hasNext() {
		if(hasNext && consumed){
			currentPage = fetchNextPage();
		}
		return !currentPage.isEmpty();
	}

	@Override
	public List<EntityData> next() {
		consumed = true;
		return currentPage;
	}

	private List<EntityData> fetchNextPage() {
		var now = System.currentTimeMillis();

		try {
			if(vidOffset > 0l){
				url = url + "&vidOffset="+vidOffset;
			}
			ResponseEntity<String> response = restClient.getResponse(url, request.getConnector().getAuthConfig());
			var latency  = System.currentTimeMillis() - now;

			handleErrors(response);
			Map<String, Object> results = objectMapper.readValue(response.getBody(), Map.class);
			hasNext = Boolean.valueOf(results.get("has-more").toString());
			vidOffset = Long.parseLong(results.get("vid-offset").toString());

			List<Map<String, Object>> contacts = (List<Map<String, Object>>) results.get("contacts");
			consumed =false;
			var contactList= contacts.stream().map(contactMap ->{
				EntityData contact = new EntityData("contact");
				contact.setDeleted(false);
				contact.setName(request.getEntityName());

				contact.setConnectorId(request.getConnector().getId());
				if (contactMap.get("lastmodifieddate") != null) {
					contact.setIgnoreFieldChanges(Set.of("lastmodifieddate"));
					contact.setLastModified(Long.parseLong(contactMap.get("lastmodifieddate").toString()));
					lastModified= Math.max(lastModified, contact.getLastModified());
				}
				Map<String,Map<String, Object>> properties =(Map<String,Map<String, Object>>) contactMap.get("properties");
				properties.forEach((fieldName, valueMap)->{
					contact.addValue(fieldName, valueMap.get("value"));
				});
				contact.setId(contactMap.get("vid").toString());
				return contact;
			}).collect(Collectors.toList());
			stats.addLatencyCount(latency, contactList.size());
			return contactList;

		} catch (Exception e){
			throw new RetriableException("SYNCARI_ERROR",e.getMessage(),"SYNCARI_ERROR",e);
		}
	}

	private void handleErrors(ResponseEntity<String> response) {
		if(response.getStatusCode().is4xxClientError()){
			throw new NonRetriableException("BAD_REQUEST",response.getBody(),response.getStatusCode().name());
		}
		if(response.getStatusCode().is5xxServerError()){
			throw new RetriableException("SERVER_ERROR",response.getBody(),response.getStatusCode().name());
		}
	}
}
