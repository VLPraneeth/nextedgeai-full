package com.syncari.core.functions;

import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DoubleType;
import com.syncari.core.datatype.IntegerType;
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
import com.syncari.core.pipeline.expression.EqualIgnoreCase;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.core.pipeline.expression.VariableExpression;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.quickstart.v2.dependency.*;
import com.syncari.core.service.SchemaService;
import com.syncari.core.utils.RedisUtils;
import com.syncari.core.validation.DBPredicateValidator;
import com.syncari.core.validation.ValidationContext;
import com.syncari.utils.Pair;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;
@Slf4j
public class AbstractAggregateFunction extends DefaultFunction implements DBPredicateValidator, DependencyService {

    private static final String SYNCARI_ENTITY_DEF_ID = "syncariEntityDefId";
    private static final String INPUT_FIELD_ID = "fieldId";
    private static final Set<Datatype> NUMERIC_TYPES = Set.of(IntegerType.VALUE, DoubleType.VALUE);

    @Autowired
    SchemaService schemaService;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    DefaultPredicateDependencyGenerator defaultPredicateDependencyGenerator;

    protected boolean hasAggregateField(){
        return true;
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
    	List<ValidationError> errors = new ArrayList<ValidationError>();
    	errors.addAll(super.validateWithoutException(validationContext));
        MappingNode node = validationContext.getNode();
        MappingGraph graph = validationContext.getGraph();
        
        if (graph == null || node == null)
			return errors;

        SimpleFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
        FunctionDefinition funcDef = functionNodeConfig.getFunctionCall().getFunctionDefinition();
        Map<String, String> configNameLabelMap = funcDef.getConfiguration().stream().collect(Collectors.toMap(c->c.getName(), c->c.getLabel()));
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();

        // syncariEntityDefId should refer to active syncari entity
        if(configMap.get(SYNCARI_ENTITY_DEF_ID) == null) {
        	return errors;
        }
        var syncariEntityDefId = configMap.get(SYNCARI_ENTITY_DEF_ID).toString();
        Optional<EntityDefinition> selectedSyncariEntity = schemaService.getSyncariEntityById(syncariEntityDefId);
		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), selectedSyncariEntity.isEmpty(),
				i18n("invalid_config_in_node", i18n(configNameLabelMap.get(SYNCARI_ENTITY_DEF_ID)), syncariEntityDefId,
						node.getName(), graph.getName()), ErrorCode.E1078.getCode()).ifPresent(e -> errors.add(e));

        // search field should refer to attribute of selected syncari entity
        selectedSyncariEntity.ifPresent(syncariEntity->{
            if(hasAggregateField()) {
                var searchFieldId = configMap.get(INPUT_FIELD_ID);
                if(searchFieldId != null) {
                	final AttributeDefinition attribute = syncariEntity.getAttribute(searchFieldId.toString());
                	validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), attribute == null,
                			i18n("invalid_config_in_node", i18n(configNameLabelMap.get(INPUT_FIELD_ID)), searchFieldId,
                					node.getName(), graph.getName()), ErrorCode.E1079.getCode()).ifPresent(e -> errors.add(e));
                	if (attribute != null) {
                		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                				!NUMERIC_TYPES.contains(attribute.getDataType()),
                				i18n("invalid_aggregate_field_datatype", attribute.getDisplayName(),
                						attribute.getDataType().getName(), node.getName(), graph.getName()), ErrorCode.E1080.getCode())
                		.ifPresent(e -> errors.add(e));
                	}
                }

            }
            validationContext.getData().put("syncariEntity", syncariEntity);

        });

        if(configMap.get(PREDICATE) instanceof Map) {
	        var predicate = (Map<String, Object>) configMap.get(PREDICATE);
			try {
				validatePredicate(validationContext, predicate);
			} catch (SyncariValidationException e) {
				errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(e.getMessage()));
			}
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

        var syncariEntityDefId = configMap.get(SYNCARI_ENTITY_DEF_ID).toString();
        EntityDefinition selectedSyncariEntity = context.getEntity(syncariEntityDefId).orElseThrow();
        qsConfig.addDependency(DependencyUtil.getEntityDependency(selectedSyncariEntity));

        if (hasAggregateField()) {
            var searchFieldId = configMap.get(INPUT_FIELD_ID).toString();
            AttributeDefinition attribute = context.getAttribute(searchFieldId).orElseThrow();
            qsConfig.addDependency(DependencyUtil.getAttributeDependency(attribute));
        }

        var predicate = (Map<String, Object>) configMap.get(PREDICATE);
        Expression filterExpression = new PredicateParser().fromMap(predicate);
        ExpressionDependencyVisitor visitor = new ExpressionDependencyVisitor(defaultPredicateDependencyGenerator, context);
        filterExpression.accept(visitor);
    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode sharableNode = context.getCurrentNode().copy();
        SharableFunctionNodeConfig functionNodeConfig = sharableNode.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();

        var syncariEntityDefId = configMap.get(SYNCARI_ENTITY_DEF_ID).toString();
        EntityDefinition resolvedEntity = (EntityDefinition) qsConfig.getResolvedValueByType(syncariEntityDefId, QSDependency.Type.Entity);
        if(resolvedEntity != null){
            configMap.put(SYNCARI_ENTITY_DEF_ID, resolvedEntity.getId());
        }

        if (hasAggregateField()) {
            var attributeId = configMap.get(INPUT_FIELD_ID).toString();
            AttributeDefinition resolvedAttrib = (AttributeDefinition) qsConfig.getResolvedValueByType(attributeId, QSDependency.Type.Attribute);
            if (resolvedAttrib != null) {
                configMap.put(INPUT_FIELD_ID, resolvedAttrib.getId());
            }
        }

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
        List<CacheIndexAttribute> cacheIndexAttributes = new ArrayList<>();
        var visitor = new SimpleExpressionVisitor() {
            public void visit(VariableExpression expression) {
                String variableName = expression.getVariableName();
                SimpleFunctionNodeConfig config = (SimpleFunctionNodeConfig) context.getNode().getConfiguration();
                Map<String, Object> configMap = config.getConfigMap();
                var syncariEntityDefId = configMap.get(LookupSyncariRecordFunction.SYNCARI_ENTITY_ID).toString();
                Optional<EntityDefinition> entityDefinition = schemaService.getSyncariEntityById(syncariEntityDefId);
                if (context.getFeatureService().isEnabled(Features.EntityCaching)) {
                    cacheIndexAttributes.add(redisUtils.createCacheIndexAttribute(variableName,false, entityDefinition));
                }
                context.getMongoUtils().constructIndexes(variableName,true,entityDefinition);
            }

            public void visit(EqualIgnoreCase expression){
                Expression left = expression.getLeft();
                if(left instanceof VariableExpression){
                    String variableName =  ((VariableExpression) left).getVariableName();
                    SimpleFunctionNodeConfig config = (SimpleFunctionNodeConfig) context.getNode().getConfiguration();
                    Map<String, Object> configMap = config.getConfigMap();
                    var syncariEntityDefId = configMap.get(LookupSyncariRecordFunction.SYNCARI_ENTITY_ID).toString();
                    Optional<EntityDefinition> entityDefinition = schemaService.getSyncariEntityById(syncariEntityDefId);
                    if (context.getFeatureService().isEnabled(Features.EntityCaching)) {
                        cacheIndexAttributes.add(redisUtils.createCacheIndexAttribute(variableName,true, entityDefinition));
                    }
                    context.getMongoUtils().constructIndexes(variableName,false,entityDefinition);
                }


            }
        };
        SimpleFunctionNodeConfig config = context.getNode().getTypedConfiguration();
        Map<String, Object> predicate = (Map<String, Object>) config.getConfigMap().get(PREDICATE);
        Expression filterExpression = new PredicateParser().fromMap(predicate);
        filterExpression.accept(visitor);
        if (context.getFeatureService().isEnabled(Features.EntityCaching)) {
            MappingGraph graph = context.getGraph();
            Optional<EntityDefinition> entityDefinition = schemaService.getSyncariEntityById(graph.getTargetId());
            entityDefinition.ifPresent(e -> redisUtils.constructOrAlterIndex(SyncariContext.getInstance().getSyncariId(), e, cacheIndexAttributes));
        }
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
    		} else if (INPUT_FIELD_ID.equals(configProperty)) {
    			Optional<EntityDefinition> entityDefinition = schemaService.getSyncariEntityById(String.valueOf(syncariEntityDefId));
    			if(entityDefinition.isPresent()) {
    				var searchFieldId = configMap.get(configProperty);
    				if(searchFieldId != null) {
    					final AttributeDefinition attribute = entityDefinition.get().getAttribute(searchFieldId.toString());
    					return List.of(Pair.of(configProperty, attribute.getDisplayName()));
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
