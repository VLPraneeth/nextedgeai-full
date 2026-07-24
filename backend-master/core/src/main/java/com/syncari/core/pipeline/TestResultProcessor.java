package com.syncari.core.pipeline;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.syncari.connector.EntityData;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.datatype.ListType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.AttributeSourceNodeConfig;
import com.syncari.core.model.Edge;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.EntitySourceNodeConfig;
import com.syncari.core.model.FunctionResult;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.PipelineTest;
import com.syncari.core.model.StagedBatchRecord;
import com.syncari.core.model.TestResult;
import com.syncari.core.model.misc.PipelineTestStatus;
import com.syncari.core.model.misc.test.TestNodeResult;
import com.syncari.core.model.misc.test.TestNodeResult.Status;
import com.syncari.core.model.misc.test.TestNodeResultAttributeValue;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.repositories.customer.TestNodeResultRepo;
import com.syncari.core.repositories.customer.TestResultRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.PipelineTestService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.service.SimulationService;
import com.syncari.core.sync.CurrentBatch;
import com.syncari.core.sync.RecordsBySyncariId;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component
public class TestResultProcessor {

    @Autowired
    SchemaService schemaService;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    MappingGraphService graphService;

    @Autowired
    SimulationService simulationService;

    @Autowired
    PipelineTestService pipelineTestService;

    @Autowired
    TestResultRepo testResultRepo;

    @Autowired
    TestNodeResultRepo testNodeResultRepo;

    public void populateEntityGraphLiveTestResult(GraphContext graphContext, MappingGraph entityGraph, CurrentBatch currentBatch) {
        Optional<PipelineTest> test = pipelineTestService.getTestById(graphContext.getTestContext().getPipelineTestId());
        if (!test.isPresent()) {
            throw new NotFoundException(String.format("Test with id %s not found.", graphContext.getTestContext().getPipelineTestId()));
        }
        Iterator<RecordsBySyncariId> recordsBySyncariIdIterator = currentBatch.recordsBySyncariIdLiveTestIterator();
        int recordsProcessedForTest = 0;
        while (recordsBySyncariIdIterator.hasNext()) {
            RecordsBySyncariId records = recordsBySyncariIdIterator.next();
            for (StagedBatchRecord record : records.getRecords()) {
                TestResult testResult = new TestResult().setTest(test.get()).setStatus(PipelineTestStatus.queued)
                    .setPipelineTestId(graphContext.getTestContext().getPipelineTestId())
                    .setSyncariRecordId(record.getSyncariId())
                    .setConnectorName(connectorService.find(record.getEntityData().getConnectorId()).get().getName())
                    .setEntityId(record.getExternalEntityDefinitionId())
                    .setExternalRecordId(record.getExternalRecordId());
                populateEntityGraphTestResult(graphContext, testResult, graphContext.getGraph(), record);
                recordsProcessedForTest++;
            }
        }
        PipelineTest updatedTest = test.get();
        updatedTest.setRecordsProcessed(recordsProcessedForTest);
        pipelineTestService.update(updatedTest);
    }

    public void populateEntityGraphSimulationTestResult(GraphContext graphContext, TestResult runResult, MappingGraph entityGraph) {
        populateEntityGraphTestResult(graphContext, runResult, graphContext.getGraph(), null);
    }
    
    public void populateEntityGraphTestResult(GraphContext graphContext, TestResult runResult, MappingGraph entityGraph,
            StagedBatchRecord record) {

        MappingGraph graph = graphService.retrieve(entityGraph.getId()).get();
        if(graph.getNodes()!=null) {
        	graph.setNodes(graph.getNodes().stream().filter(node -> node.getType() != MappingNodeType.GROUP).collect(Collectors.toList()));
    	}
        PipelineTest test = runResult.getTest();
        var syncariId = (record != null) ? record.getSyncariId() : graphContext.getCurrentSyncariId();
        var dataSnapshot = graphContext.getTestContext().getDataSnapshot();
        Map<String, NodeData> nodeData = Map.of();
        if(graphContext.isSimulationMode() && StringUtils.isBlank(syncariId)) {
        	nodeData = dataSnapshot.values().stream().findFirst().orElse(Map.of());
        } else {
        	nodeData = dataSnapshot.get(syncariId);	
        }
        var nodeDataSnapShot = nodeData;

        // dfs over the graph starting with each source
        Set<String> visited = new HashSet<>();
        graph.getSources().forEach(source -> {
            // set the input first for the node based on test
            TestNodeResult srcResult = new TestNodeResult().setNodeId(source.getId()).setNodeName(source.getName());
            var nodeConfig = (EntitySourceNodeConfig) source.getConfiguration();
            var entity = schemaService.getEntity(nodeConfig.getEntityDefinition().getId());
            NodeData srcNodeData = nodeDataSnapShot.get(source.getId());
            
            Map<String, Object> values = new HashMap<>();
            if (graphContext.isTestMode()) {
                values = record.getEntityData().removeSystemFields().getValues();
            } else if (graphContext.isSimulationMode()) {
                var inputMaybe = test.getTestConfig().getInputs().stream().filter(in -> in.getNodeId().equals(source.getId())).findFirst();
                if (inputMaybe.isPresent()) {
                    values = inputMaybe.get().getFieldValues();
                }
            }
            if (!values.isEmpty()) {
                values.forEach((k, v) -> {
                    if (!entity.hasField(k)) return;
                    var attrib = entity.getFieldByName(k);
                    srcResult.addInput(k, new TestNodeResultAttributeValue(attrib.getApiName(),
                            attrib.getDisplayName(), attrib.getDataType().getName(), v));
                });
                // set output of source node
                if (srcNodeData == null || FilterFailedResult.isFailedFilter(srcNodeData.getOutput().getResult())) {
                    // if source is FilterFailed, it has no input - don't traverse further
                    srcResult.setStatus(TestNodeResult.Status.SKIPPED);
                    visited.add(source.getId());
                    runResult.addNodeResult(srcResult);
                } else {
                    EntityData srcNodeOp = (EntityData) srcNodeData.getOutput().getResult();
                    srcNodeOp.removeSystemFields().getValues().forEach((k, v) -> {
                        if (!entity.hasField(k)) return;
                        var attrib = entity.getFieldByName(k);
                        srcResult.addOutput(k, new TestNodeResultAttributeValue(attrib.getApiName(),
                                attrib.getDisplayName(), attrib.getDataType().getName(), v));
                    });
                    srcResult.setStatus(TestNodeResult.Status.COMPLETED);
                    // dfs starting from sourceNode and passing the entity
                    visited.add(source.getId());
                    runResult.addNodeResult(srcResult);
                    var outbound = graph.getOutboundEdges(source);
                    outbound.forEach(e -> entityGraphDFSTraversal(entity, graphContext.getTestContext(), syncariId,
                            e.getDestinationStage(), graph, runResult, visited, srcNodeOp, null));
                }
            }
        });

        // visit remaining nodes and mark them as skipped
        graph.getNodes().stream().filter(node -> !visited.contains(node.getId())).forEach(node -> {

            TestNodeResult nodeResult = new TestNodeResult().setNodeId(node.getId()).setNodeName(node.getName());
            nodeResult.setStatus(TestNodeResult.Status.SKIPPED);
            runResult.addNodeResult(nodeResult);
        });

        // compare output and set nodeResult status
        if (graphContext.isSimulationMode()) {
            compareSimulationExpectedOutput(test, runResult);
        }

        // set runResult as failed if any node fails
        //boolean isFailed = runResult.getNodeResults().stream().anyMatch(nodeResult -> PipelineTestStatus.failed.equals(nodeResult.getStatus()));
        boolean isFailed = runResult.getNodeResults().stream().anyMatch(nodeResult -> TestNodeResult.Status.FAILED.equals(nodeResult.getStatus()));
        runResult.setStatus(isFailed ? PipelineTestStatus.failed : PipelineTestStatus.success);
        saveTestResult(runResult);
    }

    private void entityGraphDFSTraversal(EntityDefinition entity, TestContext testContext, String syncariId, MappingNode node, MappingGraph graph,
                                         TestResult runResult, Set<String> visited, EntityData input, MappingNode prevNode){

        if(visited.contains(node.getId())){
            return;
        }
        TestNodeResult nodeResult = new TestNodeResult().setNodeId(node.getId()).setNodeName(node.getName());
        NodeData nodeData = testContext.getNodeData(syncariId, node.getId());
        if(nodeData == null){
            nodeResult.setStatus(TestNodeResult.Status.SKIPPED);
            visited.add(node.getId());
            runResult.addNodeResult(nodeResult);
            return;
        }

        if(prevNode != null && !prevNode.getId().equals(nodeData.getInputNodeId())){
            // pipeline evaluation has not taken this path
            return;
        }

        Optional<FunctionResult> inputFnResult = getInput(testContext, syncariId, graph, node, nodeData.getInputNodeId());
        // if there is no input for a node - mark it as skipped and stop that path
        if(inputFnResult.isEmpty()){
            nodeResult.setStatus(TestNodeResult.Status.SKIPPED);
            runResult.addNodeResult(nodeResult);
            visited.add(node.getId());
            return;
        }
        // capture input for a node
        //EntityData inputData = (EntityData) input.getResult();
        input.removeSystemFields().getValues().forEach((k, v) -> {
            if (!entity.hasField(k)) return;
            var attrib = entity.getFieldByName(k);
            nodeResult.addInput(attrib.getApiName(), new TestNodeResultAttributeValue(attrib.getApiName(),
                    attrib.getDisplayName(), attrib.getDataType().getName(), v));
        });

        // capture output
        FunctionResult nodeOutput = nodeData != null ? nodeData.getOutput() : null;
        if(nodeOutput == null){
            nodeResult.setStatus(TestNodeResult.Status.SKIPPED);
            visited.add(node.getId());
            runResult.addNodeResult(nodeResult);
            return;
        }

        Object resultValue = null;
        if(FilterFailedResult.isFailedFilter(nodeOutput.getResult())){
            FilterFailedResult failedResult = FilterFailedResult.normalizedFailedResult(nodeOutput.getResult());
            if(failedResult.hasInvalidResults() || node.isFalsePredicateNode()){
                // SYN-14136 A false predicate node emitting FilterFailedResult should also be marked skipped
                nodeResult.setStatus(TestNodeResult.Status.SKIPPED);
                visited.add(node.getId());
                runResult.addNodeResult(nodeResult);
                return;
            } else {
                if(failedResult.getValue() instanceof FunctionResult){
                    var failedNodeOutput = (FunctionResult) failedResult.getValue();
                    resultValue = failedNodeOutput.getResult();
                } else {
                    resultValue = failedResult.getValue();
                }
                if(FilterFailedResult.isFailedFilter(inputFnResult.get().getResult())){
                    // if input was also a failed filter then the node is not evaluated and skipped
                    // continue the path further
                    nodeResult.setStatus(TestNodeResult.Status.SKIPPED);
                } else {
                    // if input was not a failed filter but output is, it means its a FAILED node
                    nodeResult.setStatus(TestNodeResult.Status.COMPLETED);
                }
            }
        } else {
            resultValue = nodeOutput.getResult();
            if(nodeData.isFailed()) {
            	nodeResult.setStatus(TestNodeResult.Status.FAILED);
            } else {
            	nodeResult.setStatus(TestNodeResult.Status.COMPLETED);
            }
        }

        EntityDefinition extractedEntity = Optional.ofNullable(graphService.extractEntityFromNode(node)).orElse(entity);
        EntityData output = resultValue instanceof EntityData ? (EntityData) resultValue : input;

        if(node.getType().equals(MappingNodeType.ACTION)) {
            nodeResult.addOutput("actionResult", new TestNodeResultAttributeValue("actionResult",
                    "Action Result", StringType.NAME, String.format("Executed %s Action", node.getApiName())));
        } else {
            output.removeSystemFields().getValues().forEach((k, v) -> {
                if (!extractedEntity.hasField(k)) return;
                var attrib = extractedEntity.getFieldByName(k);
                nodeResult.addOutput(attrib.getApiName(), new TestNodeResultAttributeValue(attrib.getApiName(),
                        attrib.getDisplayName(), attrib.getDataType().getName(), v));
            });
        }

        visited.add(node.getId());
        runResult.addNodeResult(nodeResult);
        graph.getOutboundEdges(node).forEach(e -> entityGraphDFSTraversal(extractedEntity, testContext, syncariId,
                e.getDestinationStage(), graph, runResult, visited, output, node));
    }

    public void populateFieldGraphSimulationResult(GraphContext graphContext, TestResult runResult, MappingGraph attributeGraph){
        MappingGraph graph = graphService.retrieve(attributeGraph.getId()).get();
        if(graph.getNodes()!=null) {
        	graph.setNodes(graph.getNodes().stream().filter(node -> node.getType() != MappingNodeType.GROUP).collect(Collectors.toList()));
    	}
        PipelineTest test = runResult.getTest();
        var dataSnapshot = graphContext.getTestContext().getDataSnapshot();
        // if syncariId not found from graphContext -> pick the first key from dataSnapshpt
        var syncariId = StringUtils.isBlank(graphContext.getCurrentSyncariId())
                ? dataSnapshot.keySet().stream().findFirst().orElse("")
                : graphContext.getCurrentSyncariId();
        Map<String, NodeData> nodeData = dataSnapshot.getOrDefault(syncariId, Map.of());

        // dfs over the graph starting with each source
        Set<String> visited = new HashSet<>();
        graph.getSources().forEach(source -> {
            // set the input first for the node based on test
            TestNodeResult srcResult = new TestNodeResult().setNodeId(source.getId()).setNodeName(source.getName());
            var nodeConfig = (AttributeSourceNodeConfig) source.getConfiguration();
            var attribute = nodeConfig.getAttributeDefinition();
            var entity = schemaService.getEntity(attribute.getEntityId());
            NodeData srcNodeData = nodeData.get(source.getId());
            var inputMaybe = test.getTestConfig().getInputs().stream().filter(in -> in.getNodeId().equals(source.getId())).findFirst();
            if(inputMaybe.isPresent()){
                //set input based on test inputs and also populate same in src output
                inputMaybe.get().getFieldValues().forEach((k, v) -> {
                    if (!entity.hasField(k)) return;
                    var attrib = entity.getFieldByName(k);
                    srcResult.addInput(k, new TestNodeResultAttributeValue(attrib.getApiName(),
                            attrib.getDisplayName(), attrib.getDataType().getName(), v));

                    srcResult.addOutput(k, new TestNodeResultAttributeValue(attrib.getApiName(),
                            attrib.getDisplayName(), attrib.getDataType().getName(), v));
                });
                // if output is FilterFailedResult - set the status as skipped and don't set the output
                if (srcNodeData == null || FilterFailedResult.isFailedFilter(srcNodeData.getOutput())) {
                    srcResult.getOutputs().clear();
                    srcResult.setStatus(TestNodeResult.Status.SKIPPED);
                } else {
                    // superimpose field value output to node outputs
                    srcResult.addOutput(attribute.getApiName(), new TestNodeResultAttributeValue(attribute.getApiName(),
                            attribute.getDisplayName(), attribute.getDataType().getName(), srcNodeData.getOutput().getResult()));
                    srcResult.setStatus(TestNodeResult.Status.COMPLETED);
                }
                runResult.addNodeResult(srcResult);

                // dfs starting from sourceNode and passing the attribute
                visited.add(source.getId());
                var outbound = graph.getOutboundEdges(source);
                outbound.forEach(e -> fieldGraphDFSTraversal(attribute, graphContext.getTestContext(), syncariId,
                        e.getDestinationStage(), graph, runResult, visited, srcResult.getInputs(), null));
            }
        });

        // visit remaining nodes and mark them as skipped
        graph.getNodes().stream().filter(node -> !visited.contains(node.getId())).forEach(node -> {
            TestNodeResult nodeResult = new TestNodeResult().setNodeId(node.getId()).setNodeName(node.getName());
            //nodeResult.setStatus(PipelineTestRunState.skipped);
            nodeResult.setStatus(TestNodeResult.Status.SKIPPED);
            runResult.addNodeResult(nodeResult);
        });

        // compare output and set nodeResult status
        if (graphContext.isSimulationMode()) {
            compareSimulationExpectedOutput(test, runResult);
        }

        boolean isFailed = runResult.getNodeResults().stream().anyMatch(nodeResult -> TestNodeResult.Status.FAILED.equals(nodeResult.getStatus()));
        runResult.setStatus(isFailed ? PipelineTestStatus.failed : PipelineTestStatus.success);
        saveTestResult(runResult);
    }

    private void fieldGraphDFSTraversal(AttributeDefinition attrib, TestContext testContext, String syncariId, MappingNode node, MappingGraph graph,
                                        TestResult runResult, Set<String> visited, Map<String, TestNodeResultAttributeValue> inputs, MappingNode prevNode){
        if(visited.contains(node.getId())){
            return;
        }
        log.info("Compiling Node Result for node {} in graph {} for Test {}", node.getName(), graph.getName(), runResult.getTest().getName());
        TestNodeResult nodeResult = new TestNodeResult().setNodeId(node.getId()).setNodeName(node.getName());
        NodeData nodeData = testContext.getNodeData(syncariId, node.getId());
        if(nodeData == null){
            nodeResult.setStatus(TestNodeResult.Status.SKIPPED);
            visited.add(node.getId());
            runResult.addNodeResult(nodeResult);
            return;
        }
        if(prevNode != null && !prevNode.getId().equals(nodeData.getInputNodeId())){
            // pipeline evaluation has not taken this path
            return;
        }

        Optional<FunctionResult> inputFnResult = getInput(testContext, syncariId, graph, node, nodeData.getInputNodeId());
        // if there is no input for a node - mark it as skipped and stop that path
        if(inputFnResult.isEmpty()){
            nodeResult.setStatus(TestNodeResult.Status.SKIPPED);
            runResult.addNodeResult(nodeResult);
            visited.add(node.getId());
            return;
        }
        // capture input for a node
        nodeResult.setInputs(new HashMap<>(inputs));

        // capture output
        FunctionResult nodeOutput = testContext.getNodeOutput(syncariId, node.getId());
        if(nodeOutput == null){
            // no output from node - mark as skipped
            nodeResult.setStatus(TestNodeResult.Status.SKIPPED);
            visited.add(node.getId());
            runResult.addNodeResult(nodeResult);
            return;
        }
        Object resultValue = null;
        if(FilterFailedResult.isFailedFilter(nodeOutput.getResult())){
            FilterFailedResult failedResult = FilterFailedResult.normalizedFailedResult(nodeOutput.getResult());
            if(failedResult.hasInvalidResults()){
                nodeResult.setStatus(TestNodeResult.Status.SKIPPED);
                visited.add(node.getId());
                runResult.addNodeResult(nodeResult);
                return;
            } else {
                if(failedResult.getValue() instanceof FunctionResult){
                    var failedNodeOutput = (FunctionResult) failedResult.getValue();
                    resultValue = failedNodeOutput.getResult();
                } else {
                    resultValue = failedResult.getValue();
                }
                if(FilterFailedResult.isFailedFilter(inputFnResult.get().getResult())){
                    // if input was also a failed filter then the node is not evaluated and skipped
                    // continue the path further
                    nodeResult.setStatus(TestNodeResult.Status.SKIPPED);
                } else {
                    // if input was not a failed filter but output is, it means its a FAILED function mark it as COMPLETED
                    nodeResult.setStatus(TestNodeResult.Status.COMPLETED);
                }
            }
        } else {
            resultValue = nodeOutput.getResult();
            nodeResult.setStatus(TestNodeResult.Status.COMPLETED);
        }

        if(MappingNodeType.FUNCTION.equals(node.getType())) {
            nodeResult.setOutputs(new HashMap<>(inputs));
        }

        AttributeDefinition extractedAttribute = Optional.ofNullable(graphService.extractAttributeFromNode(node))
                .orElse(attrib);
        if(node.getType().equals(MappingNodeType.ACTION)) {
            nodeResult.addOutput("actionResult", new TestNodeResultAttributeValue("actionResult",
                    "Action Result", StringType.NAME, String.format("Executed %s Action", node.getApiName())));
        } else {
            nodeResult.addOutput(extractedAttribute.getApiName(), new TestNodeResultAttributeValue(extractedAttribute.getApiName(),
                    extractedAttribute.getDisplayName(), extractedAttribute.getDataType().getName(), resultValue));
        }

        visited.add(node.getId());
        runResult.addNodeResult(nodeResult);
        graph.getOutboundEdges(node).forEach(e -> fieldGraphDFSTraversal(extractedAttribute, testContext, syncariId,
                e.getDestinationStage(), graph, runResult, visited, nodeResult.getOutputs(), node));
    }

    private void compareSimulationExpectedOutput(PipelineTest test, TestResult runResult){
        test.getTestConfig().getExpectedOutputs().forEach(expOp -> {
            Optional<TestNodeResult> nodeResultMaybe = runResult.findNodeResult(expOp.getNodeId());
            nodeResultMaybe.ifPresentOrElse(nodeResult -> {
            	if(nodeResult.getStatus() != Status.SKIPPED) {
	            	nodeResult.setStatus(TestNodeResult.Status.SUCCESS);
	                expOp.getFieldValues().forEach((k, v) -> {
	                    if(nodeResult.getOutputs().containsKey(k)){
	                        var nodeResultAttribValue = nodeResult.getOutputs().get(k);
	                        var ip = DatatypeFactory.getDatatype(nodeResultAttribValue.getDataType()).convert(v);
	                        Object op = DatatypeFactory.getDatatype(nodeResultAttribValue.getDataType()).convert(nodeResultAttribValue.getValue());
							if ((ip != null && op != null) && !ip.equals(op)) {
								nodeResult.setStatus(TestNodeResult.Status.FAILED);
								nodeResult.setErrorMsg(i18n("test_result_input_output_mismatch", ip, op));
							}
	                    } else {
	                        nodeResult.setStatus(TestNodeResult.Status.FAILED);
	                        nodeResult.setErrorMsg(i18n("test_result_exp_output_not_found", k));
	                    }
	                });
            	}
            },() -> {
                TestNodeResult nodeResult = new TestNodeResult().setNodeId(expOp.getNodeId());
                nodeResult.setStatus(TestNodeResult.Status.FAILED);
                nodeResult.setErrorMsg(i18n("test_result_output_node_not_found"));
                runResult.addNodeResult(nodeResult);
            });
        });
    }

    private Optional<FunctionResult> getInput(TestContext testContext, String syncariId, MappingGraph graph, MappingNode node, String inputNodeId){
        List<Edge> inboundEdges = graph.getInboundEdges(node);
        // If inputNodeId is provided, consider it as input else pick first from successfulInputs
        if(!StringUtils.isBlank(inputNodeId)){
            return Optional.ofNullable(testContext.getNodeOutput(syncariId, inputNodeId));
        }
        List<FunctionResult> inputs = inboundEdges.stream()
                .map(e -> testContext.getNodeOutput(syncariId, e.getSourceStage().getId()))
                .filter(op -> op != null) // filter nulls since null is generated by some of the skipped nodes
                .collect(Collectors.toList());

        List<FunctionResult> successfulInputs = inputs.stream()
                .filter(r -> !FilterFailedResult.isFailedFilter(r.getResult()))
                .collect(Collectors.toList());
        List<FunctionResult> failedInputs = inputs.stream().filter(i->FilterFailedResult.isFailedFilter(i.getResult()))
                .collect(Collectors.toList());

        if(successfulInputs.isEmpty()){
            //all results are failures. pick the first failure
            return failedInputs.stream().findFirst();
        } else {
            List<FunctionResult> nonnullResults = successfulInputs.stream().filter(r -> r.typedValue() != null)
                    .collect(Collectors.toList());
            Optional<FunctionResult> maybeResult = nonnullResults.isEmpty()
                    ? successfulInputs.stream().findFirst()
                    : nonnullResults.stream().findFirst();
            return maybeResult;
        }
    }

    /**
     * Save TestResult with nodeResults always stored externally.
     * This prevents the 16MB document size limit and provides better scalability.
     */
    @Transactional("customerTransactionManager")
    private void saveTestResult(TestResult testResult) {
        List<TestNodeResult> nodeResults = testResult.getWorkingNodeResults();

        // Always use external storage - set nodeResults to null
        // This signals to TestResultLoader that data is external
        testResult.setNodeResults(null);

        // Save TestResult without nodeResults
        testResultRepo.save(testResult);

        // Save each nodeResult as individual document
        saveNodeResultsExternally(testResult.getId(), nodeResults);

        log.debug("Saved TestResult {} with {} externalized node results",
                  testResult.getId(), nodeResults != null ? nodeResults.size() : 0);
    }

    /**
     * Save node results as individual external documents.
     */
    private void saveNodeResultsExternally(String testResultId, List<TestNodeResult> nodeResults) {
        if (nodeResults == null || nodeResults.isEmpty()) {
            return;
        }

        // Set the testResultId and sequence for each node result
        for (int i = 0; i < nodeResults.size(); i++) {
            TestNodeResult nodeResult = nodeResults.get(i);
            nodeResult.setTestResultId(testResultId);
            nodeResult.setSequence(i);
        }

        // Batch insert for efficiency
        testNodeResultRepo.saveAll(nodeResults);

        log.debug("Saved {} individual node result documents for TestResult {}",
                  nodeResults.size(), testResultId);
    }
}
