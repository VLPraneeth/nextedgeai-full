package com.syncari.core.model;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.NodeConfigurationVisitor;
import com.syncari.core.pipeline.PipelinePublishedEvent;
import com.syncari.core.validation.NodeValidatorVisitor;
import com.syncari.core.validation.ValidationContext;
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
@Accessors(chain = true)
public class CoreEntityNodeConfig implements NodeConfiguration {

    private EntityDefinition entityDefinition;

    private DataAuthority dataAuthority = DataAuthority.none();

    private DedupeConfig dedupeConfig = DedupeConfig.doNothing();

    private AdvancedDedupeConfig advancedDedupeConfig;

    private boolean realtime = false;
    private boolean enableNodeLogs = false;

    @Override
    public MappingNodeType getNodeType() {
        return MappingNodeType.CORE_ENTITY;
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
        return List.of(InputPort.many());
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
        Map<String, Object> config = new HashMap<>();
        if(entityDefinition == null){
            throw new RuntimeException("EntityDefinition can't be null for CoreEntityNodeConfig");
        }
        config.put("entityDefinition",entityDefinition.getId());
        config.put("realtime", realtime);
        config.put("enableNodeLogs", enableNodeLogs);
        config.putAll(dataAuthority.getConfigMap());
        config.putAll(dedupeConfig.getConfigMap());
        if(advancedDedupeConfig !=null){
            config.putAll(advancedDedupeConfig.getConfigMap());
        }
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
		validateCondition(ValidationError.scopedError(scope, nodeId), entityDefinition == null,
                "Core entity is required in %s pipeline, node %s", ErrorCode.E1181.getCode(), graphName,nodeName).ifPresent(e->errors.add(e));
		return errors;
	}

	public void postPublish(PipelinePublishedEvent context, EntityDefinition entityDefinition) {
        if (advancedDedupeConfig != null) {
            advancedDedupeConfig.postPublish(context, entityDefinition);
        }
    }

}
