package com.syncari.core.model;

import com.syncari.core.DefaultActionProperties;
import com.syncari.core.actions.ActionProperties;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.NodeConfigurationVisitor;
import com.syncari.core.validation.NodeValidatorVisitor;
import com.syncari.core.validation.ValidationContext;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class GenericActionConfig extends AbstractActionConfig {

    private ActionProperties actionProperties = new DefaultActionProperties();

    private Map<String, Object> configMap = new HashMap<>();

    @Override
    public MappingNodeType getNodeType() {
        return MappingNodeType.ACTION;
    }

    @Override
    public void validate(String graphName, String nodeName) {
    	var errors = validateWithoutException(null, graphName, null, nodeName);
    	if(errors != null && !errors.isEmpty()) {
    		throw new SyncariValidationException(errors.get(0).getMessage());
    	}
    }

    @Override
    public void accept(NodeConfigurationVisitor visitor, MappingNode node) {
        visitor.visit(this, node);
    }

    public Map<String, Object> getConfigMap() {
        return new HashMap<>(configMap);
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
        return List.of();
    }

    public Object get(String configKey) {
        return configMap.get(configKey);
    }

    public Object getOrDefault(String configKey, Object defaultValue) {
        return configMap.getOrDefault(configKey, defaultValue);
    }

    public boolean containsKey(String configKey) {
        return configMap.containsKey(configKey);
    }
}
