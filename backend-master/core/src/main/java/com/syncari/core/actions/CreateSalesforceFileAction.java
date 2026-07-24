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

@Component(ActionConstants.CREATE_SALESFORCE_FILE)
public class CreateSalesforceFileAction extends DefaultAction {

    final ConnectorService connectorService;

    private static final String SYNAPSE_ID = "synapseId";

    @Autowired
    public CreateSalesforceFileAction(final ConnectorService connectorService) {
        this.connectorService = connectorService;
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
        List<ValidationError> errors = new ArrayList<>(super.validateWithoutException(validationContext));
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
        var fileLink = configMap.get("fileLink");

        // validate each config field to be non-null
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                synapseId == null || StringUtils.isBlank(synapseId.toString()),
                i18n("missing_config_from_node", configNameLabelMap.get(SYNAPSE_ID), node.getName(), graph.getName()), ErrorCode.E1050.getCode())
                .ifPresent(e -> errors.add(e));
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                fileLink == null || StringUtils.isBlank(fileLink.toString()),
                i18n("missing_config_from_node", configNameLabelMap.get("fileLink"), node.getName(), graph.getName()), ErrorCode.E1041.getCode())
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
        var synapseId = configMap.getOrDefault(SYNAPSE_ID, "").toString();
        Optional<Connector> connectorMaybe = context.getConnector(synapseId);
        connectorMaybe.ifPresent(conn -> qsConfig.addDependency(DependencyUtil.getConnectorDependency(conn)));
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
