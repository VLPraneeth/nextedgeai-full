package com.syncari.viper.streams.stages;

import com.syncari.AbstractSyncariTest;
import com.syncari.connector.EntityData;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.*;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.TestContext;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.repositories.customer.UnresolvedReferenceRepo;
import com.syncari.core.service.*;
import com.syncari.core.simulation.SimulationCurrentBatch;
import com.syncari.core.sync.CurrentBatch;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.utils.Pair;
import com.syncari.viper.ViperContext;
import com.syncari.viper.streams.SimulationExecutionFactory;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

import static com.syncari.core.utils.GraphHelper.newGraph;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class ExecuteFieldPipelineSimulationTest extends AbstractSyncariTest {

    @Autowired
    SimulationExecutionFactory simulationExecutionFactory;
    @Autowired
    FunctionService functionService;
    @Autowired
    SyncDetailMetricService syncDetailMetricService;
    @Autowired
    FeatureService featureService;
    @MockBean
    BiFunction<EntityDefinition, Document, EntityData> entityCreate;


    @Test
    public void simulateFP_WithSingleFunction(){
        ExecuteFieldPipeline executeFieldPipeline = simulationExecutionFactory.getPipelineStages().getExecuteFieldPipeline();
        GraphContext graphContext = new GraphContext();
        executeFieldPipeline.syncDetailMetricService = syncDetailMetricService;
        ConnectorService mockConnectorService = mock(ConnectorService.class);
        ConnectorMetadata conmetaid = new ConnectorMetadata("conmetaid");
        conmetaid.setName("salesforce");
        Connector t = new Connector("testconnector",conmetaid ,"endpojnt","u","p");
        t.setId("con1");
        when(mockConnectorService.get("con1")).thenReturn(t);
        when(mockConnectorService.refreshAuthentication(t)).thenReturn(t);

        EntityDefinition sink = SchemaHelper.createEntityDef("destAccount", "Account", t);
        sink.addField(SchemaHelper.createAttribute("destfield1", StringType.VALUE, sink.getId()));
        sink.addField(SchemaHelper.createAttribute("destfield2", StringType.VALUE, sink.getId()));
        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        coreEntity.addField(SchemaHelper.createAttribute("corefield1", StringType.VALUE, sink.getId()));
        coreEntity.addField(SchemaHelper.createAttribute("corefield2", StringType.VALUE, sink.getId()));

        EntityDefinition srcEntity = SchemaHelper.createEntityDef("srcAccount", "Source Account", t);
        AttributeDefinition srcAttrib1 = SchemaHelper.createAttribute("srcfield1", StringType.VALUE, sink.getId());
        srcEntity.addField(srcAttrib1);
        AttributeDefinition srcAttrib2 = SchemaHelper.createAttribute("srcfield2", StringType.VALUE, sink.getId());
        srcEntity.addField(srcAttrib2);

        MappingGraph entityGraph = newGraph(coreEntity, functionService)
                .src(srcEntity)
                .dest(sink)
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "destAccount").getGraph();
        MappingGraph field1Graph = newGraph(coreEntity.getFieldByName("corefield1"), functionService)
                .src(srcEntity.getFieldByName("srcfield1"))
                .dest(sink.getFieldByName("destfield1"))
                .function("upper", "upper")
                .connect("srcfield1", "upper")
                .connect("upper", "corefield1")
                .connect("corefield1", "destfield1").getGraph();
        MappingGraph field2Graph = newGraph(coreEntity.getFieldByName("corefield2"), functionService)
                .src(srcEntity.getFieldByName("srcfield2"))
                .dest(sink.getFieldByName("destfield2"))
                .function("upper", "upper")
                .connect("srcfield2", "upper")
                .connect("upper", "corefield2")
                .connect("corefield2", "destfield2").getGraph();

        SchemaService mockSchemaService = mock(SchemaService.class);
        when(mockSchemaService.getEntity(srcEntity.getId())).thenReturn(srcEntity);
        when(mockSchemaService.getEntity(sink.getId())).thenReturn(sink);
        when(mockSchemaService.getEntity(coreEntity.getId())).thenReturn(coreEntity);
        when(mockSchemaService.getEntity(coreEntity.getId())).thenReturn(coreEntity);
        when(mockSchemaService.getAttribute(srcAttrib1.getId())).thenReturn(srcAttrib1);
        when(mockSchemaService.getAttribute(srcAttrib2.getId())).thenReturn(srcAttrib2);

        MappingGraphService mockGraphService = mock(MappingGraphService.class);
        when(mockGraphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(field1Graph,field2Graph));

        AttributeRepo mockAttributeRepo = mock(AttributeRepo.class);
        when(mockAttributeRepo.findAllById(List.of(coreEntity.getFieldByName("corefield1").getId(),coreEntity.getFieldByName("corefield2").getId())))
                .thenReturn(List.of(coreEntity.getFieldByName("corefield1"),coreEntity.getFieldByName("corefield2")));
        when(mockAttributeRepo.findById(coreEntity.getFieldByName("corefield1").getId()))
                .thenReturn(Optional.of(coreEntity.getFieldByName("corefield1")));
        when(mockAttributeRepo.findById(coreEntity.getFieldByName("corefield1").getId()))
                .thenReturn(Optional.of(coreEntity.getFieldByName("corefield1")));

        UnresolvedRecordService mockUnresolvedRecordService = mock(UnresolvedRecordService.class);
        when(mockUnresolvedRecordService.getUnresolvedRecords(anyString())).thenReturn(List.of());
        when(mockUnresolvedRecordService.getUnresolvedEntities(anyString(),anyString())).thenReturn(List.of());

        UnresolvedReferenceRepo mockUnresolvedReferenceRepo = mock(UnresolvedReferenceRepo.class);
        doNothing().when(mockUnresolvedReferenceRepo).updateSyncariValues(anyList());
        doNothing().when(mockUnresolvedReferenceRepo).upsertUnResolved(anyList());
        doNothing().when(mockUnresolvedReferenceRepo).deleteAllById(anyList());
        when(mockUnresolvedReferenceRepo.findUnresolvedReferenceBy(coreEntity.getId())).thenReturn(List.of());
        when(mockUnresolvedReferenceRepo.deleteBySyncariEntityIdAndRecordIds(coreEntity.getId(), List.of()))
                .thenReturn(List.of());

        RecordMergeService mockRecordMergeService = mock(RecordMergeService.class);
        when(mockRecordMergeService.advancedDedupeMerge(any(AdvancedDedupeConfig.class), any(EntityData.class), any(EntityDefinition.class),any(GraphContext.class), any(TransactionLog.class), any()))
                .thenReturn(Optional.empty());
        when(mockRecordMergeService.createMergeOperation(any(EntityDefinition.class), any(DedupeConfig.class), any(EntityData.class)))
                .thenReturn(null);

        EntityRepoService mockEntityRepoService = mock(EntityRepoService.class);
        doNothing().when(mockEntityRepoService).computeScore(anyList(), anyString(),anyMap());
        when(mockEntityRepoService.getRulesForEntityByField(anyString())).thenReturn(Map.of());

        executeFieldPipeline.connectorService = mockConnectorService;
        executeFieldPipeline.graphService = mockGraphService;
        executeFieldPipeline.schemaService = mockSchemaService;
        executeFieldPipeline.attributeProxyRepo = mockAttributeRepo;
        executeFieldPipeline.recordMergeService = mockRecordMergeService;
        executeFieldPipeline.unresolvedReferenceRepo = mockUnresolvedReferenceRepo;
        executeFieldPipeline.repoService = mockEntityRepoService;
        executeFieldPipeline.transactionLogService.setFeatureService(featureService);

        // set currentBatch in graphContext
        EntityData entityData = new EntityData("account")
                .addValue("srcField1", "Value1")
                .addValue("srcField2", "Value2")
                .setId("simulatedTest")
                .addValue("_source", t.getName());
        entityData.setConnectorId(srcEntity.getConnectorId());
        CurrentBatch currentBatch = getCurrentBatch(entityData, srcEntity, coreEntity);
        graphContext.setCurrentBatch(currentBatch);


        // set graphContext and Run simulation for field1Graph
        graphContext.setSimulationMode(true);
        graphContext.getTestContext().setEntityGraph(entityGraph);
        graphContext.getTestContext().setAttributeGraphs(List.of(field1Graph));
        graphContext.setGraph(entityGraph);

        var updatedGraphContext = executeFieldPipeline.execute(getViperContext(), graphContext);

        // check the values for nodes in field1Graph
        TestContext testContext = updatedGraphContext.getTestContext();
        assertNotNull(testContext);
        assertEquals(1, testContext.getDataSnapshot().size());
        String syncariId = updatedGraphContext.getCurrentSyncariId();
        var nodeResults = testContext.getDataSnapshot().get(syncariId);
        assertFalse(nodeResults.isEmpty());
        // check the node result for field1 graph
        var srcNode = field1Graph.findNodeByName("srcfield1").get();
        assertEquals("Value1", nodeResults.get(srcNode.getId()).getOutput().typedValue());

        var upperFnNode = field1Graph.findNodeByName("upper").get();
        assertEquals("VALUE1", nodeResults.get(upperFnNode.getId()).getOutput().typedValue());

        var coreNode = field1Graph.getCoreNode();
        assertEquals("VALUE1", nodeResults.get(coreNode.getId()).getOutput().typedValue());

        //ExecuteFieldPipeline also captures output for core node of entity graph
        var coreEntityNode = entityGraph.getCoreNode();
        EntityData coreEntityNodeOp = (EntityData) nodeResults.get(coreEntityNode.getId()).getOutput().typedValue();
        assertEquals("VALUE1", coreEntityNodeOp.getValueAsString("corefield1"));
    }
    @Test
    public void emptyNewRecordSkipped(){
        GraphContext graphContext = new GraphContext();

        ConnectorService mockConnectorService = mock(ConnectorService.class);
        ConnectorMetadata conmetaid = new ConnectorMetadata("conmetaid");
        conmetaid.setName("salesforce");
        Connector t = new Connector("testconnector",conmetaid ,"endpojnt","u","p");
        t.setId("con1");
        when(mockConnectorService.get("con1")).thenReturn(t);
        when(mockConnectorService.refreshAuthentication(t)).thenReturn(t);

        EntityDefinition sink = SchemaHelper.createEntityDef("destAccount", "Account", t);
        sink.addField(SchemaHelper.createAttribute("destfield1", StringType.VALUE, sink.getId()));
        sink.addField(SchemaHelper.createAttribute("destfield2", StringType.VALUE, sink.getId()));
        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        coreEntity.addField(SchemaHelper.createAttribute("corefield1", StringType.VALUE, sink.getId()));
        coreEntity.addField(SchemaHelper.createAttribute("corefield2", StringType.VALUE, sink.getId()));

        EntityDefinition srcEntity = SchemaHelper.createEntityDef("srcAccount", "Source Account", t);
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
                .function("upper", "upper")
                .connect("srcfield1", "upper")
                .connect("upper", "corefield1")
                .connect("corefield1", "destfield1").getGraph();
        MappingGraph field2Graph = newGraph(coreEntity.getFieldByName("corefield2"), functionService)
                .src(srcEntity.getFieldByName("srcfield2"))
                .dest(sink.getFieldByName("destfield2"))
                .function("upper", "upper")
                .connect("srcfield2", "upper")
                .connect("upper", "corefield2")
                .connect("corefield2", "destfield2").getGraph();

        SchemaService mockSchemaService = mock(SchemaService.class);
        when(mockSchemaService.getEntity(srcEntity.getId())).thenReturn(srcEntity);
        when(mockSchemaService.getEntity(sink.getId())).thenReturn(sink);
        when(mockSchemaService.getEntity(coreEntity.getId())).thenReturn(coreEntity);

        MappingGraphService mockGraphService = mock(MappingGraphService.class);
        when(mockGraphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(field1Graph,field2Graph));

        AttributeRepo mockAttributeRepo = mock(AttributeRepo.class);
        when(mockAttributeRepo.findAllById(List.of(coreEntity.getFieldByName("corefield1").getId(),coreEntity.getFieldByName("corefield2").getId())))
                .thenReturn(List.of(coreEntity.getFieldByName("corefield1"),coreEntity.getFieldByName("corefield2")));
        when(mockAttributeRepo.findById(coreEntity.getFieldByName("corefield1").getId()))
                .thenReturn(Optional.of(coreEntity.getFieldByName("corefield1")));
        when(mockAttributeRepo.findById(coreEntity.getFieldByName("corefield2").getId()))
                .thenReturn(Optional.of(coreEntity.getFieldByName("corefield2")));

        UnresolvedRecordService mockUnresolvedRecordService = mock(UnresolvedRecordService.class);
        when(mockUnresolvedRecordService.getUnresolvedRecords(anyString())).thenReturn(List.of());
        when(mockUnresolvedRecordService.getUnresolvedEntities(anyString(),anyString())).thenReturn(List.of());

        UnresolvedReferenceRepo mockUnresolvedReferenceRepo = mock(UnresolvedReferenceRepo.class);
        doNothing().when(mockUnresolvedReferenceRepo).updateSyncariValues(anyList());
        doNothing().when(mockUnresolvedReferenceRepo).upsertUnResolved(anyList());
        doNothing().when(mockUnresolvedReferenceRepo).deleteAllById(anyList());
        when(mockUnresolvedReferenceRepo.findUnresolvedReferenceBy(coreEntity.getId())).thenReturn(List.of());
        when(mockUnresolvedReferenceRepo.deleteBySyncariEntityIdAndRecordIds(coreEntity.getId(), List.of()))
                .thenReturn(List.of());

        RecordMergeService mockRecordMergeService = mock(RecordMergeService.class);
        when(mockRecordMergeService.advancedDedupeMerge(any(AdvancedDedupeConfig.class), any(EntityData.class), any(EntityDefinition.class),any(GraphContext.class), any(TransactionLog.class), any()))
                .thenReturn(Optional.empty());
        when(mockRecordMergeService.createMergeOperation(any(EntityDefinition.class), any(DedupeConfig.class), any(EntityData.class)))
                .thenReturn(null);

        EntityRepoService mockEntityRepoService = mock(EntityRepoService.class);
        doNothing().when(mockEntityRepoService).computeScore(anyList(), anyString(),anyMap());
        when(mockEntityRepoService.getRulesForEntityByField(anyString())).thenReturn(Map.of());
        EntityRepo mockEntityRepo = mock(EntityRepo.class);

        ExecuteFieldPipeline executeFieldPipeline = simulationExecutionFactory.getPipelineStages().getExecuteFieldPipeline();
        executeFieldPipeline.syncDetailMetricService = syncDetailMetricService;

        executeFieldPipeline.connectorService = mockConnectorService;
        executeFieldPipeline.graphService = mockGraphService;
        executeFieldPipeline.schemaService = mockSchemaService;
        executeFieldPipeline.attributeProxyRepo = mockAttributeRepo;
        executeFieldPipeline.recordMergeService = mockRecordMergeService;
        executeFieldPipeline.unresolvedReferenceRepo = mockUnresolvedReferenceRepo;
        executeFieldPipeline.repoService = mockEntityRepoService;
        executeFieldPipeline.entityRepo = mockEntityRepo;
        executeFieldPipeline.transactionLogService.setFeatureService(featureService);

        // set currentBatch in graphContext
        EntityData entityData = new EntityData("account")
                .addValue("_source", t.getName())
                .setNew(true);

        entityData.setConnectorId(srcEntity.getConnectorId());
        SimulationCurrentBatch currentBatch = getCurrentBatch(entityData, srcEntity, coreEntity);
        graphContext.setCurrentBatch(currentBatch);
        currentBatch.setExistingRecords(List.of());


        // set graphContext and Run simulation for field1Graph
        graphContext.setGraph(entityGraph);

        executeFieldPipeline.execute(getViperContext(), graphContext);
        //We don'tt expect a save of this empty record
        verify(mockEntityRepo,times(0)).save(any());

    }

    private SimulationCurrentBatch getCurrentBatch(EntityData entityData, EntityDefinition srcEntity, EntityDefinition syncariEntity){
        String syncariId = new ObjectId().toHexString();
        entityData.setSyncariEntityId(syncariId);
        StagedBatch staged = new StagedBatch(syncariEntity.getApiName()).setConnectorId(srcEntity.getConnectorId())
                .setCurrentBatchId(UUID.randomUUID().toString()).setSourceEntityName(srcEntity.getApiName())
                .setSourceEntityDefinitionId(srcEntity.getId());
        staged.setId(UUID.randomUUID().toString());
        StagedBatchRecord record = new StagedBatchRecord()
                .setStagedBatchId(staged.getId())
                .setEntityData(entityData)
                .setExternalRecordId(entityData.getId())
                .setExternalEntityDefinitionId(srcEntity.getId());
        record.setId(UUID.randomUUID().toString());
        record.setSyncariId(syncariId);
        SimulationCurrentBatch currentBatch = new SimulationCurrentBatch();
        currentBatch.setCurrentBatchId(staged.getCurrentBatchId());
        currentBatch.setBatchRecords(List.of(record));
        currentBatch.setEntityBatch(srcEntity, staged);
        currentBatch.setSyncariEntityName(syncariEntity.getApiName());
        currentBatch.setExistingRecords(List.of(entityData));
        return currentBatch;
    }

    private ViperContext getViperContext(){
        ViperContext context = new ViperContext(SyncariContext.getOrganziation(), SyncariContext.getInstance(), SyncariContext.getUser());
        context.setUpdateWatermark(false);
        context.setSimulationMode(true);
        return context;
    }
}
