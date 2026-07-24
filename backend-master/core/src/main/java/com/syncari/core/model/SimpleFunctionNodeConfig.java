package com.syncari.core.model;

import com.syncari.core.datatype.Datatype;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.syncari.core.utils.ValidationUtils.validateCondition;

@Data
@Accessors(chain = true)
public class SimpleFunctionNodeConfig implements NodeConfiguration {

    private FunctionCall functionCall;

    @Override
    public MappingNodeType getNodeType() {
        return MappingNodeType.FUNCTION;
    }

    @Override
    public String getApiName() {
        return functionCall.getFunctionDefinition().getName();
    }

    @Override
    public List<OutputPort> getOutputPorts() {
        return List.of(OutputPort.of(functionCall.getFunctionDefinition().getOutputType()));
    }

    @Override
    public List<InputPort> getInputPorts() {

        Stream<InputPort> inputPortStream = functionCall.getFunctionDefinition().getPositionalParams().stream().map(p ->
                InputPort.of(p.getDatatype())
        );
        List<InputPort> inputPorts = inputPortStream.collect(Collectors.toList());
        functionCall.getFunctionDefinition().getVarargParam().stream().forEach(vararg -> {
            inputPorts.add(InputPort.many(vararg.getDatatype()));
        });
        return inputPorts;

    }

    @Override
    public void validate(String graphName, String nodeName) {
    	var errors = validateWithoutException(null, graphName, null, nodeName);
    	if(errors != null && !errors.isEmpty()) {
    		throw new SyncariValidationException(errors.get(0).getMessage());
    	}

    }
    
    @Override
    public List<ValidationError> validateWithoutException(Scope scope, String graphName, String nodeId, String nodeName) {
    	List<ValidationError> errors = new ArrayList<>();
		validateCondition(ValidationError.scopedError(scope, nodeId), functionCall == null,
				"Function Call is null in %s pipeline, node %s", ErrorCode.E1178.getCode(), graphName, nodeName).ifPresent(e->errors.add(e));
		if(functionCall != null) {
			errors.addAll(functionCall.validateWithoutException(scope, graphName, nodeId, nodeName));
		}
    	return errors;
    }

    @Override
    public Map<String, Object> getConfigMap() {
        var config = new HashMap<String, Object>();
        config.put("definition", functionCall.getFunctionDefinition().getId());
        config.putAll(functionCall.getConfig());
        return config;
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
    public <T> Optional<T> getConfig(String key, Datatype<T> type){
        return  Optional.ofNullable(type.convert(getFunctionCall().getConfig(key,type)));
    }
    public <T> T getRequiredConfig(String key, Datatype<T> type){
        Optional<T> maybeConfig = getFunctionCall().getConfig(key, type);
        return  maybeConfig.map(c->type.convert(c)).orElse(null);
    }

}
