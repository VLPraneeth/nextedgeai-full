package com.syncari.connector.exception;

public class UnknownException extends ConnectorException {

	public UnknownException(String message) {
		super("UNKNOWN_EXCEPTION", message, message);
	}
	
	public UnknownException(String message, Exception e) {
		super("UNKNOWN_EXCEPTION", message, message, e);
	}

}
