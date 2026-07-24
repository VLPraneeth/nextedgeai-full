package com.syncari.viper;

import com.syncari.AbstractSyncariTest;
import com.syncari.connector.EntityData;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.test.SimulationNodeInput;
import com.syncari.core.model.misc.test.TestNodeResult;
import com.syncari.core.model.misc.test.TestConfig;
import com.syncari.core.model.util.Scope;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.repositories.customer.*;
import com.syncari.core.schema.Schema;
import com.syncari.core.service.*;
import com.syncari.core.simulation.SimulationCurrentBatch;
import com.syncari.viper.simulation.DatastoreSimulationService;
import com.syncari.viper.simulation.SimulationEntityRepo;
import com.syncari.viper.simulation.SimulationEventStore;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.syncari.core.utils.GraphHelper.*;
import static com.syncari.core.utils.SchemaHelper.createAttribute;
import static com.syncari.core.utils.SchemaHelper.createEntityDef;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;

@Ignore
public class SimulationRunnerTest extends AbstractSyncariTest {
    @Autowired
    FunctionService functionService;
    @Autowired
    ActionDefinitionRepo actionRepo;
    @Autowired
    SimulationRunner simulationRunner;

    @MockBean
    ConnectorService mockConnectorService;
    @MockBean
    private SchemaService mockSchemaService;

    @MockBean
    private MappingGraphService mockGraphService;

    @MockBean
    UnresolvedRecordService mockUnresolvedRecordService;
    
    @MockBean
    private AttributeDefinitionCache attributeDefinitionCache;

    @Autowired
    private TestResultRepo testResultRepo;
    @Autowired
    private SimulationRunRepo simulationRunRepo;

    @Autowired
    private SyncDetailMetricService syncDetailMetricService;

    @Autowired
    private FeatureService featureService;

    private Connector syncariConnector;

    @Override
    public void setUp() {
        if(syncariConnector == null){
            syncariConnector = createConnector("syncari", "syncariConnId", "syncariConnMetaId");
        }
        when(mockConnectorService.getSyncariConnector()).thenReturn(syncariConnector);
        when(mockSchemaService.getSyncariSchema()).thenReturn(new Schema());
        when(mockConnectorService.refreshAuthentication(any(Connector.class))).then(returnsFirstArg());
        super.setUp();
    }

    @Test
    public void simpleEPTest() throws InterruptedException {

        GraphContext graphContext = new GraphContext();
        EntityDefinition sink = createEntityDef("destAccount", "Account",
                createConnector("testconnector", "con1", "metaid"));
        sink.addField(createAttribute("destfield1", StringType.VALUE, sink.getId()));
        sink.addField(createAttribute("destfield2", StringType.VALUE, sink.getId()));
        EntityDefinition coreEntity = createEntityDef("coreAccount", "account", null);
        coreEntity.addField(createAttribute("corefield1", StringType.VALUE, sink.getId()));
        coreEntity.addField(createAttribute("corefield2", StringType.VALUE, sink.getId()));

        EntityDefinition srcEntity = createEntityDef("srcAccount", "Source Account",
                createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));

        var srcAttrib1 = createAttribute("srcfield1", StringType.VALUE, sink.getId());
        var srcAttrib2 = createAttribute("srcfield2", StringType.VALUE, sink.getId());
        srcEntity.addField(srcAttrib1);
        srcEntity.addField(srcAttrib2);

        MappingGraph entityGraph = newGraph(coreEntity, functionService)
                .src(srcEntity)
                .dest(sink)
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "destAccount").getGraph();
        MappingGraph field1Graph = newGraph(coreEntity.getFieldByName("corefield1"), functionService)
                .src(srcEntity.getFieldByName("srcfield1"))
                .dest(sink.getFieldByName("destfield1"))
                .connect("srcfield1", "corefield1")
                .connect("corefield1", "destfield1").getGraph();
        MappingGraph field2Graph = newGraph(coreEntity.getFieldByName("corefield2"), functionService)
                .src(srcEntity.getFieldByName("srcfield2"))
                .dest(sink.getFieldByName("destfield2"))
                .connect("srcfield2", "corefield2")
                .connect("corefield2", "destfield2").getGraph();

        graphContext.setSimulationMode(true);

        graphContext.setGraph(entityGraph).setCurrentBatch(new SimulationCurrentBatch());
        graphContext.getTestContext().setEntityGraph(entityGraph);
        graphContext.getTestContext().setAttributeGraphs(List.of(field1Graph, field2Graph));


        when(mockSchemaService.getEntity(srcEntity.getId())).thenReturn(srcEntity);
        when(mockSchemaService.getEntity(sink.getId())).thenReturn(sink);
        when(mockSchemaService.getEntity(coreEntity.getId())).thenReturn(coreEntity);
        when(mockSchemaService.getAttribute(srcAttrib1.getId())).thenReturn(srcAttrib1);
        when(mockSchemaService.getAttribute(srcAttrib2.getId())).thenReturn(srcAttrib2);

        when(mockGraphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(field1Graph, field2Graph));
        when(mockGraphService.retrieveDraftAttributeGraphs(entityGraph.getId())).thenReturn(List.of(field1Graph, field2Graph));
        when(mockGraphService.retrieve(entityGraph.getId())).thenReturn(Optional.of(entityGraph));
        when(mockGraphService.retrieveWithoutLayout(entityGraph.getId())).thenReturn(Optional.of(entityGraph));
        when(mockGraphService.retrieveDraftEntityGraph(coreEntity.getId())).thenReturn(Optional.of(entityGraph));
        when(mockGraphService.extractEntityFromNode(entityGraph.getCoreNode())).thenReturn(coreEntity);
        when(mockGraphService.extractEntityFromNode(entityGraph.getSources().findFirst().get())).thenReturn(srcEntity);
        when(mockGraphService.extractEntityFromNode(entityGraph.getSinks().findFirst().get())).thenReturn(sink);

        ConnectorMetadata conmetaid = new ConnectorMetadata("conmetaid");
        conmetaid.setName("salesforce");
        Connector t = new Connector("testconnector", conmetaid, "endpojnt", "u", "p");
        t.setId("con1");
        ConnectorMetadata srcConMeta = new ConnectorMetadata("sourceConnectorMetaId");
        srcConMeta.setName("salesforce");
        Connector srcCon = new Connector("sourceConnector", srcConMeta, "endpojnt", "u", "p");
        srcCon.setId("sourceConnectorId");

        when(mockConnectorService.get("con1")).thenReturn(t);
        when(mockConnectorService.get("sourceConnectorId")).thenReturn(srcCon);
        when(mockConnectorService.getAllActive()).thenReturn(List.of(srcCon, t));
        EntityData saved = new EntityData("account").addValue("corefield1", "Value1").addValue("corefield2", "Value2");
        when(attributeDefinitionCache.findAllById(List.of(coreEntity.getFieldByName("corefield1").getId(), coreEntity.getFieldByName("corefield2").getId())))
                .thenReturn(List.of(coreEntity.getFieldByName("corefield1"), coreEntity.getFieldByName("corefield2")));
        when(attributeDefinitionCache.findById(coreEntity.getFieldByName("corefield1").getId())).thenReturn(Optional.of(coreEntity.getFieldByName("corefield1")));
        when(attributeDefinitionCache.findById(coreEntity.getFieldByName("corefield2").getId())).thenReturn(Optional.of(coreEntity.getFieldByName("corefield2")));

        when(mockUnresolvedRecordService.getUnresolvedRecords(anyString())).thenReturn(List.of());
        when(mockUnresolvedRecordService.getUnresolvedEntities(anyString(), anyString())).thenReturn(List.of());

        PipelineTest test = new PipelineTest()
                .setScope(Scope.ENTITY)
                .setTargetId(coreEntity.getId())
                .setUserId(viperContext.getUser().getId())
                .setTestConfig(new TestConfig()
                        .setInputs(List.of(new SimulationNodeInput().setNodeId(entityGraph.getSource(srcEntity.getId()).get(0).getId()).setFieldValues(
                                Map.of("srcfield1", "Value1", "srcfield2", "Value2")

                        )))
                        .setExpectedOutputs(List.of(new SimulationNodeInput().setNodeId(entityGraph.getSink(sink.getId()).get(0).getId()).setFieldValues(
                                Map.of("destfield1", "Value1", "destfield2", "Value2")
                        ))));
        PipelineTest savedTest = simulationRunner.simulationService.createEntityTest(test);
        SimulationRun simulationRun = simulationRunner.simulationService.setupSimulationRun(coreEntity.getId(), "entityTestRun1", List.of(savedTest.getId()), entityGraph);
        simulationRun.setCreatedBy(viperContext.getUser().getId());
        SimulationRun savedSimulationRun = simulationRunRepo.save(simulationRun);

        // assert injected dependencies in simulationRunner
        assertTrue(simulationRunner.executionFactory.getGraphRunner().dataStoreService instanceof DatastoreSimulationService);
        assertTrue(simulationRunner.executionFactory.getGraphRunner().eventStore instanceof SimulationEventStore);
        assertTrue(simulationRunner.executionFactory.getGraphRunner().entityRepo instanceof SimulationEntityRepo);

        simulationRunner.simulate(savedSimulationRun.getId());
        Thread.sleep(1000);
        List<TestResult> all = testResultRepo.findBySimulationRunId(simulationRun.getId());
        assertEquals(1, all.size());
        //sdestination node output
        TestNodeResult destinationNodeResults = all.get(0).getNodeResults().stream().filter(n -> n.getNodeId().equalsIgnoreCase(entityGraph.getSinks().findFirst().get().getId())).findFirst().get();
        Map<String, Object> results = destinationNodeResults.getOutputs().values().stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v.getValue()));
        assertEquals(Map.of("destfield1", "Value1", "destfield2", "Value2"), results);

    }

    @Test
    public void simulateEPWithFilterAndSetValue() throws InterruptedException {

        GraphContext graphContext = new GraphContext();
        EntityDefinition coreEntity = createEntityDef("coreAccount", "account", null);
        var coreField1 = createAttribute("corefield1", StringType.VALUE, coreEntity.getId());
        var coreField2 = createAttribute("corefield2", StringType.VALUE, coreEntity.getId());
        coreEntity.addField(coreField1);
        coreEntity.addField(coreField2);

        EntityDefinition srcEntity = createEntityDef("srcAccount", "Source Account", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        var srcField1 = createAttribute("srcfield1", StringType.VALUE, srcEntity.getId());
        var srcField2 = createAttribute("srcfield2", StringType.VALUE, srcEntity.getId());
        srcEntity.addField(srcField1);
        srcEntity.addField(srcField2);

        Map<String, Object> predicateMap = new HashMap<>();
        var predicates = List.of(Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", srcField1.getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "RandomValue")
                )
        );
        predicateMap.put("predicate", Map.of("predicates", predicates, "operator", "AND"));

        MappingGraph entityGraph = newGraph(coreEntity, functionService)
                .src(srcEntity)
                .function("filter", "Filter", predicateMap)
                .function("isFalse", "Is False")
                .function("setValueOnEntity", "Set Value", Map.of("newValue","Value2", "attributeDefinitionId", srcField2.getId()))
                .connect("srcAccount", "Filter")
                .connect("Filter", "Is False")
                .connect("Is False", "Set Value")
                .connect("Set Value", "coreAccount").getGraph();
        MappingGraph field1Graph = newGraph(coreEntity.getFieldByName("corefield1"), functionService)
                .src(srcEntity.getFieldByName("srcfield1"))
                .connect("srcfield1", "corefield1").getGraph();
        MappingGraph field2Graph = newGraph(coreEntity.getFieldByName("corefield2"), functionService)
                .src(srcEntity.getFieldByName("srcfield2"))
                .connect("srcfield2", "corefield2").getGraph();

        graphContext.setSimulationMode(true);

        graphContext.setGraph(entityGraph).setCurrentBatch(new SimulationCurrentBatch());
        graphContext.getTestContext().setEntityGraph(entityGraph);
        graphContext.getTestContext().setAttributeGraphs(List.of(field1Graph, field2Graph));


        when(mockSchemaService.getEntity(srcEntity.getId())).thenReturn(srcEntity);
        when(mockSchemaService.getEntity(coreEntity.getId())).thenReturn(coreEntity);
        when(mockSchemaService.getAttribute(coreField1.getId())).thenReturn(coreField1);
        when(mockSchemaService.getAttribute(srcField1.getId())).thenReturn(srcField1);
        when(mockSchemaService.getAttribute(coreField2.getId())).thenReturn(coreField2);
        when(mockSchemaService.getAttribute(srcField2.getId())).thenReturn(srcField2);

        when(mockGraphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(field1Graph, field2Graph));
        when(mockGraphService.retrieveDraftAttributeGraphs(entityGraph.getId())).thenReturn(List.of(field1Graph, field2Graph));
        when(mockGraphService.retrieve(entityGraph.getId())).thenReturn(Optional.of(entityGraph));
        when(mockGraphService.retrieveWithoutLayout(entityGraph.getId())).thenReturn(Optional.of(entityGraph));
        when(mockGraphService.retrieveDraftEntityGraph(coreEntity.getId())).thenReturn(Optional.of(entityGraph));
        when(mockGraphService.retrieve(field1Graph.getId())).thenReturn(Optional.of(field1Graph));
        when(mockGraphService.retrieveWithoutLayout(field1Graph.getId())).thenReturn(Optional.of(field1Graph));
        when(mockGraphService.retrieveDraftAttributeGraph(coreField1.getId())).thenReturn(Optional.of(field1Graph));
        when(mockGraphService.retrieve(field2Graph.getId())).thenReturn(Optional.of(field2Graph));
        when(mockGraphService.retrieveWithoutLayout(field2Graph.getId())).thenReturn(Optional.of(field2Graph));
        when(mockGraphService.retrieveDraftAttributeGraph(coreField2.getId())).thenReturn(Optional.of(field2Graph));
        when(mockGraphService.extractEntityFromNode(entityGraph.getCoreNode())).thenReturn(coreEntity);
        when(mockGraphService.extractEntityFromNode(entityGraph.getSources().findFirst().get())).thenReturn(srcEntity);

        ConnectorMetadata conmetaid = new ConnectorMetadata("conmetaid");
        conmetaid.setName("salesforce");
        Connector t = new Connector("testconnector", conmetaid, "endpojnt", "u", "p");
        t.setId("con1");
        ConnectorMetadata srcConMeta = new ConnectorMetadata("sourceConnectorMetaId");
        srcConMeta.setName("salesforce");
        Connector srcCon = new Connector("sourceConnector", srcConMeta, "endpojnt", "u", "p");
        srcCon.setId("sourceConnectorId");

        when(mockConnectorService.get("con1")).thenReturn(t);
        when(mockConnectorService.get("sourceConnectorId")).thenReturn(srcCon);
        when(mockConnectorService.getAllActive()).thenReturn(List.of(srcCon, t));
        when(attributeDefinitionCache.findAllById(List.of(coreEntity.getFieldByName("corefield1").getId(), coreEntity.getFieldByName("corefield2").getId())))
                .thenReturn(List.of(coreEntity.getFieldByName("corefield1"), coreEntity.getFieldByName("corefield2")));
        when(attributeDefinitionCache.findById(coreEntity.getFieldByName("corefield1").getId())).thenReturn(Optional.of(coreEntity.getFieldByName("corefield1")));
        when(attributeDefinitionCache.findById(coreEntity.getFieldByName("corefield2").getId())).thenReturn(Optional.of(coreEntity.getFieldByName("corefield2")));
        when(attributeDefinitionCache.findAllById(List.of(coreField1.getId()))).thenReturn(List.of(coreField1));
        when(attributeDefinitionCache.findAllById(List.of(coreField2.getId()))).thenReturn(List.of(coreField2));
        when(attributeDefinitionCache.findAllById(List.of(coreField1.getId(), coreField2.getId()))).thenReturn(List.of(coreField1, coreField2));
        when(attributeDefinitionCache.findById(srcField1.getId())).thenReturn(Optional.of(srcField1));
        when(attributeDefinitionCache.findById(srcField2.getId())).thenReturn(Optional.of(srcField2));

        when(mockUnresolvedRecordService.getUnresolvedRecords(anyString())).thenReturn(List.of());
        when(mockUnresolvedRecordService.getUnresolvedEntities(anyString(), anyString())).thenReturn(List.of());

        PipelineTest test = new PipelineTest()
                .setScope(Scope.ENTITY)
                .setTargetId(coreEntity.getId())
                .setUserId(viperContext.getUser().getId())
                .setTestConfig(new TestConfig()
                        .setInputs(List.of(new SimulationNodeInput().setNodeId(entityGraph.getSource(srcEntity.getId()).get(0).getId()).setFieldValues(
                                Map.of("srcfield1", "Value1")

                        )))
                        .setExpectedOutputs(List.of(new SimulationNodeInput().setNodeId(entityGraph.getCoreNode().getId()).setFieldValues(
                                Map.of("corefield1", "Value1", "corefield2", "Value2")
                        ))));
        PipelineTest savedTest = simulationRunner.simulationService.createEntityTest(test);
        SimulationRun simulationRun = simulationRunner.simulationService.setupSimulationRun(coreEntity.getId(), "entityTestRun1", List.of(savedTest.getId()), entityGraph);
        simulationRun.setCreatedBy(viperContext.getUser().getId());
        SimulationRun savedSimulationRun = simulationRunRepo.save(simulationRun);
        simulationRunner.simulate(savedSimulationRun.getId());
        Thread.sleep(2000);
        List<TestResult> all = testResultRepo.findBySimulationRunId(simulationRun.getId());
        assertEquals(1, all.size());

        // check core node output
        TestNodeResult coreNodeResults = all.get(0).getNodeResults().stream().filter(n -> n.getNodeId().equalsIgnoreCase(entityGraph.getCoreNode().getId())).findFirst().get();
        Map<String, Object> results = coreNodeResults.getOutputs().values().stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v.getValue()));
        assertEquals(Map.of("corefield1", "Value1", "corefield2", "Value2"), results);

        // check filter node and setValue node output
        TestNodeResult filterNodeResult = all.get(0).getNodeResults().stream()
                .filter(n -> n.getNodeId().equalsIgnoreCase(
                        entityGraph.getNodes().stream().filter(node -> "Filter".equals(node.getName())).findFirst().get().getId()
                )).findFirst().get();
        Map<String, Object> filterNodeResults = filterNodeResult.getOutputs().values().stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v.getValue()));
        assertEquals(Map.of("srcfield1", "Value1"), filterNodeResults);
        assertEquals(TestNodeResult.Status.COMPLETED, filterNodeResult.getStatus());

        TestNodeResult isFalseNodeResult = all.get(0).getNodeResults().stream()
                .filter(n -> n.getNodeId().equalsIgnoreCase(
                        entityGraph.getNodes().stream().filter(node -> "Filter".equals(node.getName())).findFirst().get().getId()
                )).findFirst().get();
        Map<String, Object> isFalseNodeResults = isFalseNodeResult.getOutputs().values().stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v.getValue()));
        assertEquals(Map.of("srcfield1", "Value1"), isFalseNodeResults);
        assertEquals(TestNodeResult.Status.COMPLETED, isFalseNodeResult.getStatus());

        TestNodeResult setValueNodeResult = all.get(0).getNodeResults().stream()
                .filter(n -> n.getNodeId().equalsIgnoreCase(
                        entityGraph.getNodes().stream().filter(node -> "Set Value".equals(node.getName())).findFirst().get().getId()
                )).findFirst().get();
        Map<String, Object> setValueNodeResults = setValueNodeResult.getOutputs().values().stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v.getValue()));
        assertEquals(Map.of("srcfield1", "Value1", "srcfield2", "Value2"), setValueNodeResults);
        assertEquals(TestNodeResult.Status.COMPLETED, setValueNodeResult.getStatus());

    }

    @Ignore
    @Test
    public void simulateEPWithTerminalAction() throws InterruptedException {
        GraphContext graphContext = new GraphContext();
        EntityDefinition coreEntity = createEntityDef("coreAccount", "account", null);
        var coreField1 = createAttribute("corefield1", StringType.VALUE, coreEntity.getId());
        var coreField2 = createAttribute("corefield2", StringType.VALUE, coreEntity.getId());
        coreEntity.addField(coreField1);
        coreEntity.addField(coreField2);

        EntityDefinition srcEntity = createEntityDef("srcAccount", "Source Account", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        var srcField1 = createAttribute("srcfield1", StringType.VALUE, srcEntity.getId());
        var srcField2 = createAttribute("srcfield2", StringType.VALUE, srcEntity.getId());
        srcEntity.addField(srcField1);
        srcEntity.addField(srcField2);

        MappingGraph entityGraph = newGraph(coreEntity, functionService, actionRepo)
                .src(srcEntity)
                .action("sendEmail")
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "sendEmail").getGraph();
        MappingGraph field1Graph = newGraph(coreEntity.getFieldByName("corefield1"), functionService)
                .src(srcEntity.getFieldByName("srcfield1"))
                .connect("srcfield1", "corefield1").getGraph();
        MappingGraph field2Graph = newGraph(coreEntity.getFieldByName("corefield2"), functionService)
                .src(srcEntity.getFieldByName("srcfield2"))
                .connect("srcfield2", "corefield2").getGraph();

        graphContext.setSimulationMode(true);

        graphContext.setGraph(entityGraph).setCurrentBatch(new SimulationCurrentBatch());
        graphContext.getTestContext().setEntityGraph(entityGraph);
        graphContext.getTestContext().setAttributeGraphs(List.of(field1Graph, field2Graph));


        when(mockSchemaService.getEntity(srcEntity.getId())).thenReturn(srcEntity);
        when(mockSchemaService.getEntity(coreEntity.getId())).thenReturn(coreEntity);
        when(mockSchemaService.getAttribute(coreField1.getId())).thenReturn(coreField1);
        when(mockSchemaService.getAttribute(srcField1.getId())).thenReturn(srcField1);
        when(mockSchemaService.getAttribute(coreField2.getId())).thenReturn(coreField2);
        when(mockSchemaService.getAttribute(srcField2.getId())).thenReturn(srcField2);

        when(mockGraphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(field1Graph, field2Graph));
        when(mockGraphService.retrieveDraftAttributeGraphs(entityGraph.getId())).thenReturn(List.of(field1Graph, field2Graph));
        when(mockGraphService.retrieve(entityGraph.getId())).thenReturn(Optional.of(entityGraph));
        when(mockGraphService.retrieveDraftEntityGraph(coreEntity.getId())).thenReturn(Optional.of(entityGraph));
        when(mockGraphService.retrieve(field1Graph.getId())).thenReturn(Optional.of(field1Graph));
        when(mockGraphService.retrieveDraftAttributeGraph(coreField1.getId())).thenReturn(Optional.of(field1Graph));
        when(mockGraphService.retrieve(field2Graph.getId())).thenReturn(Optional.of(field2Graph));
        when(mockGraphService.retrieveDraftAttributeGraph(coreField2.getId())).thenReturn(Optional.of(field2Graph));
        when(mockGraphService.extractEntityFromNode(entityGraph.getCoreNode())).thenReturn(coreEntity);
        when(mockGraphService.extractEntityFromNode(entityGraph.getSources().findFirst().get())).thenReturn(srcEntity);

        ConnectorMetadata conmetaid = new ConnectorMetadata("conmetaid");
        conmetaid.setName("salesforce");
        Connector t = new Connector("testconnector", conmetaid, "endpojnt", "u", "p");
        t.setId("con1");
        ConnectorMetadata srcConMeta = new ConnectorMetadata("sourceConnectorMetaId");
        srcConMeta.setName("salesforce");
        Connector srcCon = new Connector("sourceConnector", srcConMeta, "endpojnt", "u", "p");
        srcCon.setId("sourceConnectorId");

        when(mockConnectorService.get("con1")).thenReturn(t);
        when(mockConnectorService.get("sourceConnectorId")).thenReturn(srcCon);
        when(mockConnectorService.getAllActive()).thenReturn(List.of(srcCon, t));
        when(attributeDefinitionCache.findAllById(List.of(coreEntity.getFieldByName("corefield1").getId(), coreEntity.getFieldByName("corefield2").getId())))
                .thenReturn(List.of(coreEntity.getFieldByName("corefield1"), coreEntity.getFieldByName("corefield2")));
        when(attributeDefinitionCache.findById(coreEntity.getFieldByName("corefield1").getId())).thenReturn(Optional.of(coreEntity.getFieldByName("corefield1")));
        when(attributeDefinitionCache.findById(coreEntity.getFieldByName("corefield2").getId())).thenReturn(Optional.of(coreEntity.getFieldByName("corefield2")));
        when(attributeDefinitionCache.findAllById(List.of(coreField1.getId()))).thenReturn(List.of(coreField1));
        when(attributeDefinitionCache.findAllById(List.of(coreField2.getId()))).thenReturn(List.of(coreField2));
        when(attributeDefinitionCache.findAllById(List.of(coreField1.getId(), coreField2.getId()))).thenReturn(List.of(coreField1, coreField2));
        when(attributeDefinitionCache.findById(srcField1.getId())).thenReturn(Optional.of(srcField1));
        when(attributeDefinitionCache.findById(srcField2.getId())).thenReturn(Optional.of(srcField2));

        when(mockUnresolvedRecordService.getUnresolvedRecords(anyString())).thenReturn(List.of());
        when(mockUnresolvedRecordService.getUnresolvedEntities(anyString(), anyString())).thenReturn(List.of());

        PipelineTest test = new PipelineTest()
                .setScope(Scope.ENTITY)
                .setTargetId(coreEntity.getId())
                .setUserId(viperContext.getUser().getId())
                .setTestConfig(new TestConfig()
                        .setInputs(List.of(new SimulationNodeInput().setNodeId(entityGraph.getSource(srcEntity.getId()).get(0).getId()).setFieldValues(
                                Map.of("srcfield1", "Value1", "srcfield2", "Value2")

                        )))
                        .setExpectedOutputs(List.of(new SimulationNodeInput().setNodeId(entityGraph.getCoreNode().getId()).setFieldValues(
                                Map.of("corefield1", "Value1", "corefield2", "Value2")
                        ))));
        PipelineTest savedTest = simulationRunner.simulationService.createEntityTest(test);
        SimulationRun simulationRun = simulationRunner.simulationService.setupSimulationRun(coreEntity.getId(), "entityTestRun1", List.of(savedTest.getId()), entityGraph);
        simulationRun.setCreatedBy(viperContext.getUser().getId());
        SimulationRun savedSimulationRun = simulationRunRepo.save(simulationRun);
        simulationRunner.simulate(savedSimulationRun.getId());
        Thread.sleep(2000);
        List<TestResult> all = testResultRepo.findBySimulationRunId(simulationRun.getId());
        assertEquals(1, all.size());

        // check source node output
        TestNodeResult srcNodeResults = all.get(0).getNodeResults().stream().filter(n -> n.getNodeId().equalsIgnoreCase(entityGraph.getSources().findFirst().get().getId())).findFirst().get();
        Map<String, Object> srcResults = srcNodeResults.getOutputs().values().stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v.getValue()));
        assertEquals(Map.of("srcfield1", "Value1", "srcfield2", "Value2"), srcResults);
        assertEquals(TestNodeResult.Status.COMPLETED, srcNodeResults.getStatus());

        // check core node output
        TestNodeResult coreNodeResults = all.get(0).getNodeResults().stream().filter(n -> n.getNodeId().equalsIgnoreCase(entityGraph.getCoreNode().getId())).findFirst().get();
        Map<String, Object> results = coreNodeResults.getOutputs().values().stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v.getValue()));
        assertEquals(Map.of("corefield1", "Value1", "corefield2", "Value2"), results);
        assertEquals(TestNodeResult.Status.SUCCESS, coreNodeResults.getStatus());

        //check action node's result
        TestNodeResult actionNodeResult = all.get(0).getNodeResults().stream()
                .filter(n -> n.getNodeId().equalsIgnoreCase(
                        entityGraph.getNodes().stream().filter(node -> "sendEmail".equals(node.getName())).findFirst().get().getId()
                )).findFirst().get();
        Map<String, Object> setValueNodeResults = actionNodeResult.getOutputs().values().stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v.getValue()));
        assertEquals(Map.of("actionResult", "Executed sendEmail Action"), setValueNodeResults);
        assertEquals(TestNodeResult.Status.COMPLETED, actionNodeResult.getStatus());
    }

    @Test
    public void simulateEPWithInlineAction() throws InterruptedException {
        GraphContext graphContext = new GraphContext();
        EntityDefinition coreEntity = createEntityDef("coreAccount", "account", null);
        var coreField1 = createAttribute("corefield1", StringType.VALUE, coreEntity.getId());
        var coreField2 = createAttribute("corefield2", StringType.VALUE, coreEntity.getId());
        coreEntity.addField(coreField1);
        coreEntity.addField(coreField2);

        EntityDefinition srcEntity = createEntityDef("srcAccount", "Source Account", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        var srcField1 = createAttribute("srcfield1", StringType.VALUE, srcEntity.getId());
        var srcField2 = createAttribute("srcfield2", StringType.VALUE, srcEntity.getId());
        srcEntity.addField(srcField1);
        srcEntity.addField(srcField2);

        MappingGraph entityGraph = newGraph(coreEntity, functionService, actionRepo)
                .src(srcEntity)
                .action("convertSalesforceLead")
                .connect("srcAccount", "convertSalesforceLead")
                .connect("convertSalesforceLead", "coreAccount").getGraph();
        MappingGraph field1Graph = newGraph(coreEntity.getFieldByName("corefield1"), functionService)
                .src(srcEntity.getFieldByName("srcfield1"))
                .connect("srcfield1", "corefield1").getGraph();
        MappingGraph field2Graph = newGraph(coreEntity.getFieldByName("corefield2"), functionService)
                .src(srcEntity.getFieldByName("srcfield2"))
                .connect("srcfield2", "corefield2").getGraph();

        graphContext.setSimulationMode(true);

        graphContext.setGraph(entityGraph).setCurrentBatch(new SimulationCurrentBatch());
        graphContext.getTestContext().setEntityGraph(entityGraph);
        graphContext.getTestContext().setAttributeGraphs(List.of(field1Graph, field2Graph));


        when(mockSchemaService.getEntity(srcEntity.getId())).thenReturn(srcEntity);
        when(mockSchemaService.getEntity(coreEntity.getId())).thenReturn(coreEntity);
        when(mockSchemaService.getAttribute(coreField1.getId())).thenReturn(coreField1);
        when(mockSchemaService.getAttribute(srcField1.getId())).thenReturn(srcField1);
        when(mockSchemaService.getAttribute(coreField2.getId())).thenReturn(coreField2);
        when(mockSchemaService.getAttribute(srcField2.getId())).thenReturn(srcField2);

        when(mockGraphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(field1Graph, field2Graph));
        when(mockGraphService.retrieveDraftAttributeGraphs(entityGraph.getId())).thenReturn(List.of(field1Graph, field2Graph));
        when(mockGraphService.retrieve(entityGraph.getId())).thenReturn(Optional.of(entityGraph));
        when(mockGraphService.retrieveWithoutLayout(entityGraph.getId())).thenReturn(Optional.of(entityGraph));
        when(mockGraphService.retrieveDraftEntityGraph(coreEntity.getId())).thenReturn(Optional.of(entityGraph));
        when(mockGraphService.retrieve(field1Graph.getId())).thenReturn(Optional.of(field1Graph));
        when(mockGraphService.retrieveWithoutLayout(field1Graph.getId())).thenReturn(Optional.of(field1Graph));
        when(mockGraphService.retrieveDraftAttributeGraph(coreField1.getId())).thenReturn(Optional.of(field1Graph));
        when(mockGraphService.retrieve(field2Graph.getId())).thenReturn(Optional.of(field2Graph));
        when(mockGraphService.retrieveWithoutLayout(field2Graph.getId())).thenReturn(Optional.of(field2Graph));
        when(mockGraphService.retrieveDraftAttributeGraph(coreField2.getId())).thenReturn(Optional.of(field2Graph));
        when(mockGraphService.extractEntityFromNode(entityGraph.getCoreNode())).thenReturn(coreEntity);
        when(mockGraphService.extractEntityFromNode(entityGraph.getSources().findFirst().get())).thenReturn(srcEntity);

        ConnectorMetadata conmetaid = new ConnectorMetadata("conmetaid");
        conmetaid.setName("salesforce");
        Connector t = new Connector("testconnector", conmetaid, "endpojnt", "u", "p");
        t.setId("con1");
        ConnectorMetadata srcConMeta = new ConnectorMetadata("sourceConnectorMetaId");
        srcConMeta.setName("salesforce");
        Connector srcCon = new Connector("sourceConnector", srcConMeta, "endpojnt", "u", "p");
        srcCon.setId("sourceConnectorId");

        when(mockConnectorService.get("con1")).thenReturn(t);
        when(mockConnectorService.get("sourceConnectorId")).thenReturn(srcCon);
        when(mockConnectorService.getAllActive()).thenReturn(List.of(srcCon, t));
        when(attributeDefinitionCache.findAllById(List.of(coreEntity.getFieldByName("corefield1").getId(), coreEntity.getFieldByName("corefield2").getId())))
                .thenReturn(List.of(coreEntity.getFieldByName("corefield1"), coreEntity.getFieldByName("corefield2")));
        when(attributeDefinitionCache.findById(coreEntity.getFieldByName("corefield1").getId())).thenReturn(Optional.of(coreEntity.getFieldByName("corefield1")));
        when(attributeDefinitionCache.findById(coreEntity.getFieldByName("corefield2").getId())).thenReturn(Optional.of(coreEntity.getFieldByName("corefield2")));
        when(attributeDefinitionCache.findAllById(List.of(coreField1.getId()))).thenReturn(List.of(coreField1));
        when(attributeDefinitionCache.findAllById(List.of(coreField2.getId()))).thenReturn(List.of(coreField2));
        when(attributeDefinitionCache.findAllById(List.of(coreField1.getId(), coreField2.getId()))).thenReturn(List.of(coreField1, coreField2));
        when(attributeDefinitionCache.findById(srcField1.getId())).thenReturn(Optional.of(srcField1));
        when(attributeDefinitionCache.findById(srcField2.getId())).thenReturn(Optional.of(srcField2));

        when(mockUnresolvedRecordService.getUnresolvedRecords(anyString())).thenReturn(List.of());
        when(mockUnresolvedRecordService.getUnresolvedEntities(anyString(), anyString())).thenReturn(List.of());

        PipelineTest test = new PipelineTest()
                .setScope(Scope.ENTITY)
                .setTargetId(coreEntity.getId())
                .setUserId(viperContext.getUser().getId())
                .setTestConfig(new TestConfig()
                        .setInputs(List.of(new SimulationNodeInput().setNodeId(entityGraph.getSource(srcEntity.getId()).get(0).getId()).setFieldValues(
                                Map.of("srcfield1", "Value1", "srcfield2", "Value2")

                        )))
                        .setExpectedOutputs(List.of(new SimulationNodeInput().setNodeId(entityGraph.getCoreNode().getId()).setFieldValues(
                                Map.of("corefield1", "Value1", "corefield2", "Value2")
                        ))));
        PipelineTest savedTest = simulationRunner.simulationService.createEntityTest(test);
        SimulationRun simulationRun = simulationRunner.simulationService.setupSimulationRun(coreEntity.getId(), "entityTestRun1", List.of(savedTest.getId()), entityGraph);
        simulationRun.setCreatedBy(viperContext.getUser().getId());
        SimulationRun savedSimulationRun = simulationRunRepo.save(simulationRun);
        simulationRunner.simulate(savedSimulationRun.getId());
        Thread.sleep(2000);
        List<TestResult> all = testResultRepo.findBySimulationRunId(simulationRun.getId());
        assertEquals(1, all.size());

        // check source node output
        TestNodeResult srcNodeResults = all.get(0).getNodeResults().stream().filter(n -> n.getNodeId().equalsIgnoreCase(entityGraph.getSources().findFirst().get().getId())).findFirst().get();
        Map<String, Object> srcResults = srcNodeResults.getOutputs().values().stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v.getValue()));
        assertEquals(Map.of("srcfield1", "Value1", "srcfield2", "Value2"), srcResults);
        assertEquals(TestNodeResult.Status.COMPLETED, srcNodeResults.getStatus());

        // check core node output
        TestNodeResult coreNodeResults = all.get(0).getNodeResults().stream().filter(n -> n.getNodeId().equalsIgnoreCase(entityGraph.getCoreNode().getId())).findFirst().get();
        Map<String, Object> results = coreNodeResults.getOutputs().values().stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v.getValue()));
        assertEquals(Map.of("corefield1", "Value1", "corefield2", "Value2"), results);
        assertEquals(TestNodeResult.Status.SUCCESS, coreNodeResults.getStatus());

        //check action node's result
        TestNodeResult actionNodeResult = all.get(0).getNodeResults().stream()
                .filter(n -> n.getNodeId().equalsIgnoreCase(
                        entityGraph.getNodes().stream().filter(node -> "convertSalesforceLead".equals(node.getName())).findFirst().get().getId()
                )).findFirst().get();
        Map<String, Object> setValueNodeResults = actionNodeResult.getOutputs().values().stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v.getValue()));
        assertEquals(Map.of("actionResult", "Executed convertSalesforceLead Action"), setValueNodeResults);
        assertEquals(TestNodeResult.Status.COMPLETED, actionNodeResult.getStatus());
    }

    @Test
    public void simulateEPWithSourceSideAction() throws InterruptedException {

        GraphContext graphContext = new GraphContext();
        EntityDefinition coreEntity = createEntityDef("coreAccount", "account", null);
        var coreField1 = createAttribute("corefield1", StringType.VALUE, coreEntity.getId());
        var coreField2 = createAttribute("corefield2", StringType.VALUE, coreEntity.getId());
        coreEntity.addField(coreField1);
        coreEntity.addField(coreField2);

        EntityDefinition srcEntity = createEntityDef("srcAccount", "Source Account", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        var srcField1 = createAttribute("srcfield1", StringType.VALUE, srcEntity.getId());
        var srcField2 = createAttribute("srcfield2", StringType.VALUE, srcEntity.getId());
        srcEntity.addField(srcField1);
        srcEntity.addField(srcField2);

        Map<String, Object> predicateMap = new HashMap<>();
        var predicates = List.of(Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", srcField1.getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "RandomValue")
                )
        );
        predicateMap.put("predicate", Map.of("predicates", predicates, "operator", "AND"));

        MappingGraph entityGraph = newGraph(coreEntity, functionService, actionRepo)
                .src(srcEntity)
                .function("filter", "Filter", predicateMap)
                .function("isFalse", "Is False")
                .function("setValueOnEntity", "Set Value", Map.of("newValue","Value2", "attributeDefinitionId", srcField2.getId()))
                .action("sendEmail")
                .connect("srcAccount", "Filter")
                .connect("Filter", "Is False")
                .connect("Is False", "Set Value")
                .connect("Set Value", "sendEmail")
                .connect("Set Value", "coreAccount").getGraph();
        MappingGraph field1Graph = newGraph(coreEntity.getFieldByName("corefield1"), functionService)
                .src(srcEntity.getFieldByName("srcfield1"))
                .connect("srcfield1", "corefield1").getGraph();
        MappingGraph field2Graph = newGraph(coreEntity.getFieldByName("corefield2"), functionService)
                .src(srcEntity.getFieldByName("srcfield2"))
                .connect("srcfield2", "corefield2").getGraph();

        graphContext.setSimulationMode(true);

        graphContext.setGraph(entityGraph).setCurrentBatch(new SimulationCurrentBatch());
        graphContext.getTestContext().setEntityGraph(entityGraph);
        graphContext.getTestContext().setAttributeGraphs(List.of(field1Graph, field2Graph));


        when(mockSchemaService.getEntity(srcEntity.getId())).thenReturn(srcEntity);
        when(mockSchemaService.getEntity(coreEntity.getId())).thenReturn(coreEntity);
        when(mockSchemaService.getAttribute(coreField1.getId())).thenReturn(coreField1);
        when(mockSchemaService.getAttribute(srcField1.getId())).thenReturn(srcField1);
        when(mockSchemaService.getAttribute(coreField2.getId())).thenReturn(coreField2);
        when(mockSchemaService.getAttribute(srcField2.getId())).thenReturn(srcField2);
        when(mockSchemaService.findEntity(srcEntity.getId())).thenReturn(Optional.of(srcEntity));
        when(mockSchemaService.findAttribute(srcField1.getId())).thenReturn(Optional.of(srcField1));
        when(mockSchemaService.findAttribute(srcField2.getId())).thenReturn(Optional.of(srcField2));

        when(mockGraphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(field1Graph, field2Graph));
        when(mockGraphService.retrieveDraftAttributeGraphs(entityGraph.getId())).thenReturn(List.of(field1Graph, field2Graph));
        when(mockGraphService.retrieve(entityGraph.getId())).thenReturn(Optional.of(entityGraph));
        when(mockGraphService.retrieveWithoutLayout(entityGraph.getId())).thenReturn(Optional.of(entityGraph));
        when(mockGraphService.retrieveDraftEntityGraph(coreEntity.getId())).thenReturn(Optional.of(entityGraph));
        when(mockGraphService.retrieve(field1Graph.getId())).thenReturn(Optional.of(field1Graph));
        when(mockGraphService.retrieveWithoutLayout(field1Graph.getId())).thenReturn(Optional.of(field1Graph));
        when(mockGraphService.retrieveDraftAttributeGraph(coreField1.getId())).thenReturn(Optional.of(field1Graph));
        when(mockGraphService.retrieve(field2Graph.getId())).thenReturn(Optional.of(field2Graph));
        when(mockGraphService.retrieveWithoutLayout(field2Graph.getId())).thenReturn(Optional.of(field2Graph));
        when(mockGraphService.retrieveDraftAttributeGraph(coreField2.getId())).thenReturn(Optional.of(field2Graph));
        when(mockGraphService.extractEntityFromNode(entityGraph.getCoreNode())).thenReturn(coreEntity);
        when(mockGraphService.extractEntityFromNode(entityGraph.getSources().findFirst().get())).thenReturn(srcEntity);

        ConnectorMetadata conmetaid = new ConnectorMetadata("conmetaid");
        conmetaid.setName("salesforce");
        Connector t = new Connector("testconnector", conmetaid, "endpojnt", "u", "p");
        t.setId("con1");
        ConnectorMetadata srcConMeta = new ConnectorMetadata("sourceConnectorMetaId");
        srcConMeta.setName("salesforce");
        Connector srcCon = new Connector("sourceConnector", srcConMeta, "endpojnt", "u", "p");
        srcCon.setId("sourceConnectorId");

        when(mockConnectorService.get("con1")).thenReturn(t);
        when(mockConnectorService.get("sourceConnectorId")).thenReturn(srcCon);
        when(mockConnectorService.getAllActive()).thenReturn(List.of(srcCon, t));
        when(attributeDefinitionCache.findAllById(List.of(coreEntity.getFieldByName("corefield1").getId(), coreEntity.getFieldByName("corefield2").getId())))
                .thenReturn(List.of(coreEntity.getFieldByName("corefield1"), coreEntity.getFieldByName("corefield2")));
        when(attributeDefinitionCache.findById(coreEntity.getFieldByName("corefield1").getId())).thenReturn(Optional.of(coreEntity.getFieldByName("corefield1")));
        when(attributeDefinitionCache.findById(coreEntity.getFieldByName("corefield2").getId())).thenReturn(Optional.of(coreEntity.getFieldByName("corefield2")));
        when(attributeDefinitionCache.findAllById(List.of(coreField1.getId()))).thenReturn(List.of(coreField1));
        when(attributeDefinitionCache.findAllById(List.of(coreField2.getId()))).thenReturn(List.of(coreField2));
        when(attributeDefinitionCache.findAllById(List.of(coreField1.getId(), coreField2.getId()))).thenReturn(List.of(coreField1, coreField2));
        when(attributeDefinitionCache.findById(srcField1.getId())).thenReturn(Optional.of(srcField1));
        when(attributeDefinitionCache.findById(srcField2.getId())).thenReturn(Optional.of(srcField2));

        when(mockUnresolvedRecordService.getUnresolvedRecords(anyString())).thenReturn(List.of());
        when(mockUnresolvedRecordService.getUnresolvedEntities(anyString(), anyString())).thenReturn(List.of());

        PipelineTest test = new PipelineTest()
                .setScope(Scope.ENTITY)
                .setTargetId(coreEntity.getId())
                .setUserId(viperContext.getUser().getId())
                .setTestConfig(new TestConfig()
                        .setInputs(List.of(new SimulationNodeInput().setNodeId(entityGraph.getSource(srcEntity.getId()).get(0).getId()).setFieldValues(
                                Map.of("srcfield1", "Value1")

                        )))
                        .setExpectedOutputs(List.of(new SimulationNodeInput().setNodeId(entityGraph.getCoreNode().getId()).setFieldValues(
                                Map.of("corefield1", "Value1", "corefield2", "Value2")
                        ))));
        PipelineTest savedTest = simulationRunner.simulationService.createEntityTest(test);
        SimulationRun simulationRun = simulationRunner.simulationService.setupSimulationRun(coreEntity.getId(), "entityTestRun1", List.of(savedTest.getId()), entityGraph);
        simulationRun.setCreatedBy(viperContext.getUser().getId());
        SimulationRun savedSimulationRun = simulationRunRepo.save(simulationRun);
        simulationRunner.simulate(savedSimulationRun.getId());
        Thread.sleep(2000);
        List<TestResult> all = testResultRepo.findBySimulationRunId(simulationRun.getId());
        assertEquals(1, all.size());

        // check core node output
        TestNodeResult coreNodeResults = all.get(0).getNodeResults().stream().filter(n -> n.getNodeId().equalsIgnoreCase(entityGraph.getCoreNode().getId())).findFirst().get();
        Map<String, Object> results = coreNodeResults.getOutputs().values().stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v.getValue()));
        assertEquals(Map.of("corefield1", "Value1", "corefield2", "Value2"), results);

        // check filter node and setValue node output
        TestNodeResult filterNodeResult = all.get(0).getNodeResults().stream()
                .filter(n -> n.getNodeId().equalsIgnoreCase(
                        entityGraph.getNodes().stream().filter(node -> "Filter".equals(node.getName())).findFirst().get().getId()
                )).findFirst().get();
        Map<String, Object> filterNodeResults = filterNodeResult.getOutputs().values().stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v.getValue()));
        assertEquals(Map.of("srcfield1", "Value1"), filterNodeResults);
        assertEquals(TestNodeResult.Status.COMPLETED, filterNodeResult.getStatus());

        TestNodeResult isFalseNodeResult = all.get(0).getNodeResults().stream()
                .filter(n -> n.getNodeId().equalsIgnoreCase(
                        entityGraph.getNodes().stream().filter(node -> "Filter".equals(node.getName())).findFirst().get().getId()
                )).findFirst().get();
        Map<String, Object> isFalseNodeResults = isFalseNodeResult.getOutputs().values().stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v.getValue()));
        assertEquals(Map.of("srcfield1", "Value1"), isFalseNodeResults);
        assertEquals(TestNodeResult.Status.COMPLETED, isFalseNodeResult.getStatus());

        TestNodeResult setValueNodeResult = all.get(0).getNodeResults().stream()
                .filter(n -> n.getNodeId().equalsIgnoreCase(
                        entityGraph.getNodes().stream().filter(node -> "Set Value".equals(node.getName())).findFirst().get().getId()
                )).findFirst().get();
        Map<String, Object> setValueNodeResults = setValueNodeResult.getOutputs().values().stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v.getValue()));
        assertEquals(Map.of("srcfield1", "Value1", "srcfield2", "Value2"), setValueNodeResults);
        assertEquals(TestNodeResult.Status.COMPLETED, setValueNodeResult.getStatus());

        //check action node's result
        TestNodeResult actionNodeResult = all.get(0).getNodeResults().stream()
                .filter(n -> n.getNodeId().equalsIgnoreCase(
                        entityGraph.getNodes().stream().filter(node -> "sendEmail".equals(node.getName())).findFirst().get().getId()
                )).findFirst().get();
        Map<String, Object> actionNodeResults = actionNodeResult.getOutputs().values().stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v.getValue()));
        assertEquals(Map.of("actionResult", "Executed sendEmail Action"), actionNodeResults);
        assertEquals(TestNodeResult.Status.COMPLETED, actionNodeResult.getStatus());

    }

    @Test
    public void simpleFPTest() throws InterruptedException {

        GraphContext graphContext = new GraphContext();
        EntityDefinition sink = createEntityDef("destAccount", "Account", createConnector("testconnector", "con1", "metaid"));
        var destField1 = createAttribute("destfield1", StringType.VALUE, sink.getId());
        var destField2 = createAttribute("destfield2", StringType.VALUE, sink.getId());
        sink.addField(destField1);
        sink.addField(destField2);

        EntityDefinition coreEntity = createEntityDef("coreAccount", "account", null);
        var coreField1 = createAttribute("corefield1", StringType.VALUE, coreEntity.getId());
        var coreField2 = createAttribute("corefield2", StringType.VALUE, coreEntity.getId());
        coreEntity.addField(coreField1);
        coreEntity.addField(coreField2);

        EntityDefinition srcEntity = createEntityDef("srcAccount", "Source Account", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        var srcField1 = createAttribute("srcfield1", StringType.VALUE, srcEntity.getId());
        var srcField2 = createAttribute("srcfield2", StringType.VALUE, srcEntity.getId());
        srcEntity.addField(srcField1);
        srcEntity.addField(srcField2);

        MappingGraph entityGraph = newGraph(coreEntity, functionService)
                .src(srcEntity)
                .dest(sink)
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "destAccount").getGraph();
        MappingGraph field1Graph = newGraph(coreEntity.getFieldByName("corefield1"), functionService)
                .src(srcEntity.getFieldByName("srcfield1"))
                .dest(sink.getFieldByName("destfield1"))
                .connect("srcfield1", "corefield1")
                .connect("corefield1", "destfield1").getGraph();
        MappingGraph field2Graph = newGraph(coreEntity.getFieldByName("corefield2"), functionService)
                .src(srcEntity.getFieldByName("srcfield2"))
                .dest(sink.getFieldByName("destfield2"))
                .connect("srcfield2", "corefield2")
                .connect("corefield2", "destfield2").getGraph();

        graphContext.setSimulationMode(true);
        graphContext.setGraph(entityGraph).setCurrentBatch(new SimulationCurrentBatch());
        graphContext.getTestContext().setEntityGraph(entityGraph);
        graphContext.getTestContext().setAttributeGraphs(List.of(field1Graph, field2Graph));


        when(mockSchemaService.getEntity(srcEntity.getId())).thenReturn(srcEntity);
        when(mockSchemaService.getEntity(sink.getId())).thenReturn(sink);
        when(mockSchemaService.getEntity(coreEntity.getId())).thenReturn(coreEntity);
        when(mockSchemaService.getAttribute(coreField1.getId())).thenReturn(coreField1);
        when(mockSchemaService.getAttribute(srcField1.getId())).thenReturn(srcField1);
        when(mockSchemaService.getAttribute(destField1.getId())).thenReturn(destField1);
        when(mockSchemaService.getAttribute(coreField2.getId())).thenReturn(coreField2);
        when(mockSchemaService.getAttribute(srcField2.getId())).thenReturn(srcField2);
        when(mockSchemaService.getAttribute(destField2.getId())).thenReturn(destField2);

        when(mockGraphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(field1Graph, field2Graph));
        when(mockGraphService.retrieveDraftAttributeGraphs(entityGraph.getId())).thenReturn(List.of(field1Graph, field2Graph));
        when(mockGraphService.retrieve(entityGraph.getId())).thenReturn(Optional.of(entityGraph));
        when(mockGraphService.retrieveWithoutLayout(entityGraph.getId())).thenReturn(Optional.of(entityGraph));
        when(mockGraphService.retrieveDraftEntityGraph(coreEntity.getId())).thenReturn(Optional.of(entityGraph));
        when(mockGraphService.retrieve(field1Graph.getId())).thenReturn(Optional.of(field1Graph));
        when(mockGraphService.retrieveWithoutLayout(field1Graph.getId())).thenReturn(Optional.of(field1Graph));
        when(mockGraphService.retrieveDraftAttributeGraph(coreField1.getId())).thenReturn(Optional.of(field1Graph));
        when(mockGraphService.retrieve(field2Graph.getId())).thenReturn(Optional.of(field2Graph));
        when(mockGraphService.retrieveWithoutLayout(field2Graph.getId())).thenReturn(Optional.of(field2Graph));
        when(mockGraphService.retrieveDraftAttributeGraph(coreField2.getId())).thenReturn(Optional.of(field2Graph));
        when(mockGraphService.extractEntityFromNode(entityGraph.getCoreNode())).thenReturn(coreEntity);
        when(mockGraphService.extractEntityFromNode(entityGraph.getSources().findFirst().get())).thenReturn(srcEntity);
        when(mockGraphService.extractEntityFromNode(entityGraph.getSinks().findFirst().get())).thenReturn(sink);
        when(mockGraphService.extractAttributeFromNode(field1Graph.getSources().findFirst().get())).thenReturn(srcField1);
        when(mockGraphService.extractAttributeFromNode(field1Graph.getCoreNode())).thenReturn(coreField1);
        when(mockGraphService.extractAttributeFromNode(field1Graph.getSinks().findFirst().get())).thenReturn(destField1);

        ConnectorMetadata conmetaid = new ConnectorMetadata("conmetaid");
        conmetaid.setName("salesforce");
        Connector t = new Connector("testconnector", conmetaid, "endpojnt", "u", "p");
        t.setId("con1");
        ConnectorMetadata srcConMeta = new ConnectorMetadata("sourceConnectorMetaId");
        srcConMeta.setName("salesforce");
        Connector srcCon = new Connector("sourceConnector", srcConMeta, "endpojnt", "u", "p");
        srcCon.setId("sourceConnectorId");

        when(mockConnectorService.get("con1")).thenReturn(t);
        when(mockConnectorService.get("sourceConnectorId")).thenReturn(srcCon);
        when(mockConnectorService.getAllActive()).thenReturn(List.of(srcCon, t));
        EntityData saved = new EntityData("account").addValue("corefield1", "Value1").addValue("corefield2", "Value2");
        when(attributeDefinitionCache.findAllById(List.of(coreEntity.getFieldByName("corefield1").getId(), coreEntity.getFieldByName("corefield2").getId())))
                .thenReturn(List.of(coreEntity.getFieldByName("corefield1"), coreEntity.getFieldByName("corefield2")));
        when(attributeDefinitionCache.findById(coreEntity.getFieldByName("corefield1").getId())).thenReturn(Optional.of(coreEntity.getFieldByName("corefield1")));
        when(attributeDefinitionCache.findById(coreEntity.getFieldByName("corefield2").getId())).thenReturn(Optional.of(coreEntity.getFieldByName("corefield2")));
        when(attributeDefinitionCache.findAllById(List.of(coreField1.getId()))).thenReturn(List.of(coreField1));
        when(attributeDefinitionCache.findAllById(List.of(coreField2.getId()))).thenReturn(List.of(coreField2));
        when(attributeDefinitionCache.findAllById(List.of(coreField1.getId(), coreField2.getId()))).thenReturn(List.of(coreField1, coreField2));

        when(mockUnresolvedRecordService.getUnresolvedRecords(anyString())).thenReturn(List.of());
        when(mockUnresolvedRecordService.getUnresolvedEntities(anyString(), anyString())).thenReturn(List.of());

        PipelineTest test = new PipelineTest()
                .setName("fieldTest1")
                .setScope(Scope.ATTRIBUTE)
                .setTargetId(coreField1.getId())
                .setUserId(viperContext.getUser().getId())
                .setTestConfig(new TestConfig()
                        .setInputs(List.of(new SimulationNodeInput().setNodeId(field1Graph.getSource(srcField1.getId()).get(0).getId()).setFieldValues(
                                Map.of("srcfield1", "Value1")

                        )))
                        .setExpectedOutputs(List.of(new SimulationNodeInput().setNodeId(field1Graph.getSink(destField1.getId()).get(0).getId()).setFieldValues(
                                Map.of("destfield1", "Value1")
                        ))));
        PipelineTest savedTest = simulationRunner.simulationService.createFieldTest(test);
        SimulationRun simulationRun = simulationRunner.simulationService.setupSimulationRun(coreField1.getId(), "fieldTestRun1", List.of(savedTest.getId()), field1Graph);
        simulationRun.setCreatedBy(viperContext.getUser().getId());
        SimulationRun savedSimulationRun = simulationRunRepo.save(simulationRun);

        // assert injected dependencies in simulationRunner
        assertTrue(simulationRunner.executionFactory.getGraphRunner().dataStoreService instanceof DatastoreSimulationService);
        assertTrue(simulationRunner.executionFactory.getGraphRunner().eventStore instanceof SimulationEventStore);
        assertTrue(simulationRunner.executionFactory.getGraphRunner().entityRepo instanceof SimulationEntityRepo);
        simulationRunner.simulate(savedSimulationRun.getId());
        Thread.sleep(1000);
        List<TestResult> all = testResultRepo.findBySimulationRunId(simulationRun.getId());
        assertEquals(1, all.size());
        //destination node output
        TestNodeResult destinationNodeResults = all.get(0).getNodeResults().stream()
                .filter(n -> n.getNodeId().equalsIgnoreCase(field1Graph.getSinks().findFirst().get().getId()))
                .findFirst().get();
        Map<String, Object> results = destinationNodeResults.getOutputs().values().stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v.getValue()));
        assertEquals(Map.of("destfield1", "Value1"), results);

    }

    @Test
    public void simpleFPWithListExpectedTest() throws InterruptedException {

        GraphContext graphContext = new GraphContext();
        EntityDefinition sink = createEntityDef("destAccount", "Account", createConnector("testconnector", "con1", "metaid"));
        var destField1 = createAttribute("destfield1", StringType.VALUE, sink.getId());
        var destField2 = createAttribute("destfield2", StringType.VALUE, sink.getId());
        destField1.setMultiValueField(true);
        sink.addField(destField1);
        sink.addField(destField2);

        EntityDefinition coreEntity = createEntityDef("coreAccount", "account", null);
        var coreField1 = createAttribute("corefield1", StringType.VALUE, coreEntity.getId());
        var coreField2 = createAttribute("corefield2", StringType.VALUE, coreEntity.getId());
        coreField1.setMultiValueField(true);
        coreEntity.addField(coreField1);
        coreEntity.addField(coreField2);

        EntityDefinition srcEntity = createEntityDef("srcAccount", "Source Account", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        var srcField1 = createAttribute("srcfield1", StringType.VALUE, srcEntity.getId());
        var srcField2 = createAttribute("srcfield2", StringType.VALUE, srcEntity.getId());
        srcEntity.addField(srcField1);
        srcEntity.addField(srcField2);
        srcField1.setMultiValueField(true);
        MappingGraph entityGraph = newGraph(coreEntity, functionService)
                .src(srcEntity)
                .dest(sink)
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "destAccount").getGraph();
        MappingGraph field1Graph = newGraph(coreEntity.getFieldByName("corefield1"), functionService)
                .src(srcEntity.getFieldByName("srcfield1"))
                .dest(sink.getFieldByName("destfield1"))
                .connect("srcfield1", "corefield1")
                .connect("corefield1", "destfield1").getGraph();
        MappingGraph field2Graph = newGraph(coreEntity.getFieldByName("corefield2"), functionService)
                .src(srcEntity.getFieldByName("srcfield2"))
                .dest(sink.getFieldByName("destfield2"))
                .connect("srcfield2", "corefield2")
                .connect("corefield2", "destfield2").getGraph();

        graphContext.setSimulationMode(true);
        graphContext.setGraph(entityGraph).setCurrentBatch(new SimulationCurrentBatch());
        graphContext.getTestContext().setEntityGraph(entityGraph);
        graphContext.getTestContext().setAttributeGraphs(List.of(field1Graph, field2Graph));


        when(mockSchemaService.getEntity(srcEntity.getId())).thenReturn(srcEntity);
        when(mockSchemaService.getEntity(sink.getId())).thenReturn(sink);
        when(mockSchemaService.getEntity(coreEntity.getId())).thenReturn(coreEntity);
        when(mockSchemaService.getAttribute(coreField1.getId())).thenReturn(coreField1);
        when(mockSchemaService.getAttribute(srcField1.getId())).thenReturn(srcField1);
        when(mockSchemaService.getAttribute(destField1.getId())).thenReturn(destField1);
        when(mockSchemaService.getAttribute(coreField2.getId())).thenReturn(coreField2);
        when(mockSchemaService.getAttribute(srcField2.getId())).thenReturn(srcField2);
        when(mockSchemaService.getAttribute(destField2.getId())).thenReturn(destField2);

        when(mockGraphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(field1Graph, field2Graph));
        when(mockGraphService.retrieveDraftAttributeGraphs(entityGraph.getId())).thenReturn(List.of(field1Graph, field2Graph));
        when(mockGraphService.retrieve(entityGraph.getId())).thenReturn(Optional.of(entityGraph));
        when(mockGraphService.retrieveWithoutLayout(entityGraph.getId())).thenReturn(Optional.of(entityGraph));
        when(mockGraphService.retrieveDraftEntityGraph(coreEntity.getId())).thenReturn(Optional.of(entityGraph));
        when(mockGraphService.retrieve(field1Graph.getId())).thenReturn(Optional.of(field1Graph));
        when(mockGraphService.retrieveWithoutLayout(field1Graph.getId())).thenReturn(Optional.of(field1Graph));
        when(mockGraphService.retrieveDraftAttributeGraph(coreField1.getId())).thenReturn(Optional.of(field1Graph));
        when(mockGraphService.retrieve(field2Graph.getId())).thenReturn(Optional.of(field2Graph));
        when(mockGraphService.retrieveWithoutLayout(field2Graph.getId())).thenReturn(Optional.of(field2Graph));
        when(mockGraphService.retrieveDraftAttributeGraph(coreField2.getId())).thenReturn(Optional.of(field2Graph));
        when(mockGraphService.extractEntityFromNode(entityGraph.getCoreNode())).thenReturn(coreEntity);
        when(mockGraphService.extractEntityFromNode(entityGraph.getSources().findFirst().get())).thenReturn(srcEntity);
        when(mockGraphService.extractEntityFromNode(entityGraph.getSinks().findFirst().get())).thenReturn(sink);
        when(mockGraphService.extractAttributeFromNode(field1Graph.getSources().findFirst().get())).thenReturn(srcField1);
        when(mockGraphService.extractAttributeFromNode(field1Graph.getCoreNode())).thenReturn(coreField1);
        when(mockGraphService.extractAttributeFromNode(field1Graph.getSinks().findFirst().get())).thenReturn(destField1);

        ConnectorMetadata conmetaid = new ConnectorMetadata("conmetaid");
        conmetaid.setName("salesforce");
        Connector t = new Connector("testconnector", conmetaid, "endpojnt", "u", "p");
        t.setId("con1");
        ConnectorMetadata srcConMeta = new ConnectorMetadata("sourceConnectorMetaId");
        srcConMeta.setName("salesforce");
        Connector srcCon = new Connector("sourceConnector", srcConMeta, "endpojnt", "u", "p");
        srcCon.setId("sourceConnectorId");

        when(mockConnectorService.get("con1")).thenReturn(t);
        when(mockConnectorService.get("sourceConnectorId")).thenReturn(srcCon);
        when(mockConnectorService.getAllActive()).thenReturn(List.of(srcCon, t));
        EntityData saved = new EntityData("account").addValue("corefield1", "Value1").addValue("corefield2", "Value2");
        when(attributeDefinitionCache.findAllById(List.of(coreEntity.getFieldByName("corefield1").getId(), coreEntity.getFieldByName("corefield2").getId())))
                .thenReturn(List.of(coreEntity.getFieldByName("corefield1"), coreEntity.getFieldByName("corefield2")));
        when(attributeDefinitionCache.findById(coreEntity.getFieldByName("corefield1").getId())).thenReturn(Optional.of(coreEntity.getFieldByName("corefield1")));
        when(attributeDefinitionCache.findById(coreEntity.getFieldByName("corefield2").getId())).thenReturn(Optional.of(coreEntity.getFieldByName("corefield2")));
        when(attributeDefinitionCache.findAllById(List.of(coreField1.getId()))).thenReturn(List.of(coreField1));
        when(attributeDefinitionCache.findAllById(List.of(coreField2.getId()))).thenReturn(List.of(coreField2));
        when(attributeDefinitionCache.findAllById(List.of(coreField1.getId(), coreField2.getId()))).thenReturn(List.of(coreField1, coreField2));

        when(mockUnresolvedRecordService.getUnresolvedRecords(anyString())).thenReturn(List.of());
        when(mockUnresolvedRecordService.getUnresolvedEntities(anyString(), anyString())).thenReturn(List.of());

        PipelineTest test = new PipelineTest()
                .setName("fieldTest1")
                .setScope(Scope.ATTRIBUTE)
                .setTargetId(coreField1.getId())
                .setUserId(viperContext.getUser().getId())
                .setTestConfig(new TestConfig()
                        .setInputs(List.of(new SimulationNodeInput().setNodeId(field1Graph.getSource(srcField1.getId()).get(0).getId()).setFieldValues(
                                Map.of("srcfield1", List.of("Value1","Value2","Value3"))

                        )))
                        .setExpectedOutputs(List.of(new SimulationNodeInput().setNodeId(field1Graph.getSink(destField1.getId()).get(0).getId()).setFieldValues(
                                Map.of("destfield1",  List.of("Value1","Value2","Value3"))
                        ))));
        PipelineTest savedTest = simulationRunner.simulationService.createFieldTest(test);
        SimulationRun simulationRun = simulationRunner.simulationService.setupSimulationRun(coreField1.getId(), "fieldTestRun1", List.of(savedTest.getId()), field1Graph);
        simulationRun.setCreatedBy(viperContext.getUser().getId());
        SimulationRun savedSimulationRun = simulationRunRepo.save(simulationRun);

        // assert injected dependencies in simulationRunner
        assertTrue(simulationRunner.executionFactory.getGraphRunner().dataStoreService instanceof DatastoreSimulationService);
        assertTrue(simulationRunner.executionFactory.getGraphRunner().eventStore instanceof SimulationEventStore);
        assertTrue(simulationRunner.executionFactory.getGraphRunner().entityRepo instanceof SimulationEntityRepo);
        simulationRunner.simulate(savedSimulationRun.getId());
        Thread.sleep(1000);
        List<TestResult> all = testResultRepo.findBySimulationRunId(simulationRun.getId());
        assertEquals(1, all.size());
        //destination node output
        TestNodeResult destinationNodeResults = all.get(0).getNodeResults().stream()
                .filter(n -> n.getNodeId().equalsIgnoreCase(field1Graph.getSinks().findFirst().get().getId()))
                .findFirst().get();
        Map<String, Object> results = destinationNodeResults.getOutputs().values().stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v.getValue()));
        assertEquals(Map.of("destfield1", List.of("Value1","Value2","Value3")), results);
    }

    @Test
    public void simpleFPWithEmptyListExpectedTest() throws InterruptedException {

        GraphContext graphContext = new GraphContext();
        EntityDefinition sink = createEntityDef("destAccount", "Account", createConnector("testconnector", "con1", "metaid"));
        var destField1 = createAttribute("destfield1", StringType.VALUE, sink.getId());
        var destField2 = createAttribute("destfield2", StringType.VALUE, sink.getId());
        destField1.setMultiValueField(true);
        sink.addField(destField1);
        sink.addField(destField2);

        EntityDefinition coreEntity = createEntityDef("coreAccount", "account", null);
        var coreField1 = createAttribute("corefield1", StringType.VALUE, coreEntity.getId());
        var coreField2 = createAttribute("corefield2", StringType.VALUE, coreEntity.getId());
        coreField1.setMultiValueField(true);
        coreEntity.addField(coreField1);
        coreEntity.addField(coreField2);

        EntityDefinition srcEntity = createEntityDef("srcAccount", "Source Account", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        var srcField1 = createAttribute("srcfield1", StringType.VALUE, srcEntity.getId());
        var srcField2 = createAttribute("srcfield2", StringType.VALUE, srcEntity.getId());
        srcEntity.addField(srcField1);
        srcEntity.addField(srcField2);
        srcField1.setMultiValueField(true);
        MappingGraph entityGraph = newGraph(coreEntity, functionService)
                .src(srcEntity)
                .dest(sink)
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "destAccount").getGraph();
        MappingGraph field1Graph = newGraph(coreEntity.getFieldByName("corefield1"), functionService)
                .src(srcEntity.getFieldByName("srcfield1"))
                .dest(sink.getFieldByName("destfield1"))
                .connect("srcfield1", "corefield1")
                .connect("corefield1", "destfield1").getGraph();
        MappingGraph field2Graph = newGraph(coreEntity.getFieldByName("corefield2"), functionService)
                .src(srcEntity.getFieldByName("srcfield2"))
                .dest(sink.getFieldByName("destfield2"))
                .connect("srcfield2", "corefield2")
                .connect("corefield2", "destfield2").getGraph();

        graphContext.setSimulationMode(true);
        graphContext.setGraph(entityGraph).setCurrentBatch(new SimulationCurrentBatch());
        graphContext.getTestContext().setEntityGraph(entityGraph);
        graphContext.getTestContext().setAttributeGraphs(List.of(field1Graph, field2Graph));


        when(mockSchemaService.getEntity(srcEntity.getId())).thenReturn(srcEntity);
        when(mockSchemaService.getEntity(sink.getId())).thenReturn(sink);
        when(mockSchemaService.getEntity(coreEntity.getId())).thenReturn(coreEntity);
        when(mockSchemaService.getAttribute(coreField1.getId())).thenReturn(coreField1);
        when(mockSchemaService.getAttribute(srcField1.getId())).thenReturn(srcField1);
        when(mockSchemaService.getAttribute(destField1.getId())).thenReturn(destField1);
        when(mockSchemaService.getAttribute(coreField2.getId())).thenReturn(coreField2);
        when(mockSchemaService.getAttribute(srcField2.getId())).thenReturn(srcField2);
        when(mockSchemaService.getAttribute(destField2.getId())).thenReturn(destField2);

        when(mockGraphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(field1Graph, field2Graph));
        when(mockGraphService.retrieveDraftAttributeGraphs(entityGraph.getId())).thenReturn(List.of(field1Graph, field2Graph));
        when(mockGraphService.retrieve(entityGraph.getId())).thenReturn(Optional.of(entityGraph));
        when(mockGraphService.retrieveWithoutLayout(entityGraph.getId())).thenReturn(Optional.of(entityGraph));
        when(mockGraphService.retrieveDraftEntityGraph(coreEntity.getId())).thenReturn(Optional.of(entityGraph));
        when(mockGraphService.retrieve(field1Graph.getId())).thenReturn(Optional.of(field1Graph));
        when(mockGraphService.retrieveWithoutLayout(field1Graph.getId())).thenReturn(Optional.of(field1Graph));
        when(mockGraphService.retrieveDraftAttributeGraph(coreField1.getId())).thenReturn(Optional.of(field1Graph));
        when(mockGraphService.retrieve(field2Graph.getId())).thenReturn(Optional.of(field2Graph));
        when(mockGraphService.retrieveWithoutLayout(field2Graph.getId())).thenReturn(Optional.of(field2Graph));
        when(mockGraphService.retrieveDraftAttributeGraph(coreField2.getId())).thenReturn(Optional.of(field2Graph));
        when(mockGraphService.extractEntityFromNode(entityGraph.getCoreNode())).thenReturn(coreEntity);
        when(mockGraphService.extractEntityFromNode(entityGraph.getSources().findFirst().get())).thenReturn(srcEntity);
        when(mockGraphService.extractEntityFromNode(entityGraph.getSinks().findFirst().get())).thenReturn(sink);
        when(mockGraphService.extractAttributeFromNode(field1Graph.getSources().findFirst().get())).thenReturn(srcField1);
        when(mockGraphService.extractAttributeFromNode(field1Graph.getCoreNode())).thenReturn(coreField1);
        when(mockGraphService.extractAttributeFromNode(field1Graph.getSinks().findFirst().get())).thenReturn(destField1);

        ConnectorMetadata conmetaid = new ConnectorMetadata("conmetaid");
        conmetaid.setName("salesforce");
        Connector t = new Connector("testconnector", conmetaid, "endpojnt", "u", "p");
        t.setId("con1");
        ConnectorMetadata srcConMeta = new ConnectorMetadata("sourceConnectorMetaId");
        srcConMeta.setName("salesforce");
        Connector srcCon = new Connector("sourceConnector", srcConMeta, "endpojnt", "u", "p");
        srcCon.setId("sourceConnectorId");

        when(mockConnectorService.get("con1")).thenReturn(t);
        when(mockConnectorService.get("sourceConnectorId")).thenReturn(srcCon);
        when(mockConnectorService.getAllActive()).thenReturn(List.of(srcCon, t));
        EntityData saved = new EntityData("account").addValue("corefield1", "Value1").addValue("corefield2", "Value2");
        when(attributeDefinitionCache.findAllById(List.of(coreEntity.getFieldByName("corefield1").getId(), coreEntity.getFieldByName("corefield2").getId())))
                .thenReturn(List.of(coreEntity.getFieldByName("corefield1"), coreEntity.getFieldByName("corefield2")));
        when(attributeDefinitionCache.findById(coreEntity.getFieldByName("corefield1").getId())).thenReturn(Optional.of(coreEntity.getFieldByName("corefield1")));
        when(attributeDefinitionCache.findById(coreEntity.getFieldByName("corefield2").getId())).thenReturn(Optional.of(coreEntity.getFieldByName("corefield2")));
        when(attributeDefinitionCache.findAllById(List.of(coreField1.getId()))).thenReturn(List.of(coreField1));
        when(attributeDefinitionCache.findAllById(List.of(coreField2.getId()))).thenReturn(List.of(coreField2));
        when(attributeDefinitionCache.findAllById(List.of(coreField1.getId(), coreField2.getId()))).thenReturn(List.of(coreField1, coreField2));

        when(mockUnresolvedRecordService.getUnresolvedRecords(anyString())).thenReturn(List.of());
        when(mockUnresolvedRecordService.getUnresolvedEntities(anyString(), anyString())).thenReturn(List.of());

        PipelineTest test = new PipelineTest()
                .setName("fieldTest1")
                .setScope(Scope.ATTRIBUTE)
                .setTargetId(coreField1.getId())
                .setUserId(viperContext.getUser().getId())
                .setTestConfig(new TestConfig()
                        .setInputs(List.of(new SimulationNodeInput().setNodeId(field1Graph.getSource(srcField1.getId()).get(0).getId()).setFieldValues(
                                Map.of("srcfield1", List.of())

                        )))
                        .setExpectedOutputs(List.of(new SimulationNodeInput().setNodeId(field1Graph.getSink(destField1.getId()).get(0).getId()).setFieldValues(
                                Map.of("destfield1",  List.of())
                        ))));
        PipelineTest savedTest = simulationRunner.simulationService.createFieldTest(test);
        SimulationRun simulationRun = simulationRunner.simulationService.setupSimulationRun(coreField1.getId(), "fieldTestRun1", List.of(savedTest.getId()), field1Graph);
        simulationRun.setCreatedBy(viperContext.getUser().getId());
        SimulationRun savedSimulationRun = simulationRunRepo.save(simulationRun);

        // assert injected dependencies in simulationRunner
        assertTrue(simulationRunner.executionFactory.getGraphRunner().dataStoreService instanceof DatastoreSimulationService);
        assertTrue(simulationRunner.executionFactory.getGraphRunner().eventStore instanceof SimulationEventStore);
        assertTrue(simulationRunner.executionFactory.getGraphRunner().entityRepo instanceof SimulationEntityRepo);
        simulationRunner.simulate(savedSimulationRun.getId());
        Thread.sleep(1000);
        List<TestResult> all = testResultRepo.findBySimulationRunId(simulationRun.getId());
        assertEquals(1, all.size());
        //destination node output
        TestNodeResult destinationNodeResults = all.get(0).getNodeResults().stream()
                .filter(n -> n.getNodeId().equalsIgnoreCase(field1Graph.getSinks().findFirst().get().getId()))
                .findFirst().get();
        Map<String, Object> results = destinationNodeResults.getOutputs().values().stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v.getValue()));
        assertEquals(Map.of("destfield1", List.of()), results);
    }

    @Test
    public void simulateFPWithInlineAction() throws InterruptedException {

        GraphContext graphContext = new GraphContext();
        EntityDefinition sink = createEntityDef("destAccount", "Account", createConnector("testconnector", "con1", "metaid"));
        var destField1 = createAttribute("destfield1", StringType.VALUE, sink.getId());
        var destField2 = createAttribute("destfield2", StringType.VALUE, sink.getId());
        sink.addField(destField1);
        sink.addField(destField2);

        EntityDefinition coreEntity = createEntityDef("coreAccount", "account", null);
        var coreField1 = createAttribute("corefield1", StringType.VALUE, coreEntity.getId());
        var coreField2 = createAttribute("corefield2", StringType.VALUE, coreEntity.getId());
        coreEntity.addField(coreField1);
        coreEntity.addField(coreField2);

        EntityDefinition srcEntity = createEntityDef("srcAccount", "Source Account", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        var srcField1 = createAttribute("srcfield1", StringType.VALUE, srcEntity.getId());
        var srcField2 = createAttribute("srcfield2", StringType.VALUE, srcEntity.getId());
        srcEntity.addField(srcField1);
        srcEntity.addField(srcField2);

        MappingGraph entityGraph = newGraph(coreEntity, functionService, actionRepo)
                .src(srcEntity)
                .dest(sink)
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "destAccount").getGraph();
        MappingGraph field1Graph = newGraph(coreEntity.getFieldByName("corefield1"), functionService, actionRepo)
                .src(srcEntity.getFieldByName("srcfield1"))
                .dest(sink.getFieldByName("destfield1"))
                .action("convertSalesforceLead")
                .connect("srcfield1", "convertSalesforceLead")
                .connect("convertSalesforceLead", "corefield1")
                .connect("corefield1", "destfield1").getGraph();
        MappingGraph field2Graph = newGraph(coreEntity.getFieldByName("corefield2"), functionService, actionRepo)
                .src(srcEntity.getFieldByName("srcfield2"))
                .dest(sink.getFieldByName("destfield2"))
                .connect("srcfield2", "corefield2")
                .connect("corefield2", "destfield2").getGraph();

        graphContext.setSimulationMode(true);
        graphContext.setGraph(entityGraph).setCurrentBatch(new SimulationCurrentBatch());
        graphContext.getTestContext().setEntityGraph(entityGraph);
        graphContext.getTestContext().setAttributeGraphs(List.of(field1Graph, field2Graph));


        when(mockSchemaService.getEntity(srcEntity.getId())).thenReturn(srcEntity);
        when(mockSchemaService.getEntity(sink.getId())).thenReturn(sink);
        when(mockSchemaService.getEntity(coreEntity.getId())).thenReturn(coreEntity);
        when(mockSchemaService.getAttribute(coreField1.getId())).thenReturn(coreField1);
        when(mockSchemaService.getAttribute(srcField1.getId())).thenReturn(srcField1);
        when(mockSchemaService.getAttribute(destField1.getId())).thenReturn(destField1);
        when(mockSchemaService.getAttribute(coreField2.getId())).thenReturn(coreField2);
        when(mockSchemaService.getAttribute(srcField2.getId())).thenReturn(srcField2);
        when(mockSchemaService.getAttribute(destField2.getId())).thenReturn(destField2);

        when(mockGraphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(field1Graph, field2Graph));
        when(mockGraphService.retrieveDraftAttributeGraphs(entityGraph.getId())).thenReturn(List.of(field1Graph, field2Graph));
        when(mockGraphService.retrieve(entityGraph.getId())).thenReturn(Optional.of(entityGraph));
        when(mockGraphService.retrieveWithoutLayout(entityGraph.getId())).thenReturn(Optional.of(entityGraph));
        when(mockGraphService.retrieveDraftEntityGraph(coreEntity.getId())).thenReturn(Optional.of(entityGraph));
        when(mockGraphService.retrieve(field1Graph.getId())).thenReturn(Optional.of(field1Graph));
        when(mockGraphService.retrieveWithoutLayout(field1Graph.getId())).thenReturn(Optional.of(field1Graph));
        when(mockGraphService.retrieveDraftAttributeGraph(coreField1.getId())).thenReturn(Optional.of(field1Graph));
        when(mockGraphService.retrieve(field2Graph.getId())).thenReturn(Optional.of(field2Graph));
        when(mockGraphService.retrieveWithoutLayout(field2Graph.getId())).thenReturn(Optional.of(field2Graph));
        when(mockGraphService.retrieveDraftAttributeGraph(coreField2.getId())).thenReturn(Optional.of(field2Graph));
        when(mockGraphService.extractEntityFromNode(entityGraph.getCoreNode())).thenReturn(coreEntity);
        when(mockGraphService.extractEntityFromNode(entityGraph.getSources().findFirst().get())).thenReturn(srcEntity);
        when(mockGraphService.extractEntityFromNode(entityGraph.getSinks().findFirst().get())).thenReturn(sink);
        when(mockGraphService.extractAttributeFromNode(field1Graph.getSources().findFirst().get())).thenReturn(srcField1);
        when(mockGraphService.extractAttributeFromNode(field1Graph.getCoreNode())).thenReturn(coreField1);
        when(mockGraphService.extractAttributeFromNode(field1Graph.getSinks().findFirst().get())).thenReturn(destField1);

        ConnectorMetadata conmetaid = new ConnectorMetadata("conmetaid");
        conmetaid.setName("salesforce");
        Connector t = new Connector("testconnector", conmetaid, "endpojnt", "u", "p");
        t.setId("con1");
        ConnectorMetadata srcConMeta = new ConnectorMetadata("sourceConnectorMetaId");
        srcConMeta.setName("salesforce");
        Connector srcCon = new Connector("sourceConnector", srcConMeta, "endpojnt", "u", "p");
        srcCon.setId("sourceConnectorId");

        when(mockConnectorService.get("con1")).thenReturn(t);
        when(mockConnectorService.get("sourceConnectorId")).thenReturn(srcCon);
        when(mockConnectorService.getAllActive()).thenReturn(List.of(srcCon, t));
        EntityData saved = new EntityData("account").addValue("corefield1", "Value1").addValue("corefield2", "Value2");
        when(attributeDefinitionCache.findAllById(List.of(coreEntity.getFieldByName("corefield1").getId(), coreEntity.getFieldByName("corefield2").getId())))
                .thenReturn(List.of(coreEntity.getFieldByName("corefield1"), coreEntity.getFieldByName("corefield2")));
        when(attributeDefinitionCache.findById(coreEntity.getFieldByName("corefield1").getId())).thenReturn(Optional.of(coreEntity.getFieldByName("corefield1")));
        when(attributeDefinitionCache.findById(coreEntity.getFieldByName("corefield2").getId())).thenReturn(Optional.of(coreEntity.getFieldByName("corefield2")));
        when(attributeDefinitionCache.findAllById(List.of(coreField1.getId()))).thenReturn(List.of(coreField1));
        when(attributeDefinitionCache.findAllById(List.of(coreField2.getId()))).thenReturn(List.of(coreField2));
        when(attributeDefinitionCache.findAllById(List.of(coreField1.getId(), coreField2.getId()))).thenReturn(List.of(coreField1, coreField2));

        when(mockUnresolvedRecordService.getUnresolvedRecords(anyString())).thenReturn(List.of());
        when(mockUnresolvedRecordService.getUnresolvedEntities(anyString(), anyString())).thenReturn(List.of());

        PipelineTest test = new PipelineTest()
                .setName("fieldTest1")
                .setScope(Scope.ATTRIBUTE)
                .setTargetId(coreField1.getId())
                .setUserId(viperContext.getUser().getId())
                .setTestConfig(new TestConfig()
                        .setInputs(List.of(new SimulationNodeInput().setNodeId(field1Graph.getSource(srcField1.getId()).get(0).getId()).setFieldValues(
                                Map.of("srcfield1", "Value1")
                        )))
                        .setExpectedOutputs(List.of(new SimulationNodeInput().setNodeId(field1Graph.getSink(destField1.getId()).get(0).getId()).setFieldValues(
                                Map.of("destfield1", "Value1")
                        ))));
        PipelineTest savedTest = simulationRunner.simulationService.createFieldTest(test);
        SimulationRun simulationRun = simulationRunner.simulationService.setupSimulationRun(coreField1.getId(), "fieldTestRun1", List.of(savedTest.getId()), field1Graph);
        simulationRun.setCreatedBy(viperContext.getUser().getId());
        SimulationRun savedSimulationRun = simulationRunRepo.save(simulationRun);

        simulationRunner.simulate(savedSimulationRun.getId());
        Thread.sleep(1000);
        List<TestResult> all = testResultRepo.findBySimulationRunId(simulationRun.getId());
        assertEquals(1, all.size());
        //destination node output
        TestNodeResult destinationNodeResults = all.get(0).getNodeResults().stream()
                .filter(n -> n.getNodeId().equalsIgnoreCase(field1Graph.getSinks().findFirst().get().getId()))
                .findFirst().get();
        Map<String, Object> results = destinationNodeResults.getOutputs().values().stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v.getValue()));
        assertEquals(Map.of("destfield1", "Value1"), results);
        assertEquals(TestNodeResult.Status.SUCCESS, destinationNodeResults.getStatus());

        // check source node output
        TestNodeResult srcNodeResults = all.get(0).getNodeResults().stream().filter(n -> n.getNodeId().equalsIgnoreCase(field1Graph.getSources().findFirst().get().getId())).findFirst().get();
        Map<String, Object> srcResults = srcNodeResults.getOutputs().values().stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v.getValue()));
        assertEquals(Map.of("srcfield1", "Value1"), srcResults);
        assertEquals(TestNodeResult.Status.COMPLETED, srcNodeResults.getStatus());

        // check core node output
        TestNodeResult coreNodeResults = all.get(0).getNodeResults().stream().filter(n -> n.getNodeId().equalsIgnoreCase(field1Graph.getCoreNode().getId())).findFirst().get();
        Map<String, Object> coreResults = coreNodeResults.getOutputs().values().stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v.getValue()));
        assertEquals(Map.of("corefield1", "Value1"), coreResults);
        assertEquals(TestNodeResult.Status.COMPLETED, coreNodeResults.getStatus());

        //check action node's result
        TestNodeResult actionNodeResult = all.get(0).getNodeResults().stream()
                .filter(n -> n.getNodeId().equalsIgnoreCase(
                        field1Graph.getNodes().stream().filter(node -> "convertSalesforceLead".equals(node.getName())).findFirst().get().getId()
                )).findFirst().get();
        Map<String, Object> setValueNodeResults = actionNodeResult.getOutputs().values().stream().collect(Collectors.toMap(v -> v.getApiName(), v -> v.getValue()));
        assertEquals(Map.of("actionResult", "Executed convertSalesforceLead Action"), setValueNodeResults);
        assertEquals(TestNodeResult.Status.COMPLETED, actionNodeResult.getStatus());

    }
}