package com.syncari.core.actions;

import com.syncari.connector.Constants;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.sharable.SharableActionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.quickstart.v2.dependency.DependencyUtil;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.validation.ValidationContext;

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

@Component(ActionConstants.CREATE_ZENDESK_TICKET)
public class CreateZendeskTicketAction extends DefaultAction {

    @Autowired
    ConnectorService connectorService;

    private static final String SYNAPSE_ID = "synapseId";
    private static final String TYPE = "type";
    private static final String PRIORITY = "priority";


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
        ActionDefinition actionDef = actionService.getAction(node.getApiName()).get();
        Map<String, String> configNameLabelMap = actionDef.getConfiguration().stream()
                .collect(Collectors.toMap(c -> c.getName(), c -> c.getLabel()));
        Map<String, Object> configMap = actionNodeConfig.getConfigMap();
        var synapseId = configMap.get(SYNAPSE_ID);
        var type = configMap.get(TYPE);
        var priority = configMap.get(PRIORITY);

        // validate each config field to be non-null
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                synapseId == null || StringUtils.isBlank(synapseId.toString()),
                i18n("missing_config_from_node", configNameLabelMap.get(SYNAPSE_ID), node.getName(), graph.getName()), ErrorCode.E1057.getCode())
                .ifPresent(e -> errors.add(e));
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                type == null || StringUtils.isBlank(type.toString()),
                i18n("missing_config_from_node", configNameLabelMap.get(TYPE), node.getName(), graph.getName()), ErrorCode.E1058.getCode())
                .ifPresent(e -> errors.add(e));
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                priority == null || StringUtils.isBlank(priority.toString()),
                i18n("missing_config_from_node", configNameLabelMap.get(PRIORITY), node.getName(), graph.getName()), ErrorCode.E1059.getCode())
                .ifPresent(e -> errors.add(e));

        // validate synapse if exists and if its a valid zendesk synapse
        if(synapseId != null) {
        	Optional<Connector> connectorMaybe = connectorService.find(synapseId.toString());
        	validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
        			connectorMaybe.isEmpty() || !Constants.ZENDESK.equals(connectorMaybe.get().getMetadata().getName()),
        			i18n("invalid_config_in_node", configNameLabelMap.get(SYNAPSE_ID), synapseId.toString(), node.getName(), graph.getName()),
                    ErrorCode.E1060.getCode()).ifPresent(e -> errors.add(e));
        	if(!connectorMaybe.isEmpty() && Constants.ZENDESK.equals(connectorMaybe.get().getMetadata().getName())) {
        		if (ActionsSeed.zendeskTypeValues().stream().filter(m -> m.get("value").equals(type)).findFirst().isEmpty()) {
        			errors.add(ValidationError.scopedError(node.getScope(), node.getId())
        					.withMessage(i18n("invalid_config_in_node", configNameLabelMap.get(TYPE), type.toString(),
        							node.getName(), graph.getName())));
        		}
        		
        		if (ActionsSeed.zendeskPriorityValues().stream().filter(m -> m.get("value").equals(priority)).findFirst().isEmpty()) {
        			errors.add(ValidationError.scopedError(node.getScope(), node.getId())
        					.withMessage(i18n("invalid_config_in_node", configNameLabelMap.get(PRIORITY),
        							priority.toString(), node.getName(), graph.getName())));
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
    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
        Map<String, Object> resolvedConfigMap = getDefaultResolvedConfig(context);

        // resolve the slack credential and create mapping node
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode sharableNode = context.getCurrentNode();
        SharableActionNodeConfig actionNodeConfig = sharableNode.getTypedConfiguration();
        var synapseId = actionNodeConfig.getConfigMap().getOrDefault(SYNAPSE_ID, "").toString();
        Connector resolvedConn = (Connector) qsConfig.getResolvedValueByType(synapseId, QSDependency.Type.Connector);
        if(resolvedConn != null){
            resolvedConfigMap.put(SYNAPSE_ID, resolvedConn.getId());
        }

        actionNodeConfig.setConfigMap(resolvedConfigMap);
        sharableNode.setConfiguration(actionNodeConfig);

        return sharableGraphTransformer.toMappingNode(sharableNode, context.getCurrentPipeline());
    }
}
