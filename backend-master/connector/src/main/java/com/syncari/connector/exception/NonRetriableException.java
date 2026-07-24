package com.syncari.connector.exception;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class NonRetriableException extends ConnectorException {

	public NonRetriableException(String errorCode, String message, String statusCode) {
		super(errorCode, message, statusCode);
	}
	public NonRetriableException(ErrorCodes errorCode, String message, String statusCode) {
		super(errorCode.name(), message, statusCode);
	}

    public NonRetriableException(String errorCode, String message, String statusCode, Exception cause) {
        super(errorCode, message, statusCode, cause);
	}

    public NonRetriableException(ErrorCodes errorCode, String message, String statusCode, Exception cause) {
		super(errorCode.name(), message, statusCode, cause);
	}

	public String toString(){
		return super.toString();
	}
}
