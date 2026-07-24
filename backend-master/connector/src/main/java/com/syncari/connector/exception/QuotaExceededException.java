package com.syncari.connector.exception;

import lombok.Data;

@Data
public class QuotaExceededException extends ConnectorException {

    String connectorId;
    long tryInSeconds = 0;

    public QuotaExceededException(String errorCode, String message, String statusCode, String connectorId, long tryInseconds){
        super(errorCode, message, statusCode);
        this.connectorId = connectorId;
        this.tryInSeconds = tryInseconds;
    }
}
