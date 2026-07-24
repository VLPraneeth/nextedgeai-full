package com.syncari.core.model.misc.sharable;

import com.syncari.connector.Constants;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class SharableSinkAttributeNodeConfig implements SharableNodeConfiguration {

    private AttributeDefinition attributeDefinition;
    private Object defaultValue;
    private boolean alwaysUseDefaultOnEmpty=false;
    private boolean required=false;
    private Constants.REJECT_EMPTY_ENUM rejectEmpty = Constants.REJECT_EMPTY_ENUM.NEVER;

    @Override
    public MappingNodeType getNodeType() {
        return MappingNodeType.ATTRIBUTE_SINK;
    }

    @Override
    public String getApiName() {
        return attributeDefinition.getApiName();
    }

    @Override
    public List<OutputPort> getOutputPorts() {
        return Collections.emptyList();
    }

    @Override
    public List<InputPort> getInputPorts() {
        return List.of(InputPort.of(attributeDefinition.getDataType()));
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
        var config = new HashMap<String, Object>();
        config.put("attributeDefinition",attributeDefinition.getId());
        config.put("defaultValue",defaultValue==null ? "":defaultValue);
        config.put("alwaysUseDefaultOnEmpty",alwaysUseDefaultOnEmpty);
        config.put(Constants.REJECT_EMPTY, rejectEmpty);
        return config;
    }

	@Override
	public List<ValidationError> validateWithoutException(Scope scope, String graphName, String nodeId,
			String nodeName) {
		return List.of();
	}
}
