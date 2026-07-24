package com.syncari.core.validation;

import com.syncari.connector.Constants;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Edge;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.EntitySinkNodeConfig;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.service.ConnectorService;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Component
public class SinkEntityNodeValidator implements ValidationService {

	@Autowired
	ConnectorService connService;

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

        // validate if sink nodes with valid entity definition
        EntitySinkNodeConfig sinkNodeConfig = node.getTypedConfiguration();
        EntityDefinition entity = sinkNodeConfig.getEntityDefinition();
		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), entity == null,
				i18n("invalid_sink_node", node.getName(), graph.getName()), ErrorCode.E1162.getCode()).ifPresent(e -> errors.add(e));
		if (entity != null) {
			validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
					entity.isArchived() || entity.isDeleted(),
					i18n("deleted_sink_node_entity", node.getName(), graph.getName()), ErrorCode.E1163.getCode()).ifPresent(e -> errors.add(e));

			// validate sink entity node is not a syncari entity
			connService.find(entity.getConnectorId()).ifPresent(connector -> {
				validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
						connector.isSyncariConnector(),
						i18n("invalid_sink_node", node.getName(), graph.getName()), ErrorCode.E1164.getCode())
						.ifPresent(e -> errors.add(e));
			});

			// validate duplicate sink nodes
			var sinks = graph.getSinks();
			var duplicateSink = sinks.filter(sink -> {
				EntitySinkNodeConfig nodeConfig = sink.getTypedConfiguration();
				return !sink.getId().equals(node.getId()) && entity.getId().equals(nodeConfig.getEntityDefinition().getId());
			}).findFirst();
			
			duplicateSink.ifPresent(dup -> {
				errors.add(ValidationError.scopedError(node.getScope(), node.getId())
						.withMessage(i18n("duplicate_sink_node", node.getName(), dup.getName(), graph.getName())));
			});

			// Unfortunately this is a very custom validation and we should refactor in a future to introduce custom validations for Synk
			if ("lead".equalsIgnoreCase(entity.getApiName()) && sinkNodeConfig.getDestinationParams().containsKey(Constants.MARKETO_LOOK_UP_FIELD)) {
				String lookUpFieldId = (String) sinkNodeConfig.getDestinationParams().get(Constants.MARKETO_LOOK_UP_FIELD);
				AttributeDefinition lookUpFieldDefinition = entity.getIdToAttributes().get(lookUpFieldId);
				if (!"string".equalsIgnoreCase(lookUpFieldDefinition.getDataType().getName()) &&
						!"id".equalsIgnoreCase(lookUpFieldDefinition.getDataType().getName()) &&
						!"integer".equalsIgnoreCase(lookUpFieldDefinition.getDataType().getName())
				) {
					errors.add(ValidationError.scopedError(node.getScope(), node.getId())
							.withMessage(i18n("unsupported_maketo_lookup_field_type", lookUpFieldDefinition.getDataType().getName())));
				}

			}

			String batchSize = (String) sinkNodeConfig.getDestinationParams().get(Constants.PIPELINE_BATCH_SIZE);
			if (StringUtils.isNotBlank(batchSize)) {
				try {
					Integer.parseInt(batchSize.trim());
				} catch (NumberFormatException exception) {
					errors.add(ValidationError.scopedError(node.getScope(), node.getId())
							.withMessage(i18n("invalid_batch_size_value")));
				}
			}
		}


        /*// sink node should either be terminal or must connect to action node
        List<Edge> outboundEdge = graph.getOutboundEdges(node);
        outboundEdge.forEach(edge -> {
        	if (edge.getDestinationStage() != null) {
				validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
						!MappingNodeType.ACTION.equals(edge.getDestinationStage().getType()),
						i18n("sink_node_output_must_connect_to_action", node.getName(), graph.getName()), ErrorCode.E1165.getCode())
								.ifPresent(e -> errors.add(e));
        	}
        });*/
        
        return errors;
    }
}
