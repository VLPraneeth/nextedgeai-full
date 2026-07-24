package com.syncari.core.model;

import com.syncari.connector.Constants;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.NodeConfigurationVisitor;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.validation.NodeValidatorVisitor;
import com.syncari.core.validation.ValidationContext;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.DBRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Data
@Accessors(chain=true)
public class AttributeSinkNodeConfig implements NodeConfiguration {

    @DBRef
    private AttributeDefinition attributeDefinition;

    private Object defaultValue;
    private String entityDefinitionId;

    private boolean alwaysUseDefaultOnEmpty=false;
    private Constants.REJECT_EMPTY_ENUM rejectEmpty = Constants.REJECT_EMPTY_ENUM.NEVER;

    private boolean required=false;

    @Override
    public MappingNodeType getNodeType() {
        return MappingNodeType.ATTRIBUTE_SINK;
    }

    @Override
    public String getApiName() {
		return (attributeDefinition != null) ? attributeDefinition.getApiName() : null;
    }

    @Override
    public List<OutputPort> getOutputPorts() {
        return Collections.emptyList();
    }

    @Override
    public List<InputPort> getInputPorts() {
		return (attributeDefinition != null) ? List.of(InputPort.of(attributeDefinition.getDataType())) : List.of();
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
        if(attributeDefinition != null) {
        	config.put("attributeDefinition",attributeDefinition.getId());
        }
        config.put("defaultValue", defaultValue==null ? "":defaultValue);
        config.put("alwaysUseDefaultOnEmpty", alwaysUseDefaultOnEmpty);
        config.put(Constants.REJECT_EMPTY, rejectEmpty);
        return config;
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
                i18n("missing_destination_attribute", graphName, nodeName), ErrorCode.E1148.getCode()).ifPresent(e->errors.add(e));
		if (attributeDefinition != null) {
			validateCondition(ValidationError.scopedError(scope, nodeId),
					defaultValue != null && !StringUtils.isBlank(defaultValue.toString())
							&& !TokenHelper.hasTokens(defaultValue.toString())
							&& (attributeDefinition != null && attributeDefinition.convert(defaultValue) == null),
					i18n("inconvertible_data_type", defaultValue != null ? defaultValue.toString() : null, nodeName,
							graphName, attributeDefinition.getDataType().getName()), ErrorCode.E1149.getCode()).ifPresent(e -> errors.add(e));
			validateCondition(ValidationError.scopedError(scope, nodeId),
					attributeDefinition != null && !attributeDefinition.isUpdatable(),
					i18n("readonly_field_as_destination_error", attributeDefinition.getDisplayName(), graphName), ErrorCode.E1150.getCode())
							.ifPresent(e -> errors.add(e));
		}
		return errors;
	}
    
	@Override
	public boolean isValidConfig() {
		return attributeDefinition != null;
	}

}
