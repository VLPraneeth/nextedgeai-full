package com.syncari.viper.streams.stages;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.MergeRequest;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.SyncResponse;
import com.syncari.connector.service.def.DataService;
import com.syncari.core.DataTransformer;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.StringType;
import com.syncari.core.event.store.EventStore;
import com.syncari.core.exceptions.PipelineException;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.Watermark;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.jtwig.JTwigPipelineEvaluator;
import com.syncari.core.pipeline.jtwig.TokenEnvironment;
import com.syncari.core.pipeline.jtwig.TokenEnvironmentConfig;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.repositories.customer.StagedBatchRecordRepo;
import com.syncari.core.repositories.customer.StagedExternalRecordRepo;
import com.syncari.core.service.*;
import com.syncari.core.sync.CurrentBatch;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.utils.GraphHelper;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.viper.ViperContext;
import org.bson.types.ObjectId;
import org.jtwig.environment.DefaultEnvironmentConfiguration;
import org.jtwig.environment.EnvironmentFactory;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.syncari.core.utils.GraphHelper.createConnector;
import static com.syncari.core.utils.GraphHelper.newGraph;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SaveToSinkMockTest {
    @Test
    public void deleteMergeLoserIdMappings() {
        SaveToSink saveToSink = new SaveToSink();
        saveToSink.idMappingService = Mockito.mock(IdMappingService.class);
        EntityDefinition sink = new EntityDefinition("account", "Account");
        sink.setId("sinkId");
        MergeRequest mergeRequest = new MergeRequest(new ConnectorInfo(), new EntitySchema("account", "Account"));
        mergeRequest.setWinner(new EntityData("account").setId("winnerExternalId").setSyncariEntityId("winnerSyncariId"));
        mergeRequest.setLosers(List.of(
                new EntityData("account").setId("loserExternalId1").setSyncariEntityId("loserSyncariId1"),
                new EntityData("account").setId("loserExternalId2").setSyncariEntityId("loserSyncariId2")
                )
        );
        IdMapping toDelete = new IdMapping().setSyncariId("loserSyncariId2").setEntityName("account").addMapping("connector", "loserExternalId2", "sinkId");
        IdMapping toUpdate = new IdMapping().setSyncariId("loserSyncariId1").setEntityName("account").addMapping("connector", "loserExternalId1", "sinkId")
                .addMapping("secondConnector", "secondExternalId", "secondSink");
        when(saveToSink.idMappingService.findBySyncariIds("account", List.of("loserSyncariId1", "loserSyncariId2")))
                .thenReturn(List.of(
                        //THis idmapping is updated
                        toUpdate,
                        //This is deleted
                        toDelete
                ));
        saveToSink.deleteLoserIdMapping("connector", sink,
                "account", mergeRequest);
        assertTrue(toUpdate.findMapping("connector", "sinkId", "loserExternalId1").isEmpty());
        assertTrue(toUpdate.findMapping("secondConnector", "secondSink", "secondExternalId").isPresent());
        assertTrue(toDelete.getMappings().isEmpty());
        verify(saveToSink.idMappingService, times(1)).deleteAll(List.of(toDelete));
        verify(saveToSink.idMappingService, times(1)).saveAll(List.of(toUpdate));
    }

    @Test
    public void acceptDeletesFromSelectedSourcesOnly() {
        SaveToSink saveToSink = new SaveToSink();
        saveToSink.idMappingService = Mockito.mock(IdMappingService.class);
        EntityDefinition sink = new EntityDefinition("account", "Account");
        sink.setId("sinkId");
        MergeRequest mergeRequest = new MergeRequest(new ConnectorInfo(), new EntitySchema("account", "Account"));
        mergeRequest.setWinner(new EntityData("account").setId("winnerExternalId").setSyncariEntityId("winnerSyncariId"));
        mergeRequest.setLosers(List.of(
                        new EntityData("account").setId("loserExternalId1").setSyncariEntityId("loserSyncariId1"),
                        new EntityData("account").setId("loserExternalId2").setSyncariEntityId("loserSyncariId2")
                )
        );
        IdMapping toDelete = new IdMapping().setSyncariId("loserSyncariId2").setEntityName("account").addMapping("connector", "loserExternalId2", "sinkId");
        IdMapping toUpdate = new IdMapping().setSyncariId("loserSyncariId1").setEntityName("account").addMapping("connector", "loserExternalId1", "sinkId")
                .addMapping("secondConnector", "secondExternalId", "secondSink");
        when(saveToSink.idMappingService.findBySyncariIds("account", List.of("loserSyncariId1", "loserSyncariId2")))
                .thenReturn(List.of(
                        //THis idmapping is updated
                        toUpdate,
                        //This is deleted
                        toDelete
                ));
        saveToSink.deleteLoserIdMapping("connector", sink,
                "account", mergeRequest);
        assertTrue(toUpdate.findMapping("connector", "sinkId", "loserExternalId1").isEmpty());
        assertTrue(toUpdate.findMapping("secondConnector", "secondSink", "secondExternalId").isPresent());
        assertTrue(toDelete.getMappings().isEmpty());
        verify(saveToSink.idMappingService, times(1)).deleteAll(List.of(toDelete));
        verify(saveToSink.idMappingService, times(1)).saveAll(List.of(toUpdate));
    }

    @Test
    public void removeUnchangedWithModifiedStagedBatch() {
        SaveToSink saveToSink = new SaveToSink();
        EntityDefinition entityDef = SchemaHelper.createEntityDef("customObj", "customObj", null);
        entityDef.addField(SchemaHelper.createAttribute("attrib1", StringType.VALUE, entityDef.getId()));
        entityDef.addField(SchemaHelper.createAttribute("attrib2", StringType.VALUE, entityDef.getId()));
        entityDef.addField(SchemaHelper.createAttribute("attrib3", StringType.VALUE, entityDef.getId()));
        entityDef.addField(SchemaHelper.createAttribute("id", StringType.VALUE, entityDef.getId()).setIdField(true));
        EntitySchema schema = new DataTransformer().toEntitySchema(entityDef, GraphHelper.createConnector("connectorName", "connectorId", "metaId"));

        EntityData data = new EntityData();
        data.addValue("attrib1", "v1");
        data.addValue("attrib2", "");
        data.addValue("attrib3", null);
        data.setName("customObj");
        data.setId(ObjectId.get().toHexString());
        Map<String, CoreAttributeNodeConfig> sinkToCoreConfigMap = Map.of(
                "attrib1", new CoreAttributeNodeConfig().setRejectEmptyString(true),
                "attrib2", new CoreAttributeNodeConfig().setRejectEmptyString(false),
                "attrib3", new CoreAttributeNodeConfig().setRejectEmptyString(true)
        );
        HashMap<String, AttributeDefinition> sinkAttribMap = new HashMap<>();
        entityDef.getAttributes().forEach(a -> sinkAttribMap.put(a.getApiName(), a));
        StagedBatchRecord record = new StagedBatchRecord();
        record.setEntityData(new EntityData().addValue("attrib1", "v1").addValue("attrib2", "").addValue("attrib3", "").setName("customObj"));
        saveToSink.removeUnchangedFields(sinkAttribMap, schema, data, Optional.of(record), sinkToCoreConfigMap);
        //no changes detected, so all fields removed
        assertTrue(data.getValues().isEmpty());

        EntityData data2 = new EntityData("customObj").addValue("attrib1", "v1").addValue("attrib2", "v2").addValue("attrib3", "v3").setName("customObj")
                .setId(ObjectId.get().toHexString());
        //record is marked as modified, no fields removed, even though it looks like there were no changes
        record.setModifiedByPipeline(true);
        saveToSink.removeUnchangedFields(sinkAttribMap, schema, data2, Optional.of(record), sinkToCoreConfigMap);
        assertFalse(data2.getValues().isEmpty());
        assertEquals("v1", data2.getValue("attrib1"));
        assertEquals("v2", data2.getValue("attrib2"));
        assertEquals("v3", data2.getValue("attrib3"));
    }


    @Test
    public void removeUnchangedNulls() {
        SaveToSink saveToSink = new SaveToSink();
        final EntityDefinition entityDef = SchemaHelper.createEntityDefinition("customObj")
                .bool("attrib1").id().getEntityDefinition();
        EntitySchema schema = new DataTransformer().toEntitySchema(entityDef, GraphHelper.createConnector("connectorName", "connectorId", "metaId"));

        EntityData data = new EntityData();
        data.addValue("attrib1", null);
        data.setId(ObjectId.get().toHexString());
        Map<String, CoreAttributeNodeConfig> sinkToCoreConfigMap = Map.of(
                "attrib1", new CoreAttributeNodeConfig().setRejectEmptyString(true)
        );
        HashMap<String, AttributeDefinition> sinkAttribMap = new HashMap<>();
        entityDef.getAttributes().forEach(a -> sinkAttribMap.put(a.getApiName(), a));
        StagedBatchRecord record = new StagedBatchRecord();
        record.setEntityData(new EntityData().addValue("attrib1", null).setName("customObj"));
        saveToSink.removeUnchangedFields(sinkAttribMap, schema, data, Optional.of(record), sinkToCoreConfigMap);
        //no changes detected, so all fields removed
        assertTrue(data.getValues().isEmpty());

        EntityData data2 = new EntityData("customObj").setName("customObj")
                .setId(ObjectId.get().toHexString());
        //record is marked as modified, no fields removed, even though it looks like there were no changes
        record.setModifiedByPipeline(true);
        saveToSink.removeUnchangedFields(sinkAttribMap, schema, data2, Optional.of(record), sinkToCoreConfigMap);
        assertTrue(data2.getValues().isEmpty());
    }

    @Test
    public void defaultValuesConvertedCorrectly() {
        SaveToSink saveToSink = new SaveToSink();
        saveToSink.tokenHelper = new TokenHelper(new TokenEnvironmentConfig().tokenEnvironment());

        final EntityDefinition entityDef = SchemaHelper.createEntityDefinition("customObj")
                .bool("attrib1")
                .string("attrib2", true)
                .id().getEntityDefinition();
        final AttributeDefinition sinkAttribute = entityDef.getFieldByName("attrib1");
        final AttributeDefinition sinkAttribute2 = entityDef.getFieldByName("attrib2");
        final AttributeSinkNodeConfig sinkNodeCOnfig = new AttributeSinkNodeConfig();
        sinkNodeCOnfig.setDefaultValue(null);
        final Object defaultValueListNull = saveToSink.getDefaultValue(sinkNodeCOnfig, sinkAttribute2, new GraphContext());
        assertNull(defaultValueListNull);
        sinkNodeCOnfig.setDefaultValue("");
        final Object defaultValueList = saveToSink.getDefaultValue(sinkNodeCOnfig, sinkAttribute2, new GraphContext());
        assertEquals(List.of(), defaultValueList);
        sinkNodeCOnfig.setDefaultValue("false");
        final Object defaultValue = saveToSink.getDefaultValue(sinkNodeCOnfig, sinkAttribute, new GraphContext());
        assertEquals(false, defaultValue);

    }

    @Test
    public void filterTransactionLogFields() {

        SaveToSink saveToSink = new SaveToSink();
        EntityDefinition entityDef = SchemaHelper.createEntityDef("customObj", "customObj", null);
        entityDef.addField(SchemaHelper.createAttribute("attrib1", StringType.VALUE, entityDef.getId()));
        entityDef.addField(SchemaHelper.createAttribute("attrib2", StringType.VALUE, entityDef.getId()));
        entityDef.addField(SchemaHelper.createAttribute("attrib3", StringType.VALUE, entityDef.getId()));
        entityDef.addField(SchemaHelper.createAttribute("id", StringType.VALUE, entityDef.getId()).setIdField(true));

        GraphContext context = new GraphContext();
        AttributeDefinition syncariField1 = entityDef.getFieldByName("attrib1");
        AttributeDefinition syncariField2 = entityDef.getFieldByName("attrib2");
        AttributeDefinition syncariField3 = entityDef.getFieldByName("attrib3");
        context.set("sinkField_externalAttrib1", syncariField1);
        context.set("sinkField_externalAttrib2", syncariField2);
        context.set("sinkField_externalAttrib3", syncariField3);

        TransactionLog log = new TransactionLog().addChange(new FieldChange().setFieldId(syncariField1.getId()).setNewValue("v1").setOldValue("v2"));
        EntityData data = new EntityData();
        data.addValue("externalAttrib1", "v1");
        data.addValue("externalAttrib2", "ev2");
        data.addValue("externalAttrib3", "ev3");
        data.setName("customObj");
        data.setId(ObjectId.get().toHexString());

        saveToSink.filterTransactionFields(data, false, List.of(log), context, new EntitySchema());

        assertEquals(3, data.getValues().size());

        saveToSink.filterTransactionFields(data, true, List.of(log), context, new EntitySchema());

        assertEquals(1, data.getValues().size());

        TransactionLog log1 = new TransactionLog().addChange(new FieldChange().setFieldId(syncariField1.getId()).setNewValue("v1").setOldValue("v2"));
        TransactionLog log2 = new TransactionLog().addChange(new FieldChange().setFieldId(syncariField2.getId()).setNewValue("ev1").setOldValue("ev2"));

        data = new EntityData();
        data.addValue("externalAttrib1", "v1");
        data.addValue("externalAttrib2", "ev2");
        data.addValue("externalAttrib3", "ev3");
        data.setName("customObj");
        data.setId(ObjectId.get().toHexString());

        saveToSink.filterTransactionFields(data, true, List.of(log1, log2), context, new EntitySchema());
        assertEquals(2, data.getValues().size());

        // set the data is deleted and confirm no fields are removed
        data = new EntityData();
        data.addValue("externalAttrib1", "v1");
        data.addValue("externalAttrib2", "ev2");
        data.addValue("externalAttrib3", "ev3");
        data.setName("customObj");
        data.setDeleted(true);
        data.setId(ObjectId.get().toHexString());
        saveToSink.filterTransactionFields(data, true, List.of(log1, log2), context, new EntitySchema());
        assertEquals(3, data.getValues().size());

        // no transaction logs, all fields removed
        data = new EntityData();
        data.addValue("externalAttrib1", "v1");
        data.addValue("externalAttrib2", "ev2");
        data.addValue("externalAttrib3", "ev3");
        data.setName("customObj");
        data.setId(ObjectId.get().toHexString());
        saveToSink.filterTransactionFields(data, true, List.of(), context, new EntitySchema());
        assertEquals(0, data.getValues().size());
    }

    @Test
    public void retrieveActiveSinksInGraph() {
        SaveToSink saveToSink = new SaveToSink();
        SchemaService mockSchemaService = mock(SchemaService.class);
        ConnectorService mockConnService = mock(ConnectorService.class);

        saveToSink.schemaService = mockSchemaService;
        saveToSink.connectorService = mockConnService;

        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        var coreField1 = SchemaHelper.createAttribute("corefield1", StringType.VALUE, coreEntity.getId());
        coreEntity.addField(coreField1);

        Connector connector = createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta");
        EntityDefinition srcEntity = SchemaHelper.createEntityDef("srcAccount", "Source Account", connector);
        var srcField1 = SchemaHelper.createAttribute("srcfield1", StringType.VALUE, srcEntity.getId());
        srcEntity.addField(srcField1);

        MappingGraph entityGraph = newGraph(coreEntity, null)
                .src(srcEntity, "srcAccount")
                .dest(srcEntity, "sinkAccount")
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "sinkAccount")
                .getGraph();

        doReturn(List.of(srcEntity)).when(mockSchemaService).refreshSynapseSchema(any(), any(), any());
        doReturn(coreEntity).when(mockSchemaService).getEntity(anyString());
        doReturn(List.of(connector)).when(mockConnService).getAllActive();

        entityGraph.getCoreNode().setConfiguration(new CoreEntityNodeConfig().setEntityDefinition(coreEntity));

        // schema refresh is not done when same entity is also a source node
        Optional<EntityDefinition> refreshedSinks = saveToSink.refreshSchema(srcEntity, connector, entityGraph, false);
        assertFalse(refreshedSinks.isEmpty());

        assertEquals(srcEntity, refreshedSinks.get());
        verify(mockSchemaService, never()).refreshSynapseSchema(any(), any(), any());

        // remove source
        entityGraph.removeSource(srcEntity.getId());
        refreshedSinks = saveToSink.refreshSchema(srcEntity, connector, entityGraph, false);
        assertFalse(refreshedSinks.isEmpty());
        assertEquals(srcEntity, refreshedSinks.get());
        verify(mockSchemaService).refreshSynapseSchema(any(), any(), any());
    }

    @Test
    public void retrieveActiveSinksInGraph_SimulationMode() {
        SaveToSink saveToSink = new SaveToSink();
        SchemaService mockSchemaService = mock(SchemaService.class);
        ConnectorService mockConnService = mock(ConnectorService.class);

        saveToSink.schemaService = mockSchemaService;
        saveToSink.connectorService = mockConnService;

        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        var coreField1 = SchemaHelper.createAttribute("corefield1", StringType.VALUE, coreEntity.getId());
        coreEntity.addField(coreField1);

        Connector connector = createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta");
        EntityDefinition srcEntity = SchemaHelper.createEntityDef("srcAccount", "Source Account", connector);
        var srcField1 = SchemaHelper.createAttribute("srcfield1", StringType.VALUE, srcEntity.getId());
        srcEntity.addField(srcField1);

        MappingGraph entityGraph = newGraph(coreEntity, null)
                .src(srcEntity, "srcAccount")
                .dest(srcEntity, "sinkAccount")
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "sinkAccount")
                .getGraph();

        doReturn(List.of(srcEntity)).when(mockSchemaService).refreshSynapseSchema(any(), any(), any());
        doReturn(coreEntity).when(mockSchemaService).getEntity(anyString());
        doReturn(List.of(connector)).when(mockConnService).getAllActive();

        entityGraph.getCoreNode().setConfiguration(new CoreEntityNodeConfig().setEntityDefinition(coreEntity));
        var connectors = mockConnService.getAllActive();
        // schema refresh is not done when same entity is also a source node
        Optional<EntityDefinition> refreshedSinks = saveToSink.refreshSchema(srcEntity, connector, entityGraph, true);
        assertFalse(refreshedSinks.isEmpty());

        assertEquals(srcEntity, refreshedSinks.get());
        verify(mockSchemaService, never()).refreshSynapseSchema(any(), any(), any());

        // remove source
        entityGraph.removeSource(srcEntity.getId());
        refreshedSinks = saveToSink.refreshSchema(srcEntity, connector, entityGraph, true);
        assertFalse(refreshedSinks.isEmpty());

        assertEquals(srcEntity, refreshedSinks.get());
        verify(mockSchemaService, never()).refreshSynapseSchema(any(), any(), any());
    }

    @Test
    public void retrieveSinkWithError() {
        SaveToSink saveToSink = new SaveToSink();
        SchemaService mockSchemaService = mock(SchemaService.class);
        ConnectorService mockConnService = mock(ConnectorService.class);
        saveToSink.schemaService = mockSchemaService;
        saveToSink.connectorService = mockConnService;


        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        var coreField1 = SchemaHelper.createAttribute("corefield1", StringType.VALUE, coreEntity.getId());
        coreEntity.addField(coreField1);

        Connector connector = createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta");
        EntityDefinition srcEntity = SchemaHelper.createEntityDef("srcAccount", "Source Account", connector);
        EntityDefinition sinkEntity = SchemaHelper.createEntityDef("sinkAccount", "Sink Account", connector);
        var srcField1 = SchemaHelper.createAttribute("srcfield1", StringType.VALUE, srcEntity.getId());
        var sinkField1 = SchemaHelper.createAttribute("sinkfield1", StringType.VALUE, sinkEntity.getId());
        srcEntity.addField(srcField1);
        sinkEntity.addField(sinkField1);

        MappingGraph entityGraph = newGraph(coreEntity, null)
                .src(srcEntity, "srcAccount")
                .dest(sinkEntity, "sinkAccount")
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "sinkAccount")
                .getGraph();

        when(mockSchemaService.refreshSynapseSchema(any(), any(), any()))
                .thenThrow(new RuntimeException("Test exception"));
        //doReturn(List.of(srcEntity)).when(mockSchemaService).refreshSynapseSchema(any(), any(), any());
        doReturn(coreEntity).when(mockSchemaService).getEntity(anyString());
        doReturn(List.of(connector)).when(mockConnService).getAllActive();

        entityGraph.getCoreNode().setConfiguration(new CoreEntityNodeConfig().setEntityDefinition(coreEntity));

        Exception e = null;
        try {
            Optional<EntityDefinition> refreshedSinks = saveToSink.refreshSchema(sinkEntity, connector, entityGraph, false);
        } catch (Exception ex) {
            e = ex;
        }

        assertNotNull(e);
        assertTrue(e instanceof PipelineException);
        assertEquals("java.lang.RuntimeException: Test exception", e.getMessage());
        assertEquals(entityGraph.getSinks().collect(Collectors.toList()).get(0).getId(), ((PipelineException) e).getNodeId());
        assertEquals(entityGraph.getId(), ((PipelineException) e).getGraphId());
        assertEquals(entityGraph.getScope(), ((PipelineException) e).getScope());

    }

    @Test
    public void schemaRefreshNeverCalledIfNoUpdatesFound() {
        SaveToSink saveToSink = new SaveToSink();
        SchemaService mockSchemaService = mock(SchemaService.class);
        ConnectorService mockConnService = mock(ConnectorService.class);
        WatermarkService mockWMService = mock(WatermarkService.class);
        EntityRepo mockEntityRepo = mock(EntityRepo.class);

        saveToSink.schemaService = mockSchemaService;
        saveToSink.connectorService = mockConnService;
        saveToSink.watermarkService = mockWMService;
        saveToSink.entityRepo = mockEntityRepo;

        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        var coreField1 = SchemaHelper.createAttribute("corefield1", StringType.VALUE, coreEntity.getId());
        coreEntity.addField(coreField1);

        Connector connector = createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta");
        EntityDefinition srcEntity = SchemaHelper.createEntityDefinition("srcAccount", connector)
                .string("srcfield1").getEntityDefinition();
        EntityDefinition sinkEntity = SchemaHelper
                .createEntityDefinition("sinkAccount", connector)
                .string("sinkfield1")
                .getEntityDefinition();
        MappingGraph entityGraph = newGraph(coreEntity, null)
                .src(srcEntity, "srcAccount")
                .dest(sinkEntity, "sinkAccount")
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "sinkAccount")
                .getGraph();

        doReturn(sinkEntity).when(mockSchemaService).getEntity(sinkEntity.getId());

        when(mockWMService.getOrCreateDownstreamWatermark(eq("srcAccount"), eq(sinkEntity))).thenReturn(
                new SyncDetail(sinkEntity.getId(), "srcAccount", new Watermark(0, 0, false, 0))
        );
        when(mockEntityRepo.find(eq(coreEntity), any(), any(PageCursor.class))).thenReturn(List.of());
        // schema refresh is not done when same entity is also a source node

        final GraphContext graphContext = new GraphContext();
        graphContext.setCurrentBatch(new CurrentBatch(null).setSyncariEntity(srcEntity).setSyncariEntityName("srcAccount"));
        final CurrentBatch execute = saveToSink.execute(sinkEntity,
                new ViperContext(null, null, null), graphContext);

        verify(mockSchemaService, never()).refreshSynapseSchema(eq(connector.getId()), eq(sinkEntity), any());
    }

    @Test
    //TODO: Perhaps this should be the behavior even when SyncOnTxnLog flag is not set
    public void outOfBandTransactionsConsideredInChangeDetetctionWhenSyncOnTxnLogSet() {
        final SaveToSink saveToSink = new SaveToSink();

        final EntityDefinition destEntity = createEntityDef("destEntity", "destField");
        final EntityDefinition srcEntity = createEntityDef("srcEntity", "srcField");
        final EntityDefinition coreEntity = createEntityDef("coreEntity", "coreField");
        final var ep = newGraph(coreEntity)
                .src(srcEntity)
                .dest(destEntity)
                .connect(srcEntity.getApiName(), coreEntity.getApiName())
                .connect(coreEntity.getApiName(), destEntity.getApiName())
                .getGraph();
        final EntitySinkNodeConfig destConfig = ep.getSink(destEntity.getId()).get(0).getTypedConfiguration();
        destConfig.setSyncOnTxnLog(true);
        List<MappingGraph> fps = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final AttributeDefinition coreField = coreEntity.getFieldByName("coreField" + i);
            final AttributeDefinition srcField = srcEntity.getFieldByName("srcField" + i);
            final AttributeDefinition destField = destEntity.getFieldByName("destField" + i);
            fps.add(newGraph(coreField)
                    .src(srcField)
                    .dest(destField)
                    .connect(srcField.getApiName(), coreField.getApiName())
                    .connect(coreField.getApiName(), destField.getApiName())
                    .getGraph());
        }
        final GraphContext graphContext = new GraphContext();
        final CurrentBatch currentBatch = new CurrentBatch(null).setSyncariEntityName(coreEntity.getApiName());

        currentBatch.setExternalRecordRepo(mock(StagedExternalRecordRepo.class));
        when(currentBatch.getExternalRecordRepo().findByExternalRecordIdAndExternalEntityDefinitionId(any(), any()))
                .thenReturn(Optional.empty());
        currentBatch.setCurrentBatchId(UUID.randomUUID().toString());
        graphContext.setCurrentBatch(currentBatch);
        graphContext.setGraph(ep);
        graphContext.setSyncariEntity(coreEntity);
        saveToSink.schemaService = mock(SchemaService.class);
        saveToSink.connectorService = mock(ConnectorService.class);
        saveToSink.watermarkService = mock(WatermarkService.class);
        saveToSink.entityRepo = mock(EntityRepo.class);
        saveToSink.dataTransformer = new DataTransformer();
        saveToSink.dataServiceFactory = mock(DataServiceFactory.class);
        saveToSink.graphService = mock(MappingGraphService.class);
        saveToSink.attributeProxyRepo = mock(AttributeRepo.class);
        saveToSink.transactionLogService = mock(TransactionLogService.class);
        saveToSink.unresolvedRecordService = mock(UnresolvedRecordService.class);
        saveToSink.idMappingService = mock(IdMappingService.class);
        saveToSink.syncDetailMetricService = mock(SyncDetailMetricService.class);
        saveToSink.eventStore = mock(EventStore.class);
        saveToSink.pipelineUtil = mock(PipelineUtil.class);
        saveToSink.stagedBatchRecordRepo = mock(StagedBatchRecordRepo.class);
        TokenEnvironment environment = new TokenEnvironment(new EnvironmentFactory().create(new DefaultEnvironmentConfiguration()), Map.of());
        saveToSink.evaluator = new JTwigPipelineEvaluator(environment, new TokenHelper(environment), null, null, null);

        when(saveToSink.stagedBatchRecordRepo.findByStagedBatchIdAndSyncariIdsAndEntity(any(), any(), any())).thenReturn(List.of());
        when(saveToSink.schemaService.getEntity(destEntity.getId())).thenReturn(destEntity);
        when(saveToSink.connectorService.get(destEntity.getConnectorId())).thenReturn(destEntity.getConnector());
        final SyncDetail t = new SyncDetail();
        t.setWatermark(new Watermark(0, System.currentTimeMillis(), false, 0));
        when(saveToSink.watermarkService.getOrCreateDownstreamWatermark(
                coreEntity.getApiName(), destEntity
        )).thenReturn(t);
        final List<EntityData> records = generateRecords(coreEntity, 1);
        when(saveToSink.entityRepo.find(eq(coreEntity), any(Instant.class),
                any(PageCursor.class)))
                .thenReturn(records);
        final ArgumentCaptor<SyncRequest> syncRequestCaptor = ArgumentCaptor.forClass(SyncRequest.class);
        DataService mockDataService = mock(DataService.class);
        when(mockDataService.update(syncRequestCaptor.capture())).thenReturn(new SyncResponse());
        when(saveToSink.dataServiceFactory.getDataService(any())).thenReturn(mockDataService);
        when(saveToSink.graphService.retrieveAttributeGraphsForEntityGraph(ep.getId())).thenReturn(fps);
        when(saveToSink.attributeProxyRepo.findById(anyString())).thenAnswer(invocation ->
                Optional.of(coreEntity.getAttribute(invocation.getArgument(0).toString()))
        );
        when(saveToSink.transactionLogService.findMergesByBatchId(anyString(), any(Date.class), any(PageCursor.class)))
                .thenReturn(new com.syncari.core.model.pagination.Page<>(null, List.of()));
        final TransactionLog txLog = new TransactionLog();
        txLog.setSyncariId(records.get(0).getId());
        txLog.setOperation(Operation.update);
        coreEntity.getAttributes().forEach(a -> {
            if (!a.isIdField()) {
                txLog.addChange(new FieldChange().setFieldId(a.getId()).setApiName(a.getApiName()).setNewValue(records.get(0).getValue(a.getApiName()) + "_newValue").setOldValue(records.get(0).getValue(a.getApiName())));
            }
        });

        when(saveToSink.transactionLogService.findTransactions(eq(coreEntity), eq(List.of(records.get(0).getId())), anyLong()))
                .thenReturn(List.of(txLog));
        doNothing().when(saveToSink.unresolvedRecordService).upsert(anyList());
        doNothing().when(saveToSink.unresolvedRecordService).delete(anyList());
        when(saveToSink.idMappingService.findBySyncariIds(eq(coreEntity.getApiName()), anySet()))
                .thenAnswer(invocation -> generateIdMappings(coreEntity, destEntity, invocation.getArgument(1)));
        when(saveToSink.syncDetailMetricService.updateSyncDetailMetric(any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        doNothing().when(saveToSink.eventStore).insertErrorLogs(anyList());
        when(saveToSink.pipelineUtil.getEntitySyncErrorMetrics(any(), any())).thenReturn(Stream.empty());
        long t1 = System.currentTimeMillis();
        Instance instance = new Instance();
        instance.setSyncariId("99999");
        SyncariContext.runWithContext(new Organization(), instance, SyncariContext.getUser(), () -> {
            final CurrentBatch execute = saveToSink.execute(destEntity,
                    new ViperContext(new Organization(), new Instance(), new User()), graphContext);
            List<EntityData> data = syncRequestCaptor.getValue().getData().get(destEntity.getConnectorId());
            assertEquals(1, data.size());
        });
    }
    @Test
    public void profileSaveToSink() {
        final SaveToSink saveToSink = new SaveToSink();

        final EntityDefinition destEntity = createEntityDef("destEntity", "destField");
        final EntityDefinition srcEntity = createEntityDef("srcEntity", "srcField");
        final EntityDefinition coreEntity = createEntityDef("coreEntity", "coreField");
        final var ep = newGraph(coreEntity)
                .src(srcEntity)
                .dest(destEntity)
                .connect(srcEntity.getApiName(), coreEntity.getApiName())
                .connect(coreEntity.getApiName(), destEntity.getApiName())
                .getGraph();
        List<MappingGraph> fps = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            final AttributeDefinition coreField = coreEntity.getFieldByName("coreField" + i);
            final AttributeDefinition srcField = srcEntity.getFieldByName("srcField" + i);
            final AttributeDefinition destField = destEntity.getFieldByName("destField" + i);
            fps.add(newGraph(coreField)
                    .src(srcField)
                    .dest(destField)
                    .connect(srcField.getApiName(), coreField.getApiName())
                    .connect(coreField.getApiName(), destField.getApiName())
                    .getGraph());
        }
        final GraphContext graphContext = new GraphContext();
        final CurrentBatch currentBatch = new CurrentBatch(null).setSyncariEntityName(coreEntity.getApiName());

        currentBatch.setExternalRecordRepo(mock(StagedExternalRecordRepo.class));
        when(currentBatch.getExternalRecordRepo().findByExternalRecordIdAndExternalEntityDefinitionId(any(), any()))
                .thenReturn(Optional.empty());
        currentBatch.setCurrentBatchId(UUID.randomUUID().toString());
        graphContext.setCurrentBatch(currentBatch);
        graphContext.setGraph(ep);
        graphContext.setSyncariEntity(coreEntity);
        saveToSink.schemaService = mock(SchemaService.class);
        saveToSink.connectorService = mock(ConnectorService.class);
        saveToSink.watermarkService = mock(WatermarkService.class);
        saveToSink.entityRepo = mock(EntityRepo.class);
        saveToSink.dataTransformer = new DataTransformer();
        saveToSink.dataServiceFactory = mock(DataServiceFactory.class);
        saveToSink.graphService = mock(MappingGraphService.class);
        saveToSink.attributeProxyRepo = mock(AttributeRepo.class);
        saveToSink.transactionLogService = mock(TransactionLogService.class);
        saveToSink.unresolvedRecordService = mock(UnresolvedRecordService.class);
        saveToSink.idMappingService = mock(IdMappingService.class);
        saveToSink.syncDetailMetricService = mock(SyncDetailMetricService.class);
        saveToSink.eventStore = mock(EventStore.class);
        saveToSink.pipelineUtil = mock(PipelineUtil.class);
        TokenEnvironment environment = new TokenEnvironment(new EnvironmentFactory().create(new DefaultEnvironmentConfiguration()), Map.of());
        saveToSink.evaluator = new JTwigPipelineEvaluator(environment, new TokenHelper(environment), null, null, null);


        when(saveToSink.schemaService.getEntity(destEntity.getId())).thenReturn(destEntity);
        when(saveToSink.connectorService.get(destEntity.getConnectorId())).thenReturn(destEntity.getConnector());
        final SyncDetail t = new SyncDetail();
        t.setWatermark(new Watermark(0, System.currentTimeMillis(), false, 0));
        when(saveToSink.watermarkService.getOrCreateDownstreamWatermark(
                coreEntity.getApiName(), destEntity
        )).thenReturn(t);
        when(saveToSink.entityRepo.find(eq(coreEntity), eq(Instant.ofEpochMilli(t.getWatermark().getStart())),
                any(PageCursor.class)))
                .thenReturn(List.of(new EntityData(coreEntity.getApiName())),
                        generateRecords(coreEntity, 500),
                        generateRecords(coreEntity, 500),
                        generateRecords(coreEntity, 500),
                        generateRecords(coreEntity, 500),
                        List.of());
        DataService mockDataService = mock(DataService.class);
        when(saveToSink.dataServiceFactory.getDataService(any())).thenReturn(mockDataService);
        when(saveToSink.graphService.retrieveAttributeGraphsForEntityGraph(ep.getId())).thenReturn(fps);
        when(saveToSink.attributeProxyRepo.findById(anyString())).thenAnswer(invocation ->
                Optional.of(coreEntity.getAttribute(invocation.getArgument(0).toString()))
        );
        when(saveToSink.transactionLogService.findMergesByBatchId(anyString(), any(Date.class), any(PageCursor.class)))
                .thenReturn(new com.syncari.core.model.pagination.Page<>(null, List.of()));
        doNothing().when(saveToSink.unresolvedRecordService).upsert(anyList());
        doNothing().when(saveToSink.unresolvedRecordService).delete(anyList());
        when(saveToSink.idMappingService.findBySyncariIds(eq(coreEntity.getApiName()), anySet()))
                .thenAnswer(invocation -> generateIdMappings(coreEntity, destEntity, invocation.getArgument(1)));
        when(saveToSink.syncDetailMetricService.updateSyncDetailMetric(any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        doNothing().when(saveToSink.eventStore).insertErrorLogs(anyList());
        when(saveToSink.pipelineUtil.getEntitySyncErrorMetrics(any(), any())).thenReturn(Stream.empty());
        long t1 = System.currentTimeMillis();
        Instance instance = new Instance();
        instance.setSyncariId("99999");
        SyncariContext.runWithContext(new Organization(), instance, SyncariContext.getUser(),()-> {
            final CurrentBatch execute = saveToSink.execute(destEntity,
                    new ViperContext(new Organization(), new Instance(), new User()), graphContext);
            long t2 = System.currentTimeMillis();
            System.out.println("Time Taken = " + (t2 - t1) + "ms");
        });
    }

    private List<IdMapping> generateIdMappings(EntityDefinition coreEntity, EntityDefinition externalEntity, Set<String> syncariIds) {
        return syncariIds.stream().map(id -> new IdMapping().setSyncariId(id).setEntityName(coreEntity.getApiName()).addMapping(
                externalEntity.getConnectorId(), UUID.randomUUID().toString(), externalEntity.getId()
        )).collect(Collectors.toList());
    }

    private static List<EntityData> generateRecords(EntityDefinition coreEntity, int numRecords) {
        List<EntityData> records = new ArrayList<>();
        for (int i = 0; i < numRecords; i++) {
            EntityData record = new EntityData(coreEntity.getApiName());
            record.setId(ObjectId.get().toHexString());
            record.setSyncariEntityId(record.getId());
            record.setLastModified(System.currentTimeMillis());
            for (AttributeDefinition attribute : coreEntity.getAttributes()) {
                record.addValue(attribute.getApiName(), "value_" + attribute.getApiName() + "_" + i);
            }
            records.add(record);
        }
        return records;
    }

    private static EntityDefinition createEntityDef(String destEntity, String destField) {
        final SchemaHelper destEntityBuilder = SchemaHelper.createEntityDefinition(destEntity, SchemaHelper.createConnector()).id();
        for (int i = 0; i < 150; i++) {
            destEntityBuilder.string(destField + i);
        }
        return destEntityBuilder.getEntityDefinition();
    }
}