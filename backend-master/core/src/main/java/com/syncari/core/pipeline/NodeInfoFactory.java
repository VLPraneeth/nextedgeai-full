package com.syncari.core.pipeline;

import com.syncari.core.functions.DefaultFunction;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.quickstart.v2.dependency.DependencyService;
import com.syncari.core.service.DefaultNodeInfoService;
import com.syncari.core.service.NodeInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NodeInfoFactory {

    @Autowired
    ApplicationContext context;

    @Autowired
    DefaultNodeInfoService nodeInfoService;

    @Autowired
    DefaultFunction defaultFunction;

    public NodeInfoService getNodeInfoService(MappingNode node){
        switch (node.getType()){
            case FUNCTION:
                return getFunctionNodeInfoService(node);
            default:
                return nodeInfoService;
        }
    }

    public NodeInfoService getFunctionNodeInfoService(MappingNode node) {
        try {
            Object clazz = context.getBean(node.getApiName());
            if (clazz != null && NodeInfoService.class.isAssignableFrom(clazz.getClass())) {
                return (NodeInfoService) clazz;
            }
        } catch (NoSuchBeanDefinitionException e){
            log.info("NodeInfoService for {} is not found. Returning default function impl", node.getApiName());
        }
        return defaultFunction;
    }
}
