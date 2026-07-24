package com.syncari.core.schema;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.util.Status;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AttributeDef {
	String id;
	String apiName;
	String displayName;
	String dataStoreName;
	String description;
	String dataType;
	Status status;
	EntityType type;
	boolean isIdField;
	String compositeKey;
	boolean isMultiValueField;
	Set<String> tags = new HashSet<>();
	List<String> values = new ArrayList<String>();
	boolean isMapped;
	boolean hasChanges;
	DraftStatus draftStatus;
	boolean readOnly;
	boolean createOnly;
	boolean required;
	boolean unique;
	boolean watermarkField;
	boolean ready;
	String referenceTo;
	String referenceTargetField;
	boolean isSyncariDefined;
	boolean isSystem;
	String parentAttributeId;
	int length;
    int precision;
    int scale;
	int index;
	// Specifies the actual data type if datatype is picklist
	String subDataType;
	boolean schemaUpdatable;
	boolean schemaDeletable;
	boolean hasPublishedPipeline;
	PipelineStatus pipelineStatus;

    public boolean getIsMapped() {
	    return isMapped;
	}

	public boolean getIsSyncariDefined() {
		return isSyncariDefined;
	}

	public boolean isReference() {
	    return referenceTo != null && referenceTargetField != null;
	}

    public boolean isPotentialWatermarkField() {
        // Matches the frontend datatypes validation for watermark field types.
        return List.of("datetime", "timestamp", "integer", "long", "date").contains(dataType.toLowerCase());
    }

	public AttributeDef(String id, String name) {
		this.id = id;
		this.apiName = name;
	}

	public AttributeDef() {}
}
