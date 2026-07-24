package com.syncari.core.model;

import java.util.HashMap;
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
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@Accessors(chain=true)
public class GroupNodeConfig implements NodeConfiguration {
    private GroupDefinition groupDefinition;


	private List<String> childNodeIds = List.of();
	private List<String> tags = List.of();
	private String color;
	private boolean collapsed;
	private String shape;
	private String graphDirection;
	private String childNodeSummary;
	private String description;
    @Override
    public MappingNodeType getNodeType() {
        return MappingNodeType.GROUP;
    }

    @Override
    public String getApiName() {
        return groupDefinition.getName();
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
    	// This is a container to other nodes, hence no validation required
    	log.debug("Validation called on group node");
    }

    @Override
    public List<ValidationError> validateWithoutException(Scope scope, String graphName, String nodeId, String nodeName) {
    	// This is a container to other nodes, hence no validation required
    	log.debug("Validation without exception called on group node");
    	return List.of();
    }

    @Override
    public Map<String, Object> getConfigMap() {
        var config = new HashMap<String, Object>();
        config.put("definition", groupDefinition);
        config.put("childNodeIds", childNodeIds);
        config.put("description", description);
        config.put("tags", tags);
        config.put("childNodeSummary", childNodeSummary);
        config.put("color", color);
        config.put("collapsed", collapsed);
        config.put("shape", shape);
        return config;
    }

    @Override
    public void accept(NodeConfigurationVisitor visitor, MappingNode node) {
    }

    @Override
    public void accept(NodeConfigurationVisitor visitor) {
    }

    @Override
    public void accept(NodeValidatorVisitor validator, ValidationContext validationContext) {
    }
    @Override
    public List<ValidationError> acceptWithoutException(NodeValidatorVisitor validator, ValidationContext validationContext) {
        return List.of();
    }
}
