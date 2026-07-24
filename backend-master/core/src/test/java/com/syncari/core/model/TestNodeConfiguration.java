package com.syncari.core.model;

import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.NodeConfigurationVisitor;
import com.syncari.core.validation.NodeValidatorVisitor;
import com.syncari.core.validation.ValidationContext;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@Data
public class TestNodeConfiguration implements NodeConfiguration {

    private List<OutputPort> outputPorts;
    private List<InputPort> inputPorts;

    @Override
    public MappingNodeType getNodeType() {
        return MappingNodeType.FUNCTION;
    }

    @Override
    public String getApiName() {
        return "testApiName";
    }

    @Override
    public void validate(String graphName, String nodeName) {

    }

    @Override
    public Map<String, Object> getConfigMap() {
        return Collections.emptyMap();
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
	public List<ValidationError> validateWithoutException(Scope scope, String graphName, String nodeId,
			String nodeName) {
		return List.of();
	}

	@Override
	public List<ValidationError> acceptWithoutException(NodeValidatorVisitor validator,
			ValidationContext validationContext) {
		return List.of();
	}
}
