package com.syncari.core.model.misc.sharable;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.InputPort;
import com.syncari.core.model.OutputPort;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.SchedulingType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.ValidationError;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.syncari.core.utils.ValidationUtils.validateCondition;

@Data
@Accessors(chain = true)
public class SharableSourceEntityNodeConfig implements SharableNodeConfiguration {

    private EntityDefinition entityDefinition;
    private Map<String, Object> sourceParams = new HashMap<>();
    //cron format
    private String schedule="";

    private boolean deletePropagated=true;

    private SchedulingType exhaustAllRecords=SchedulingType.PROCESS_ALL;
    
    private Map<String, Object> additionalParams = new HashMap<>();

    @Override
    public MappingNodeType getNodeType() {
        return MappingNodeType.ENTITY_SOURCE;
    }

    @Override
    public String getApiName() {
        return entityDefinition.getApiName();
    }

    @Override
    public List<OutputPort> getOutputPorts() {
        return List.of(OutputPort.many());
    }

    @Override
    public List<InputPort> getInputPorts() {
        return List.of();
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
        return Map.of( "entityDefinition",entityDefinition.getId(),"connectorId",entityDefinition.getConnectorId(),"schedule",schedule);
    }

	@Override
	public List<ValidationError> validateWithoutException(Scope scope, String graphName, String nodeId,
			String nodeName) {
		List<ValidationError> errors = new ArrayList<>();
		validateCondition(ValidationError.scopedError(scope, nodeId), entityDefinition == null,
                "Source entity is required in %s pipeline, node %s", ErrorCode.E1189.getCode(), graphName, nodeName).ifPresent(e->errors.add(e));
		return errors;
	}
}
