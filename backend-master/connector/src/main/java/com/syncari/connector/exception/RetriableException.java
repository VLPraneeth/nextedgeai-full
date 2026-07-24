package com.syncari.connector.exception;

public class RetriableException extends ConnectorException {

	public RetriableException(String errorCode, String message, String statusCode) {
		super(errorCode, message, statusCode);
	}
	public RetriableException(String errorCode, String message, String statusCode,Exception e) {
		super(errorCode, message, statusCode,e);
	}

	public RetriableException(ErrorCodes errorCode, String message, String statusCode) {
		super(errorCode.name(), message, statusCode);
	}

	public RetriableException(ErrorCodes errorCode, String message, String statusCode, Exception e) {
		super(errorCode.name(), message, statusCode, e);
	}

	public String toString(){
		return super.toString();
	}
}
