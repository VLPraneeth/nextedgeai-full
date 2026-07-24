package com.syncari.core.functions;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.validation.ValidationContext;
import com.syncari.utils.Pair;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component(FunctionConstants.INSERT_SYNCARI_RECORD_ON_FIELD)
public class InsertSyncariRecordOnFieldFunction extends DefaultFunction {

    @Autowired
    @Qualifier(FunctionConstants.INSERT_SYNCARI_RECORD)
    InsertSyncariRecordFunction insertRecordFunction;

    @Override
    public void validate(ValidationContext validationContext) {
    	var errors = validateWithoutException(validationContext);
    	if(errors != null && !errors.isEmpty()) {
    		throw new SyncariValidationException(errors.get(0).getMessage());
    	}
    }
    
    @Override
    public List<ValidationError> validateWithoutException(ValidationContext validationContext) {
        return insertRecordFunction.validateWithoutException(validationContext);
    }

    @Override
    public void extract(QuickStartContext context) {
        insertRecordFunction.extract(context);
    }

    @Override
    public List<Pair<String, String>> toUserFriendlyValue(DiffInfoContext context, String configProperty) {
        return insertRecordFunction.toUserFriendlyValue(context, configProperty);
    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
        return insertRecordFunction.resolve(context);
    }
}
