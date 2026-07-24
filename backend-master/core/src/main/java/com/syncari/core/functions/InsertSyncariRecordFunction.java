package com.syncari.core.functions;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.quickstart.v2.dependency.DependencyUtil;
import com.syncari.core.service.SchemaService;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.validation.ValidationContext;
import com.syncari.utils.Pair;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Component(FunctionConstants.INSERT_SYNCARI_RECORD)
public class InsertSyncariRecordFunction extends DefaultFunction {

    private static final String SYNCARI_ENTITY_DEF_ID = "syncariEntityDefId";

    @Autowired
    SchemaService schemaService;

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

        var syncariEntityDefId = configMap.get(SYNCARI_ENTITY_DEF_ID);
        if(syncariEntityDefId == null) {
        	return errors;
        }
        Optional<EntityDefinition> syncariEntityMaybe = schemaService.findEntity(syncariEntityDefId.toString());
		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), syncariEntityMaybe.isEmpty(),
				i18n("invalid_config_in_node", configNameLabelMap.get(SYNCARI_ENTITY_DEF_ID), syncariEntityDefId,
						node.getName(), graph.getName()), ErrorCode.E1106.getCode()).ifPresent(e -> errors.add(e));

		List<Map<String, Map<String, String>>> updateFields = (List<Map<String, Map<String, String>>>) configMap.get("insertFields");
		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
				updateFields == null || updateFields.isEmpty(), i18n("invalid_config_in_node",
						configNameLabelMap.get("insertFields"), "Empty Fields", node.getName(), graph.getName()), ErrorCode.E1107.getCode())
								.ifPresent(e -> errors.add(e));
		if(syncariEntityMaybe.isEmpty()) {
			return errors;
		}
        EntityDefinition syncariEntity = syncariEntityMaybe.get();
        validationContext.getData().put("syncariEntity", syncariEntity);

        // inputFieldId refers to attribute of connected sources or core entity
        // validate each field
        for (Map<String, Map<String, String>> s : updateFields) {
			validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), s.get("updateField") == null,
					i18n("update_records_empty_attribute", validationContext.getNode().getName(),
							validationContext.getGraph().getName()), ErrorCode.E1108.getCode()).ifPresent(e -> errors.add(e));
			if(s.get("updateField") != null) {
				String attributeId = s.get("updateField").get("value");
				AttributeDefinition attribute = syncariEntity.getAttribute(attributeId);
				validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), attribute == null,
						i18n("insert_record_invalid_attribute", validationContext.getNode().getName(),
								validationContext.getGraph().getName()), ErrorCode.E1109.getCode()).ifPresent(e -> errors.add(e));
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
        // 1. Selected Syncari entity
        var syncariEntityDefId = configMap.get(SYNCARI_ENTITY_DEF_ID).toString();
        EntityDefinition syncariEntity = context.getEntity(syncariEntityDefId).orElseThrow();
        qsConfig.addDependency(DependencyUtil.getEntityDependency(syncariEntity));

        // 2. attributes from insertFields config
        List<Map<String, Map<String, String>>> updateFields = (List<Map<String, Map<String, String>>>) configMap.get("insertFields");
        for (Map<String, Map<String, String>> s : updateFields) {
            String attributeId = s.get("updateField").get("value");
            AttributeDefinition attribute = context.getAttribute(attributeId).orElseThrow();
            qsConfig.addDependency(DependencyUtil.getAttributeDependency(attribute));
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

        // 2. attributes from insertFields config
        List<Map<String, Map<String, String>>> insertFields = (List<Map<String, Map<String, String>>>) configMap.get("insertFields");
        for (Map<String, Map<String, String>> s : insertFields) {
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
        configMap.put("insertFields", insertFields);
        functionNodeConfig.getFunctionCall().setConfig(configMap);
        functionNodeConfig.getFunctionCall().setParams(resolveParams(context, functionNodeConfig));
        sharableNode.setConfiguration(functionNodeConfig);

        return sharableGraphTransformer.toMappingNode(sharableNode, context.getCurrentPipeline());
    }
    
    @Override
	public List<Pair<String, String>> toUserFriendlyValue(DiffInfoContext context, String configProperty) {
    	if (context != null && context.getCurrentNode() != null) {
    		if ("insertFields".equals(configProperty)) {
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
