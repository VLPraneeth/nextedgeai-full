package com.syncari.core.actions;

import com.syncari.connector.Constants;
import com.syncari.connector.service.MarketoService;
import com.syncari.core.DataTransformer;
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

@Slf4j
@Component
public class MarketoListAction extends DefaultAction {

    @Autowired
    ActionService actionDefinitionRepo;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    MarketoService marketoService;

    @Autowired
    DataTransformer transformer;

    private static final String SYNAPSE_ID = "synapseId";
    private static final String LIST_ID = "listId";
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
        ActionDefinition actionDef = actionDefinitionRepo.findByName(node.getApiName()).get();
        Map<String, String> configNameLabelMap = actionDef.getConfiguration().stream()
                .collect(Collectors.toMap(c -> c.getName(), c -> c.getLabel()));
        Map<String, Object> configMap = actionNodeConfig.getConfigMap();
        var synapseId = configMap.get(SYNAPSE_ID);
        var listId = configMap.get(LIST_ID);
        var leadId = configMap.get(LEAD_ID);

        // validate each config field to be non-null
		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
				synapseId == null || StringUtils.isBlank(synapseId.toString()),
				i18n("missing_config_from_node", configNameLabelMap.get(SYNAPSE_ID), node.getName(), graph.getName()), ErrorCode.E1066.getCode())
						.ifPresent(e -> errors.add(e));
		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
				listId == null || StringUtils.isBlank(listId.toString()),
				i18n("missing_config_from_node", configNameLabelMap.get(LIST_ID), node.getName(), graph.getName()), ErrorCode.E1067.getCode())
						.ifPresent(e -> errors.add(e));
		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
				leadId == null || StringUtils.isBlank(leadId.toString()),
				i18n("missing_config_from_node", configNameLabelMap.get(LEAD_ID), node.getName(), graph.getName()), ErrorCode.E1068.getCode())
						.ifPresent(e -> errors.add(e));


        // validate synapse if exists and if its a valid marketo synapse
		if(synapseId != null && StringUtils.isNotBlank(synapseId.toString())) {
			Optional<Connector> connectorMaybe = connectorService.find(synapseId.toString());
			validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), connectorMaybe.isEmpty() || !Constants.MARKETO.equals(connectorMaybe.get().getMetadata().getName()),
					i18n("invalid_config_in_node", configNameLabelMap.get(SYNAPSE_ID), synapseId.toString(), node.getName(), graph.getName()), ErrorCode.E1069.getCode()).ifPresent(e -> errors.add(e));
			
			if(connectorMaybe.isPresent() && listId != null && StringUtils.isNotBlank(listId.toString()) && !TokenHelper.hasTokens(listId.toString())) {
				try {
					// validate listId - should be a valid integer
					Integer.parseInt(listId.toString());
					// check if connector has access to the list
					marketoService.validateListAccess(listId.toString(), transformer.toConnectorInfo(connectorMaybe.get()));
				} catch (NumberFormatException e) {
					log.error("validation error occured ", e);
					errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(i18n("invalid_config_in_node", configNameLabelMap.get(LIST_ID), listId.toString(), node.getName(), graph.getName())));
				} catch (RuntimeException e){
					log.error("validation error occured ", e);
					errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(i18n("invalid_list_marketo_synapse", connectorMaybe.get().getName(), listId.toString(), node.getName(), graph.getName())));
				}
				
			}
		}



        // validate leadId - should be a valid integer
        if(leadId != null && StringUtils.isNotBlank(leadId.toString()) && !TokenHelper.hasTokens(leadId.toString())) {
            try {
                Integer.parseInt(leadId.toString());
            } catch (NumberFormatException e) {
            	log.error("validation error occured ", e);
            	errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(i18n("invalid_config_in_node", configNameLabelMap.get(LEAD_ID), leadId.toString(), node.getName(), graph.getName())));
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
