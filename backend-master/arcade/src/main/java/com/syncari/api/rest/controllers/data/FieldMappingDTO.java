package com.syncari.api.rest.controllers.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FieldMappingDTO {

    String id;
    String syncariEntityId;

    String synapseId;
    String synapseName;

    String synapseEntityId;
    String synapseEntityApiName;
    String synapseEntityDisplayName;

    String synapseFieldId;
    String synapseFieldApiName;
    String synapseFieldDisplayName;
    String synapseFieldDatatype;

    boolean createNewSyncariField;
    String syncariFieldId;
    String syncariFieldApiName;
    String syncariFieldDisplayName;
    String syncariFieldDatatype;
    boolean syncariFieldIsRequired;
    boolean syncariFieldIsMultiValued;

    List<MappingDirection> directions;

    public enum MappingDirection {
        SYNC_FROM,
        SYNC_TO
    }
}
