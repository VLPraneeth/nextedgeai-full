package com.syncari.core.model.misc.sharable;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.InputPort;
import com.syncari.core.model.OutputPort;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.ValidationError;

import lombok.Data;
import lombok.experimental.Accessors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.syncari.core.utils.ValidationUtils.validateCondition;

@Data
@Accessors(chain = true)
public class SharableFunctionNodeConfig implements SharableNodeConfiguration {

    private SharableFunctionCall functionCall;

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
    public Map<String, Object> getConfigMap() {
        var config = new HashMap<String, Object>();
        config.put("definition", functionCall.getFunctionDefinition().getId());
        config.putAll(functionCall.getConfig());
        return config;
    }

	@Override
	public List<ValidationError> validateWithoutException(Scope scope, String graphName, String nodeId,
			String nodeName) {
		return List.of();
	}
	
	public SharableFunctionNodeConfig copy() {
	  SharableFunctionNodeConfig copy = new SharableFunctionNodeConfig();
      copy.functionCall = this.functionCall != null ? functionCall.copy() : null;
      return copy;
  }
}
