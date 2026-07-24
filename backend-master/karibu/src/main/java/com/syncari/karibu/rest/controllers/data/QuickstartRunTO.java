package com.syncari.karibu.rest.controllers.data;

import com.syncari.utils.KeyValue;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class QuickstartRunTO {
    private String qsSynapseName;
    private String qsSynapseId;
    private String qsEntityApiName;
    private String qsEntityId;
    private String qsFieldApiName;
    private String qsFieldId;
    private String synapseName;
    private String synapseId;
    private String entityApiName;
    private String entityId;
    private String fieldApiName;
    private String fieldId;

    public QuickstartRunTO() {

    }
}
