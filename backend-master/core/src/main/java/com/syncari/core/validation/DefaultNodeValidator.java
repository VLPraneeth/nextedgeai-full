package com.syncari.core.validation;

import java.util.List;

import org.springframework.stereotype.Component;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.util.ValidationError;

@Component
public class DefaultNodeValidator implements ValidationService {

    @Override
    public void validate(ValidationContext validationContext) {
    	var errors = validateWithoutException(validationContext);
    	if(errors != null && !errors.isEmpty()) {
    		throw new SyncariValidationException(errors.get(0).getMessage());
    	}
    }

	@Override
	public List<ValidationError> validateWithoutException(ValidationContext validationContext) {
		return List.of();
	}
}
