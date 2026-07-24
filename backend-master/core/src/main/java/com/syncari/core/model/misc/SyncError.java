package com.syncari.core.model.misc;

import java.time.Instant;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Builder
@Accessors(chain = true)
public class SyncError {
    private String connectorId;
    private String connectorName;
    private String batchId;
    private String syncariEntityName;
    private String externalEntityName;
    private String operation;
    private String errorCode;
    private String errorDetails;
    private String syncariRecordId;
    private String externalRecordId;
    Instant occuredTime;

    public SyncError() {
    }

    public SyncError(String connectorId, String connectorName, String batchId, String syncariEntityName,
            String externalEntityName, String operation, String errorCode, String errorDetails, String syncariRecordId,
            String externalRecordId, Instant occured) {
        this.connectorId = connectorId;
        this.connectorName = connectorName;
        this.batchId = batchId;
        this.syncariEntityName = syncariEntityName;
        this.externalEntityName = externalEntityName;
        this.operation = operation;
        this.errorCode = errorCode;
        this.errorDetails = errorDetails;
        this.syncariRecordId = syncariRecordId;
        this.externalRecordId = externalRecordId;
        occuredTime = occured;
    }
}
