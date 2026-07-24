package com.syncari.core.functions;

import com.syncari.core.DataTransformer;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.AttributeSourceNodeConfig;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.ServiceCredential;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.quickstart.v2.dependency.DependencyUtil;
import com.syncari.core.enrich.ClearbitService;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.DataServiceFactory;
import com.syncari.core.service.LookupService;
import com.syncari.core.service.ProvisioningService;
import com.syncari.core.validation.GraphValidationUtil;
import com.syncari.core.validation.ValidationContext;

import lombok.extern.slf4j.Slf4j;

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

@Slf4j
@Component(FunctionConstants.ENRICH_PERSON)
public class EnrichPersonFunction extends DefaultFunction {

    @Autowired
    ConnectorService connectorService;

    @Autowired
    ProvisioningService provService;

    @Autowired
    ClearbitService clearbitService;

    @Autowired
    DataServiceFactory dataServiceFactory;

    @Autowired
    DataTransformer transformer;

    private static final String SERVICE_ID = "serviceId";
    private static final String ENRICH_USING = "enrichUsing";
    private static final String SOURCE_ENTITY_ID = "entityDefinition";
    private static final String INPUT_FIELD_ID = "emailField";
    private static final String LOOKUP_FIELD_NAME = "lookUpKey";

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

        SimpleFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
        FunctionDefinition funcDef = functionNodeConfig.getFunctionCall().getFunctionDefinition();
        Map<String, String> configNameLabelMap = funcDef.getConfiguration().stream().collect(Collectors.toMap(c -> c.getName(), c -> c.getLabel()));
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();

        // validate enrichment source
        var serviceId = configMap.get(SERVICE_ID);
        if(serviceId == null) {
        	return errors;
        }
        Optional<ServiceCredential> serviceCred = provService.getCredentials(serviceId.toString());
        Optional<Connector> connector = serviceId == null ? Optional.empty() : connectorService.find(serviceId.toString());
		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
				serviceCred.isEmpty() && connector.isEmpty(), i18n("invalid_config_in_node",
						configNameLabelMap.get(SERVICE_ID), serviceId, node.getName(), graph.getName()), ErrorCode.E1100.getCode())
								.ifPresent(e -> errors.add(e));


        Map<String, String> inputFields = new HashMap<>();
        Map<String, String> outputFields = new HashMap<>();
        if(serviceCred.isPresent()){
            inputFields = clearbitService.getInputFields("contact");
            outputFields = clearbitService.getOutputFields("contact");
        } else if(connector.isPresent()){
            Connector enrichConnector = connector.get();
            LookupService service = dataServiceFactory.getLookupService(enrichConnector.getMetadata());
            var connectorInfo = transformer.toConnectorInfo(enrichConnector);
            inputFields = service.getInputFields(connectorInfo, "contact");
            outputFields = service.getOutputFields(connectorInfo, "contact");
        }

        // validate enrich using input field
        var enrichUsing = configMap.get(ENRICH_USING);
        if(enrichUsing == null) {
        	return errors;
        }
		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
				!inputFields.keySet().contains(enrichUsing), i18n("invalid_config_in_node",
						configNameLabelMap.get(ENRICH_USING), enrichUsing, node.getName(), graph.getName()), ErrorCode.E1101.getCode())
								.ifPresent(e -> errors.add(e));


        boolean connectedToCore = graph.pathToNodeMatches(node,n->n.getId().equals(graph.getCoreNode().getId()));
        // validate source entity
        var sourceEntityId = configMap.get(SOURCE_ENTITY_ID);
        if(sourceEntityId == null) {
        	return errors;
        }
        //if node is  NOT connected to core, the source can only be one of the sources
        List<MappingNode> connectedSources = graph.getSources().filter(src -> graph.pathToNodeMatches(node, n->n.getId().equals(src.getId())))
                .collect(Collectors.toList());
        if(connectedSources.isEmpty()){
            return errors;
        }
		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
				!connectedToCore
						&& !GraphValidationUtil.isValidSourceEntityReference(sourceEntityId.toString(), validationContext),
				i18n("invalid_config_in_node", configNameLabelMap.get(SOURCE_ENTITY_ID), sourceEntityId, node.getName(),
						graph.getName()), ErrorCode.E1102.getCode()).ifPresent(e -> errors.add(e));
        //if node is connected to core, the source can only be syncari
		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
				connectedToCore && !GraphValidationUtil.isValidCoreEntityReference(sourceEntityId.toString(), validationContext),
				i18n("invalid_config_in_node", configNameLabelMap.get(SOURCE_ENTITY_ID), sourceEntityId, node.getName(),
						graph.getName()), ErrorCode.E1103.getCode()).ifPresent(e -> errors.add(e));

        // validate input field belong to source entity selected
        var inputFieldId = configMap.get(INPUT_FIELD_ID);
        if(inputFieldId == null) {
        	return errors;
        }
		try {
			EntityDefinition srcEntity = schemaService.getEntity(sourceEntityId.toString());
			boolean isFieldFromSourceEntity = srcEntity.getAttributes().stream().anyMatch(a -> inputFieldId.equals(a.getId()));
			validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), !isFieldFromSourceEntity,
					i18n("invalid_config_in_node", configNameLabelMap.get(INPUT_FIELD_ID), inputFieldId, node.getName(),
							graph.getName()), ErrorCode.E1104.getCode()).ifPresent(e -> errors.add(e));
		} catch (RuntimeException e) {
			log.error("validation error occured ", e);
		}

        // validate enrich using input field
        var lookupFieldName = configMap.get(LOOKUP_FIELD_NAME);
        if(lookupFieldName == null) {
        	return errors;
        }
		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
				!outputFields.keySet().contains(lookupFieldName), i18n("invalid_config_in_node",
						configNameLabelMap.get(LOOKUP_FIELD_NAME), lookupFieldName, node.getName(), graph.getName()), ErrorCode.E1105.getCode())
								.ifPresent(e -> errors.add(e));
		
		return errors;
    }

    protected boolean isValidSourceEntityReference(String entityId, ValidationContext validationContext){
        MappingGraph graph = validationContext.getGraph();
        List<String> sourceEntityIds = graph.getSources().map(n -> {
            AttributeSourceNodeConfig cfg = n.getTypedConfiguration();
            return cfg.getAttributeDefinition().getEntityId();
        }).collect(Collectors.toList());
        return sourceEntityIds.contains(entityId);
    }

    @Override
    public void extract(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode node = context.getCurrentNode();
        super.extract(context);
        SharableFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();
        var serviceId = configMap.get(SERVICE_ID).toString();
        Optional<ServiceCredential> serviceCred = provService.getCredentials(serviceId);
        Optional<Connector> connector = serviceId == null ? Optional.empty() : connectorService.find(serviceId);
        if(serviceCred.isPresent()){
            qsConfig.addDependency(new QSDependency()
                    .setId(serviceCred.get().getId())
                    .setType(QSDependency.Type.Service)
                    .setSourceValue(serviceCred.get()));
        } else {
            Connector enrichConnector = connector.get();
            qsConfig.addDependency(new QSDependency()
                    .setId(enrichConnector.getId())
                    .setType(QSDependency.Type.Service)
                    .setSourceValue(enrichConnector));
        }

        var inputEntityId = configMap.getOrDefault(SOURCE_ENTITY_ID, "").toString();
        Optional<EntityDefinition> ipEntity = context.getEntity(inputEntityId);
        ipEntity.ifPresent(e -> qsConfig.addDependency(DependencyUtil.getEntityDependency(e)));

        var inputFieldId = configMap.getOrDefault(INPUT_FIELD_ID, "").toString();
        Optional<AttributeDefinition> ipField = context.getAttribute(inputFieldId);
        ipField.ifPresent(f -> qsConfig.addDependency(DependencyUtil.getAttributeDependency(f)));
    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode sharableNode = context.getCurrentNode();
        SharableFunctionNodeConfig functionNodeConfig = sharableNode.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();
        var srcServiceId = configMap.get(SERVICE_ID).toString();

        var service = qsConfig.getResolvedValueByType(srcServiceId, QSDependency.Type.Service);
        if(service instanceof ServiceCredential){
            ServiceCredential serviceCred = (ServiceCredential) service;
            configMap.put(SERVICE_ID, serviceCred.getId());
        } else {
            Connector connector = (Connector) service;
            configMap.put(SERVICE_ID, connector.getId());
        }

        var inputEntityId = configMap.get(SOURCE_ENTITY_ID).toString();
        EntityDefinition inputEntity = (EntityDefinition) qsConfig.getResolvedValueByType(inputEntityId, QSDependency.Type.Entity);
        configMap.put(SOURCE_ENTITY_ID, inputEntity.getId());

        var inputFieldId = configMap.get(INPUT_FIELD_ID).toString();
        AttributeDefinition inputField = (AttributeDefinition) qsConfig.getResolvedValueByType(inputFieldId, QSDependency.Type.Attribute);
        configMap.put(INPUT_FIELD_ID, inputField.getId());

        functionNodeConfig.getFunctionCall().setConfig(configMap);
        functionNodeConfig.getFunctionCall().setParams(resolveParams(context, functionNodeConfig));
        sharableNode.setConfiguration(functionNodeConfig);

        return sharableGraphTransformer.toMappingNode(sharableNode, context.getCurrentPipeline());
    }
}
