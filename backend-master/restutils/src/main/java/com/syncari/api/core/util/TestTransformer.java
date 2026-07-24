package com.syncari.api.core.util;

import com.syncari.api.rest.controllers.data.test.*;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.*;
import com.syncari.core.model.misc.test.*;
import com.syncari.core.model.util.Scope;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.service.TagService;
import com.syncari.core.service.TestResultLoader;
import com.syncari.core.service.UserService;
import com.syncari.restutils.transformers.GraphTransformer;
import com.syncari.utils.DateUtil;
import com.syncari.utils.KeyValue;

import org.parboiled.common.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class TestTransformer {

    @Autowired
    UserService userService;

    @Autowired
    TagService tagService;

    @Autowired
    MappingGraphService graphService;

    @Autowired
    SchemaService schemaService;

    @Autowired
    GraphTransformer graphTransformer;

    @Autowired
    TestResultLoader testResultLoader;

    public List<PipelineTestDTO> toTestDTOs(List<PipelineTest> tests) {
        return tests.stream().map(test -> createPipelineTestDTO(test)).collect(Collectors.toList());
    }

    public PipelineTestDTO toPipelineTestDTO(PipelineTest test){
        PipelineTestDTO testDTO = createPipelineTestDTO(test);
        testDTO.setTestData(getSimulationTestData(test));
        return testDTO;
    }

    private PipelineTestDTO createPipelineTestDTO(PipelineTest test){
        User user = userService.getUserById(test.getUserId());
        PipelineTestDTO dto = new PipelineTestDTO()
                .setId(test.getId()).setDisplayName(test.getName())
                .setDescription(test.getDescription()).setOwnerEmail(user.getEmail())
                .setOwnerFirstName(user.getFirstName()).setOwnerLastName(user.getLastName())
                .setTags(tagService.getTagNames(Taggable.test, test.getId()));

        return dto;
    }

    private PipelineTestData getSimulationTestData(PipelineTest test){
        PipelineTestData testData = new PipelineTestData();
        testData.setInput(getNodeData(test.getTestConfig().getInputs()));
        testData.setExpectedResult(getNodeData(test.getTestConfig().getExpectedOutputs()));
        return testData;
    }

    private List<PipelineTestNodeData> getNodeData(List<SimulationNodeInput> inputs){
        List<PipelineTestNodeData> nodeInputs = new ArrayList<>();
        inputs.stream().forEach(input -> {
            input.getFieldValues().forEach((k, v) -> {
                PipelineTestNodeData node = new PipelineTestNodeData();
                node.setNodeId(input.getNodeId());
                node.setNodeName(input.getNodeName());
                node.setValue(v);
                node.setApiName(k);
                // TODO: check if this can be removed, if displayName and dataType is really necessary for UI
                graphService.findNode(input.getNodeId()).ifPresent(n -> {
                    EntityDefinition entity = getEntity(n);
                    // certain nodes like a filter node, will not have entitydefinition.
                    if (entity == null) return;
                    AttributeDefinition attr = entity.getFieldByName(k);
                    if(attr != null){
                        node.setDataType(attr.getDataType().getName());
                        node.setIsMultiValueField(attr.isMultiValueField());
                        node.setDisplayName(attr.getDisplayName());
                        var convertedValue = attr.convert(v);
                        node.setValue(convertedValue == null ? "" : convertedValue);
                    }
                });
                nodeInputs.add(node);
            });
        });

        return nodeInputs;
    }

    private EntityDefinition getEntity(MappingNode node, Map<String, EntityDefinition> cachedEntities){
        String entityId = "";
        if(Scope.ENTITY.equals(node.getScope())) {
            if (!node.getConfiguration().getConfigMap().containsKey("entityDefinition")) return null;
            entityId = node.getConfiguration().getConfigMap().get("entityDefinition").toString();
        } else {
            if (!node.getConfiguration().getConfigMap().containsKey("attributeDefinition")) return null;
            String attribId = node.getConfiguration().getConfigMap().get("attributeDefinition").toString();
            var attribute = schemaService.getAttribute(attribId);
            entityId = attribute.getEntityId();
        }
        if(!cachedEntities.containsKey(entityId)){
            cachedEntities.put(entityId, schemaService.getEntity(entityId));

        }
        return cachedEntities.get(entityId);
    }

    private EntityDefinition getEntity(MappingNode node){
        return getEntity(node, new HashMap<>());
    }

    public PipelineTest toPipelineTest(PipelineTestDTO testDTO, Scope scope, String targetId){
        User user = userService.getUserByEmail(testDTO.getOwnerEmail())
                .orElseThrow(() -> new NotFoundException(User.class, "email", testDTO.getOwnerEmail()));
        PipelineTest test = new PipelineTest();
        test.setName(testDTO.getDisplayName());
        test.setDescription(testDTO.getDescription());
        test.setScope(scope);
        test.setTestMode(PipelineTest.TestMode.SIMULATION);
        test.setUserId(user.getId());
        test.setTargetId(targetId);
        test.setTestConfig(createTestConfig(testDTO.getTestData()));
        test.setId(testDTO.getId());

        var tags = testDTO.getTags().stream()
                .map(t -> new Tag(t, true, Taggable.test, testDTO.getId()))
                .collect(Collectors.toList());
        test.setTags(tags);
        return test;
    }

    public List<KeyValue> toFieldPicklist(List<AttributeDefinition> attributes){
        List<KeyValue> fieldPicklist = new ArrayList<>();
        attributes.forEach(attr -> {
            KeyValue field = new KeyValue();
            field.put("datatype", attr.getDataType().getName());
            field.put("label", attr.getDisplayName());
            field.put("isMultiValueField", attr.isMultiValueField());
            field.put("value", attr.getApiName());
            field.put("id", attr.getId());

            fieldPicklist.add(field);
        });
        return fieldPicklist;
    }

    public List<TestRunDTO> toSimulationTestRunsDTO(List<SimulationRun> runs) {
        List<TestRunDTO> runsDTO = new ArrayList<>();
        runs.forEach(run -> {
            List<String> testNames = new ArrayList<>();
            run.getSimulationResults().forEach(result -> {
                testNames.add(result.getTest().getName());
            });
            runsDTO.add(new TestRunDTO().setId(run.getId())
                    .setRunName(run.getName())
                    .setTestNames(testNames));
        });
        return runsDTO;
    }

    public TestRunDTO toSimulationTestRunDTO(SimulationRun run) {
        List<String> testNames = new ArrayList<>();
        run.getSimulationResults().forEach(result -> {
            testNames.add(result.getTest().getName());
        });
        return new TestRunDTO().setId(run.getId())
                .setRunName(run.getName())
                .setTestNames(testNames);
    }

    public PipelineTestRunResultsDTO toSimulationRunResultsDTO(SimulationRun simulationRun, List<TestResult> results) {
        PipelineTestRunResultsDTO resultsDTO = new PipelineTestRunResultsDTO();
        resultsDTO.setStatus(simulationRun.getStatus().name());
        List<PipelineTestRunResultDTO> resultDTOs = new ArrayList<>();
        SimulationMappingGraph graph = simulationRun.getGraph();

        // Load node results for all test results
        testResultLoader.loadNodeResults(results);

        results.stream().forEach(result -> {
            PipelineTest test = result.getTest();
            Set<String> outputNodeIds = test.getTestConfig().getExpectedOutputs().stream()
                    .map(eop -> eop.getNodeId()).collect(Collectors.toSet());
            List<PipelineTestNodeData> outputNodeResults = new ArrayList<>();
            List<TestNodeResult> nodeResults = result.getNodeResults();
            List<PipelineTestNodeRunResultDTO> nodeResultDTOs = new ArrayList<>();

            nodeResults.forEach(nodeResult -> {
                Optional<MappingNode> node = graph.getNode(nodeResult.getNodeId());
                PipelineTestData testData = new PipelineTestData();

                List<PipelineTestNodeData> inputs = new ArrayList<>();
                Map<String, TestNodeResultAttributeValue> nodeInputs = nodeResult.getInputs();
                nodeInputs.forEach((k, v) -> {
                    var value = v.getValue();
                    inputs.add(new PipelineTestNodeData().setNodeId(nodeResult.getNodeId())
                            .setNodeName(nodeResult.getNodeName())
                            .setValue(value == null ? "" : value.toString())
                            .setApiName(v.getApiName())
                            .setDataType(v.getDataType())
                            .setDisplayName(v.getDisplayName())
                    );
                });
                testData.setInput(inputs);

                List<PipelineTestNodeData> actualResult = new ArrayList<>();
                Map<String, TestNodeResultAttributeValue> nodeOutputs = nodeResult.getOutputs();
                Optional<SimulationNodeInput> expectedOpForNode = test.getTestConfig().findTestExpectedOutputsForNode(nodeResult.getNodeId());
                Map<String, Object> expectedOpValues = expectedOpForNode.isPresent() ? expectedOpForNode.get().getFieldValues() : Map.of();
                nodeOutputs.forEach((k, v) -> {

                    var value = v.getValue();
                    actualResult.add(new PipelineTestNodeData().setNodeId(nodeResult.getNodeId())
                            .setNodeName(nodeResult.getNodeName())
                            .setValue(value == null ? "" : value.toString())
                            .setApiName(v.getApiName())
                            .setDataType(v.getDataType())
                            .setDisplayName(v.getDisplayName())
                            .setFailed(expectedOpValues.containsKey(k) && !Objects.equals(expectedOpValues.get(k), value))
                    );
                });
                testData.setActualResult(actualResult);


                nodeResultDTOs.add(
                    new PipelineTestNodeRunResultDTO().setNodeId(nodeResult.getNodeId())
                        //.setState(nodeResult.getStatus())
                        .setStatus(nodeResult.getStatus().name())
                        .setErrorMsg(nodeResult.getErrorMsg())
                        .setDisplayName(node.isPresent() ? graphTransformer.generateLabel(node.get()) : nodeResult.getNodeName())
                        .setTestData(testData)
                );

                // capture the output node's result to populate overview
                if(outputNodeIds.contains(nodeResult.getNodeId())){
                    outputNodeResults.addAll(actualResult);
                }
            });

            // populate overview of test data
            PipelineTestData testOverview = getSimulationTestData(test);
            testOverview.setActualResult(outputNodeResults);

            User user = userService.getUserById(test.getUserId());
            resultDTOs.add(new PipelineTestRunResultDTO()
                    .setId(test.getId())
                    .setDisplayName(test.getName())
                    .setDescription(test.getDescription())
                    .setNodes(nodeResultDTOs)
                    .setStatus(result.getStatus().name())
                    .setErrorMsg(result.getErrorMsg())
                    .setOwnerFirstName(user.getFirstName())
                    .setOwnerLastName(user.getLastName())
                    .setOwnerEmail(user.getEmail())
                    .setTestData(testOverview)
                    );

        });
        resultsDTO.setId(simulationRun.getId());
        resultsDTO.setRunName(simulationRun.getName());
        resultsDTO.setResultDetails(resultDTOs);
        return resultsDTO;
    }

    private PipelineTestData populatePipelineTestData(PipelineTestData testData, PipelineTest test,
                                                      List<PipelineTestNodeData> inputs, List<PipelineTestNodeData> actualResult, MappingGraph graph) {
        testData.getInput().addAll(getPipelineNodeData(inputs,graph));
        // TOOD: Remove? No expected results for a live pipeline test
        //testData.getExpectedResult().addAll(getPipelineNodeData(actualResult));
        return testData;
    }

    private List<PipelineTestNodeData> getPipelineNodeData(List<PipelineTestNodeData> inputs, MappingGraph graph){
        List<PipelineTestNodeData> nodeInputs = new ArrayList<>();
        Map<String, EntityDefinition> cachedEntities= new HashMap<>();
        inputs.stream().forEach(input -> {
            PipelineTestNodeData node = new PipelineTestNodeData();
            node.setNodeId(input.getNodeId());
            node.setNodeName(input.getNodeName());
            node.setValue(input.getValue());
            node.setApiName(input.getApiName());
            node.setDataType(input.getDataType());
            node.setDisplayName(input.getDisplayName());
            if(graph != null) {
            	graph.getNode(input.getNodeId()).ifPresent(n -> {
            		EntityDefinition entity = getEntity(n,cachedEntities);
            		// certain nodes like a filter node, will not have entitydefinition.
            		if (entity == null) return;
            		if (!entity.hasField(input.getApiName())) return; 
            		AttributeDefinition attr = entity.getFieldByName(input.getApiName());
            		if(attr != null){
            			node.setDataType(attr.getDataType().getName());
            			node.setDisplayName(attr.getDisplayName());
            			var convertedValue = attr.convert(input.getValue());
            			node.setValue(convertedValue == null ? "" : convertedValue);
            		}
            	});
            }
            nodeInputs.add(node);
        });

        return nodeInputs;
    }

    public List<TestRunDTO> toPipelineTestRunDTO(List<PipelineTest> tests) {
        List<TestRunDTO> runsDTO = new ArrayList<>();
        tests.forEach(run -> runsDTO.add(new TestRunDTO(run)));
        return runsDTO;
    }

    public PipelineTestRunResultsDTO toPipelineTestResultDTO(PipelineTest pipelineTest, List<TestResult> results) {
        PipelineTestRunResultsDTO resultsDTO = new PipelineTestRunResultsDTO(pipelineTest);
        List<PipelineTestRunResultDTO> resultDTOs = new ArrayList<>();

        Optional<MappingGraph> graph = graphService.retrieve(pipelineTest.getGraphId());
        User user = userService.getUserById(pipelineTest.getUserId());

        // Load node results for all test results
        testResultLoader.loadNodeResults(results);

        results.stream().forEach(result -> {
            // populate overview of test data
            PipelineTestData testOverview = new PipelineTestData();
            List<TestNodeResult> nodeResults = result.getNodeResults();
            Set<String> outputNodeIds = nodeResults.stream().map(nodeRes -> nodeRes.getNodeId()).collect(Collectors.toSet());
            List<PipelineTestNodeData> outputNodeResults = new ArrayList<>();
            List<PipelineTestNodeRunResultDTO> nodeResultDTOs = new ArrayList<>();

            nodeResults.forEach(nodeResult -> {
                Optional<MappingNode> node = Optional.empty();
                if(graph.isPresent()) {
                	node = graph.get().getNode(nodeResult.getNodeId());
                }
                PipelineTestData testData = new PipelineTestData();

                List<PipelineTestNodeData> inputs = new ArrayList<>();
                nodeResult.getInputs().forEach((k, v) -> {
                    inputs.add(new PipelineTestNodeData(nodeResult, v));
                });
                testData.setInput(inputs);

                List<PipelineTestNodeData> actualResult = new ArrayList<>();
                nodeResult.getOutputs().forEach((k, v) -> {
                    // TODO: fix this to capture 'Sync Errors', for live tests, for now, no record level failures
                    actualResult.add(new PipelineTestNodeData(nodeResult, v).setFailed(false));
                });
                testData.setActualResult(actualResult);

                String displayName = (node.isPresent() ? graphTransformer.generateLabel(node.get()) : nodeResult.getNodeName());
                nodeResultDTOs.add(new PipelineTestNodeRunResultDTO(nodeResult, displayName, testData));

                populatePipelineTestData(testOverview, pipelineTest, inputs, actualResult, graph.orElse(null));
                // capture the output node's result to populate overview
                if(outputNodeIds.contains(nodeResult.getNodeId())){
                    outputNodeResults.addAll(actualResult);
                }
            });

            // populate overview of test data
            testOverview.setActualResult(outputNodeResults);

            resultDTOs.add(new PipelineTestRunResultDTO(pipelineTest, nodeResultDTOs, testOverview, result, user));
        });
        resultsDTO.setResultDetails(resultDTOs);
        return resultsDTO;
    }

    private TestConfig createTestConfig(PipelineTestData testData){
        TestConfig testConfig = new TestConfig();
        testConfig.setInputs(getTestInputs(testData.getInput()));
        testConfig.setExpectedOutputs(getTestInputs(testData.getExpectedResult()));
        return testConfig;
    }

    private List<SimulationNodeInput> getTestInputs(List<PipelineTestNodeData> inputs){
        Map<String, List<PipelineTestNodeData>> mapOfNodeInputs = inputs.stream()
                .collect(Collectors.groupingBy(PipelineTestNodeData::getNodeId));

        return mapOfNodeInputs.entrySet().stream().map(e -> {
            SimulationNodeInput nodeInput = new SimulationNodeInput();
            nodeInput.setNodeId(e.getKey());
            nodeInput.setNodeName(e.getValue().isEmpty() ? null : e.getValue().get(0).getNodeName());
            Map<String, Object> fieldValues = new HashMap<>();
            e.getValue().forEach(in -> fieldValues.put(in.getApiName(), in.getValue()));
            nodeInput.setFieldValues(fieldValues);
            return nodeInput;
        }).collect(Collectors.toList());
    }

}
