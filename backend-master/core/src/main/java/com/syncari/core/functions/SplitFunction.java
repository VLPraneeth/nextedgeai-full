package com.syncari.core.functions;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Edge;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.validation.TokenValidator;
import com.syncari.core.validation.ValidationContext;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component(FunctionConstants.SPLIT)
public class SplitFunction extends DefaultFunction {

    private static final String DELIMITER = "delimiter";

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
    	validateCondition(ValidationError.globalError(), validationContext.getGraph() == null,
                i18n("missing_field_in_validation_context", "graph"), ErrorCode.E1137.getCode()).ifPresent(e->errors.add(e));
        validateCondition(ValidationError.globalError(), validationContext.getNode() == null,
                i18n("missing_field_in_validation_context", "node"), ErrorCode.E1138.getCode()).ifPresent(e->errors.add(e));

        MappingNode node = validationContext.getNode();
        MappingGraph graph = validationContext.getGraph();
        
        if (graph == null || node == null)
			return errors;

        SimpleFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();

        // validate configuration (required fields + token validation if present)
        Optional<FunctionDefinition> funcDefMaybe = functionService.findByNameAndScope(node.getApiName(), node.getScope());
        funcDefMaybe.ifPresent(funcDef -> {
            funcDef.getConfiguration().forEach(configuration -> {
                var value = configMap.get(configuration.getName());
				validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
						configuration.isRequired() && (value == null || StringUtils.isEmpty(value.toString())),
						i18n("missing_config_from_node", i18n(configuration.getLabel()), node.getName(),
								graph.getName()), ErrorCode.E1139.getCode()).ifPresent(e -> errors.add(e));

				if(value != null && StringUtils.isNotBlank(value.toString())) {
					try {
						TokenValidator.validateToken(tokenHelper, value, validationContext);
					}catch (SyncariValidationException e) {
						log.error("validation error occured ", e);
						errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(e.getMessage()));
					}
				}
            });
        });

        // check if function is connected to source node or core node
        List<MappingNode> connectedSources = graph.getSources().filter(src -> graph.pathToNodeMatches(node, n->n.getId().equals(src.getId())))
                .collect(Collectors.toList());
        MappingNode coreNode = graph.getCoreNode();
        boolean isCoreConnected = graph.pathToNodeMatches(node, n->n.getId().equals(coreNode.getId()));
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), !isCoreConnected && connectedSources.isEmpty(),
                i18n("node_not_connected_with_source_or_core", node.getName()), ErrorCode.E1140.getCode()).ifPresent(e -> errors.add(e));

        // check if a function is dangling node. All function should have inbound and outbound edges
        List<Edge> inboundEdges = graph.getInboundEdges(node);
        List<Edge> outboundEdges = graph.getOutboundEdges(node);
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), inboundEdges.isEmpty() || outboundEdges.isEmpty(),
                i18n("function_node_disconnected", node.getName(), graph.getName()), ErrorCode.E1141.getCode()).ifPresent(e -> errors.add(e));
        FunctionDefinition funcDef = functionNodeConfig.getFunctionCall().getFunctionDefinition();
        Map<String, String> configNameLabelMap = funcDef.getConfiguration().stream().collect(Collectors.toMap(c -> c.getName(), c -> c.getLabel()));

        var delimiter = configMap.getOrDefault(DELIMITER, "").toString();
		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), delimiter.isEmpty(),
				i18n("invalid_config_in_node", configNameLabelMap.get(DELIMITER), delimiter, node.getName(),
						graph.getName()), ErrorCode.E1142.getCode()).ifPresent(ee -> errors.add(ee));
		return errors;
    }
}
