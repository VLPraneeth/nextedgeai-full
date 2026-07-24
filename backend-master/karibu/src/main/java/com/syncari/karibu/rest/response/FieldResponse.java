package com.syncari.karibu.rest.response;

import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Tag;
import com.syncari.core.model.UUIDAuditModel;
import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@ToString(callSuper=true)
public class FieldResponse extends BaseKaribuResponse {
    private String entityId;
    private String apiName;
    private String status;
    private String datastoreName;
    private String displayName;
    private String description;
    private String compositeKey;
    private String dataType;
    private String parentAttributeId;
    private String referenceTargetField;
    private String referenceTo;
    private int length;
    private int precision;
    private boolean custom;
    private boolean calculated;
    private boolean createOnly;
    private boolean idField;
    private boolean multiValueField;
    private boolean readOnly;
    private boolean required;
    private boolean syncariDefined;
    private boolean system;
    private boolean unique;
    private boolean watermarkField;
    private boolean isDraft;
    private List<String> picklistValues;
    List<String> tags;

    @Override
    public <k extends KaribuResponse, h extends UUIDAuditModel> Object populate(h object) {
        AttributeDefinition fieldDTO = (AttributeDefinition) object;
        return populateFieldResponse(fieldDTO);
    }

    public FieldResponse getFieldResponse (AttributeDefinition fieldDTO) {
        return populateFieldResponse(fieldDTO);
    }

    private FieldResponse populateFieldResponse (AttributeDefinition fieldDTO) {
        FieldResponse response = new FieldResponse();

        response.setId(fieldDTO.getId());
        response.setEntityId(fieldDTO.getEntityId());
        response.setApiName(fieldDTO.getApiName());
        response.setStatus(fieldDTO.getStatus().toString());
        response.setDatastoreName(fieldDTO.getDataStoreName());
        response.setDisplayName(fieldDTO.getDisplayName());
        response.setDescription(fieldDTO.getDescription());
        response.setCompositeKey(fieldDTO.getCompositeKey());
        response.setDataType(fieldDTO.getDataType().getName());
        response.setParentAttributeId(fieldDTO.getParentAttributeId());
        response.setReferenceTargetField(fieldDTO.getReferenceTargetField());
        response.setReferenceTo(fieldDTO.getReferenceTo());
        response.setLength(fieldDTO.getLength());
        response.setPrecision(fieldDTO.getPrecision());
        response.setCustom(fieldDTO.isCustom());
        response.setCalculated(fieldDTO.isCalculated());
        response.setCreateOnly(fieldDTO.isCreateOnly());
        response.setIdField(fieldDTO.isIdField());
        response.setMultiValueField(fieldDTO.isMultiValueField());
        response.setReadOnly(!fieldDTO.isUpdatable());
        response.setRequired(!fieldDTO.isNillable());
        response.setSyncariDefined(fieldDTO.isSyncariDefined());
        response.setSystem(fieldDTO.isSystem());
        response.setUnique(fieldDTO.isUnique());
        response.setWatermarkField(fieldDTO.isWatermarkField());
        response.setDraft(fieldDTO.isDraft());
        response.setPicklistValues(fieldDTO.getPicklistValues());
        response.setTags(getTags(fieldDTO.getTags()));
        response.setCreatedBy(fieldDTO.getCreatedBy());
        response.setCreatedAt(fieldDTO.getCreatedAt());
        response.setUpdatedBy(fieldDTO.getUpdatedBy());
        response.setUpdatedAt(fieldDTO.getUpdatedAt());

        return response;
    }

    private List<String> getTags (List<Tag> tagList) {
        List<String> tags = new ArrayList<>();
        tagList.stream().forEach(t -> tags.add(t.getName()));
        return tags;
    }

}
