package com.syncari.core.functions;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.syncari.core.datatype.StringType;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.core.pipeline.DiffInfoExpressionVisitor;
import com.syncari.core.pipeline.DynamicDispatchVisitor;
import com.syncari.core.pipeline.FilterEvaluationVisitor;
import com.syncari.core.pipeline.NodeInfoContext;
import com.syncari.core.service.NodeInfoService;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Edge;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.ParameterValue;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.SharableGraphTransformer;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.PipelinePublishedEvent;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.quickstart.v2.dependency.DependencyService;
import com.syncari.core.quickstart.v2.dependency.DependencyUtil;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import com.syncari.core.service.DiffInfoService;
import com.syncari.core.service.FunctionService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.validation.TokenValidator;
import com.syncari.core.validation.ValidationContext;
import com.syncari.core.validation.ValidationService;
import com.syncari.utils.Pair;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DefaultFunction implements ValidationService, DependencyService, NodeInfoService, DiffInfoService {

    @Autowired
    FunctionService functionService;

    @Autowired
    SchemaService schemaService;

    @Autowired
    SharableGraphTransformer sharableGraphTransformer;

    @Autowired
    TokenHelper tokenHelper;

    @Autowired
    MappingNodeRepo nodeRepo;

    public static final String PREDICATE = "predicate";
    private static final String ATTACH_PREDICATE = "attachPredicate";
    private static final String SYNCARI_ENTITY_DEF_ID = "syncariEntityDefId";

    public void postPublish(PipelinePublishedEvent context) {

    }

    private final Pattern NODE_OUTPUT_PATTERN = Pattern.compile("output_(\\w+)\\.x\\.(\\w+)");
    private final Pattern ACTION_NODE_OUTPUT_PATTERN = Pattern.compile("action_output_(\\w+)\\_(\\w+)");
    protected final Pattern TEMP_VARIABLE_PATTERN = Pattern.compile("syncari.temp.(\\w+)");

    @Override
    public void validate(ValidationContext validationContext) {
        var errors = validateWithoutException(validationContext);
        if(errors != null && !errors.isEmpty()) {
            throw new SyncariValidationException(errors.get(0).getMessage());
        }
    }

    @Override
    public List<ValidationError> validateWithoutException(ValidationContext validationContext) {
        List<ValidationError> errors = new ArrayList<>();
        validateCondition(ValidationError.globalError(), validationContext.getGraph() == null,
                i18n("missing_field_in_validation_context", "graph"), ErrorCode.E1089.getCode()).ifPresent(e->errors.add(e));
        validateCondition(ValidationError.globalError(), validationContext.getNode() == null,
                i18n("missing_field_in_validation_context", "node"), ErrorCode.E1090.getCode()).ifPresent(e->errors.add(e));

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
                        configuration.isRequired() && (value == null || StringUtils.isBlank(value.toString())),
                        i18n("missing_config_from_node", i18n(configuration.getLabel()), node.getName(),
                                graph.getName()), ErrorCode.E1091.getCode()).ifPresent(e -> errors.add(e));

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
                i18n("node_not_connected_with_source_or_core", node.getName()), ErrorCode.E1092.getCode()).ifPresent(e -> errors.add(e));

        // check if a function is dangling node. All function should have inbound and outbound edges
        List<Edge> inboundEdges = graph.getInboundEdges(node);
        List<Edge> outboundEdges = graph.getOutboundEdges(node);
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), inboundEdges.isEmpty() || outboundEdges.isEmpty(),
                i18n("function_node_disconnected", node.getName(), graph.getName()), ErrorCode.E1093.getCode()).ifPresent(e -> errors.add(e));
        return errors;
    }

    @Override
    public void extract(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode node = context.getCurrentNode();
        SharableFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();
        Optional<FunctionDefinition> funcDefMaybe = functionService.findByNameAndScope(node.getApiName(), node.getScope());
        funcDefMaybe.ifPresent(funcDef -> {
            funcDef.getConfiguration().forEach(configuration -> {
                var value = configMap.get(configuration.getName());
                if(value != null && !StringUtils.isBlank(value.toString())){
                    DependencyUtil.getTokenDependencies(value.toString()).forEach(d -> qsConfig.addDependency(d));
                }
            });
        });

        // add node references in paramValue as dependency
        functionNodeConfig.getFunctionCall().getParams().forEach(paramValue -> {
            var nodeRefDep = DependencyUtil.getNodeOutputDependency(paramValue.getContextName());
            if(nodeRefDep != null){
                qsConfig.addDependency(nodeRefDep);
            }
        });
    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode sharableNode = context.getCurrentNode();
        SharableFunctionNodeConfig functionNodeConfig = sharableNode.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();
        Optional<FunctionDefinition> funcDefMaybe = functionService.findByNameAndScope(sharableNode.getApiName(), sharableNode.getScope());
        funcDefMaybe.ifPresent(funcDef -> {
            funcDef.getConfiguration().forEach(configuration -> {
                var value = configMap.get(configuration.getName());
                if(value != null && !StringUtils.isBlank(value.toString())){
                    if(TokenHelper.hasTokens(value.toString())){
                        var resolvedValue = (String) qsConfig.getResolvedValueByType(value.toString(), QSDependency.Type.Token);
                        if(resolvedValue != null){
                            configMap.put(configuration.getName(), resolvedValue);
                        }
                    }
                }
            });
        });

        functionNodeConfig.getFunctionCall().setParams(resolveParams(context, functionNodeConfig));
        functionNodeConfig.getFunctionCall().setConfig(configMap);
        sharableNode.setConfiguration(functionNodeConfig);
        MappingNode functionNode = sharableGraphTransformer.toMappingNode(sharableNode, context.getCurrentPipeline());
        return functionNode;
    }

    protected List<ParameterValue> resolveParams(QuickStartContext context, SharableFunctionNodeConfig functionNodeConfig){
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        List<ParameterValue> paramValues = new ArrayList<>();
        functionNodeConfig.getFunctionCall().getParams().forEach(paramValue -> {
            Matcher nodeOutputMatcher = NODE_OUTPUT_PATTERN.matcher(paramValue.getContextName());
            Matcher actionNodeOutputMatcher = ACTION_NODE_OUTPUT_PATTERN.matcher(paramValue.getContextName());
            if(nodeOutputMatcher.matches()){
                var resolvedValue = (String) qsConfig.getResolvedValueByType(paramValue.getContextName(), QSDependency.Type.Node_Output_Ref);
                String newValue = resolvedValue != null ? resolvedValue : paramValue.getContextName();

                paramValues.add(new ParameterValue(paramValue.getDataType(), newValue, paramValue.getParamSource()));
            } else if(actionNodeOutputMatcher.matches()){
                var resolvedValue = (String) qsConfig.getResolvedValueByType(paramValue.getContextName(), QSDependency.Type.Action_Node_Output_Ref);
                String newValue = resolvedValue != null ? resolvedValue : paramValue.getContextName();

                paramValues.add(new ParameterValue(paramValue.getDataType(), newValue, paramValue.getParamSource()));
            }
        });
        return paramValues;
    }

    @Override
    public String inferNodeOutputDatatype(NodeInfoContext context) {
        MappingNode node = context.getCurrentNode();
        SimpleFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
        return functionNodeConfig.getOutputPorts().stream().findFirst().map(o -> o.getDatatype()).orElse(StringType.VALUE).getName();
    }

    @Override
    public List<Pair<String, String>> toUserFriendlyValue(DiffInfoContext context, String configProperty) {
        if (context != null && context.getCurrentNode() != null) {
            SimpleFunctionNodeConfig functionNodeConfig = context.getCurrentNode().getTypedConfiguration();
            Map<String, Object> configMap = functionNodeConfig.getConfigMap();
            var syncariEntityDefId = configMap.getOrDefault(SYNCARI_ENTITY_DEF_ID, configProperty);
            if (SYNCARI_ENTITY_DEF_ID.equals(configProperty)) {
                Optional<EntityDefinition> entityDefinition = schemaService.getSyncariEntityById(String.valueOf(syncariEntityDefId));
                if(entityDefinition.isPresent()) {
                    return List.of(Pair.of(configProperty, entityDefinition.get().getDisplayName()));
                }
            } else if(PREDICATE.equals(configProperty)
                    || ATTACH_PREDICATE.equals(configProperty)) {
                try {
                    Map<String, Object> predicate = (Map<String, Object>) configMap.get(configProperty);
                    Expression filterExpression = new PredicateParser().fromMap(predicate);
                    var evaluator = new DiffInfoExpressionVisitor(schemaService, nodeRepo);
                    filterExpression.accept(evaluator);
                    return List.of(Pair.of(configProperty, evaluator.getValue()));
                }catch (Exception e) {
                    log.error("Predicate to user friendly conversion failed", e);
                }
            }
        }
        return DiffInfoService.super.toUserFriendlyValue(context, configProperty);
    }

    public void createIndexes(MappingGraph graph, MappingNode node) {}
}
