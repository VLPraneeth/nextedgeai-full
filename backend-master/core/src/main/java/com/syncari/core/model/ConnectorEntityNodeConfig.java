package com.syncari.core.model;

import java.util.List;
import java.util.Map;

import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.NodeConfigurationVisitor;
import com.syncari.core.validation.NodeValidatorVisitor;
import com.syncari.core.validation.ValidationContext;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * This is almost a marker config for Syncari Core
 */
@Data
@Accessors(chain=true)
public class ConnectorEntityNodeConfig implements NodeConfiguration {

    @Override
    public MappingNodeType getNodeType() {
        return MappingNodeType.CONNECTOR_ENTITY;
    }

    @Override
    public String getApiName() {
        return null;
    }

    @Override
    public List<OutputPort> getOutputPorts() {
        return List.of();
    }

    @Override
    public List<InputPort> getInputPorts() {
        return List.of();
    }

    @Override
    public void validate(String graphName, String nodeName) {
    }

    @Override
    public Map<String, Object> getConfigMap() {
        return Map.of();
    }

    @Override
    public void accept(NodeConfigurationVisitor visitor, MappingNode node) {
        //visitor.visit(this,node);
    }

    @Override
    public void accept(NodeConfigurationVisitor visitor) {
        //visitor.visit(this);
    }

    @Override
    public void accept(NodeValidatorVisitor validator, ValidationContext validationContext) {
    }
    
    @Override
    public List<ValidationError> acceptWithoutException(NodeValidatorVisitor validator, ValidationContext validationContext) {
        return List.of();
    }

	@Override
	public List<ValidationError> validateWithoutException(Scope scope, String graphName, String nodeId,
			String nodeName) {
		 return List.of();
	}

}
