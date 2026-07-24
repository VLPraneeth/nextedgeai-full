package com.syncari.connector.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.syncari.connector.Status;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Data
@Accessors(chain = true)
public class AttributeSchema {
    String id;
    String apiName;
	String displayName;
	String description;
	String dataType;
	boolean custom;
	String defaultValue;
	boolean nillable = true;
	boolean initializable = true;
	boolean updateable = true;
	boolean createOnly;
	boolean calculated;
	boolean unique;
	int length;
	int precision;
	int scale;
	int index;
	Status status;
	String referenceTo;
	String referenceTargetField;
    String referenceToPluralName;
	@JsonProperty
	private boolean isSystem;
	@JsonProperty
	private boolean isIdField;
	private String compositeKey;
	@JsonProperty
	private boolean isWatermarkField;
	@JsonIgnore
	private boolean isNoTimezoneWatermark;
	@JsonProperty
	private boolean isCreatedAtField;
	@JsonProperty
	private boolean isUpdatedAtField;
	@JsonProperty
	private boolean isMultiValueField; // ex: lists
	boolean isSyncariDefined;
	String parentAttributeId;
	String externalId;
	String entityId;
	//if the datatype is child, we expect this to be set
	EntitySchema childSchema;
	@Deprecated
	private List<String> picklistValues = new ArrayList<String>(); // for all newer implementations use picklist
	private List<Picklist> picklist = new ArrayList<>();
	// Specifies the actual data type if datatype is picklist
	private String subDataType;
	private static final Set<String> TEMPORAL_TYPES = Set.of("date","datetime","timestamp");
	public AttributeSchema() {}
	public static final String COMPOSITE_KEY_DELIMETER = "|";

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Accessors(chain = true)
	public static class Picklist {
		private String id;
		private String label;
	}

	public AttributeSchema(String apiName, String dataType) {
		this.apiName = apiName;
		this.dataType = dataType;
	}

	public boolean isReference(){
		return "reference".equalsIgnoreCase(dataType) || "polymorphicreference".equals(dataType);
	}

	public boolean isTemporalType(){
		return TEMPORAL_TYPES.contains(dataType.toLowerCase());
	}

	public boolean isChildField(){
		return parentAttributeId != null;
	}

    public boolean isFileLink() {
        return "filelink".equalsIgnoreCase(dataType);
    }

	public String composeKeys() {
		return apiName+COMPOSITE_KEY_DELIMETER+getCompositeKey();
	}

	public boolean isCompositeKey() {
		return isIdField() && !StringUtils.isBlank(getCompositeKey());
	}
}
