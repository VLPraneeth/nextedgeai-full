package com.syncari.core.model;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.NodeConfigurationVisitor;
import com.syncari.core.validation.NodeValidatorVisitor;
import com.syncari.core.validation.ValidationContext;
import lombok.Data;
import lombok.experimental.Accessors;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.DBRef;

import java.util.*;

import static com.syncari.core.utils.ValidationUtils.validateCondition;

@Data
@Accessors(chain = true)
public class EntitySinkNodeConfig implements NodeConfiguration {

	@DBRef
	private EntityDefinition entityDefinition;

	private Map<String, Object> destinationParams = new HashMap<>();

	// listt of source entitydefinitions which are allowed to delete a record from
	// this destination
	private List<String> acceptsDeletesFrom = new ArrayList<>();

	private boolean createDisconnectedMapping = false;

	private boolean syncOnTxnLog = false;

	@Override
	public MappingNodeType getNodeType() {
		return MappingNodeType.ENTITY_SINK;
	}

	@Override
	public String getApiName() {
		return entityDefinition != null ? entityDefinition.getApiName(): null;
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
		return Map.of("entityDefinition", entityDefinition != null ? entityDefinition.getId(): "", "connectorId", entityDefinition != null ? entityDefinition.getConnectorId(): "",
				"acceptsDeletesFrom", acceptsDeletesFrom, "createDisconnectedMapping", createDisconnectedMapping,  "destinationParams", destinationParams, "syncOnTxnLog", syncOnTxnLog);
	}

	@Override
	public void accept(NodeConfigurationVisitor visitor, MappingNode node) {
		visitor.visit(this, node);
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
				"Destination entity is required in %s pipeline, node %s", ErrorCode.E1182.getCode(), graphName,
				nodeName).ifPresent(e->errors.add(e));
		return errors;
	}

	@Override
	public boolean isValidConfig() {
		return entityDefinition != null;
	}
}
