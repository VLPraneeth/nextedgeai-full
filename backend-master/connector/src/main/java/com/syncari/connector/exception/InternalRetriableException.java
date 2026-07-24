package com.syncari.connector.exception;

public class InternalRetriableException extends RetriableException{

    public InternalRetriableException(String errorCode, String message, String statusCode) {
        super(errorCode, message, statusCode);
    }
}
