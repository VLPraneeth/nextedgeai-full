package com.syncari.core.functions;

import com.syncari.core.Features;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.NodeInfoContext;
import com.syncari.core.pipeline.NodeInfoFactory;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.service.FeatureService;
import com.syncari.core.service.NodeInfoService;
import com.syncari.core.validation.ValidationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component(FunctionConstants.PREDICATE)
public class PredicateFunction extends BooleanFunction {

    @Autowired
    FeatureService featureService;
    @Autowired
    NodeInfoFactory nodeInfoFactory;

    @Override
    public void validate(ValidationContext validationContext) {
        var errors = validateWithoutException(validationContext);
        if(errors != null && !errors.isEmpty()) {
            throw new SyncariValidationException(errors.get(0).getMessage());
        }
    }

    @Override
    public List<ValidationError> validateWithoutException(ValidationContext validationContext) {
        return super.validateWithoutException(validationContext);
    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
        SharableNode sharableNode = context.getCurrentNode();
        SharableFunctionNodeConfig functionNodeConfig = sharableNode.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();
        functionNodeConfig.getFunctionCall().setConfig(configMap);
        functionNodeConfig.getFunctionCall().setParams(resolveParams(context, functionNodeConfig));
        sharableNode.setConfiguration(functionNodeConfig);
        MappingNode functionNode = sharableGraphTransformer.toMappingNode(sharableNode, context.getCurrentPipeline());
        return functionNode;
    }
    
    @Override
    public String inferNodeOutputDatatype(NodeInfoContext context) {
        MappingGraph graph = context.getPipeline();
        MappingNode node = context.getCurrentNode();

        var prevNode = graph.getPreviousNodes(node).stream().findFirst();
        if(prevNode.isPresent()){
            NodeInfoService nodeInfoService = nodeInfoFactory.getNodeInfoService(prevNode.get());
            context.setCurrentNode(prevNode.get());
            return nodeInfoService.inferNodeOutputDatatype(context);
        }
        SimpleFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
        return functionNodeConfig.getOutputPorts().stream().findFirst().map(o -> o.getDatatype()).orElse(StringType.VALUE).getName();

    }
}
