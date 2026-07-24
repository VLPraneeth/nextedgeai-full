package com.syncari.core.validation;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

import java.util.ArrayList;
import java.util.List;

import com.syncari.core.model.util.ErrorCode;
import org.springframework.stereotype.Component;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.AttributeSourceNodeConfig;
import com.syncari.core.model.Edge;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.util.ValidationError;

@Component
public class SourceAttributeNodeValidator implements ValidationService {

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
        AttributeSourceNodeConfig srcNodeConfig = node.getTypedConfiguration();
        AttributeDefinition attrib = srcNodeConfig.getAttributeDefinition();
		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), attrib == null,
				i18n("invalid_source_node", node.getName(), graph.getName()), ErrorCode.E1166.getCode()).ifPresent(e -> errors.add(e));
		if (attrib != null) {
			validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
					attrib.isArchived() || attrib.isDeleted(),
					i18n("deleted_source_node_field", node.getName(), graph.getName()), ErrorCode.E1167.getCode()).ifPresent(e -> errors.add(e));

			validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
					!attrib.isActive(), i18n("inactive_source_node_field", node.getName(), graph.getName()), ErrorCode.E1167.getCode()).ifPresent(e -> errors.add(e));
		}
        List<Edge> inboundEdges = graph.getInboundEdges(node);
		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), !inboundEdges.isEmpty(),
				i18n("error_source_node_with_inbound_edge", node.getName(), graph.getName()), ErrorCode.E1168.getCode())
						.ifPresent(e -> errors.add(e));
        
        return errors;
    }
}
