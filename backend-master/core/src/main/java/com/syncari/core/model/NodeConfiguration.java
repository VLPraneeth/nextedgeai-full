package com.syncari.core.model;

import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.NodeConfigurationVisitor;
import com.syncari.core.pipeline.NodeInfoContext;
import com.syncari.core.pipeline.NodeInfoFactory;
import com.syncari.core.service.NodeInfoService;
import com.syncari.core.validation.NodeValidatorVisitor;
import com.syncari.core.validation.ValidationContext;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface NodeConfiguration extends Serializable {

    MappingNodeType getNodeType();

    String getApiName();

    List<OutputPort> getOutputPorts();

    List<InputPort> getInputPorts();

    default boolean isLeafNode() {
        return getOutputPorts().isEmpty();
    }

    default boolean isRootNode() {
        return getInputPorts().isEmpty();
    }

    default String getConfigType(){
        return this.getClass().getSimpleName();
    }

    void validate(String graphName, String nodeName);
    
    List<ValidationError> validateWithoutException(Scope scope, String graphName, String nodeId, String nodeName);

    Map<String, Object> getConfigMap();

    void accept(NodeConfigurationVisitor visitor, MappingNode node);

    void accept(NodeConfigurationVisitor visitor);

    void accept(NodeValidatorVisitor validator, ValidationContext validationContext);
    
    List<ValidationError> acceptWithoutException(NodeValidatorVisitor validator, ValidationContext validationContext);

    default Optional<OutputPort> getOutputPort(){
        final List<OutputPort> outputPorts = getOutputPorts();
        return outputPorts.isEmpty() ? Optional.empty() : Optional.of(outputPorts.get(0));
    }

    default Optional<InputPort> getInputPort(){
        final List<InputPort> inputPorts = getInputPorts();
        return inputPorts.isEmpty() ? Optional.empty() : Optional.of(inputPorts.get(0));
    }

    default String inferOutputDataType(NodeInfoFactory factory, NodeInfoContext context){
        NodeInfoService nodeInfoService = factory.getNodeInfoService(context.getCurrentNode());
        return nodeInfoService.inferNodeOutputDatatype(context);
    }
    
    default boolean isValidConfig() {
    	return true;
    }
}
