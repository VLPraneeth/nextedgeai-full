package com.syncari.core.model.misc.sharable;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AdvancedDedupeConfig;
import com.syncari.core.model.DataAuthority;
import com.syncari.core.model.DedupeConfig;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.InputPort;
import com.syncari.core.model.OutputPort;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.ValidationError;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class SharableCoreEntityNodeConfig implements SharableNodeConfiguration {

    private EntityDefinition entityDefinition;
    private DataAuthority dataAuthority = DataAuthority.none();
    private DedupeConfig dedupeConfig = DedupeConfig.doNothing();
    private AdvancedDedupeConfig advancedDedupeConfig;

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
        config.putAll(dataAuthority.getConfigMap());
        config.putAll(dedupeConfig.getConfigMap());
        if(advancedDedupeConfig !=null){
            config.putAll(advancedDedupeConfig.getConfigMap());
        }
        return config;
    }

	@Override
	public List<ValidationError> validateWithoutException(Scope scope, String graphName, String nodeId,
			String nodeName) {
		return List.of();
	}
}
