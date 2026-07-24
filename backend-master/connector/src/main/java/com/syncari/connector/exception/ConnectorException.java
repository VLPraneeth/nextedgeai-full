package com.syncari.connector.exception;

import lombok.Data;

@Data
public abstract class ConnectorException extends RuntimeException {
	String errorCode;
	String statusCode;

	public ConnectorException(String errorCode, String message, String statusCode) {
		super(message);
		this.errorCode = errorCode;
		this.statusCode = statusCode;
	}
	public ConnectorException(String errorCode, String message, String statusCode,Exception e) {
		super(message, e);
		this.errorCode=errorCode;
		this.statusCode=statusCode;

	}

	public String toString(){
		return String.format("%s ErrorCode:%s,StatusCode:%s,ErrorMessage:%s",getClass().getName(),errorCode,statusCode,getMessage());
	}

}
