package com.syncari.connector.exception;

public class NotSupportedException extends RuntimeException {

    public NotSupportedException(Throwable cause) {
        super(cause);
    }

    public NotSupportedException(String message) {
        super(message);
    }
}
