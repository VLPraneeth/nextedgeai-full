package com.syncari.core.pipeline.jtwig;

import com.syncari.connector.EntityData;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.actions.ActionConstants;
import com.syncari.core.actions.Actions;
import com.syncari.core.datatype.ListType;
import com.syncari.core.datatype.ObjectType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.event.store.model.NodeAudit;
import com.syncari.core.model.*;
import com.syncari.core.model.util.Scope;
import com.syncari.core.pipeline.FilterFailedResult;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.PipelineEvaluator;
import com.syncari.core.repositories.customer.ActionDefinitionRepo;
import com.syncari.core.service.*;
import com.syncari.core.sync.CurrentBatch;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.utils.Pair;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class JTwigNodeVisitorTest extends AbstractSyncariTest {

    @Autowired
    FunctionService functionService;

    @Autowired
    ActionService actionService;

    @Autowired
    ActionDefinitionRepo actionDefinitionRepo;

    @Autowired
    PipelineNodeAuditService pipelineNodeAuditService;

    @Autowired
    Actions actions;

    @Autowired
    FeatureService featureService;

    @Test
    public void executeFunction_AttachRecordFunctionsNotEvaluatedForSimulation(){

        FunctionDefinition attachRecordFn = functionService.findByNameAndScope("attachRecord", Scope.ENTITY).get();
        executeFunction(attachRecordFn, true);
    }

    @Test
    public void executeFunction_AdvancedAttachRecordFunctionsNotEvaluatedForSimulation(){

        FunctionDefinition attachRecordFn = functionService.findByNameAndScope("advancedAttachRecord", Scope.ENTITY).get();
        executeFunction(attachRecordFn, true);
    }

    @Test
    public void executeFunction_AdvancedAttachRecordFunctionsEvaluated() {

        FunctionDefinition attachRecordFn = functionService.findByNameAndScope("advancedAttachRecord", Scope.ENTITY).get();
        final GraphContext graphContext = executeFunction(attachRecordFn, false);
        final Optional<NodeAuditBatch> nodeAuditsInBuffer = pipelineNodeAuditService
                .getNodeAuditsInBuffer(graphContext.getCurrentBatch().getCurrentBatchId());
        assertTrue(nodeAuditsInBuffer.isPresent());
        assertEquals(1, nodeAuditsInBuffer.get().getAuditLogs().size());
    }

    @Test
    public void executeAction_BatchableSinkSideAction() throws InvocationTargetException, IllegalAccessException {
        ActionDefinition addToProgramAction = actionService.getAction(ActionConstants.ADD_TO_MARKETO_PROGRAM).get();
        executeAction(addToProgramAction, false);
    }

    @Test
    public void executeAction_NodeErrorLogs() throws InvocationTargetException, IllegalAccessException {
        ActionDefinition fnDef = actionService.getAction(ActionConstants.ADD_TO_MARKETO_PROGRAM).get();
        boolean isSimulation = false;

        Map<String, Object> configMap = new HashMap<>();
        configMap.put("synapseId", "mkto");
        configMap.put("programId", "");
        configMap.put("leadId", "");
        GenericActionConfig actionConfig = new GenericActionConfig();
        actionConfig.setConfigMap(configMap);
        MappingNode actionNode = new MappingNode().setName("actionNode").setScope(Scope.ENTITY).setApiName(ActionConstants.ADD_TO_MARKETO_PROGRAM)
                .setConfiguration(actionConfig);
        actionNode.setId(ObjectId.get().toHexString());

        List<Pair<FunctionResult, MappingNode>> evaluatedResultList = List.of(Pair.of(new FunctionResult(FilterFailedResult.VALUE, ObjectType.VALUE), actionNode),
                Pair.of(new FunctionResult("evaluatedValue", StringType.VALUE), actionNode));

        FunctionResult evaluatedResult = new FunctionResult(evaluatedResultList, ObjectType.VALUE);

        List<Edge> edgesToGraph = mock(List.class);
        MappingGraph graph = mock(MappingGraph.class);
        when(graph.getTargetId()).thenReturn("targetId");
        when(graph.isNodeLoggingOn()).thenReturn(true);
        when(graph.getScope()).thenReturn(Scope.ENTITY);
        when(graph.getInboundEdges(any())).thenReturn(edgesToGraph);
        Stream<Edge> mockStream = mock(Stream.class);
        when(edgesToGraph.stream()).thenReturn(mockStream);
        Stream<Object> mockStream1 = mock(Stream.class);
        Stream<Object> mockStream2 = mock(Stream.class);
        when(mockStream.flatMap(any())).thenReturn((mockStream1));
        when(mockStream1.collect(any())).thenReturn(evaluatedResultList);
        graph.setEdges(edgesToGraph);
        GraphContext graphContext = getContext().setSimulationMode(isSimulation)
                .setGraph(graph);


        PipelineEvaluator mockEvaluator = mock(PipelineEvaluator.class);
        when(mockEvaluator.evaluate(any(), any())).thenReturn(evaluatedResult);
        final Actions mockActions = spy(actions);
        doThrow(new RuntimeException("Cannot add to list")).when(mockActions).addToMarketoProgram(any(), any());
        JTwigNodeVisitor jTwigNodeVisitor = new JTwigNodeVisitor(graphContext, mockEvaluator, mockActions, pipelineNodeAuditService, featureService);

        jTwigNodeVisitor.visit(actionConfig, actionNode);
        assertTrue(graphContext.containsKey("output_" + actionNode.getId()));
        assertTrue(graphContext.containsKey(String.format("action_output_%s_result", actionNode.getId())));
        assertTrue(graphContext.containsKey(String.format("action_output_%s_status", actionNode.getId())));
        assertTrue(graphContext.containsKey(String.format("Action Result From %s", actionNode.getName())));
        final Optional<NodeAuditBatch> nodeAuditsInBuffer = pipelineNodeAuditService.getNodeAuditsInBuffer(graphContext.getCurrentBatch().getCurrentBatchId());
        assertTrue(nodeAuditsInBuffer.isPresent());
        assertEquals(1, nodeAuditsInBuffer.get().getAuditLogs().size());
        final NodeAudit log = nodeAuditsInBuffer.get().getAuditLogs().poll();
        assertEquals("RuntimeException: Cannot add to list", log.getError());

    }
    @Test
    public void executeAction_ReturnsOnlySuccessfulInputsback() throws InvocationTargetException, IllegalAccessException {
        ActionDefinition fnDef = actionService.getAction(ActionConstants.ADD_TO_MARKETO_PROGRAM).get();
        boolean isSimulation = false;

        Map<String, Object> configMap = new HashMap<>();
        configMap.put("synapseId", "mkto");
        configMap.put("programId", "");
        configMap.put("leadId", "");
        GenericActionConfig actionConfig = new GenericActionConfig();
        actionConfig.setConfigMap(configMap);
        MappingNode actionNode = new MappingNode().setName("actionNode").setScope(Scope.ENTITY).setApiName(ActionConstants.ADD_TO_MARKETO_PROGRAM)
                .setConfiguration(actionConfig);
        actionNode.setId(ObjectId.get().toHexString());

        List<Pair<FunctionResult, MappingNode>> evaluatedResultList = List.of(Pair.of(new FunctionResult(FilterFailedResult.VALUE, ObjectType.VALUE),actionNode),
                Pair.of(new FunctionResult("evaluatedValue", StringType.VALUE), actionNode));

        FunctionResult evaluatedResult = new FunctionResult(evaluatedResultList, ObjectType.VALUE);

        List<Edge> edgesToGraph = mock(List.class);
        MappingGraph graph = mock(MappingGraph.class);
        when(graph.getTargetId()).thenReturn("targetId");
        when(graph.isNodeLoggingOn()).thenReturn(true);
        when(graph.getScope()).thenReturn(Scope.ENTITY);
        when(graph.getInboundEdges(any())).thenReturn(edgesToGraph);
        Stream<Edge> mockStream = mock(Stream.class);
        when(edgesToGraph.stream()).thenReturn(mockStream);
        Stream<Object> mockStream1 = mock(Stream.class);
        Stream<Object> mockStream2 = mock(Stream.class);
        when(mockStream.flatMap(any())).thenReturn((mockStream1));
        when(mockStream1.collect(any())).thenReturn(evaluatedResultList);
        graph.setEdges(edgesToGraph);
        GraphContext graphContext = getContext().setSimulationMode(isSimulation)
                .setGraph(graph);




        PipelineEvaluator mockEvaluator = mock(PipelineEvaluator.class);
        when(mockEvaluator.evaluate(any(), any())).thenReturn(evaluatedResult);
        JTwigNodeVisitor jTwigNodeVisitor = new JTwigNodeVisitor(graphContext, mockEvaluator, actions, pipelineNodeAuditService, featureService);

        jTwigNodeVisitor.visit(actionConfig, actionNode);
        assertTrue(graphContext.containsKey("output_" + actionNode.getId()));
        assertTrue(graphContext.containsKey(String.format("action_output_%s_result", actionNode.getId())));
        assertTrue(graphContext.containsKey(String.format("action_output_%s_status", actionNode.getId())));
        assertTrue(graphContext.containsKey(String.format("Action Result From %s", actionNode.getName())));
        Pair output = (Pair) graphContext.get("output_" + actionNode.getId());
        assertNotNull(((FunctionResult) output.x).getResult());
        assertEquals("evaluatedValue", ((FunctionResult) output.x).getResult());
        final Optional<NodeAuditBatch> nodeAuditsInBuffer = pipelineNodeAuditService.getNodeAuditsInBuffer(graphContext.getCurrentBatch().getCurrentBatchId());
        assertTrue(nodeAuditsInBuffer.isPresent());
        assertEquals(1, nodeAuditsInBuffer.get().getAuditLogs().size());

    }

    @Test
    public void executeAction_WithListAsInput() throws InvocationTargetException, IllegalAccessException {
        ActionDefinition fnDef = actionService.getAction(ActionConstants.SEND_SLACK_MESSAGE).get();
        boolean isSimulation = false;

        Map<String, Object> configMap = new HashMap<>();
        configMap.put("synapseId", "slack");
        configMap.put("channel", "testing");
        configMap.put("text", "test");
        GenericActionConfig actionConfig = new GenericActionConfig();
        actionConfig.setConfigMap(configMap);
        MappingNode actionNode = new MappingNode().setName("actionNode").setScope(Scope.ENTITY).setApiName(ActionConstants.SEND_SLACK_MESSAGE)
                .setConfiguration(actionConfig);
        actionNode.setId(ObjectId.get().toHexString());

        FunctionResult inputListFR = new FunctionResult(List.of(FilterFailedResult.VALUE,FilterFailedResult.VALUE), ListType.VALUE);
        List<Pair<FunctionResult, MappingNode>> evaluatedResultList = List.of(Pair.of(inputListFR,actionNode));

        FunctionResult evaluatedResult = new FunctionResult(evaluatedResultList, ObjectType.VALUE);

        List<Edge> edgesToGraph = mock(List.class);
        MappingGraph graph = mock(MappingGraph.class);
        when(graph.getTargetId()).thenReturn("targetId");
        when(graph.getScope()).thenReturn(Scope.ENTITY);
        when(graph.getInboundEdges(any())).thenReturn(edgesToGraph);
        Stream<Edge> mockStream = mock(Stream.class);
        when(edgesToGraph.stream()).thenReturn(mockStream);
        Stream<Object> mockStream1 = mock(Stream.class);
        Stream<Object> mockStream2 = mock(Stream.class);
        when(mockStream.flatMap(any())).thenReturn((mockStream1));
        when(mockStream1.collect(any())).thenReturn(evaluatedResultList);
        graph.setEdges(edgesToGraph);
        GraphContext graphContext = getContext().setSimulationMode(isSimulation)
                .setGraph(graph);
        PipelineEvaluator mockEvaluator = mock(PipelineEvaluator.class);
        when(mockEvaluator.evaluate(any(), any())).thenReturn(evaluatedResult);
        JTwigNodeVisitor jTwigNodeVisitor = new JTwigNodeVisitor(graphContext, mockEvaluator, actions, pipelineNodeAuditService, featureService);

        jTwigNodeVisitor.visit(actionConfig, actionNode);
        assertTrue(graphContext.containsKey("output_" + actionNode.getId()));
        Pair output = (Pair) graphContext.get("output_" + actionNode.getId());
        assertNotNull(((FunctionResult) output.x).getResult());
        assertEquals(inputListFR.getResult(), ((FunctionResult) output.x).getResult());
        assertFalse(graphContext.containsKey("previous"));
    }

    private static GraphContext getContext() {
        final MappingGraph mappingGraph = new MappingGraph();
        mappingGraph.setId(ObjectId.get().toHexString());
        mappingGraph.setScope(Scope.ENTITY);
        mappingGraph.setSettings(new PipelineSettings(false, true, true, false, false, "", "",""));
        return new GraphContext().setGraph(mappingGraph)
                .setSyncariEntity(SchemaHelper.createEntityDef("test", "test"))
                .setCurrentBatch(new CurrentBatch(null)
                        .setCurrentBatchId(UUID.randomUUID().toString()));
    }

    @Test
    public void executeAction_ReturnsFailedResultIfNoSuccessfulInputs() throws InvocationTargetException, IllegalAccessException {
        ActionDefinition fnDef = actionService.getAction(ActionConstants.ADD_TO_MARKETO_PROGRAM).get();
        boolean isSimulation = false;

        Map<String, Object> configMap = new HashMap<>();
        configMap.put("synapseId", "mkto");
        configMap.put("programId", "");
        configMap.put("leadId", "");
        GenericActionConfig actionConfig = new GenericActionConfig();
        actionConfig.setConfigMap(configMap);
        MappingNode actionNode = new MappingNode().setName("actionNode").setScope(Scope.ENTITY).setApiName(ActionConstants.ADD_TO_MARKETO_PROGRAM)
                .setConfiguration(actionConfig);
        actionNode.setId(ObjectId.get().toHexString());

        List<Pair<FunctionResult, MappingNode>> evaluatedResultList = List.of(Pair.of(new FunctionResult(FilterFailedResult.VALUE, ObjectType.VALUE),actionNode),
                Pair.of(new FunctionResult(FilterFailedResult.VALUE, ObjectType.VALUE), actionNode));

        FunctionResult evaluatedResult = new FunctionResult(evaluatedResultList, ObjectType.VALUE);

        List<Edge> edgesToGraph = mock(List.class);
        MappingGraph graph = mock(MappingGraph.class);
        when(graph.getTargetId()).thenReturn("targetId");
        when(graph.getScope()).thenReturn(Scope.ENTITY);
        when(graph.getInboundEdges(any())).thenReturn(edgesToGraph);
        Stream<Edge> mockStream = mock(Stream.class);
        when(edgesToGraph.stream()).thenReturn(mockStream);
        Stream<Object> mockStream1 = mock(Stream.class);
        Stream<Object> mockStream2 = mock(Stream.class);
        when(mockStream.flatMap(any())).thenReturn((mockStream1));
        when(mockStream1.collect(any())).thenReturn(evaluatedResultList);
        graph.setEdges(edgesToGraph);
        GraphContext graphContext = getContext().setSimulationMode(isSimulation)
                .setGraph(graph);




        PipelineEvaluator mockEvaluator = mock(PipelineEvaluator.class);
        when(mockEvaluator.evaluate(any(), any())).thenReturn(evaluatedResult);
        JTwigNodeVisitor jTwigNodeVisitor = new JTwigNodeVisitor(graphContext, mockEvaluator, actions, pipelineNodeAuditService, featureService);

        jTwigNodeVisitor.visit(actionConfig, actionNode);
        assertTrue(graphContext.containsKey("output_"+actionNode.getId()));
        Pair output = ( Pair) graphContext.get("output_"+actionNode.getId());
        assertNotNull(((FunctionResult)output.x).getResult());
        assertEquals(FilterFailedResult.VALUE,((FunctionResult)output.x).getResult());
    }

    private GraphContext executeFunction(FunctionDefinition fnDef, boolean isSimulation) {


        FunctionCall functionCall = new FunctionCall().setFunctionDefinition(fnDef)
                .setParams(List.of(ParameterValue.string("output_nodeId", "input")));

        MappingNode fnNode = new MappingNode().setScope(Scope.ENTITY).setApiName("functionNode").setName("Function Node")
                .setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(functionCall));
        GraphContext graphContext = getContext().setSimulationMode(isSimulation);
        FunctionResult inputResult = new FunctionResult("inputValue", StringType.VALUE);
        MappingNode inputNode = new MappingNode().setScope(Scope.ENTITY).setApiName("srcNode").setName("Source Node")
                .setConfiguration(new EntitySourceNodeConfig()).setMappingGraphId("graphId");
        var input = Pair.of(inputResult, inputNode);

        FunctionResult evaluatedResult = new FunctionResult("evaluatedValue", StringType.VALUE);
        PipelineEvaluator mockEvaluator = mock(PipelineEvaluator.class);
        when(mockEvaluator.evaluate(any(), any())).thenReturn(evaluatedResult);
        graphContext.put("record",new EntityData().setId("1234"));
        JTwigNodeVisitor jTwigNodeVisitor = new JTwigNodeVisitor(graphContext, mockEvaluator, null, pipelineNodeAuditService, featureService);
        FunctionResult outputResult = jTwigNodeVisitor.executeFunction(functionCall, input, fnNode);

        if(isSimulation) {
            verify(mockEvaluator, never()).evaluate(any(), any());
            assertNotEquals(outputResult.getResult(), evaluatedResult.getResult());
            assertEquals(inputResult.getResult(), outputResult.getResult());
        } else {
            verify(mockEvaluator, times(1)).evaluate(any(), any());
            assertEquals(outputResult.getResult(), evaluatedResult.getResult());
        }
        if (JTwigNodeVisitor.WHITE_LIST_ACTION_FUNCTION_FORCACHING.contains(fnDef.getName())){
            assertNotNull(graphContext.getNodeResultsCache("output_"+fnNode.getId()+"12340", null));
        }else{
            assertNull(graphContext.getNodeResultsCache("output_"+fnNode.getId() + "12340", null));
        }
        return graphContext;
    }

    private void executeAction(ActionDefinition fnDef, boolean isSimulation) throws InvocationTargetException, IllegalAccessException {

        GraphContext graphContext = getContext().setSimulationMode(isSimulation)
                .setGraph(new MappingGraph().setTargetId("targetId").setScope(Scope.ENTITY));
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("synapseId", "mkto");
        configMap.put("programId", "");
        configMap.put("leadId", "");
        GenericActionConfig actionConfig = new GenericActionConfig();
        actionConfig.setConfigMap(configMap);

        MappingNode actionNode = new MappingNode().setName("actionNode").setScope(Scope.ENTITY).setApiName(ActionConstants.ADD_TO_MARKETO_PROGRAM)
                .setConfiguration(actionConfig);
        actionNode.setId(ObjectId.get().toHexString());

        FunctionResult evaluatedResult = new FunctionResult("evaluatedValue", StringType.VALUE);
        PipelineEvaluator mockEvaluator = mock(PipelineEvaluator.class);
        when(mockEvaluator.evaluate(any(), any())).thenReturn(evaluatedResult);
        JTwigNodeVisitor jTwigNodeVisitor = new JTwigNodeVisitor(graphContext, mockEvaluator, actions, pipelineNodeAuditService, featureService);
        //jTwigNodeVisitor.setActionMap(actionMap);

        jTwigNodeVisitor.visit(actionConfig, actionNode);
        assertTrue(graphContext.containsKey("output_"+actionNode.getId()));
        assertFalse(graphContext.getNodeResultsCache("output_"+actionNode.getId(),false));

    }
}
