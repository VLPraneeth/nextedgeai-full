package com.syncari.core.validation;

import java.util.List;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.util.ValidationError;

public interface ValidationService {

    default void validate(ValidationContext validationContext){
    	var errors = validateWithoutException(validationContext);
    	if(errors != null && !errors.isEmpty()) {
    		throw new SyncariValidationException(errors.get(0).getMessage());
    	}
    }
    
    default List<ValidationError> validateWithoutException(ValidationContext validationContext) {
    	return List.of();
    }
}
