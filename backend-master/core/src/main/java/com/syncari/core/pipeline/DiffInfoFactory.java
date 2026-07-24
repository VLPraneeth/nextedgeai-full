package com.syncari.core.pipeline;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.syncari.core.model.MappingNode;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.service.CoreAttributeDiffInfoService;
import com.syncari.core.service.CoreEntityDiffInfoService;
import com.syncari.core.service.DefaultDiffInfoService;
import com.syncari.core.service.DestinationEntityDiffInfoService;
import com.syncari.core.service.DiffInfoService;
import com.syncari.core.service.SourceEntityDiffInfoService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DiffInfoFactory {

    @Autowired
    ApplicationContext context;

    @Autowired
    DefaultDiffInfoService diffInfoService;
    
    @Autowired
    CoreAttributeDiffInfoService coreAttributeDiffInfoService;
    
    @Autowired
    CoreEntityDiffInfoService coreEntityDiffInfoService; 
    
    @Autowired
    SourceEntityDiffInfoService sourceEntityDiffInfoService;
    
    @Autowired
    DestinationEntityDiffInfoService destinationEntityDiffInfoService;

    public DiffInfoService getDiffInfoService(MappingNode node){
    	try {
            Object clazz = context.getBean(node.getApiName());
            if (clazz != null && DiffInfoService.class.isAssignableFrom(clazz.getClass())) {
                return (DiffInfoService) clazz;
            }
        } catch (NoSuchBeanDefinitionException e){
        	if(node.getType()==MappingNodeType.CORE_ATTRIBUTE) {
        		return coreAttributeDiffInfoService;
        	} else if(node.getType()==MappingNodeType.CORE_ENTITY) {
        		return coreEntityDiffInfoService;
        	} else if(node.getType()==MappingNodeType.ENTITY_SOURCE) {
        		return sourceEntityDiffInfoService;
        	} else if(node.getType()==MappingNodeType.ENTITY_SINK) {
        		return destinationEntityDiffInfoService;
        	} else {
        		log.info("DiffInfoService for {} is not found. Returning default impl", node.getApiName());
        	}
        }
        return diffInfoService;
    	
    }
}
