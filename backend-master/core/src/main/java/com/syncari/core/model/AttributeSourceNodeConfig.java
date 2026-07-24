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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.syncari.core.utils.ValidationUtils.validateCondition;

@Data
@Accessors(chain=true)
public class AttributeSourceNodeConfig implements NodeConfiguration {

    @DBRef
    private AttributeDefinition attributeDefinition;

    private String entityDefinitionId;

    @Override
    public MappingNodeType getNodeType() {
        return MappingNodeType.ATTRIBUTE_SOURCE;
    }

    @Override
    public String getApiName() {
		return (attributeDefinition != null) ? attributeDefinition.getApiName() : null;
    }

    @Override
    public List<OutputPort> getOutputPorts() {
		return (attributeDefinition != null) ? List.of(OutputPort.of(attributeDefinition.getDataType())) : List.of();
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
		return (attributeDefinition != null) ? Map.of("attributeDefinition", attributeDefinition.getId()) : Map.of();
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
		validateCondition(ValidationError.scopedError(scope, nodeId), attributeDefinition == null,
                "A source attribute is required in %s pipeline, node %s", ErrorCode.E1180.getCode(), graphName,nodeName).ifPresent(e->errors.add(e));
		return errors;
	}
	
	@Override
	public boolean isValidConfig() {
		return attributeDefinition != null;
	}
}
