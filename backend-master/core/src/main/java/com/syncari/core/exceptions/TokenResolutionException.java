package com.syncari.core.exceptions;

public class TokenResolutionException extends RuntimeException {

	private static final long serialVersionUID = 3460220621512201061L;

	public TokenResolutionException(Throwable cause) {
		super(cause);
	}
	
	public TokenResolutionException(String message) {
		super(message);
	}
	
	public TokenResolutionException(String message, Throwable cause) {
		super(message, cause);
	}
}
