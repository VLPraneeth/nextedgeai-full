package com.syncari.core.validation;

import java.util.List;

import com.syncari.core.model.AttributeSinkNodeConfig;
import com.syncari.core.model.AttributeSourceNodeConfig;
import com.syncari.core.model.CoreAttributeNodeConfig;
import com.syncari.core.model.CoreEntityNodeConfig;
import com.syncari.core.model.EntitySinkNodeConfig;
import com.syncari.core.model.EntitySourceNodeConfig;
import com.syncari.core.model.GenericActionConfig;
import com.syncari.core.model.SendEmailActionConfig;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.util.ValidationError;

public interface NodeValidatorVisitor {

    void validate(SimpleFunctionNodeConfig simpleFunctionNodeConfig, ValidationContext validationContext);

    void validate(GenericActionConfig actionConfig, ValidationContext validationContext);

    void validate(AttributeSinkNodeConfig attributeSinkNodeConfig, ValidationContext validationContext);

    void validate(AttributeSourceNodeConfig attributeSourceNodeConfig, ValidationContext validationContext);

    void validate(EntitySinkNodeConfig entitySinkNodeConfig, ValidationContext validationContext);

    void validate(EntitySourceNodeConfig entitySourceNodeConfig, ValidationContext validationContext);

    void validate(CoreAttributeNodeConfig coreAttributeNodeConfig, ValidationContext validationContext);

    void validate(CoreEntityNodeConfig coreEntityNodeConfig, ValidationContext validationContext);

    void validate(SendEmailActionConfig sendEmailActionConfig, ValidationContext validationContext);
    
    List<ValidationError> validateWithoutException(SimpleFunctionNodeConfig simpleFunctionNodeConfig, ValidationContext validationContext);

    List<ValidationError> validateWithoutException(GenericActionConfig actionConfig, ValidationContext validationContext);

    List<ValidationError> validateWithoutException(AttributeSinkNodeConfig attributeSinkNodeConfig, ValidationContext validationContext);

    List<ValidationError> validateWithoutException(AttributeSourceNodeConfig attributeSourceNodeConfig, ValidationContext validationContext);

    List<ValidationError> validateWithoutException(EntitySinkNodeConfig entitySinkNodeConfig, ValidationContext validationContext);

    List<ValidationError> validateWithoutException(EntitySourceNodeConfig entitySourceNodeConfig, ValidationContext validationContext);

    List<ValidationError> validateWithoutException(CoreAttributeNodeConfig coreAttributeNodeConfig, ValidationContext validationContext);

    List<ValidationError> validateWithoutException(CoreEntityNodeConfig coreEntityNodeConfig, ValidationContext validationContext);

    List<ValidationError> validateWithoutException(SendEmailActionConfig sendEmailActionConfig, ValidationContext validationContext);

}
