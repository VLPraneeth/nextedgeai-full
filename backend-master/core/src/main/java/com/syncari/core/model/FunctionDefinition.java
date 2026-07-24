package com.syncari.core.model;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.syncari.core.datatype.Datatype;

import com.syncari.core.datatype.ObjectType;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.Wither;
import org.apache.commons.lang3.SerializationUtils;

@Data
@Accessors(chain = true)
public class FunctionDefinition extends NodeDefinition<FunctionDefinition> {
    private List<Datatype> additionalInputTypes = List.of();
    public FunctionCall withParams(List<ParameterValue> params) {
        return new FunctionCall().setFunctionDefinition(this).setParams(params);
    }

    public FunctionCall withParams(ParameterValue... params) {
        List<ParameterValue> paramList = Arrays.asList(params);
        return new FunctionCall().setFunctionDefinition(this).setParams(paramList);
    }

    public FunctionDefinition addParameter(String name, Datatype datatype) {
        positionalParams.add(new Parameter(name, datatype, false));
        return this;
    }

    public Datatype getInputType(){
        return positionalParams.isEmpty()? ObjectType.VALUE : positionalParams.get(0).getDatatype();
    }
    
    public Parameter getParam() {
        return positionalParams.get(0);
    }

    public FunctionDefinition setVarargParam(String name, Datatype datatype) {
        varargParam = new Parameter(name, datatype, true);
        return this;
    }

    public String compile(List<ParameterValue> params) {
        return engineType.compile(this, params);
    }

    public FunctionDefinition clone(){
        FunctionDefinition clone = new FunctionDefinition()
                .setDisplayName(displayName)
                .setAvailableForDataTypes(availableForDataTypes)
                .setDynamicConfig(dynamicConfig)
                .setHidden(hidden)
                .setAdditionalInputTypes(additionalInputTypes)
                .setEngineType(engineType)
                .setPositionalParams(positionalParams)
                .setScope(scope)
                .setName(name)
                .setOutputType(outputType)
                .setHelpPath(helpPath)
                .setHelpSummary(helpSummary)
                .setIconPath(iconPath);
        clone.setDescription(description);
        clone.setType(type);
        clone.setConfiguration(configuration.stream().map(c-> SerializationUtils.clone(c)).collect(Collectors.toList()));
        clone.setId(this.getId());
        clone.setUpdatedAt(getUpdatedAt());
        clone.setCreatedAt(getCreatedAt());
        clone.setCreatedBy(getCreatedBy());
        clone.setUpdatedBy(getUpdatedBy());
        return clone;
    }

    @Override
    public FunctionDefinition makeCopy() {
        return this;
    }

    @Override
    public void copyValuesFrom(FunctionDefinition model) {
        // not implemented
    }
}
