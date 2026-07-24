package com.syncari.connector.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.syncari.connector.ConnectorInfo;

import lombok.Data;

@Data
public class DescribeAllRequest {
	List<String> entities = new ArrayList<>();
	List<EntitySchema> existing = new ArrayList<>();
	List<String> activeEntities = new ArrayList<>();
	private ConnectorInfo connector;
	private boolean forceRefresh;

	public DescribeAllRequest(ConnectorInfo connector, List<String> entities) {
		this.connector = connector;
		this.entities = entities;
	}

	public Optional<EntitySchema> getExistingEntity(String apiName) {
		return existing.stream().filter(e -> e.getApiName().equalsIgnoreCase(apiName)).findFirst();
	}

}
