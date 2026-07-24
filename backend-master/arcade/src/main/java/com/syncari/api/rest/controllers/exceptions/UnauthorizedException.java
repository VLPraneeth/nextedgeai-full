package com.syncari.api.rest.controllers.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.UNAUTHORIZED, reason = "Unauthorized User")
public class UnauthorizedException extends RuntimeException{

    public UnauthorizedException(Throwable t){
        super(t);
    }
    public UnauthorizedException(String message){
        super(message);
    }

    public UnauthorizedException(String message, Object... params){
        super(String.format(message,params));
    }
}
