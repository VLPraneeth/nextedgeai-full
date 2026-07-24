package com.syncari.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.syncari.core.datatype.*;
import com.syncari.core.model.misc.DraftableModel;
import com.syncari.core.model.util.Status;
import com.syncari.core.schema.DataStoreConfig;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import lombok.experimental.Wither;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.annotation.Transient;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
@Wither
@Accessors(chain = true)
@SuperBuilder(toBuilder = true)
@Slf4j
public class AttributeDefinition extends DraftableModel<AttributeDefinition> {
    @NotNull(message = "Attribute entity id is required")
    String entityId;
    @NotNull(message = "Attribute api name is required")
    String apiName;
    @NotNull(message = "Attribute display name is required")
    String displayName;
    @NotNull(message = "Attribute data type is required")
    String dataType;
    String description;
    int length;
    int precision;
    int scale;
    int index;
    String referenceTo;
    String referenceTargetField;
    String referenceToPluralName;
    String defaultValue;
    boolean custom;
    boolean nillable = true;
    boolean initializable = true;
    boolean updatable = true;
    boolean createOnly = false;
    boolean calculated;
    boolean unique;
    Status status;
    boolean isSystem;
    boolean isIdField;
    String compositeKey;
    boolean isWatermarkField;
    @JsonIgnore
    boolean isNoTimezoneWatermark;
    boolean isCreatedAtField;
    boolean isUpdatedAtField;
    private boolean isMultiValueField; // ex: lists
    boolean isSyncariDefined;
    String parentAttributeId;
    String externalId;
    boolean seeded;

    // schema
    boolean schemaUpdatable;
    boolean schemaDeletable;

    @Deprecated
    private List<String> picklistValues = new ArrayList<String>(); // for all newer implementations use picklist
    private List<Picklist> picklist = new ArrayList<>();
    @Getter(value=AccessLevel.NONE)
    @Setter(value=AccessLevel.NONE)
    DataStoreConfig storeConfig = new DataStoreConfig();
    @Transient
    List<Tag> tags = new ArrayList<>();
    int dfiWeight;
    // Specifies the actual data type if datatype is picklist
    private String subDataType;


    @Data
    @Accessors(chain = true)
    public static class Picklist {
        private String id;
        private String label;
    }

    public AttributeDefinition() {
    }

    public Datatype getDataType() {
        return DatatypeFactory.getDatatype(dataType);
    }

    public AttributeDefinition setDataType(Datatype datatype) {
        this.dataType = datatype.getName();
        return this;
    }

    public boolean isActive() {
        return getStatus() == Status.ACTIVE;
    }

    public boolean isDeleted() {
        return getStatus() == Status.DELETED;
    }

    public boolean isReference() {
        return ReferenceType.NAME.equalsIgnoreCase(dataType) || PolymorphicReferenceType.NAME.equalsIgnoreCase(dataType);
    }

    public boolean isReferenceTo(String entityDefinitionName) {
        return isReference() && (referenceTo == null ? "" : referenceTo).equalsIgnoreCase(entityDefinitionName);
    }

    public boolean isChildField(){
        return parentAttributeId != null;
    }

    public boolean hasDefaultValue(){
        return defaultValue!=null;
    }
    public String getReferencedAttributeName() {
        return referenceTargetField == null ? "Id" : referenceTargetField;
    }

    @Override
    public AttributeDefinition makeCopy() {
        AttributeDefinition copy = new AttributeDefinition();
        copy.copyValuesFrom(this);
        copy.setEntityId(this.getEntityId());
        copy.setId(null);
        return copy;
    }

    @Override
    public void copyValuesFrom(AttributeDefinition model) {
        this.setApiName(model.getApiName()).setDisplayName(model.getDisplayName()).setDataType(model.getDataType())
                .setDescription(model.getDescription()).setStatus(model.getStatus())
                .setLength(model.getLength())
                .setDataStoreName(model.getDataStoreName())
                .setPrecision(model.getPrecision()).setScale(model.getScale()).setReferenceTo(model.getReferenceTo())
                .setReferenceTargetField(model.getReferenceTargetField()).setDefaultValue(model.getDefaultValue())
                .setCustom(model.isCustom()).setNillable(model.isNillable()).setInitializable(model.isInitializable())
                .setUpdatable(model.isUpdatable()).setCalculated(model.isCalculated()).setUnique(model.isUnique())
                .setSystem(model.isSystem()).setIdField(model.isIdField()).setWatermarkField(model.isWatermarkField())
                .setCreatedAtField(model.isCreatedAtField()).setUpdatedAtField(model.isUpdatedAtField())
                .setMultiValueField(model.isMultiValueField()).setExternalId(model.getExternalId()).setCompositeKey(model.getCompositeKey())
                .setSeeded(model.isSeeded()).setPicklistValues(model.getPicklistValues()).setPicklist(model.getPicklist())
                .setParentAttributeId(model.getParentAttributeId()).setSyncariDefined(model.isSyncariDefined())
                .setCreateOnly(model.isCreateOnly())
                .setSubDataType(model.getSubDataType())
                .setIndex(model.getIndex())
        ;
    }

    public boolean compare(AttributeDefinition model) {
        return Objects.equals(this.getApiName(), model.getApiName()) && Objects.equals(this.getDisplayName(), model.getDisplayName())
                && Objects.equals(this.getDataType(), model.getDataType()) && Objects.equals(this.getDescription(), model.getDescription())
                && Objects.equals(this.getStatus(), model.getStatus()) && Objects.equals(this.getLength(), model.getLength())
                && Objects.equals(this.getDataStoreName(), model.getDataStoreName()) && Objects.equals(this.getPrecision(), model.getPrecision())
                && Objects.equals(this.getScale(), model.getScale()) && Objects.equals(this.getReferenceTo(), model.getReferenceTo())
                && Objects.equals(this.getReferenceTargetField(), model.getReferenceTargetField())
                && Objects.equals(this.getDefaultValue(), model.getDefaultValue()) && Objects.equals(this.isCustom(), model.isCustom())
                && Objects.equals(this.isNillable(), model.isNillable()) && Objects.equals(this.isInitializable(), model.isInitializable())
                && Objects.equals(this.isUpdatable(), model.isUpdatable()) && Objects.equals(this.isCalculated(), model.isCalculated())
                && Objects.equals(this.isUnique(), model.isUnique()) && Objects.equals(this.isSystem(), model.isSystem())
                && Objects.equals(this.isIdField(), model.isIdField()) && Objects.equals(this.isWatermarkField(), model.isWatermarkField())
                && Objects.equals(this.isCreatedAtField(), model.isCreatedAtField()) && Objects.equals(this.isUpdatedAtField(), model.isUpdatedAtField())
                && Objects.equals(this.isMultiValueField(), model.isMultiValueField()) && Objects.equals(this.getExternalId(), model.getExternalId())
                && Objects.equals(this.getCompositeKey(), model.getCompositeKey()) && Objects.equals(this.isSeeded(), model.isSeeded())
                && Objects.equals(this.getPicklistValues(), model.getPicklistValues()) && Objects.equals(this.getPicklist(), model.getPicklist())
                && Objects.equals(this.getParentAttributeId(), model.getParentAttributeId()) && Objects.equals(this.isSyncariDefined(), model.isSyncariDefined())
                && Objects.equals(this.isCreateOnly(), model.isCreateOnly()) && Objects.equals(this.getSubDataType(), model.getSubDataType())
                && Objects.equals(this.getIndex(), model.getIndex()) && Objects.equals(this.isNoTimezoneWatermark(), model.isNoTimezoneWatermark());
    }

    public boolean isDsNameAltered() {
        return !Objects.equals(storeConfig.getOldName(), storeConfig.getNewName());
    }
    
    public String getDataStoreName() {
        if(StringUtils.isBlank(storeConfig.getNewName())) {
            return apiName;
        } else {
            if (isNewDataStoreNameCorrect()){
                return storeConfig.getNewName();
            }else{
                return apiName;
            }
        }
    }

    private boolean isNewDataStoreNameCorrect(){
        if (StringUtils.isNotEmpty(storeConfig.getNewName())){
            if (!storeConfig.getNewName().equals(apiName)){
                // split api name and then check again if newname is same or not
                String [] arr = apiName.split("_");
                if ((null != arr) && (arr.length > 0) && arr[0].equals(storeConfig.getNewName())){
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public AttributeDefinition setDataStoreName(String name) {
        storeConfig.setNewName(name);
        return this;
    }
    
    public String getDataStoreOldName() {
        if(StringUtils.isBlank(storeConfig.getOldName())) {
            return apiName;
        } else {
            return storeConfig.getOldName();
        }
    }
    
    public void resetDataStoreName(String name) {
        storeConfig.setOldName(name);
    }

    public Object convert(Object value) {
        try {
            Object converted;
            if (isMultiValueField()) {
                if (isList(value)) {
                    converted = List.class.cast(value).stream()
                            .map(v -> getDataType().convert(v))
                            .collect(Collectors.toList());
                } else {
                    converted = value == null ? null : asList(value);
                }
            } else {
				if (isList(value) && !dataType.equalsIgnoreCase(ListType.NAME)) {
					List list = (List) value;
					return list.isEmpty() ? null : getDataType().convert(list.get(0));
				}
                converted = value == null ? null : getDataType().convert(value);
            }
            return converted;
        } catch (Exception e) {
            log.error("Conversion error. Could not convert value {} to datatype {} for field {} error {}", value, getDataType().getName(), getApiName(), e.getMessage());
            log.debug(e.getMessage(), e);
        }
        return value;

    }

	private boolean isList(Object value) {
		return value != null && List.class.isAssignableFrom(value.getClass());
	}

    private List<Object> asList(Object value) {
        Object converted = getDataType().convert(value);
        if(converted==null){
            return List.of();
        }
        return List.of(converted);
    }

    /**
     * Check if the attribute is of child type - different from isChildField, which defines the field hierarchy. The value
     * for rhis field will be an EntityData object
     * @return true if datatype is ChildType
     */
    public boolean isChild(){
        return ChildType.VALUE.equals(getDataType());
    }

    public Datatype getSubDataType() {
        return StringUtils.isNotBlank(subDataType) ? DatatypeFactory.getDatatype(subDataType) : null;
    }

    public AttributeDefinition setSubDataType(Datatype subDataType) {
        this.subDataType = (subDataType != null) ? subDataType.getName() : null;
        return this;
    }

    public boolean isExternalIdType() {
        return ExternalIdType.VALUE.equals(getDataType());
    }

    public void markExternal(AttributeDefinition fromField){
        if (fromField.isIdField()){
            this.setDataType(ExternalIdType.VALUE);
        }
        this.setReferenceTo(fromField.getEntityId());
        this.setReferenceTargetField(fromField.getId());
        this.setSystem(true);
        this.setSyncariDefined(true);
        this.setStatus(Status.ACTIVE);
        this.setUpdatable(false);
        this.setSchemaUpdatable(false);
        this.setSchemaDeletable(true);
    }
    public void markReference(AttributeDefinition fromField){
        if (fromField.isReference()) {
            this.setDataType(ExternalReferenceType.VALUE);
        }
        this.setReferenceTo(fromField.getEntityId());
        this.setReferenceTargetField(fromField.getId());
        this.setSystem(true);
        this.setSyncariDefined(true);
        this.setStatus(Status.ACTIVE);
        this.setUpdatable(false);
        this.setSchemaUpdatable(false);
        this.setSchemaDeletable(true);
        }
}