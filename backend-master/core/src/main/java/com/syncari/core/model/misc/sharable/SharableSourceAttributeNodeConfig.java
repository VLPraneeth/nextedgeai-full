package com.syncari.core.model.misc.sharable;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.InputPort;
import com.syncari.core.model.OutputPort;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.ValidationError;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class SharableSourceAttributeNodeConfig implements SharableNodeConfiguration {

    private AttributeDefinition attributeDefinition;

    @Override
    public MappingNodeType getNodeType() {
        return MappingNodeType.ATTRIBUTE_SOURCE;
    }

    @Override
    public String getApiName() {
        return attributeDefinition.getApiName();
    }

    @Override
    public List<OutputPort> getOutputPorts() {
        return List.of(OutputPort.of(attributeDefinition.getDataType()));
    }

    @Override
    public List<InputPort> getInputPorts() {
        return Collections.emptyList();
    }

    @Override
    public void validate(String graphName, String nodeName) {
    	var errors = validateWithoutException(null, graphName, null, nodeName);
    	if(errors != null && !errors.isEmpty()) {
    		throw new SyncariValidationException(errors.get(0).getMessage());
    	}
    }
    @Override
    public Map<String, Object> getConfigMap() {
        return Map.of( "attributeDefinition",attributeDefinition.getId());
    }

	@Override
	public List<ValidationError> validateWithoutException(Scope scope, String graphName, String nodeId,
			String nodeName) {
		return List.of();
	}
}
