package com.syncari.core.actions;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Connector;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.misc.sharable.SharableActionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.validation.ValidationContext;
import com.syncari.utils.Pair;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component(ActionConstants.SEND_MSTEAMS_MESSAGE)
public class SendMSTeamsMessageAction extends DefaultAction {

    private static final String SERVICE_ID = "serviceId";
    private static final String CHANNEL = "channel";
    private static final String TEAM_ID = "teamId";
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
        Optional<Connector> meteamsMaybe = connectorService.find(serviceId);
        meteamsMaybe.ifPresent(service -> {
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
