package com.syncari.core.utils;

import java.util.Optional;
import java.util.function.Supplier;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.util.ValidationError;

public class ValidationUtils {

    public static Supplier<SyncariValidationException> error(String message, Object ... args){
        return () -> new SyncariValidationException(message,args);
    }

    public static void validateCondition(boolean errorCondition, String message, Object... args){
        if(errorCondition){
            throw new SyncariValidationException(message, args);
        }
    }
    public static void validateCondition(boolean errorCondition, String message){
        if(errorCondition){
            throw new SyncariValidationException(message);
        }
    }
    
	public static Optional<ValidationError> validateCondition(ValidationError errorTemplate, boolean errorCondition,
			String message, String errorCode) {
		if (errorTemplate != null && errorCondition) {
			ValidationError error = errorTemplate.copy();
			error.setMessage(message);
			error.setErrorCode(errorCode);
			return Optional.of(error);
		}
		return Optional.empty();
	}

	public static Optional<ValidationError> validateCondition(ValidationError errorTemplate, boolean errorCondition,
			String message, String errorCode, Object... args) {
		if (errorTemplate != null && errorCondition) {
			ValidationError error = errorTemplate.copy();
			error.setMessage(String.format(message, args));
			error.setErrorCode(errorCode);
			return Optional.of(error);
		}
		return Optional.empty();
	}

}
