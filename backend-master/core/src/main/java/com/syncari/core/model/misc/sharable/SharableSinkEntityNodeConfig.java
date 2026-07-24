package com.syncari.core.model.misc.sharable;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.InputPort;
import com.syncari.core.model.OutputPort;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.ValidationError;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.syncari.core.utils.ValidationUtils.validateCondition;

@Data
@Accessors(chain = true)
public class SharableSinkEntityNodeConfig implements SharableNodeConfiguration {

    private EntityDefinition entityDefinition;

    @Override
    public MappingNodeType getNodeType() {
        return MappingNodeType.ENTITY_SINK;
    }

    @Override
    public String getApiName() {
        return entityDefinition.getApiName();
    }

    @Override
    public List<OutputPort> getOutputPorts() {
        return List.of(OutputPort.any());
    }

    @Override
    public List<InputPort> getInputPorts() {
        return List.of(InputPort.any());
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
        return Map.of( "entityDefinition",entityDefinition.getId(),"connectorId",entityDefinition.getConnectorId());
    }

	@Override
	public List<ValidationError> validateWithoutException(Scope scope, String graphName, String nodeId,
			String nodeName) {
		List<ValidationError> errors = new ArrayList<>();
		validateCondition(ValidationError.scopedError(scope, nodeId), entityDefinition == null,
                "Destination entity is required in %s pipeline, node %s", ErrorCode.E1188.getCode(), graphName, nodeName).ifPresent(e->errors.add(e));
		return errors;
	}
}
