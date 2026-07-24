package com.syncari.connector.exception;

import org.springframework.http.HttpStatus;

import lombok.Data;

@Data
public class HttpException extends RuntimeException {
	HttpStatus status;
	
	public HttpException(String message, HttpStatus status) {
		super(message);
		this.status = status;
	}
}
