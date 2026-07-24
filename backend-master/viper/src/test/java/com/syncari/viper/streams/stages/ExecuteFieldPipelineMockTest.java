package com.syncari.viper.streams.stages;

import com.syncari.connector.EntityData;
import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.ReferenceType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.event.store.EventStore;
import com.syncari.core.model.*;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.BatchActionContext;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.NodeConfigurationVisitor;
import com.syncari.core.pipeline.PipelineEvaluator;
import com.syncari.core.pipeline.jtwig.JTwigPipelineEvaluator;
import com.syncari.core.repositories.customer.*;
import com.syncari.core.service.*;
import com.syncari.core.sync.CurrentBatch;
import com.syncari.core.sync.RecordsBySyncariId;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.core.validation.NodeValidatorVisitor;
import com.syncari.core.validation.ValidationContext;
import com.syncari.viper.ViperContext;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.syncari.core.utils.GraphHelper.newGraph;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ExecuteFieldPipelineMockTest {

    @Autowired
    FunctionService functionService;

    @Autowired
    PipelineUtil pipelineUtil;

    EntityData entityData1;
    EntityData entityData2;

    @Test
    public void deleteIdMappingWithRepeatedRecordIds(){
        IdMappingRepo idMappingRepo= mock(IdMappingRepo.class);
        IdMapping m1= new IdMapping().setSyncariId("s1").setEntityName("contact").addMapping("c1","e1","ed1");
        IdMapping m2= new IdMapping().setSyncariId("s2").setEntityName("contact").addMapping("c2","e2","ed2");
        IdMapping m3= new IdMapping().setSyncariId("s3").setEntityName("contact").addMapping("c4","e4","ed4");
        when(idMappingRepo.findBySyncariIds(eq("contact"),anyList())).thenReturn(List.of(m1,m2,m3));

        when(idMappingRepo.saveAll(any())).thenReturn(List.of(m1,m2,m3));
        doNothing().when(idMappingRepo).deleteAll(any());

        ExecuteFieldPipeline executeFieldPipeline = new ExecuteFieldPipeline(null, null, null, null,
                null, null, null, null,
                idMappingRepo, null, null, null,null, null,null, null, pipelineUtil,null);
        StagedBatchRecord r1 = new StagedBatchRecord().setExternalRecordId("e1").setExternalEntityDefinitionId("ed1").setSyncariId("s1").setEntityData(new EntityData().setConnectorId("c1"));
        StagedBatchRecord r2 = new StagedBatchRecord().setExternalRecordId("e2").setExternalEntityDefinitionId("ed2").setSyncariId("s2").setEntityData(new EntityData().setConnectorId("c2"));
        StagedBatchRecord r3 = new StagedBatchRecord().setExternalRecordId("e3").setExternalEntityDefinitionId("ed3").setSyncariId("s3").setEntityData(new EntityData().setConnectorId("c3"));
        StagedBatchRecord r3_2 = new StagedBatchRecord().setExternalRecordId("e4").setExternalEntityDefinitionId("ed4").setSyncariId("s3").setEntityData(new EntityData().setConnectorId("c4"));
        ArgumentCaptor<List<IdMapping>> saveCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<IdMapping>> deleteCaptor = ArgumentCaptor.forClass(List.class);

        executeFieldPipeline.deleteIdMapping(List.of(r1,r2,r3,r3_2),"contact");

        verify(idMappingRepo).deleteAll(deleteCaptor.capture());
        verify(idMappingRepo).saveAll(saveCaptor.capture());
        assertEquals(0,saveCaptor.getValue().size());
        assertEquals(3,deleteCaptor.getValue().size());
    }

    @Test
    public void isSyncariId(){
        SchemaService schemaService= mock(SchemaService.class);
        EntityRepo entityRepo= mock(EntityRepo.class);
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").id().string("name").watermark().getEntityDefinition();
        when(schemaService.getSyncariEntityByName("account")).thenReturn(Optional.of(entityDefinition));
        final String id = ObjectId.get().toHexString();
        final EntityData accountRecord = new EntityData().setId(id).setName("account");
        when(entityRepo.findById(eq(entityDefinition),eq(id))).thenReturn(Optional.of(accountRecord));

        ExecuteFieldPipeline executeFieldPipeline = new ExecuteFieldPipeline(null, entityRepo, null, null,
                schemaService, null, null, null,
                null, null, null, null,null, null,null, null, pipelineUtil,null);

        final AttributeDefinition syncariAttribute = SchemaHelper.createAttribute("accountId", ReferenceType.VALUE, ObjectId.get().toHexString()).setReferenceTo("account");
        //existing synncari record
        assertTrue(executeFieldPipeline.isSyncariId(syncariAttribute,id,new HashMap<>()));
        //not a syncari formatted value
        assertFalse(executeFieldPipeline.isSyncariId(syncariAttribute,"non-id",new HashMap<>()));
        //syncari formatted, but not resolved in syncari_
        assertFalse(executeFieldPipeline.isSyncariId(syncariAttribute,ObjectId.get().toHexString(),new HashMap<>()));

    }

    @Test
    public void isRecordDeleted(){
        SchemaService schemaService= mock(SchemaService.class);
        EntityRepo entityRepo= mock(EntityRepo.class);
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").id().string("name").watermark().getEntityDefinition();
        when(schemaService.getSyncariEntityByName("account")).thenReturn(Optional.of(entityDefinition));
        
        ExecuteFieldPipeline executeFieldPipeline = new ExecuteFieldPipeline(null, entityRepo, null, null,
                schemaService, null, null, null,
                null, null, null, null,null, null, null, null, pipelineUtil,null);

        final String id = ObjectId.get().toHexString();
        final String syncariId = ObjectId.get().toHexString();

        Optional<IdMapping> existingIdMapping = Optional.empty();

        EntityData accountRecord = new EntityData().setId(id).setName("account");
        List<StagedBatchRecord> records = new ArrayList<>();
        records.add(new StagedBatchRecord().setEntityData(accountRecord).
            setExternalEntityDefinitionId(entityDefinition.getId())
            .setExternalRecordId(accountRecord.getId()).setSyncariId(syncariId));
        assertFalse(executeFieldPipeline.isRecordDeleted(records, existingIdMapping));

        accountRecord = new EntityData().setId(id).setName("account").setDeleted(true);
        records.clear();
        records.add(new StagedBatchRecord().setEntityData(accountRecord).
            setExternalEntityDefinitionId(entityDefinition.getId())
            .setExternalRecordId(accountRecord.getId()).setSyncariId(syncariId));
        assertTrue(executeFieldPipeline.isRecordDeleted(records, existingIdMapping));

        accountRecord = new EntityData().setId(id).setName("account").setDeleted(true);
        // Another not deleted.
        EntityData accountRecord1 = new EntityData().setId(ObjectId.get().toHexString()).setName("account").setDeleted(false);
        records.clear();
        records.add(new StagedBatchRecord().setEntityData(accountRecord).
            setExternalEntityDefinitionId(entityDefinition.getId())
            .setExternalRecordId(accountRecord.getId()).setSyncariId(syncariId));
        records.add(new StagedBatchRecord().setEntityData(accountRecord1).
            setExternalEntityDefinitionId(entityDefinition.getId())
            .setExternalRecordId(accountRecord1.getId()).setSyncariId(syncariId));
        assertFalse(executeFieldPipeline.isRecordDeleted(records, existingIdMapping));


        final IdMapping idMapping = new IdMapping().addMapping("c1", id, "c1-e1");
        existingIdMapping = Optional.of(idMapping);
        accountRecord = new EntityData().setId(id).setName("account").setDeleted(true);
        records.clear();
        records.add(new StagedBatchRecord().setEntityData(accountRecord).setExternalEntityDefinitionId(entityDefinition.getId())
            .setExternalRecordId(accountRecord.getId()).setSyncariId(syncariId));
        assertFalse(executeFieldPipeline.isRecordDeleted(records, existingIdMapping));

        // Disconnect and check, it should allow deletes.
        idMapping.disconnectMapping("c1","c1-e1",id);
        assertTrue(executeFieldPipeline.isRecordDeleted(records, existingIdMapping));
    }

    @Test
    public void findSyncariFk_MultiValued(){
        SchemaService schemaService= mock(SchemaService.class);
        MappingGraphService graphService = mock(MappingGraphService.class);
        EntityRepo entityRepo= mock(EntityRepo.class);
        IdMappingRepo idMappingRepo = mock(IdMappingRepo.class);
        when(idMappingRepo.findByExternalIds(anyString(), anyString(), anyString(), anyList())).thenReturn(List.of());
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").id().string("name").watermark().getEntityDefinition();
        when(schemaService.getSyncariEntityByName("account")).thenReturn(Optional.of(entityDefinition));
        when(graphService.retrieveApprovedEntityGraph(any())).thenReturn(Optional.of(new MappingGraph()));

        ExecuteFieldPipeline executeFieldPipeline = new ExecuteFieldPipeline(null, entityRepo, graphService, null,
                schemaService, null, null, null,
                idMappingRepo, null, null, null,null, null,null, null, pipelineUtil,null);

        AttributeDefinition syncariAttribute = SchemaHelper.createAttribute("accountId", ReferenceType.VALUE, ObjectId.get().toHexString()).setReferenceTo("account");
        ResolvedReference resolved = executeFieldPipeline.findSyncariFk("account", syncariAttribute, "1", entityDefinition, new GraphContext(), Map.of("test", Map.of("test", "test")));
        assertTrue(resolved.hasUnresolvedReferences());
        assertEquals(1, resolved.getUnresolvedReferences().size());
        assertTrue(resolved.getUnresolvedReferences().contains("1"));

        // Pass an array of values to a single valued FK attribute resolution.
        resolved = executeFieldPipeline.findSyncariFk("account", syncariAttribute, List.of("1", "2"), entityDefinition, new GraphContext(), Map.of("test", Map.of("test", "test")));
        assertTrue(resolved.hasUnresolvedReferences());
        assertEquals(2, resolved.getUnresolvedReferences().size());
        assertTrue(resolved.getUnresolvedReferences().contains("1"));
        assertTrue(resolved.getUnresolvedReferences().contains("2"));

        // When treated as multivalued, same results.
        AttributeDefinition spyAttr = spy(syncariAttribute);
        when(spyAttr.isMultiValueField()).thenReturn(true);

        resolved = executeFieldPipeline.findSyncariFk("account", spyAttr, List.of("1", "2"), entityDefinition, new GraphContext(), Map.of("test", Map.of("test", "test")));
        assertTrue(resolved.hasUnresolvedReferences());
        assertEquals(2, resolved.getUnresolvedReferences().size());
        assertTrue(resolved.getUnresolvedReferences().contains("1"));
        assertTrue(resolved.getUnresolvedReferences().contains("2"));
    }

    @Test
    public void upsertIdMappingWithMultipleStagedRecordsForSyncariId(){
        IdMappingRepo idMappingRepo= mock(IdMappingRepo.class);

        FeatureService featureService = mock(FeatureService.class);
        EntityRepoService repoService = mock(EntityRepoService.class);

        when(idMappingRepo.saveAll(any())).thenReturn(List.of(new IdMapping()));
        doNothing().when(repoService).connectExternalId(any(), any(), any(), any(), any());

        ExecuteFieldPipeline executeFieldPipeline = new ExecuteFieldPipeline(null, null, null, null,
                null, null, null, null,
                idMappingRepo, null, null, repoService,null, null,null, featureService, pipelineUtil,null);

        final EntityDefinition entityDefinition1 = SchemaHelper.createEntityDef("externalContact1","External Contact1").setConnectorId("c1");
        final EntityDefinition entityDefinition2 = SchemaHelper.createEntityDef("externalContact2","External Contact2").setConnectorId("c2");

        StagedBatchRecord r1 = new StagedBatchRecord().setExternalRecordId("e1").setExternalEntityDefinitionId(entityDefinition1.getId())
                .setSyncariId("s1").setEntityData(new EntityData().setConnectorId("c1").setId("e1")).setStagedBatchId("sb1").setNew(true);
        StagedBatchRecord r2 = new StagedBatchRecord().setExternalRecordId("e2").setExternalEntityDefinitionId(entityDefinition2.getId())
                .setSyncariId("s1").setEntityData(new EntityData().setConnectorId("c2").setId("e2")).setStagedBatchId("sb2").setNew(true);
        ArgumentCaptor<List<IdMapping>> saveCaptor = ArgumentCaptor.forClass(List.class);

        CurrentBatch batch= mock(CurrentBatch.class);
        when(batch.lookupConnectorIdByBatchId("sb1")).thenReturn(entityDefinition1);
        when(batch.lookupConnectorIdByBatchId("sb2")).thenReturn(entityDefinition2);
        List<IdMapping> idMappingBatch = new ArrayList<>();
        executeFieldPipeline.upsertIdMappings(batch,"contact",new RecordsBySyncariId("s1").addRecord(r1).addRecord(r2), idMappingBatch,
                Optional.empty(), null, null, Optional.empty());

        final IdMapping savedIdMappings = idMappingBatch.get(0);
        assertNotNull(savedIdMappings);
        assertEquals(2, savedIdMappings.getMappings().size());
        assertTrue(savedIdMappings.findMapping("c1",entityDefinition1.getId(),"e1").isPresent());
        assertTrue(savedIdMappings.findMapping("c2",entityDefinition2.getId(),"e2").isPresent());


/*        verify(idMappingRepo).upsert(saveCaptor.capture());
        final IdMapping savedIdMappings = saveCaptor.getValue().get(0);
        assertNotNull(savedIdMappings);
        assertEquals(2, savedIdMappings.getMappings().size());
        assertTrue(savedIdMappings.findMapping("c1",entityDefinition1.getId(),"e1").isPresent());
        assertTrue(savedIdMappings.findMapping("c2",entityDefinition2.getId(),"e2").isPresent());*/

    }

    @Test
    public void runBatchAttribActions(){
        EntityDefinition source = SchemaHelper.createEntityDefinition("srcContact")
                .string("srcFirstName").string("srcLastName").string("srcEmail").id().getEntityDefinition();
        EntityDefinition coreEntity = SchemaHelper.createEntityDefinition("contact")
                        .string("firstName").string("lastName").string("email").id().getEntityDefinition();
        EntityDefinition dest = SchemaHelper.createEntityDefinition("destContact")
                .string("destFirstName").string("destLastName").string("destEmail").id().getEntityDefinition();
        final MappingGraph firstNameGraph = newGraph(coreEntity.getFieldByName("firstName"))
                .src(source.getFieldByName("srcFirstName"))
                .dest(dest.getFieldByName("destFirstName"))
                .action("sendSlackMessage", "slackMessage1")
                .action("sendSlackMessage", "slackMessage2")
                .action("sendSlackMessage", "slackMessage3")
                .action("sendEmail", "sendEmail1")
                .action("sendEmail", "sendEmail2")
                .action("sendEmail", "sendEmail3")
                .function("lower")
                .function("replace")
                .connect("srcFirstName", "slackMessage1")
                .connect("slackMessage1", "sendEmail1")
                .connect("slackMessage1", "sendEmail2")
                .connect("sendEmail2", "lower")
                .connect("lower", "firstName")
                .connect("firstName", "replace")
                .connect("replace", "sendEmail3")
                .connect("replace", "slackMessage2")
                .connect("replace", "slackMessage3")
                .connect("slackMessage3", "destFirstName").getGraph();
        final MappingGraph lastNameGraph = newGraph(coreEntity.getFieldByName("lastName"))
                .src(source.getFieldByName("srcLastName"))
                .dest(dest.getFieldByName("destLastName"))

                .action("sendSlackMessage", "lastNameGraphSlackMessage1")
                .action("sendSlackMessage", "lastNameGraphSlackMessage2")
                .action("sendSlackMessage", "lastNameGraphSlackMessage3")
                .action("sendEmail", "lastNameGraphSendEmail1")
                .action("sendEmail", "lastNameGraphSendEmail2")
                .action("sendEmail", "lastNameGraphSendEmail3")
                .function("lower","lastNameGraphLowerCase")
                .function("replace","lastNameGraphReplace")
                .connect("srcLastName","lastNameGraphSlackMessage1")
                .connect("lastNameGraphSlackMessage1", "lastNameGraphSendEmail1")
                .connect("lastNameGraphSlackMessage1", "lastNameGraphSendEmail2")
                .connect("lastNameGraphSendEmail1", "lastNameGraphLowerCase")
                .connect("lastNameGraphLowerCase", "lastName")
                .connect("lastName", "lastNameGraphReplace")
                .connect("lastNameGraphReplace", "lastNameGraphSendEmail3")
                .connect("lastNameGraphReplace", "lastNameGraphSlackMessage2")
                .connect("lastNameGraphReplace", "lastNameGraphSlackMessage3")
                .connect("lastNameGraphSlackMessage3", "destLastName").getGraph();


        final BatchActionContext batchActionContext = new BatchActionContext();
        addBatchNodes(batchActionContext, firstNameGraph, "slackMessage1","slackMessage2","slackMessage3","sendEmail1","sendEmail2","sendEmail3");
        addBatchNodes(batchActionContext, lastNameGraph, "lastNameGraphSlackMessage1","lastNameGraphSlackMessage2","lastNameGraphSlackMessage3","lastNameGraphSendEmail1","lastNameGraphSendEmail2","lastNameGraphSendEmail3");

        PipelineEvaluator evaluator = mock(PipelineEvaluator.class);
        ArgumentCaptor<MappingNode> nodeCaptor = ArgumentCaptor.forClass(MappingNode.class);
        doNothing().when(evaluator).evaluate(any(MappingNode.class), any(MappingGraph.class), any(GraphContext.class), ArgumentMatchers.<Predicate<MappingNode>>any(), any());
        final ExecuteFieldPipeline executeFieldPipeline = new ExecuteFieldPipeline(null, null, null, evaluator, null, null, null, null, null, null, null, null, null, null, null,null, pipelineUtil,null);
        final LinkedHashMap<AttributeDefinition, MappingGraph> graphs = new LinkedHashMap<>();
        graphs.put(coreEntity.getFieldByName("firstName"),firstNameGraph);
        graphs.put(coreEntity.getFieldByName("lastName"),lastNameGraph);
        executeFieldPipeline.runAttributeBatchActions(graphs,new GraphContext(), batchActionContext);
        verify(evaluator, times(12)).evaluate(nodeCaptor.capture(), any(MappingGraph.class), any(GraphContext.class), ArgumentMatchers.<Predicate<MappingNode>>any(), any());
        assertEquals(12, nodeCaptor.getAllValues().size());
        assertEquals(List.of(
                "slackMessage1", "sendEmail1", "sendEmail2", "sendEmail3", "slackMessage2", "slackMessage3",
                "lastNameGraphSlackMessage1", "lastNameGraphSendEmail1", "lastNameGraphSendEmail2", "lastNameGraphSendEmail3", "lastNameGraphSlackMessage2", "lastNameGraphSlackMessage3"
        ), nodeCaptor.getAllValues().stream().map(MappingNode::getName).collect(Collectors.toList()));
    }

    private void addBatchNodes(BatchActionContext batchActionContext, MappingGraph firstNameGraph, String...actions) {
        for (String action : actions) {
            batchActionContext.getBatchActionNodes().add(firstNameGraph.getNodeByName(action).get());
        }
    }

    private ExecuteFieldPipeline mockAndGetTestCaseForMerge(GraphContext graphContext, EntityRepo mockEntityRepo,
            RecordMergeService mockRecordMergeService) {

        ConnectorService mockConnectorService = mock(ConnectorService.class);
        ConnectorMetadata conmetaid = new ConnectorMetadata("conmetaid");
        conmetaid.setName("salesforce");
        Connector t = new Connector("testconnector",conmetaid ,"endpojnt","u","p");
        t.setId("con1");
        when(mockConnectorService.get("con1")).thenReturn(t);
        when(mockConnectorService.refreshAuthentication(t)).thenReturn(t);
        when(mockConnectorService.getSyncariConnector()).thenReturn(t);

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

        EntityRepoService mockEntityRepoService = mock(EntityRepoService.class);
        doNothing().when(mockEntityRepoService).computeScore(anyList(), anyString());
        DatastoreService datastoreService = mock(DatastoreService.class);
        PipelineEvaluator pipelineEvaluator = mock(PipelineEvaluator.class);
        IdMappingRepo idMappingRepo = mock(IdMappingRepo.class);
        EventStore eventStore = mock(EventStore.class);

        TransactionLog txnLog = new TransactionLog();
        txnLog.setId(UUID.randomUUID().toString());

        // set currentBatch in graphContext
        String syncariId1 = new ObjectId().toHexString();
        entityData1 = new EntityData("account")
                .addValue("_source", t.getName())
                .addValue("name", "record1")
                .setConnectorId(t.getId())
                .setSyncariEntityId(syncariId1)
                .setId(syncariId1)
                .setNew(true);

        String syncariId2 = new ObjectId().toHexString();
        entityData2 = new EntityData("account")
                .addValue("_source", t.getName())
                .addValue("name", "record2")
                .setConnectorId(t.getId())
                .setSyncariEntityId(syncariId2)
                .setId(syncariId2)
                .setNew(true);

        entityData1.setConnectorId(srcEntity.getConnectorId());
        entityData2.setConnectorId(srcEntity.getConnectorId());
        //CurrentBatch currentBatch = getCurrentBatch(List.of(entityData, entityData1), srcEntity, coreEntity);
        List<StagedBatchRecord> stages = getStagedBatchRecords(List.of(entityData1, entityData2), srcEntity, coreEntity);
        RecordsBySyncariId rec1 = new RecordsBySyncariId(stages.get(0).getSyncariId());
        rec1.addRecord(stages.get(0));
        RecordsBySyncariId rec2 = new RecordsBySyncariId(stages.get(1).getSyncariId());
        rec2.addRecord(stages.get(1));

        CurrentBatch mockCurrentBatch = mock(CurrentBatch.class);
        Iterator dataIteratorMock = mock(Iterator.class);
        when(dataIteratorMock.hasNext()).thenReturn(true,true,false);
        when(dataIteratorMock.next()).thenReturn(rec1,rec2);
        when(mockCurrentBatch.recordsBySyncariIdIterator()).thenReturn(dataIteratorMock);
        when(mockCurrentBatch.lookupConnectorIdByBatchId(anyString())).thenReturn(srcEntity);

        graphContext.setCurrentBatch(mockCurrentBatch);

        when(mockEntityRepo.save(any(EntityDefinition.class), eq(entityData1))).thenReturn(entityData1);
        when(mockEntityRepo.save(any(EntityDefinition.class), eq(entityData2))).thenReturn(entityData2);

        // set graphContext and Run simulation for field1Graph
        graphContext.setGraph(entityGraph);

        return new ExecuteFieldPipeline(mockConnectorService, mockEntityRepo, mockGraphService, 
            pipelineEvaluator, mockSchemaService, mockAttributeRepo, eventStore, mockRecordMergeService, idMappingRepo,
            mockUnresolvedReferenceRepo, datastoreService, mockEntityRepoService,null, null,null, null, pipelineUtil,null);
    }
    @Test
    public void isResolvableReference(){
        EntityDefinition account = SchemaHelper.createEntityDefinition("account")
                .string("actName").id().getEntityDefinition();
        EntityDefinition contact = SchemaHelper.createEntityDefinition("contact")
                .string("firstName").string("lastName").string("email").id().reference("acctId", "account").getEntityDefinition();

        final MappingGraphService mappingGraphService = mock(MappingGraphService.class);
        final EntityRepo entityRepo = mock(EntityRepo.class);
        final SchemaService schemaService = mock(SchemaService.class);
        final ExecuteFieldPipeline executeFieldPipeline = new ExecuteFieldPipeline(null, entityRepo, mappingGraphService, null
                , schemaService, null, null, null, null,
                null, null, null, null, null, null, null, pipelineUtil,null);
        when(schemaService.getSyncariEntityByName("account")).thenReturn(Optional.of(account));
        when(entityRepo.count(account, Optional.empty())).thenReturn(1l);
        when(mappingGraphService.retrieveApprovedEntityGraph(account.getId())).thenReturn(Optional.of(new MappingGraph()));
        assertTrue(executeFieldPipeline.isResolvableReference(contact.getFieldByName("acctId"), new GraphContext()));

        when(schemaService.getSyncariEntityByName("account")).thenReturn(Optional.empty());
        when(entityRepo.count(account, Optional.empty())).thenReturn(0l);
        assertFalse(executeFieldPipeline.isResolvableReference(contact.getFieldByName("acctId"),  new GraphContext()));

        when(schemaService.getSyncariEntityByName("account")).thenReturn(Optional.of(account));
        when(entityRepo.count(account, Optional.empty())).thenReturn(0l);
        when(mappingGraphService.retrieveApprovedEntityGraph(account.getId())).thenReturn(Optional.empty());
        assertFalse(executeFieldPipeline.isResolvableReference(contact.getFieldByName("acctId"), new GraphContext()));

        reset(schemaService);
        reset(entityRepo);
        reset(mappingGraphService);
        when(schemaService.getSyncariEntityByName("account")).thenReturn(Optional.of(account));
        when(entityRepo.count(account, Optional.empty())).thenReturn(1l);
        when(mappingGraphService.retrieveApprovedEntityGraph(account.getId())).thenReturn(Optional.of(new MappingGraph()));
        //multiple executions result in one call to the graph
        final GraphContext currentContext = new GraphContext();
        assertTrue(executeFieldPipeline.isResolvableReference(contact.getFieldByName("acctId"), currentContext));
        assertTrue(executeFieldPipeline.isResolvableReference(contact.getFieldByName("acctId"), currentContext));
        assertTrue(executeFieldPipeline.isResolvableReference(contact.getFieldByName("acctId"), currentContext));
        assertTrue(executeFieldPipeline.isResolvableReference(contact.getFieldByName("acctId"), currentContext));

        verify(schemaService,times(1)).getSyncariEntityByName("account");
        verify(entityRepo, times(1)).count(account, Optional.empty());
    }

    @Test
    public void testGraphTraversal() {
        EntityDefinition source = SchemaHelper.createEntityDefinition("srcContact")
                .string("srcFirstName").string("srcLastName").string("srcEmail").id().getEntityDefinition();
        EntityDefinition coreEntity = SchemaHelper.createEntityDefinition("contact")
                .string("firstName").string("lastName").string("email").id().getEntityDefinition();
        EntityDefinition dest = SchemaHelper.createEntityDefinition("destContact")
                .string("destFirstName").string("destLastName").string("destEmail").id().getEntityDefinition();


        final MappingGraph firstNameGraph = newGraph(coreEntity.getFieldByName("firstName"))
                .src(source.getFieldByName("srcFirstName"))
                .dest(dest.getFieldByName("destFirstName"))
                .function("filter", "filter1")
                .function("setValue", "Set Value 1")
                .function("isFalse", "Is False")
                .function("setValue", "Set Value 2")
                .function("filter", "filter2")
                .connect("srcFirstName", "filter1")
                .connect("filter1", "Set Value 1")
                .connect("filter1", "Is False")
                .connect("Set Value 1", "filter2")
                .connect("Is False", "Set Value 2")
                .connect("Set Value 2", "filter2")
                .connect("filter2", "destFirstName").getGraph().setScope(Scope.ATTRIBUTE);


        firstNameGraph.getNodes().forEach(n -> n.setConfiguration(new NodeConfiguration() {
            @Override
            public MappingNodeType getNodeType() {
                return null;
            }

            @Override
            public String getApiName() {
                return "";
            }

            @Override
            public List<OutputPort> getOutputPorts() {
                return null;
            }

            @Override
            public List<InputPort> getInputPorts() {
                return null;
            }

            @Override
            public void validate(String graphName, String nodeName) {

            }

            @Override
            public List<ValidationError> validateWithoutException(Scope scope, String graphName, String nodeId, String nodeName) {
                return null;
            }

            @Override
            public Map<String, Object> getConfigMap() {
                return null;
            }

            @Override
            public void accept(NodeConfigurationVisitor visitor, MappingNode node) {

            }

            @Override
            public void accept(NodeConfigurationVisitor visitor) {

            }

            @Override
            public void accept(NodeValidatorVisitor validator, ValidationContext validationContext) {

            }

            @Override
            public List<ValidationError> acceptWithoutException(NodeValidatorVisitor validator, ValidationContext validationContext) {
                return null;
            }
        }));

        JTwigPipelineEvaluator evaluator = spy(new JTwigPipelineEvaluator(null, null, null, null,null));

        var destNode = firstNameGraph.getNodeByName("destFirstName").get();

        ArgumentCaptor<MappingNode> nodeCaptor = ArgumentCaptor.forClass(MappingNode.class);
        //graphs.put(coreEntity.getFieldByName("lastName"),lastNameGraph);
        evaluator.evaluate(destNode, firstNameGraph, new GraphContext().setGraph(firstNameGraph), n -> n.getType() == MappingNodeType.ATTRIBUTE_SOURCE, new HashSet<>());

        verify(evaluator, times(7)).evaluateV1(nodeCaptor.capture(), eq(firstNameGraph), any(GraphContext.class), ArgumentMatchers.<Predicate<MappingNode>>any(), any());

    }


    @Test
    public void upsertIdMappingWithMergeOperation(){

        IdMappingRepo idMappingRepo = mock(IdMappingRepo.class);

        FeatureService featureService = mock(FeatureService.class);

        EntityRepoService repoService = mock(EntityRepoService.class);

        when(idMappingRepo.save(any())).thenReturn(new IdMapping());
        doNothing().when(repoService).connectExternalId(any(), any(), any(), any(), any());

        //when(featureService.isEnabled(Features.EntityBatching, true)).thenReturn(true);

        ExecuteFieldPipeline executeFieldPipeline = new ExecuteFieldPipeline(null, null, null, null,
                null, null, null, null,
                idMappingRepo, null, null, repoService, null, null, null, featureService, pipelineUtil,null);

        final EntityDefinition entityDefinition1 = SchemaHelper.createEntityDef("externalContact1", "External Contact1").setConnectorId("c1");
        final EntityDefinition entityDefinition2 = SchemaHelper.createEntityDef("externalContact2", "External Contact2").setConnectorId("c2");

        StagedBatchRecord r1 = new StagedBatchRecord().setExternalRecordId("e1").setExternalEntityDefinitionId(entityDefinition1.getId())
                .setSyncariId("s1").setEntityData(new EntityData().setConnectorId("c1").setId("e1")).setStagedBatchId("sb1").setNew(true);
        StagedBatchRecord r2 = new StagedBatchRecord().setExternalRecordId("e2").setExternalEntityDefinitionId(entityDefinition2.getId())
                .setSyncariId("s1").setEntityData(new EntityData().setConnectorId("c2").setId("e2")).setStagedBatchId("sb2").setNew(true);
        ArgumentCaptor<List<IdMapping>> saveCaptor = ArgumentCaptor.forClass(List.class);

        CurrentBatch batch = mock(CurrentBatch.class);
        when(batch.lookupConnectorIdByBatchId("sb1")).thenReturn(entityDefinition1);
        when(batch.lookupConnectorIdByBatchId("sb2")).thenReturn(entityDefinition2);
        //MergeOperation mergeOperation = new MergeOperation().setWinningRecord().setLosingRecords();

        List<IdMapping> idMappingBatch = new ArrayList<>();

        executeFieldPipeline.upsertIdMappings(batch, "contact", new RecordsBySyncariId("s1").addRecord(r1).addRecord(r2), idMappingBatch,
                Optional.empty(), null, null, Optional.empty());

        assertEquals(1, idMappingBatch.size());
        assertEquals(2, idMappingBatch.get(0).getMappings().size());

        MergeOperation mergeOperation = new MergeOperation().setWinningRecord(new EntityData().setId("s1")).setLosingRecords(List.of());
        executeFieldPipeline.upsertIdMappings(batch, "contact", new RecordsBySyncariId("s1").addRecord(r1).addRecord(r2), idMappingBatch,Optional.of(mergeOperation),
                null, null, Optional.empty());

        assertTrue(idMappingBatch.isEmpty());
        verify(idMappingRepo).upsert(saveCaptor.capture());
        final IdMapping savedIdMappings = saveCaptor.getValue().get(0);
        assertNotNull(savedIdMappings);
        assertEquals(2, savedIdMappings.getMappings().size());
    }

    private ViperContext getViperContext(){
        ViperContext context = new ViperContext(SyncariContext.getOrganziation(), SyncariContext.getInstance(), SyncariContext.getUser());
        context.setUpdateWatermark(false);
        return context;
    }

    private List<StagedBatchRecord> getStagedBatchRecords(List<EntityData> entityDatas, EntityDefinition srcEntity, EntityDefinition syncariEntity){

        StagedBatch staged = new StagedBatch(syncariEntity.getApiName()).setConnectorId(srcEntity.getConnectorId())
                    .setCurrentBatchId(UUID.randomUUID().toString()).setSourceEntityName(srcEntity.getApiName())
                    .setSourceEntityDefinitionId(srcEntity.getId());
            staged.setId(UUID.randomUUID().toString());

        List<StagedBatchRecord> stagedBatchRecords = new ArrayList();
        for (EntityData entityData: entityDatas) {
            StagedBatchRecord record = new StagedBatchRecord()
                    .setStagedBatchId(staged.getId())
                    .setEntityData(entityData)
                    .setExternalRecordId(entityData.getId())
                    .setExternalEntityDefinitionId(srcEntity.getId());
            record.setId(UUID.randomUUID().toString());
            record.setSyncariId(entityData.getSyncariEntityId());
            stagedBatchRecords.add(record);
        }
        
        return stagedBatchRecords;
    }

}