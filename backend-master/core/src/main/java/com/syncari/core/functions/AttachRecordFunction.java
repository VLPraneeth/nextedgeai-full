package com.syncari.core.functions;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.service.SchemaService;
import com.syncari.core.validation.GraphValidationUtil;
import com.syncari.core.validation.ValidationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Component(FunctionConstants.ATTACH_RECORD)
public class AttachRecordFunction extends DefaultFunction{

    private static final String SYNCARI_ENTITY_DEF_ID = "syncariEntityDefId";
    private static final String EXTERNAL_ENTITY_DEF_ID = "externalEntityDefId";
    private static final String INPUT_FIELD_ID = "inputFieldId";
    private static final String SEARCH_FIELD_ID = "searchFieldId";

    @Autowired
    SchemaService schemaService;

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
        
        Connector syncariConnector = validationContext.getSyncariConnector();

        SimpleFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
        FunctionDefinition funcDef = functionNodeConfig.getFunctionCall().getFunctionDefinition();
        Map<String, String> configNameLabelMap = funcDef.getConfiguration().stream().collect(Collectors.toMap(c->c.getName(), c->c.getLabel()));
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();

        // externalEntityDefIId should refer to any source entity
        var externalEntityDefId = configMap.get(EXTERNAL_ENTITY_DEF_ID);
        if(externalEntityDefId != null) {
		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
				!GraphValidationUtil.isValidSourceEntityReference(externalEntityDefId.toString(), validationContext),
				i18n("invalid_config_in_node", configNameLabelMap.get(EXTERNAL_ENTITY_DEF_ID), externalEntityDefId.toString(),
						node.getName(), graph.getName()), ErrorCode.E1082.getCode()).ifPresent(e -> errors.add(e));
        }

        // syncariEntityDefId should refer to active syncari entity
        var syncariEntityDefId = configMap.get(SYNCARI_ENTITY_DEF_ID);
        if(syncariEntityDefId != null) {
        	List<EntityDefinition> syncariEntities = schemaService.getEntities(syncariConnector.getId());
        	Optional<EntityDefinition> selectedSyncariEntity = syncariEntities.stream().filter(e -> syncariEntityDefId.equals(e.getId())).findFirst();
        	validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), selectedSyncariEntity.isEmpty(),
        			i18n("invalid_config_in_node", configNameLabelMap.get(SYNCARI_ENTITY_DEF_ID), syncariEntityDefId,
        					node.getName(), graph.getName()), ErrorCode.E1083.getCode()).ifPresent(e -> errors.add(e));
        	
        	// search field should refer to attribute of selected syncari entity
        	if(selectedSyncariEntity.isPresent()) {
        		var searchFieldId = configMap.get(SEARCH_FIELD_ID).toString();
        		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
        				!selectedSyncariEntity.get().getAttributes().stream()
        				.anyMatch(a -> a.getId().equals(searchFieldId)),
        				i18n("invalid_config_in_node", configNameLabelMap.get(SEARCH_FIELD_ID), searchFieldId,
        						node.getName(), graph.getName()), ErrorCode.E1084.getCode()).ifPresent(e -> errors.add(e));
        	}
        }


        // inputFieldId refers to attribute of connected sources or core entity
        var inputFieldId = configMap.get(INPUT_FIELD_ID);
        MappingNode coreNode = graph.getCoreNode();
		if (inputFieldId != null && coreNode != null) {
			boolean isCoreConnected = graph.pathToNodeMatches(node, n -> n.getId().equals(coreNode.getId()));
			if (isCoreConnected) {
				validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
						!GraphValidationUtil.isAttributeRefFromCoreEntity(inputFieldId.toString(), validationContext),
						i18n("invalid_config_in_node", configNameLabelMap.get(INPUT_FIELD_ID), inputFieldId,
								node.getName(), graph.getName()), ErrorCode.E1085.getCode()).ifPresent(e -> errors.add(e));
			} else {
				validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
						!GraphValidationUtil.isAttributeRefFromSourceEntity(inputFieldId.toString(), validationContext),
						i18n("invalid_config_in_node", configNameLabelMap.get(INPUT_FIELD_ID), inputFieldId,
								node.getName(), graph.getName()), ErrorCode.E1086.getCode()).ifPresent(e -> errors.add(e));
			}
		}
		return errors;
    }
}