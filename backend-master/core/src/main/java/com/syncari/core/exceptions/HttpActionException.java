package com.syncari.core.exceptions;

import org.springframework.http.RequestEntity;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class HttpActionException extends RuntimeException {

	private static final long serialVersionUID = 3460220621512201061L;
	private RequestEntity<String> request;

	public HttpActionException(Throwable cause) {
		super(cause);
	}
	
	public HttpActionException(String message) {
		super(message);
	}
	
	public HttpActionException(String message, Throwable cause) {
		super(message, cause);
	}
}
