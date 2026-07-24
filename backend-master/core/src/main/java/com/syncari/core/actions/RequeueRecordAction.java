package com.syncari.core.actions;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.syncari.core.model.util.ErrorCode;
import org.springframework.stereotype.Component;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.validation.ValidationContext;

@Component(ActionConstants.REQUEUE_RECORD)
public class RequeueRecordAction extends DefaultAction {
    static final Set<String> FILTER_FUNCTIONS = Set.of("isTrue","isFalse","filter", "predicate");
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
        
        boolean onSourceSide =
        //not connected from core node
                !graph.pathToNodeMatches(node, n->n.getConfiguration().getNodeType() == MappingNodeType.CORE_ENTITY)
        &&
                        //and connected from a source
        graph.pathToNodeMatches(node, n->n.getConfiguration().getNodeType() == MappingNodeType.ENTITY_SOURCE);
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), !onSourceSide,
                i18n("requeue_action_validation_not_on_source_side",  node.getName(), graph.getName()), ErrorCode.E1070.getCode()).ifPresent(e -> errors.add(e));

        graph.getPreviousNodes(node).forEach(prev->{
            validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), !FILTER_FUNCTIONS.contains(prev.getApiName()),
                    i18n("requeue_action_validation_not_connected_to_filter",  node.getName(), graph.getName()), ErrorCode.E1071.getCode()).ifPresent(e -> errors.add(e));

        });
        
        return errors;
    }


}
