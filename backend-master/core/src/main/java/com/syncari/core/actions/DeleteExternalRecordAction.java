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

import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Component(ActionConstants.DELETE_EXTERNAL_RECORD)
public class DeleteExternalRecordAction extends DefaultAction {

    @Autowired
    ConnectorService connectorService;

    @Autowired
    SchemaService schemaService;

    private static final String SYNAPSE_ID = "synapseId";
    private static final String ENTITY_ID = "entityId";
    private static final String RECORD_ID = "recordId";

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
        var recordId = configMap.get(RECORD_ID);

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
		}

        // validate recordId
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), entityId == null,
                i18n("invalid_config_in_node", configNameLabelMap.get(RECORD_ID),
                        null, node.getName(), graph.getName()), ErrorCode.E1052.getCode()).ifPresent(e -> errors.add(e));

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
		}
    	return super.toUserFriendlyValue(context, configProperty);
    }
}
