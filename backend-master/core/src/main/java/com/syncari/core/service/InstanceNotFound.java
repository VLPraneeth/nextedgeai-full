package com.syncari.core.service;

public class InstanceNotFound extends RuntimeException{
    public InstanceNotFound(String message, Throwable cause) {
        super(message, cause);
    }
    public InstanceNotFound(String message) {
        super(message);
    }
}
