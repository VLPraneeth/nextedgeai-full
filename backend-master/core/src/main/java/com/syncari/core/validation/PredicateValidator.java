package com.syncari.core.validation;

import com.syncari.core.pipeline.expression.VariableExpression;

public interface PredicateValidator {

    void validateVarExpression(VariableExpression variableExpression, ValidationContext validationContext);
}

