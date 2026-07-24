package com.syncari.api.rest.controllers.exceptions;


import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = "Validation errors found")
public class BadRequestException extends RuntimeException {
    public BadRequestException(Throwable t){
        super(t);
    }
    public BadRequestException(String message){
        super(message);
    }

    public BadRequestException(String message, Object... params){
        super(String.format(message,params));
    }

}
