package com.syncari.connector.exception;

import lombok.Data;

@Data
public class AuthenticationException extends NonRetriableException {
    String connectorId;
    String connectorName;

    public AuthenticationException(String connectorId, String connectorName, String message) {
        super(ErrorCodes.LOGIN_ERROR, message, "401");
        this.connectorId = connectorId;
        this.connectorName = connectorName;
    }

    public AuthenticationException(String connectorId, String connectorName, String message, String statusCode) {
        super(ErrorCodes.LOGIN_ERROR, message, statusCode);
        this.connectorId = connectorId;
        this.connectorName = connectorName;
    }
}
