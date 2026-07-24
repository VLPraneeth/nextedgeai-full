package com.syncari.api.rest.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.api.core.util.TestTransformer;
import com.syncari.api.rest.controllers.data.test.PipelineTestNodeData;
import com.syncari.api.rest.controllers.data.test.SimulationRunRequest;
import com.syncari.api.rest.controllers.data.test.PipelineTestDTO;
import com.syncari.api.rest.controllers.data.test.PipelineTestData;
import com.syncari.api.rest.controllers.data.test.TestRunDTO;
import com.syncari.api.rest.controllers.data.test.PipelineTestRunResultsDTO;
import com.syncari.api.rest.controllers.exceptions.ResourceNotFoundException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.AttributeSinkNodeConfig;
import com.syncari.core.model.Connector;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.PipelineTest;
import com.syncari.core.model.SimulationRun;
import com.syncari.core.model.TestResult;
import com.syncari.core.model.SyncStream.Status;
import com.syncari.core.model.misc.PipelineTestStatus;
import com.syncari.core.model.misc.test.SimulationMappingGraph;
import com.syncari.core.model.misc.test.TestNodeResult;
import com.syncari.core.model.misc.test.TestNodeResultAttributeValue;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import com.syncari.core.repositories.customer.PipelineTestRepo;
import com.syncari.core.repositories.customer.SimulationRunRepo;
import com.syncari.core.repositories.customer.TestResultRepo;
import com.syncari.core.schema.AttributeDef;
import com.syncari.core.schema.EntityDef;
import static com.syncari.core.security.Permissions.READ_STUDIO;
import static com.syncari.core.security.Permissions.WRITE_STUDIO;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.PipelineTestService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.service.SimulationService;
import com.syncari.restutils.data.MappingGraphDTO;
import com.syncari.utils.DateUtil;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class TestControllerTest extends AbstractSyncariTest {

    @Autowired
    PipelineController pipelineController;

    @Autowired
    private MockMvc mvc;

    @Autowired
    MappingGraphService graphService;

    @Autowired
    MappingNodeRepo nodeRepo;

    private static Connector connector;

    @Autowired
    private ConnectorService connectorService;

    @Autowired
    private PipelineTestService pipelineTestService;

    @Autowired
    private EndSystemConfig config;

    @Autowired
    SchemaService schemaService;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    TestTransformer transformer;

    @Autowired
    SimulationService simulationService;

    @Autowired
    SimulationRunRepo simulationRunRepo;

    @Autowired
    TestResultRepo testResultRepo;

    @Autowired
    PipelineTestRepo pipelineTestRepo;

    MappingGraphDTO entityGraph;

    @Override
    public void setUp() {
        super.setUp();
        if(connector == null) {
            connector = new Connector("testConnector-1", connectorService.describe("salesforce").getId(),
                    config.getSalesforceUrl(), config.getUser(), config.getPassword());
            connector.getAuthConfig().setToken(config.getToken());
            connector = connectorService.save(connector);
            connectorService.authenticated(connector.getId());
            connectorService.activate(connector.getId());
            schemaService.activateMapping(connector);
        }
        pushContext();
    }

    @Override
    public void tearDown() {
        restoreContext();
        resetRepos(testResultRepo, simulationRunRepo, pipelineTestRepo);
        super.tearDown();
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void createEntityTest() throws Exception {
        var graph =  getEntityGraph();
        var srcNode = graph.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ENTITY_SOURCE).findFirst().get();
        var sinkNode = graph.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ENTITY_SINK).findFirst().get();
        var testSimulation = createEntityTestSimulationInstance(graph);

        var result = mvc.perform(
                post("/api/v1/test/entityPipeline/{targetId}", graph.getTargetId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(testSimulation))
        ).andReturn();

        var createEntityTestSimulationResult = mapper.readValue(result.getResponse().getContentAsString(), PipelineTestDTO.class);

        assertTrue(createEntityTestSimulationResult.getDisplayName().equalsIgnoreCase("test1Name"));
        assertTrue(createEntityTestSimulationResult.getOwnerFirstName().equalsIgnoreCase("Syncari"));
        assertTrue(createEntityTestSimulationResult.getOwnerLastName().equalsIgnoreCase("Admin"));
        assertTrue(createEntityTestSimulationResult.getOwnerEmail().equalsIgnoreCase("admin@syncari.com"));
        assertTrue(createEntityTestSimulationResult.getDescription().equalsIgnoreCase("test1Name Description"));
        assertEquals(createEntityTestSimulationResult.getTags().size(), 1);
        assertTrue(createEntityTestSimulationResult.getTags().contains("entityTestTag1"));
        assertFalse(createEntityTestSimulationResult.getTestData().getInput().isEmpty());
        assertEquals(srcNode.getName(),createEntityTestSimulationResult.getTestData().getInput().get(0).getNodeName());
        assertFalse(createEntityTestSimulationResult.getTestData().getExpectedResult().isEmpty());
        assertEquals(sinkNode.getName(),createEntityTestSimulationResult.getTestData().getExpectedResult().get(0).getNodeName());
    }

    // Get list of list of entity tests test
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void getEntityTests() throws Exception {
        var graph = getEntityGraph();
        PipelineTest test = createEntityTestSimulation(graph);

        var result = mvc.perform(
                get("/api/v1/test/entityPipeline/{targetId}", graph.getTargetId())
                        .contentType(MediaType.APPLICATION_JSON)
        ).andReturn();

        var listOfTests = (List<PipelineTestDTO>)mapper.readValue(result.getResponse().getContentAsString(), new TypeReference<List<PipelineTestDTO>>(){});
        assertEquals(listOfTests.size(), 1);
        assertTrue(listOfTests.get(0).getDisplayName().equalsIgnoreCase("test1Name"));
        assertTrue(listOfTests.get(0).getDescription().equalsIgnoreCase("test1Name Description"));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void getEntityTest() throws Exception {
        var graph =  getEntityGraph();
        PipelineTest test = createEntityTestSimulation(graph);

        var result = mvc.perform(
                get("/api/v1/test/entityPipeline/{targetId}/testId/{testId}", graph.getTargetId(), test.getId())
                        .contentType(MediaType.APPLICATION_JSON)
        ).andReturn();

        var testResult = mapper.readValue(result.getResponse().getContentAsString(), PipelineTestDTO.class);
        assertTrue(testResult.getDisplayName().equalsIgnoreCase("test1Name"));
        assertTrue(testResult.getDescription().equalsIgnoreCase("test1Name Description"));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void deleteEntityTest() throws Exception {
        var graph =  getEntityGraph();
        PipelineTest test = createEntityTestSimulation(graph);

        var result = mvc.perform(
                delete("/api/v1/test/entityPipeline/{targetId}/testId/{test}", graph.getTargetId(), test.getId())
                        .contentType(MediaType.APPLICATION_JSON)
        ).andReturn();

        var testResult = mapper.readValue(result.getResponse().getContentAsString(), PipelineTestDTO.class);
        assertTrue(testResult.getDisplayName().equalsIgnoreCase("test1Name"));
        assertTrue(testResult.getDescription().equalsIgnoreCase("test1Name Description"));

        result = mvc.perform(
                get("/api/v1/test/entityPipeline/{targetId}/testId/{testId}", graph.getTargetId(), test.getId())
                        .contentType(MediaType.APPLICATION_JSON)
        ).andReturn();
        assertTrue(mapper.readValue(result.getResponse().getContentAsString(), ResourceNotFoundException.class).getMessage().contains("not found"));
    }

    // Run Test test
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void runEntityTests() throws Exception {
        var graph = getEntityGraph();
        PipelineTest test = createEntityTestSimulation(graph);

        SimulationRunRequest runRequest = new SimulationRunRequest();
        runRequest.setName("entityTestRun1");
        runRequest.setTestIds(List.of(test.getId()));
        var result = mvc.perform(
                post("/api/v1/test/entityPipeline/{targetId}/run", graph.getTargetId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(runRequest))
        ).andReturn();

        var simulationTestRun = (TestRunDTO)mapper.readValue(result.getResponse().getContentAsString(), TestRunDTO.class);
        assertEquals(simulationTestRun.getTestNames().size(), 1);
        assertEquals(simulationTestRun.getTestNames().get(0), "test1Name");
        assertEquals(simulationTestRun.getRunName(), "entityTestRun1");
    }

    // Get the list of entity test runs
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void getEntityTestRuns() throws Exception {
        var graph = getEntityGraph();
        PipelineTest test = createEntityTestSimulation(graph);
        TestResult testResult = createEntityTestResult(graph, test);

        var result = mvc.perform(
                get("/api/v1/test/entityPipeline/{targetId}/run", graph.getTargetId())
                        .contentType(MediaType.APPLICATION_JSON)
        ).andReturn();

        var simulationTestRuns = (List<TestRunDTO>)mapper.readValue(result.getResponse().getContentAsString(), new TypeReference<List<TestRunDTO>>(){});
        assertEquals(simulationTestRuns.get(0).getTestNames().size(), 1);
        assertTrue(simulationTestRuns.get(0).getTestNames().get(0).equalsIgnoreCase("test1Name"));
    }

    // Get a entity test by id
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void getEntityTestRunById() throws Exception {
        var graph = getEntityGraph();
        PipelineTest test = createEntityTestSimulation(graph);
        TestResult testResult = createEntityTestResult(graph, graphService.retrieveDraftEntityGraph(test.getTargetId()).get(), test);

        var result = mvc.perform(
                get("/api/v1/test/entityPipeline/{targetId}/run/{runId}", graph.getTargetId(), testResult.getSimulationRunId())
                        .contentType(MediaType.APPLICATION_JSON)
        ).andReturn();

        var simulationTestRunResults = mapper.readValue(result.getResponse().getContentAsString(), PipelineTestRunResultsDTO.class);
        assertEquals(simulationTestRunResults.getResultDetails().size(), 1);
        assertTrue(simulationTestRunResults.getResultDetails().get(0).getDisplayName().equalsIgnoreCase("test1Name"));
    }

    // Get entity latest test test
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void getEntityTestRunLatest() throws Exception {
        var graph = getEntityGraph();
        PipelineTest test = createEntityTestSimulation(graph);
        TestResult testResult = createEntityTestResult(graph, graphService.retrieveDraftEntityGraph(test.getTargetId()).get(), test);

        var result = mvc.perform(
                get("/api/v1/test/entityPipeline/{targetId}/run/{runId}", graph.getTargetId(), "latest")
                        .contentType(MediaType.APPLICATION_JSON)
        ).andReturn();

        var simulationTestRunResults = mapper.readValue(result.getResponse().getContentAsString(), PipelineTestRunResultsDTO.class);
        assertEquals(simulationTestRunResults.getResultDetails().size(), 1);
        assertTrue(simulationTestRunResults.getResultDetails().get(0).getDisplayName().equalsIgnoreCase("test1Name"));
    }

    // Get entity latest test with no runs test
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void getEntityTestRunNoRuns() throws Exception {
        var graph = getEntityGraph();

        var result = mvc.perform(
                get("/api/v1/test/entityPipeline/{targetId}/run/{runId}", graph.getTargetId(), "latest")
                        .contentType(MediaType.APPLICATION_JSON)
        ).andReturn();

        assertEquals(result.getResponse().getContentLength(), 0);
        assertTrue(result.getResponse().getContentAsString().isEmpty());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void assertNullValuesInTestDoesNotThrowNPE() throws Exception {
        var graph = getEntityGraph();
        
        var testSimulation = createEntityTestSimulationInstanceWithNull(graph);
        PipelineTest test = simulationService.createEntityTest(transformer.toPipelineTest(testSimulation, Scope.ENTITY, graph.getTargetId()));
        
        TestResult testResult = createEntityTestResultWithNullValue(graph, 
            graphService.retrieveDraftEntityGraph(test.getTargetId()).get(), test);

        var result = mvc.perform(
                get("/api/v1/test/entityPipeline/{targetId}/run/{runId}", graph.getTargetId(), testResult.getSimulationRunId())
                        .contentType(MediaType.APPLICATION_JSON)
        ).andReturn();

        var simulationTestRunResults = mapper.readValue(result.getResponse().getContentAsString(), PipelineTestRunResultsDTO.class);
        assertEquals(simulationTestRunResults.getResultDetails().size(), 1);
        assertTrue(simulationTestRunResults.getResultDetails().get(0).getDisplayName().equalsIgnoreCase("test1Name"));
        PipelineTestNodeData actualResult = simulationTestRunResults.getResultDetails().get(0)
            .getNodes().get(0).getTestData().getActualResult().get(0);
        assertFalse(actualResult.isFailed());
        assertNotNull(actualResult);
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void assertNullValuesInTestWithDifferentExpOpDoesNotThrowNPE() throws Exception {
        var graph = getEntityGraph();

        var testSimulation = createEntityTestSimulationInstanceWithDifferentExpectedOutput(graph);
        PipelineTest test = simulationService.createEntityTest(transformer.toPipelineTest(testSimulation, Scope.ENTITY, graph.getTargetId()));

        TestResult testResult = createEntityTestResultWithNullValue(graph,
                graphService.retrieveDraftEntityGraph(test.getTargetId()).get(), test);

        var result = mvc.perform(
                get("/api/v1/test/entityPipeline/{targetId}/run/{runId}", graph.getTargetId(), testResult.getSimulationRunId())
                        .contentType(MediaType.APPLICATION_JSON)
        ).andReturn();

        var simulationTestRunResults = mapper.readValue(result.getResponse().getContentAsString(), PipelineTestRunResultsDTO.class);
        assertEquals(simulationTestRunResults.getResultDetails().size(), 1);
        assertTrue(simulationTestRunResults.getResultDetails().get(0).getDisplayName().equalsIgnoreCase("test1Name"));
        PipelineTestNodeData actualResult = simulationTestRunResults.getResultDetails().get(0)
                .getNodes().get(0).getTestData().getActualResult().get(0);
        assertNotNull(actualResult);
        assertFalse(actualResult.isFailed());
    }

    @Test
    @Ignore
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void failedTestDueToMismatchingOutput() throws Exception {
        var graph = getEntityGraph();

        var testSimulation = createEntityTestSimulationWithMismatchingOutput(graph);
        PipelineTest test = simulationService.createEntityTest(transformer.toPipelineTest(testSimulation, Scope.ENTITY, graph.getTargetId()));

        TestResult testResult = createEntityTestResultWithNullValue(graph,
                graphService.retrieveDraftEntityGraph(test.getTargetId()).get(), test);

        var result = mvc.perform(
                get("/api/v1/test/entityPipeline/{targetId}/run/{runId}", graph.getTargetId(), testResult.getSimulationRunId())
                        .contentType(MediaType.APPLICATION_JSON)
        ).andReturn();

        var simulationTestRunResults = mapper.readValue(result.getResponse().getContentAsString(), PipelineTestRunResultsDTO.class);
        assertEquals(simulationTestRunResults.getResultDetails().size(), 1);
        assertTrue(simulationTestRunResults.getResultDetails().get(0).getDisplayName().equalsIgnoreCase("test1Name"));
        PipelineTestNodeData actualResultSource = simulationTestRunResults.getResultDetails().get(0)
                .getNodes().get(0).getTestData().getActualResult().get(0);
        assertNotNull(actualResultSource);
        assertFalse(actualResultSource.isFailed());

        PipelineTestNodeData actualResultSink = simulationTestRunResults.getResultDetails().get(0)
                .getNodes().get(2).getTestData().getActualResult().get(0);
        assertNotNull(actualResultSink);
        assertTrue(actualResultSink.isFailed());
        assertEquals("test error message", simulationTestRunResults.getResultDetails().get(0)
                .getNodes().get(2).getErrorMsg());
    }

    // Get the list of entity live test runs
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void getEntityLiveTestRuns() throws Exception {
        var graphDTO = getEntityGraph();
        MappingGraph graph =  graphService.retrieveDraftEntityGraph(graphDTO.getTargetId()).get();
        PipelineTest test = pipelineTestService.getNewTestInstanceForGraph(graph, Instant.EPOCH, Instant.now(), 2, new HashMap<>(), Status.READY, null);
        TestResult testResult = createEntityLiveTestResult(graphDTO, graph, test);

        var result = mvc.perform(
                get("/api/v1/test/entityPipeline/{graphId}/test", graph.getId())
                        .contentType(MediaType.APPLICATION_JSON)
        ).andReturn();

        var liveTestResults = (List<TestRunDTO>)mapper.readValue(result.getResponse().getContentAsString(), new TypeReference<List<TestRunDTO>>(){});
        assertEquals(liveTestResults.get(0).getTestNames().size(), 1);
        
        assertTrue(liveTestResults.get(0).getTestNames().get(0)
            .equalsIgnoreCase(new DateUtil().format(test.getCreatedAt(), DateUtil.dateFormat1)));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void getEntityLiveTestResults() throws Exception {
        var graphDTO = getEntityGraph();
        MappingGraph graph =  graphService.retrieveDraftEntityGraph(graphDTO.getTargetId()).get();
        PipelineTest test = pipelineTestService.getNewTestInstanceForGraph(graph, Instant.EPOCH, Instant.now(), 2, new HashMap<>(), Status.READY, null);
        
        TestResult testResult = createEntityLiveTestResultWithNullValue(graphDTO, graph, test);

        var result = mvc.perform(
                get("/api/v1/test/entityPipeline/{graphId}/test/{pipelineTestId}", graph.getId(), testResult.getPipelineTestId())
                        .contentType(MediaType.APPLICATION_JSON)
        ).andReturn();

        var liveTestRunResults = mapper.readValue(result.getResponse().getContentAsString(), PipelineTestRunResultsDTO.class);
        assertEquals(liveTestRunResults.getResultDetails().size(), 1);
        assertTrue(liveTestRunResults.getResultDetails().get(0).getDisplayName()
            .equalsIgnoreCase(new DateUtil().format(test.getCreatedAt(), DateUtil.dateFormat1)));
        PipelineTestNodeData actualResult = liveTestRunResults.getResultDetails().get(0)
            .getNodes().get(0).getTestData().getActualResult().get(0);
        assertNotNull(actualResult);
    }

    // Create a field test test
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void createFieldTest() throws Exception {
        var graph =  createFieldPipeline();
        var srcNode = graph.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ATTRIBUTE_SOURCE).findFirst().get();
        var sinkNode = graph.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ATTRIBUTE_SINK).findFirst().get();

        var testSimulation = createFieldTestSimulationInstance(graph);

        var result = mvc.perform(
                post("/api/v1/test/fieldPipeline/{targetId}", graph.getTargetId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(testSimulation))
        ).andReturn();

        var createFieldTestSimulationResult = mapper.readValue(result.getResponse().getContentAsString(), PipelineTestDTO.class);

        assertTrue(createFieldTestSimulationResult.getDisplayName().equalsIgnoreCase("fieldTest1Name"));
        assertTrue(createFieldTestSimulationResult.getOwnerFirstName().equalsIgnoreCase("Syncari"));
        assertTrue(createFieldTestSimulationResult.getOwnerLastName().equalsIgnoreCase("Admin"));
        assertTrue(createFieldTestSimulationResult.getOwnerEmail().equalsIgnoreCase("admin@syncari.com"));
        assertTrue(createFieldTestSimulationResult.getDescription().equalsIgnoreCase("fieldTest1Name Description"));
        assertEquals(createFieldTestSimulationResult.getTags().size(), 1);
        assertTrue(createFieldTestSimulationResult.getTags().contains("fieldTestTag1"));
        assertFalse(createFieldTestSimulationResult.getTestData().getInput().isEmpty());
        assertEquals(srcNode.getName(),createFieldTestSimulationResult.getTestData().getInput().get(0).getNodeName());
        assertFalse(createFieldTestSimulationResult.getTestData().getExpectedResult().isEmpty());
        assertEquals(sinkNode.getName(),createFieldTestSimulationResult.getTestData().getExpectedResult().get(0).getNodeName());
    }

    // Get list of list of field tests test
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void getFieldTests() throws Exception {
        var graph = createFieldPipeline();
        PipelineTest test = createFieldTestSimulation(graph);

        var result = mvc.perform(
                get("/api/v1/test/fieldPipeline/{targetId}", graph.getTargetId())
                        .contentType(MediaType.APPLICATION_JSON)
        ).andReturn();

        var listOfTests = (List<PipelineTestDTO>)mapper.readValue(result.getResponse().getContentAsString(), new TypeReference<List<PipelineTestDTO>>(){});
        assertEquals(listOfTests.size(), 1);
        assertTrue(listOfTests.get(0).getDisplayName().equalsIgnoreCase("fieldTest1Name"));
        assertTrue(listOfTests.get(0).getDescription().equalsIgnoreCase("fieldTest1Name Description"));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void getFieldTest() throws Exception {
        var graph =  createFieldPipeline();
        PipelineTest test = createFieldTestSimulation(graph);

        var result = mvc.perform(
                get("/api/v1/test/fieldPipeline/{targetId}/testId/{testId}", graph.getTargetId(), test.getId())
                        .contentType(MediaType.APPLICATION_JSON)
        ).andReturn();

        var testResult = mapper.readValue(result.getResponse().getContentAsString(), PipelineTestDTO.class);
        assertTrue(testResult.getDisplayName().equalsIgnoreCase("fieldTest1Name"));
        assertTrue(testResult.getDescription().equalsIgnoreCase("fieldTest1Name Description"));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void deleteFieldTest() throws Exception {
        var graph =  createFieldPipeline();
        PipelineTest test = createFieldTestSimulation(graph);

        var result = mvc.perform(
                delete("/api/v1/test/fieldPipeline/{targetId}/testId/{testId}", graph.getTargetId(), test.getId())
                        .contentType(MediaType.APPLICATION_JSON)
        ).andReturn();

        var testResult = mapper.readValue(result.getResponse().getContentAsString(), PipelineTestDTO.class);
        assertTrue(testResult.getDisplayName().equalsIgnoreCase("fieldTest1Name"));
        assertTrue(testResult.getDescription().equalsIgnoreCase("fieldTest1Name Description"));

        result = mvc.perform(
                get("/api/v1/test/fieldPipeline/{targetId}/testId/{testId}", graph.getTargetId(), test.getId())
                        .contentType(MediaType.APPLICATION_JSON)
        ).andReturn();
        assertTrue(mapper.readValue(result.getResponse().getContentAsString(), ResourceNotFoundException.class).getMessage().contains("not found"));
    }

    // Run Field Test test
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void runFieldTests() throws Exception {
        var graph = createFieldPipeline();
        PipelineTest test = createFieldTestSimulation(graph);

        SimulationRunRequest runRequest = new SimulationRunRequest();
        runRequest.setName("fieldTestRun1");
        runRequest.setTestIds(List.of(test.getId()));
        var result = mvc.perform(
                post("/api/v1/test/fieldPipeline/{targetId}/run", graph.getTargetId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(runRequest))
        ).andReturn();

        var simulationTestRun = (TestRunDTO)mapper.readValue(result.getResponse().getContentAsString(), TestRunDTO.class);
        assertEquals(simulationTestRun.getTestNames().size(), 1);
        assertEquals(simulationTestRun.getTestNames().get(0), "fieldTest1Name");
        assertEquals(simulationTestRun.getRunName(), "fieldTestRun1");
    }

    // Get the list of entity test runs
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void getFieldTestRuns() throws Exception {
        var graph = createFieldPipeline();
        PipelineTest test = createFieldTestSimulation(graph);
        TestResult testResult = createEntityTestResult(graph, graphService.retrieveDraftAttributeGraph(test.getTargetId()).get(), test);
        var result = mvc.perform(
                get("/api/v1/test/fieldPipeline/{targetId}/run", graph.getTargetId())
                        .contentType(MediaType.APPLICATION_JSON)
        ).andReturn();

        var simulationTestRuns = (List<TestRunDTO>)mapper.readValue(result.getResponse().getContentAsString(), new TypeReference<List<TestRunDTO>>(){});
        assertEquals(simulationTestRuns.get(0).getTestNames().size(), 1);
        assertTrue(simulationTestRuns.get(0).getTestNames().get(0).equalsIgnoreCase("fieldTest1Name"));
    }

    // Get a field test by id
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void getFieldTestRunById() throws Exception {
        var graph = createFieldPipeline();
        PipelineTest test = createFieldTestSimulation(graph);
        TestResult testResult = createEntityTestResult(graph, graphService.retrieveDraftAttributeGraph(test.getTargetId()).get(), test);

        var result = mvc.perform(
                get("/api/v1/test/fieldPipeline/{targetId}/run/{runId}", graph.getTargetId(), testResult.getSimulationRunId())
                        .contentType(MediaType.APPLICATION_JSON)
        ).andReturn();

        var simulationTestRunResults = mapper.readValue(result.getResponse().getContentAsString(), PipelineTestRunResultsDTO.class);
        assertEquals(simulationTestRunResults.getResultDetails().size(), 1);
        assertTrue(simulationTestRunResults.getResultDetails().get(0).getDisplayName().equalsIgnoreCase("fieldTest1Name"));
    }

    // Get a field latest test test
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void getFieldTestRunLatest() throws Exception {
        var graph = createFieldPipeline();
        PipelineTest test = createFieldTestSimulation(graph);
        TestResult testResult = createEntityTestResult(graph, graphService.retrieveDraftAttributeGraph(test.getTargetId()).get(), test);

        var result = mvc.perform(
                get("/api/v1/test/fieldPipeline/{targetId}/run/{runId}", graph.getTargetId(), "latest")
                        .contentType(MediaType.APPLICATION_JSON)
        ).andReturn();

        var simulationTestRunResults = mapper.readValue(result.getResponse().getContentAsString(), PipelineTestRunResultsDTO.class);
        assertEquals(simulationTestRunResults.getResultDetails().size(), 1);
        assertTrue(simulationTestRunResults.getResultDetails().get(0).getDisplayName().equalsIgnoreCase("fieldTest1Name"));
    }

    // Get a field latest test with no runs test
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void getFieldTestRunNoRuns() throws Exception {
        var graph = createFieldPipeline();

        var result = mvc.perform(
                get("/api/v1/test/fieldPipeline/{targetId}/run/{runId}", graph.getTargetId(), "latest")
                        .contentType(MediaType.APPLICATION_JSON)
        ).andReturn();

        assertEquals(result.getResponse().getContentLength(), 0);
        assertTrue(result.getResponse().getContentAsString().isEmpty());
    }

    private MappingGraphDTO createFieldPipeline() {
        EntityDef entityDef = schemaService.getSyncariSchema().findEntityByName("account").get();
        AttributeDef attributeDef = entityDef.getField("Name").get();
        var entityGraph = pipelineController.getEntityPipeline(entityDef.getId());
        setDefaultValueOnSinkNode(attributeDef);
        var graph = pipelineController.getFieldPipeline(attributeDef.getId());
        MappingGraphDTO graph1 = (MappingGraphDTO) pipelineController.createFieldPipeline(attributeDef.getId(), graph.setName("Dummy"));
        return graph1;
    }

    private void setDefaultValueOnSinkNode(AttributeDef attributeDef) {
        MappingGraph nameGraph = graphService.retrieveAttributeGraph(attributeDef.getId()).orElseGet(()-> graphService.createDefaultAttributeGraph(attributeDef.getId()));
        nameGraph.getSinks().forEach(sink -> {
            ((AttributeSinkNodeConfig) sink.getConfiguration()).setDefaultValue("Default");
            graphService.upsertAttributeGraph(nameGraph);
        });
    }

    private PipelineTestDTO createFieldTestSimulationInstance(MappingGraphDTO graph) {
        var attributeSource = graph.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ATTRIBUTE_SOURCE).findFirst().get();
        var attributeSink = graph.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ATTRIBUTE_SINK).findFirst().get();

        var testSimulation = new PipelineTestDTO().setOwnerEmail("admin@syncari.com").setDisplayName("fieldTest1Name").setDescription("fieldTest1Name Description");
        var input = new PipelineTestNodeData().setNodeId(attributeSource.getId()).setNodeName(attributeSource.getName())
                .setApiName("Name").setValue("name");
        var inputs = new ArrayList<PipelineTestNodeData>();
        inputs.add(input);

        var expectedResult = new PipelineTestNodeData().setNodeId(attributeSink.getId()).setNodeName(attributeSink.getName())
                .setApiName("Name").setValue("name");
        var expectedResults = new ArrayList<PipelineTestNodeData>();
        expectedResults.add(expectedResult);

        var testData = new PipelineTestData().setInput(inputs).setExpectedResult(expectedResults);
        testSimulation.setTestData(testData);
        testSimulation.setTags(Set.of("fieldTestTag1"));
        return testSimulation;
    }

    private PipelineTest createFieldTestSimulation(MappingGraphDTO graph) {
        var testSimulation = createFieldTestSimulationInstance(graph);
        return simulationService.createFieldTest(transformer.toPipelineTest(testSimulation, Scope.ATTRIBUTE, graph.getTargetId()));
    }

    private PipelineTestDTO createEntityTestSimulationInstance(MappingGraphDTO graph) {
        var entitySource = graph.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ENTITY_SOURCE).findFirst().get();
        var entitySink = graph.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ENTITY_SINK).findFirst().get();

        var testSimulation = new PipelineTestDTO().setOwnerEmail("admin@syncari.com").setDisplayName("test1Name").setDescription("test1Name Description");
        var input = new PipelineTestNodeData().setNodeId(entitySource.getId()).setNodeName(entitySource.getName())
                .setApiName("website").setValue("city");
        var inputs = new ArrayList<PipelineTestNodeData>();
        inputs.add(input);

        var expectedResult = new PipelineTestNodeData().setNodeId(entitySink.getId()).setNodeName(entitySink.getName())
                .setApiName("website").setValue("city");
        var expectedResults = new ArrayList<PipelineTestNodeData>();
        expectedResults.add(expectedResult);

        var testData = new PipelineTestData().setInput(inputs).setExpectedResult(expectedResults);
        testSimulation.setTestData(testData);
        testSimulation.setTags(Set.of("entityTestTag1"));
        return testSimulation;
    }

    private PipelineTest createEntityTestSimulation(MappingGraphDTO graph) {
        var testSimulation = createEntityTestSimulationInstance(graph);
        return simulationService.createEntityTest(transformer.toPipelineTest(testSimulation, Scope.ENTITY, graph.getTargetId()));
    }

    private TestResult createEntityTestResult(MappingGraphDTO graph, MappingGraph draft, PipelineTest test) {
        List<PipelineTest> tests = new ArrayList<>();
        tests.add(test);

        // Create an simulation run entry
        SimulationMappingGraph simGraph = new SimulationMappingGraph().createSimulationMappingGraph(draft);
        SimulationRun simulationRun = simulationRunRepo.save(new SimulationRun()
                .setName(ZonedDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy - HH:mm:ss Z")))
                .setTargetId(graph.getTargetId())
                .setGraph(simGraph)
                .setExecutedAt(ZonedDateTime.now())
        );

        TestResult runResult = new TestResult();
        runResult.setSimulationRunId(simulationRun.getId());
        runResult.setStatus(PipelineTestStatus.success);
        runResult.setTest(test);

        List<TestNodeResult> nodeResults = new ArrayList<>();
        List<MappingNode> nodes = draft.getNodes();
        nodes.stream().forEach(node -> {
            Map<String, TestNodeResultAttributeValue> outputs = new HashMap<>();
            Map<String, TestNodeResultAttributeValue> nodeInputs = new HashMap<>();
            List<AttributeDefinition> attr = graphService.getInputFieldsForAttributeNode(graph.getTargetId(), node.getId());
            attr.forEach(a -> {

                TestNodeResultAttributeValue nodeInput = new TestNodeResultAttributeValue()
                        .setApiName(a.getApiName())
                        .setDataType(a.getDataType().getName())
                        .setDisplayName(a.getDisplayName())
                        .setValue(RandomStringUtils.random(6, true, true));
                nodeInputs.put(a.getApiName(), nodeInput);

                TestNodeResultAttributeValue output = new TestNodeResultAttributeValue()
                        .setApiName(a.getApiName())
                        .setDataType(a.getDataType().getName())
                        .setDisplayName(a.getDisplayName())
                        .setValue(RandomStringUtils.random(6, true, true));
                outputs.put(a.getApiName(), output);
            });

            nodeResults.add(new TestNodeResult()
                    .setNodeId(node.getId())
                    .setNodeName(node.getName())
                    .setInputs(nodeInputs)
                    .setOutputs(outputs)
                    // TODO: Set the node inputs and outputs
                    //.setStatus(PipelineTestRunState.completed)
                    .setStatus(TestNodeResult.Status.COMPLETED));
        });
        runResult.setNodeResults(nodeResults);
        return testResultRepo.save(runResult);
    }

    private TestResult createEntityTestResult(MappingGraphDTO graph, PipelineTest test) {
        return createEntityTestResult(graph, graphService.retrieveDraftEntityGraph(test.getTargetId()).get(), test);
    }

    private MappingGraphDTO getEntityGraph() {
        if(entityGraph == null) {
            EntityDef entityDef = schemaService.getSyncariSchema().findEntityByName("account").get();
            entityGraph = pipelineController.getEntityPipeline(entityDef.getId());
        }
        return entityGraph;
    }

    private PipelineTestDTO createEntityTestSimulationInstanceWithNull(MappingGraphDTO graph) {
        var entitySource = graph.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ENTITY_SOURCE).findFirst().get();
        var entitySink = graph.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ENTITY_SINK).findFirst().get();

        var testSimulation = new PipelineTestDTO().setOwnerEmail("admin@syncari.com").setDisplayName("test1Name").setDescription("test1Name Description");
        var input = new PipelineTestNodeData().setNodeId(entitySource.getId()).setNodeName(entitySource.getName())
                .setApiName("phone").setValue(null);
        var inputs = new ArrayList<PipelineTestNodeData>();
        inputs.add(input);

        var expectedResult = new PipelineTestNodeData().setNodeId(entitySink.getId()).setNodeName(entitySink.getName())
                .setApiName("phone").setValue(null);
        var expectedResults = new ArrayList<PipelineTestNodeData>();
        expectedResults.add(expectedResult);

        var testData = new PipelineTestData().setInput(inputs).setExpectedResult(expectedResults);
        testSimulation.setTestData(testData);
        testSimulation.setTags(Set.of("entityTestTag1"));
        return testSimulation;
    }

    private PipelineTestDTO createEntityTestSimulationInstanceWithDifferentExpectedOutput(MappingGraphDTO graph) {
        var entitySource = graph.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ENTITY_SOURCE).findFirst().get();
        var entitySink = graph.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ENTITY_SINK).findFirst().get();

        var testSimulation = new PipelineTestDTO().setOwnerEmail("admin@syncari.com").setDisplayName("test1Name").setDescription("test1Name Description");
        var input = new PipelineTestNodeData().setNodeId(entitySource.getId()).setNodeName(entitySource.getName())
                .setApiName("phone").setValue(null);
        var inputs = new ArrayList<PipelineTestNodeData>();
        inputs.add(input);

        var expectedResult = new PipelineTestNodeData().setNodeId(entitySink.getId()).setNodeName(entitySink.getName())
                .setApiName("Name").setValue(null);
        var expectedResults = new ArrayList<PipelineTestNodeData>();
        expectedResults.add(expectedResult);

        var testData = new PipelineTestData().setInput(inputs).setExpectedResult(expectedResults);
        testSimulation.setTestData(testData);
        testSimulation.setTags(Set.of("entityTestTag1"));
        return testSimulation;
    }

    private PipelineTestDTO createEntityTestSimulationWithMismatchingOutput(MappingGraphDTO graph) {
        var entitySource = graph.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ENTITY_SOURCE).findFirst().get();
        var entitySink = graph.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ENTITY_SINK).findFirst().get();

        var testSimulation = new PipelineTestDTO().setOwnerEmail("admin@syncari.com").setDisplayName("test1Name").setDescription("test1Name Description");
        var input = new PipelineTestNodeData().setNodeId(entitySource.getId()).setNodeName(entitySource.getName())
                .setApiName("phone").setValue(null);
        var inputs = new ArrayList<PipelineTestNodeData>();
        inputs.add(input);

        var expectedResult = new PipelineTestNodeData().setNodeId(entitySink.getId()).setNodeName(entitySink.getName())
                .setApiName("phone").setValue("03/03/2021");
        var expectedResults = new ArrayList<PipelineTestNodeData>();
        expectedResults.add(expectedResult);

        var testData = new PipelineTestData().setInput(inputs).setExpectedResult(expectedResults);
        testSimulation.setTestData(testData);
        testSimulation.setTags(Set.of("entityTestTag1"));
        return testSimulation;
    }

    private TestResult createEntityTestResultWithNullValue(MappingGraphDTO graph, MappingGraph draft, PipelineTest test) {
        List<PipelineTest> tests = new ArrayList<>();
        tests.add(test);

        // Create an simulation run entry
        SimulationMappingGraph simGraph = new SimulationMappingGraph().createSimulationMappingGraph(draft);
        SimulationRun simulationRun = simulationRunRepo.save(new SimulationRun()
                .setName(ZonedDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy - HH:mm:ss Z")))
                .setTargetId(graph.getTargetId())
                .setGraph(simGraph)
                .setExecutedAt(ZonedDateTime.now())
        );

        TestResult runResult = new TestResult();
        runResult.setSimulationRunId(simulationRun.getId());
        runResult.setStatus(PipelineTestStatus.success);
        runResult.setTest(test);

        List<TestNodeResult> nodeResults = new ArrayList<>();
        List<MappingNode> nodes = draft.getNodes();
        nodes.stream().forEach(node -> {
            Map<String, TestNodeResultAttributeValue> outputs = new HashMap<>();
            Map<String, TestNodeResultAttributeValue> nodeInputs = new HashMap<>();
            TestNodeResultAttributeValue nodeInput = new TestNodeResultAttributeValue()
                    .setApiName("CreatedDate")
                    .setDataType("datetime")
                    .setDisplayName("Created Date")
                    .setValue(null);
            nodeInputs.put("CreatedDate", nodeInput);

            TestNodeResultAttributeValue output = new TestNodeResultAttributeValue()
                    .setApiName("CreatedDate")
                    .setDataType("datetime")
                    .setDisplayName("Created Date")
                    .setValue(null);
            outputs.put("CreatedDate", output);

            nodeResults.add(new TestNodeResult()
                    .setNodeId(node.getId())
                    .setNodeName(node.getName())
                    .setInputs(nodeInputs)
                    .setOutputs(outputs)
                    .setStatus(TestNodeResult.Status.COMPLETED)
                    .setErrorMsg("test error message"));
        });
        runResult.setNodeResults(nodeResults);
        return testResultRepo.save(runResult);
    }

    private TestResult createEntityLiveTestResult(MappingGraphDTO graph, MappingGraph draft, PipelineTest test) {
        List<PipelineTest> tests = new ArrayList<>();
        tests.add(test);

        TestResult runResult = new TestResult();
        runResult.setPipelineTestId(test.getId());
        runResult.setStatus(PipelineTestStatus.success);
        runResult.setTest(test);

        List<TestNodeResult> nodeResults = new ArrayList<>();
        List<MappingNode> nodes = draft.getNodes();
        nodes.stream().forEach(node -> {
            Map<String, TestNodeResultAttributeValue> outputs = new HashMap<>();
            Map<String, TestNodeResultAttributeValue> nodeInputs = new HashMap<>();
            List<AttributeDefinition> attr = graphService.getInputFieldsForAttributeNode(graph.getTargetId(), node.getId());
            attr.forEach(a -> {

                TestNodeResultAttributeValue nodeInput = new TestNodeResultAttributeValue()
                        .setApiName(a.getApiName())
                        .setDataType(a.getDataType().getName())
                        .setDisplayName(a.getDisplayName())
                        .setValue(RandomStringUtils.random(6, true, true));
                nodeInputs.put(a.getApiName(), nodeInput);

                TestNodeResultAttributeValue output = new TestNodeResultAttributeValue()
                        .setApiName(a.getApiName())
                        .setDataType(a.getDataType().getName())
                        .setDisplayName(a.getDisplayName())
                        .setValue(RandomStringUtils.random(6, true, true));
                outputs.put(a.getApiName(), output);
            });

            nodeResults.add(new TestNodeResult()
                    .setNodeId(node.getId())
                    .setNodeName(node.getName())
                    .setInputs(nodeInputs)
                    .setOutputs(outputs)
                    // TODO: Set the node inputs and outputs
                    //.setStatus(PipelineTestRunState.completed)
                    .setStatus(TestNodeResult.Status.COMPLETED));
        });
        runResult.setNodeResults(nodeResults);
        return testResultRepo.save(runResult);
    }


    private TestResult createEntityLiveTestResultWithNullValue(MappingGraphDTO graph, MappingGraph draft, PipelineTest test) {
        List<PipelineTest> tests = new ArrayList<>();
        tests.add(test);

        TestResult runResult = new TestResult();
        runResult.setPipelineTestId(test.getId());
        runResult.setStatus(PipelineTestStatus.success);
        runResult.setTest(test);

        List<TestNodeResult> nodeResults = new ArrayList<>();
        List<MappingNode> nodes = draft.getNodes();
        nodes.stream().forEach(node -> {
            Map<String, TestNodeResultAttributeValue> outputs = new HashMap<>();
            Map<String, TestNodeResultAttributeValue> nodeInputs = new HashMap<>();
            TestNodeResultAttributeValue nodeInput = new TestNodeResultAttributeValue()
                    .setApiName("CreatedDate")
                    .setDataType("datetime")
                    .setDisplayName("Created Date")
                    .setValue(null);
            nodeInputs.put("CreatedDate", nodeInput);

            TestNodeResultAttributeValue output = new TestNodeResultAttributeValue()
                    .setApiName("CreatedDate")
                    .setDataType("datetime")
                    .setDisplayName("Created Date")
                    .setValue(null);
            outputs.put("CreatedDate", output);

            nodeResults.add(new TestNodeResult()
                    .setNodeId(node.getId())
                    .setNodeName(node.getName())
                    .setInputs(nodeInputs)
                    .setOutputs(outputs)
                    .setStatus(TestNodeResult.Status.COMPLETED));
        });
        runResult.setNodeResults(nodeResults);
        return testResultRepo.save(runResult);
    }
}
