package com.syncari.viper.streams.stages;

import com.syncari.AbstractSyncariTest;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.SyncRequest;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.ConnectorStatus;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.NodeData;
import com.syncari.core.pipeline.TestContext;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.service.*;
import com.syncari.core.simulation.SimulationCurrentBatch;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.utils.Pair;
import com.syncari.viper.simulation.ReadOnlyDataService;
import com.syncari.viper.simulation.SimulationDataServiceFactory;
import com.syncari.viper.streams.SimulationExecutionFactory;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.syncari.core.utils.GraphHelper.createConnector;
import static com.syncari.core.utils.GraphHelper.newGraph;
import static org.junit.Assert.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SaveToSinkSimulationTest extends AbstractSyncariTest {
    @Autowired
    SimulationExecutionFactory simulationExecutionFactory;
    @Autowired
    FunctionService functionService;
    @Autowired
    SyncDetailMetricService syncDetailMetricService;

    @Test
    public void simpleEPTest() {
        SaveToSink saveToSink = simulationExecutionFactory.getPipelineStages().getSaveToSink();
        saveToSink.syncDetailMetricService = syncDetailMetricService;
        GraphContext graphContext = new GraphContext();
        EntityDefinition sink = SchemaHelper.createEntityDef("destAccount", "Account", createConnector("testconnector", "con1", "metaid"));
        sink.addField(SchemaHelper.createAttribute("destfield1", StringType.VALUE, sink.getId()));
        sink.addField(SchemaHelper.createAttribute("destfield2", StringType.VALUE, sink.getId()));
        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        coreEntity.addField(SchemaHelper.createAttribute("corefield1", StringType.VALUE, sink.getId()));
        coreEntity.addField(SchemaHelper.createAttribute("corefield2", StringType.VALUE, sink.getId()));

        EntityDefinition srcEntity = SchemaHelper.createEntityDef("srcAccount", "Source Account", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        srcEntity.addField(SchemaHelper.createAttribute("srcfield1", StringType.VALUE, sink.getId()));
        srcEntity.addField(SchemaHelper.createAttribute("srcfield2", StringType.VALUE, sink.getId()));
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

        SchemaService mockSchemaService = mock(SchemaService.class);
        when(mockSchemaService.getEntity(srcEntity.getId())).thenReturn(srcEntity);
        when(mockSchemaService.getEntity(sink.getId())).thenReturn(sink);
        when(mockSchemaService.getEntity(coreEntity.getId())).thenReturn(coreEntity);
        when(mockSchemaService.refreshSynapseSchema(eq(sink.getConnectorId()), eq(sink), any())).thenReturn(List.of(sink));
        graphContext.setSyncariEntity(coreEntity);
        MappingGraphService mockGraphService = mock(MappingGraphService.class);
        when(mockGraphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(field1Graph, field2Graph));


        ConnectorService mockConnectorService = mock(ConnectorService.class);
        ConnectorMetadata conmetaid = new ConnectorMetadata("conmetaid");
        conmetaid.setName("salesforce");
        Connector t = new Connector("testconnector", conmetaid, "endpojnt", "u", "p");
        t.setId("con1");
        t.setStatus(ConnectorStatus.ACTIVE);
        when(mockConnectorService.get("con1")).thenReturn(t);
        when(mockConnectorService.refreshAuthentication(any(Connector.class))).then(returnsFirstArg());

        AttributeRepo mockAttributeRepo = mock(AttributeRepo.class);
        when(mockAttributeRepo.findAllById(List.of(coreEntity.getFieldByName("corefield1").getId(), coreEntity.getFieldByName("corefield2").getId())))
                .thenReturn(List.of(coreEntity.getFieldByName("corefield1"), coreEntity.getFieldByName("corefield2")));

        when(mockAttributeRepo.findById(coreEntity.getFieldByName("corefield1").getId())).thenReturn(Optional.of(coreEntity.getFieldByName("corefield1")));
        when(mockAttributeRepo.findById(coreEntity.getFieldByName("corefield2").getId())).thenReturn(Optional.of(coreEntity.getFieldByName("corefield2")));

        UnresolvedRecordService mockUnresolvedRecordService = mock(UnresolvedRecordService.class);
        when(mockUnresolvedRecordService.getUnresolvedRecords(anyString())).thenReturn(List.of());
        when(mockUnresolvedRecordService.getUnresolvedEntities(anyString(), anyString())).thenReturn(List.of());
        saveToSink.graphService = mockGraphService;
        saveToSink.schemaService = mockSchemaService;
        saveToSink.connectorService = mockConnectorService;
        saveToSink.attributeProxyRepo = mockAttributeRepo;
        saveToSink.unresolvedRecordService = mockUnresolvedRecordService;
        EntityRepo entityRepo = saveToSink.entityRepo;
        EntityData saved = entityRepo.save(new EntityData("account")).addValue("corefield1", "Value1").addValue("corefield2", "Value2");
        graphContext.setSimulationMode(true);

        final SimulationCurrentBatch currentBatch = createSimulationBatch();
        graphContext.setGraph(entityGraph).setCurrentBatch(currentBatch);
        graphContext.getTestContext().setEntityGraph(entityGraph);
        graphContext.getTestContext().setAttributeGraphs(List.of(field1Graph, field2Graph));
        saveToSink.execute(sink, viperContext, graphContext);
        Pair<FunctionResult, MappingNode> result = (Pair<FunctionResult, MappingNode>) graphContext.get("output_" + entityGraph.getSink(sink.getId()).get(0).getId());
        SimulationDataServiceFactory simulationDataServiceFactory = (SimulationDataServiceFactory) saveToSink.dataServiceFactory;
        ReadOnlyDataService simulatedDataService = (ReadOnlyDataService) simulationDataServiceFactory.getDataService(conmetaid);
        assertEquals(1, simulatedDataService.getCreates().size());
        SyncRequest createRequest = simulatedDataService.getCreates().iterator().next();
        EntityData entityData = createRequest.getData().get("con1").get(0);

        assertEquals(sink.getApiName(), entityData.getName());
        assertEquals("Value2", entityData.getValueAsString("destfield2"));
        assertEquals("Value1", entityData.getValueAsString("destfield1"));
        TestContext testContext = graphContext.getTestContext();
        Map<String, Map<String, NodeData>> dataSnapshot = testContext.getDataSnapshot();
        assertTrue(dataSnapshot.containsKey(saved.getId()));
        assertEquals(saved.getId(), graphContext.getCurrentSyncariId());
        assertEquals(saved.getId(), entityData.getSyncariEntityId());
        assertNotNull(dataSnapshot.get(saved.getId()).get(entityGraph.getSinks().findFirst().get().getId()));
    }


    @Test
    public void EPWithFunctions() {
        SaveToSink saveToSink = simulationExecutionFactory.getPipelineStages().getSaveToSink();
        saveToSink.syncDetailMetricService = syncDetailMetricService;
        GraphContext graphContext = new GraphContext();
        EntityDefinition sink = SchemaHelper.createEntityDef("destAccount", "Account", createConnector("testconnector", "con1", "metaid"));
        sink.addField(SchemaHelper.createAttribute("destfield1", StringType.VALUE, sink.getId()));
        sink.addField(SchemaHelper.createAttribute("destfield2", StringType.VALUE, sink.getId()));
        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        coreEntity.addField(SchemaHelper.createAttribute("corefield1", StringType.VALUE, sink.getId()));
        coreEntity.addField(SchemaHelper.createAttribute("corefield2", StringType.VALUE, sink.getId()));

        EntityDefinition srcEntity = SchemaHelper.createEntityDef("srcAccount", "Source Account", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        srcEntity.addField(SchemaHelper.createAttribute("srcfield1", StringType.VALUE, sink.getId()));
        srcEntity.addField(SchemaHelper.createAttribute("srcfield2", StringType.VALUE, sink.getId()));
        graphContext.setSyncariEntity(coreEntity);
        MappingGraph entityGraph = newGraph(coreEntity, functionService)
                .src(srcEntity)
                .dest(sink)
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "destAccount").getGraph();
        MappingGraph field1Graph = newGraph(coreEntity.getFieldByName("corefield1"), functionService)
                .src(srcEntity.getFieldByName("srcfield1"))
                .function("lower","lowercase1")
                .dest(sink.getFieldByName("destfield1"))
                .connect("srcfield1", "corefield1")
                .connect("corefield1", "lowercase1")
                .connect("lowercase1", "destfield1")
                .getGraph();
        MappingGraph field2Graph = newGraph(coreEntity.getFieldByName("corefield2"), functionService)
                .src(srcEntity.getFieldByName("srcfield2"))
                .dest(sink.getFieldByName("destfield2"))
                .connect("srcfield2", "corefield2")
                .connect("corefield2", "destfield2").getGraph();

        SchemaService mockSchemaService = mock(SchemaService.class);
        when(mockSchemaService.getEntity(srcEntity.getId())).thenReturn(srcEntity);
        when(mockSchemaService.getEntity(sink.getId())).thenReturn(sink);
        when(mockSchemaService.getEntity(coreEntity.getId())).thenReturn(coreEntity);
        when(mockSchemaService.refreshSynapseSchema(eq(sink.getConnectorId()), eq(sink), any())).thenReturn(List.of(sink));
        MappingGraphService mockGraphService = mock(MappingGraphService.class);
        when(mockGraphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(field1Graph, field2Graph));

        ConnectorService mockConnectorService = mock(ConnectorService.class);
        ConnectorMetadata conmetaid = new ConnectorMetadata("conmetaid");
        conmetaid.setName("salesforce");
        Connector t = new Connector("testconnector", conmetaid, "endpojnt", "u", "p");
        t.setId("con1");
        t.setStatus(ConnectorStatus.ACTIVE);
        when(mockConnectorService.get("con1")).thenReturn(t);
        when(mockConnectorService.refreshAuthentication(any(Connector.class))).then(returnsFirstArg());

        AttributeRepo mockAttributeRepo = mock(AttributeRepo.class);
        when(mockAttributeRepo.findAllById(List.of(coreEntity.getFieldByName("corefield1").getId(), coreEntity.getFieldByName("corefield2").getId())))
                .thenReturn(List.of(coreEntity.getFieldByName("corefield1"), coreEntity.getFieldByName("corefield2")));

        when(mockAttributeRepo.findById(coreEntity.getFieldByName("corefield1").getId())).thenReturn(Optional.of(coreEntity.getFieldByName("corefield1")));
        when(mockAttributeRepo.findById(coreEntity.getFieldByName("corefield2").getId())).thenReturn(Optional.of(coreEntity.getFieldByName("corefield2")));

        UnresolvedRecordService mockUnresolvedRecordService = mock(UnresolvedRecordService.class);
        when(mockUnresolvedRecordService.getUnresolvedRecords(anyString())).thenReturn(List.of());
        when(mockUnresolvedRecordService.getUnresolvedEntities(anyString(), anyString())).thenReturn(List.of());
        saveToSink.graphService = mockGraphService;
        saveToSink.schemaService = mockSchemaService;
        saveToSink.connectorService = mockConnectorService;
        saveToSink.attributeProxyRepo = mockAttributeRepo;
        saveToSink.unresolvedRecordService = mockUnresolvedRecordService;
        EntityRepo entityRepo = saveToSink.entityRepo;
        EntityData saved = entityRepo.save(new EntityData("account")).addValue("corefield1", "Value1").addValue("corefield2", "Value2");
        graphContext.setSimulationMode(true);

        graphContext.setGraph(entityGraph).setCurrentBatch(createSimulationBatch());
        graphContext.getTestContext().setEntityGraph(entityGraph);
        graphContext.getTestContext().setAttributeGraphs(List.of(field1Graph, field2Graph));
        saveToSink.execute(sink, viperContext, graphContext);
        Pair<FunctionResult, MappingNode> result = (Pair<FunctionResult, MappingNode>) graphContext.get("output_" + entityGraph.getSink(sink.getId()).get(0).getId());
        SimulationDataServiceFactory simulationDataServiceFactory = (SimulationDataServiceFactory) saveToSink.dataServiceFactory;
        ReadOnlyDataService simulatedDataService = (ReadOnlyDataService) simulationDataServiceFactory.getDataService(conmetaid);
        assertEquals(1, simulatedDataService.getCreates().size());
        SyncRequest createRequest = simulatedDataService.getCreates().iterator().next();
        EntityData entityData = createRequest.getData().get("con1").get(0);

        assertEquals(sink.getApiName(), entityData.getName());
        assertEquals("Value2", entityData.getValueAsString("destfield2"));
        assertEquals("value1", entityData.getValueAsString("destfield1"));
        TestContext testContext = graphContext.getTestContext();
        Map<String, Map<String, NodeData>> dataSnapshot = testContext.getDataSnapshot();
        assertTrue(dataSnapshot.containsKey(saved.getId()));
        assertEquals(saved.getId(), graphContext.getCurrentSyncariId());
        assertEquals(saved.getId(), entityData.getSyncariEntityId());
        assertEquals("value1", dataSnapshot.get(saved.getId()).get(field1Graph.getSinks().findFirst().get().getId()).getOutput().getResult());
        assertEquals("Value2", dataSnapshot.get(saved.getId()).get(field2Graph.getSinks().findFirst().get().getId()).getOutput().getResult());
        assertEquals(Map.of("destfield1", "value1", "destfield2", "Value2"), ((EntityData) dataSnapshot.get(saved.getId()).get(entityGraph.getSinks().findFirst().get().getId()).getOutput().getResult()).getValues());
    }

    private static SimulationCurrentBatch createSimulationBatch() {
        final SimulationCurrentBatch simulationCurrentBatch = new SimulationCurrentBatch();
        simulationCurrentBatch.setCurrentBatchId(UUID.randomUUID().toString());
        return simulationCurrentBatch;
    }
}