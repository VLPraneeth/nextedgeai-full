package com.syncari.core.model.misc;

import com.syncari.core.model.util.SyncDirection;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class FieldMapping {

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

    SyncDirection direction;
    String error;

}
