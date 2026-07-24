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

public class GenericNodeValidatorVisitor implements NodeValidatorVisitor {

    private NodeValidatorFactory nodeValidatorFactory;

    public GenericNodeValidatorVisitor(NodeValidatorFactory nodeValidatorFactory){
        this.nodeValidatorFactory = nodeValidatorFactory;
    }

    @Override
    public void validate(SimpleFunctionNodeConfig simpleFunctionNodeConfig, ValidationContext validationContext) {
        var validator = nodeValidatorFactory.getFunctionNodeValidator(validationContext.getNode());
        validator.validate(validationContext);
    }

    @Override
    public void validate(GenericActionConfig actionConfig, ValidationContext validationContext) {
        var validator = nodeValidatorFactory.getActionNodeValidator(validationContext.getNode());
        validator.validate(validationContext);
    }

    @Override
    public void validate(AttributeSinkNodeConfig attributeSinkNodeConfig, ValidationContext validationContext) {
        var validator = nodeValidatorFactory.getSinkAttributeyNodeValidator(validationContext.getNode());
        validator.validate(validationContext);
    }

    @Override
    public void validate(AttributeSourceNodeConfig attributeSourceNodeConfig, ValidationContext validationContext) {
        var validator = nodeValidatorFactory.getSourceAttributeNodeValidator(validationContext.getNode());
        validator.validate(validationContext);
    }

    @Override
    public void validate(EntitySinkNodeConfig entitySinkNodeConfig, ValidationContext validationContext) {
        var validator = nodeValidatorFactory.getSinkEntityNodeValidator(validationContext.getNode());
        validator.validate(validationContext);
    }

    @Override
    public void validate(EntitySourceNodeConfig entitySourceNodeConfig, ValidationContext validationContext) {
        var validator = nodeValidatorFactory.getSourceEntityNodeValidator(validationContext.getNode());
        validator.validate(validationContext);
    }

    @Override
    public void validate(CoreAttributeNodeConfig coreAttributeNodeConfig, ValidationContext validationContext) {
        var validator = nodeValidatorFactory.getCoreAttributeNodeValidator(validationContext.getNode());
        validator.validate(validationContext);
    }

    @Override
    public void validate(CoreEntityNodeConfig coreEntityNodeConfig, ValidationContext validationContext) {
        var validator = nodeValidatorFactory.getCoreEntityNodeValidator(validationContext.getNode());
        validator.validate(validationContext);
    }

    @Override
    public void validate(SendEmailActionConfig sendEmailActionConfig, ValidationContext validationContext) {
        var validator = nodeValidatorFactory.getActionNodeValidator(validationContext.getNode());
        validator.validate(validationContext);
    }

    @Override
    public List<ValidationError> validateWithoutException(SimpleFunctionNodeConfig simpleFunctionNodeConfig, ValidationContext validationContext) {
        var validator = nodeValidatorFactory.getFunctionNodeValidator(validationContext.getNode());
        return validator.validateWithoutException(validationContext);
    }

    @Override
    public List<ValidationError> validateWithoutException(GenericActionConfig actionConfig, ValidationContext validationContext) {
        var validator = nodeValidatorFactory.getActionNodeValidator(validationContext.getNode());
        return validator.validateWithoutException(validationContext);
    }

    @Override
    public List<ValidationError> validateWithoutException(AttributeSinkNodeConfig attributeSinkNodeConfig, ValidationContext validationContext) {
        var validator = nodeValidatorFactory.getSinkAttributeyNodeValidator(validationContext.getNode());
        return validator.validateWithoutException(validationContext);
    }

    @Override
    public List<ValidationError> validateWithoutException(AttributeSourceNodeConfig attributeSourceNodeConfig, ValidationContext validationContext) {
        var validator = nodeValidatorFactory.getSourceAttributeNodeValidator(validationContext.getNode());
        return validator.validateWithoutException(validationContext);
    }

    @Override
    public List<ValidationError> validateWithoutException(EntitySinkNodeConfig entitySinkNodeConfig, ValidationContext validationContext) {
        var validator = nodeValidatorFactory.getSinkEntityNodeValidator(validationContext.getNode());
        return validator.validateWithoutException(validationContext);
    }

    @Override
    public List<ValidationError> validateWithoutException(EntitySourceNodeConfig entitySourceNodeConfig, ValidationContext validationContext) {
        var validator = nodeValidatorFactory.getSourceEntityNodeValidator(validationContext.getNode());
        return validator.validateWithoutException(validationContext);
    }

    @Override
    public List<ValidationError> validateWithoutException(CoreAttributeNodeConfig coreAttributeNodeConfig, ValidationContext validationContext) {
        var validator = nodeValidatorFactory.getCoreAttributeNodeValidator(validationContext.getNode());
        return validator.validateWithoutException(validationContext);
    }

    @Override
    public List<ValidationError> validateWithoutException(CoreEntityNodeConfig coreEntityNodeConfig, ValidationContext validationContext) {
        var validator = nodeValidatorFactory.getCoreEntityNodeValidator(validationContext.getNode());
        return validator.validateWithoutException(validationContext);
    }

    @Override
    public List<ValidationError> validateWithoutException(SendEmailActionConfig sendEmailActionConfig, ValidationContext validationContext) {
        var validator = nodeValidatorFactory.getActionNodeValidator(validationContext.getNode());
        return validator.validateWithoutException(validationContext);
    }
}
