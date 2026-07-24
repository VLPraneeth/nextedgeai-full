package com.syncari.api.rest.controllers.data;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class FieldMapping {
	private String syncariEntityId;
	private String synapseEntityId;
	private String synapseFieldId;
	private String synapseFieldName;
	private String synapseApiName;
	private List<String> selectedConnectorIds = new ArrayList<>();
	private String graphDraftId;
	private String dataType;
	private String referenceEntityId;
}
