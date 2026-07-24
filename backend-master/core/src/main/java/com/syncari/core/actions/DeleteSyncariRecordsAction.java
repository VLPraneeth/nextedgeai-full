package com.syncari.core.actions;

import com.syncari.connector.Constants;
import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.actions.ActionConstants;
import com.syncari.core.datatype.BooleanType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.functions.LookupSyncariRecordFunction;
import com.syncari.core.model.*;
import com.syncari.core.model.cache.CacheIndexAttribute;
import com.syncari.core.model.misc.sharable.SharableActionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.core.pipeline.PipelinePublishedEvent;
import com.syncari.core.pipeline.SimpleExpressionVisitor;
import com.syncari.core.pipeline.expression.*;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.quickstart.v2.dependency.DefaultPredicateDependencyGenerator;
import com.syncari.core.quickstart.v2.dependency.DependencyUtil;
import com.syncari.core.quickstart.v2.dependency.ExpressionDependencyResolver;
import com.syncari.core.quickstart.v2.dependency.ExpressionDependencyVisitor;
import com.syncari.core.service.ActionService;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.utils.RedisUtils;
import com.syncari.core.validation.ExpressionValidatorVisitor;
import com.syncari.core.validation.PredicateValidator;
import com.syncari.core.validation.ValidationContext;
import com.syncari.utils.Pair;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
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

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component(ActionConstants.DELETE_SYNCARI_RECORD)
public class DeleteSyncariRecordsAction extends DefaultAction implements PredicateValidator{

    @Autowired
    ActionService actionService;

    @Autowired
    SchemaService schemaService;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    DefaultPredicateDependencyGenerator defaultPredicateDependencyGenerator;

    private static final String SYNCARI_ENTITY_DEF_ID = "syncariEntityDefId";

    private final Pattern FIELD_OUTPUT_PATTERN = Pattern.compile("field_(\\w+)");

    public static final String PREDICATE = "predicate";

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

        GenericActionConfig actionNodeConfig = node.getTypedConfiguration();
        ActionDefinition funcDef = actionService.getAction(node.getApiName()).get();
        Map<String, String> configNameLabelMap = funcDef.getConfiguration().stream()
                .collect(Collectors.toMap(c -> c.getName(), c -> c.getLabel()));
        Map<String, Object> configMap = actionNodeConfig.getConfigMap();

        var syncariEntityDefId = configMap.get(SYNCARI_ENTITY_DEF_ID);
        if(syncariEntityDefId == null) {
            return errors;
        }
        Optional<EntityDefinition> syncariEntityMaybe = schemaService.findEntity(syncariEntityDefId.toString());
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), syncariEntityMaybe.isEmpty(),
                i18n("invalid_config_in_node", configNameLabelMap.get(SYNCARI_ENTITY_DEF_ID), syncariEntityDefId,
                        node.getName(), graph.getName()), ErrorCode.E1143.getCode()).ifPresent(ee -> errors.add(ee));



        if (syncariEntityMaybe.isPresent()) {
            EntityDefinition syncariEntity = syncariEntityMaybe.get();
            validationContext.getData().put("syncariEntity", syncariEntity);

            try {
                if(configMap.get("predicate") instanceof Map) {
                    Map<String, Object> searchCriteria = (Map<String, Object>) configMap.get("predicate");
                    validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                            MapUtils.isEmpty(searchCriteria), i18n("invalid_config_in_node",
                                    configNameLabelMap.get("predicate"), "Empty Conditions", node.getName(), graph.getName()), ErrorCode.E1144.getCode())
                            .ifPresent(ee -> errors.add(ee));
                    // validate search criteria
                    Expression searchExpression = new PredicateParser().fromMap(searchCriteria);
                    ExpressionValidatorVisitor visitor = new ExpressionValidatorVisitor(this, validationContext);
                    searchExpression.accept(visitor);
                }
            } catch (SyncariValidationException e) {
                log.error("validation error occured ", e);
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
        Matcher attribMatcher = FIELD_OUTPUT_PATTERN.matcher(variableName);
        String attributeId = attribMatcher.find() ? attribMatcher.group(1) : null;
        EntityDefinition selectedSyncariEntity = (EntityDefinition) validationContext.getData().get("syncariEntity");
        if(selectedSyncariEntity != null) {
            validateCondition(!StringUtils.isBlank(attributeId) &&
                            !selectedSyncariEntity.getAttributes().stream().anyMatch(a -> a.getId().equals(attributeId)),
                    i18n("update_records_invalid_predicate", validationContext.getNode().getName(), validationContext.getGraph().getName()));
        }

    }

    @Override
    public void extract(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode node = context.getCurrentNode();
        super.extract(context);
        SharableActionNodeConfig actionNodeConfig = node.getTypedConfiguration();
        Map<String, Object> configMap = actionNodeConfig.getConfigMap();
        // 1. Selected Syncari entity
        var syncariEntityDefId = configMap.get(SYNCARI_ENTITY_DEF_ID).toString();
        EntityDefinition syncariEntity = context.getEntity(syncariEntityDefId).orElseThrow();
        qsConfig.addDependency(DependencyUtil.getEntityDependency(syncariEntity));

        // 2. Attributes from search condition
        var predicate = (Map<String, Object>) configMap.get("predicate");
        Expression filterExpression = new PredicateParser().fromMap(predicate);
        ExpressionDependencyVisitor visitor = new ExpressionDependencyVisitor(defaultPredicateDependencyGenerator, context);
        filterExpression.accept(visitor);
    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode sharableNode = context.getCurrentNode().copy();
        SharableActionNodeConfig actionNodeConfig = sharableNode.getTypedConfiguration();
        Map<String, Object> configMap = actionNodeConfig.getConfigMap();

        // 1. Selected Syncari entity
        var syncariEntityDefId = configMap.get(SYNCARI_ENTITY_DEF_ID).toString();
        EntityDefinition resolvedEntity = (EntityDefinition) qsConfig.getResolvedValueByType(syncariEntityDefId, QSDependency.Type.Entity);
        if(resolvedEntity != null){
            configMap.put(SYNCARI_ENTITY_DEF_ID, resolvedEntity.getId());
        }

        // 2. attributes from predicate condition
        Map<String, Object> predicate = (Map<String, Object>) configMap.get("predicate");
        ExpressionDependencyResolver resolver = new ExpressionDependencyResolver(context);
        var resolvedPredicate = resolver.fromMap(predicate);
        configMap.put("predicate", resolvedPredicate);
        actionNodeConfig.setConfigMap(configMap);
        sharableNode.setConfiguration(actionNodeConfig);

        return sharableGraphTransformer.toMappingNode(sharableNode, context.getCurrentPipeline());
    }

    @Override
    public void postPublish(PipelinePublishedEvent context) {
        List<CacheIndexAttribute> cacheIndexAttributes = new ArrayList<>();
        var visitor = new SimpleExpressionVisitor() {
            public void visit(VariableExpression expression) {
                String variableName = expression.getVariableName();
                GenericActionConfig actionNodeConfig = context.getNode().getTypedConfiguration();
                Map<String, Object> configMap = actionNodeConfig.getConfigMap();
                var syncariEntityDefId = configMap.get(LookupSyncariRecordFunction.SYNCARI_ENTITY_ID).toString();
                Optional<EntityDefinition> entityDefinition = schemaService.getSyncariEntityById(syncariEntityDefId);
                if (context.getFeatureService().isEnabled(Features.EntityCaching)) {
                    cacheIndexAttributes.add(redisUtils.createCacheIndexAttribute(variableName,false, false, entityDefinition));
                }
                context.getMongoUtils().constructIndexes(variableName,true,entityDefinition);
            }

            public void visit(EqualIgnoreCase expression){
                Expression left = expression.getLeft();
                if(left instanceof VariableExpression){
                    String variableName =  ((VariableExpression) left).getVariableName();
                    GenericActionConfig actionNodeConfig = context.getNode().getTypedConfiguration();
                    Map<String, Object> configMap = actionNodeConfig.getConfigMap();
                    var syncariEntityDefId = configMap.get(LookupSyncariRecordFunction.SYNCARI_ENTITY_ID).toString();
                    Optional<EntityDefinition> entityDefinition = schemaService.getSyncariEntityById(syncariEntityDefId);
                    if (context.getFeatureService().isEnabled(Features.EntityCaching)) {
                        cacheIndexAttributes.add(redisUtils.createCacheIndexAttribute(variableName,true, false, entityDefinition));
                    }
                    context.getMongoUtils().constructIndexes(variableName,false,entityDefinition);
                }

            }

            public void visit(Contains expression){
                Expression left = expression.getLeft();
                if(left instanceof VariableExpression){
                    String variableName =  ((VariableExpression) left).getVariableName();
                    GenericActionConfig actionNodeConfig = context.getNode().getTypedConfiguration();
                    Map<String, Object> configMap = actionNodeConfig.getConfigMap();
                    var syncariEntityDefId = configMap.get(LookupSyncariRecordFunction.SYNCARI_ENTITY_ID).toString();
                    Optional<EntityDefinition> entityDefinition = schemaService.getSyncariEntityById(syncariEntityDefId);
                    if (context.getFeatureService().isEnabled(Features.EntityCaching)) {
                        cacheIndexAttributes.add(redisUtils.createCacheIndexAttribute(variableName,true, false, entityDefinition));
                    }
                }
            }

            public void visit(StartsWith expression){
                Expression left = expression.getLeft();
                if(left instanceof VariableExpression){
                    String variableName =  ((VariableExpression) left).getVariableName();
                    GenericActionConfig actionNodeConfig = context.getNode().getTypedConfiguration();
                    Map<String, Object> configMap = actionNodeConfig.getConfigMap();
                    var syncariEntityDefId = configMap.get(LookupSyncariRecordFunction.SYNCARI_ENTITY_ID).toString();
                    Optional<EntityDefinition> entityDefinition = schemaService.getSyncariEntityById(syncariEntityDefId);
                    if (context.getFeatureService().isEnabled(Features.EntityCaching)) {
                        cacheIndexAttributes.add(redisUtils.createCacheIndexAttribute(variableName,true, false, entityDefinition));
                    }
                }
            }

            public void visit(Empty expression){
                if (context.getFeatureService().isEnabled(Features.EntityCaching)) {
                    cacheIndexAttributes.add(context.getRedisUtils().createNullField());
                }
            }

            public void visit(NotEmpty expression){
                if (context.getFeatureService().isEnabled(Features.EntityCaching)) {
                    cacheIndexAttributes.add(context.getRedisUtils().createNullField());
                }
            }
        };
        GenericActionConfig actionNodeConfig = context.getNode().getTypedConfiguration();
        Map<String, Object> configMap = actionNodeConfig.getConfigMap();
        Map<String, Object> predicate = (Map<String, Object>) configMap.get(PREDICATE);
        var syncariEntityDefId = configMap.get(LookupSyncariRecordFunction.SYNCARI_ENTITY_ID).toString();
        Optional<EntityDefinition> entityDefinition = schemaService.getSyncariEntityById(syncariEntityDefId);
        Expression filterExpression = new PredicateParser().fromMap(predicate);
        filterExpression.accept(visitor);

        if (context.getFeatureService().isEnabled(Features.EntityCaching)) {
            log.info("Constructing index for entity {}", entityDefinition.get().getApiName());
            cacheIndexAttributes.add(context.getRedisUtils().createSystemIndexAttribute("_id", StringType.VALUE, false));
            cacheIndexAttributes.add(context.getRedisUtils().createSystemIndexAttribute("isDeleted", BooleanType.VALUE, false));
            MappingGraph graph = context.getGraph();
            entityDefinition.ifPresent(e -> redisUtils.constructOrAlterIndex(SyncariContext.getInstance().getSyncariId(), e, cacheIndexAttributes));
        }
    }
    
    @Override
    public List<Pair<String, String>> toUserFriendlyValue(DiffInfoContext context, String configProperty) {
    	if (SYNCARI_ENTITY_DEF_ID.equals(configProperty) && context != null && context.getCurrentNode() != null) {
    		Map<String, Object> configMap = context.getCurrentNode().getConfiguration().getConfigMap();
    		if(configMap == null) {
    			configMap = Map.of();
    		}
            var entityId = configMap.get(SYNCARI_ENTITY_DEF_ID);
            if(entityId != null) {
            	var entity = schemaService.findEntity(entityId.toString());
            	if(entity.isPresent()) {
            		return List.of(Pair.of(configProperty, entity.get().getDisplayName()));
            	}
            }
    	}else if ("value".equals(configProperty) && context != null && context.getCurrentNode() != null) {
    		return List.of();
    	}
    	return super.toUserFriendlyValue(context, configProperty);
    }
    
    @Override
    public boolean postProcess(QuickStartContext context) {
      SharableNode sharableNode = context.getCurrentNode();
      SharableActionNodeConfig functionNodeConfig = sharableNode.getTypedConfiguration();
      Map<String, Object> configMap = functionNodeConfig.getConfigMap();

      Map<String, Object> predicate = (Map<String, Object>) configMap.get(PREDICATE);
      ExpressionDependencyResolver resolver = new ExpressionDependencyResolver(context);
      var resolvedPredicate = resolver.fromMap(predicate);
      
      GenericActionConfig nodeConfig = context.getCurrentMappingNode().getTypedConfiguration();
      var gacMap = nodeConfig.getConfigMap();
      gacMap.put(PREDICATE, resolvedPredicate);
      nodeConfig.setConfigMap(gacMap);
      return true;
    }
}
