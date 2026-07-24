package com.syncari.restutils.utils;

import com.syncari.connector.Constants;
import com.syncari.core.model.*;
import com.syncari.core.pipeline.AbstractNodeConfigurationVisitor;

import java.util.HashMap;
import java.util.Map;

public class NodeConfigMapVisitor extends AbstractNodeConfigurationVisitor {

    private Map<String, Object> configMap;

    protected void defaultVisit(NodeConfiguration configuration) {
        configMap = new HashMap<>(configuration.getConfigMap());
    }

    public void visit(AttributeSinkNodeConfig config) {
        defaultVisit(config);
        if(config.getAttributeDefinition() != null) {
        	configMap.put("configId",config.getAttributeDefinition().getEntityId()+"_sink");
        }
    }

    public void visit(AttributeSourceNodeConfig config) {
        defaultVisit(config);
        if(config.getAttributeDefinition() != null) {
        	configMap.put("configId",config.getAttributeDefinition().getEntityId()+"_source");
        }
    }
    public void visit(EntitySourceNodeConfig config) {
        defaultVisit(config);
        if(config.getEntityDefinition() != null) {
        	configMap.put("configId",config.getEntityDefinition().getConnectorId());
        	config.getEntityDefinition().getSourceParams().forEach(sourceParam->{
        		configMap.put(sourceParam.getApiName(), config.getSourceParams().get(sourceParam.getApiName()));
        	});
        }
    }

    public void visit(EntitySinkNodeConfig config) {
        defaultVisit(config);
        if(config.getEntityDefinition() != null) {
	        configMap.put("configId",config.getEntityDefinition().getConnectorId());
	        config.getEntityDefinition().getDestinationParams().forEach(destParam->{
	            configMap.put(destParam.getApiName(), config.getDestinationParams().get(destParam.getApiName()));
	        });
        }
    }
    public void visit(CoreAttributeNodeConfig config) {
        defaultVisit(config);
        configMap.put("configId",config.getAttributeDefinition().getId());
    }

    public void visit(CoreEntityNodeConfig config) {
        defaultVisit(config);
        if(config.getEntityDefinition() != null) {
        	configMap.put("configId",config.getEntityDefinition().getConnectorId());
        }
    }
    public void visit(GenericActionConfig config) {
        defaultVisit(config);
        configMap.putAll(config.getConfigMap());
    }
    @Override
    public void visit(SimpleFunctionNodeConfig simpleFunctionNodeConfig) {
        configMap = new HashMap<>(simpleFunctionNodeConfig.getConfigMap());
        configMap.put("configId",simpleFunctionNodeConfig.getFunctionCall().getFunctionDefinition().getId());
//        if ("filter".equals(simpleFunctionNodeConfig.getFunctionCall().getFunctionDefinition().getName())) {
//            Expression expression = (Expression) simpleFunctionNodeConfig.getFunctionCall().getConfig().get("predicate");
//            PredicateSerializingVisitor visitor = new PredicateSerializingVisitor();
//            expression.accept(visitor);
//            configMap.put("predicate", visitor.serialized());
//        }
    }

    public Map<String, Object> getConfigMap() {
        return configMap;
    }
}
