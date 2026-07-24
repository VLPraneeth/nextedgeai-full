package com.syncari.core.actions;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.validation.ValidationContext;

import java.util.List;

import org.springframework.stereotype.Component;

@Component(ActionConstants.REMOVE_FROM_MARKETO_LIST)
public class RemoveFromMarketoListAction extends MarketoListAction {

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

    @Override
    public void extract(QuickStartContext context) {
        super.extract(context);
    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
        return super.resolve(context);
    }
}
