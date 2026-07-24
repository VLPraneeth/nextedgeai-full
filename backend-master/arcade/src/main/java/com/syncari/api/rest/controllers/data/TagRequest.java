package com.syncari.api.rest.controllers.data;

import com.syncari.core.model.misc.Taggable;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TagRequest {
	private String name;
	private Object value;
	private Taggable type;
	private String taggedId;
	
	public TagRequest() {}
}
