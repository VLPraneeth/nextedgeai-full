package com.syncari.api.rest.controllers;

import static com.syncari.utils.I18n.*;

import com.syncari.api.core.util.*;
import com.syncari.api.rest.controllers.data.*;
import com.syncari.api.rest.controllers.data.test.*;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.util.*;
import com.syncari.utils.KeyValue;
import static com.syncari.core.security.Permissions.*;
import com.syncari.core.service.*;
import java.util.*;
import lombok.extern.slf4j.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.security.access.annotation.*;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/test")
public class TestController {

    @Autowired
    PipelineTestService pipelineTestService;

    @Autowired
    SimulationService simulationService;

    @Autowired
    SchemaService schemaService;

    @Autowired
    MappingGraphService graphService;

    @Autowired
    TestTransformer transformer;

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline/{targetId}")
    public List<PipelineTestDTO> listEntityTests(@PathVariable String targetId){
        List<PipelineTest> tests = simulationService.listEntityTests(targetId);
        return transformer.toTestDTOs(tests);
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/fieldPipeline/{targetId}")
    public List<PipelineTestDTO> listFieldTests(@PathVariable String targetId){
        List<PipelineTest> tests = simulationService.listFieldTests(targetId);
        return transformer.toTestDTOs(tests);
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline/{targetId}/testId/{testId}")
    public PipelineTestDTO getEntityTest(@PathVariable String targetId, @PathVariable String testId){
        PipelineTest test = simulationService.getEntityTest(targetId, testId);
        return transformer.toPipelineTestDTO(test);
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/fieldPipeline/{targetId}/testId/{testId}")
    public PipelineTestDTO getFieldTest(@PathVariable String targetId, @PathVariable String testId){
        PipelineTest test = simulationService.getFieldTest(targetId, testId);
        return transformer.toPipelineTestDTO(test);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/entityPipeline/{targetId}")
    public PipelineTestDTO createEntityTest(@PathVariable String targetId, @RequestBody PipelineTestDTO test){
        PipelineTest saved = simulationService.createEntityTest(transformer.toPipelineTest(test, Scope.ENTITY, targetId));
        return transformer.toPipelineTestDTO(saved);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/fieldPipeline/{targetId}")
    public PipelineTestDTO createFieldTest(@PathVariable String targetId, @RequestBody PipelineTestDTO test) {
        PipelineTest saved = simulationService.createFieldTest(transformer.toPipelineTest(test, Scope.ATTRIBUTE, targetId));
        return transformer.toPipelineTestDTO(saved);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.PUT, value = "/entityPipeline/{targetId}/testId/{testId}")
    public PipelineTestDTO updateEntityTest(@PathVariable String targetId, @PathVariable String testId, @RequestBody PipelineTestDTO test){
        PipelineTest saved = simulationService.updateEntityTest(testId, transformer.toPipelineTest(test, Scope.ENTITY, targetId));
        return transformer.toPipelineTestDTO(saved);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.PUT, value = "/fieldPipeline/{targetId}/testId/{testId}")
    public PipelineTestDTO updateFieldTest(@PathVariable String targetId, @PathVariable String testId, @RequestBody PipelineTestDTO test) {
        PipelineTest saved = simulationService.updateFieldTest(testId, transformer.toPipelineTest(test, Scope.ATTRIBUTE, targetId));
        return transformer.toPipelineTestDTO(saved);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.DELETE, value = "/entityPipeline/{targetId}/testId/{testId}")
    public PipelineTestDTO deleteEntityTest(@PathVariable String targetId, @PathVariable String testId) {
        return transformer.toPipelineTestDTO(simulationService.deleteEntityTest(testId));
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.DELETE, value = "/fieldPipeline/{targetId}/testId/{testId}")
    public PipelineTestDTO deleteFieldTest(@PathVariable String targetId, @PathVariable String testId) {
        return transformer.toPipelineTestDTO(simulationService.deleteFieldTest(testId));
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline/{targetId}/nodeId/{nodeId}/fields/picklistValues")
    public List<KeyValue> getEntityPipelineNodeInputPicklist(@PathVariable String targetId, @PathVariable String nodeId){
        List<AttributeDefinition> attributes = graphService.getInputFieldsForEntityNode(targetId, nodeId);
        return transformer.toFieldPicklist(attributes);
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/fieldPipeline/{targetId}/nodeId/{nodeId}/fields/picklistValues")
    public List<KeyValue> getFieldPipelineNodeInputPicklist(@PathVariable String targetId, @PathVariable String nodeId){
        List<AttributeDefinition> attributes = graphService.getInputFieldsForAttributeNode(targetId, nodeId);
        return transformer.toFieldPicklist(attributes);
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/fieldPipeline/{targetId}/run")
    public TestRunDTO runFieldTest(@PathVariable String targetId, @RequestBody SimulationRunRequest runRequest){
        return transformer.toSimulationTestRunDTO(simulationService.runFieldTests(targetId, runRequest.getName(), runRequest.getTestIds()));
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/fieldPipeline/{targetId}/run")
    public List<TestRunDTO> getFieldTestRuns(@PathVariable String targetId) {
        List<SimulationRun> simulationTestRuns = simulationService.getFieldTestRuns(targetId);
        return transformer.toSimulationTestRunsDTO(simulationTestRuns);
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/fieldPipeline/{targetId}/run/{runId}")
    public PipelineTestRunResultsDTO runFieldRunResult(@PathVariable String targetId, @PathVariable String runId){
        SimulationRun simulationRun = simulationService.getFieldSimulationRun(targetId, runId);
        if (simulationRun != null) {
            List<TestResult> runResult = simulationService.getFieldSimulationResults(targetId, simulationRun.getId());
            return transformer.toSimulationRunResultsDTO(simulationRun, runResult);
        }
        return null;
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/entityPipeline/{targetId}/run")
    public TestRunDTO runEntityTest(@PathVariable String targetId, @RequestBody SimulationRunRequest runRequest){
        return transformer.toSimulationTestRunDTO(simulationService.runEntityTests(targetId, runRequest.getName(), runRequest.getTestIds()));
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline/{targetId}/run")
    public List<TestRunDTO> getEntityTestRuns(@PathVariable String targetId) {
        List<SimulationRun> simulationTestRuns = simulationService.getEntityTestRuns(targetId);
        return transformer.toSimulationTestRunsDTO(simulationTestRuns);
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline/{targetId}/run/{runId}")
    public PipelineTestRunResultsDTO runEntityRunResult(@PathVariable String targetId, @PathVariable String runId){
        SimulationRun simulationRun = simulationService.getEntitySimulationRun(targetId, runId);
        if (simulationRun != null) {
            List<TestResult> runResult = simulationService.getEntitySimulationResults(targetId, simulationRun.getId());
            return transformer.toSimulationRunResultsDTO(simulationRun, runResult);
        }
        return null;
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline/{graphId}/test")
    public List<TestRunDTO> getEntityPipelineTestRuns(@PathVariable String graphId) {
        List<PipelineTest> pipelineTests = pipelineTestService.getEntityPipelineTests(graphId);
        return transformer.toPipelineTestRunDTO(pipelineTests);
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline/{graphId}/test/{pipelineTestId}")
    public PipelineTestRunResultsDTO getEntityPipelineTestResult(@PathVariable String graphId, @PathVariable String pipelineTestId) {
        Optional<PipelineTest> pipelineTest = pipelineTestService.getTestByIdAndGraphId(graphId, pipelineTestId);
        if (!pipelineTest.isPresent()) {
            throw new SyncariValidationException(i18n("pipeline_test_not_found"));
        }
        List<TestResult> runResult = pipelineTestService.getEntityPipelineTestResults(pipelineTest.get().getId());
        return transformer.toPipelineTestResultDTO(pipelineTest.get(), runResult);
    }
}
