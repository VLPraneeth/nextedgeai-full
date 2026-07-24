package com.syncari.core.functions;

import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.BooleanType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.cache.CacheIndexAttribute;
import com.syncari.core.model.misc.sharable.SharableFunctionCall;
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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component
public class LookupSyncariRecordFunction extends DefaultFunction implements PredicateValidator {

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

    private static final String SORT_FIELDS = "sortFields";
    public static final String SYNCARI_ENTITY_ID = "syncariEntityDefId";
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
        var syncariEntityDefId = configMap.get(SYNCARI_ENTITY_ID);
        if(syncariEntityDefId == null) {
            return errors;
        }
        Optional<EntityDefinition> syncariEntityMaybe = schemaService.getSyncariEntityById(syncariEntityDefId.toString());
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), syncariEntityMaybe.isEmpty(),
                i18n("invalid_config_in_node", configNameLabelMap.get(SYNCARI_ENTITY_ID), syncariEntityDefId,
                        node.getName(), graph.getName()), ErrorCode.E1116.getCode()).ifPresent(e -> errors.add(e));

        // validate sort fields
        //UI saves value as string under some conditions
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                configMap.get(SORT_FIELDS) != null
                        && !List.class.isAssignableFrom(configMap.get(SORT_FIELDS).getClass()),
                i18n("lookup_record_invalid_sortField", validationContext.getNode().getName(),
                        validationContext.getGraph().getName()), ErrorCode.E1117.getCode()).ifPresent(e -> errors.add(e));

        if(syncariEntityMaybe.isPresent()) {
            EntityDefinition syncariEntity = syncariEntityMaybe.get();
            validationContext.getData().put("syncariEntity", syncariEntity);

            var predicate = configMap.get(PREDICATE);
            if (!(predicate instanceof Map) || ((Map<String, Object>)predicate).isEmpty()) {
                errors.add(ValidationError.scopedError(node.getScope(), node.getId())
                        .withMessage(i18n("missing_config_from_node", i18n("lookup_record_predicate_label"),
                                node.getName(), graph.getName())));
            } else {
                try {
                    Expression filterExpression = new PredicateParser().fromMap((Map<String, Object>) predicate);
                    ExpressionValidatorVisitor visitor = new ExpressionValidatorVisitor(this, validationContext);
                    filterExpression.accept(visitor);
                } catch (SyncariValidationException e) {
                    log.error("validation error occured ", e);
                    errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(e.getMessage()));
                }
            }
            if(configMap.get(SORT_FIELDS) != null && List.class.isAssignableFrom(configMap.get(SORT_FIELDS).getClass())) {
                var sortCondition = (List<Map<String, Map<String, String>>>) configMap.getOrDefault(SORT_FIELDS, List.of());
                if(sortCondition!=null && !sortCondition.isEmpty()){
                    sortCondition.forEach(s -> {
                        String sortField = MapUtils.getString(s.get("sortField"), "value", "");
                        String sortDirection = MapUtils.getString(s.get("sortDirection"), "value", "");

                        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                                !syncariEntity.getAttributes().stream().anyMatch(a -> a.getId().equals(sortField)),
                                i18n("lookup_record_invalid_sortField", validationContext.getNode().getName(),
                                        validationContext.getGraph().getName()), ErrorCode.E1118.getCode()).ifPresent(e -> errors.add(e));

                        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                                !List.of("asc", "desc").contains(sortDirection),
                                i18n("lookup_record_invalid_sortDirection", sortDirection,
                                        validationContext.getNode().getName(), validationContext.getGraph().getName()), ErrorCode.E1119.getCode())
                                .ifPresent(e -> errors.add(e));
                    });
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
                    i18n("lookup_record_invalid_predicate", validationContext.getNode().getName(), validationContext.getGraph().getName()));
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
        var syncariEntityDefId = configMap.get(SYNCARI_ENTITY_ID).toString();
        log.info("Resolving dependency for {} for lookup node {} entity {}", syncariEntityDefId, node.getId(), context.getCurrentPipeline().getTargetId());
        EntityDefinition syncariEntity = context.getEntity(syncariEntityDefId).orElseThrow();
        qsConfig.addDependency(DependencyUtil.getEntityDependency(syncariEntity));

        // 2. Attributes from lookup condition
        var predicate = (Map<String, Object>) configMap.get(PREDICATE);
        Expression filterExpression = new PredicateParser().fromMap(predicate);
        ExpressionDependencyVisitor visitor = new ExpressionDependencyVisitor(defaultPredicateDependencyGenerator, context);
        filterExpression.accept(visitor);

        // 3. sort fields dependency extraction
        var sortConditions = (List<Map<String, Map<String, String>>>) configMap.getOrDefault(SORT_FIELDS, List.of());
        sortConditions.forEach(s -> {
            String attributeId = s.get("sortField").get("value");
            AttributeDefinition attribute = context.getAttribute(attributeId).orElseThrow();
            qsConfig.addDependency(DependencyUtil.getAttributeDependency(attribute));
        });
    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode sharableNode = context.getCurrentNode().copy();
        SharableFunctionNodeConfig functionNodeConfig = sharableNode.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();

        // 1. Selected Syncari entity
        var syncariEntityDefId = configMap.get(SYNCARI_ENTITY_ID).toString();
        log.info("Resolving syncariEntityDefId dependency for {}", syncariEntityDefId);
        EntityDefinition resolvedEntity = (EntityDefinition) qsConfig.getResolvedValueByType(syncariEntityDefId, QSDependency.Type.Entity);
        if(resolvedEntity != null){
            configMap.put(SYNCARI_ENTITY_ID, resolvedEntity.getId());
        }

        // 2. Attributes from lookup condition
        Map<String, Object> predicate = (Map<String, Object>) configMap.get(PREDICATE);
        ExpressionDependencyResolver resolver = new ExpressionDependencyResolver(context);
        var resolvedPredicate = resolver.fromMap(predicate);
        configMap.put(PREDICATE, resolvedPredicate);

        // 3: sort fields dependency resolution
        var sortFields = (List<Map<String, Map<String, String>>>) configMap.getOrDefault(SORT_FIELDS, List.of());
        for (Map<String, Map<String, String>> s : sortFields) {
            Map<String, String> sortFieldMap = new HashMap<>(s.get("sortField"));
            String attributeId = sortFieldMap.get("value");
            AttributeDefinition resolvedAttrib = (AttributeDefinition) qsConfig.getResolvedValueByType(attributeId, QSDependency.Type.Attribute);
            if(resolvedAttrib != null){
                sortFieldMap.put("value", resolvedAttrib.getId());
            }
            s.put("sortField", sortFieldMap);
        }
        configMap.put(SORT_FIELDS, sortFields);
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
                    cacheIndexAttributes.add(redisUtils.createCacheIndexAttribute(variableName,false, entityDefinition));
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
                        cacheIndexAttributes.add(redisUtils.createCacheIndexAttribute(variableName,true, entityDefinition));
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
                        cacheIndexAttributes.add(redisUtils.createCacheIndexAttribute(variableName,true, entityDefinition));
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
                        cacheIndexAttributes.add(redisUtils.createCacheIndexAttribute(variableName,true, entityDefinition));
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
        Map<String, Object> predicate = (Map<String, Object>) config.getConfigMap().get(PREDICATE);
        Expression filterExpression = new PredicateParser().fromMap(predicate);
        filterExpression.accept(visitor);
        if (featureService.isEnabled(Features.EntityCaching)) {

            cacheIndexAttributes.add(redisUtils.createSystemIndexAttribute("_id", StringType.VALUE, false));
            cacheIndexAttributes.add(redisUtils.createSystemIndexAttribute("isDeleted", BooleanType.VALUE, false));

            Map<String, Object> configMap = config.getConfigMap();
            var syncariEntityDefId = configMap.get(LookupSyncariRecordFunction.SYNCARI_ENTITY_ID).toString();
            Optional<EntityDefinition> entityDefinition = schemaService.getSyncariEntityById(syncariEntityDefId);

            // for each sort field
            var sortFields = (List<Map<String, Object>>) config.getConfigMap().get(SORT_FIELDS);
            sortFields.stream().forEach(s -> {
                if (s.containsKey("sortField")) {
                    String sortField = (String)((Map)s.get("sortField")).get("value");
                    if (!StringUtils.isEmpty(sortField)) {
                        var optAttrib = cacheIndexAttributes.stream().filter(c -> c.getPath().equals(sortField)).findFirst();

                        optAttrib.ifPresentOrElse(attrib -> attrib.setSortable(true), () -> {
                            cacheIndexAttributes.add(redisUtils.createCacheIndexAttribute(sortField, false, entityDefinition));
                        });
                    }
                }
            });
            entityDefinition.ifPresent(e -> redisUtils.constructOrAlterIndex(SyncariContext.getInstance().getSyncariId(), e, cacheIndexAttributes));
        }
    }

    @Override
    public List<Pair<String, String>> toUserFriendlyValue(DiffInfoContext context, String configProperty) {
        if (context != null && context.getCurrentNode() != null) {
            if("value".equals(configProperty)) { // Skip the property
                return List.of();
            } if (SORT_FIELDS.equals(configProperty)) {
                SimpleFunctionNodeConfig functionNodeConfig = context.getCurrentNode().getTypedConfiguration();
                Map<String, Object> configMap = functionNodeConfig.getConfigMap();
                var syncariEntityDefId = configMap.getOrDefault(SYNCARI_ENTITY_ID, configProperty);
                Optional<EntityDefinition> entityDefinition = schemaService.getSyncariEntityById(String.valueOf(syncariEntityDefId));
                if(entityDefinition.isPresent()) {
                    List<Map<String, Map<String, String>>> updateFields = (List<Map<String, Map<String, String>>>) configMap.get(configProperty);
                    List<List<String>> row = new ArrayList<>();
                    for (Map<String, Map<String, String>> s : updateFields) {
                        List<String> col = new ArrayList<>();
                        Map<String, String> updateFieldMap = new HashMap<>(s.get("sortField"));
                        String attributeId = updateFieldMap.get("value");
                        AttributeDefinition resolvedAttrib = entityDefinition.get().getAttribute(attributeId.toString());
                        if(resolvedAttrib != null){
                            col.add(resolvedAttrib.getDisplayName());
                        }
                        Map<String, String> newValueMap = new HashMap<>(s.get("sortDirection"));
                        col.add(newValueMap.get("value"));
                        row.add(col);
                    }
                    if(row.size() == 1) {
                        return List.of(Pair.of(configProperty, row.get(0).toString()));
                    } else {
                        return List.of(Pair.of(configProperty, row.toString()));
                    }
                }
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
