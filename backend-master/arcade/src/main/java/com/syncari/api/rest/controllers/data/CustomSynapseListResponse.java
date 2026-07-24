package com.syncari.api.rest.controllers.data;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.syncari.core.draft.DraftStatus;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class CustomSynapseListResponse {
	private String id;
	private String parentId;
	private String name;
	private String displayName;
	private boolean publishToGlobal;
	@JsonProperty
	private boolean isGlobal;
	private String customSynapseType;
	private Integer entitiesCount;
	private String authenticationType;
	private String updatedBy;
	private Date updatedAt;
	private DraftStatus draftStatus;
}
