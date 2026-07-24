package com.syncari.core.actions;

import com.syncari.connector.Constants;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.ActionDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.GenericActionConfig;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.misc.sharable.SharableActionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.quickstart.v2.dependency.DependencyUtil;
import com.syncari.core.service.ActionService;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.validation.ValidationContext;

import lombok.extern.slf4j.Slf4j;

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
@Component(ActionConstants.ADD_TO_MARKETO_PROGRAM)
public class AddToMarketoProgramAction extends DefaultAction {

    @Autowired
    ActionService actionService;

    @Autowired
    ConnectorService connectorService;

    private static final String SYNAPSE_ID = "synapseId";
    private static final String PROGRAM_ID = "programId";
    private static final String LEAD_ID = "leadId";

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
        var programId = configMap.get(PROGRAM_ID);
        var leadId = configMap.get(LEAD_ID);

        // validate synapse if exists and if its a valid marketo synapse
		if (synapseId != null) {
			Optional<Connector> connectorMaybe = connectorService.find(synapseId.toString());
            validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                    connectorMaybe.isEmpty() || !Constants.MARKETO.equals(connectorMaybe.get().getMetadata().getName()),
                    i18n("invalid_config_in_node", configNameLabelMap.get(SYNAPSE_ID), synapseId.toString(),
                            node.getName(), graph.getName()), ErrorCode.E1049.getCode())
                    .ifPresent(e -> errors.add(e));
        }

        // validate programId - should be a valid integer or token
        if(programId != null && !TokenHelper.hasTokens(programId.toString())) {
            try {
                // validate programId - should be a valid integer
                Integer.parseInt(programId.toString());
            } catch (NumberFormatException e) {
            	log.error("validation error occured ", e);
				errors.add(ValidationError.scopedError(node.getScope(), node.getId())
						.withMessage(i18n("invalid_config_in_node", configNameLabelMap.get(PROGRAM_ID),
								programId.toString(), node.getName(), graph.getName())));
            }

        }

        // validate leadId - should be a valid integer or token
        if(leadId != null && !TokenHelper.hasTokens(leadId.toString())) {
            try {
                Integer.parseInt(leadId.toString());
            } catch (NumberFormatException e) {
            	log.error("validation error occured ", e);
            	errors.add(ValidationError.scopedError(node.getScope(), node.getId())
						.withMessage(i18n("invalid_config_in_node", configNameLabelMap.get(LEAD_ID),
								leadId.toString(), node.getName(), graph.getName())));
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
