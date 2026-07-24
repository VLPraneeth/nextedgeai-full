package com.syncari.core.model.misc.sharable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.syncari.core.model.GroupDefinition;
import com.syncari.core.model.InputPort;
import com.syncari.core.model.OutputPort;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.ValidationError;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SharableGroupNodeConfig implements SharableNodeConfiguration {

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
	public List<ValidationError> validateWithoutException(Scope scope, String graphName, String nodeId,
			String nodeName) {
		return List.of();
	}
}
