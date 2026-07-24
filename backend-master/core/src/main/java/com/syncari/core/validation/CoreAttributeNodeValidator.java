package com.syncari.core.validation;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.syncari.core.model.AttributeSourceNodeConfig;
import com.syncari.core.model.DatAuthorityStrategy;
import com.syncari.core.model.DataAuthority;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.service.SchemaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.CoreAttributeNodeConfig;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.util.ValidationError;

@Component
public class CoreAttributeNodeValidator implements ValidationService {

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
    	List<ValidationError> errors = new ArrayList<>();

        MappingNode node = validationContext.getNode();
        MappingGraph graph = validationContext.getGraph();
        
        if (graph == null || node == null)
			return errors;

        // validate if source nodes are terminal nodes with no inbound edges
        CoreAttributeNodeConfig coreNodeConfig = node.getTypedConfiguration();
        AttributeDefinition attrib = coreNodeConfig.getAttributeDefinition();
		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), attrib == null,
				i18n("invalid_core_node", node.getName(), graph.getName()), ErrorCode.E1155.getCode()).ifPresent(e -> errors.add(e));
		if(attrib == null) return errors; // do not perform further validation if attrib is null to avoid any potential NPE

		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
				attrib.isArchived() || attrib.isDeleted(),
				i18n("deleted_core_node_field", node.getName(), graph.getName()), ErrorCode.E1156.getCode()).ifPresent(e -> errors.add(e));

		if(attrib.isReference()) {
			Optional<EntityDefinition> refEntity = schemaService.getSyncariEntityByName(attrib.getReferenceTo());
			validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), refEntity.isEmpty(),
					i18n("invalid_entity_reference_for_ref_field", attrib.getReferenceTo(), attrib.getDisplayName()),
					ErrorCode.E1008.getCode()).ifPresent(ee -> errors.add(ee));
		}

        // validate if the strategy is selected synapse and if its connected
		DataAuthority dataAuthority = coreNodeConfig.getDataAuthority();
		if(DatAuthorityStrategy.SELECTED_CONNECTOR.equals(dataAuthority.getDatAuthorityStrategy())
				&& dataAuthority.getDataAuthorityConfiguration().containsKey("connectorId")
				&& dataAuthority.getDataAuthorityConfiguration().get("connectorId") != null){
			String connectorId = dataAuthority.getDataAuthorityConfiguration().get("connectorId").toString();
			List<String> validConnectorIds = new ArrayList<>();
			graph.getConnectedSources().forEach(s -> {
				AttributeDefinition sourceAttrib = ((AttributeSourceNodeConfig)s.getConfiguration()).getAttributeDefinition();
				EntityDefinition sourceEntity = validationContext.getSourceEntityMap().get(sourceAttrib.getEntityId());
				if(sourceEntity != null){
					validConnectorIds.add(sourceEntity.getConnectorId());
				}
			});

			validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
					!validConnectorIds.contains(connectorId),
					i18n("invalid_synapse_for_data_authority_core_node", node.getName(), graph.getName()),
					ErrorCode.E1192.getCode()).ifPresent(e -> errors.add(e));

		}

        return errors;
    }
}
