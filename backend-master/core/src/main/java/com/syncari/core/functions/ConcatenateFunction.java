package com.syncari.core.functions;

import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.quickstart.v2.dependency.DependencyUtil;
import com.syncari.core.service.SchemaService;
import com.syncari.utils.Pair;

import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component(FunctionConstants.CONCATENATE)
public class ConcatenateFunction extends DefaultFunction {
	
	@Autowired
	SchemaService schemaService;

    public static final String SEPARATOR = "separator";
    public static final String VALUES = "values";

    private final Pattern NODE_OUTPUT_PATTERN = Pattern.compile("output_(\\w+)\\.x\\.(\\w+)");
    private final Pattern ACTION_NODE_OUTPUT_PATTERN = Pattern.compile("action_output_(\\w+)\\_(\\w+)");

    @Override
    public void extract(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode node = context.getCurrentNode();
        super.extract(context);
        SharableFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();
        // 1. Extract dependency from values
        var values = (List<String>) configMap.get(VALUES);
        values.forEach(val -> {

            Matcher nodeOutputMatcher = NODE_OUTPUT_PATTERN.matcher(val);
            Matcher actionOutputMatcher = ACTION_NODE_OUTPUT_PATTERN.matcher(val);

            if(ObjectId.isValid(val)){
                // if variable is of type ObjectId and a valid attribute reference then add that as dependency
                Optional<AttributeDefinition> attrib = context.getAttribute(val);
                attrib.ifPresent(attributeDefinition -> qsConfig.addDependency(DependencyUtil.getAttributeDependency(attributeDefinition)));
            } else if(nodeOutputMatcher.matches()){
                qsConfig.addDependency(DependencyUtil.getNodeOutputDependency(val));
            } else if(actionOutputMatcher.matches()){
                qsConfig.addDependency(DependencyUtil.getActionNodeOutputDependency(val));
            }
        });
    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode sharableNode = context.getCurrentNode();
        SharableFunctionNodeConfig functionNodeConfig = sharableNode.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();

        // 1. Resolve dependency of values
        var values = (List<String>) configMap.get(VALUES);
        var resolvedValues = values.stream().map(val -> {

            Matcher nodeOutputMatcher = NODE_OUTPUT_PATTERN.matcher(val);
            Matcher actionOutputMatcher = ACTION_NODE_OUTPUT_PATTERN.matcher(val);

            if(ObjectId.isValid(val)){
                AttributeDefinition resolvedAttrib = (AttributeDefinition) qsConfig.getResolvedValueByType(val, QSDependency.Type.Attribute);
                return resolvedAttrib.getId();
            } else if(nodeOutputMatcher.matches()){
                return qsConfig.getResolvedValueByType(val, QSDependency.Type.Node_Output_Ref);
            } else if(actionOutputMatcher.matches()){
                return qsConfig.getResolvedValueByType(val, QSDependency.Type.Action_Node_Output_Ref);
            }
            return val;
        }).collect(Collectors.toList());

        configMap.put(VALUES, resolvedValues);
        functionNodeConfig.getFunctionCall().setConfig(configMap);
        functionNodeConfig.getFunctionCall().setParams(resolveParams(context, functionNodeConfig));
        sharableNode.setConfiguration(functionNodeConfig);

        return sharableGraphTransformer.toMappingNode(sharableNode, context.getCurrentPipeline());
    }
    
    @Override
	public List<Pair<String, String>> toUserFriendlyValue(DiffInfoContext context, String configProperty) {
    	if (context != null && context.getCurrentNode() != null) {
    		if (VALUES.equals(configProperty)) {
    			SimpleFunctionNodeConfig functionNodeConfig = context.getCurrentNode().getTypedConfiguration();
    			Map<String, Object> configMap = functionNodeConfig.getConfigMap();
    			var values = (List<String>) configMap.getOrDefault(VALUES, configProperty);
    			return List.of(Pair.of(configProperty, values.stream().map(val -> {
    	            if(ObjectId.isValid(val)){
    	                AttributeDefinition resolvedAttrib = schemaService.getAttribute(val);
    	                return resolvedAttrib.getDisplayName();
    	            } else {
    	            	return val;
    	            }
    			}).collect(Collectors.toList()).toString()));
    		}
    	}
		return super.toUserFriendlyValue(context, configProperty);
	}
}
