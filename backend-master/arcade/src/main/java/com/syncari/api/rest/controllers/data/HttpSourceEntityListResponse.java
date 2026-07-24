package com.syncari.api.rest.controllers.data;

import java.util.Date;
import java.util.List;

import com.syncari.utils.KeyValue;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class HttpSourceEntityListResponse {
	private String id;
	private String metaId;
	private String apiName;
	private String displayName;
	private String endpoint;
	private String method;
	private String updatedBy;
	private Date updatedAt;
	private List<KeyValue> usedInPipeline;
	private List<KeyValue> usedInPublishedPipeline;
}
