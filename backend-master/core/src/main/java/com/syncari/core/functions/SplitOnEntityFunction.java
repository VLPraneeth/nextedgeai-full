package com.syncari.core.functions;

import com.syncari.core.model.util.ValidationError;
import com.syncari.core.validation.ValidationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component(FunctionConstants.SPLIT_ON_ENTITY)
public class SplitOnEntityFunction extends DefaultFunction {

    @Autowired
    private SplitFunction splitFunction;

    @Override
    public void validate(ValidationContext validationContext) {
        // Delegate to SplitFunction validator
        splitFunction.validate(validationContext);
    }

    @Override
    public List<ValidationError> validateWithoutException(ValidationContext validationContext) {
        // Delegate to SplitFunction validator
        return splitFunction.validateWithoutException(validationContext);
    }
}