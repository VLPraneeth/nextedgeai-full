package com.syncari.core.actions;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.sharable.SharableActionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.quickstart.v2.dependency.DependencyUtil;
import com.syncari.core.service.ConnectorService;
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

@Component(ActionConstants.UPDATE_EXTERNAL_RECORD)
public class UpdateExternalRecordAction extends DefaultAction {

    @Autowired
    ConnectorService connectorService;

    @Autowired
    SchemaService schemaService;

    private static final String SYNAPSE_ID = "synapseId";
    private static final String ENTITY_ID = "entityId";

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
        var synapseId = configMap.get(SYNAPSE_ID);
        var entityId = configMap.get(ENTITY_ID);

        // validate synapseId
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), synapseId == null,
                i18n("invalid_config_in_node", configNameLabelMap.get(SYNAPSE_ID),
                        null, node.getName(), graph.getName()), ErrorCode.E1051.getCode()).ifPresent(e -> errors.add(e));

        // validate entityId
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), entityId == null,
                i18n("invalid_config_in_node", configNameLabelMap.get(ENTITY_ID),
                        null, node.getName(), graph.getName()), ErrorCode.E1052.getCode()).ifPresent(e -> errors.add(e));
        if (entityId != null) {
            Optional<EntityDefinition> entity = schemaService.findEntity(entityId.toString());
            validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), entity.isEmpty(),
                    i18n("invalid_config_in_node", configNameLabelMap.get(ENTITY_ID),
                            entityId, node.getName(), graph.getName()), ErrorCode.E1053.getCode()).ifPresent(e -> errors.add(e));
            EntityDefinition syncariEntity = entity.get();
            List<Map<String, Map<String, String>>> updateFields = (List<Map<String, Map<String, String>>>) configMap.get("updateFields");

			validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
					updateFields == null || updateFields.isEmpty(),
					i18n("invalid_config_in_node", configNameLabelMap.get("updateFields"), "Empty Fields", node.getName(), graph.getName()),
                    ErrorCode.E1054.getCode()).ifPresent(e -> errors.add(e));
			// validate each field
            if (updateFields != null) {
                for (Map<String, Map<String, String>> s : updateFields) {
                    validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                            s.get("updateField") == null, i18n("update_records_empty_attribute",
                                    validationContext.getNode().getName(), validationContext.getGraph().getName()), ErrorCode.E1055.getCode())
                            .ifPresent(e -> errors.add(e));
                    if (s.get("updateField") != null) {
                        String attributeId = s.get("updateField").get("value");
                        // Skip validation if the field contains tokens
                        if (!TokenHelper.hasTokens(attributeId)) {
                            AttributeDefinition attribute = syncariEntity.getAttribute(attributeId);
                            validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), attribute == null,
                                    i18n("update_record_invalid_attribute", validationContext.getNode().getName(),
                                            validationContext.getGraph().getName(), attributeId), ErrorCode.E1056.getCode())
                                    .ifPresent(e -> errors.add(e));
                            // do this validation only if attribute is not null
                            if (attribute != null){
                                validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), (attribute.isUpdatable() == false),
                                        i18n("update_record_readonly_attribute", attribute.getApiName(), validationContext.getNode().getName(),
                                                validationContext.getGraph().getName()), ErrorCode.E1056.getCode())
                                        .ifPresent(e -> errors.add(e));
                            }
                        }

                    }
                }
			}
		}

        return errors;

    }

    @Override
    public void extract(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode node = context.getCurrentNode();
        super.extract(context);

        SharableActionNodeConfig actionNodeConfig = node.getTypedConfiguration();
        Map<String, Object> configMap = actionNodeConfig.getConfigMap();
        var synapseId = configMap.getOrDefault(SYNAPSE_ID, "").toString();
        Optional<Connector> connectorMaybe = context.getConnector(synapseId);
        connectorMaybe.ifPresent(conn -> {
            qsConfig.addDependency(DependencyUtil.getConnectorDependency(conn));
        });

        // 2. Selected Syncari entity
        var entityId = configMap.get(ENTITY_ID).toString();
        EntityDefinition entity = context.getEntity(entityId).orElseThrow();
        qsConfig.addDependency(DependencyUtil.getEntityDependency(entity));

        // 3. attributes from updateFields config
        List<Map<String, Map<String, String>>> updateFields = (List<Map<String, Map<String, String>>>) configMap.get("updateFields");
        for (Map<String, Map<String, String>> s : updateFields) {
            String attributeId = s.get("updateField").get("value");

            // Check if updateField contains tokens - if so, extract token dependencies
            if(TokenHelper.hasTokens(attributeId)) {
                DependencyUtil.getTokenDependencies(attributeId).forEach(d -> qsConfig.addDependency(d));
            } else {
                // Only add attribute dependency if it's not a token
                AttributeDefinition attribute = context.getAttribute(attributeId).orElseThrow();
                qsConfig.addDependency(DependencyUtil.getAttributeDependency(attribute));
            }

            String newVal = s.get("newValue").get("value");
            DependencyUtil.getTokenDependencies(newVal).forEach(d -> qsConfig.addDependency(d));

        }
    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
        Map<String, Object> resolvedConfigMap = getDefaultResolvedConfig(context);

        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode sharableNode = context.getCurrentNode();
        SharableActionNodeConfig actionNodeConfig = sharableNode.getTypedConfiguration();
        var synapseId = actionNodeConfig.getConfigMap().getOrDefault(SYNAPSE_ID, "").toString();
        Connector resolvedConn = (Connector) qsConfig.getResolvedValueByType(synapseId, QSDependency.Type.Connector);
        if(resolvedConn != null){
            resolvedConfigMap.put(SYNAPSE_ID, resolvedConn.getId());
        }

        // 2. Selected entity
        var entityDefId = actionNodeConfig.getConfigMap().get(ENTITY_ID).toString();
        EntityDefinition resolvedEntity = (EntityDefinition) qsConfig.getResolvedValueByType(entityDefId, QSDependency.Type.Entity);
        if(resolvedEntity != null){
            resolvedConfigMap.put(ENTITY_ID, resolvedEntity.getId());
        }

        // 3. attributes from updateFields config
        List<Map<String, Map<String, String>>> updateFields = (List<Map<String, Map<String, String>>>) actionNodeConfig.getConfigMap().get("updateFields");
        for (Map<String, Map<String, String>> s : updateFields) {
            Map<String, String> updateFieldMap = new HashMap<>(s.get("updateField"));
            String attributeId = updateFieldMap.get("value");

            // Check if updateField contains tokens - resolve tokens first
            if(TokenHelper.hasTokens(attributeId)){
                var resolvedValue = (String) qsConfig.getResolvedValueByType(attributeId, QSDependency.Type.Token);
                if(resolvedValue != null){
                    updateFieldMap.put("value", resolvedValue);
                }
            } else {
                // Only resolve as attribute if it's not a token
                AttributeDefinition resolvedAttrib = (AttributeDefinition) qsConfig.getResolvedValueByType(attributeId, QSDependency.Type.Attribute);
                if(resolvedAttrib != null){
                    updateFieldMap.put("value", resolvedAttrib.getId());
                }
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
        resolvedConfigMap.put("updateFields", updateFields);

        actionNodeConfig.setConfigMap(resolvedConfigMap);
        sharableNode.setConfiguration(actionNodeConfig);

        return sharableGraphTransformer.toMappingNode(sharableNode, context.getCurrentPipeline());
    }
    
    @Override
    public List<Pair<String, String>> toUserFriendlyValue(DiffInfoContext context, String configProperty) {
    	if (ENTITY_ID.equals(configProperty) && context != null && context.getCurrentNode() != null) {
    		Map<String, Object> configMap = context.getCurrentNode().getConfiguration().getConfigMap();
    		if(configMap == null) {
    			configMap = Map.of();
    		}
            var entityId = configMap.get(ENTITY_ID);
            if(entityId != null) {
            	var entity = schemaService.findEntity(entityId.toString());
            	if(entity.isPresent()) {
            		return List.of(Pair.of(configProperty, entity.get().getDisplayName()));
            	}
            }
    	} else if ("updateFields".equals(configProperty)) {
    		Map<String, Object> configMap = context.getCurrentNode().getConfiguration().getConfigMap();
			var syncariEntityDefId = configMap.getOrDefault(ENTITY_ID, configProperty);
			Optional<EntityDefinition> entityDefinition = schemaService.getSyncariEntityById(String.valueOf(syncariEntityDefId));
			if(entityDefinition.isPresent()) {
				List<Map<String, Map<String, String>>> updateFields = (List<Map<String, Map<String, String>>>) configMap.get(configProperty);
				List<List<String>> row = new ArrayList<>();
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
		            row.add(col);
		        }
		        if(row.size() == 1) {
		        	return List.of(Pair.of(configProperty, row.get(0).toString()));
		        } else {
		        	return List.of(Pair.of(configProperty, row.toString()));
		        }
			}
		}
    	return super.toUserFriendlyValue(context, configProperty);
    }
}
