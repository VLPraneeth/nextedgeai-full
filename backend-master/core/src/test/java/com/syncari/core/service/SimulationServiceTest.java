package com.syncari.core.service;

import com.syncari.connector.Constants;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.EndSystemConfig;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.BooleanType;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.core.model.CoreEntityNodeConfig;
import com.syncari.core.model.Edge;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.EntitySourceNodeConfig;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.PipelineTest;
import com.syncari.core.model.misc.test.SimulationNodeInput;
import com.syncari.core.model.misc.test.TestConfig;
import com.syncari.core.model.util.Scope;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.repositories.customer.MappingGraphRepo;
import com.syncari.core.repositories.customer.PipelineTestRepo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class SimulationServiceTest extends AbstractSyncariTest {

    @Autowired
    SimulationService simulationService;

    @Autowired
    PipelineTestRepo pipelineTestRepo;

    @Autowired
    FunctionService functionService;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    EndSystemConfig config;

    @Autowired
    EntityDefinitionRepo entityProxyRepo;

    @Autowired
    MappingGraphService mappingGraphService;

    @Autowired
    MappingGraphRepo mappingGraphRepo;

    @Autowired
    SchemaService schemaService;

    @Autowired
    TagService tagService;

    Connector connector;

    @Before
    public void setUp() {
        super.setUp();
    }

    @After
    public void tearDown() {
        resetRepos(pipelineTestRepo);
        super.tearDown();
    }

    @Test
    public void createAndUpdateEntityTest(){

        SimulationService service = spy(SimulationService.class);
        doNothing().when(service).validate(any(PipelineTest.class));
        doNothing().when(service).fixTestInputDataType(any(), any());
        service.pipelineTestRepo = pipelineTestRepo;
        service.tagService = tagService;

        MappingGraphService mockGraphService = mock(MappingGraphService.class);
        doReturn(Optional.of(new MappingGraph())).when(mockGraphService).retrieveDraftEntityGraph(anyString());
        service.graphService = mockGraphService;
        // create test
        PipelineTest test = getEntityTest();
        var savedTest = service.createEntityTest(test);
        assertNotNull(savedTest);
        assertNotNull(savedTest.getId());

        // retrieve test
        var retrievedTest = service.getEntityTest(savedTest.getTargetId(), savedTest.getId());
        assertEquals(savedTest.getId(), retrievedTest.getId());
        assertEquals(savedTest, retrievedTest);

        // update test
        retrievedTest.setName("Updated Entity Test 123");
        var updatedTest = service.updateEntityTest(retrievedTest.getId(), retrievedTest);
        assertNotEquals(savedTest.getName(), updatedTest.getName());
        assertEquals("Updated Entity Test 123", updatedTest.getName());
    }

    @Test
    public void createAndUpdateFieldTest(){

        SimulationService service = spy(SimulationService.class);
        doNothing().when(service).validate(any(PipelineTest.class));
        doNothing().when(service).fixTestInputDataType(any(), any());
        service.pipelineTestRepo = pipelineTestRepo;
        service.tagService = tagService;
        MappingGraphService mockGraphService = mock(MappingGraphService.class);
        doReturn(Optional.of(new MappingGraph())).when(mockGraphService).retrieveDraftAttributeGraph(anyString());
        service.graphService = mockGraphService;

        // create test
        PipelineTest test = getFieldTest();
        var savedTest = service.createFieldTest(test);
        assertNotNull(savedTest);
        assertNotNull(savedTest.getId());

        // retrieve test
        var retrievedTest = service.getFieldTest(savedTest.getTargetId(), savedTest.getId());
        assertEquals(savedTest.getId(), retrievedTest.getId());
        assertEquals(savedTest, retrievedTest);

        // update test
        retrievedTest.setName("Updated Entity Test 123");
        var updatedTest = service.updateFieldTest(retrievedTest.getId(), retrievedTest);
        assertNotEquals(savedTest.getName(), updatedTest.getName());
        assertEquals("Updated Entity Test 123", updatedTest.getName());
    }

    @Test
    public void createEntityTest_WithConvertedDataType(){

        EntityDefinition srcEntity = new EntityDefinition("srcEntity", "Source Entity");
        srcEntity.setId("srcEntity");
        AttributeDefinition srcAttribute1 = new AttributeDefinition().setApiName("srcAttrib1").setDisplayName("Source Attribute1").setDataType(new IntegerType());
        srcAttribute1.setId("srcAttrib1");
        AttributeDefinition srcAttribute2 = new AttributeDefinition().setApiName("srcAttrib2").setDisplayName("Source Attribute2").setDataType(new BooleanType());
        srcAttribute2.setId("srcAttrib2");
        srcEntity.setAttributes(List.of(srcAttribute1, srcAttribute2));

        EntityDefinition syncariEntity = new EntityDefinition("syncariEntity", "Syncari Entity");
        syncariEntity.setId("syncariEntity");
        AttributeDefinition coreAttribute1 = new AttributeDefinition().setApiName("coreAttribute1").setDisplayName("Core Attribute1").setDataType(new IntegerType());
        srcAttribute1.setId("coreAttribute1");
        syncariEntity.setAttributes(List.of(coreAttribute1));

        // create entity graph
        MappingGraph graph = new MappingGraph();
        graph.setTargetId(syncariEntity.getId()).setName("graph1").setScope(Scope.ENTITY);
        graph.setDraftStatus(DraftStatus.NEW);
        graph.setId("graph1");

        CoreEntityNodeConfig coreEntityNodeConfig = new CoreEntityNodeConfig().setEntityDefinition(syncariEntity);
        EntitySourceNodeConfig srcEntityNodeConfig = new EntitySourceNodeConfig().setEntityDefinition(srcEntity);
        MappingNode srcNode = new MappingNode().setName("srcNode").setScope(Scope.ENTITY).setApiName(srcEntity.getApiName())
                .setMappingGraphId(graph.getId()).setConfiguration(srcEntityNodeConfig);
        srcNode.setId("srcNode");
        MappingNode coreNode = new MappingNode().setName("coreNode").setScope(Scope.ENTITY).setApiName(syncariEntity.getApiName())
                .setMappingGraphId(graph.getId()).setConfiguration(coreEntityNodeConfig);
        coreNode.setId("coreNode");
        Edge edge = new Edge().setSourceStage(srcNode).setDestinationStage(coreNode).setGraphId(graph.getId());
        edge.setId("edge");

        graph.setNodes(List.of(srcNode, coreNode));
        graph.setEdges(List.of(edge));

        // create test
        PipelineTest test = new PipelineTest();
        test.setTargetId(syncariEntity.getId());
        test.setUserId(SyncariContext.getUser().getId());
        test.setScope(Scope.ENTITY);
        test.setName("Entity Test 123");
        test.setDescription("Field Test Description");
        TestConfig testConfig = new TestConfig();
        Map<String, Object> input = new HashMap<>();
        input.put(srcAttribute1.getApiName(), "123");
        input.put(srcAttribute2.getApiName(), "true");

        Map<String, Object> output = new HashMap<>();
        output.put(coreAttribute1.getApiName(), "123");
        SimulationNodeInput input1 = new SimulationNodeInput()
                .setNodeId(srcNode.getId())
                .setFieldValues(input);
        SimulationNodeInput input2 = new SimulationNodeInput()
                .setNodeId(coreNode.getId())
                .setFieldValues(output);
        testConfig.setInputs(List.of(input1));
        testConfig.setExpectedOutputs(List.of(input2));
        test.setTestConfig(testConfig);

        var orgGraphService = simulationService.graphService;
        var orgScheamService = simulationService.schemaService;
        try{
            MappingGraphService mockGraphService = mock(MappingGraphService.class);
            SchemaService mockSchemaService = mock(SchemaService.class);

            doReturn(Optional.of(graph)).when(mockGraphService).retrieveDraftEntityGraph(graph.getTargetId());
            doReturn(srcEntity).when(mockSchemaService).getEntity(srcEntity.getId());
            doReturn(syncariEntity).when(mockSchemaService).getEntity(syncariEntity.getId());
            doReturn(srcEntity).when(mockGraphService).extractEntityFromNode(srcNode);
            doReturn(syncariEntity).when(mockGraphService).extractEntityFromNode(coreNode);

            simulationService.graphService = mockGraphService;
            simulationService.schemaService = mockSchemaService;

            assertEquals("123", test.getTestConfig().getInputs().get(0).getFieldValues().get(srcAttribute1.getApiName()));
            assertEquals("true", test.getTestConfig().getInputs().get(0).getFieldValues().get(srcAttribute2.getApiName()));
            assertEquals("123", test.getTestConfig().getExpectedOutputs().get(0).getFieldValues().get(coreAttribute1.getApiName()));

            PipelineTest savedTest = simulationService.createEntityTest(test);

            assertEquals(123l, savedTest.getTestConfig().getInputs().get(0).getFieldValues().get(srcAttribute1.getApiName()));
            assertEquals(true, savedTest.getTestConfig().getInputs().get(0).getFieldValues().get(srcAttribute2.getApiName()));

            assertEquals(123l, savedTest.getTestConfig().getExpectedOutputs().get(0).getFieldValues().get(coreAttribute1.getApiName()));

            PipelineTest retrievedTest = simulationService.getEntityTest(syncariEntity.getId(), savedTest.getId());
            assertEquals(123l, retrievedTest.getTestConfig().getInputs().get(0).getFieldValues().get(srcAttribute1.getApiName()));
            assertEquals(true, retrievedTest.getTestConfig().getInputs().get(0).getFieldValues().get(srcAttribute2.getApiName()));

            assertEquals(123l, retrievedTest.getTestConfig().getExpectedOutputs().get(0).getFieldValues().get(coreAttribute1.getApiName()));

        } finally {
            simulationService.graphService = orgGraphService;
            simulationService.schemaService = orgScheamService;
        }
    }

    @Test
    public void validateEntityTest_NoDraftGraph(){

        PipelineTest test = getEntityTest();
        try {
            simulationService.validate(test);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("No ENTITY graph is found for the test Entity Test 123", e.getMessage());
        }
    }

    @Test
    public void validateAttributeTest_NoDraftGraph(){

        PipelineTest test = getFieldTest();
        try {
            simulationService.validate(test);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("No ATTRIBUTE graph is found for the test Attribute Test 123", e.getMessage());
        }
    }

    @Test
    public void validateAttributeTest_NullInputValue(){

        Connector sfdcConnector = getConnector();
        EntityDefinition syncariEntity = schemaService.findEntity(connectorService.findSyncariConnector().getId(), "account").get();
        EntityDefinition sfdcEntity = schemaService.findEntity(sfdcConnector.getId(),"account").get();
        AttributeDefinition syncariAttribute = syncariEntity.getFieldByName("Name");
        AttributeDefinition sfdcAttribute = sfdcEntity.getFieldByName("name");
        MappingGraph attribGraph = mappingGraphService.retrieveDraftAttributeGraph(syncariAttribute.getId()).get();
        assertEquals(DraftStatus.NEW, attribGraph.getDraftStatus());
        attribGraph = mappingGraphRepo.save(attribGraph);

        assertNotNull(attribGraph.getSource(sfdcEntity.getId()));
        assertNotNull(attribGraph.getSink(sfdcEntity.getId()));

        MappingNode sourceNode = attribGraph.getSource(sfdcAttribute.getId()).get(0);
        MappingNode sinkNode = attribGraph.getSink(sfdcAttribute.getId()).get(0);

        PipelineTest test = getFieldTest();
        test.setTargetId(syncariAttribute.getId());
        var nullInputs = new HashMap<String, Object>();
        nullInputs.put(sfdcAttribute.getApiName(), null);
        SimulationNodeInput input1 = new SimulationNodeInput()
                .setNodeId(sourceNode.getId())
                .setFieldValues(nullInputs);
        test.getTestConfig().setInputs(List.of(input1));

        SimulationNodeInput expectedOutput1 = new SimulationNodeInput()
                .setNodeId(sinkNode.getId())
                .setFieldValues(nullInputs);
        test.getTestConfig().setExpectedOutputs(List.of(expectedOutput1));

        // test with null inputs are are validated successfully
        simulationService.validate(test);

    }

    @Test
    public void validateEntityTest(){

        Connector sfdcConnector = getConnector();
        EntityDefinition syncariEntity = entityProxyRepo
                .findByConnectorId(connectorService.findSyncariConnector().getId()).get(0);
        EntityDefinition sfdcEntity = schemaService.findEntity(sfdcConnector.getId(),"account").get();
        MappingGraph entityGraph = mappingGraphService.retrieveDraftEntityGraph(syncariEntity.getId()).get();
        assertEquals(DraftStatus.NEW, entityGraph.getDraftStatus());
        entityGraph = mappingGraphRepo.save(entityGraph);

        assertNotNull(entityGraph.getSource(sfdcEntity.getId()));
        assertNotNull(entityGraph.getSink(sfdcEntity.getId()));


        // create test without targetId
        PipelineTest test = new PipelineTest();
        try {
            simulationService.validate(test);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing Target Id in test", e.getMessage());
        }

        // create test without valid scope
        test.setTargetId(syncariEntity.getId());
        test.setScope(Scope.SCHEMA);
        try {
            simulationService.validate(test);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Test Scope SCHEMA", e.getMessage());
        }

        // null testConfig
        test.setScope(Scope.ENTITY);
        test.setUserId(SyncariContext.getUser().getId());
        test.setName("Entity Test 123");
        try {
            simulationService.validate(test);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing test config", e.getMessage());
        }

        // No input in testConfig
        TestConfig testConfig = new TestConfig();
        test.setTestConfig(testConfig);
        try {
            simulationService.validate(test);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Please ensure that you provide a value for at least one input field.", e.getMessage());
        }

        // No expectedOutput in testConfig
        SimulationNodeInput input1 = new SimulationNodeInput()
                .setNodeId("node1")
                .setFieldValues(Map.of("attr1", "value1"));
        testConfig.setInputs(List.of(input1));
        try {
            simulationService.validate(test);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Please ensure that you provide a value for at least one expected output field.", e.getMessage());
        }

        // invalid input node in testConfig
        SimulationNodeInput expectedOutput1 = new SimulationNodeInput()
                .setNodeId("node2")
                .setFieldValues(Map.of("attr2", "value2"));
        testConfig.setExpectedOutputs(List.of(expectedOutput1));
        try {
            simulationService.validate(test);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid test input. Source Node with Id node1 does not exist. Please note that simulation tests created in previous version may not work on newer versions of the pipeline.", e.getMessage());
        }

        MappingNode sourceNode = entityGraph.getSource(sfdcEntity.getId()).get(0);
        MappingNode sinkNode = entityGraph.getSink(sfdcEntity.getId()).get(0);
        AttributeDefinition sfdcAttrib = sfdcEntity.getFieldByName("name");

        // set invalid attribute for input in testConfig
        input1.setNodeId(sourceNode.getId()).setFieldValues(Map.of("INVALID_ATTRIB", "value1"));
        testConfig.setInputs(List.of(input1));
        try {
            simulationService.validate(test);
            fail();
        } catch (SyncariValidationException e){
            assertEquals(String.format("Invalid attribute input %s for node %s", "INVALID_ATTRIB", sourceNode.getName()), e.getMessage());
        }

        // set valid input
        input1.setNodeId(sourceNode.getId()).setFieldValues(Map.of(sfdcAttrib.getApiName(), "value1"));
        testConfig.setInputs(List.of(input1));
        try {
            simulationService.validate(test);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid expected output for your test. Destination node labeled node2 does not exist.", e.getMessage());
        }

        // set invalid attribute for expected output in testConfig
        expectedOutput1.setNodeId(sinkNode.getId()).setFieldValues(Map.of("INVALID_ATTRIB", "value2"));
        testConfig.setExpectedOutputs(List.of(expectedOutput1));
        try {
            simulationService.validate(test);
            fail();
        } catch (SyncariValidationException e){
            assertEquals(String.format("Invalid attribute input %s for node %s", "INVALID_ATTRIB", sourceNode.getName()), e.getMessage());
        }

        // set valid expected output - all success
        expectedOutput1.setNodeId(sinkNode.getId()).setFieldValues(Map.of(sfdcAttrib.getApiName(), "value2"));
        testConfig.setExpectedOutputs(List.of(expectedOutput1));
        simulationService.validate(test);

    }

    @Test
    public void validateFieldTest(){

        Connector sfdcConnector = getConnector();
        EntityDefinition syncariEntity = schemaService.findEntity(connectorService.findSyncariConnector().getId(), "account").get();
        EntityDefinition sfdcEntity = schemaService.findEntity(sfdcConnector.getId(),"account").get();
        AttributeDefinition syncariAttribute = syncariEntity.getFieldByName("Name");
        AttributeDefinition sfdcAttribute = sfdcEntity.getFieldByName("name");
        MappingGraph attribGraph = mappingGraphService.retrieveDraftAttributeGraph(syncariAttribute.getId()).get();
        assertEquals(DraftStatus.NEW, attribGraph.getDraftStatus());
        attribGraph = mappingGraphRepo.save(attribGraph);

        assertNotNull(attribGraph.getSource(sfdcEntity.getId()));
        assertNotNull(attribGraph.getSink(sfdcEntity.getId()));


        // create test without targetId
        PipelineTest test = new PipelineTest();
        try {
            simulationService.validate(test);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing Target Id in test", e.getMessage());
        }

        // create test without valid scope
        test.setTargetId(syncariAttribute.getId());
        test.setScope(Scope.SCHEMA);
        try {
            simulationService.validate(test);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Test Scope SCHEMA", e.getMessage());
        }

        // null testConfig
        test.setScope(Scope.ATTRIBUTE);
        test.setUserId(SyncariContext.getUser().getId());
        test.setName("Attribute Test 123");
        try {
            simulationService.validate(test);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing test config", e.getMessage());
        }

        // No input in testConfig
        TestConfig testConfig = new TestConfig();
        test.setTestConfig(testConfig);
        try {
            simulationService.validate(test);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Please ensure that you provide a value for at least one input field.", e.getMessage());
        }

        // No expectedOutput in testConfig
        SimulationNodeInput input1 = new SimulationNodeInput()
                .setNodeId("node1")
                .setFieldValues(Map.of("attr1", "value1"));
        testConfig.setInputs(List.of(input1));
        try {
            simulationService.validate(test);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Please ensure that you provide a value for at least one expected output field.", e.getMessage());
        }

        // invalid input node in testConfig
        SimulationNodeInput expectedOutput1 = new SimulationNodeInput()
                .setNodeId("node2")
                .setFieldValues(Map.of("attr2", "value2"));
        testConfig.setExpectedOutputs(List.of(expectedOutput1));
        try {
            simulationService.validate(test);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid test input. Source Node with Id node1 does not exist. Please note that simulation tests created in previous version may not work on newer versions of the pipeline.", e.getMessage());
        }

        MappingNode sourceNode = attribGraph.getSource(sfdcAttribute.getId()).get(0);
        MappingNode sinkNode = attribGraph.getSink(sfdcAttribute.getId()).get(0);

        // set invalid attribute for input in testConfig
        input1.setNodeId(sourceNode.getId()).setFieldValues(Map.of("INVALID_ATTRIB", "value1"));
        testConfig.setInputs(List.of(input1));
        try {
            simulationService.validate(test);
            fail();
        } catch (SyncariValidationException e){
            assertEquals(String.format("Invalid attribute input %s for node %s", "INVALID_ATTRIB", sourceNode.getName()), e.getMessage());
        }

        // set valid input
        input1.setNodeId(sourceNode.getId()).setFieldValues(Map.of(sfdcAttribute.getApiName(), "value1"));
        testConfig.setInputs(List.of(input1));
        try {
            simulationService.validate(test);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid expected output for your test. Destination node labeled node2 does not exist.", e.getMessage());
        }

        // set invalid attribute for expected output in testConfig
        expectedOutput1.setNodeId(sinkNode.getId()).setFieldValues(Map.of("INVALID_ATTRIB", "value2"));
        testConfig.setExpectedOutputs(List.of(expectedOutput1));
        try {
            simulationService.validate(test);
            fail();
        } catch (SyncariValidationException e){
            assertEquals(String.format("Invalid attribute input %s for node %s", "INVALID_ATTRIB", sourceNode.getName()), e.getMessage());
        }
        
        // set null input
        expectedOutput1.setNodeId(sinkNode.getId()).setFieldValues(Map.of(sfdcAttribute.getApiName(), "value2"));
        Map<String, Object> fieldValues = new HashMap<>();
        fieldValues.put(sfdcAttribute.getApiName(), "");
        input1.setNodeId(sourceNode.getId()).setFieldValues(fieldValues);
        testConfig.setExpectedOutputs(List.of(expectedOutput1));
        try {
        	simulationService.validate(test);
        } catch (SyncariValidationException e){
        	fail();
        }

        // set valid expected output - all success
        expectedOutput1.setNodeId(sinkNode.getId()).setFieldValues(Map.of(sfdcAttribute.getApiName(), "value2"));
        input1.setNodeId(sourceNode.getId()).setFieldValues(Map.of(sfdcAttribute.getApiName(), "value1"));
        testConfig.setExpectedOutputs(List.of(expectedOutput1));
        simulationService.validate(test);

    }

    private PipelineTest getEntityTest(){
        PipelineTest test = new PipelineTest();
        test.setTargetId("syncariEntity123");
        test.setUserId(SyncariContext.getUser().getId());
        test.setScope(Scope.ENTITY);
        test.setName("Entity Test 123");
        test.setDescription("Entity Test Description");
        TestConfig testConfig = new TestConfig();
        SimulationNodeInput input1 = new SimulationNodeInput()
                .setNodeId("node1")
                .setFieldValues(Map.of("attr1", "value1", "attr12", "value12"));
        SimulationNodeInput input2 = new SimulationNodeInput()
                .setNodeId("node2")
                .setFieldValues(Map.of("attr2", "value2"));
        testConfig.setInputs(List.of(input1, input2));
        testConfig.setExpectedOutputs(List.of(input2));
        test.setTestConfig(testConfig);
        return test;
    }

    private PipelineTest getFieldTest(){
        PipelineTest test = new PipelineTest();
        test.setTargetId("syncariAttribute123");
        test.setUserId(SyncariContext.getUser().getId());
        test.setScope(Scope.ATTRIBUTE);
        test.setName("Attribute Test 123");
        test.setDescription("Attribute Test Description");
        TestConfig testConfig = new TestConfig();
        SimulationNodeInput input1 = new SimulationNodeInput()
                .setNodeId("node1")
                .setFieldValues(Map.of("attr1", "value1"));
        SimulationNodeInput input2 = new SimulationNodeInput()
                .setNodeId("node2")
                .setFieldValues(Map.of("attr2", "value2"));
        testConfig.setInputs(List.of(input1, input2));
        testConfig.setExpectedOutputs(List.of(input2));
        test.setTestConfig(testConfig);
        return test;
    }


    private Connector getConnector() {
        if(connector == null) {
            ConnectorMetadata metadata = connectorService.describe(Constants.TEST_SYNAPSE);
            connector = new Connector("testSynapse", metadata.getId(), "http://someurl");
            connector.setMetadata(metadata);
            Connector saved = connectorService.save(connector);
            connectorService.authenticated(saved.getId());
            connectorService.activate(saved.getId(), true, saved.getId());
        }
        return connector;
    }
}
