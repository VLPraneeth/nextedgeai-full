package com.syncari.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.syncari.core.datatype.*;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.misc.DraftableModel;
import com.syncari.core.model.misc.Partition;
import com.syncari.core.model.util.Status;
import com.syncari.core.schema.DataStoreConfig;
import com.syncari.core.utils.SchemaHelper;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.experimental.Wither;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.annotation.Transient;

import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Data
@Accessors(chain = true)
@Wither
@AllArgsConstructor
public class EntityDefinition extends DraftableModel<EntityDefinition> {
	private static final String DIGITS = "\\d.*";
    @NotNull(message = "EntityDefinition api name is required")
	String apiName;
	@NotNull(message = "EntityDefinition display name is required")
	String displayName;
	String pluralName;
	String description;
	Status status;
	String connectorId;
	String connectorTypeId;
	boolean custom;
	boolean readOnly;
	int version;
	Set<Partition> partitions = new HashSet<Partition>();
    @Setter(value=AccessLevel.PRIVATE)
	DataStoreConfig storeConfig = new DataStoreConfig();
	@JsonIgnore
	@Transient
	Map<String, AttributeDefinition> idToAttribMap = new HashMap<>();
	@JsonIgnore
	@Transient
	List<AttributeDefinition> attributes = new ArrayList<>();
	@JsonIgnore
	@Transient
	List<Tag> tags = new ArrayList<>();
	boolean seeded;
	boolean isChild;
	@JsonIgnore
	@Transient
	List<Reference> references;
	boolean syncariSource;
	boolean runDFI;
	boolean runMerge;

	@Transient
	@JsonIgnore
	Connector connector;
	private List<AttributeDefinition> sourceParams = new ArrayList<>();
	private List<AttributeDefinition> destinationParams = new ArrayList<>();

	private Map<String, Object> additionalProperties = new HashMap<>();
	@Transient
	@JsonIgnore
	private List<AttributeDefinition> activeAttributes;
	public EntityDefinition() {
	}

	public EntityDefinition(String apiName, String displayName) {
		this.apiName = apiName;
		this.displayName = displayName;
	}

	public boolean hasField(String apiName) {
		return attributes.stream().filter(f -> apiName.equalsIgnoreCase(f.apiName)).findFirst().isPresent();
	}
	
	public AttributeDefinition getFieldByName(String apiName) {
		Optional<AttributeDefinition> attributeDefinition = attributes.stream().filter(f -> apiName.equalsIgnoreCase(f.apiName)).findFirst();
		return attributeDefinition.orElseThrow(()->new SyncariValidationException("No field by name " + apiName));
	}
	
	public Optional<AttributeDefinition> getIdField() {
	    return attributes.stream().filter(f -> f.isIdField).findFirst();
	}
	
	public Optional<AttributeDefinition> getWatermarkField() {
	    return attributes.stream().filter(f -> f.isWatermarkField).findFirst();
	}

	public Optional<AttributeDefinition> getField(String apiName) {
		return attributes.stream().filter(f -> apiName.equalsIgnoreCase(f.apiName)).findFirst();
	}

    public Optional<AttributeDefinition> getFieldById(String fieldId) {
		return attributes.stream().filter(f -> fieldId.equalsIgnoreCase(f.getId())).findFirst();
	}

    public Optional<AttributeDefinition> getFieldByDatastoreName(String datastoreName) {
		return attributes.stream().filter(f -> datastoreName.equalsIgnoreCase(f.getDataStoreName())).findFirst();
	}

	public List<AttributeDefinition> getExternalIdFields() {
		return attributes.stream().filter(f -> f.getDataType() instanceof ExternalIdType).collect(Collectors.toList());
	}

	public List<AttributeDefinition> getExternalReferenceFields() {
		return attributes.stream().filter(f -> f.getDataType() instanceof ExternalReferenceType).collect(Collectors.toList());
	}

	public void addField(AttributeDefinition field) {
		attributes.add(field);
		activeAttributes = null;
		idToAttribMap.put(field.getId(),field);
	}

	public AttributeDefinition getAttribute(String attributeId) {
		if(idToAttribMap.isEmpty()){
			populateIdToAttrMap(attributes);
		}
		return idToAttribMap.get(attributeId);
	}
	
	public List<AttributeDefinition> getAttributes() {
	    return attributes == null ? new ArrayList<>() : attributes;
	}

    public List<AttributeDefinition> getFileLinkAttributes() {
        return attributes == null ? new ArrayList<>() : attributes.stream().filter(a-> a.getDataType() instanceof FileLinkType)
            .collect(Collectors.toList());
    }

	public List<AttributeDefinition> getActiveAttributes() {
		if (activeAttributes == null) {
			activeAttributes = attributes == null ? new ArrayList<>() : attributes.stream().filter(a -> a.isActive()).collect(Collectors.toList());
		}
		return activeAttributes;
	}

	public EntityDefinition setAttributes(List<AttributeDefinition> attributes) {
		idToAttribMap.clear();
		this.attributes = attributes;
		this.activeAttributes = null;
		populateIdToAttrMap(attributes);
		return this;
	}

	private void populateIdToAttrMap(List<AttributeDefinition> attributes) {
		if(attributes != null) {
		    for (AttributeDefinition a : attributes) {
		        idToAttribMap.put(a.getId(), a);
		    }
		}
	}

	@Override
	public boolean equals(Object other){
		if(other == null ||!(other instanceof EntityDefinition)) return false;
		return getId() !=null && getId().equals(((EntityDefinition)other).getId());
	}

	@Override
	public int hashCode() {
		return getId() == null ? super.hashCode() : getId().hashCode();
	}

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

	public boolean isDeleted() {
		return getStatus() == Status.DELETED;
	}

    @Override
    public EntityDefinition makeCopy() {
		EntityDefinition copy = new EntityDefinition().setApiName(apiName).setAttributes(new ArrayList<>()).setConnectorId(connectorId)
				.setConnectorTypeId(connectorTypeId).setCustom(custom).setDisplayName(displayName).setPluralName(pluralName)
				.setStatus(status).setPartitions(partitions).setDescription(description).setStoreConfig(storeConfig).setSyncariSource(syncariSource)
				.setAdditionalProperties(additionalProperties).setRunDFI(runDFI).setRunMerge(runMerge);
        copy.setDraftStatus(getDraftStatus());
        attributes.forEach(a -> {
            AttributeDefinition aCopy = a.withEntityId(null);
            aCopy.setId(null);
            copy.attributes.add(aCopy);
        });
        return copy;
    }

    @Override
    public void copyValuesFrom(EntityDefinition model) {
		setApiName(model.getApiName()).setDisplayName(model.getDisplayName()).setDescription(model.getDescription())
				.setPluralName(model.getPluralName()).setStoreConfig(model.getStoreConfig())
				.setCustom(model.isCustom()).setVersion(model.getVersion()).setPartitions(model.getPartitions())
				.setAdditionalProperties(model.getAdditionalProperties()).setRunDFI(model.isRunDFI()).setRunMerge(model.isRunMerge());

    }
    
    public Map<String, AttributeDefinition> getIdToAttributes() {
        if(idToAttribMap.isEmpty()){
			idToAttribMap = getAttributes().stream()
					.collect(Collectors.toMap(AttributeDefinition::getId, a -> a));
		}
        return idToAttribMap;
    }

    public Map<String, AttributeDefinition> getApiNameLowerCasedToAttributes() {
        return getAttributes().stream().collect(Collectors.toMap(a -> a.getApiName().toLowerCase(), a -> a));
    }
    
    public void validateWatermark() {
        Optional<AttributeDefinition> watermarkField = getWatermarkField();
        validateCondition(watermarkField.isEmpty(), i18n("watermark_missing"), getApiName());
        boolean hasMultipleWatermarks = getAttributes().stream().filter(a -> a.isWatermarkField()).count() > 1;
        validateCondition(hasMultipleWatermarks, i18n("multiple_watermark"), getApiName());

        Datatype dataType = watermarkField.get().getDataType();
        boolean invalidType = !(dataType instanceof DatetimeType
                || dataType instanceof DateType
                || dataType instanceof StringType
                || dataType instanceof IntegerType
                || dataType instanceof TimestampType);
        validateCondition(invalidType, i18n("watermark_invalid_type"), getApiName());

        long idCount = getAttributes().stream().filter(a -> a.isIdField()).count();
        validateCondition(idCount == 0, i18n("id_field_missing"), getApiName());
        validateCondition(idCount > 1, i18n("multiple_id_field"), getApiName());
    }

    /**
     * Validates that entity does not have multiple watermark fields.
     * This validation must always run regardless of whether the connector supports noWatermark capability.
     */
    public void validateMultipleWatermarks() {
        boolean hasMultipleWatermarks = getAttributes().stream().filter(a -> a.isWatermarkField()).count() > 1;
        validateCondition(hasMultipleWatermarks, i18n("multiple_watermark"), getApiName());
    }

    public String getDataStoreName() {
        String name;
        if(StringUtils.isBlank(storeConfig.getNewName())) {
            name = apiName;
        } else {
            name = storeConfig.getNewName();
        }
        return SchemaHelper.curatedDataStoreName(name);
    }

    // Use this method to get the datastore name resolved from storeconfig oldname and new name
    public String getResolvedDataStoreName(){
		return this.isDsNameAltered() && StringUtils.isBlank(this.getStoreConfig().getOldName()) ? this.getDataStoreName() :
				this.isDsNameAltered() ? this.getDataStoreName(): this.getDataStoreOldName();
	}


    public String getDataStoreOldName() {
        if(StringUtils.isBlank(storeConfig.getOldName())) {
            return SchemaHelper.curatedDataStoreName(apiName);
        } else {
            return storeConfig.getOldName();
        }
    }
    
    public EntityDefinition setDataStoreName(String name) {
        storeConfig.setNewName(name);
        return this;
    }
    
    public void resetDataStoreName(String name) {
		storeConfig.setOldName(name);
	}

	public boolean isDsNameAltered() {
		return !Objects.equals(storeConfig.getOldName(), storeConfig.getNewName());
	}

	public List<AttributeDefinition> getAlteredDsNameAttrs() {
		return attributes.stream().filter(a -> a.isDsNameAltered()).collect(Collectors.toList());
	}

	public String getDatasetId() {
		Object datasetIdObj = additionalProperties.get("datasetId");
		if (datasetIdObj == null) {
			return null;
		}
		return datasetIdObj.toString();
	}
}