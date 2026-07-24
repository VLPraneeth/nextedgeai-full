package com.syncari.core.functions;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.validation.ValidationContext;

import java.util.List;

import org.springframework.stereotype.Component;

@Component(FunctionConstants.LOOKUP_SYNCARI_RECORD_ON_FIELD)
public class LookupSyncariRecordOnFieldFunction extends LookupSyncariRecordFunction {

    @Override
    public void validate(ValidationContext validationContext) {
    	var errors = validateWithoutException(validationContext);
    	if(errors != null && !errors.isEmpty()) {
    		throw new SyncariValidationException(errors.get(0).getMessage());
    	}
    }
    
    @Override
    public List<ValidationError> validateWithoutException(ValidationContext validationContext) {
        return super.validateWithoutException(validationContext);
    }
}
