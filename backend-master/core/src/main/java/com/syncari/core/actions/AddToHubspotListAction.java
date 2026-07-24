package com.syncari.core.actions;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.syncari.core.model.util.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.ActionDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.GenericActionConfig;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.misc.sharable.SharableActionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.quickstart.v2.dependency.DependencyUtil;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.validation.ValidationContext;

@Component(ActionConstants.ADD_TO_HUBSPOT_LIST)
public class AddToHubspotListAction extends DefaultAction {

    @Autowired
    ConnectorService connectorService;

    private static final String SYNAPSE_ID = "synapseId";
    private static final String LIST_ID = "listId";
    private static final String VALUE_TYPE = "valueType";
    private static final String VALUE = "value";

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
        var listId = configMap.get(LIST_ID);
        var valueType = configMap.get(VALUE_TYPE);
        var value = configMap.get(VALUE);

        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), synapseId == null,
                i18n("invalid_config_in_node", configNameLabelMap.get(SYNAPSE_ID),
                        null, node.getName(), graph.getName()), ErrorCode.E1045.getCode()).ifPresent(e -> errors.add(e));

        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), listId == null,
                i18n("invalid_config_in_node", configNameLabelMap.get(LIST_ID),
                        null, node.getName(), graph.getName()), ErrorCode.E1046.getCode()).ifPresent(e -> errors.add(e));

        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), valueType == null,
                i18n("invalid_config_in_node", configNameLabelMap.get(VALUE_TYPE),
                        null, node.getName(), graph.getName()), ErrorCode.E1047.getCode()).ifPresent(e -> errors.add(e));

        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), value == null,
                i18n("invalid_config_in_node", configNameLabelMap.get(VALUE),
                        null, node.getName(), graph.getName()), ErrorCode.E1048.getCode()).ifPresent(e -> errors.add(e));

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
