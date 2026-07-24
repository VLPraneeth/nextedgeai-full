package com.syncari.core.model;

import java.util.HashMap;
import java.util.Map;

import javax.validation.constraints.NotNull;

import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document
public class Resource extends UUIDAuditModel {
	@NotNull(message = "Resource Type is required")
	private ResourceType type;
	private Map<String, String> configuration = new HashMap<>();

	public Resource(ResourceType type) {
		this.type = type;
	}

	public Resource() {
	}

}
