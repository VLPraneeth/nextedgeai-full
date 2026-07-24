package com.syncari.core.actions;

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
import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.ServiceCredentialService;
import com.syncari.core.validation.ValidationContext;
import com.syncari.utils.Pair;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component(ActionConstants.SEND_SLACK_MESSAGE)
public class SendSlackMessageAction extends DefaultAction {

    @Autowired
    ServiceCredentialService credentialService;

    @Autowired
    ConnectorService connectorService;

    private static final String SERVICE_ID = "serviceId";
    private static final String CHANNEL = "channel";
    private static final String MESSAGE = "message";
    

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
        var serviceId = configMap.get(SERVICE_ID);
        var channel = configMap.get(CHANNEL);
        var message = configMap.get(MESSAGE);

        // validate each config field to be non-null
		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
				serviceId == null || StringUtils.isBlank(serviceId.toString()),
				i18n("missing_config_from_node", configNameLabelMap.get(SERVICE_ID), node.getName(), graph.getName()), ErrorCode.E1075.getCode())
						.ifPresent(e -> errors.add(e));
		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
				channel == null || StringUtils.isBlank(channel.toString()),
				i18n("missing_config_from_node", configNameLabelMap.get(CHANNEL), node.getName(), graph.getName()), ErrorCode.E1076.getCode())
						.ifPresent(e -> errors.add(e));   
		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
				message == null || StringUtils.isBlank(message.toString()),
				i18n("missing_config_from_node", configNameLabelMap.get(MESSAGE), node.getName(), graph.getName()), ErrorCode.E1077.getCode())
						.ifPresent(e -> errors.add(e));   
        return errors;
    }

    @Override
    public void extract(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode node = context.getCurrentNode();
        super.extract(context);

        SharableActionNodeConfig actionNodeConfig = node.getTypedConfiguration();
        Map<String, Object> configMap = actionNodeConfig.getConfigMap();
        var serviceId = configMap.getOrDefault(SERVICE_ID, "").toString();
        Optional<Connector> slackMaybe = connectorService.find(serviceId);
        slackMaybe.ifPresent(service -> {
            qsConfig.addDependency(new QSDependency()
                    .setId(service.getId())
                    .setType(QSDependency.Type.Service)
                    .setSourceValue(service));
        });
    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
        Map<String, Object> resolvedConfigMap = getDefaultResolvedConfig(context);

        // resolve the slack credential and create mapping node
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode sharableNode = context.getCurrentNode();
        SharableActionNodeConfig actionNodeConfig = sharableNode.getTypedConfiguration();
        var serviceId = resolvedConfigMap.getOrDefault(SERVICE_ID, "").toString();
        Connector resolvedCred = (Connector) qsConfig.getResolvedValueByType(serviceId, QSDependency.Type.Service);
        if(resolvedCred != null){
            resolvedConfigMap.put(SERVICE_ID, resolvedCred.getId());
        }

        actionNodeConfig.setConfigMap(resolvedConfigMap);
        sharableNode.setConfiguration(actionNodeConfig);

        return sharableGraphTransformer.toMappingNode(sharableNode, context.getCurrentPipeline());
    }
    
    @Override
    public List<Pair<String, String>> toUserFriendlyValue(DiffInfoContext context, String configProperty) {
    	if (SERVICE_ID.equals(configProperty) && context != null && context.getCurrentNode() != null) {
    		Map<String, Object> configMap = context.getCurrentNode().getConfiguration().getConfigMap();
    		if(configMap == null) {
    			configMap = Map.of();
    		}
            var synapseId = configMap.get(SERVICE_ID);
            if(synapseId != null) {
            	var con = connectorService.find(synapseId.toString());
            	if(con.isPresent()) {
            		return List.of(Pair.of(configProperty, con.get().getName()));
            	}
            }
    	}
    	return super.toUserFriendlyValue(context, configProperty);
    }
}
