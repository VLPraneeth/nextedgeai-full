package com.syncari.core.pipeline.jtwig;

import com.syncari.core.actions.Actions;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.model.*;
import com.syncari.core.pipeline.*;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.FunctionExpression;
import com.syncari.core.service.FeatureService;
import com.syncari.core.service.PipelineNodeAuditService;
import com.syncari.core.token.TokenHelper;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jtwig.JtwigModel;
import org.jtwig.JtwigTemplate;
import org.jtwig.environment.Environment;
import org.jtwig.resource.reference.ResourceReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Predicate;

import static com.syncari.core.model.util.MappingNodeType.ATTRIBUTE_SOURCE;
import static com.syncari.core.pipeline.jtwig.functions.SideChannelFunction.extractResult;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@Service("defaultJTwigPipelineEvaluator")
public class JTwigPipelineEvaluator implements PipelineEvaluator {
    protected Environment environment;
    protected Actions actions;
    protected  TokenHelper tokenHelper;
    protected FeatureService featureService;
    protected PipelineNodeAuditService pipelineNodeAuditService;

    @Autowired
    public JTwigPipelineEvaluator(Environment environment, TokenHelper tokenHelper, Actions actions, PipelineNodeAuditService pipelineNodeAuditService, FeatureService featureService){
        this.environment = environment;
        this.tokenHelper = tokenHelper;
        this.actions = actions;
        this.pipelineNodeAuditService = pipelineNodeAuditService;
        this.featureService = featureService;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public FunctionResult evaluate(Expression expression, Map<String, Object> context, Datatype outputType) {
        JTwigTemplateGenerationVisitor visitor = new JTwigTemplateGenerationVisitor(tokenHelper);
        FunctionCallContextVisitor contextVisitor = new FunctionCallContextVisitor(context);
        //Generate the JTwig template by traversing stage pipeline using a generation visitor

        expression.accept(visitor);
        FunctionSideChannel.remove();
        //Update context with context in functionCall
        expression.accept(contextVisitor);
        try {
            ResourceReference resource = new ResourceReference(
                    ResourceReference.STRING,
                    visitor.getGeneratedBody()

            );
            JtwigTemplate jtwigTemplate = new JtwigTemplate(environment, resource);
            JtwigModel model = JtwigModel.newModel(context);
            Object result = jtwigTemplate.render(model);
            return new FunctionResult(extractResult(result), outputType);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            contextVisitor.clear();
            FunctionSideChannel.remove();
        }
    }

    public void evaluateV1(MappingNode target, MappingGraph graph, GraphContext context, Predicate<MappingNode> stop, Set<String> visited) {

        if (stop.test(target)) {
            log.debug("Stopping evaluation because it reached a matching node {}", target.getId());
            if(target.getType() == ATTRIBUTE_SOURCE) {
                AttributeSourceNodeConfig attributeSourceNodeConfig = target.getTypedConfiguration();
                context.put("current_source_attribute_id", attributeSourceNodeConfig.getAttributeDefinition().getId());
            }
            return;
        }
        List<Edge> inboundEdges = graph.getInboundEdges(target);

        //DFS going back from from given node , terminate when stop predicate matches
        //Result of every step is stored in context, along with the corresponding node
        //Result key is output_<nodeId>
        //Skip accidental dangling edges
        inboundEdges.stream().filter(edge -> edge.getDestinationStage()!=null && edge.getSourceStage()!=null && !visited.contains(edge.getSourceStage().getId())).forEach(edge -> {
            if (graph.isBackEdge(edge)) {
                // this should never happen for graph running in V1
                throw new RuntimeException("Back edge detected in graph " + graph.getName() + " from " + edge.getSourceStage().getName() + " to " + edge.getDestinationStage().getName());
            }
            try {
                evaluateV1(edge.getSourceStage(), graph, context, stop, visited);
            }catch(TerminateExecutionPathException e){
                context.addError(context.getCurrentSyncariId(),
                        new NodeError().setError(e.getMessage()).setErrorDetails(ExceptionUtils.getStackTrace(e))
                                .setNodeId(target.getId()).setNodeName(target.getApiName()));

                log.debug("Execution path terminated for graph {} while evaluating", graph.getName(),edge.getSourceStage().getName());
            }
        });
        context.setCurrentNode(target);
        JTwigNodeVisitor jTwigNodeVisitor = new JTwigNodeVisitor(context, this,actions, pipelineNodeAuditService, featureService);
        //visitor mutates context
        target.getConfiguration().accept(jTwigNodeVisitor, target);
        visited.add(target.getId());
    }

    public void evaluateLoop(MappingNode loopNode, MappingGraph graph, GraphContext context, Set<String> visited) {

        while (context.continueLoop()) {
            // get input to
            List<Edge> inboundEdges = graph.getInboundEdges(loopNode);
            // save the output of the current node and pass set it before each node.
            inboundEdges.stream().filter(edge -> edge.getDestinationStage()!=null && edge.getSourceStage()!=null && graph.isBackEdge(edge)).forEach(edge -> {
                evaluateV2(edge.getSourceStage(), graph, context, node -> node.getId().equals(loopNode.getId()), new HashSet<>());
            });

            context.setCurrentNode(loopNode);
            JTwigNodeVisitor jTwigNodeVisitor = new JTwigNodeVisitor(context, this,actions, pipelineNodeAuditService, featureService);
            //visitor mutates context
            loopNode.getConfiguration().accept(jTwigNodeVisitor, loopNode);
        }
        context.endLoop(loopNode);
    }


    public void evaluateV2(MappingNode target, MappingGraph graph, GraphContext context, Predicate<MappingNode> stop, Set<String> visited) {

        if (stop.test(target)) {
            log.debug("Stopping evaluation because it reached a matching node {}", target.getId());
            if(target.getType() == ATTRIBUTE_SOURCE) {
                AttributeSourceNodeConfig attributeSourceNodeConfig = target.getTypedConfiguration();
                context.put("current_source_attribute_id", attributeSourceNodeConfig.getAttributeDefinition().getId());
            }
            return;
        }

        // retrieve inbound edges, ignore back edge to the loop
        List<Edge> inboundEdges = graph.getInboundEdges(target, true);

        //DFS going back from from given node , terminate when stop predicate matches
        //Result of every step is stored in context, along with the corresponding node
        //Result key is output_<nodeId>
        //Skip accidental dangling edges
        inboundEdges.stream().filter(edge -> edge.getDestinationStage()!=null && edge.getSourceStage()!=null
                && !visited.contains(edge.getSourceStage().getId())).forEach(edge -> {
            try {
                evaluateV2(edge.getSourceStage(), graph, context, stop, visited);
            }catch(TerminateExecutionPathException e){
                context.addError(context.getCurrentSyncariId(),
                        new NodeError().setError(e.getMessage()).setErrorDetails(ExceptionUtils.getStackTrace(e))
                                .setNodeId(target.getId()).setNodeName(target.getApiName()));

                log.debug("Execution path terminated for graph {} while evaluating", graph.getName(),edge.getSourceStage().getName());
            }
        });
        context.setCurrentNode(target);
        JTwigNodeVisitor jTwigNodeVisitor = new JTwigNodeVisitor(context, this,actions, pipelineNodeAuditService, featureService);
        //visitor mutates context
        target.getConfiguration().accept(jTwigNodeVisitor, target);
        visited.add(target.getId());

        if (context.isInLoop()) {
            evaluateLoop(target, graph, context, visited);
        }
    }

    public void evaluate(MappingNode target, MappingGraph graph, GraphContext context, Predicate<MappingNode> stop, Set<String> visited) {

       if (isSimpleLoops(context)) {
           evaluateV2(target, graph, context, stop, visited);
       } else {
           evaluateV1(target, graph, context, stop, visited);
       }
    }

    public FunctionResult evaluate(FunctionCall call, GraphContext context) {
        JTwigTemplateGenerationVisitor visitor = new JTwigTemplateGenerationVisitor(tokenHelper);
        FunctionExpression expression = new FunctionExpression(call);
        expression.accept(visitor);
        String nodeName = context.getCurrentNode() == null ? "N/A" : context.getCurrentNode().getName();
        String nodeId = context.getCurrentNode() == null ? "N/A" : context.getCurrentNode().getId();
        String generatedBody = visitor.getGeneratedBody();
        ResourceReference resource = new ResourceReference(
                ResourceReference.STRING,
                generatedBody

        );
        try {
            JtwigTemplate jtwigTemplate = new JtwigTemplate(environment, resource);
            JtwigModel model = JtwigModel.newModel(context);
            Object result = jtwigTemplate.render(model);
            Object extractedResult = extractResult(result);
            if(extractedResult!=null && extractedResult instanceof FunctionResult){
                FunctionResult functionResult = FunctionResult.class.cast(extractedResult);
                return new FunctionResult(functionResult.getResult(), call.getFunctionDefinition().getOutputType(),functionResult.getLookupResult(),functionResult.getLookupCount());
            }
            return new FunctionResult(extractedResult, call.getFunctionDefinition().getOutputType());

        }catch(TerminateExecutionPathException e){
            log.debug("Node: {} Terminated. Function call: {}",nodeName, generatedBody);
            throw e;
        } catch (Exception e) {
            log.error(String.format("Error executing function node %s. Error: %s", nodeName, e.getMessage()), e);
            throw new RuntimeException(i18n("method_invocation_failed", nodeName, ExceptionUtils.getRootCause(e).getMessage()), e);
        } finally {
            FunctionSideChannel.remove();
        }
    }

    private boolean isSimpleLoops(GraphContext context) {
        return context.isSimpleLoopOn();
    }
}

class FunctionCallContextVisitor extends SimpleExpressionVisitor {

    private Map<String, Object> context;

    public FunctionCallContextVisitor(Map<String, Object> context) {
        this.context = context;
    }

    @Override
    public void visit(FunctionExpression exp) {

        context.put("functionConfig", exp.getFunctionCall().getConfig());
        context.put("functionCall", exp.getFunctionCall());
        if(!context.containsKey("context")) {
            context.put("context", null);
        }
    }

    public void clear() {
        context.remove("functionCall");
        context.remove("functionConfig");
        context.remove("context");
    }
}