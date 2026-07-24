package com.syncari.karibu.rest.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.syncari.core.model.UUIDAuditModel;
import lombok.Data;
import lombok.ToString;

import java.util.List;
import java.util.Map;

@Data
@ToString(callSuper=true)
public class TransactionResponse extends BaseKaribuResponse {
    private String id;
    private String syncariId;
    private String occurredAt;
    private String entityId;
    private String entityName;

    private String sourceId;
    private String operation;
    private String createdBy;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<Map<String, Object>> transactionDetails;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<Map<String, Object>> sources;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<Map<String, Object>> destination;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<Map<String, Object>> errors;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<Map<String, Object>> mergeDetails;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> winningRecord;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<Map<String, Object>> losingRecords;


    @Override
    public <k extends KaribuResponse, h extends UUIDAuditModel> Object populate(h object) {
        return null;
    }
}
