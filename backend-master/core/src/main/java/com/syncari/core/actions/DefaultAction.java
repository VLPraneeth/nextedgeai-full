package com.syncari.core.actions;

import com.syncari.core.actions.http.HttpActionProperties;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.sharable.SharableActionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.SharableGraphTransformer;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.core.pipeline.DiffInfoExpressionVisitor;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.PipelinePublishedEvent;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.quickstart.v2.dependency.DependencyService;
import com.syncari.core.quickstart.v2.dependency.DependencyUtil;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import com.syncari.core.service.ActionService;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.DiffInfoService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.utils.LookupCriteriaVisitor;
import com.syncari.core.utils.MongoCriteria;
import com.syncari.core.validation.TokenValidator;
import com.syncari.core.validation.ValidationContext;
import com.syncari.core.validation.ValidationService;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component
public class DefaultAction implements ValidationService, DependencyService, DiffInfoService, ActionExecutionService {
    private static final String SYNAPSE_ID = "synapseId";
    public static final String PREDICATE = "predicate";
    private static final String ATTACH_PREDICATE = "attachPredicate";

    @Autowired
    protected ActionService actionService;

    @Autowired
    protected SharableGraphTransformer sharableGraphTransformer;

    @Autowired
    protected TokenHelper tokenHelper;

    @Autowired
    protected ConnectorService connectorService;

    @Autowired
    protected SchemaService schemaService;

    @Autowired
    protected MappingNodeRepo nodeRepo;

    public void postPublish(PipelinePublishedEvent context) {

    }

    public void createIndexes(MappingGraph graph, MappingNode node) {
    }

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
                i18n("missing_field_in_validation_context", "graph"), ErrorCode.E1061.getCode()).ifPresent(e->errors.add(e));
        validateCondition(ValidationError.globalError(), validationContext.getNode() == null,
                i18n("missing_field_in_validation_context", "node"), ErrorCode.E1062.getCode()).ifPresent(e->errors.add(e));

        MappingNode node = validationContext.getNode();
        MappingGraph graph = validationContext.getGraph();
        if (graph == null || node == null)
            return errors;

        GenericActionConfig actionNodeConfig = node.getTypedConfiguration();
        Map<String, Object> configMap = actionNodeConfig.getConfigMap();

        Optional<ActionDefinition> actionDefMaybe = getActionDefinition(node, actionNodeConfig);
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), actionDefMaybe.isEmpty(),
                i18n("unknown_action_node", node.getApiName(), node.getName(), graph.getName()), ErrorCode.E1063.getCode())
                .ifPresent(e -> errors.add(e));
        actionDefMaybe.ifPresent(actionDef -> {
            actionDef.getConfiguration().forEach(configuration -> {
                var value = configMap.get(configuration.getName());
                validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                        configuration.isRequired() && (value == null || StringUtils.isBlank(value.toString())),
                        i18n("missing_config_from_node", configuration.getLabel(), node.getName(), graph.getName()), ErrorCode.E1064.getCode())
                        .ifPresent(e -> errors.add(e));
                if (value != null && StringUtils.isNotBlank(value.toString())) {
                    try {
                        TokenValidator.validateToken(tokenHelper, value, validationContext);
                    } catch (SyncariValidationException e) {
                        log.error("validation error occured ", e);
                        errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(e.getMessage()));
                    }
                }
            });
        });

        // action node should always have an inbound edge and node connected
        List<Edge> inboundEdges = graph.getInboundEdges(node);
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), inboundEdges.isEmpty(),
                i18n("action_node_missing_inbound", node.getName(), graph.getName()), ErrorCode.E1065.getCode()).ifPresent(e -> errors.add(e));
        return errors;
    }

    protected Optional<ActionDefinition> getActionDefinition(MappingNode node, GenericActionConfig actionNodeConfig) {
        Optional<ActionDefinition> actionDefMaybe;
        Object actionId = actionNodeConfig.getConfigMap().get("configId");
        if (actionId != null) {
            actionDefMaybe = actionService.findByIdAndPopulateSeed(actionId.toString());
        } else {
            actionDefMaybe = actionService.getAction(node.getApiName());
        }
        return actionDefMaybe;
    }

    @Override
    public void extract(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode node = context.getCurrentNode();
        SharableActionNodeConfig actionNodeConfig = node.getTypedConfiguration();
        Map<String, Object> configMap = actionNodeConfig.getConfigMap();
        Optional<ActionDefinition> actionDefMaybe = actionService.getAction(actionNodeConfig.getName());
        actionDefMaybe.ifPresent(actionDef -> {

            // If this is a HttpAction/Custom Action look for Auth credentials
            //TODO: Move this logic to CustomAction
            if (actionDef.isCustom()) {
                var actionProps = (HttpActionProperties) ((CustomActionDefinition) actionDef).getProperties();
                Optional<Connector> connectorMaybe = context.getConnector(actionProps.getAuthenticationInfo().getCredentialId());
                connectorMaybe.ifPresent(conn -> {
                    qsConfig.addDependency(DependencyUtil.getConnectorDependency(conn));
                });
            }
            actionDef.getConfiguration().forEach(configuration -> {
                var value = configMap.get(configuration.getName());
                if(value != null){
                    // Handle List values (e.g., recipients in SendMail)
                    if(value instanceof List){
                        List<?> listValue = (List<?>) value;
                        listValue.forEach(item -> {
                            if(item != null && !StringUtils.isBlank(item.toString())){
                                DependencyUtil.getTokenDependencies(item.toString()).forEach(d -> qsConfig.addDependency(d));
                            }
                        });
                    } else if(!StringUtils.isBlank(value.toString())){
                        DependencyUtil.getTokenDependencies(value.toString()).forEach(d -> qsConfig.addDependency(d));
                    }
                }
            });
        });
    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
        SharableNode sharableNode = context.getCurrentNode();
        SharableActionNodeConfig actionNodeConfig = sharableNode.getTypedConfiguration();
        var resolvedConfigMap = getDefaultResolvedConfig(context);
        actionNodeConfig.setConfigMap(resolvedConfigMap);
        sharableNode.setConfiguration(actionNodeConfig);
        MappingNode actionNode = sharableGraphTransformer.toMappingNode(sharableNode, context.getCurrentPipeline());
        return actionNode;
    }

    public Map<String, Object> getDefaultResolvedConfig(QuickStartContext context){
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode sharableNode = context.getCurrentNode();
        SharableActionNodeConfig actionNodeConfig = sharableNode.getTypedConfiguration();
        Map<String, Object> configMap = actionNodeConfig.getConfigMap();
        Optional<ActionDefinition> actionDefMaybe = actionService.getOrInstallAction(actionNodeConfig.getName());
        if (actionDefMaybe.isEmpty() || (actionDefMaybe.get().isCustom() && !actionDefMaybe.get().isApproved())) {
            throw new RuntimeException(String.format("No published action found with API name %s", actionNodeConfig.getName()));
        }

        actionDefMaybe.ifPresent(actionDef -> {
            actionDef.getConfiguration().forEach(configuration -> {
                var value = configMap.get(configuration.getName());
                if(value != null){
                    // Handle List values (e.g., recipients in SendMail)
                    if(value instanceof List){
                        List<?> listValue = (List<?>) value;
                        List<String> resolvedList = new ArrayList<>();
                        for(Object item : listValue){
                            if(item != null && !StringUtils.isBlank(item.toString())){
                                if(TokenHelper.hasTokens(item.toString())){
                                    var resolvedValue = (String) qsConfig.getResolvedValueByType(item.toString(), QSDependency.Type.Token);
                                    resolvedList.add(resolvedValue != null ? resolvedValue : item.toString());
                                } else {
                                    resolvedList.add(item.toString());
                                }
                            }
                        }
                        configMap.put(configuration.getName(), resolvedList);
                    } else if(!StringUtils.isBlank(value.toString())){
                        if(TokenHelper.hasTokens(value.toString())){
                            var resolvedValue = (String) qsConfig.getResolvedValueByType(value.toString(), QSDependency.Type.Token);
                            if(resolvedValue != null){
                                configMap.put(configuration.getName(), resolvedValue);
                            }
                        }
                    }
                }
            });
        });

        return configMap;
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
        } else if(PREDICATE.equals(configProperty)
                || ATTACH_PREDICATE.equals(configProperty)) {
            try {
                Map<String, Object> configMap = context.getCurrentNode().getConfiguration().getConfigMap();
                if(configMap == null) {
                    configMap = Map.of();
                }
                Map<String, Object> predicate = (Map<String, Object>) configMap.get(configProperty);
                Expression filterExpression = new PredicateParser().fromMap(predicate);
                var evaluator = new DiffInfoExpressionVisitor(schemaService, nodeRepo);
                filterExpression.accept(evaluator);
                return List.of(Pair.of(configProperty, evaluator.getValue()));
            } catch (Exception e) {
                log.error("Predicate to user friendly conversion failed", e);
            }
        }
        return DiffInfoService.super.toUserFriendlyValue(context, configProperty);
    }

    public <T> T getConfig(String configName, GenericActionConfig actionConfig) {
        return (T) actionConfig.getConfigMap().get(configName);
    }

    public <T> T getConfigOrDefault(String configName, GenericActionConfig actionConfig, T defaultValue) {
        return actionConfig.getConfigMap().containsKey(configName) ? (T) actionConfig.getConfigMap().get(configName) : defaultValue;
    }

    public <T> Optional<T> getConfig(String configName, GenericActionConfig actionConfig, Datatype type) {
        return Optional.ofNullable((T) type.convert(actionConfig.getConfigMap().get(configName)));
    }

    public <T> T getConfigOrDefault(String configName, GenericActionConfig actionConfig, Datatype type, T defaultValue) {
        return Optional.ofNullable((T) type.convert(actionConfig.getConfigMap().get(configName))).orElse(defaultValue);
    }

    public <T> List<T> getMultiValuedConfig(String configName, GenericActionConfig actionConfig, Datatype type) {
        final Object value = actionConfig.getConfigMap().get(configName);
        if (value == null) return List.of();
        if (List.class.isInstance(value)) {
            return (List<T>) List.class.cast(value).stream().map(o -> type.convert(o)).collect(Collectors.toList());
        }
        return (List<T>) List.of(type.convert(value));
    }

    @Override
    public ActionResult execute(GenericActionConfig actionConfig, GraphContext context) {
        return new ActionResult(false, "not implemented");
    }

    protected Optional<MongoCriteria> getCriteria(GraphContext context, EntityDefinition entity, Map<String, Object> predicates, EntityRepo entityRepo) {
        if (!MapUtils.isEmpty(predicates)) {
            Expression expression = new PredicateParser(StringUtils.EMPTY).fromMap(predicates);
            Optional<MongoCriteria> mongoCriteria = Optional.of(new LookupCriteriaVisitor(context, expression, tokenHelper,
                    entity.getIdToAttributes(), List.of(), (key) -> entityRepo.hasCaseInsensitiveIndexOnField(entity, key)));
            return mongoCriteria;
        } else {
            return Optional.empty();
        }
    }

}
