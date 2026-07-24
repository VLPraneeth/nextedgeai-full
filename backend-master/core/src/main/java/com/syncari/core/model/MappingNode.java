package com.syncari.core.model;

import com.syncari.core.datatype.BooleanType;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.ValidationError;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.SerializationUtils;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@Data
@Accessors(chain = true)
public class MappingNode extends UUIDAuditModel {
    @NotNull
    private Scope scope;
    @NotNull
    private String name;
    @NotNull
    private String apiName;
    @NotNull
    private NodeConfiguration configuration;

    @NotNull
    private String mappingGraphId;
    
    private String groupId;
    
    private String originalId;

    public void validate(String graphName) {
        configuration.validate(graphName, name);
    }
    
    public List<ValidationError> validateWithoutException(String graphName) {
        return configuration.validateWithoutException(scope, graphName, id, name);
    }

    public MappingNodeType getType() {
        return configuration.getNodeType();
    }

    public <T extends NodeConfiguration> T getTypedConfiguration() {
        return (T) configuration;
    }

    public Object getConfig(String configKey) {
        return configuration.getConfigMap().get(configKey);
    }

    public String getStringConfig(String configKey) {
        final Map<String, Object> configMap = configuration.getConfigMap();
        return configMap.containsKey(configKey) ? configMap.get(configKey).toString() : null;
    }


    public Optional<String> getEntityDefinitionId() {
        if (getTypedConfiguration().getConfigMap().containsKey("entityDefinition")) {
            return Optional.of(getTypedConfiguration().getConfigMap().get("entityDefinition").toString());
        }
        return Optional.empty();
    }

    public Optional<OutputPort> getOutputPort() {
        return configuration.getOutputPort();
    }

    public  Optional<InputPort> getInputPort() {
        return configuration.getInputPort();
    }

    public MappingNode copy(String id, String name, String mappingGraphId, String groupId){
        final MappingNode clone = SerializationUtils.clone(this);
        clone.setId(id);
        clone.setName(name);
        clone.setMappingGraphId(mappingGraphId);
        clone.setGroupId(groupId);
        return clone;

    }

    public boolean isCoreNode(){
        return MappingNodeType.CORE_ENTITY == getType() || MappingNodeType.CORE_ATTRIBUTE == getType();
    }

    public boolean isSourceNode(){
        return MappingNodeType.ENTITY_SOURCE == getType() || MappingNodeType.ATTRIBUTE_SOURCE == getType();
    }

    public boolean isFalsePredicateNode(){
        if(!isPredicateNode()) return false;
        SimpleFunctionNodeConfig fnNodeConfig = this.getTypedConfiguration();
        var predicateType = fnNodeConfig.getConfig("value", new BooleanType());
        return predicateType.isPresent() && !predicateType.get();
    }

    public boolean isPredicateNode() {
        if (MappingNodeType.FUNCTION != getType()) return false;
        SimpleFunctionNodeConfig fnNodeConfig = this.getTypedConfiguration();
        FunctionDefinition fnDef = fnNodeConfig.getFunctionCall().getFunctionDefinition();
        return fnDef != null && "predicate".equals(fnDef.getName());
    }

    public boolean isFunctionNode() {
        return MappingNodeType.FUNCTION.equals(getType());
    }

    public boolean isActionNode() {
        return MappingNodeType.ACTION.equals(getType());
    }

}

