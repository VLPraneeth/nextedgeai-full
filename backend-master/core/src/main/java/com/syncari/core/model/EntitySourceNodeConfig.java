package com.syncari.core.model;

import static com.syncari.core.utils.ValidationUtils.validateCondition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.syncari.core.model.util.*;
import org.springframework.data.mongodb.core.mapping.DBRef;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.pipeline.NodeConfigurationVisitor;
import com.syncari.core.validation.NodeValidatorVisitor;
import com.syncari.core.validation.ValidationContext;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class EntitySourceNodeConfig implements NodeConfiguration {
	public static List<String> EXCLUDED_PROPERTIES = List.of("schedule", "exhaustAllRecords", "sourceParams",
			"deletePropagated", "entityDefinition", "targetId", "connectorId", "configId", "graphVersion");

    @DBRef
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
        return entityDefinition != null ? entityDefinition.getApiName(): null;
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
    	var configMap = new HashMap<String, Object>(Map.of("entityDefinition", entityDefinition != null ? entityDefinition.getId(): "", "connectorId", entityDefinition != null ? entityDefinition.getConnectorId(): "",
				"schedule", schedule, "exhaustAllRecords", exhaustAllRecords, "sourceParams", sourceParams, "entity", entityDefinition != null ? entityDefinition.getId(): ""));
		if(additionalParams != null) {
			configMap.putAll(additionalParams);
		}
		return configMap;
    }

    @Override
    public void accept(NodeConfigurationVisitor visitor, MappingNode node) {
        visitor.visit(this,node);
    }

    @Override
    public void accept(NodeConfigurationVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public void accept(NodeValidatorVisitor validator, ValidationContext validationContext) {
        validator.validate(this, validationContext);
    }
    
    @Override
    public List<ValidationError> acceptWithoutException(NodeValidatorVisitor validator, ValidationContext validationContext) {
        return validator.validateWithoutException(this, validationContext);
    }

	@Override
	public List<ValidationError> validateWithoutException(Scope scope, String graphName, String nodeId,
			String nodeName) {
		List<ValidationError> errors = new ArrayList<>();
		validateCondition(ValidationError.scopedError(scope, nodeId), entityDefinition == null,
                "Source entity is required in %s pipeline, node %s", ErrorCode.E1183.getCode(), graphName, nodeName).ifPresent(e->errors.add(e));
		return errors;
	}
	
	@Override
	public boolean isValidConfig() {
		return entityDefinition != null;
	}
}
