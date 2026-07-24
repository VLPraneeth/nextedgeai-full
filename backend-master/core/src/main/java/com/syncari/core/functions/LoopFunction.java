package com.syncari.core.functions;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.sharable.SharableFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.core.pipeline.expression.VariableExpression;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.quickstart.v2.dependency.DefaultPredicateDependencyGenerator;
import com.syncari.core.quickstart.v2.dependency.DependencyUtil;
import com.syncari.core.quickstart.v2.dependency.ExpressionDependencyResolver;
import com.syncari.core.quickstart.v2.dependency.ExpressionDependencyVisitor;
import com.syncari.core.validation.ExpressionValidatorVisitor;
import com.syncari.core.validation.PredicateValidator;
import com.syncari.core.validation.ValidationContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component(FunctionConstants.LOOP)
public class LoopFunction extends DefaultFunction implements PredicateValidator {

    @Autowired
    DefaultPredicateDependencyGenerator defaultPredicateDependencyGenerator;

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
        FunctionCall functionCall = functionNodeConfig.getFunctionCall();

        String option = functionCall.getConfig().getOrDefault("option", "").toString();
        String startIndex = functionCall.getConfig().get("startIndex") != null ? functionCall.getConfig().get("startIndex").toString() : "";
        String endIndex = functionCall.getConfig().get("endIndex") != null ? functionCall.getConfig().get("endIndex").toString() : "";
        String variable = functionCall.getConfig().get("variable") != null ? functionCall.getConfig().get("variable").toString() : "";
        String maxLoop = functionCall.getConfig().get("maxLoop") != null ? functionCall.getConfig().get("maxLoop").toString() : "";

        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), StringUtils.isBlank(option)
                || !(option.equalsIgnoreCase("index") || option.equalsIgnoreCase("variable") || option.equalsIgnoreCase("condition")),
                i18n("invalid-loop-option", option,
                node.getName(), graph.getName()), ErrorCode.E1198.getCode()).ifPresent(e -> errors.add(e));

        if (option.equalsIgnoreCase("index")) {
            validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), StringUtils.isBlank(startIndex)
                            || StringUtils.isBlank(endIndex),
                    i18n("invalid-loop-index-option",
                            node.getName(), graph.getName()), ErrorCode.E1198.getCode()).ifPresent(e -> errors.add(e));
        } else if (option.equalsIgnoreCase("variable")) {
            validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), StringUtils.isBlank(variable),
                    i18n("invalid-loop-variable-option",
                            node.getName(), graph.getName()), ErrorCode.E1198.getCode()).ifPresent(e -> errors.add(e));
        } else if (option.equalsIgnoreCase("condition")) {
            validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), StringUtils.isBlank(maxLoop),
                    i18n("invalid-max-loop",
                            node.getName(), graph.getName()), ErrorCode.E1198.getCode()).ifPresent(e -> errors.add(e));
            try {
                var predicate = (Map<String, Object>) functionCall.getConfig().get(PREDICATE);
                Expression filterExpression = new PredicateParser().fromMap(predicate);
                ExpressionValidatorVisitor visitor = new ExpressionValidatorVisitor(this, validationContext);
                filterExpression.accept(visitor);
            } catch (SyncariValidationException e) {
                log.error("validation error occured ", e);
                if(e.getMessage().contains("Unknown operator ")) {
                    errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(i18n("filter_operator_required",
                            validationContext.getNode().getName(), validationContext.getGraph().getName())));
                } else {
                    errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(e.getMessage()));
                }
            }
        }

        List<Edge> edges = graph.getOutboundEdges(node);
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), edges.size() == 0 || edges.size() > 2 ||
                        !edges.stream().anyMatch(e -> e.getDestinationStage().getApiName().equals(FunctionConstants.FOR_EACH)),
                i18n("invalid-edges-from-loop",
                        node.getName(), graph.getName()), ErrorCode.E1198.getCode()).ifPresent(e -> errors.add(e));
        validateLoop(graph, node, errors);

        // if the loop function does not have an outgoing edge (other than foreach), confirm that last node(s) in the loop are action nodes
        if (edges.size() == 1) {
            validateTerminalLoop(graph, node, errors);
        }
        return errors;
    }


    private void validateTerminalLoop(MappingGraph graph, MappingNode node, List<ValidationError> errors) {

            // find a foreach node
        Optional<Edge> backEdge = graph.getInboundEdges(node).stream().filter(e -> graph.isBackEdge(e)).findFirst();
        backEdge.ifPresent(edge -> {
            var functionNodes = graph.getInboundEdges(edge.getSourceStage()).stream().map(Edge::getSourceStage).filter(n -> n.getType() != MappingNodeType.ACTION);
            functionNodes.forEach(f -> {
                validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                        !f.getApiName().equals(FunctionConstants.LOOP)
                        , i18n("invalid-nodes-terminal-loop",
                                node.getName(), graph.getName()), ErrorCode.E1198.getCode()).ifPresent(e -> errors.add(e));

                if (f.getApiName().equals(FunctionConstants.LOOP)) {
                    validateTerminalLoop(graph,f, errors);
                }
            });
        });

    }

    private void validateLoop(MappingGraph graph, MappingNode node, List<ValidationError> errors) {

        // there should be incoming
        List<MappingNode> incomingEndLoops = graph.getInboundEdges(node).stream().map(Edge::getSourceStage)
                .filter(n -> n.getApiName().equals(FunctionConstants.END_LOOP)).collect(Collectors.toList());

        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), incomingEndLoops.size() != 1,
                i18n("invalid-edges-from-endloop",
                        node.getName(), graph.getName()), ErrorCode.E1198.getCode()).ifPresent(e -> errors.add(e));

        if (!incomingEndLoops.isEmpty()) {

            MappingNode endLoopNode = incomingEndLoops.get(0);
            MappingNode forEachNode = graph.getOutboundEdges(node).stream().map(Edge::getDestinationStage)
                    .filter(n -> n.getApiName().equals(FunctionConstants.FOR_EACH)).findFirst().get();
            Set<MappingNodeType> stopNodes = Set.of(MappingNodeType.CORE_ATTRIBUTE, MappingNodeType.CORE_ENTITY, MappingNodeType.ENTITY_SINK, MappingNodeType.ATTRIBUTE_SINK);
            List<List<MappingNode>> paths = graph.findAllPaths(graph, forEachNode, n -> n.getId().equals(endLoopNode.getId()) || stopNodes.contains(n.getType()));

            paths.forEach(p -> {
                if (!p.isEmpty()) {
                    // each loop path should end in the same end loop node.
                    validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), !p.get(p.size() - 1).getId().equals(endLoopNode.getId()),
                            i18n("invalid-loop-path",
                                    node.getName(), graph.getName()), ErrorCode.E1199.getCode()).ifPresent(e -> errors.add(e));
                }
            });
        }
    }

    @Override
    public void validateVarExpression(VariableExpression expression, ValidationContext validationContext) {
        String variableName = expression.getVariableName();
        validateVariableName(variableName, validationContext);
    }

    private void validateVariableName(String variableName, ValidationContext validationContext){
        Matcher tempVarMatcher = TEMP_VARIABLE_PATTERN.matcher(variableName);
        String tempVar = tempVarMatcher.find() ? tempVarMatcher.group(1) : null;
        validateCondition(tempVar == null, i18n("invalid-loop-temp-variable", validationContext.getNode().getName(), validationContext.getGraph().getName()));

    }

    @Override
    public void extract(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode node = context.getCurrentNode();
        super.extract(context);
        SharableFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();
        if (null != configMap.get(PREDICATE) && !(configMap.get(PREDICATE) instanceof String)){
            var predicate = (Map<String, Object>) configMap.get(PREDICATE);
            Expression filterExpression = new PredicateParser().fromMap(predicate);
            ExpressionDependencyVisitor visitor = new ExpressionDependencyVisitor(defaultPredicateDependencyGenerator, context);
            filterExpression.accept(visitor);
        }
    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
        SharableNode sharableNode = context.getCurrentNode().copy();
        SharableFunctionNodeConfig functionNodeConfig = sharableNode.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();
        if (null != configMap.get(PREDICATE) && !(configMap.get(PREDICATE) instanceof String)){
            Map<String, Object> predicate = (Map<String, Object>) configMap.get(PREDICATE);

            ExpressionDependencyResolver resolver = new ExpressionDependencyResolver(context);
            var resolvedPredicate = resolver.fromMap(predicate);
            configMap.put(PREDICATE, resolvedPredicate);
        }
        functionNodeConfig.getFunctionCall().setConfig(configMap);
        functionNodeConfig.getFunctionCall().setParams(resolveParams(context, functionNodeConfig));
        sharableNode.setConfiguration(functionNodeConfig);

        return sharableGraphTransformer.toMappingNode(sharableNode, context.getCurrentPipeline());
    }
}
