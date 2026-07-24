package com.syncari.core.functions;

import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.BooleanType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.cache.CacheIndexAttribute;
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
import com.syncari.core.service.FeatureService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.utils.MongoUtils;
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
@Component(FunctionConstants.UPDATE_SYNCARI_RECORDS)
public class UpdateRecordsFunction extends DefaultFunction implements PredicateValidator {

    private static final String SYNCARI_ENTITY_DEF_ID = "syncariEntityDefId";

    @Autowired
    SchemaService schemaService;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private MongoUtils customerMongoUtils;

    @Autowired
    private FeatureService featureService;

    @Autowired
    DefaultPredicateDependencyGenerator defaultPredicateDependencyGenerator;

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
        Map<String, String> configNameLabelMap = funcDef.getConfiguration().stream()
                .collect(Collectors.toMap(c -> c.getName(), c -> c.getLabel()));
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();

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
                // search field should refer to attribute of selected syncari entity
                Map<String, Object> searchCriteria = (Map<String, Object>) configMap.get("predicate");
                validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                        MapUtils.isEmpty(searchCriteria), i18n("invalid_config_in_node",
                                configNameLabelMap.get("predicate"), "Empty Conditions", node.getName(), graph.getName()), ErrorCode.E1144.getCode())
                        .ifPresent(ee -> errors.add(ee));
                // validate search criteria
                Expression searchExpression = new PredicateParser().fromMap(searchCriteria);
                ExpressionValidatorVisitor visitor = new ExpressionValidatorVisitor(this, validationContext);
                searchExpression.accept(visitor);
            } catch (SyncariValidationException e) {
                log.error("validation error occured ", e);
                errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(e.getMessage()));
            }

            // inputFieldId refers to attribute of connected sources or core entity
            List<Map<String, Map<String, String>>> updateFields = (List<Map<String, Map<String, String>>>) configMap
                    .get("updateFields");
            validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                    updateFields == null || updateFields.isEmpty(), i18n("invalid_config_in_node",
                            configNameLabelMap.get("updateFields"), "Empty Update Fields", node.getName(), graph.getName()), ErrorCode.E1145.getCode())
                    .ifPresent(ee -> errors.add(ee));

            if(updateFields != null) {
                for (Map<String, Map<String, String>> s : updateFields) {
                    validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                            s.get("updateField") == null, i18n("update_records_empty_attribute",
                                    validationContext.getNode().getName(), validationContext.getGraph().getName()), ErrorCode.E1146.getCode())
                            .ifPresent(ee -> errors.add(ee));

                    validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                            s.get("operation") == null, i18n("update_records_empty_attribute",
                                    validationContext.getNode().getName(), validationContext.getGraph().getName()), ErrorCode.E1146.getCode())
                            .ifPresent(ee -> errors.add(ee));
                    if(s.get("updateField") != null) {
                        String attributeId = s.get("updateField").get("value");
                        AttributeDefinition attribute = syncariEntity.getAttribute(attributeId);
                        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), attribute == null,
                                i18n("update_records_invalid_attribute", validationContext.getNode().getName(),
                                        validationContext.getGraph().getName()), ErrorCode.E1147.getCode()).ifPresent(ee -> errors.add(ee));
                    }
                }
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
        SharableFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();
        // 1. Selected Syncari entity
        var syncariEntityDefId = configMap.get(SYNCARI_ENTITY_DEF_ID).toString();
        EntityDefinition syncariEntity = context.getEntity(syncariEntityDefId).orElseThrow();
        qsConfig.addDependency(DependencyUtil.getEntityDependency(syncariEntity));

        // 2. Attributes from search condition
        var predicate = (Map<String, Object>) configMap.get("predicate");
        Expression filterExpression = new PredicateParser().fromMap(predicate);
        ExpressionDependencyVisitor visitor = new ExpressionDependencyVisitor(defaultPredicateDependencyGenerator, context);
        filterExpression.accept(visitor);

        // 3. attributes from updateFields config
        List<Map<String, Map<String, String>>> updateFields = (List<Map<String, Map<String, String>>>) configMap.get("updateFields");
        for (Map<String, Map<String, String>> s : updateFields) {
            String attributeId = s.get("updateField").get("value");
            AttributeDefinition attribute = context.getAttribute(attributeId).orElseThrow();
            qsConfig.addDependency(DependencyUtil.getAttributeDependency(attribute));

            String newVal = s.get("newValue").get("value");
            DependencyUtil.getTokenDependencies(newVal).forEach(d -> qsConfig.addDependency(d));

        }
    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode sharableNode = context.getCurrentNode();
        SharableFunctionNodeConfig functionNodeConfig = sharableNode.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();

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

        // 3. attributes from updateFields config
        List<Map<String, Map<String, String>>> updateFields = (List<Map<String, Map<String, String>>>) configMap.get("updateFields");
        for (Map<String, Map<String, String>> s : updateFields) {
            Map<String, String> updateFieldMap = new HashMap<>(s.get("updateField"));
            String attributeId = updateFieldMap.get("value");
            AttributeDefinition resolvedAttrib = (AttributeDefinition) qsConfig.getResolvedValueByType(attributeId, QSDependency.Type.Attribute);
            if(resolvedAttrib != null){
                updateFieldMap.put("value", resolvedAttrib.getId());
            }
            s.put("updateField", updateFieldMap);

            Map<String, String> newValueMap = new HashMap<>(s.get("newValue"));
            String newValue = newValueMap.get("value");
            if(TokenHelper.hasTokens(newValue)){
                var resolvedValue = (String) qsConfig.getResolvedValueByType(newValue, QSDependency.Type.Token);
                if(resolvedValue != null){
                    newValueMap.put("value", resolvedValue);
                }
            }
            s.put("newValue", newValueMap);
        }
        configMap.put("updateFields", updateFields);
        functionNodeConfig.getFunctionCall().setConfig(configMap);
        functionNodeConfig.getFunctionCall().setParams(resolveParams(context, functionNodeConfig));
        sharableNode.setConfiguration(functionNodeConfig);

        return sharableGraphTransformer.toMappingNode(sharableNode, context.getCurrentPipeline());
    }

    @Override
    public void postPublish(PipelinePublishedEvent context) {
        createIndexes(context.getGraph(), context.getNode());
    }

    @Override
    public void createIndexes(MappingGraph graph, MappingNode node) {
        List<CacheIndexAttribute> cacheIndexAttributes = new ArrayList<>();
        var visitor = new SimpleExpressionVisitor() {
            public void visit(VariableExpression expression) {
                String variableName = expression.getVariableName();
                SimpleFunctionNodeConfig config = (SimpleFunctionNodeConfig) node.getConfiguration();
                Map<String, Object> configMap = config.getConfigMap();
                var syncariEntityDefId = configMap.get(LookupSyncariRecordFunction.SYNCARI_ENTITY_ID).toString();
                Optional<EntityDefinition> entityDefinition = schemaService.getSyncariEntityById(syncariEntityDefId);
                if (featureService.isEnabled(Features.EntityCaching)) {
                    cacheIndexAttributes.add(redisUtils.createCacheIndexAttribute(variableName,false, false, entityDefinition));
                }
                customerMongoUtils.constructIndexes(variableName,true,entityDefinition);
            }

            public void visit(EqualIgnoreCase expression){
                Expression left = expression.getLeft();
                if(left instanceof VariableExpression){
                    String variableName =  ((VariableExpression) left).getVariableName();
                    SimpleFunctionNodeConfig config = (SimpleFunctionNodeConfig) node.getConfiguration();
                    Map<String, Object> configMap = config.getConfigMap();
                    var syncariEntityDefId = configMap.get(LookupSyncariRecordFunction.SYNCARI_ENTITY_ID).toString();
                    Optional<EntityDefinition> entityDefinition = schemaService.getSyncariEntityById(syncariEntityDefId);
                    if (featureService.isEnabled(Features.EntityCaching)) {
                        cacheIndexAttributes.add(redisUtils.createCacheIndexAttribute(variableName,true, false, entityDefinition));
                    }
                    customerMongoUtils.constructIndexes(variableName,false,entityDefinition);
                }

            }

            public void visit(Contains expression){
                Expression left = expression.getLeft();
                if(left instanceof VariableExpression){
                    String variableName =  ((VariableExpression) left).getVariableName();
                    SimpleFunctionNodeConfig config = (SimpleFunctionNodeConfig) node.getConfiguration();
                    Map<String, Object> configMap = config.getConfigMap();
                    var syncariEntityDefId = configMap.get(LookupSyncariRecordFunction.SYNCARI_ENTITY_ID).toString();
                    Optional<EntityDefinition> entityDefinition = schemaService.getSyncariEntityById(syncariEntityDefId);
                    if (featureService.isEnabled(Features.EntityCaching)) {
                        cacheIndexAttributes.add(redisUtils.createCacheIndexAttribute(variableName,true, false, entityDefinition));
                    }
                }
            }

            public void visit(StartsWith expression){
                Expression left = expression.getLeft();
                if(left instanceof VariableExpression){
                    String variableName =  ((VariableExpression) left).getVariableName();
                    SimpleFunctionNodeConfig config = (SimpleFunctionNodeConfig) node.getConfiguration();
                    Map<String, Object> configMap = config.getConfigMap();
                    var syncariEntityDefId = configMap.get(LookupSyncariRecordFunction.SYNCARI_ENTITY_ID).toString();
                    Optional<EntityDefinition> entityDefinition = schemaService.getSyncariEntityById(syncariEntityDefId);
                    if (featureService.isEnabled(Features.EntityCaching)) {
                        cacheIndexAttributes.add(redisUtils.createCacheIndexAttribute(variableName,true, false, entityDefinition));
                    }
                }
            }

            public void visit(Empty expression){
                if (featureService.isEnabled(Features.EntityCaching)) {
                    cacheIndexAttributes.add(redisUtils.createNullField());
                }
            }

            public void visit(NotEmpty expression){
                if (featureService.isEnabled(Features.EntityCaching)) {
                    cacheIndexAttributes.add(redisUtils.createNullField());
                }
            }
        };
        SimpleFunctionNodeConfig config = node.getTypedConfiguration();
        var configMap = (Map<String, Object>) config.getConfigMap();
        Map<String, Object> predicate = (Map<String, Object>) config.getConfigMap().get(PREDICATE);
        var syncariEntityDefId = configMap.get(LookupSyncariRecordFunction.SYNCARI_ENTITY_ID).toString();
        Optional<EntityDefinition> entityDefinition = schemaService.getSyncariEntityById(syncariEntityDefId);
        Expression filterExpression = new PredicateParser().fromMap(predicate);
        filterExpression.accept(visitor);

        if (featureService.isEnabled(Features.EntityCaching)) {
            log.info("Constructing index for entity {}", entityDefinition.get().getApiName());
            cacheIndexAttributes.add(redisUtils.createSystemIndexAttribute("_id", StringType.VALUE, false));
            cacheIndexAttributes.add(redisUtils.createSystemIndexAttribute("isDeleted", BooleanType.VALUE, false));
            entityDefinition.ifPresent(e -> redisUtils.constructOrAlterIndex(SyncariContext.getInstance().getSyncariId(), e, cacheIndexAttributes));
        }
    }

    @Override
    public List<Pair<String, String>> toUserFriendlyValue(DiffInfoContext context, String configProperty) {
        if (context != null && context.getCurrentNode() != null) {
            if("value".equals(configProperty)) { // Skip the property
                return List.of();
            } else if ("updateFields".equals(configProperty)) {
                List<Pair<String, String>> res = new ArrayList<Pair<String,String>>();
                SimpleFunctionNodeConfig functionNodeConfig = context.getCurrentNode().getTypedConfiguration();
                Map<String, Object> configMap = functionNodeConfig.getConfigMap();
                var syncariEntityDefId = configMap.getOrDefault(SYNCARI_ENTITY_DEF_ID, configProperty);
                Optional<EntityDefinition> entityDefinition = schemaService.getSyncariEntityById(String.valueOf(syncariEntityDefId));
                if(entityDefinition.isPresent()) {
                    List<Map<String, Map<String, String>>> updateFields = (List<Map<String, Map<String, String>>>) configMap.get(configProperty);
                    int i = 1;
                    for (Map<String, Map<String, String>> s : updateFields) {
                        List<String> col = new ArrayList<>();
                        Map<String, String> updateFieldMap = new HashMap<>(s.get("updateField"));
                        String attributeId = updateFieldMap.get("value");
                        AttributeDefinition resolvedAttrib = entityDefinition.get().getAttribute(attributeId.toString());
                        if(resolvedAttrib != null){
                            col.add(resolvedAttrib.getDisplayName());
                        }
                        Map<String, String> newValueMap = new HashMap<>(s.get("newValue"));
                        col.add(newValueMap.get("value"));
                        Map<String, String> operationMap = new HashMap<>(s.get("operation"));
                        col.add(operationMap.get("value"));
                        res.add(Pair.of(configProperty + "@@@" + i, col.toString()));
                        i++;

                    }
                    return res;
                }
            }
        }
        return super.toUserFriendlyValue(context, configProperty);
    }
}
