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
import com.syncari.core.repositories.customer.ActionDefinitionRepo;
import com.syncari.core.service.ActionService;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.token.TokenHelper;
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

@Component(ActionConstants.ADD_TO_SFDC_CAMPAIGN)
public class AddToCampaignAction extends DefaultAction {

    @Autowired
    ConnectorService connectorService;

    @Autowired
    ActionService actionService;

    private static final String SYNAPSE_ID = "synapseId";
    private static final String ENTITY = "entity";
    private static final String CAMPAIGN_ID = "campaignId";
    private static final String STATUS = "status";

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
        //List<ValidationError>
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
        var entity = configMap.get(ENTITY);
        var campaignId = configMap.get(CAMPAIGN_ID);
        var status = configMap.get(STATUS);

        // validate each config field to be non-null
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                synapseId == null || StringUtils.isBlank(synapseId.toString()),
                i18n("missing_config_from_node", configNameLabelMap.get(SYNAPSE_ID), node.getName(), graph.getName()), ErrorCode.E1040.getCode())
                .ifPresent(e -> errors.add(e));
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                entity == null || StringUtils.isBlank(entity.toString()),
                i18n("missing_config_from_node", configNameLabelMap.get(ENTITY), node.getName(), graph.getName()), ErrorCode.E1041.getCode())
                .ifPresent(e -> errors.add(e));
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                campaignId == null || StringUtils.isBlank(campaignId.toString()),
                i18n("missing_config_from_node", configNameLabelMap.get(CAMPAIGN_ID), node.getName(), graph.getName()), ErrorCode.E1042.getCode())
                .ifPresent(e -> errors.add(e));


        // validate synapse if exists and if its a valid salesforce synapse
		if (synapseId != null && StringUtils.isNotBlank(synapseId.toString())) {
			Optional<Connector> connectorMaybe = connectorService.find(synapseId.toString());
			validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
					connectorMaybe.isEmpty()
							|| !Constants.SALESFORCE.equals(connectorMaybe.get().getMetadata().getName()),
					i18n("invalid_config_in_node", configNameLabelMap.get(SYNAPSE_ID), synapseId.toString(), node.getName(), graph.getName()),
                    ErrorCode.E1043.getCode()).ifPresent(e -> errors.add(e));
		}

        // validate entity to be either Lead or Contact if not a token
		if (entity != null && StringUtils.isNotBlank(entity.toString())) {
			validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
					!TokenHelper.hasTokens(entity.toString())
							&& !List.of("Lead", "Contact").contains(entity.toString()),
					i18n("invalid_config_in_node", configNameLabelMap.get(ENTITY), entity.toString(), node.getName(), graph.getName()),
                    ErrorCode.E1044.getCode()).ifPresent(e -> errors.add(e));
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
