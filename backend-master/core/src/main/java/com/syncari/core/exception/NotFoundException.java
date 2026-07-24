package com.syncari.core.exception;

import static com.syncari.utils.I18n.*;
import static java.lang.String.format;

public class NotFoundException extends RuntimeException {

	public NotFoundException(Throwable cause) {
		super(cause);
	}
	
	public NotFoundException(String message) {
		super(message);
	}
	
	public NotFoundException(Class clazz, String field, String value) {
		super(format(i18n("not_found"), clazz.getSimpleName(), field, value));
	}
}
