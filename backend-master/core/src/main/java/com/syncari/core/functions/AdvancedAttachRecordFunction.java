package com.syncari.core.functions;

import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.cache.CacheIndexAttribute;
import com.syncari.core.model.misc.sharable.SharableFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.core.pipeline.PipelinePublishedEvent;
import com.syncari.core.pipeline.SimpleExpressionVisitor;
import com.syncari.core.pipeline.expression.EqualIgnoreCase;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.core.pipeline.expression.VariableExpression;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.quickstart.v2.dependency.DefaultPredicateDependencyGenerator;
import com.syncari.core.quickstart.v2.dependency.ExpressionDependencyResolver;
import com.syncari.core.quickstart.v2.dependency.ExpressionDependencyVisitor;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.service.FeatureService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.utils.MongoUtils;
import com.syncari.core.utils.RedisUtils;
import com.syncari.core.validation.ExpressionValidatorVisitor;
import com.syncari.core.validation.PredicateValidator;
import com.syncari.core.validation.ValidationContext;
import com.syncari.utils.Pair;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Component(FunctionConstants.ADVANCED_ATTACH_RECORD)
@Slf4j
public class AdvancedAttachRecordFunction extends DefaultFunction implements PredicateValidator {

    @Autowired
    DefaultPredicateDependencyGenerator defaultPredicateDependencyGenerator;

    @Autowired
    SchemaService schemaService;

    @Autowired
    EntityRepo entityRepo;

    @Autowired
    TokenHelper tokenHelper;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private MongoUtils customerMongoUtils;

    @Autowired
    private FeatureService featureService;

    private static final String PREDICATE = "attachPredicate";
    private final Pattern FIELD_OUTPUT_PATTERN = Pattern.compile("field_(\\w+)");

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

        try {
            Map<String, Object> predicate = null;
            if(configMap.get(PREDICATE) instanceof Map) {
                predicate = (Map<String, Object>) configMap.get(PREDICATE);
            }
            Expression filterExpression = new PredicateParser().fromMap(predicate);
            ExpressionValidatorVisitor visitor = new ExpressionValidatorVisitor(this, validationContext);
            filterExpression.accept(visitor);
        } catch (SyncariValidationException e) {
            log.error("validation error occured ", e);
            errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(e.getMessage()));
        }

        MappingNode coreNode = graph.getCoreNode();

        boolean isCoreConnected = graph.pathToNodeMatches(coreNode, n->n.getId().equals(node.getId()));
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), !isCoreConnected,
                i18n("node_not_connected_with_core", node.getName(), graph.getName()), ErrorCode.E1081.getCode()).ifPresent(e -> errors.add(e));

        return errors;
    }

    @Override
    public void validateVarExpression(VariableExpression expression, ValidationContext validationContext) {
        String variableName = expression.getVariableName();
        validateVariableName(variableName, validationContext);
    }

    private void validateVariableName(String variableName, ValidationContext validationContext){
        Matcher attribMatcher = FIELD_OUTPUT_PATTERN.matcher(variableName);
        String attributeId = attribMatcher.find() ? attribMatcher.group(1) : null;
        validateCondition(StringUtils.isBlank(attributeId),
                i18n("attach_record_invalid_predicate", validationContext.getNode().getName(), validationContext.getGraph().getName()));
        EntityDefinition syncariEntity = validationContext.getCoreEntity();
        validateCondition(!StringUtils.isBlank(attributeId) &&
                        !syncariEntity.getAttributes().stream().anyMatch(a -> a.getId().equals(attributeId)),
                i18n("attach_record_invalid_predicate", validationContext.getNode().getName(), validationContext.getGraph().getName()));

    }

    @Override
    public void extract(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode node = context.getCurrentNode();
        super.extract(context);

        SharableFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();

        // 1. Attributes from lookup condition
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
    public void postPublish(PipelinePublishedEvent context) {
        log.info("Post publish event processing for AdvancedAttachRecordFunction.");
        createIndexes(context.getGraph(), context.getNode());
    }

    @Override
    public void createIndexes(MappingGraph graph, MappingNode node) {
        List<CacheIndexAttribute> cacheIndexAttributes = new ArrayList<>();
        var visitor = new SimpleExpressionVisitor() {
            public void visit(VariableExpression expression) {
                String variableName = expression.getVariableName();
                Optional<EntityDefinition> entityDefinition = schemaService.getSyncariEntityById(graph.getTargetId());
                if (featureService.isEnabled(Features.EntityCaching)) {
                    cacheIndexAttributes.add(redisUtils.createCacheIndexAttribute(variableName,false, entityDefinition));
                }
                customerMongoUtils.constructIndexes(variableName,true,entityDefinition);
            }

            public void visit(EqualIgnoreCase expression){
                Expression left = expression.getLeft();
                if(left instanceof VariableExpression){
                    String variableName =  ((VariableExpression) left).getVariableName();
                    Optional<EntityDefinition> entityDefinition = schemaService.getSyncariEntityById(graph.getTargetId());
                    if (featureService.isEnabled(Features.EntityCaching)) {
                        cacheIndexAttributes.add(redisUtils.createCacheIndexAttribute(variableName,true, entityDefinition));
                    }
                    customerMongoUtils.constructIndexes(variableName,false,entityDefinition);
                }

            }
        };
        SimpleFunctionNodeConfig config = node.getTypedConfiguration();
        Map<String, Object> predicate = (Map<String, Object>) config.getConfigMap().get(PREDICATE);
        Expression filterExpression = new PredicateParser().fromMap(predicate);
        filterExpression.accept(visitor);
        if (featureService.isEnabled(Features.EntityCaching)) {
            Optional<EntityDefinition> entityDefinition = schemaService.getSyncariEntityById(graph.getTargetId());
            entityDefinition.ifPresent(e -> redisUtils.constructOrAlterIndex(SyncariContext.getInstance().getSyncariId(), e, cacheIndexAttributes));
        }
    }

    @Override
    public List<Pair<String, String>> toUserFriendlyValue(DiffInfoContext context, String configProperty) {
        if("value".equals(configProperty)) { // Skip the property
            return List.of();
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
