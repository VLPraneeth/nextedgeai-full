package com.syncari.core.model.misc.sharable;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.DataAuthority;
import com.syncari.core.model.InputPort;
import com.syncari.core.model.OutputPort;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.ValidationError;
import com.syncari.utils.KeyValue;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class SharableCoreAttributeNodeConfig implements SharableNodeConfiguration {

    private AttributeDefinition attributeDefinition;

    private boolean rejectEmptyValue =true;
    private DataAuthority dataAuthority = DataAuthority.none();

    @Override
    public MappingNodeType getNodeType() {
        return MappingNodeType.CORE_ATTRIBUTE;
    }

    @Override
    public String getApiName() {
        return attributeDefinition.getApiName();
    }

    @Override
    public List<OutputPort> getOutputPorts() {
        return List.of(OutputPort.many(attributeDefinition.getDataType()));
    }

    @Override
    public List<InputPort> getInputPorts() {
        return List.of(InputPort.many(attributeDefinition.getDataType()));
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
        return KeyValue.of("attributeDefinition",attributeDefinition.getId(),"rejectEmptyValue",rejectEmptyValue,"dataAuthority", dataAuthority.getConfigMap());
    }

	@Override
	public List<ValidationError> validateWithoutException(Scope scope, String graphName, String nodeId,
			String nodeName) {
		return List.of();
	}
}
