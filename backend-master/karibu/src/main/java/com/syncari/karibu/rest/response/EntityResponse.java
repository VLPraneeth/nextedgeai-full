package com.syncari.karibu.rest.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.Tag;
import com.syncari.core.model.UUIDAuditModel;
import com.syncari.core.schema.EntityType;
import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@ToString(callSuper=true)
public class EntityResponse extends BaseKaribuResponse {
    private String synapseId;
    private String apiName;
    private String status;
    private String datastoreName;
    private String displayName;
    private String description;
    private String type;
    private boolean isReadOnly;
    private boolean isDraft;
    List<String> tags;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<FieldResponse> fields;

    @Override
    public <k extends KaribuResponse, h extends UUIDAuditModel> Object populate(h object) {
        EntityDefinition schemaDTO = (EntityDefinition) object;
        EntityResponse response = new EntityResponse();

        response.setId(schemaDTO.getId());
        response.setSynapseId(schemaDTO.getConnectorId());
        response.setApiName(schemaDTO.getApiName());
        if (null != schemaDTO.getStatus())
            response.setStatus(schemaDTO.getStatus().toString());
        response.setDatastoreName(schemaDTO.getDataStoreName());
        response.setDisplayName(schemaDTO.getDisplayName());
        response.setDescription(schemaDTO.getDescription());
        response.setType(schemaDTO.isCustom() ? EntityType.custom.name() : EntityType.standard.name());
        response.setReadOnly(schemaDTO.isReadOnly());
        response.setDraft(schemaDTO.isDraft());
        response.setTags(getTags(schemaDTO.getTags()));
        response.setCreatedBy(schemaDTO.getCreatedBy());
        response.setCreatedAt(schemaDTO.getCreatedAt());
        response.setUpdatedBy(schemaDTO.getUpdatedBy());
        response.setUpdatedAt(schemaDTO.getUpdatedAt());
        if (schemaDTO != null)
            response.setFields(getFields(schemaDTO.getAttributes()));

        return response;

    }
    
    private List<FieldResponse> getFields(List<AttributeDefinition> attributes) {
        FieldResponse field = new FieldResponse();
        List<FieldResponse> fields = new ArrayList<>();
        
        for (AttributeDefinition attribute : attributes) {
            fields.add(field.getFieldResponse(attribute));
        }
        
        return fields;
    }

    private List<String> getTags (List<Tag> tagList) {
        List<String> tags = new ArrayList<>();
        tagList.stream().forEach(t -> tags.add(t.getName()));
        return tags;
    }
}
