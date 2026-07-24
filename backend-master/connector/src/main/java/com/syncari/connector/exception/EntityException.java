package com.syncari.connector.exception;

import lombok.Data;

@Data
public class EntityException extends NonRetriableException {
    final String entityName;
    final String connectorName;

    public EntityException(String connectorName, String entityName, ErrorCodes errorCode, String statusCode, String message) {
        super(errorCode, message, statusCode);
        this.connectorName = connectorName;
        this.entityName = entityName;
    }

}
