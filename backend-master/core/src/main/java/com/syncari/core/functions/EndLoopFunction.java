package com.syncari.core.functions;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.validation.ValidationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component(FunctionConstants.END_LOOP)
public class EndLoopFunction extends DefaultFunction {

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

        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), graph.getOutboundEdges(node).size() != 1,
                i18n("invalid-outgoing-endloop",
                        node.getName(), graph.getName()), ErrorCode.E1198.getCode()).ifPresent(e -> errors.add(e));

        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                graph.getOutboundEdges(node).stream().filter(e -> !e.getDestinationStage().getApiName().equals(FunctionConstants.LOOP)).findFirst().isPresent(),
                i18n("invalid-outgoing-endloop",
                        node.getName(), graph.getName()), ErrorCode.E1198.getCode()).ifPresent(e -> errors.add(e));
        return errors;
    }
}
