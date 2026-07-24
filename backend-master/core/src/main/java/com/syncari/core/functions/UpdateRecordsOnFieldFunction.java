package com.syncari.core.functions;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.core.pipeline.PipelinePublishedEvent;
import com.syncari.core.pipeline.SimpleExpressionVisitor;
import com.syncari.core.pipeline.expression.EqualIgnoreCase;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.core.pipeline.expression.VariableExpression;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.validation.ValidationContext;
import com.syncari.utils.Pair;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component(FunctionConstants.UPDATE_SYNCARI_RECORDS_ON_FIELD)
public class UpdateRecordsOnFieldFunction extends UpdateRecordsFunction {
    @Autowired
    @Qualifier("updateSyncariRecords")
    UpdateRecordsFunction updateRecordsFunction;

    @Override
    public void validate(ValidationContext validationContext) {
    	var errors = validateWithoutException(validationContext);
    	if(errors != null && !errors.isEmpty()) {
    		throw new SyncariValidationException(errors.get(0).getMessage());
    	}
    }
    
    @Override
    public List<ValidationError> validateWithoutException(ValidationContext validationContext) {
        return updateRecordsFunction.validateWithoutException(validationContext);
    }

    @Override
    public void extract(QuickStartContext context) {
        updateRecordsFunction.extract(context);
    }
    @Override
    public void postPublish(PipelinePublishedEvent context) {
        super.postPublish(context);
    }
    
    @Override
	public List<Pair<String, String>> toUserFriendlyValue(DiffInfoContext context, String configProperty) {
    	return updateRecordsFunction.toUserFriendlyValue(context, configProperty);
    }
}
