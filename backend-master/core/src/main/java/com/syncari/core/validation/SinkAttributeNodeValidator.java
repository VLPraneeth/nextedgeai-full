package com.syncari.core.validation;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

import java.util.ArrayList;
import java.util.List;

import com.syncari.core.model.util.ErrorCode;
import org.springframework.stereotype.Component;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.AttributeSinkNodeConfig;
import com.syncari.core.model.Edge;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.ValidationError;

@Component
public class SinkAttributeNodeValidator implements ValidationService {

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
        AttributeSinkNodeConfig sinkNodeConfig = node.getTypedConfiguration();
        AttributeDefinition attrib = sinkNodeConfig.getAttributeDefinition();
		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), attrib == null,
				i18n("invalid_sink_node", node.getName(), graph.getName()), ErrorCode.E1159.getCode()).ifPresent(e -> errors.add(e));
        if(attrib != null) {
			validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
					attrib.isArchived() || attrib.isDeleted(),
					i18n("deleted_sink_node_field", node.getName(), graph.getName()), ErrorCode.E1160.getCode()).ifPresent(e -> errors.add(e));

			validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
					!attrib.isActive(), i18n("inactive_sink_node_field", node.getName(), graph.getName()), ErrorCode.E1167.getCode()).ifPresent(e -> errors.add(e));
        	
			// validate duplicate sink nodes
			var sinks = graph.getSinks();
			var duplicateSink = sinks.filter(sink -> {
				AttributeSinkNodeConfig nodeConfig = sink.getTypedConfiguration();
				String attribId = null;
				if(nodeConfig != null && nodeConfig.getAttributeDefinition() != null) {
					attribId = nodeConfig.getAttributeDefinition().getId();
				}
				return !sink.getId().equals(node.getId()) && attrib.getId().equals(attribId);
			}).findFirst();
			duplicateSink.ifPresent(dup -> {
				errors.add(ValidationError.scopedError(node.getScope(), node.getId())
						.withMessage(i18n("duplicate_sink_node", node.getName(), dup.getName(), graph.getName())));
			});
        }
        
        return errors;
    }
}
