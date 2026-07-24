package com.syncari.core.functions;


import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Edge;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.validation.ValidationContext;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.syncari.utils.I18n.i18n;

@Component(FunctionConstants.CASE_BRANCH)
public class CaseBranchFunction extends DefaultFunction {

    private static final String CASE_VALUE = "value";

    public static MappingNode getCaseNode(MappingNode caseBranchNode, MappingGraph graph) {
        Optional<Edge> edgeInfo = graph.getEdges().stream().filter(e -> e.getDestinationStage().getId().equals(caseBranchNode.getId())).findAny();
        if (edgeInfo.isEmpty())
            throw new SyncariValidationException("Case Node is not connected for case branch node %s for graph %s", caseBranchNode.getName(), graph.getName());
        Optional<MappingNode> nodeInfo = graph.getNodes().stream().filter(e -> e.getId().equals(edgeInfo.get().getSourceStage().getId())).findFirst();
        if (nodeInfo.isEmpty() || !nodeInfo.get().getApiName().equals(CaseFunction.CASE))
            throw new SyncariValidationException("Case Node is not connected for case branch node %s for graph %s", caseBranchNode.getName(), graph.getName());
        return nodeInfo.get();
    }


    public static String getConfiguredCaseValue(Map<String, Object> configMap) {
        return (String) configMap.get(CASE_VALUE);
    }

    @Override
    public void validate(ValidationContext validationContext) {
        var errors = validateWithoutException(validationContext);
        if(errors != null && !errors.isEmpty()) {
            throw new SyncariValidationException(errors.get(0).getMessage());
        }
    }

    @Override
    public List<ValidationError> validateWithoutException(ValidationContext validationContext) {
        List<ValidationError> errors = new ArrayList<>(super.validateWithoutException(validationContext));
        MappingNode node = validationContext.getNode();
        try {
            MappingNode caseNode = getCaseNode(node, validationContext.getGraph());
            Set<String> cases = CaseFunction.getCaseListFromConfig(caseNode.getConfiguration().getConfigMap());
            if (!cases.contains(getConfiguredCaseValue(node.getConfiguration().getConfigMap()))){
                errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(i18n("invalid_case_value_in_case_branch",
                        getConfiguredCaseValue(node.getConfiguration().getConfigMap()), node.getName(), validationContext.getGraph().getName())));
            }
        } catch (Exception e) {
            errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(e.getMessage()));
        }
        return errors;
    }

}
