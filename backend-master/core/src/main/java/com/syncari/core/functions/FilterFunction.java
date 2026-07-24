package com.syncari.core.functions;

import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.core.pipeline.NodeInfoContext;
import com.syncari.core.pipeline.NodeInfoFactory;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.core.pipeline.expression.VariableExpression;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.quickstart.v2.dependency.DefaultPredicateDependencyGenerator;
import com.syncari.core.quickstart.v2.dependency.ExpressionDependencyResolver;
import com.syncari.core.quickstart.v2.dependency.ExpressionDependencyVisitor;
import com.syncari.core.service.NodeInfoService;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.validation.ExpressionValidatorVisitor;
import com.syncari.core.validation.GraphValidationUtil;
import com.syncari.core.validation.PredicateValidator;
import com.syncari.core.validation.ValidationContext;
import com.syncari.utils.Pair;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component(FunctionConstants.FILTER)
public class FilterFunction extends DefaultFunction implements PredicateValidator {

    @Autowired
    DefaultPredicateDependencyGenerator defaultPredicateDependencyGenerator;

    @Autowired
    NodeInfoFactory nodeInfoFactory;
    @Autowired
    TokenHelper tokenHelper;

    private final String INCOMING_CHANGE = "incoming_change";
    private final Pattern FIELD_OUTPUT_PATTERN = Pattern.compile("field_(\\w+)");
    private final Pattern LOOKUP_OUTPUT_PATTERN = Pattern.compile("Records from (\\w+)");
    private final Pattern NODE_OUTPUT_PATTERN = Pattern.compile("output_(\\w+)\\.x\\.(\\w+)");
    private final Pattern ACTION_NODE_OUTPUT_PATTERN = Pattern.compile("action_output_(\\w+)\\_(\\w+)");

    private final Pattern DESTINATION_OUTPUT_PATTERN = Pattern.compile("destination_status|destination_error|destination_operation");

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
        /* config validation:
        predicate - check expression. If its a VariableExpression, the left should be one of:
        - valid field from connected source entity
        - valid field from core entity
        - incoming_change
        - output from previous node (output_{attributeId}.x.typedValue)
        - lookup output from prev node (output_{attributeId}.x.lookupResult)
         */
        SimpleFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();
        var predicate = (Map<String, Object>) configMap.get(PREDICATE);
        try {
        	Expression filterExpression = new PredicateParser().fromMap(predicate);
        	ExpressionValidatorVisitor visitor = new ExpressionValidatorVisitor(this, validationContext);
        	filterExpression.accept(visitor);
		} catch (SyncariValidationException e) {
			log.error("validation error occured ", e);
			if(e.getMessage().contains("Unknown operator ")) {
				errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(i18n("filter_operator_required",
						validationContext.getNode().getName(), validationContext.getGraph().getName())));
			} else {
				errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(e.getMessage()));
			}
		}
        return errors;
    }

    @Override
    public void validateVarExpression(VariableExpression expression, ValidationContext validationContext) {
        String variableName = expression.getVariableName();
        validateVariableName(variableName, validationContext);
    }

    private void validateVariableName(String variableName, ValidationContext validationContext){
        if(INCOMING_CHANGE.equals(variableName)) return;
        if(variableName != null && tokenHelper.hasTokens(variableName)) return;

        MappingGraph graph = validationContext.getGraph();
        MappingNode currentNode = validationContext.getNode();
        Matcher attribMatcher = FIELD_OUTPUT_PATTERN.matcher(variableName);
        Matcher lookupMatcher = LOOKUP_OUTPUT_PATTERN.matcher(variableName);
        Matcher nodeMatcher = NODE_OUTPUT_PATTERN.matcher(variableName); //
        Matcher actionNodeMatcher = ACTION_NODE_OUTPUT_PATTERN.matcher(variableName);
        Matcher destinationNodeMatcher = DESTINATION_OUTPUT_PATTERN.matcher(variableName);
        String attributeId = attribMatcher.find() ? attribMatcher.group(1) : null;
        String tmpNodeId = nodeMatcher.find() ? nodeMatcher.group(1) : null;
        String resultType = nodeMatcher.find() ? nodeMatcher.group(2) : null;
        if (resultType == null && actionNodeMatcher.find()) {
            tmpNodeId = actionNodeMatcher.group(1);
            resultType = actionNodeMatcher.find() ? actionNodeMatcher.group(2) : null;
        }
        final String nodeId = tmpNodeId;

        if(!lookupMatcher.find() && !destinationNodeMatcher.find()) {
            validateCondition(StringUtils.isBlank(attributeId) && StringUtils.isBlank(nodeId),
                    i18n("filter_invalid_predicate", validationContext.getNode().getName(), validationContext.getGraph().getName()));
        }

        // check if function is connected to source node or core node
        MappingNode coreNode = graph.getCoreNode();
        boolean isCoreConnected = graph.pathToNodeMatches(currentNode, n -> n.getId().equals(coreNode.getId()));

        if(isCoreConnected){
            validateCondition(!StringUtils.isBlank(attributeId) &&
                            !GraphValidationUtil.isAttributeRefFromCoreEntity(attributeId, validationContext),
                    i18n("filter_invalid_predicate", validationContext.getNode().getName(), validationContext.getGraph().getName()));
        } else {
            validateCondition(!StringUtils.isBlank(attributeId) &&
                            !GraphValidationUtil.isAttributeRefFromSourceEntity(attributeId, validationContext) &&
                            !GraphValidationUtil.isAttributeRefFromCoreEntity(attributeId, validationContext),
                    i18n("filter_invalid_predicate", validationContext.getNode().getName(), validationContext.getGraph().getName()));
        }

        Optional<MappingNode> prevNode = validationContext.getGraph().getInboundEdges(validationContext.getNode()).stream()
                .map(edge -> edge.getSourceStage())
                .filter(node -> node.getId().equals(nodeId)).findFirst();
        validateCondition(!StringUtils.isBlank(nodeId) && prevNode.isEmpty(),
                i18n("filter_invalid_predicate", validationContext.getNode().getName(), validationContext.getGraph().getName()));

        if(!StringUtils.isBlank(nodeId) && prevNode.isPresent()) {
            validateCondition("lookupResult".equals(resultType)
                            && !isLookupFunction(prevNode.get().getApiName()),
                    i18n("filter_invalid_predicate", validationContext.getNode().getName(), validationContext.getGraph().getName()));
        }
    }

    private boolean isLookupFunction(String apiName) {
        return apiName.equalsIgnoreCase("lookupSyncariRecord") ||
                apiName.equalsIgnoreCase("advancedLookupSyncariRecord")
                || apiName.equalsIgnoreCase("advancedLookUpSyncariRecordOnField");
    }

    @Override
    public void extract(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode node = context.getCurrentNode();
        super.extract(context);
        SharableFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();
        var predicate = (Map<String, Object>) configMap.get(PREDICATE);
        Expression filterExpression = new PredicateParser().fromMap(predicate);
        ExpressionDependencyVisitor visitor = new ExpressionDependencyVisitor(defaultPredicateDependencyGenerator, context);
        filterExpression.accept(visitor);
    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
        SharableNode sharableNode = context.getCurrentNode().copy();
        SharableFunctionNodeConfig functionNodeConfig = sharableNode.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();
        Map<String, Object> predicate = (Map<String, Object>) configMap.get(PREDICATE);

        ExpressionDependencyResolver resolver = new ExpressionDependencyResolver(context);
        var resolvedPredicate = resolver.fromMap(predicate);
        configMap.put(PREDICATE, resolvedPredicate);
        functionNodeConfig.getFunctionCall().setConfig(configMap);
        functionNodeConfig.getFunctionCall().setParams(resolveParams(context, functionNodeConfig));
        sharableNode.setConfiguration(functionNodeConfig);

        return sharableGraphTransformer.toMappingNode(sharableNode, context.getCurrentPipeline());
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
    
    @Override
	public List<Pair<String, String>> toUserFriendlyValue(DiffInfoContext context, String configProperty) {
		if (context != null && context.getCurrentNode() != null) {
			if ("value".equals(configProperty)) { // Skip the property
				return List.of();
			}
		}
		return super.toUserFriendlyValue(context, configProperty);
	}
    
    @Override
    public boolean postProcess(QuickStartContext context) {
      SharableNode sharableNode = context.getCurrentNode();
      SharableFunctionNodeConfig functionNodeConfig = sharableNode.getTypedConfiguration();
      Map<String, Object> configMap = functionNodeConfig.getConfigMap();

      Map<String, Object> predicate = (Map<String, Object>) configMap.get(PREDICATE);
      ExpressionDependencyResolver resolver = new ExpressionDependencyResolver(context);
      var resolvedPredicate = resolver.fromMap(predicate);
      SimpleFunctionNodeConfig nodeConfig = context.getCurrentMappingNode().getTypedConfiguration();
      nodeConfig.getFunctionCall().getConfig().put(PREDICATE, resolvedPredicate);
      return true;
    }
}
