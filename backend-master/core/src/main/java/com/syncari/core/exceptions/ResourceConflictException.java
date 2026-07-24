package com.syncari.core.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = "Validation errors found")
public class ResourceConflictException extends RuntimeException{
    public ResourceConflictException(Throwable t){
        super(t);
    }
    public ResourceConflictException(String message){
        super(message);
    }

    public ResourceConflictException(String message, Object... params){
        super(String.format(message,params));
    }
}
