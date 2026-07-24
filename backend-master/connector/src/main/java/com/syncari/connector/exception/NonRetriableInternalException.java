package com.syncari.connector.exception;

public class NonRetriableInternalException extends NonRetriableException {

    public NonRetriableInternalException(String errorCode, String statusCode, String message, Exception e) {
        super(errorCode, message, statusCode, e);
    }
    
    public NonRetriableInternalException(String errorCode, String statusCode, String message) {
        super(errorCode, message, statusCode);
    }
    
    public NonRetriableInternalException(ErrorCodes errorCode, String message, String statusCode, Exception cause) {
		this(errorCode.name(), message, statusCode, cause);
	}

}
