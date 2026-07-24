package com.syncari.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.core.SyncariContext;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Message;
import com.syncari.core.event.Publisher;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.PipelineTestStatus;
import com.syncari.core.model.misc.Taggable;
import com.syncari.core.model.misc.test.SimulationMappingGraph;
import com.syncari.core.model.misc.test.SimulationNodeInput;
import com.syncari.core.model.misc.test.TestConfig;
import com.syncari.core.model.util.Scope;
import com.syncari.core.repositories.customer.PipelineTestRepo;
import com.syncari.core.repositories.customer.SimulationRunRepo;
import com.syncari.core.repositories.customer.TestResultRepo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

@Component
@Slf4j
public class SimulationService {

    @Autowired
    MappingGraphService graphService;

    @Autowired
    PipelineTestRepo pipelineTestRepo;

    @Autowired
    SimulationRunRepo simulationRunRepo;

    @Autowired
    TestResultRepo testResultRepo;

    @Autowired
    SchemaService schemaService;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    Publisher publisher;

    @Autowired
    TagService tagService;

    private static final int LIMIT = 500;

    public void validate(PipelineTest test){
        validateCondition(StringUtils.isEmpty(test.getTargetId()), i18n("test_empty_targetId"));
        if(Scope.ENTITY.equals(test.getScope())){
            validateEntityTest(test);
        } else if(Scope.ATTRIBUTE.equals(test.getScope())){
            validateFieldTest(test);
        } else{
            throw new SyncariValidationException(i18n("test_invalid_scope", test.getScope()));
        }
    }

    private void validateEntityTest(PipelineTest test){
        TestConfig config = test.getTestConfig();
        // check if draft entity graph exist
        Optional<MappingGraph> draftMaybe = graphService.retrieveDraftEntityGraph(test.getTargetId());
        validateCondition(draftMaybe.isEmpty(), i18n("test_no_draft_graph", test.getScope().name(), test.getName()));
        MappingGraph draft = draftMaybe.get();

        validateTestConfig(config, draft);
    }

    private void validateFieldTest(PipelineTest test){
        TestConfig config = test.getTestConfig();
        // check if draft attribute graph exist
        Optional<MappingGraph> draftMaybe = graphService.retrieveDraftAttributeGraph(test.getTargetId());
        validateCondition(draftMaybe.isEmpty(), i18n("test_no_draft_graph", test.getScope().name(), test.getName()));
        MappingGraph draft = draftMaybe.get();

        validateTestConfig(config, draft);
    }

    private void validateTestConfig(TestConfig config, MappingGraph draft){
        // validate test inputs and expected output
        validateCondition(config == null, i18n("test_null_config"));
        var sourceNodes = draft.getSources().collect(Collectors.toList());
        var sinkNodes = draft.getSinks().collect(Collectors.toList());
        var syncariNode = draft.getCoreNode();
        validateCondition(config.getInputs().isEmpty(), i18n("test_missing_input"));
        validateCondition(config.getExpectedOutputs().isEmpty(), i18n("test_missing_expected_output"));
        config.getInputs().forEach(input -> {
            boolean isValidInput = sourceNodes.stream().anyMatch(n -> n.getId().equals(input.getNodeId()));
            validateCondition(!isValidInput, i18n("test_invalid_input_node", input.getNodeId()));

            MappingNode node = sourceNodes.stream().filter(n -> n.getId().equals(input.getNodeId())).findFirst().get();
            validateNodeInputs(node, input);
        });

        if(sinkNodes.isEmpty()){
            // In absence of sink nodes, expectedOutput can be set for core syncari node
            config.getExpectedOutputs().forEach(input -> {
                validateCondition(!input.getNodeId().equals(syncariNode.getId()), i18n("test_invalid_output_node", input.getNodeId()));
                validateNodeInputs(syncariNode, input);
            });
        } else {
            // expectedOutput should be set for sink nodes if present in graph
            config.getExpectedOutputs().forEach(input -> {
                boolean isSinkInput = sinkNodes.stream().anyMatch(n -> n.getId().equals(input.getNodeId()));
                validateCondition(!isSinkInput, i18n("test_invalid_output_node", input.getNodeId()));
                MappingNode node = sinkNodes.stream().filter(n -> n.getId().equals(input.getNodeId())).findFirst().get();
                validateNodeInputs(node, input);
            });
        }
    }

    private void validateNodeInputs(MappingNode node, SimulationNodeInput input){
        EntityDefinition entity = node.getScope().equals(Scope.ENTITY)
                ? graphService.extractEntityFromNode(node)
                : schemaService.getEntity(graphService.extractAttributeFromNode(node).getEntityId());

        validateCondition(entity == null, i18n("test_invalid_node_for_input", node.getName()));
        input.getFieldValues().forEach((apiName, value) -> {
            var inputAttribute = entity.getField(apiName);
            validateCondition(inputAttribute.isEmpty(),
                    i18n("test_invalid_attribute_input", apiName, node.getName()));
            // validate if input value is convertible in field's data type
            var convertedValue = inputAttribute.get().convert(value);
			validateCondition(value != null && !StringUtils.isBlank(value.toString())  && convertedValue == null,
					i18n("test_inconvertible_value_for_input"), value == null ? null : value.toString(),
					inputAttribute.get().getDataType().getName(), inputAttribute.get().getDisplayName());
        });

    }

    public List<PipelineTest> listEntityTests(String syncariEntityId){
        return pipelineTestRepo.findByTargetIdAndScope(syncariEntityId, Scope.ENTITY);
    }

    public List<PipelineTest> listFieldTests(String syncariAttributeId){
        return pipelineTestRepo.findByTargetIdAndScope(syncariAttributeId, Scope.ATTRIBUTE);
    }

    public PipelineTest getEntityTest(String syncariEntityId, String testId){
        PipelineTest test = getTest(testId);
        validateCondition(!Scope.ENTITY.equals(test.getScope()) || !syncariEntityId.equals(test.getTargetId()),
                String.format("Test with id %s does not belong to Syncari Entity with Id %s", testId, syncariEntityId));
        return test;
    }

    public PipelineTest getFieldTest(String syncariAttributeId, String testId){
        PipelineTest test = getTest(testId);
        validateCondition(!Scope.ATTRIBUTE.equals(test.getScope()) || !syncariAttributeId.equals(test.getTargetId()),
                String.format("Test with id %s does not belong to Syncari Attribute with Id %s", testId, syncariAttributeId));
        return test;

    }

    public PipelineTest getTest(String testId){
        return pipelineTestRepo.findById(testId)
                .orElseThrow(() -> new NotFoundException(PipelineTest.class, "id", testId));
    }

    public SimulationRun getSimulationRun(String simulationRunId){
        SimulationRun simRun = simulationRunRepo.findById(simulationRunId)
                .orElseThrow(() -> new NotFoundException(SimulationRun.class, "id", simulationRunId));
        simRun.setSimulationResults(testResultRepo.findBySimulationRunId(simulationRunId));
        return simRun;
    }

    public PipelineTest createEntityTest(PipelineTest test){
        validate(test);
        PipelineTest saved = upsertTest(test);
        Map<String, Object> tagMap = test.getTags().stream().collect(Collectors.toMap(t -> t.getName(), t -> t.getValue()));
        List<Tag> tags = tagService.assign(tagMap, Taggable.test, saved.getId());
        saved.setTags(tags);
        return saved;
    }

    public PipelineTest createFieldTest(PipelineTest test){
        validate(test);
        PipelineTest saved = upsertTest(test);
        Map<String, Object> tagMap = test.getTags().stream().collect(Collectors.toMap(t -> t.getName(), t -> t.getValue()));
        List<Tag> tags = tagService.assign(tagMap, Taggable.test, saved.getId());
        saved.setTags(tags);
        return saved;
    }

    public PipelineTest updateEntityTest(String testId, PipelineTest test){
        validate(test);
        PipelineTest saved = upsertTest(test);
        var updatedTags = tagService.updateTagsFor(saved.getId(), Taggable.test, test.getTags());
        saved.setTags(updatedTags);
        return saved;
    }

    public PipelineTest updateFieldTest(String testId, PipelineTest test){
        validate(test);
        PipelineTest saved = upsertTest(test);
        var updatedTags = tagService.updateTagsFor(saved.getId(), Taggable.test, test.getTags());
        saved.setTags(updatedTags);
        return saved;
    }

    public SimulationRun runFieldTests(String targetId, String runName, List<String> testIds) {
        MappingGraph draft = graphService.retrieveDraftAttributeGraph(targetId).orElseThrow(() -> new NotFoundException(MappingGraph.class, "id", targetId));
        EntityDefinition coreEntity = graphService.getCoreEntity(draft);
        Map<String, EntityDefinition> sourceEntitiesMap = graphService.getConnectedSourceEntityMap(draft);
        graphService.validateGraph(draft, coreEntity, sourceEntitiesMap);
        SimulationRun simulationRun = setupSimulationRun(targetId, runName, testIds, draft);
        sendSimulationMessage(simulationRun, draft);
        return simulationRun;
    }

    public List<SimulationRun> getFieldTestRuns(String targetId) {
        return simulationRunRepo.findByTargetId(targetId, PageRequest.of(0, LIMIT)).stream().map(simRun ->
                simRun.setSimulationResults(testResultRepo.findBySimulationRunId(simRun.getId()))
        ).collect(Collectors.toList());
    }

    public PipelineTest deleteFieldTest(String testId) {
        PipelineTest test = pipelineTestRepo.findById(testId).orElseThrow(() -> new NotFoundException(PipelineTest.class, "id", testId));
        pipelineTestRepo.deleteByTestId(testId);
        return test;
    }

    public PipelineTest deleteEntityTest(String testId) {
        return deleteFieldTest(testId);
    }

    public SimulationRun getFieldSimulationRun(String targetId, String runId) {
        if (runId.equalsIgnoreCase("latest")) {
            return simulationRunRepo.findLatest(targetId);
        } else {
            return simulationRunRepo.findById(runId).orElseThrow(() -> new NotFoundException(SimulationRun.class, "id", runId));
        }
    }

    public List<TestResult> getFieldSimulationResults(String targetId, String runId) {
        return testResultRepo.findBySimulationRunId(runId);
    }


    public SimulationRun runEntityTests(String targetId, String runName, List<String> testIds) {
        MappingGraph draft = graphService.retrieveDraftEntityGraph(targetId).orElseThrow(() -> new NotFoundException(MappingGraph.class, "id", targetId));
        EntityDefinition coreEntity = graphService.getCoreEntity(draft);
        Map<String, EntityDefinition> sourceEntitiesMap = graphService.getConnectedSourceEntityMap(draft);
        graphService.validateGraph(draft, coreEntity, sourceEntitiesMap);
        SimulationRun simulationRun = setupSimulationRun(targetId, runName, testIds, draft);
        sendSimulationMessage(simulationRun, draft);
        return simulationRun;
    }

    public void sendSimulationMessage(SimulationRun simulationRun, MappingGraph draft){
        try {
            Event event = new Event().setType(EventTypes.SIMULATE_PIPELINE).setDetails(Map.of("simulationRunId", simulationRun.getId()));
            Message msg = new Message(SyncariContext.getSyncariId(), event);
            String eventString = mapper.writeValueAsString(msg);
            log.info(String.format("Sending Message: %s", eventString));
            publisher.publishToViperQueue(eventString);
            log.info(format("Successfully sent message to Simulate pipeline %s", draft.getId()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public SimulationRun setupSimulationRun(String targetId, String name, List<String> testIds, MappingGraph draft) {
        List<PipelineTest> tests = new ArrayList<>();
        testIds.forEach(testId -> {
            tests.add(pipelineTestRepo.findById(testId)
                    .orElseThrow(() -> new NotFoundException(PipelineTest.class, "id", testId)));
        });

        // validate tests before simulating
        tests.forEach(t -> validate(t));

        // Create an simulation run entry
        SimulationMappingGraph simDraft = new SimulationMappingGraph().createSimulationMappingGraph(draft);
        SimulationRun simulationRun = simulationRunRepo.save(new SimulationRun()
                .setName(!StringUtils.isBlank(name) ? name
                        : ZonedDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy - HH:mm:ss Z")))
                .setTargetId(targetId)
                .setGraph(simDraft)
                .setExecutedAt(ZonedDateTime.now())
        );
        List<TestResult> results = testResultRepo.saveAll(
                tests.stream().map(t ->
                        new TestResult()
                                .setSimulationRunId(simulationRun.getId())
                                .setTest(t)
                                .setStatus(PipelineTestStatus.queued)
                ).collect(Collectors.toList()));

        simulationRun.setSimulationResults(results);
        return simulationRun;
    }

    public SimulationRun getEntitySimulationRun(String targetId, String runId) {
        if (runId.equalsIgnoreCase("latest")) {
            return simulationRunRepo.findLatest(targetId);
        } else {
            return simulationRunRepo.findById(runId).orElseThrow(() -> new NotFoundException(SimulationRun.class, "id", runId));
        }
    }

    public List<SimulationRun> getEntityTestRuns(String targetId) {
        return simulationRunRepo.findByTargetId(targetId, PageRequest.of(0, LIMIT)).stream().map(simRun ->
                simRun.setSimulationResults(testResultRepo.findBySimulationRunId(simRun.getId()))
        ).collect(Collectors.toList());
    }

    public List<TestResult> getEntitySimulationResults(String targetId, String runId) {
        return testResultRepo.findBySimulationRunId(runId);
    }

    private PipelineTest upsertTest(PipelineTest test){
        Optional<MappingGraph> draft = Scope.ENTITY.equals(test.getScope())
                ? graphService.retrieveDraftEntityGraph(test.getTargetId())
                : graphService.retrieveDraftAttributeGraph(test.getTargetId());

        fixTestInputDataType(test.getTestConfig().getInputs(), draft.get());
        fixTestInputDataType(test.getTestConfig().getExpectedOutputs(), draft.get());

        return pipelineTestRepo.save(test);
    }

    protected void fixTestInputDataType(List<SimulationNodeInput> inputs, MappingGraph graph){
        inputs.forEach(input -> {
            MappingNode node = graph.getNode(input.getNodeId()).get();
            EntityDefinition entity = node.getScope().equals(Scope.ENTITY)
                    ? graphService.extractEntityFromNode(node)
                    : schemaService.getEntity(graphService.extractAttributeFromNode(node).getEntityId());

            Map<String, Object> inputValuesWithDataType = new HashMap<>();
            input.getFieldValues().forEach((apiName, value) -> {
                var inputAttribute = entity.getField(apiName);
                var convertedValue = inputAttribute.get().convert(value);
                inputValuesWithDataType.put(apiName, convertedValue);
            });

            input.setFieldValues(inputValuesWithDataType);
        });
    }
}
