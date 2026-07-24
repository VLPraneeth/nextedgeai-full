package com.syncari.core.functions;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Edge;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.validation.ValidationContext;

import java.util.ArrayList;
import java.util.List;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

public class BooleanFunction extends DefaultFunction {

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

        /* check boolean functions for
        - exactly one inbound edge
        - exactly one outbound edge
        - inbound edge must connect filter node
        */
        List<Edge> inboundEdges = graph.getInboundEdges(node);
        List<Edge> outboundEdges = graph.getOutboundEdges(node);
		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), inboundEdges.size() > 1,
				i18n("func_with_multiple_input", node.getName(), graph.getName()), ErrorCode.E1087.getCode()).ifPresent(e -> errors.add(e));

		if(!inboundEdges.isEmpty()) {
	        var inboundEdge = inboundEdges.get(0);
			validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
					!isFilterNode(inboundEdge.getSourceStage()),
					i18n("func_not_connected_to_filter", node.getName(), graph.getName()),ErrorCode.E1088.getCode()).ifPresent(e -> errors.add(e));
		}
		return errors;
    }

    private boolean isFilterNode(MappingNode node){
        return "filter".equalsIgnoreCase(node.getApiName());
    }
}
