package com.syncari.core.functions;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.syncari.core.model.util.ErrorCode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.core.DataTransformer;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Connector;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.quickstart.v2.dependency.DefaultPredicateDependencyGenerator;
import com.syncari.core.quickstart.v2.dependency.DependencyUtil;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.DataServiceFactory;
import com.syncari.core.service.SchemaService;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.validation.ValidationContext;
import com.syncari.utils.Pair;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component(FunctionConstants.LOOKUP_EXTERNAL_RECORD)
public class LookUpExternalRecordFunction extends DefaultFunction {


	@Autowired
    SchemaService schemaService;
    
    @Autowired
    ConnectorService connectorService;
    
    @Autowired
    DataServiceFactory factory;
    
    @Autowired
    DataTransformer transformer;

    @Autowired
    DefaultPredicateDependencyGenerator defaultPredicateDependencyGenerator;

    private static final String CONDITION = "query";
    private static final String POSITIONAL_PARAMS = "positionalParams";
    private static final String SYNAPSE_ID = "synapseId";

    @Override
    public void validate(ValidationContext validationContext) {
    	var errors = validateWithoutException(validationContext);
    	if(errors != null && !errors.isEmpty()) {
    		throw new SyncariValidationException(errors.get(0).getMessage());
    	}
    }
    
    @Override
    public List<ValidationError> validateWithoutException(ValidationContext validationContext) {
    	List<ValidationError> errors = new ArrayList<ValidationError>();
    	errors.addAll(super.validateWithoutException(validationContext));
        MappingNode node = validationContext.getNode();
        MappingGraph graph = validationContext.getGraph();
        
        if (graph == null || node == null)
			return errors;

        SimpleFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
        FunctionDefinition funcDef = functionNodeConfig.getFunctionCall().getFunctionDefinition();
        Map<String, String> configNameLabelMap = funcDef.getConfiguration().stream().collect(Collectors.toMap(c -> c.getName(), c -> c.getLabel()));
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();
        var synapseId = configMap.get(SYNAPSE_ID);
        var condition = configMap.get(CONDITION);
        var params = configMap.get(POSITIONAL_PARAMS);
        
        if(synapseId != null) {
        	Optional<Connector> connector = connectorService.find(synapseId.toString());
        	validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), connector.isEmpty(),
        			i18n("lookup_external_synapse_invalid", configNameLabelMap.get(SYNAPSE_ID), synapseId, node.getName(),
        					graph.getName()), ErrorCode.E1110.getCode()).ifPresent(e -> errors.add(e));
        	
        }
		if (condition != null) {
			List<String> parts = new ArrayList<>();
			if(params != null && !StringUtils.isBlank(params.toString().trim())) {
	        	parts = Arrays.asList(params.toString().trim().split(","));
	        }
			validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
					TokenHelper.hasTokens(condition.toString()), i18n("lookup_external_condition_invalid",
							configNameLabelMap.get(CONDITION), condition, node.getName(), graph.getName()), ErrorCode.E1111.getCode())
									.ifPresent(e -> errors.add(e));
			if (TokenHelper.hasTokens(condition.toString())) {
				return errors;
			}
			int paramCount = parts.size();
			validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
					StringUtils.countMatches(condition.toString(), "?") != paramCount, i18n("lookup_external_params_invalid",
							configNameLabelMap.get(CONDITION), condition, node.getName(), graph.getName()), ErrorCode.E1112.getCode())
			.ifPresent(e -> errors.add(e));
		}
        
        return errors;
    }

    @Override
    public void extract(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode node = context.getCurrentNode();
        super.extract(context);
        SharableFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();
        // 1. Selected Synapse
        var synapseId = configMap.get(SYNAPSE_ID).toString();
        Connector synapse = connectorService.find(synapseId).orElseThrow();
        qsConfig.addDependency(DependencyUtil.getConnectorDependency(synapse));

    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode sharableNode = context.getCurrentNode();
        SharableFunctionNodeConfig functionNodeConfig = sharableNode.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();

        // 1. Selected Synapse
        var synapseId = configMap.get(SYNAPSE_ID).toString();
        Connector resolvedSynapse = (Connector) qsConfig.getResolvedValueByType(synapseId, QSDependency.Type.Connector);
        if(resolvedSynapse != null){
            configMap.put(SYNAPSE_ID, resolvedSynapse.getId());
        }
        
        functionNodeConfig.getFunctionCall().setConfig(configMap);
        functionNodeConfig.getFunctionCall().setParams(resolveParams(context, functionNodeConfig));
        sharableNode.setConfiguration(functionNodeConfig);

        return sharableGraphTransformer.toMappingNode(sharableNode, context.getCurrentPipeline());
    }
    
    @Override
    public List<Pair<String, String>> toUserFriendlyValue(DiffInfoContext context, String configProperty) {
    	if (SYNAPSE_ID.equals(configProperty) && context != null && context.getCurrentNode() != null) {
    		Map<String, Object> configMap = context.getCurrentNode().getConfiguration().getConfigMap();
    		if(configMap == null) {
    			configMap = Map.of();
    		}
            var synapseId = configMap.get(SYNAPSE_ID);
            if(synapseId != null) {
            	var con = connectorService.find(synapseId.toString());
            	if(con.isPresent()) {
            		return List.of(Pair.of(configProperty, con.get().getName()));
            	}
            }
    	}
    	return super.toUserFriendlyValue(context, configProperty);
    }

}
