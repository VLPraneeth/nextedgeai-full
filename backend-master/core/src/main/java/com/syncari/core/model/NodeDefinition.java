package com.syncari.core.model;

import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.misc.DraftableModel;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.*;

@Data
@Accessors(chain = true)
public abstract class NodeDefinition<T> extends DraftableModel<T> {
    String name;
    String displayName;
    String description;
    String helpSummary;

    String helpPath;
    String iconPath;
    Datatype outputType;
    boolean dynamicConfig;
    List<Parameter> positionalParams = new ArrayList<>();
    Parameter varargParam;
    Scope scope = Scope.ATTRIBUTE;
    Type type = Type.STANDARD;
    List<FunctionConfiguration> configuration = new ArrayList<>();
    EngineType engineType;

    Set<Datatype> availableForDataTypes = new HashSet<>();
    boolean hidden;
    private Set<AttributeDefinition> contextVariables = new HashSet<>();

    public boolean hasVararg(){
        return getVarargParam().isPresent();
    }

    public Optional<Parameter> getVarargParam(){
        return Optional.ofNullable(varargParam);
    }
    
    public boolean hasOutput(){
        return this.getClass().isAssignableFrom(ActionDefinition.class);
    }


    public T setName(String name) {
        this.name = name;
        return (T)this;
    }
    public T setHidden(boolean hidden) {
        this.hidden = hidden;
        return (T)this;
    }

    public T setDynamicConfig(boolean dynamicConfig) {
        this.dynamicConfig = dynamicConfig;
        return (T)this;
    }

    public NodeDefinition<T> addContextVariable(String variableName, String displayName, Datatype datatype, boolean isMultivalued) {
        contextVariables.add(new AttributeDefinition()
                .setApiName(variableName).setDisplayName(displayName).setDataType(datatype).setMultiValueField(isMultivalued));
        return this;
    }

    public NodeDefinition<T> addContextVariable(String variableName, Datatype datatype) {
        return addContextVariable(variableName, variableName, datatype, false);
    }

    public NodeDefinition<T> addContextVariable(String variableName, Datatype datatype, boolean multivalued) {
        return addContextVariable(variableName, variableName, datatype, multivalued);
    }

    public NodeDefinition<T> addContextVariable(String variableName) {
        return addContextVariable(variableName, variableName, StringType.VALUE, false);
    }

    public T setDisplayName(String displayName) {
        this.displayName = displayName;
        return (T)this;
    }

    public T setHelpSummary(String helpSummary) {
        this.helpSummary = helpSummary;
        return (T)this;
    }

    public T setHelpPath(String helpPath) {
        this.helpPath = helpPath;
        return (T)this;
    }

    public T setIconPath(String iconPath) {
        this.iconPath = iconPath;
        return (T)this;
    }

    public T setOutputType(Datatype outputType) {
        this.outputType = outputType;
        return (T)this;
    }

    public T setPositionalParams(List<Parameter> positionalParams) {
        this.positionalParams = positionalParams;
        return (T)this;
    }

    public T setVarargParam(Parameter varargParam) {
        this.varargParam = varargParam;
        return (T)this;
    }

    public T setScope(Scope scope) {
        this.scope = scope;
        return (T)this;
    }

    public T setType(Type type) {
        this.type = type;
        return (T)this;
    }
    public Optional<FunctionConfiguration> findConfiguration(String name){
        return configuration.stream().filter(f->name.equalsIgnoreCase(f.getName())).findFirst();
    }
    public T setConfiguration(List<FunctionConfiguration> configuration) {
        this.configuration = configuration;
        return (T)this;
    }

    public T setEngineType(EngineType engineType) {
        this.engineType = engineType;
        return (T)this;
    }

    public T setAvailableForDataTypes(Set<Datatype> dataTypes){
        this.availableForDataTypes = dataTypes;
        return (T)this;
    }

    public boolean isCustom(){
        return Type.CUSTOM == getType();
    }

}


