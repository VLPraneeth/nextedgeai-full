package com.syncari.api.core.util;

import com.syncari.api.rest.controllers.data.FieldMappingDTO;
import com.syncari.api.rest.controllers.data.FieldMappingResponse;
import com.syncari.api.rest.controllers.data.UpdateFieldMappingDTO;
import com.syncari.core.model.misc.FieldMapping;
import com.syncari.core.model.misc.UpdateFieldMappingRequest;
import com.syncari.core.model.util.SyncDirection;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class MappingTransformer {


    public List<FieldMapping> toMappingFields(List<FieldMappingDTO> mappings, String syncariEntityId) {
        return mappings.stream().map(mapping -> toMappingField(mapping, syncariEntityId)).collect(Collectors.toList());
    }

    public FieldMapping toMappingField(FieldMappingDTO mappingDTO, String syncariEntityId) {
        FieldMapping fieldMapping = new FieldMapping().setId(mappingDTO.getId())
                .setSynapseId(mappingDTO.getSynapseId())
                .setSynapseName(mappingDTO.getSynapseName())
                .setSynapseEntityId(mappingDTO.getSynapseEntityId())
                .setSynapseEntityApiName(mappingDTO.getSynapseEntityApiName())
                .setSynapseEntityDisplayName(mappingDTO.getSynapseEntityDisplayName())
                .setSynapseFieldId(mappingDTO.getSynapseFieldId())
                .setSynapseFieldApiName(mappingDTO.getSynapseFieldApiName())
                .setSynapseFieldDisplayName(mappingDTO.getSynapseFieldDisplayName())
                .setSynapseFieldDatatype(mappingDTO.getSynapseFieldDatatype())
                .setSyncariEntityId(syncariEntityId)
                .setSyncariFieldId(mappingDTO.getSyncariFieldId())
                .setSyncariFieldApiName(mappingDTO.getSyncariFieldApiName())
                .setSyncariFieldDisplayName(mappingDTO.getSyncariFieldDisplayName())
                .setSyncariFieldDatatype(mappingDTO.getSyncariFieldDatatype())
                .setCreateNewSyncariField(mappingDTO.isCreateNewSyncariField())
                .setDirection(retrieveSyncDirection(mappingDTO.getDirections()))
                .setSyncariFieldIsMultiValued(mappingDTO.isSyncariFieldIsMultiValued())
                .setSyncariFieldIsRequired(mappingDTO.isSyncariFieldIsRequired());
        return fieldMapping;
    }

    public List<FieldMappingDTO> toMappingFieldDTOs(List<FieldMapping> mappings) {
        return mappings.stream().map(mapping -> toMappingFieldDTO(mapping)).collect(Collectors.toList());
    }

    public FieldMappingDTO toMappingFieldDTO(FieldMapping mapping) {
        FieldMappingDTO fieldMappingDTO = new FieldMappingDTO().setId(mapping.getId())
                .setSynapseId(mapping.getSynapseId())
                .setSynapseName(mapping.getSynapseName())
                .setSynapseEntityId(mapping.getSynapseEntityId())
                .setSynapseEntityApiName(mapping.getSynapseEntityApiName())
                .setSynapseEntityDisplayName(mapping.getSynapseEntityDisplayName())
                .setSynapseFieldId(mapping.getSynapseFieldId())
                .setSynapseFieldApiName(mapping.getSynapseFieldApiName())
                .setSynapseFieldDisplayName(mapping.getSynapseFieldDisplayName())
                .setSynapseFieldDatatype(mapping.getSynapseFieldDatatype())
                .setSyncariFieldId(mapping.getSyncariFieldId())
                .setSyncariFieldApiName(mapping.getSyncariFieldApiName())
                .setSyncariFieldDisplayName(mapping.getSyncariFieldDisplayName())
                .setSyncariFieldDatatype(mapping.getSyncariFieldDatatype())
                .setCreateNewSyncariField(mapping.isCreateNewSyncariField())
                .setDirections(retrieveMappingDirections(mapping.getDirection()));
        return fieldMappingDTO;
    }

    public FieldMappingResponse toFieldMappingResponse(List<FieldMapping> mappings){

        FieldMappingResponse  response = new FieldMappingResponse();
        List<FieldMapping> errorMappings = mappings.stream().filter(m -> !StringUtils.isBlank(m.getError())).collect(Collectors.toList());
        if(errorMappings.isEmpty()){
            // successful
            response.setSuccess(true);
            mappings.forEach(m -> response.addResult(toMappingFieldDTO(m)));
        } else {
            // failed
            response.setSuccess(false);
            errorMappings.forEach(m -> response.addError(m.getId(), m.getError()));
        }

        return response;
    }

    public UpdateFieldMappingRequest toUpdateFieldMappingRequest(UpdateFieldMappingDTO updateFieldMappingDTO, String syncariEntityId){
        UpdateFieldMappingRequest req = new UpdateFieldMappingRequest();
        req.setExisting(toMappingField(updateFieldMappingDTO.getExisting(), syncariEntityId));
        req.setUpdated(toMappingField(updateFieldMappingDTO.getUpdated(), syncariEntityId));
        return req;
    }

    public SyncDirection retrieveSyncDirection(List<FieldMappingDTO.MappingDirection> directions) {
        if(directions == null || directions.isEmpty()) return null;
        if(directions.contains(FieldMappingDTO.MappingDirection.SYNC_FROM) &&
                directions.contains(FieldMappingDTO.MappingDirection.SYNC_TO)){
            return SyncDirection.BIDI;
        } else if (directions.contains(FieldMappingDTO.MappingDirection.SYNC_FROM)){
            return SyncDirection.INBOUND;
        } else if(directions.contains(FieldMappingDTO.MappingDirection.SYNC_TO)){
            return SyncDirection.OUTBOUND;
        } else {
            throw new RuntimeException(String.format("Unknown Mapping Directions: %s", directions.toString()));
        }
    }

    public List<FieldMappingDTO.MappingDirection> retrieveMappingDirections(SyncDirection direction) {
        if(direction == null) return List.of();
        switch (direction){
            case BIDI:
                return List.of(FieldMappingDTO.MappingDirection.SYNC_FROM, FieldMappingDTO.MappingDirection.SYNC_TO);
            case INBOUND:
                return List.of(FieldMappingDTO.MappingDirection.SYNC_FROM);
            case OUTBOUND:
                return List.of(FieldMappingDTO.MappingDirection.SYNC_TO);
            default:
                throw new RuntimeException(String.format("Invalid Sync Direction: %s", direction));
        }

    }

}
