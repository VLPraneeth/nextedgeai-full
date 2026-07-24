package com.syncari.core.validation;

import com.syncari.core.actions.DefaultAction;
import com.syncari.core.functions.DefaultFunction;
import com.syncari.core.model.MappingNode;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NodeValidatorFactory {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private DefaultFunction defaultFunction;

    @Autowired
    private DefaultAction defaultAction;

    @Autowired
    private SourceEntityNodeValidator sourceEntityNodeValidator;

    @Autowired
    private SourceAttributeNodeValidator sourceAttributeNodeValidator;

    @Autowired
    private SinkEntityNodeValidator sinkEntityNodeValidator;

    @Autowired
    private SinkAttributeNodeValidator sinkAttributeNodeValidator;
    
    @Autowired
    private CoreEntityNodeValidator coreEntityNodeValidator;

    @Autowired
    private CoreAttributeNodeValidator coreAttributeNodeValidator;

    @Autowired
    private DefaultNodeValidator defaultNodeValidator;

    public ValidationService getActionNodeValidator(MappingNode node) {
        try {
            Object clazz = context.getBean(node.getApiName());
            if (clazz != null && ValidationService.class.isAssignableFrom(clazz.getClass())) {
                return (ValidationService) clazz;
            }
        } catch (NoSuchBeanDefinitionException e){
            log.debug("Validator for {} is not found. Returning default action validator", node.getApiName());
        }
        return defaultAction;
    }

    public ValidationService getFunctionNodeValidator(MappingNode node) {
        try {
            Object clazz = context.getBean(node.getApiName());
            if (clazz != null && ValidationService.class.isAssignableFrom(clazz.getClass())) {
                return (ValidationService) clazz;
            }
        } catch (NoSuchBeanDefinitionException e){
            log.debug("Validator for {} is not found. Returning default function validator", node.getApiName());
        }
        return defaultFunction;
    }

    public Optional<DefaultFunction> getFunction(MappingNode node) {
        try {
            Object clazz = context.getBean(node.getApiName());
            if (clazz != null && DefaultFunction.class.isAssignableFrom(clazz.getClass())) {
                return Optional.of((DefaultFunction) clazz);
            }
        } catch (NoSuchBeanDefinitionException e){
            log.debug("Validator for {} is not found. Returning default function validator", node.getApiName());
        }
        return Optional.empty();
    }

    public Optional<DefaultAction> getAction(MappingNode node) {
        try {
            Object clazz = context.getBean(node.getApiName());
            if (clazz != null && DefaultAction.class.isAssignableFrom(clazz.getClass())) {
                return Optional.of((DefaultAction) clazz);
            }
        } catch (NoSuchBeanDefinitionException e){
            log.info("Validator for {} is not found", node.getApiName());
        }
        return Optional.empty();
    }

    public ValidationService getSourceEntityNodeValidator(MappingNode node) {
        return sourceEntityNodeValidator;
    }

    public ValidationService getSinkEntityNodeValidator(MappingNode node) {
        return sinkEntityNodeValidator;
    }
    
    public ValidationService getCoreEntityNodeValidator(MappingNode node) {
    	return coreEntityNodeValidator;
    }

    public ValidationService getCoreAttributeNodeValidator(MappingNode node) {
    	return coreAttributeNodeValidator;
    }

    public ValidationService getSourceAttributeNodeValidator(MappingNode node) {
        return sourceAttributeNodeValidator;
    }

    public ValidationService getSinkAttributeyNodeValidator(MappingNode node) {
        return sinkAttributeNodeValidator;
    }

    public ValidationService getDefaultNodeValidator() {
        return defaultNodeValidator;
    }
}
