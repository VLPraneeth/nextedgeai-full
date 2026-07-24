package com.syncari.karibu.rest.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.ToString;

@Data
@ToString(callSuper=true)
public class ErrorResponse {

    private String errorType;
    private String synapseId;
    private String synapseName;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String syncariEntityName;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String externalEntityName;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String syncariRecordId;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String externalRecordId;
    private String operation;
    private String error;
    private String errorDetail;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String occurredAt;

}
