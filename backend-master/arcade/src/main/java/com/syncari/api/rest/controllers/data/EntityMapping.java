package com.syncari.api.rest.controllers.data;
import com.syncari.utils.KeyValue;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class EntityMapping {
	private String id;
	private String name;
	private String apiName;
	private List<KeyValue> offsetFieldList = new ArrayList<>();
	private String selectedOffsetFieldId;
	private boolean isOffsetFieldReadOnly;
	private List<String> selectedConnectorIds = new ArrayList<>();
	private boolean needsOffsetField = true;
}
