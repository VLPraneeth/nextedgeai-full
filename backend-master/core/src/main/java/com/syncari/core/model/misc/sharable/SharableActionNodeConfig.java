package com.syncari.core.model.misc.sharable;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.ActionDefinition;
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
public class SharableActionNodeConfig implements SharableNodeConfiguration {

    private ActionDefinition actionDefinition;
    private String name;
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
    public Map<String, Object> getConfigMap() {
        configMap.put("name",name);
        return new HashMap<>(configMap);
    }

    @Override
    public String getApiName() {
        return actionDefinition.getName();
    }

    @Override
    public List<OutputPort> getOutputPorts() {
        return List.of(OutputPort.any());
    }

    @Override
    public List<InputPort> getInputPorts() {
        return List.of(InputPort.any());
    }

	@Override
	public List<ValidationError> validateWithoutException(Scope scope, String graphName, String nodeId,
			String nodeName) {
		return List.of();
	}
	
	public SharableActionNodeConfig copy() {
	  SharableActionNodeConfig copy = new SharableActionNodeConfig();
	  copy.actionDefinition = this.actionDefinition;
	  copy.name = this.name;
      copy.configMap = new HashMap<String, Object>(this.configMap);
      return copy;
  }

}
