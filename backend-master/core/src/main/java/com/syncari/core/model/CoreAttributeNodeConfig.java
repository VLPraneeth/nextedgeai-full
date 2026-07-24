package com.syncari.core.model;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.NodeConfigurationVisitor;
import com.syncari.core.utils.ValidationUtils;
import com.syncari.core.validation.NodeValidatorVisitor;
import com.syncari.core.validation.ValidationContext;
import com.syncari.utils.KeyValue;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.syncari.core.utils.ValidationUtils.validateCondition;

/**
 * This is almost a marker config for Syncari Core
 */
@Data
@Accessors(chain=true)
public class CoreAttributeNodeConfig implements NodeConfiguration {

    private AttributeDefinition attributeDefinition;

    private boolean rejectEmptyValue =true;
    private boolean rejectEmptyString = true;
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
        return KeyValue.of("attributeDefinition",attributeDefinition.getId(),"rejectEmptyValue",rejectEmptyValue,"rejectEmptyString",rejectEmptyString,"dataAuthority", dataAuthority.getConfigMap());
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
                "A core Syncari attribute is required in %s pipeline, node %s", ErrorCode.E1179.getCode(), graphName,nodeName).ifPresent(e->errors.add(e));
		return errors;
	}

}
