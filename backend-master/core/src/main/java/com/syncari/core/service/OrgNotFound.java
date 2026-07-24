package com.syncari.core.service;

public class OrgNotFound extends RuntimeException{
    public OrgNotFound(String message, Throwable cause) {
        super(message, cause);
    }
    public OrgNotFound(String message) {
        super(message);
    }
}
