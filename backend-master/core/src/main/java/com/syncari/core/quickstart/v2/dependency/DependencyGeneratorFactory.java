package com.syncari.core.quickstart.v2.dependency;

import com.syncari.core.actions.DefaultAction;
import com.syncari.core.functions.DefaultFunction;
import com.syncari.core.model.misc.sharable.SharableNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DependencyGeneratorFactory {

    @Autowired
    ApplicationContext context;

    @Autowired
    DefaultFunction defaultFunction;

    @Autowired
    DefaultAction defaultAction;

    @Autowired
    SourceEntityNodeDependencyGenerator sourceEntityNodeDependencyGenerator;

    @Autowired
    SourceAttributeNodeDependencyGenerator sourceAttributeNodeDependencyGenerator;

    @Autowired
    SinkEntityNodeDependencyGenerator sinkEntityNodeDependencyGenerator;

    @Autowired
    SinkAttributeNodeDependencyGenerator sinkAttributeNodeDependencyGenerator;

    @Autowired
    CoreEntityNodeDependencyGenerator coreEntityNodeDependencyGenerator;

    @Autowired
    CoreAttributeNodeDependencyGenerator coreAttributeNodeDependencyGenerator;

    @Autowired
    DefaultNodeDependencyGenerator defaultNodeDependencyGenerator;
    
    @Autowired
    GroupNodeDependencyGenerator groupNodeDependencyGenerator;

    public DependencyService getDependencyGenerator(SharableNode node){
        switch (node.getType()){
            case CORE_ENTITY:
                return getCoreEntityNodeDependencyGenerator(node);
            case CORE_ATTRIBUTE:
                return getCoreAttributeyNodeDependencyGenerator(node);
            case ENTITY_SOURCE:
                return getSourceEntityNodeDependencyGenerator(node);
            case ATTRIBUTE_SOURCE:
                return getSourceAttributeNodeDependencyGenerator(node);
            case ENTITY_SINK:
                return getSinkEntityNodeDependencyGenerator(node);
            case ATTRIBUTE_SINK:
                return getSinkAttributeyNodeDependencyGenerator(node);
            case FUNCTION:
                return getFunctionNodeDependencyGenerator(node);
            case ACTION:
                return getActionDependencyGenerator(node);
            case GROUP:
            	return getGroupNodeDependencyGenerator(node);
            default:
                return getDefaultNodeDependencyGenerator();
        }
    }

    public DependencyService getActionDependencyGenerator(SharableNode node) {
        try {
            Object clazz = context.getBean(node.getApiName());
            if (clazz != null && DependencyService.class.isAssignableFrom(clazz.getClass())) {
                return (DependencyService) clazz;
            }
        } catch (NoSuchBeanDefinitionException e){
            log.info("Dependency generator for {} is not found. Returning default action generator", node.getApiName());
        }
        return defaultAction;
    }

    public DependencyService getFunctionNodeDependencyGenerator(SharableNode node) {
        try {
            Object clazz = context.getBean(node.getApiName());
            if (clazz != null && DependencyService.class.isAssignableFrom(clazz.getClass())) {
                return (DependencyService) clazz;
            }
        } catch (NoSuchBeanDefinitionException e){
            log.info("Dependency generator for {} is not found. Returning default function generator", node.getApiName());
        }
        return defaultFunction;
    }

    public DependencyService getSourceEntityNodeDependencyGenerator(SharableNode node) {
        return sourceEntityNodeDependencyGenerator;
    }

    public DependencyService getSinkEntityNodeDependencyGenerator(SharableNode node) {
        return sinkEntityNodeDependencyGenerator;
    }

    public DependencyService getSourceAttributeNodeDependencyGenerator(SharableNode node) {
        return sourceAttributeNodeDependencyGenerator;
    }

    public DependencyService getSinkAttributeyNodeDependencyGenerator(SharableNode node) {
        return sinkAttributeNodeDependencyGenerator;
    }

    public DependencyService getCoreEntityNodeDependencyGenerator(SharableNode node) {
        return coreEntityNodeDependencyGenerator;
    }

    public DependencyService getCoreAttributeyNodeDependencyGenerator(SharableNode node) {
        return coreAttributeNodeDependencyGenerator;
    }

    public DependencyService getDefaultNodeDependencyGenerator() {
        return defaultNodeDependencyGenerator;
    }
    
    public DependencyService getGroupNodeDependencyGenerator(SharableNode node) {
        return groupNodeDependencyGenerator;
    }
}