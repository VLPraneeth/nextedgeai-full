package com.syncari.viper.streams.stages;

import com.syncari.AbstractSyncariTest;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.connector.data.*;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.service.def.DataService;
import com.syncari.core.actions.Actions;
import com.syncari.core.datatype.*;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.ConnectorStatus;
import com.syncari.core.model.misc.EntitySyncErrorMetric;
import com.syncari.core.model.misc.SyncError;
import com.syncari.core.model.misc.Watermark;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.pagination.PageDirection;
import com.syncari.core.model.pagination.PageInfo;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import com.syncari.core.pipeline.FilterFailedResult;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.PipelineEvaluator;
import com.syncari.core.pipeline.jtwig.JTwigPipelineEvaluator;
import com.syncari.core.repositories.customer.*;
import com.syncari.core.schema.Schema;
import com.syncari.core.service.*;
import com.syncari.core.sync.CurrentBatch;
import com.syncari.core.sync.RecordsBySyncariId;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.utils.Pair;
import com.syncari.viper.ViperContext;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.jooq.lambda.Seq;
import org.jtwig.environment.Environment;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.syncari.core.utils.GraphHelper.newGraph;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.anyIterable;
import static org.mockito.Mockito.anyList;

public class SaveToSinkTest extends AbstractSyncariTest {
    @MockBean
    IdMappingService idMappingRepo;
    @MockBean
    ConnectorService connectorService;
    @MockBean
    SchemaService schemaService;
    @MockBean
    EntityRepo entityRepo;
    @MockBean
    PipelineEvaluator evaluator;
    @SpyBean
    TransactionLogService transactionLogService;
    @MockBean
    EntityDatabaseRepo entityDatabaseRepo;

    @MockBean
    BiFunction<EntityDefinition, Document, EntityData> entityCreate;

    @MockBean
    TransactionLogRepo txLogRepo;
    @MockBean
    SinkLogRepo sinkLogRepo;
    @MockBean
    MappingGraphService graphService;
    @MockBean
    private AttributeDefinitionCache attributeDefinitionCache;
    @Autowired
    FunctionService functionService;
    @Autowired
    ActionDefinitionRepo actionDefinitionRepo;
    @Autowired
    PipelineNodeAuditService pipelineNodeAuditService;

    @Mock
    CurrentBatch currentBatch;
    @Mock
    Iterator<RecordsBySyncariId> recordsIter;

    @MockBean
    DataServiceFactory mockDataServiceFactory;
    @MockBean(name="zenDeskService")
    DataService zendeskService;

    @MockBean(name="salesforceService")
    DataService salesforceService;

    @MockBean(name="hubspotService")
    DataService hubspotService;

    @MockBean(name="defaultEmailService")
    EmailService emailService;

    @MockBean
    StagedBatchRecordRepo stagedBatchRecordRepo;

    @Autowired
    FeatureService featureService;

    @Autowired
    SaveToSink saveToSink;

    @Autowired
    TokenHelper tokenHelper;

    @Autowired
    Environment environment;

    @Autowired
    Actions actions;


/*
    @Autowired
    private final BiFunction<EntityDefinition, Document, EntityData> entityCreate;
*/

    private Connector syncariConnector;

    @Before
    public void init() {

        doNothing().when(eventService).log(any());
    }

    @Override
    public void setUp() {
        if(syncariConnector == null){
            syncariConnector = createConnector("syncari", "syncariConnId", "syncariConnMetaId");
        }
        when(connectorService.getSyncariConnector()).thenReturn(syncariConnector);
        when(schemaService.getSyncariSchema()).thenReturn(new Schema());
        super.setUp();
    }

    @Test
    public void applyRejectEmptyPolicy() {
        Connector connector = new Connector("my zendesk connector", "zendeskConnectorId",
                "https://someendpoint");
        connector.setId("my zendesk connector");
        connector.setMetadata(new ConnectorMetadata("zendeskConnectorId"));
        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());
        EntityDefinition sinkEntityDef = new EntityDefinition();
        sinkEntityDef.setConnectorId(connector.getId());
        sinkEntityDef.setApiName("Organization");
        sinkEntityDef.setDisplayName("Organization");
        sinkEntityDef.setStatus(Status.ACTIVE);
        sinkEntityDef.setId(ObjectId.get().toHexString());
        AttributeDefinition sinkNameAttr = createAttribute("Name", new StringType(), sinkEntityDef.getId());
        AttributeDefinition coreNameAttr = createAttribute("Name", new StringType(), coreEntityDef.getId());
        MappingGraph nameAttrGraph = createGraph(coreNameAttr.getId(), Scope.ATTRIBUTE);
        MappingNode mappingNode = sinkAttributeNode(sinkNameAttr, nameAttrGraph);
        AttributeSinkNodeConfig sinkNodeConfig = mappingNode.getTypedConfiguration();

        // NEVER config, create record
        EntityData entityData = new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("Name", null)
                .setNew(true);
        saveToSink.applyRejectEmptyPolicy(entityData, mappingNode);
        assertTrue(entityData.has("Name"));
        assertTrue(entityData.getValue("Name") == null);
        // NEVER config, update record
        entityData.setNew(false);
        saveToSink.applyRejectEmptyPolicy(entityData, mappingNode);
        assertTrue(entityData.has("Name"));
        assertTrue(entityData.getValue("Name") == null);


        sinkNodeConfig.setRejectEmpty(Constants.REJECT_EMPTY_ENUM.ALWAYS);
        entityData = new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("Name", null)
                .setNew(true);
        // ALWAYS config, create record
        saveToSink.applyRejectEmptyPolicy(entityData, mappingNode);
        assertFalse(entityData.has("Name"));
        // ALWAYS config, update record
        entityData.setNew(false);
        entityData.setId("123");
        saveToSink.applyRejectEmptyPolicy(entityData, mappingNode);
        assertFalse(entityData.has("Name"));


        sinkNodeConfig.setRejectEmpty(Constants.REJECT_EMPTY_ENUM.ON_CREATE);
        entityData = new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("Name", null)
                .setNew(true);
        // ON_CREATE config, create record
        saveToSink.applyRejectEmptyPolicy(entityData, mappingNode);
        assertFalse(entityData.has("Name"));
        // ON_CREATE config, update record
        entityData = new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("Name", null)
                .setNew(false);
        entityData.setId("123");
        saveToSink.applyRejectEmptyPolicy(entityData, mappingNode);
        assertTrue(entityData.has("Name"));
        assertTrue(entityData.getValue("Name") == null);


        sinkNodeConfig.setRejectEmpty(Constants.REJECT_EMPTY_ENUM.ON_UPDATE);
        entityData = new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("Name", null)
                .setNew(true);
        // ON_UPDATE config, create record
        saveToSink.applyRejectEmptyPolicy(entityData, mappingNode);
        assertTrue(entityData.has("Name"));
        assertTrue(entityData.getValue("Name") == null);
        // ON_UPDATE config, update record
        entityData = new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("Name", null)
                .setNew(false);
        entityData.setId("123");
        saveToSink.applyRejectEmptyPolicy(entityData, mappingNode);
        assertFalse(entityData.has("Name"));
    }

    @Test
    public void applyRejectEmptyPolicyEmptyString() {
        Connector connector = new Connector("my zendesk connector", "zendeskConnectorId",
                "https://someendpoint");
        connector.setId("my zendesk connector");
        connector.setMetadata(new ConnectorMetadata("zendeskConnectorId"));
        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());
        EntityDefinition sinkEntityDef = new EntityDefinition();
        sinkEntityDef.setConnectorId(connector.getId());
        sinkEntityDef.setApiName("Organization");
        sinkEntityDef.setDisplayName("Organization");
        sinkEntityDef.setStatus(Status.ACTIVE);
        sinkEntityDef.setId(ObjectId.get().toHexString());
        AttributeDefinition sinkNameAttr = createAttribute("Name", new StringType(), sinkEntityDef.getId());
        AttributeDefinition coreNameAttr = createAttribute("Name", new StringType(), coreEntityDef.getId());
        MappingGraph nameAttrGraph = createGraph(coreNameAttr.getId(), Scope.ATTRIBUTE);
        MappingNode mappingNode = sinkAttributeNode(sinkNameAttr, nameAttrGraph);
        AttributeSinkNodeConfig sinkNodeConfig = mappingNode.getTypedConfiguration();

        // NEVER config, create record
        EntityData entityData = new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("Name", "")
                .setNew(true);
        saveToSink.applyRejectEmptyPolicy(entityData, mappingNode);
        assertTrue(entityData.has("Name"));
        assertTrue("".equals(entityData.getValue("Name")));
        // NEVER config, update record
        entityData.setNew(false);
        saveToSink.applyRejectEmptyPolicy(entityData, mappingNode);
        assertTrue(entityData.has("Name"));
        assertTrue("".equals(entityData.getValue("Name")));


        sinkNodeConfig.setRejectEmpty(Constants.REJECT_EMPTY_ENUM.ALWAYS);
        entityData = new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("Name", "")
                .setNew(true);
        // ALWAYS config, create record
        saveToSink.applyRejectEmptyPolicy(entityData, mappingNode);
        assertFalse(entityData.has("Name"));
        // ALWAYS config, update record
        entityData.setNew(false);
        entityData.setId("123");
        saveToSink.applyRejectEmptyPolicy(entityData, mappingNode);
        assertFalse(entityData.has("Name"));


        sinkNodeConfig.setRejectEmpty(Constants.REJECT_EMPTY_ENUM.ON_CREATE);
        entityData = new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("Name", "")
                .setNew(true);
        // ON_CREATE config, create record
        saveToSink.applyRejectEmptyPolicy(entityData, mappingNode);
        assertFalse(entityData.has("Name"));
        // ON_CREATE config, update record
        entityData = new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("Name", "")
                .setNew(false);
        entityData.setId("123");
        saveToSink.applyRejectEmptyPolicy(entityData, mappingNode);
        assertTrue(entityData.has("Name"));
        assertTrue("".equals(entityData.getValue("Name")));


        sinkNodeConfig.setRejectEmpty(Constants.REJECT_EMPTY_ENUM.ON_UPDATE);
        entityData = new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("Name", "")
                .setNew(true);
        // ON_UPDATE config, create record
        saveToSink.applyRejectEmptyPolicy(entityData, mappingNode);
        assertTrue(entityData.has("Name"));
        assertTrue("".equals(entityData.getValue("Name")));
        // ON_UPDATE config, update record
        entityData = new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("Name", "")
                .setNew(false);
        entityData.setId("123");
        saveToSink.applyRejectEmptyPolicy(entityData, mappingNode);
        assertFalse(entityData.has("Name"));
    }

    public void newEntitiesAreCreatedInConnectedSystem() {

        ViperContext context = new ViperContext(new Organization(), new Instance(), new User());

        Connector connector = new Connector("my zendesk connector", "zendeskConnectorId",
                "https://someendpoint");
        connector.setId("my zendesk connector");
        connector.setMetadata(new ConnectorMetadata("zendeskConnectorId"));
        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());

        EntityDefinition sinkEntityDef = new EntityDefinition();
        sinkEntityDef.setConnectorId(connector.getId());
        sinkEntityDef.setApiName("Organization");
        sinkEntityDef.setDisplayName("Organization");
        sinkEntityDef.setStatus(Status.ACTIVE);
        sinkEntityDef.setId(ObjectId.get().toHexString());
        AttributeDefinition coreNameAttr = createAttribute("Name", new StringType(), coreEntityDef.getId());
        AttributeDefinition sinkNameAttr = createAttribute("Name", new StringType(), sinkEntityDef.getId());


        AttributeDefinition coreRevenueAttribute = createAttribute("Revenue", new DoubleType(), coreEntityDef.getId());
        AttributeDefinition coreQualityAttribute = createAttribute("Quality", new StringType(), coreEntityDef.getId());
        AttributeDefinition sinkRevenueAttribute = createAttribute("Revenue", new DoubleType(), sinkEntityDef.getId());
        AttributeDefinition sinkQualityAttribute = createAttribute("Sink Quality", new StringType(), sinkEntityDef.getId());

        MappingGraph entityGraph = createGraph(coreEntityDef.getId(),Scope.ENTITY);
        MappingNode coreNode = coreEntityNode(coreEntityDef, entityGraph);
        MappingNode sinkNode = coreSinkNode(sinkEntityDef,entityGraph);

        MappingNode setValueNode = new MappingNode().setScope(Scope.ENTITY).setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall()
                .setFunctionDefinition(functionService.findByNameAndScope("setValueOnEntity",Scope.ENTITY).get())
                .setParams(List.of(ParameterValue.string("output_"+coreNode.getId(),"input")))
                .setConfig(Map.of("attributeDefinitionId",coreQualityAttribute.getId(),"newValue","GOOD_RECORD"))
        )).setName("Set Value");
        setValueNode.setId(ObjectId.get().toHexString());
        entityGraph.getNodes().add(setValueNode);
        Edge coreToSetValueSink = edge(coreNode, setValueNode, entityGraph);
        coreToSetValueSink.setId(ObjectId.get().toHexString());
        Edge setValueToSink = edge(setValueNode, sinkNode, entityGraph);
        setValueToSink.setId(ObjectId.get().toHexString());

        MappingGraph nameAttrGraph = createGraph(coreNameAttr.getId(), Scope.ATTRIBUTE);
        MappingGraph revAttrGraph = createGraph(coreRevenueAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph qualiytAttrGraph = createGraph(coreQualityAttribute.getId(), Scope.ATTRIBUTE);

        MappingNode coreNameAttrNode = coreAttributeNode(coreNameAttr,nameAttrGraph);
        MappingNode sinkNameAttrNode = sinkAttributeNode(sinkNameAttr,nameAttrGraph);
        Edge coreAttrToSink = edge(coreNameAttrNode, sinkNameAttrNode, nameAttrGraph);

        MappingNode coreQAttrNode = coreAttributeNode(coreQualityAttribute,qualiytAttrGraph);
        MappingNode sinkQAttrNode = sinkAttributeNode(sinkQualityAttribute,qualiytAttrGraph);
        Edge coreQToSinkQ = edge(coreQAttrNode, sinkQAttrNode,qualiytAttrGraph);

        var sinkRevAttrNode = sinkAttributeNode(sinkRevenueAttribute, revAttrGraph);
        var coreRevAttrNode = coreAttributeNode(coreRevenueAttribute, revAttrGraph);
        Edge revenueCoreToSink = edge(coreRevAttrNode, sinkRevAttrNode, revAttrGraph);

        sinkEntityDef.addField(sinkNameAttr);
        sinkEntityDef.addField(sinkRevenueAttribute);
        sinkEntityDef.addField(sinkQualityAttribute);
        TransactionLog log = new TransactionLog()
                .setBatchId("currentBatchId")
                .setEntityName("account")
                .setEntityId(coreEntityDef.getId())
                .setNew(true)
                .setSyncariId("syncariAcctId123")
                .setOccurredAt(System.currentTimeMillis())
                .addSource("my zendesk connector", "my zendesk connector", "externalDefnitionId", "externalZDId",System.currentTimeMillis())
                .addChange(new FieldChange().setFieldId(coreNameAttr.getId()).setOldValue(null).setNewValue("Account Name").setApiName("Name"))
                .addChange(new FieldChange().setFieldId(coreRevenueAttribute.getId()).setOldValue(null).setNewValue(300.0).setApiName("Revenue"));
        log.setId(ObjectId.get().toHexString());

        when(txLogRepo.findAllStream()).thenReturn(Stream.empty());
        when(transactionLogService.findMergesByBatchId("currentBatchId", Date.from(Instant.EPOCH), new PageCursor("", PageDirection.previous,500)))
                .thenReturn(new com.syncari.core.model.pagination.Page<TransactionLog>(new PageInfo(), new ArrayList<>()));
        when(txLogRepo.findByBatchId("currentBatchId", Pageable.unpaged())).thenReturn(Page.empty());
        when(txLogRepo.findByBatchIdAndSyncariIdIn("currentBatchId", List.of("syncariAcctId123"))).thenReturn(List.of(log));
        when(txLogRepo.findById(log.getId())).thenReturn(Optional.of(log));
        when(connectorService.get("my zendesk connector")).thenReturn(connector);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        when(currentBatch.getEntityBatch(sinkEntityDef)).thenReturn(new StagedBatch(sinkEntityDef.getApiName()).setConnectorId("my zendesk connector"));
        when(currentBatch.recordsBySyncariIdIterator()).thenReturn(recordsIter);
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(recordsIter.hasNext()).thenReturn(true, true, false);
        when(recordsIter.next()).thenReturn(new RecordsBySyncariId("syncariAcctId123"));

        List<EntityData> t = List.of(
                new EntityData("account")
                        .setSyncariEntityId("syncariAcctId123")
                        .addValue("Name", "Account Name")
                        .addValue("Revenue", 300.0)
                        .setLastTransactionLogId(log.getId())
        );
        when(entityRepo.find(any(EntityDefinition.class), any(),any(PageCursor.class))).thenReturn(t);
        when(graphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(nameAttrGraph,revAttrGraph, qualiytAttrGraph));
        when(attributeDefinitionCache.findById(coreNameAttr.getId())).thenReturn(Optional.of(coreNameAttr));
        when(attributeDefinitionCache.findById(coreRevenueAttribute.getId())).thenReturn(Optional.of(coreRevenueAttribute));
        when(attributeDefinitionCache.findById(coreQualityAttribute.getId())).thenReturn(Optional.of(coreQualityAttribute));
        when(attributeDefinitionCache.findAllById(anyIterable())).thenReturn(List.of(coreNameAttr, coreRevenueAttribute, coreQualityAttribute));

        when(idMappingRepo.findBySyncariId("account", "syncariAcctId123")).thenReturn(Optional.empty());

        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+setValueNode.getId(), Pair.of(new FunctionResult(t.get(0).addValue(coreQualityAttribute.getApiName(),"BAD_RECORD"), ObjectType.VALUE),setValueNode));
            return null;
        }).when(evaluator).evaluate(eq(sinkNode), eq(entityGraph),any(GraphContext.class),any(), any());
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+coreNameAttrNode.getId(),new FunctionResult("Account Name",StringType.VALUE));
            return null;
        }).when(evaluator).evaluate(eq(sinkNameAttrNode), eq(nameAttrGraph),any(GraphContext.class),eq(n->n.getType()== MappingNodeType.CORE_ATTRIBUTE), any());
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+coreRevAttrNode.getId(),new FunctionResult(300.0,DoubleType.VALUE));
            return null;
        }).when(evaluator).evaluate(eq(sinkRevAttrNode), eq(revAttrGraph),any(GraphContext.class),eq(n->n.getType()== MappingNodeType.CORE_ATTRIBUTE), any());
        when(mockDataServiceFactory.getDataService(connector.getMetadata())).thenReturn(zendeskService);
        SyncResponse response = new SyncResponse();
        response.setResults(List.of(new Result(true, "1", "syncariAcctId123")));
        when(zendeskService.create(any())).thenReturn(response);
        when(schemaService.getEntity(sinkEntityDef.getId())).thenReturn(sinkEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        saveToSink.execute(sinkEntityDef, context,new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph));

        verify(connectorService).get("my zendesk connector");
        verify(schemaService).getEntity(sinkEntityDef.getId());
        verify(currentBatch).getSyncariEntityName();
        verify(transactionLogService).findMergesByBatchId("currentBatchId", Date.from(Instant.EPOCH), new PageCursor("", PageDirection.previous, 500));

        //verify(txLogRepo).findByBatchId("currentBatchId", Pageable.unpaged());
        ArgumentCaptor<Map<String, Object>> contextCapture = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<SyncRequest> requestCapture = ArgumentCaptor.forClass(SyncRequest.class);
        ArgumentCaptor<List<FieldChange>> fieldChangeCapture = ArgumentCaptor.forClass(List.class);

        verify(idMappingRepo).findBySyncariIds("account", Set.of("syncariAcctId123"));
        //verify(evaluator, times(3)).evaluate(any(MappingNode.class), any(MappingGraph.class), any(GraphContext.class),any(Predicate.class));
        verify(mockDataServiceFactory).getDataService(connector.getMetadata());

        verify(zendeskService).create(requestCapture.capture());
        verify(transactionLogService).setExternalOutgoingValue(any(), fieldChangeCapture.capture());

        assertEquals("Organization", requestCapture.getValue().getEntityName());
        assertNotNull(requestCapture.getValue().getData().get("my zendesk connector"));
        assertEquals(3, requestCapture.getValue().getData().get("my zendesk connector").get(0).getValues().size());
        assertEquals("Account Name", requestCapture.getValue().getData().get("my zendesk connector").get(0).getValue("Name"));
        assertEquals("BAD_RECORD", requestCapture.getValue().getData().get("my zendesk connector").get(0).getValue(sinkQualityAttribute.getApiName()));
        assertEquals(300.0, requestCapture.getValue().getData().get("my zendesk connector").get(0).getValue("Revenue"));

        // destination side transaction logs
        List<FieldChange> fieldChanges = fieldChangeCapture.getValue();
        var fieldChange= fieldChanges.stream().filter(f -> f.getFieldId().equalsIgnoreCase(coreNameAttr.getId())).findFirst().get();
        assertEquals("Account Name", fieldChange.getOutgoingExternalValues().get(sinkNameAttr.getId()).getValue());
        assertEquals(StringType.NAME, fieldChange.getOutgoingExternalValues().get(sinkNameAttr.getId()).getDataType());

        fieldChange= fieldChanges.stream().filter(f -> f.getFieldId().equalsIgnoreCase(coreQualityAttribute.getId())).findFirst().get();
        assertEquals("BAD_RECORD", fieldChange.getOutgoingExternalValues().get(sinkQualityAttribute.getId()).getValue());
        assertEquals(StringType.NAME, fieldChange.getOutgoingExternalValues().get(sinkQualityAttribute.getId()).getDataType());

        fieldChange= fieldChanges.stream().filter(f -> f.getFieldId().equalsIgnoreCase(coreRevenueAttribute.getId())).findFirst().get();
        assertEquals(300.0, fieldChange.getOutgoingExternalValues().get(sinkRevenueAttribute.getId()).getValue());
        assertEquals(DoubleType.NAME, fieldChange.getOutgoingExternalValues().get(sinkRevenueAttribute.getId()).getDataType());
    }

    @Test
    public void destinationUnpappedRecordCreatedInConnectedSystem() {
        destinationCreateTest(false);
    }

    @Test
    public void disconnectedRecordCreatedInConnectedSystem() {
        destinationCreateTest(true);
    }

    public void destinationCreateTest(boolean isDestinationDisconnected) {

        ViperContext context = new ViperContext(new Organization(), new Instance(), new User());

        Connector hubspotConn = new Connector("my hubspot connector", "hubspotConnectorId",
                "https://someendpoint");
        hubspotConn.setId(ObjectId.get().toHexString());
        hubspotConn.setStatus(ConnectorStatus.ACTIVE);
        final Connector salesforceConn = createConnector("my salesforce connector", "salesforceConnectorId");
        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());
        AttributeDefinition coreNameAttr = createAttribute("Name", new StringType(), coreEntityDef.getId());
        AttributeDefinition coreWebsiteAttr = createAttribute("website", new StringType(), coreEntityDef.getId());

        EntityDefinition sourceEntityDef1 = new EntityDefinition();
        sourceEntityDef1.setConnectorId(hubspotConn.getId());
        sourceEntityDef1.setApiName("Company");
        sourceEntityDef1.setDisplayName("company");
        sourceEntityDef1.setStatus(Status.ACTIVE);
        sourceEntityDef1.setId(ObjectId.get().toHexString());
        AttributeDefinition source1NameAttr = createAttribute("Name", new StringType(), sourceEntityDef1.getId());
        AttributeDefinition source1WebsiteAttr = createAttribute("website", new StringType(), sourceEntityDef1.getId());
        sourceEntityDef1.addField(source1NameAttr);
        sourceEntityDef1.addField(source1WebsiteAttr);

        EntityDefinition sourceEntityDef2 = new EntityDefinition();
        sourceEntityDef2.setConnectorId(salesforceConn.getId());
        sourceEntityDef2.setApiName("Account");
        sourceEntityDef2.setDisplayName("Account");
        sourceEntityDef2.setStatus(Status.ACTIVE);
        sourceEntityDef2.setId(ObjectId.get().toHexString());
        AttributeDefinition source2NameAttr = createAttribute("name", new StringType(), sourceEntityDef2.getId());
        AttributeDefinition source2WebsiteAttr = createAttribute("Website", new StringType(), sourceEntityDef2.getId());
        sourceEntityDef2.addField(source2NameAttr);
        sourceEntityDef2.addField(source2WebsiteAttr);

        var entityGraph = newGraph(coreEntityDef, functionService)
                .src(sourceEntityDef1)
                .src(sourceEntityDef2)
                .dest(sourceEntityDef1, "sink_" + sourceEntityDef1.getApiName())
                .dest(sourceEntityDef2, "sink_" + sourceEntityDef2.getApiName())
                .connect(sourceEntityDef1.getApiName(), coreEntityDef.getApiName())
                .connect(sourceEntityDef2.getApiName(), coreEntityDef.getApiName())
                .connect(coreEntityDef.getApiName(), "sink_" + sourceEntityDef1.getApiName())
                .connect(coreEntityDef.getApiName(), "sink_" + sourceEntityDef2.getApiName()).getGraph();

        MappingGraph nameAttrGraph = newGraph(coreNameAttr, functionService)
                .src(source1NameAttr)
                .src(source2NameAttr)
                .dest(source1NameAttr, "sink_" + source1NameAttr.getApiName())
                .dest(source2NameAttr, "sink_" + source2NameAttr.getApiName())
                .connect(source1NameAttr.getApiName(), coreNameAttr.getApiName())
                .connect(source2NameAttr.getApiName(), coreNameAttr.getApiName())
                .connect(coreNameAttr.getApiName(), "sink_" + source1NameAttr.getApiName())
                .connect(coreNameAttr.getApiName(), "sink_" + source2NameAttr.getApiName()).getGraph();

        MappingGraph websiteAttrGraph = newGraph(coreWebsiteAttr, functionService)
                .src(source1WebsiteAttr)
                .src(source2WebsiteAttr)
                .dest(source1WebsiteAttr, "sink_" + source1WebsiteAttr.getApiName())
                .dest(source2WebsiteAttr, "sink_" + source2WebsiteAttr.getApiName())
                .connect(source1WebsiteAttr.getApiName(), coreWebsiteAttr.getApiName())
                .connect(source2WebsiteAttr.getApiName(), coreWebsiteAttr.getApiName())
                .connect(coreWebsiteAttr.getApiName(), "sink_" + source1WebsiteAttr.getApiName())
                .connect(coreWebsiteAttr.getApiName(), "sink_" + source2WebsiteAttr.getApiName()).getGraph();

        // set disconneced flag = true
        var entitySinkConfig = entityGraph.getConnectedSinks().filter(s -> ((EntitySinkNodeConfig)s.getConfiguration()).getEntityDefinition().getId().equals(sourceEntityDef2.getId())).map(s -> ((EntitySinkNodeConfig)s.getConfiguration())).findFirst().get();

        if (isDestinationDisconnected) entitySinkConfig.setCreateDisconnectedMapping(true);

        TransactionLog log = new TransactionLog()
                .setBatchId("currentBatchId")
                .setEntityName("account")
                .setEntityId(coreEntityDef.getId())
                .setNew(true)
                .setSyncariId("syncariAcctId123")
                .setOperation(Operation.update)
                .setOccurredAt(System.currentTimeMillis())
                .addSource(hubspotConn.getId(), hubspotConn.getName(), sourceEntityDef1.getId(), "1234",System.currentTimeMillis())
                .addChange(new FieldChange().setFieldId(coreNameAttr.getId()).setOldValue(null).setNewValue("Account Name").setApiName("Name"))
                .addChange(new FieldChange().setFieldId(coreWebsiteAttr.getId()).setOldValue(null).setNewValue("http://www.google.com").setApiName("Website"));
        log.setId(ObjectId.get().toHexString());

        when(txLogRepo.findAllStream()).thenReturn(Stream.empty());
        when(transactionLogService.findMergesByBatchId("currentBatchId", Date.from(Instant.EPOCH), new PageCursor("", PageDirection.previous,500)))
                .thenReturn(new com.syncari.core.model.pagination.Page<TransactionLog>(new PageInfo(), new ArrayList<>()));
        when(txLogRepo.findByBatchId("currentBatchId", Pageable.unpaged())).thenReturn(Page.empty());
        when(txLogRepo.findByBatchIdAndSyncariIdIn("currentBatchId", List.of("syncariAcctId123"))).thenReturn(List.of(log));
        when(txLogRepo.findById(log.getId())).thenReturn(Optional.of(log));
        when(connectorService.get(hubspotConn.getId())).thenReturn(hubspotConn);
        when(connectorService.get(salesforceConn.getId())).thenReturn(salesforceConn);
        when(connectorService.refreshAuthentication(hubspotConn)).thenReturn(hubspotConn);
        when(connectorService.refreshAuthentication(salesforceConn)).thenReturn(salesforceConn);

        when(currentBatch.getEntityBatch(sourceEntityDef1)).thenReturn(new StagedBatch(sourceEntityDef1.getApiName()).setConnectorId(hubspotConn.getId()));
        when(currentBatch.recordsBySyncariIdIterator()).thenReturn(recordsIter);
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(recordsIter.hasNext()).thenReturn(true, true, false);
        when(recordsIter.next()).thenReturn(new RecordsBySyncariId("syncariAcctId123"));

        List<EntityData> t = List.of(
                new EntityData("account")
                        .setSyncariEntityId("syncariAcctId123")
                        .addValue("Name", "Account Name")
                        .addValue("Website", "http://www.google.com")
                        .setLastTransactionLogId(log.getId())
        );
        when(entityRepo.find(any(EntityDefinition.class), any(),any(PageCursor.class))).thenReturn(t);
        when(graphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(nameAttrGraph, websiteAttrGraph));
        when(attributeDefinitionCache.findById(coreNameAttr.getId())).thenReturn(Optional.of(coreNameAttr));
        when(attributeDefinitionCache.findById(coreNameAttr.getId())).thenReturn(Optional.of(coreNameAttr));
        when(attributeDefinitionCache.findById(coreWebsiteAttr.getId())).thenReturn(Optional.of(coreWebsiteAttr));
        when(attributeDefinitionCache.findAllById(anyIterable())).thenReturn(List.of(coreNameAttr, coreWebsiteAttr));

        // setup id mapping with salesforce as disconnected and hubspot as connected
        var mappings = isDestinationDisconnected ? List.of(
                new IdMapping.Mapping(hubspotConn.getId(), "1234", sourceEntityDef1.getId(), "syncariAcctId123", false),
                new IdMapping.Mapping(salesforceConn.getId(), "oldSalesforceId", sourceEntityDef2.getId(), "syncariAcctId123", true))
                : List.of(
                new IdMapping.Mapping(hubspotConn.getId(), "1234", sourceEntityDef1.getId(), "syncariAcctId123", false));



        when(idMappingRepo.findBySyncariId("account", "syncariAcctId123"))
                .thenReturn(Optional.of(new IdMapping().setSyncariId("syncariAcctId123").setEntityName(coreEntityDef.getApiName()).setMappings(mappings)));

        when(idMappingRepo.findBySyncariIds("account", Set.of("syncariAcctId123")))
                .thenReturn(List.of(new IdMapping().setSyncariId("syncariAcctId123").setEntityName(coreEntityDef.getApiName()).setMappings(mappings)));

        when(mockDataServiceFactory.getDataService(hubspotConn.getMetadata())).thenReturn(hubspotService);
        when(mockDataServiceFactory.getDataService(salesforceConn.getMetadata())).thenReturn(salesforceService);
        SyncResponse response = new SyncResponse();
        response.setResults(List.of(new Result(true, "newSalesforceId", "syncariAcctId123")));
        when(salesforceService.create(any())).thenReturn(response);
        when(schemaService.getEntity(sourceEntityDef1.getId())).thenReturn(sourceEntityDef1);
        when(schemaService.getEntity(sourceEntityDef2.getId())).thenReturn(sourceEntityDef2);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        // sourceEntityDef same as Sink here
        when(schemaService.refreshSynapseSchema(eq(sourceEntityDef2.getConnectorId()), eq(sourceEntityDef2), any())).thenReturn(List.of(sourceEntityDef2));
        final GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph);
        graphContext.setSyncariEntity(coreEntityDef);

        saveToSink.execute(sourceEntityDef2, context, graphContext);

        ArgumentCaptor<SyncResponse> syncResponse = ArgumentCaptor.forClass(SyncResponse.class);

        verify(idMappingRepo).saveIdMapping(any(), any(), syncResponse.capture(), any());
        assertEquals(true, syncResponse.getValue().isSuccess());
        assertEquals("newSalesforceId", syncResponse.getValue().getResults().get(0).getId());

    }

    @Test
    public void entitiesAreUpdatedInConnectedSystem() {

        ViperContext context = new ViperContext(new Organization(), new Instance(), new User());

        final Connector connector = createConnector("my zendesk connector", "zendeskConnectorId");
        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());

        EntityDefinition sinkEntityDef = new EntityDefinition();
        sinkEntityDef.setConnectorId(connector.getId());
        sinkEntityDef.setApiName("Organization");
        sinkEntityDef.setDisplayName("Organization");
        sinkEntityDef.setStatus(Status.ACTIVE);
        sinkEntityDef.setId(ObjectId.get().toHexString());
        AttributeDefinition coreNameAttr = createAttribute("Name", new StringType(), coreEntityDef.getId());
        AttributeDefinition sinkNameAttr = createAttribute("Name", new StringType(), sinkEntityDef.getId());


        AttributeDefinition coreRevenueAttribute = createAttribute("Revenue", new DoubleType(), coreEntityDef.getId());
        AttributeDefinition coreQualityAttribute = createAttribute("Quality", new StringType(), coreEntityDef.getId());
        // Make revenue field as non-updateable
        AttributeDefinition sinkRevenueAttribute = createAttribute("Revenue", new DoubleType(), sinkEntityDef.getId()).setUpdatable(false);
        AttributeDefinition sinkQualityAttribute = createAttribute("Sink Quality", new StringType(), sinkEntityDef.getId());

        AttributeDefinition sinkCreateOnlyAttribute = createAttribute("CreateOnly", new StringType(), sinkEntityDef.getId()).setCreateOnly(true);
        AttributeDefinition coreCreateOnlyAttribute = createAttribute("CreateOnly", new StringType(), coreEntityDef.getId());

        MappingGraph entityGraph = createGraph(coreEntityDef.getId(),Scope.ENTITY);
        MappingNode coreNode = coreEntityNode(coreEntityDef, entityGraph);
        MappingNode sinkNode = coreSinkNode(sinkEntityDef,entityGraph);

        MappingNode setValueNode = new MappingNode().setScope(Scope.ENTITY).setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall()
                .setFunctionDefinition(functionService.findByNameAndScope("setValueOnEntity",Scope.ENTITY).get())
                .setParams(List.of(ParameterValue.string("output_"+coreNode.getId(),"input")))
                .setConfig(Map.of("attributeDefinitionId",coreQualityAttribute.getId(),"newValue","GOOD_RECORD"))
        )).setName("Set Value");
        setValueNode.setId(ObjectId.get().toHexString());
        entityGraph.getNodes().add(setValueNode);
        Edge coreToSetValueSink = edge(coreNode, setValueNode, entityGraph);
        coreToSetValueSink.setId(ObjectId.get().toHexString());
        Edge setValueToSink = edge(setValueNode, sinkNode, entityGraph);
        setValueToSink.setId(ObjectId.get().toHexString());

        MappingGraph nameAttrGraph = createGraph(coreNameAttr.getId(), Scope.ATTRIBUTE);
        MappingGraph revAttrGraph = createGraph(coreRevenueAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph qualiytAttrGraph = createGraph(coreQualityAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph createOnlytAttrGraph = createGraph(coreCreateOnlyAttribute.getId(), Scope.ATTRIBUTE);

        MappingNode coreNameAttrNode = coreAttributeNode(coreNameAttr,nameAttrGraph);
        MappingNode sinkNameAttrNode = sinkAttributeNode(sinkNameAttr,nameAttrGraph);
        Edge coreAttrToSink = edge(coreNameAttrNode, sinkNameAttrNode, nameAttrGraph);

        MappingNode coreQAttrNode = coreAttributeNode(coreQualityAttribute,qualiytAttrGraph);
        MappingNode sinkQAttrNode = sinkAttributeNode(sinkQualityAttribute,qualiytAttrGraph);
        Edge coreQToSinkQ = edge(coreQAttrNode, sinkQAttrNode,qualiytAttrGraph);

        var sinkRevAttrNode = sinkAttributeNode(sinkRevenueAttribute, revAttrGraph);
        var coreRevAttrNode = coreAttributeNode(coreRevenueAttribute, revAttrGraph);
        Edge revenueCoreToSink = edge(coreRevAttrNode, sinkRevAttrNode, revAttrGraph);

        var sinkCreateOnlyAttrNode = sinkAttributeNode(sinkCreateOnlyAttribute, createOnlytAttrGraph);
        var coreCreateOnlyAttrNode = coreAttributeNode(coreCreateOnlyAttribute, createOnlytAttrGraph);
        Edge createOnlyCoreToSink = edge(coreCreateOnlyAttrNode, sinkCreateOnlyAttrNode, createOnlytAttrGraph);

        sinkEntityDef.addField(sinkNameAttr);
        sinkEntityDef.addField(sinkRevenueAttribute);
        sinkEntityDef.addField(sinkQualityAttribute);
        sinkEntityDef.addField(sinkCreateOnlyAttribute);
        TransactionLog log = new TransactionLog()
                .setBatchId("currentBatchId")
                .setEntityName("account")
                .setEntityId(coreEntityDef.getId())
                .setNew(false)
                .setSyncariId("syncariAcctId123")
                .setOccurredAt(System.currentTimeMillis())
                .addSource("my zendesk connector", "my zendesk connector", "externalDefnitionId", "externalZDId",System.currentTimeMillis())
                .addChange(new FieldChange().setFieldId(coreNameAttr.getId()).setOldValue("Old Name").setNewValue("Account Name").setApiName("Name"))
                .addChange(new FieldChange().setFieldId(coreRevenueAttribute.getId()).setOldValue(100.0).setNewValue(300.0).setApiName("Revenue"));
        log.setId(ObjectId.get().toHexString());
        when(txLogRepo.findAllStream()).thenReturn(Stream.empty());
        when(transactionLogService.findMergesByBatchId("currentBatchId", Date.from(Instant.EPOCH), new PageCursor("", PageDirection.previous,500)))
                .thenReturn(new com.syncari.core.model.pagination.Page<TransactionLog>(new PageInfo(), new ArrayList<>()));
        when(txLogRepo.findByBatchId("currentBatchId", Pageable.unpaged())).thenReturn(Page.empty());
        when(txLogRepo.findByBatchIdAndSyncariIdIn("currentBatchId", List.of("syncariAcctId123"))).thenReturn(List.of(log));
        when(txLogRepo.findById(log.getId())).thenReturn(Optional.of(log));
        when(connectorService.get("my zendesk connector")).thenReturn(connector);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        when(currentBatch.getEntityBatch(sinkEntityDef)).thenReturn(new StagedBatch(sinkEntityDef.getApiName()).setConnectorId("my zendesk connector"));
        when(currentBatch.recordsBySyncariIdIterator()).thenReturn(recordsIter);
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(recordsIter.hasNext()).thenReturn(true, true, false);
        when(recordsIter.next()).thenReturn(new RecordsBySyncariId("syncariAcctId123"));

        List<EntityData> t = List.of(new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("Name", "Account Name")
                .addValue("Revenue", 300.0)
                .addValue("CreateOnly", "This should be discarded")
                .setLastTransactionLogId(log.getId())
                .setNew(false));
        when(entityRepo.find(any(EntityDefinition.class), any(),any(PageCursor.class))).thenReturn(t);
        when(graphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(nameAttrGraph,revAttrGraph, qualiytAttrGraph, createOnlytAttrGraph));
        when(attributeDefinitionCache.findById(coreNameAttr.getId())).thenReturn(Optional.of(coreNameAttr));
        when(attributeDefinitionCache.findById(coreRevenueAttribute.getId())).thenReturn(Optional.of(coreRevenueAttribute));
        when(attributeDefinitionCache.findById(coreQualityAttribute.getId())).thenReturn(Optional.of(coreQualityAttribute));
        when(attributeDefinitionCache.findById(coreCreateOnlyAttribute.getId())).thenReturn(Optional.of(coreCreateOnlyAttribute));
        when(attributeDefinitionCache.findAllById(anyIterable())).thenReturn(List.of(coreNameAttr, coreRevenueAttribute, coreQualityAttribute, coreCreateOnlyAttribute));
        //id mapping present to make it an update operation

        when(idMappingRepo.findBySyncariIds("account", Set.of("syncariAcctId123"))).thenReturn(List.of(
                new IdMapping().setEntityName("account").setSyncariId("syncariAcctId123").setMappings(List.of(
                        IdMapping.mapping(connector.getId(), "syncariAcctId123", sinkEntityDef.getId(), "syncariAcctId123")))));

        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+setValueNode.getId(), Pair.of(new FunctionResult(t.get(0).addValue(coreQualityAttribute.getApiName(),"BAD_RECORD"), ObjectType.VALUE),setValueNode));
            return null;
        }).when(evaluator).evaluate(eq(sinkNode), eq(entityGraph),any(GraphContext.class),any(), any());
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+coreNameAttrNode.getId(),new FunctionResult("Account Name",StringType.VALUE));
            return null;
        }).when(evaluator).evaluate(eq(sinkNameAttrNode), eq(nameAttrGraph),any(GraphContext.class),eq(n->n.getType()== MappingNodeType.CORE_ATTRIBUTE), any());
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+coreRevAttrNode.getId(),new FunctionResult(300.0,DoubleType.VALUE));
            return null;
        }).when(evaluator).evaluate(eq(sinkRevAttrNode), eq(revAttrGraph),any(GraphContext.class),eq(n->n.getType()== MappingNodeType.CORE_ATTRIBUTE), any());
        when(mockDataServiceFactory.getDataService(connector.getMetadata())).thenReturn(zendeskService);
        SyncResponse response = new SyncResponse();
        response.setResults(List.of(new Result(true, "1", "syncariAcctId123")));
        when(zendeskService.update(any())).thenReturn(response);
        when(schemaService.getEntity(sinkEntityDef.getId())).thenReturn(sinkEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);

        when(schemaService.refreshSynapseSchema(eq(sinkEntityDef.getConnectorId()), eq(sinkEntityDef), any())).thenReturn(List.of(sinkEntityDef));
        final GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph);
        graphContext.setSyncariEntity(coreEntityDef);

        saveToSink.execute(sinkEntityDef, context, graphContext);

        verify(connectorService).get("my zendesk connector");
        verify(schemaService).getEntity(sinkEntityDef.getId());
        verify(currentBatch, atLeastOnce()).getSyncariEntityName();
        verify(transactionLogService).findMergesByBatchId(eq("currentBatchId"), any(), eq(new PageCursor("", PageDirection.previous, 500)));
        ArgumentCaptor<Map<String, Object>> contextCapture = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<SyncRequest> requestCapture = ArgumentCaptor.forClass(SyncRequest.class);
        ArgumentCaptor<List<TransactionLog>> transactionLogCapture = ArgumentCaptor.forClass(List.class);

        verify(idMappingRepo).findBySyncariIds("account", Set.of("syncariAcctId123"));
        //verify(evaluator, times(3)).evaluate(any(MappingNode.class), any(MappingGraph.class), any(GraphContext.class),any(Predicate.class));
        verify(mockDataServiceFactory).getDataService(connector.getMetadata());

        verify(zendeskService, never()).create(requestCapture.capture());
        verify(zendeskService).update(requestCapture.capture());
        verify(transactionLogService).log(transactionLogCapture.capture());

        assertEquals("Organization", requestCapture.getValue().getEntityName());
        assertNotNull(requestCapture.getValue().getData().get("my zendesk connector"));
        assertEquals(2, requestCapture.getValue().getData().get("my zendesk connector").get(0).getValues().size());
        assertEquals("Account Name", requestCapture.getValue().getData().get("my zendesk connector").get(0).getValue("Name"));
        assertEquals("BAD_RECORD", requestCapture.getValue().getData().get("my zendesk connector").get(0).getValue(sinkQualityAttribute.getApiName()));
        // Revenue field was filtered out and not send in SyncRequest as it was set non-updateable
        assertNull(requestCapture.getValue().getData().get("my zendesk connector").get(0).getValue("Revenue"));
        // createOnly field is filtered out
        assertNull(requestCapture.getValue().getData().get("my zendesk connector").get(0).getValue("CreateOnly"));

        // destination side transaction logs

        List<TransactionLog> transactionLogs = transactionLogCapture.getValue();
        assertTrue(transactionLogs.size() == 1);

        var fieldChange= transactionLogs.get(0).getChange((coreNameAttr.getId())).get();
        assertEquals("Account Name", fieldChange.getOutgoingExternalValues().get(sinkNameAttr.getId()).getValue());
        assertEquals("my zendesk connector", fieldChange.getOutgoingExternalValues().get(sinkNameAttr.getId()).getConnectorName());

        fieldChange= transactionLogs.get(0).getChange((coreQualityAttribute.getId())).get();
        assertEquals("BAD_RECORD", fieldChange.getOutgoingExternalValues().get(sinkQualityAttribute.getId()).getValue());
    }

    @Test
    public void entitiesUpdatedNoExternalRecord() {

        ViperContext context = new ViperContext(new Organization(), new Instance(), new User());

        Connector connector = createConnector("my zendesk connector", "my zendesk connector", "zendeskConnectorId");
        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());

        EntityDefinition sinkEntityDef = new EntityDefinition();
        sinkEntityDef.setConnectorId(connector.getId());
        sinkEntityDef.setApiName("Organization");
        sinkEntityDef.setDisplayName("Organization");
        sinkEntityDef.setStatus(Status.ACTIVE);
        sinkEntityDef.setId(ObjectId.get().toHexString());
        AttributeDefinition coreNameAttr = createAttribute("Name", new StringType(), coreEntityDef.getId());
        AttributeDefinition sinkNameAttr = createAttribute("Name", new StringType(), sinkEntityDef.getId());


        AttributeDefinition coreRevenueAttribute = createAttribute("Revenue", new DoubleType(), coreEntityDef.getId());
        AttributeDefinition coreQualityAttribute = createAttribute("Quality", new StringType(), coreEntityDef.getId());
        // Make revenue field as non-updateable
        AttributeDefinition sinkRevenueAttribute = createAttribute("Revenue", new DoubleType(), sinkEntityDef.getId()).setUpdatable(false);
        AttributeDefinition sinkQualityAttribute = createAttribute("Sink Quality", new StringType(), sinkEntityDef.getId());

        AttributeDefinition sinkCreateOnlyAttribute = createAttribute("CreateOnly", new StringType(), sinkEntityDef.getId()).setCreateOnly(true);
        AttributeDefinition coreCreateOnlyAttribute = createAttribute("CreateOnly", new StringType(), coreEntityDef.getId());

        MappingGraph entityGraph = createGraph(coreEntityDef.getId(),Scope.ENTITY);
        MappingNode coreNode = coreEntityNode(coreEntityDef, entityGraph);
        MappingNode sinkNode = coreSinkNode(sinkEntityDef,entityGraph);

        Edge coreToSink = edge(coreNode, sinkNode, entityGraph);
        coreToSink.setId(ObjectId.get().toHexString());

        MappingGraph nameAttrGraph = createGraph(coreNameAttr.getId(), Scope.ATTRIBUTE);
        MappingGraph revAttrGraph = createGraph(coreRevenueAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph qualiytAttrGraph = createGraph(coreQualityAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph createOnlytAttrGraph = createGraph(coreCreateOnlyAttribute.getId(), Scope.ATTRIBUTE);

        MappingNode coreNameAttrNode = coreAttributeNode(coreNameAttr,nameAttrGraph);
        MappingNode sinkNameAttrNode = sinkAttributeNode(sinkNameAttr,nameAttrGraph);
        Edge coreAttrToSink = edge(coreNameAttrNode, sinkNameAttrNode, nameAttrGraph);

        MappingNode coreQAttrNode = coreAttributeNode(coreQualityAttribute,qualiytAttrGraph);
        MappingNode sinkQAttrNode = sinkAttributeNode(sinkQualityAttribute,qualiytAttrGraph);
        Edge coreQToSinkQ = edge(coreQAttrNode, sinkQAttrNode,qualiytAttrGraph);

        var sinkRevAttrNode = sinkAttributeNode(sinkRevenueAttribute, revAttrGraph);
        var coreRevAttrNode = coreAttributeNode(coreRevenueAttribute, revAttrGraph);
        Edge revenueCoreToSink = edge(coreRevAttrNode, sinkRevAttrNode, revAttrGraph);

        var sinkCreateOnlyAttrNode = sinkAttributeNode(sinkCreateOnlyAttribute, createOnlytAttrGraph);
        var coreCreateOnlyAttrNode = coreAttributeNode(coreCreateOnlyAttribute, createOnlytAttrGraph);
        Edge createOnlyCoreToSink = edge(coreCreateOnlyAttrNode, sinkCreateOnlyAttrNode, createOnlytAttrGraph);

        sinkEntityDef.addField(sinkNameAttr);
        sinkEntityDef.addField(sinkRevenueAttribute);
        sinkEntityDef.addField(sinkQualityAttribute);
        sinkEntityDef.addField(sinkCreateOnlyAttribute);
        TransactionLog log = new TransactionLog()
                .setEntityName("account")
                .setEntityId(coreEntityDef.getId())
                .setNew(false)
                .setSyncariId("syncariAcctId123")
                .setOccurredAt(System.currentTimeMillis())
                .addSource("my zendesk connector", "my zendesk connector", "externalDefnitionId", "externalZDId",System.currentTimeMillis())
                .addChange(new FieldChange().setFieldId(coreNameAttr.getId()).setOldValue("Old Name").setNewValue("Account Name").setApiName("Name"))
                .addChange(new FieldChange().setFieldId(coreRevenueAttribute.getId()).setOldValue(100.0).setNewValue(300.0).setApiName("Revenue"));
        log.setId(ObjectId.get().toHexString());
        when(txLogRepo.findAllStream()).thenReturn(Stream.empty());
        when(transactionLogService.findMergesByBatchId("currentBatchId", Date.from(Instant.EPOCH), new PageCursor("", PageDirection.previous,500)))
                .thenReturn(new com.syncari.core.model.pagination.Page<TransactionLog>(new PageInfo(), new ArrayList<>()));
        when(txLogRepo.findByBatchId("currentBatchId", Pageable.unpaged())).thenReturn(Page.empty());
        when(txLogRepo.findByBatchIdAndSyncariIdIn("currentBatchId", List.of("syncariAcctId123"))).thenReturn(List.of());
        when(txLogRepo.findById(log.getId())).thenReturn(Optional.of(log));
        when(connectorService.get("my zendesk connector")).thenReturn(connector);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        when(currentBatch.getEntityBatch(sinkEntityDef)).thenReturn(new StagedBatch(sinkEntityDef.getApiName()).setConnectorId("my zendesk connector"));
        when(currentBatch.recordsBySyncariIdIterator()).thenReturn(recordsIter);
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(recordsIter.hasNext()).thenReturn(true, true, false);
        when(recordsIter.next()).thenReturn(new RecordsBySyncariId("syncariAcctId123"));

        List<EntityData> t = List.of(new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("Name", "Account Name")
                .addValue("Revenue", 300.0)
                .addValue("CreateOnly", "This should be discarded")
                .setLastTransactionLogId(log.getId())
                .setNew(false));
        when(entityRepo.find(any(EntityDefinition.class), any(),any(PageCursor.class))).thenReturn(t);
        when(graphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(nameAttrGraph,revAttrGraph, qualiytAttrGraph, createOnlytAttrGraph));
        when(attributeDefinitionCache.findById(coreNameAttr.getId())).thenReturn(Optional.of(coreNameAttr));
        when(attributeDefinitionCache.findById(coreRevenueAttribute.getId())).thenReturn(Optional.of(coreRevenueAttribute));
        when(attributeDefinitionCache.findById(coreQualityAttribute.getId())).thenReturn(Optional.of(coreQualityAttribute));
        when(attributeDefinitionCache.findById(coreCreateOnlyAttribute.getId())).thenReturn(Optional.of(coreCreateOnlyAttribute));
        when(attributeDefinitionCache.findAllById(anyIterable())).thenReturn(List.of(coreNameAttr, coreRevenueAttribute, coreQualityAttribute, coreCreateOnlyAttribute));
        //id mapping present to make it an update operation
        when(idMappingRepo.findBySyncariIds("account", Set.of("syncariAcctId123")))
                .thenReturn(List.of(new IdMapping().setEntityName("account").setSyncariId("syncariAcctId123").setMappings(List.of(
                        IdMapping.mapping(connector.getId(), "syncariAcctId123", sinkEntityDef.getId(), "syncariAcctId123")))));
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+coreNameAttrNode.getId(),new FunctionResult("Account Name",StringType.VALUE));
            return null;
        }).when(evaluator).evaluate(eq(sinkNameAttrNode), eq(nameAttrGraph),any(GraphContext.class),eq(n->n.getType()== MappingNodeType.CORE_ATTRIBUTE), any());
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+coreRevAttrNode.getId(),new FunctionResult(300.0,DoubleType.VALUE));
            return null;
        }).when(evaluator).evaluate(eq(sinkRevAttrNode), eq(revAttrGraph),any(GraphContext.class),eq(n->n.getType()== MappingNodeType.CORE_ATTRIBUTE), any());
        when(mockDataServiceFactory.getDataService(connector.getMetadata())).thenReturn(zendeskService);
        SyncResponse response = new SyncResponse();
        response.setResults(List.of(new Result(true, "1", "syncariAcctId123")));
        when(zendeskService.update(any())).thenReturn(response);
        when(schemaService.getEntity(sinkEntityDef.getId())).thenReturn(sinkEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(schemaService.refreshSynapseSchema(eq(sinkEntityDef.getConnectorId()), eq(sinkEntityDef), any())).thenReturn(List.of(sinkEntityDef));
        final GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph);
        graphContext.setSyncariEntity(coreEntityDef);

        saveToSink.execute(sinkEntityDef, context, graphContext);


        verify(connectorService).get("my zendesk connector");
        verify(schemaService).getEntity(sinkEntityDef.getId());
        verify(currentBatch, atLeastOnce()).getSyncariEntityName();

        verify(transactionLogService).findMergesByBatchId(eq("currentBatchId"), any(), eq(new PageCursor("", PageDirection.previous, 500)));
        //verify(txLogRepo).findByBatchId("currentBatchId", Pageable.unpaged());
        ArgumentCaptor<Map<String, Object>> contextCapture = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<SyncRequest> requestCapture = ArgumentCaptor.forClass(SyncRequest.class);
        ArgumentCaptor<List<TransactionLog>> transactionLogsCapture = ArgumentCaptor.forClass(List.class);

        verify(idMappingRepo).findBySyncariIds("account", Set.of("syncariAcctId123"));
        //verify(evaluator, times(3)).evaluate(any(MappingNode.class), any(MappingGraph.class), any(GraphContext.class),any(Predicate.class));
        verify(mockDataServiceFactory).getDataService(connector.getMetadata());

        verify(zendeskService, never()).create(requestCapture.capture());
        verify(zendeskService).update(requestCapture.capture());
        verify(transactionLogService).log(transactionLogsCapture.capture());

        assertEquals("Organization", requestCapture.getValue().getEntityName());
        assertNotNull(requestCapture.getValue().getData().get("my zendesk connector"));
        assertEquals(2, requestCapture.getValue().getData().get("my zendesk connector").get(0).getValues().size());
        assertEquals("Account Name", requestCapture.getValue().getData().get("my zendesk connector").get(0).getValue("Name"));
        // Revenue field was filtered out and not send in SyncRequest as it was set non-updateable
        assertNull(requestCapture.getValue().getData().get("my zendesk connector").get(0).getValue("Revenue"));
        // createOnly field is filtered out
        assertNull(requestCapture.getValue().getData().get("my zendesk connector").get(0).getValue("CreateOnly"));

        // destination side transaction logs
        var transactionLogs = transactionLogsCapture.getValue();
        assertTrue(transactionLogs.size() == 1);

        var fieldChange= transactionLogs.get(0).getChange(coreNameAttr.getId()).get();
        assertEquals("Account Name", fieldChange.getOutgoingExternalValues().get(sinkNameAttr.getId()).getValue());
        assertEquals("my zendesk connector", fieldChange.getOutgoingExternalValues().get(sinkNameAttr.getId()).getConnectorName());
    }

    @Test
    public void entitiesUpdatedNoExternalRecordSinkSideChange() {

        ViperContext context = new ViperContext(new Organization(), new Instance(), new User());

        final Connector connector = createConnector("my zendesk connector", "zendeskConnectorId");
        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());

        EntityDefinition sinkEntityDef = new EntityDefinition();
        sinkEntityDef.setConnectorId(connector.getId());
        sinkEntityDef.setApiName("Organization");
        sinkEntityDef.setDisplayName("Organization");
        sinkEntityDef.setStatus(Status.ACTIVE);
        sinkEntityDef.setId(ObjectId.get().toHexString());
        AttributeDefinition coreNameAttr = createAttribute("Name", new StringType(), coreEntityDef.getId());
        AttributeDefinition sinkNameAttr = createAttribute("Name", new StringType(), sinkEntityDef.getId());


        AttributeDefinition coreRevenueAttribute = createAttribute("Revenue", new DoubleType(), coreEntityDef.getId());
        AttributeDefinition coreQualityAttribute = createAttribute("Quality", new StringType(), coreEntityDef.getId());
        // Make revenue field as non-updateable
        AttributeDefinition sinkRevenueAttribute = createAttribute("Revenue", new DoubleType(), sinkEntityDef.getId()).setUpdatable(false);
        AttributeDefinition sinkQualityAttribute = createAttribute("Sink Quality", new StringType(), sinkEntityDef.getId());

        AttributeDefinition sinkCreateOnlyAttribute = createAttribute("CreateOnly", new StringType(), sinkEntityDef.getId()).setCreateOnly(true);
        AttributeDefinition coreCreateOnlyAttribute = createAttribute("CreateOnly", new StringType(), coreEntityDef.getId());

        MappingGraph entityGraph = createGraph(coreEntityDef.getId(),Scope.ENTITY);
        MappingNode coreNode = coreEntityNode(coreEntityDef, entityGraph);
        MappingNode sinkNode = coreSinkNode(sinkEntityDef,entityGraph);

        Edge coreToSink = edge(coreNode, sinkNode, entityGraph);
        coreToSink.setId(ObjectId.get().toHexString());

        MappingGraph nameAttrGraph = createGraph(coreNameAttr.getId(), Scope.ATTRIBUTE);
        MappingGraph revAttrGraph = createGraph(coreRevenueAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph qualiytAttrGraph = createGraph(coreQualityAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph createOnlytAttrGraph = createGraph(coreCreateOnlyAttribute.getId(), Scope.ATTRIBUTE);

        MappingNode coreNameAttrNode = coreAttributeNode(coreNameAttr,nameAttrGraph);
        MappingNode sinkNameAttrNode = sinkAttributeNode(sinkNameAttr,nameAttrGraph);
        Edge coreAttrToSink = edge(coreNameAttrNode, sinkNameAttrNode, nameAttrGraph);

        MappingNode coreQAttrNode = coreAttributeNode(coreQualityAttribute,qualiytAttrGraph);
        MappingNode sinkQAttrNode = sinkAttributeNode(sinkQualityAttribute,qualiytAttrGraph);
        Edge coreQToSinkQ = edge(coreQAttrNode, sinkQAttrNode,qualiytAttrGraph);

        var sinkRevAttrNode = sinkAttributeNode(sinkRevenueAttribute, revAttrGraph);
        var coreRevAttrNode = coreAttributeNode(coreRevenueAttribute, revAttrGraph);

        // set value on revenue attribute
        MappingNode setValueNode = new MappingNode().setScope(Scope.ATTRIBUTE).setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall()
                .setFunctionDefinition(functionService.findByNameAndScope("setValue",Scope.ATTRIBUTE).get())
                .setParams(List.of(ParameterValue.string("output_"+coreRevAttrNode.getId(),"input")))
                .setConfig(Map.of("attributeDefinitionId",sinkRevAttrNode.getId(),"newValue",350.0)
                ))).setName("Set Value");
        setValueNode.setId(ObjectId.get().toHexString());

        Edge revenueCoreToSetValue = edge(coreRevAttrNode, setValueNode, revAttrGraph);
        Edge setValueToSink = edge(setValueNode, sinkRevAttrNode, revAttrGraph);

        var sinkCreateOnlyAttrNode = sinkAttributeNode(sinkCreateOnlyAttribute, createOnlytAttrGraph);
        var coreCreateOnlyAttrNode = coreAttributeNode(coreCreateOnlyAttribute, createOnlytAttrGraph);
        Edge createOnlyCoreToSink = edge(coreCreateOnlyAttrNode, sinkCreateOnlyAttrNode, createOnlytAttrGraph);

        sinkEntityDef.addField(sinkNameAttr);
        sinkEntityDef.addField(sinkRevenueAttribute);
        sinkEntityDef.addField(sinkQualityAttribute);
        sinkEntityDef.addField(sinkCreateOnlyAttribute);
        TransactionLog log = new TransactionLog()
                .setEntityName("account")
                .setEntityId(coreEntityDef.getId())
                .setNew(false)
                .setBatchId(UUID.randomUUID().toString())
                .setSyncariId("syncariAcctId123")
                .setOccurredAt(System.currentTimeMillis())
                .addSource("my zendesk connector", "my zendesk connector", "externalDefnitionId", "externalZDId",System.currentTimeMillis())
                .addChange(new FieldChange().setFieldId(coreNameAttr.getId()).setOldValue("Old Name").setNewValue("Account Name").setApiName("Name"))
                .addChange(new FieldChange().setFieldId(coreRevenueAttribute.getId()).setOldValue(100.0).setNewValue(300.0).setApiName("Revenue"));
        log.setId(ObjectId.get().toHexString());

        when(transactionLogService.findMergesByBatchId("currentBatchId", Date.from(Instant.EPOCH), new PageCursor("", PageDirection.previous,500)))
                .thenReturn(new com.syncari.core.model.pagination.Page<TransactionLog>(new PageInfo(), new ArrayList<>()));
        when(txLogRepo.findByBatchId("currentBatchId", Pageable.unpaged())).thenReturn(Page.empty());
        when(txLogRepo.findByBatchIdAndSyncariIdIn("currentBatchId", List.of("syncariAcctId123"))).thenReturn(List.of());
        when(txLogRepo.findById(log.getId())).thenReturn(Optional.of(log));
        when(transactionLogService.findByTransactionLogId(log.getId(), Instant.EPOCH.toEpochMilli())).thenReturn(Optional.of(log));
        when(connectorService.get("my zendesk connector")).thenReturn(connector);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        when(currentBatch.getEntityBatch(sinkEntityDef)).thenReturn(new StagedBatch(sinkEntityDef.getApiName()).setConnectorId("my zendesk connector"));
        when(currentBatch.recordsBySyncariIdIterator()).thenReturn(recordsIter);
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(recordsIter.hasNext()).thenReturn(true, true, false);
        when(recordsIter.next()).thenReturn(new RecordsBySyncariId("syncariAcctId123"));

        List<EntityData> t = List.of(new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("Name", "Account Name")
                .addValue("Revenue", 300.0)
                .addValue("CreateOnly", "This should be discarded")
                .addValue("Quality", null)
                .setNew(false).setLastTransactionLogId(log.getId()));
        when(entityRepo.find(any(EntityDefinition.class), any(),any(PageCursor.class))).thenReturn(t);
        when(graphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(nameAttrGraph,revAttrGraph, qualiytAttrGraph, createOnlytAttrGraph));
        when(attributeDefinitionCache.findById(coreNameAttr.getId())).thenReturn(Optional.of(coreNameAttr));
        when(attributeDefinitionCache.findById(coreRevenueAttribute.getId())).thenReturn(Optional.of(coreRevenueAttribute));
        when(attributeDefinitionCache.findById(coreQualityAttribute.getId())).thenReturn(Optional.of(coreQualityAttribute));
        when(attributeDefinitionCache.findById(coreCreateOnlyAttribute.getId())).thenReturn(Optional.of(coreCreateOnlyAttribute));
        when(attributeDefinitionCache.findAllById(anyIterable())).thenReturn(List.of(coreNameAttr, coreRevenueAttribute, coreQualityAttribute, coreCreateOnlyAttribute));
        //id mapping present to make it an update operation
        when(idMappingRepo.findBySyncariIds("account", Set.of("syncariAcctId123")))
                .thenReturn(List.of(new IdMapping().setEntityName("account").setSyncariId("syncariAcctId123").setMappings(List.of(
                        IdMapping.mapping(connector.getId(), "syncariAcctId123", sinkEntityDef.getId(), "syncariAcctId123")))));
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+setValueNode.getId(), Pair.of(new FunctionResult(350.0,DoubleType.VALUE),setValueNode));
            ctx.put("output_"+coreRevAttrNode.getId(),new FunctionResult(300.0,DoubleType.VALUE));
            return null;
        }).when(evaluator).evaluate(eq(sinkRevAttrNode), eq(revAttrGraph),any(GraphContext.class),any(), any());
        when(mockDataServiceFactory.getDataService(connector.getMetadata())).thenReturn(zendeskService);
        SyncResponse response = new SyncResponse();
        response.setResults(List.of(new Result(true, "1", "syncariAcctId123")));
        when(zendeskService.update(any())).thenReturn(response);
        when(schemaService.getEntity(sinkEntityDef.getId())).thenReturn(sinkEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(schemaService.refreshSynapseSchema(eq(sinkEntityDef.getConnectorId()), eq(sinkEntityDef), any())).thenReturn(List.of(sinkEntityDef));
        final GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph);
        graphContext.setSyncariEntity(coreEntityDef);

        saveToSink.execute(sinkEntityDef, context, graphContext);

        verify(connectorService).get("my zendesk connector");
        verify(schemaService).getEntity(sinkEntityDef.getId());
        verify(currentBatch, atLeastOnce()).getSyncariEntityName();
        ArgumentCaptor<Map<String, Object>> contextCapture = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<SyncRequest> requestCapture = ArgumentCaptor.forClass(SyncRequest.class);
        ArgumentCaptor<List<TransactionLog>> transactionLogsCapture = ArgumentCaptor.forClass(List.class);

        verify(idMappingRepo).findBySyncariIds("account", Set.of("syncariAcctId123"));
        //verify(evaluator, times(3)).evaluate(any(MappingNode.class), any(MappingGraph.class), any(GraphContext.class),any(Predicate.class));
        verify(mockDataServiceFactory).getDataService(connector.getMetadata());

        verify(zendeskService, never()).create(requestCapture.capture());
        verify(zendeskService).update(requestCapture.capture());
        verify(transactionLogService).log(transactionLogsCapture.capture());

        assertEquals("Organization", requestCapture.getValue().getEntityName());
        assertNotNull(requestCapture.getValue().getData().get("my zendesk connector"));
        assertEquals(2, requestCapture.getValue().getData().get("my zendesk connector").get(0).getValues().size());
        assertEquals("Account Name", requestCapture.getValue().getData().get("my zendesk connector").get(0).getValue("Name"));
        // Revenue field was filtered out and not send in SyncRequest as it was set non-updateable
        assertNull(requestCapture.getValue().getData().get("my zendesk connector").get(0).getValue("Revenue"));
        // createOnly field is filtered out
        assertNull(requestCapture.getValue().getData().get("my zendesk connector").get(0).getValue("CreateOnly"));

        var transactionLogs = transactionLogsCapture.getValue();
        assertTrue(transactionLogs.size() == 1);
        // destination side transaction logs
        var fieldChange= transactionLogs.get(0).getChange((coreNameAttr.getId())).get();
        assertEquals("Account Name", fieldChange.getOutgoingExternalValues().get(sinkNameAttr.getId()).getValue());
        assertEquals("my zendesk connector", fieldChange.getOutgoingExternalValues().get(sinkNameAttr.getId()).getConnectorName());
    }

    @Test
    public void entitiesUpdatedDeletedExternalRecord() {

        ViperContext context = new ViperContext(new Organization(), new Instance(), new User());

        Connector connector = createConnector("my zendesk connector", "zendeskConnectorId");
        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());

        EntityDefinition sinkEntityDef = new EntityDefinition();
        sinkEntityDef.setConnectorId(connector.getId());
        sinkEntityDef.setApiName("Organization");
        sinkEntityDef.setDisplayName("Organization");
        sinkEntityDef.setStatus(Status.ACTIVE);
        sinkEntityDef.setId(ObjectId.get().toHexString());
        AttributeDefinition coreNameAttr = createAttribute("Name", new StringType(), coreEntityDef.getId());
        AttributeDefinition sinkNameAttr = createAttribute("Name", new StringType(), sinkEntityDef.getId());
        coreEntityDef.addField(new AttributeDefinition().setApiName("syncari_id_1").setDataType(ExternalIdType.VALUE).setReferenceTo(sinkEntityDef.getId()));


        AttributeDefinition coreRevenueAttribute = createAttribute("Revenue", new DoubleType(), coreEntityDef.getId());
        AttributeDefinition coreQualityAttribute = createAttribute("Quality", new StringType(), coreEntityDef.getId());
        // Make revenue field as non-updateable
        AttributeDefinition sinkRevenueAttribute = createAttribute("Revenue", new DoubleType(), sinkEntityDef.getId()).setUpdatable(false);
        AttributeDefinition sinkQualityAttribute = createAttribute("Sink Quality", new StringType(), sinkEntityDef.getId());

        AttributeDefinition sinkCreateOnlyAttribute = createAttribute("CreateOnly", new StringType(), sinkEntityDef.getId()).setCreateOnly(true);
        AttributeDefinition coreCreateOnlyAttribute = createAttribute("CreateOnly", new StringType(), coreEntityDef.getId());

        MappingGraph entityGraph = createGraph(coreEntityDef.getId(),Scope.ENTITY);
        MappingNode coreNode = coreEntityNode(coreEntityDef, entityGraph);
        MappingNode sinkNode = coreSinkNode(sinkEntityDef,entityGraph);

        Edge coreToSink = edge(coreNode, sinkNode, entityGraph);
        coreToSink.setId(ObjectId.get().toHexString());

        MappingGraph nameAttrGraph = createGraph(coreNameAttr.getId(), Scope.ATTRIBUTE);
        MappingGraph revAttrGraph = createGraph(coreRevenueAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph qualiytAttrGraph = createGraph(coreQualityAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph createOnlytAttrGraph = createGraph(coreCreateOnlyAttribute.getId(), Scope.ATTRIBUTE);

        MappingNode coreNameAttrNode = coreAttributeNode(coreNameAttr,nameAttrGraph);
        MappingNode sinkNameAttrNode = sinkAttributeNode(sinkNameAttr,nameAttrGraph);
        Edge coreAttrToSink = edge(coreNameAttrNode, sinkNameAttrNode, nameAttrGraph);

        MappingNode coreQAttrNode = coreAttributeNode(coreQualityAttribute,qualiytAttrGraph);
        MappingNode sinkQAttrNode = sinkAttributeNode(sinkQualityAttribute,qualiytAttrGraph);
        Edge coreQToSinkQ = edge(coreQAttrNode, sinkQAttrNode,qualiytAttrGraph);

        var sinkRevAttrNode = sinkAttributeNode(sinkRevenueAttribute, revAttrGraph);
        var coreRevAttrNode = coreAttributeNode(coreRevenueAttribute, revAttrGraph);

        // set value on revenue attribute
        MappingNode setValueNode = new MappingNode().setScope(Scope.ATTRIBUTE).setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall()
                .setFunctionDefinition(functionService.findByNameAndScope("setValue",Scope.ATTRIBUTE).get())
                .setParams(List.of(ParameterValue.string("output_"+coreRevAttrNode.getId(),"input")))
                .setConfig(Map.of("attributeDefinitionId",sinkRevAttrNode.getId(),"newValue",350.0)
                ))).setName("Set Value");
        setValueNode.setId(ObjectId.get().toHexString());

        Edge revenueCoreToSetValue = edge(coreRevAttrNode, setValueNode, revAttrGraph);
        Edge setValueToSink = edge(setValueNode, sinkRevAttrNode, revAttrGraph);

        var sinkCreateOnlyAttrNode = sinkAttributeNode(sinkCreateOnlyAttribute, createOnlytAttrGraph);
        var coreCreateOnlyAttrNode = coreAttributeNode(coreCreateOnlyAttribute, createOnlytAttrGraph);
        Edge createOnlyCoreToSink = edge(coreCreateOnlyAttrNode, sinkCreateOnlyAttrNode, createOnlytAttrGraph);

        sinkEntityDef.addField(sinkNameAttr);
        sinkEntityDef.addField(sinkRevenueAttribute);
        sinkEntityDef.addField(sinkQualityAttribute);
        sinkEntityDef.addField(sinkCreateOnlyAttribute);
        TransactionLog log = new TransactionLog()
                .setEntityName("account")
                .setEntityId(coreEntityDef.getId())
                .setNew(false)
                .setBatchId(UUID.randomUUID().toString())
                .setSyncariId("syncariAcctId123")
                .setOccurredAt(System.currentTimeMillis())
                .addSource("my zendesk connector", "my zendesk connector", "externalDefnitionId", "externalZDId",System.currentTimeMillis())
                .addChange(new FieldChange().setFieldId(coreNameAttr.getId()).setOldValue("Old Name").setNewValue("Account Name").setApiName("Name"))
                .addChange(new FieldChange().setFieldId(coreRevenueAttribute.getId()).setOldValue(100.0).setNewValue(300.0).setApiName("Revenue"));
        log.setId(ObjectId.get().toHexString());

        when(transactionLogService.findMergesByBatchId("currentBatchId", Date.from(Instant.EPOCH), new PageCursor("", PageDirection.previous,500)))
                .thenReturn(new com.syncari.core.model.pagination.Page<TransactionLog>(new PageInfo(), new ArrayList<>()));
        when(txLogRepo.findByBatchId("currentBatchId", Pageable.unpaged())).thenReturn(Page.empty());
        when(txLogRepo.findByBatchIdAndSyncariIdIn("currentBatchId", List.of("syncariAcctId123"))).thenReturn(List.of());
        when(txLogRepo.findById(log.getId())).thenReturn(Optional.of(log));
        when(transactionLogService.findByTransactionLogId(log.getId(), Instant.EPOCH.toEpochMilli())).thenReturn(Optional.of(log));
        when(connectorService.get("my zendesk connector")).thenReturn(connector);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        when(currentBatch.getEntityBatch(sinkEntityDef)).thenReturn(new StagedBatch(sinkEntityDef.getApiName()).setConnectorId("my zendesk connector"));
        when(currentBatch.recordsBySyncariIdIterator()).thenReturn(recordsIter);
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(recordsIter.hasNext()).thenReturn(true, true, false);
        when(recordsIter.next()).thenReturn(new RecordsBySyncariId("syncariAcctId123"));

        List<EntityData> t = List.of(new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("Name", "Account Name")
                .addValue("Revenue", 300.0)
                .addValue("CreateOnly", "This should be discarded")
                .addValue("Quality", null)
                .setNew(false).setLastTransactionLogId(log.getId()));
        when(entityRepo.find(any(EntityDefinition.class), any(),any(PageCursor.class))).thenReturn(t);
        when(graphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(nameAttrGraph,revAttrGraph, qualiytAttrGraph, createOnlytAttrGraph));
        when(attributeDefinitionCache.findById(coreNameAttr.getId())).thenReturn(Optional.of(coreNameAttr));
        when(attributeDefinitionCache.findById(coreRevenueAttribute.getId())).thenReturn(Optional.of(coreRevenueAttribute));
        when(attributeDefinitionCache.findById(coreQualityAttribute.getId())).thenReturn(Optional.of(coreQualityAttribute));
        when(attributeDefinitionCache.findById(coreCreateOnlyAttribute.getId())).thenReturn(Optional.of(coreCreateOnlyAttribute));
        when(attributeDefinitionCache.findAllById(anyIterable())).thenReturn(List.of(coreNameAttr, coreRevenueAttribute, coreQualityAttribute, coreCreateOnlyAttribute));
        //id mapping present to make it an update operation
        when(idMappingRepo.findBySyncariIds("account", Set.of("syncariAcctId123")))
                .thenReturn(List.of(new IdMapping().setEntityName("account").setSyncariId("syncariAcctId123").setMappings(List.of(
                        IdMapping.mapping(connector.getId(), "1", sinkEntityDef.getId(), "syncariAcctId123")))));

        when(idMappingRepo.saveAll(anyList())).thenReturn(List.of());
        doNothing().when(entityRepo).updateValues(any(EntityDefinition.class), anyList());

        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+setValueNode.getId(), Pair.of(new FunctionResult(350.0,DoubleType.VALUE),setValueNode));
            ctx.put("output_"+coreRevAttrNode.getId(),new FunctionResult(300.0,DoubleType.VALUE));
            return null;
        }).when(evaluator).evaluate(eq(sinkRevAttrNode), eq(revAttrGraph),any(GraphContext.class),any(), any());
        when(mockDataServiceFactory.getDataService(connector.getMetadata())).thenReturn(zendeskService);
        SyncResponse response = new SyncResponse();

        Result result = new Result(false, "1", "syncariAcctId123");
        result.setErrorCode(ErrorCodes.DATA_NOT_FOUND.name());
        response.setResults(List.of(result));
        when(zendeskService.update(any())).thenReturn(response);
        when(schemaService.getEntity(sinkEntityDef.getId())).thenReturn(sinkEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(schemaService.refreshSynapseSchema(eq(sinkEntityDef.getConnectorId()), eq(sinkEntityDef), any())).thenReturn(List.of(sinkEntityDef));
        final GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph);
        graphContext.setSyncariEntity(coreEntityDef);

        saveToSink.execute(sinkEntityDef, context, graphContext);

        verify(connectorService).get("my zendesk connector");
        verify(schemaService).getEntity(sinkEntityDef.getId());
        verify(currentBatch, atLeastOnce()).getSyncariEntityName();
        ArgumentCaptor<Map<String, Object>> contextCapture = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<SyncRequest> requestCapture = ArgumentCaptor.forClass(SyncRequest.class);
        ArgumentCaptor<List<IdMapping>> idMappings = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<EntityData>> entities = ArgumentCaptor.forClass(List.class);

        verify(idMappingRepo, times(2)).findBySyncariIds("account", Set.of("syncariAcctId123"));
        //verify(evaluator, times(3)).evaluate(any(MappingNode.class), any(MappingGraph.class), any(GraphContext.class),any(Predicate.class));
        verify(mockDataServiceFactory).getDataService(connector.getMetadata());

        verify(zendeskService, never()).create(requestCapture.capture());
        verify(zendeskService).update(requestCapture.capture());
        verify(idMappingRepo).saveAll(idMappings.capture());
        verify(entityRepo).updateValues(eq(coreEntityDef), entities.capture());
        List<IdMapping> mappings = idMappings.getValue();
        assertTrue(mappings.size() == 1);
        assertTrue(mappings.get(0).getAllMappings(connector.getId(), sinkEntityDef.getId()).get(0).isDisconnected());
        assertTrue(entities.getValue().size() == 1);
        assertTrue(entities.getValue().get(0).getValues().containsKey("syncari_id_1"));
        assertNull(entities.getValue().get(0).getValue("syncari_id_1"));

    }

    @Test
    public void deletedFieldsIgnored() {

        ViperContext context = new ViperContext(new Organization(), new Instance(), new User());

        Connector connector = createConnector("my zendesk connector", "zendeskConnectorId");
        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());

        EntityDefinition sinkEntityDef = new EntityDefinition();
        sinkEntityDef.setConnectorId(connector.getId());
        sinkEntityDef.setApiName("Organization");
        sinkEntityDef.setDisplayName("Organization");
        sinkEntityDef.setStatus(Status.ACTIVE);
        sinkEntityDef.setId(ObjectId.get().toHexString());
        AttributeDefinition coreNameAttr = createAttribute("Name", new StringType(), coreEntityDef.getId());
        AttributeDefinition sinkNameAttr = createAttribute("Name", new StringType(), sinkEntityDef.getId());


        AttributeDefinition coreRevenueAttribute = createAttribute("Revenue", new DoubleType(), coreEntityDef.getId());
        AttributeDefinition coreQualityAttribute = createAttribute("Quality", new StringType(), coreEntityDef.getId());
        AttributeDefinition coreOwnerAttribute = createAttribute("Owner", new ReferenceType(), coreEntityDef.getId());
        AttributeDefinition sinkRevenueAttribute = createAttribute("Revenue", new DoubleType(), sinkEntityDef.getId());
        AttributeDefinition sinkQualityAttribute = createAttribute("Sink Quality", new StringType(), sinkEntityDef.getId());
        AttributeDefinition sinkOwnerAttribute = createReferenceAttribute("Sink Owner",  sinkEntityDef.getId());

        MappingGraph entityGraph = createGraph(coreEntityDef.getId(),Scope.ENTITY);
        MappingNode coreNode = coreEntityNode(coreEntityDef, entityGraph);
        MappingNode sinkNode = coreSinkNode(sinkEntityDef,entityGraph);

        MappingNode setValueNode = new MappingNode().setScope(Scope.ENTITY).setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall()
                .setFunctionDefinition(functionService.findByNameAndScope("setValueOnEntity",Scope.ENTITY).get())
                .setParams(List.of(ParameterValue.string("output_"+coreNode.getId(),"input")))
                .setConfig(Map.of("attributeDefinitionId",coreQualityAttribute.getId(),"newValue","GOOD_RECORD"))
        )).setName("Set Value");
        setValueNode.setId(ObjectId.get().toHexString());
        entityGraph.getNodes().add(setValueNode);
        Edge coreToSetValueSink = edge(coreNode, setValueNode, entityGraph);
        coreToSetValueSink.setId(ObjectId.get().toHexString());
        Edge setValueToSink = edge(setValueNode, sinkNode, entityGraph);
        setValueToSink.setId(ObjectId.get().toHexString());

        MappingGraph nameAttrGraph = createGraph(coreNameAttr.getId(), Scope.ATTRIBUTE);
        MappingGraph revAttrGraph = createGraph(coreRevenueAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph qualiytAttrGraph = createGraph(coreQualityAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph ownerAttrGraph = createGraph(coreOwnerAttribute.getId(), Scope.ATTRIBUTE);

        MappingNode coreNameAttrNode = coreAttributeNode(coreNameAttr,nameAttrGraph);
        MappingNode sinkNameAttrNode = sinkAttributeNode(sinkNameAttr,nameAttrGraph);
        Edge coreAttrToSink = edge(coreNameAttrNode, sinkNameAttrNode, nameAttrGraph);

        MappingNode coreQAttrNode = coreAttributeNode(coreQualityAttribute,qualiytAttrGraph);
        MappingNode coreOwnerAttrNode = coreAttributeNode(coreOwnerAttribute,ownerAttrGraph);
        MappingNode sinkQAttrNode = sinkAttributeNode(sinkQualityAttribute,qualiytAttrGraph);
        MappingNode sinkOwnerAttrNode = sinkAttributeNode(sinkOwnerAttribute,ownerAttrGraph);
        Edge coreQToSinkQ = edge(coreQAttrNode, sinkQAttrNode,qualiytAttrGraph);
        Edge coreOToSinkO = edge(coreOwnerAttrNode, sinkOwnerAttrNode,ownerAttrGraph);

        var sinkRevAttrNode = sinkAttributeNode(sinkRevenueAttribute, revAttrGraph);
        var coreRevAttrNode = coreAttributeNode(coreRevenueAttribute, revAttrGraph);
        Edge revenueCoreToSink = edge(coreRevAttrNode, sinkRevAttrNode, revAttrGraph);

        sinkEntityDef.addField(sinkNameAttr);
        sinkEntityDef.addField(sinkRevenueAttribute);
        sinkEntityDef.addField(sinkQualityAttribute);
        sinkEntityDef.addField(sinkOwnerAttribute);
        TransactionLog log = new TransactionLog()
                .setBatchId("currentBatchId")
                .setEntityName("account")
                .setEntityId(coreEntityDef.getId())
                .setNew(true)
                .setSyncariId("syncariAcctId123")
                .setOccurredAt(System.currentTimeMillis())
                .addSource("my zendesk connector","externalZDId",System.currentTimeMillis())
                .addChange(new FieldChange().setFieldId(coreNameAttr.getId()).setOldValue(null).setNewValue("Account Name").setApiName("Name"))
                .addChange(new FieldChange().setFieldId(coreRevenueAttribute.getId()).setOldValue(null).setNewValue(300.0).setApiName("Revenue"));

        log.setId(ObjectId.get().toHexString());
        when(txLogRepo.findAllStream()).thenReturn(Stream.empty());
        when(transactionLogService.findMergesByBatchId("currentBatchId", Date.from(Instant.EPOCH), new PageCursor("", PageDirection.previous,500)))
                .thenReturn(new com.syncari.core.model.pagination.Page<TransactionLog>(new PageInfo(), new ArrayList<>()));
        when(txLogRepo.findByBatchId("currentBatchId", Pageable.unpaged())).thenReturn(Page.empty());
        when(txLogRepo.findByBatchIdAndSyncariIdIn("currentBatchId", List.of("syncariAcctId123"))).thenReturn(List.of(log));
        when(connectorService.get("my zendesk connector")).thenReturn(connector);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        when(currentBatch.getEntityBatch(sinkEntityDef)).thenReturn(new StagedBatch(sinkEntityDef.getApiName()).setConnectorId("my zendesk connector"));
        when(currentBatch.recordsBySyncariIdIterator()).thenReturn(recordsIter);
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(recordsIter.hasNext()).thenReturn(true, true, false);
        when(recordsIter.next()).thenReturn(new RecordsBySyncariId("syncariAcctId123"));

        List<EntityData> t = List.of(new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("Name", "Account Name")
                .addValue("Revenue", 300.0));
        when(entityRepo.find(any(EntityDefinition.class), any(),any(PageCursor.class))).thenReturn(t);
        when(graphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(nameAttrGraph,revAttrGraph, qualiytAttrGraph, ownerAttrGraph));
        when(attributeDefinitionCache.findById(coreNameAttr.getId())).thenReturn(Optional.of(coreNameAttr));
        when(attributeDefinitionCache.findById(coreRevenueAttribute.getId())).thenReturn(Optional.of(coreRevenueAttribute));
        when(attributeDefinitionCache.findById(coreQualityAttribute.getId())).thenReturn(Optional.of(coreQualityAttribute));
        when(attributeDefinitionCache.findById(coreOwnerAttribute.getId())).thenReturn(Optional.of(coreOwnerAttribute));
        when(attributeDefinitionCache.findAllById(anyIterable())).thenReturn(List.of(coreNameAttr, coreRevenueAttribute, coreQualityAttribute, coreOwnerAttribute));
        //no id mapping present
        when(idMappingRepo.findBySyncariIds("account", Set.of("syncariAcctId123"))).thenReturn(List.of());
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+setValueNode.getId(), Pair.of(new FunctionResult(t.get(0).addValue(coreQualityAttribute.getApiName(),"BAD_RECORD"), ObjectType.VALUE),setValueNode));
            return null;
        }).when(evaluator).evaluate(eq(sinkNode), eq(entityGraph),any(GraphContext.class),any(), any());
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+coreNameAttrNode.getId(),new FunctionResult("Account Name",StringType.VALUE));
            return null;
        }).when(evaluator).evaluate(eq(sinkNameAttrNode), eq(nameAttrGraph),any(GraphContext.class),eq(n->n.getType()== MappingNodeType.CORE_ATTRIBUTE), any());
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+coreRevAttrNode.getId(),new FunctionResult(300.0,DoubleType.VALUE));
            return null;
        }).when(evaluator).evaluate(eq(sinkRevAttrNode), eq(revAttrGraph),any(GraphContext.class),eq(n->n.getType()== MappingNodeType.CORE_ATTRIBUTE), any());
        when(mockDataServiceFactory.getDataService(connector.getMetadata())).thenReturn(zendeskService);
        SyncResponse response = new SyncResponse();
        when(zendeskService.create(any())).thenReturn(response);
        when(schemaService.getEntity(sinkEntityDef.getId())).thenReturn(sinkEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);

        //remove sinkQualityAttribute from sinkEntityDef
        sinkEntityDef.setAttributes(sinkEntityDef.getAttributes().stream().filter(a->!a.getApiName().equals(sinkQualityAttribute.getApiName())).collect(Collectors.toList()));
        sinkEntityDef.setAttributes(sinkEntityDef.getAttributes().stream().filter(a->!a.getApiName().equals(sinkOwnerAttribute.getApiName())).collect(Collectors.toList()));
        when(schemaService.refreshSynapseSchema(eq(sinkEntityDef.getConnectorId()), eq(sinkEntityDef), any())).thenReturn(List.of(sinkEntityDef));
        final GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph);
        graphContext.setSyncariEntity(coreEntityDef);

        saveToSink.execute(sinkEntityDef, context, graphContext);


        verify(connectorService).get("my zendesk connector");
        verify(schemaService).getEntity(sinkEntityDef.getId());
        verify(currentBatch).getSyncariEntityName();
        verify(transactionLogService).findMergesByBatchId(eq("currentBatchId"), any(), eq(new PageCursor("", PageDirection.previous, 500)));
        //verify(txLogRepo).findByBatchId("currentBatchId", Pageable.unpaged());
        ArgumentCaptor<Map<String, Object>> contextCapture = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<SyncRequest> requestCapture = ArgumentCaptor.forClass(SyncRequest.class);

        verify(idMappingRepo).findBySyncariIds("account", Set.of("syncariAcctId123"));
        //verify(evaluator, times(3)).evaluate(any(MappingNode.class), any(MappingGraph.class), any(GraphContext.class),any(Predicate.class));
        verify(mockDataServiceFactory).getDataService(connector.getMetadata());

        verify(zendeskService).create(requestCapture.capture());

        assertEquals("Organization", requestCapture.getValue().getEntityName());
        assertNotNull(requestCapture.getValue().getData().get("my zendesk connector"));
        assertEquals("Account Name", requestCapture.getValue().getData().get("my zendesk connector").get(0).getValue("Name"));
        //The deleted attribute was skipped, so we should not have it in the end result
        assertFalse(requestCapture.getValue().getData().get("my zendesk connector").get(0).has(sinkQualityAttribute.getApiName()));
        assertFalse(requestCapture.getValue().getData().get("my zendesk connector").get(0).has(sinkOwnerAttribute.getApiName()));
        assertEquals(300.0, requestCapture.getValue().getData().get("my zendesk connector").get(0).getValue("Revenue"));

    }
    @Test
    public void paginationDoesNotAccumulate() {

        ViperContext context = new ViperContext(new Organization(), new Instance(), new User());

        final Connector connector = createConnector("my zendesk connector", "zendeskConnectorId");
        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());



        EntityDefinition sinkEntityDef = new EntityDefinition();
        sinkEntityDef.setConnectorId(connector.getId());
        sinkEntityDef.setApiName("Organization");
        sinkEntityDef.setDisplayName("Organization");
        sinkEntityDef.setStatus(Status.ACTIVE);
        sinkEntityDef.setId(ObjectId.get().toHexString());
        AttributeDefinition coreNameAttr = createAttribute("Name", new StringType(), coreEntityDef.getId());
        AttributeDefinition sinkNameAttr = createAttribute("Name", new StringType(), sinkEntityDef.getId());


        AttributeDefinition coreRevenueAttribute = createAttribute("Revenue", new DoubleType(), coreEntityDef.getId());
        AttributeDefinition coreQualityAttribute = createAttribute("Quality", new StringType(), coreEntityDef.getId());
        AttributeDefinition sinkRevenueAttribute = createAttribute("Revenue", new DoubleType(), sinkEntityDef.getId());
        AttributeDefinition sinkQualityAttribute = createAttribute("Sink Quality", new StringType(), sinkEntityDef.getId());

        MappingGraph entityGraph = createGraph(coreEntityDef.getId(),Scope.ENTITY);
        MappingNode coreNode = coreEntityNode(coreEntityDef, entityGraph);
        MappingNode sinkNode = coreSinkNode(sinkEntityDef,entityGraph);

        MappingNode setValueNode = new MappingNode().setScope(Scope.ENTITY).setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall()
                .setFunctionDefinition(functionService.findByNameAndScope("setValueOnEntity",Scope.ENTITY).get())
                .setParams(List.of(ParameterValue.string("output_"+coreNode.getId(),"input")))
                .setConfig(Map.of("attributeDefinitionId",coreQualityAttribute.getId(),"newValue","GOOD_RECORD"))
        )).setName("Set Value");
        setValueNode.setId(ObjectId.get().toHexString());
        entityGraph.getNodes().add(setValueNode);
        Edge coreToSetValueSink = edge(coreNode, setValueNode, entityGraph);
        coreToSetValueSink.setId(ObjectId.get().toHexString());
        Edge setValueToSink = edge(setValueNode, sinkNode, entityGraph);
        setValueToSink.setId(ObjectId.get().toHexString());

        MappingGraph nameAttrGraph = createGraph(coreNameAttr.getId(), Scope.ATTRIBUTE);
        MappingGraph revAttrGraph = createGraph(coreRevenueAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph qualiytAttrGraph = createGraph(coreQualityAttribute.getId(), Scope.ATTRIBUTE);

        MappingNode coreNameAttrNode = coreAttributeNode(coreNameAttr,nameAttrGraph);
        MappingNode sinkNameAttrNode = sinkAttributeNode(sinkNameAttr,nameAttrGraph);
        Edge coreAttrToSink = edge(coreNameAttrNode, sinkNameAttrNode, nameAttrGraph);

        MappingNode coreQAttrNode = coreAttributeNode(coreQualityAttribute,qualiytAttrGraph);
        MappingNode sinkQAttrNode = sinkAttributeNode(sinkQualityAttribute,qualiytAttrGraph);
        Edge coreQToSinkQ = edge(coreQAttrNode, sinkQAttrNode,qualiytAttrGraph);

        var sinkRevAttrNode = sinkAttributeNode(sinkRevenueAttribute, revAttrGraph);
        var coreRevAttrNode = coreAttributeNode(coreRevenueAttribute, revAttrGraph);
        Edge revenueCoreToSink = edge(coreRevAttrNode, sinkRevAttrNode, revAttrGraph);

        sinkEntityDef.addField(sinkNameAttr);
        sinkEntityDef.addField(sinkRevenueAttribute);
        sinkEntityDef.addField(sinkQualityAttribute);
        TransactionLog log = new TransactionLog()
                .setBatchId("currentBatchId")
                .setEntityName("account")
                .setEntityId(coreEntityDef.getId())
                .setNew(true)
                .setSyncariId("syncariAcctId123")
                .setOccurredAt(System.currentTimeMillis())
                .addSource("my zendesk connector","externalZDId",System.currentTimeMillis())
                .addChange(new FieldChange().setFieldId(coreNameAttr.getId()).setOldValue(null).setNewValue("Account Name").setApiName("Name"))
                .addChange(new FieldChange().setFieldId(coreRevenueAttribute.getId()).setOldValue(null).setNewValue(300.0).setApiName("Revenue"));
        when(txLogRepo.findAllStream()).thenReturn(Stream.empty());
        when(transactionLogService.findMergesByBatchId("currentBatchId", Date.from(Instant.EPOCH), new PageCursor("", PageDirection.previous,500)))
                .thenReturn(new com.syncari.core.model.pagination.Page<TransactionLog>(new PageInfo(), new ArrayList<>()));
        when(txLogRepo.findByBatchId("currentBatchId", Pageable.unpaged())).thenReturn(Page.empty());
        when(txLogRepo.findByBatchIdAndSyncariIdIn("currentBatchId", List.of("syncariAcctId123"))).thenReturn(List.of(log));
        when(connectorService.get("my zendesk connector")).thenReturn(connector);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        when(currentBatch.getEntityBatch(sinkEntityDef)).thenReturn(new StagedBatch(sinkEntityDef.getApiName()).setConnectorId("my zendesk connector"));
        when(currentBatch.recordsBySyncariIdIterator()).thenReturn(recordsIter);
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(recordsIter.hasNext()).thenReturn(true, true, false);
        when(recordsIter.next()).thenReturn(new RecordsBySyncariId("syncariAcctId123"));

        List<EntityData> page1 = newRecords(500,0);
        List<EntityData> page2 = newRecords(150,500);
        List<EntityData> page3 = newRecords(0,0);
        List<EntityData> allRecords = new ArrayList<>(page1);
        allRecords.addAll(page2);
        when(entityRepo.find(any(EntityDefinition.class), any(), any(PageCursor.class))).thenReturn(page1, page1, page2, page3);
        when(graphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(nameAttrGraph,revAttrGraph, qualiytAttrGraph));
        when(attributeDefinitionCache.findById(coreNameAttr.getId())).thenReturn(Optional.of(coreNameAttr));
        when(attributeDefinitionCache.findById(coreRevenueAttribute.getId())).thenReturn(Optional.of(coreRevenueAttribute));
        when(attributeDefinitionCache.findById(coreQualityAttribute.getId())).thenReturn(Optional.of(coreQualityAttribute));
        when(attributeDefinitionCache.findAllById(anyIterable())).thenReturn(List.of(coreNameAttr, coreRevenueAttribute, coreQualityAttribute));
        //no id mapping present
        when(idMappingRepo.findBySyncariIds(eq("account"), Set.of(anyString()))).thenReturn(List.of());
        AtomicInteger counter= new AtomicInteger(0);
        doAnswer((Answer<Void>) m -> {
            EntityData record = allRecords.get(counter.getAndIncrement());
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+setValueNode.getId(), Pair.of(new FunctionResult(record.addValue(coreQualityAttribute.getApiName(),"BAD_RECORD"), ObjectType.VALUE),setValueNode));
            return null;
        }).when(evaluator).evaluate(eq(sinkNode), eq(entityGraph),any(GraphContext.class),any(), any());
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+coreNameAttrNode.getId(),new FunctionResult("Account Name",StringType.VALUE));
            return null;
        }).when(evaluator).evaluate(eq(sinkNameAttrNode), eq(nameAttrGraph),any(GraphContext.class),eq(n->n.getType()== MappingNodeType.CORE_ATTRIBUTE), any());
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+coreRevAttrNode.getId(),new FunctionResult(300.0,DoubleType.VALUE));
            return null;
        }).when(evaluator).evaluate(eq(sinkRevAttrNode), eq(revAttrGraph),any(GraphContext.class),eq(n->n.getType()== MappingNodeType.CORE_ATTRIBUTE), any());
        when(mockDataServiceFactory.getDataService(connector.getMetadata())).thenReturn(zendeskService);
        SyncResponse response = new SyncResponse();
        when(zendeskService.create(any())).thenReturn(response);
        when(schemaService.getEntity(sinkEntityDef.getId())).thenReturn(sinkEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(schemaService.refreshSynapseSchema(eq(sinkEntityDef.getConnectorId()), eq(sinkEntityDef), any())).thenReturn(List.of(sinkEntityDef));
        final GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph);
        graphContext.setSyncariEntity(coreEntityDef);

        saveToSink.execute(sinkEntityDef, context, graphContext);

        verify(connectorService).get("my zendesk connector");
        verify(schemaService).getEntity(sinkEntityDef.getId());
        verify(currentBatch).getSyncariEntityName();
        verify(transactionLogService).findMergesByBatchId(eq("currentBatchId"), any(), eq(new PageCursor("", PageDirection.previous, 500)));
        //verify(txLogRepo).findByBatchId("currentBatchId", Pageable.unpaged());
        ArgumentCaptor<Map<String, Object>> contextCapture = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<SyncRequest> requestCapture = ArgumentCaptor.forClass(SyncRequest.class);

        //verify(idMappingRepo).findBySyncariIds("account", eq(anySet()));
        //verify(evaluator, times(3)).evaluate(any(MappingNode.class), any(MappingGraph.class), any(GraphContext.class),any(Predicate.class));
        verify(mockDataServiceFactory).getDataService(connector.getMetadata());

        verify(zendeskService,times(2)).create(requestCapture.capture());

        assertEquals("Organization", requestCapture.getValue().getEntityName());
        assertEquals(500,requestCapture.getAllValues().get(0).getData().get("my zendesk connector").size());
        assertEquals(150,requestCapture.getAllValues().get(1).getData().get("my zendesk connector").size());
        assertNotNull(requestCapture.getValue().getData().get("my zendesk connector"));
        //first record of last batch
        assertEquals("Account Name500", requestCapture.getValue().getData().get("my zendesk connector").get(0).getValue("Name"));
        assertEquals("BAD_RECORD", requestCapture.getValue().getData().get("my zendesk connector").get(0).getValue(sinkQualityAttribute.getApiName()));
        assertEquals(300.0, requestCapture.getValue().getData().get("my zendesk connector").get(0).getValue("Revenue"));

    }

    protected List<EntityData> newRecords(int numRecords, int startIndex) {
        List<EntityData> records = new ArrayList<>();
        for(int i=0;i<numRecords;i++){
            records.add(new EntityData("account")
                    .setSyncariEntityId("syncariAcctId"+(startIndex+i))
                    .addValue("Name", "Account Name" +(startIndex+i))
                    .addValue("Revenue", 300.0));
        }
        return records;
    }

    @Test
    public void filterOnIncomingChangeField() {

        ViperContext context = new ViperContext(new Organization(), new Instance(), new User());

        Connector connector = createConnector("my zendesk connector", "zendeskConnectorId");
        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());



        EntityDefinition sinkEntityDef = new EntityDefinition();
        sinkEntityDef.setConnectorId(connector.getId());
        sinkEntityDef.setApiName("Organization");
        sinkEntityDef.setDisplayName("Organization");
        sinkEntityDef.setStatus(Status.ACTIVE);
        sinkEntityDef.setId(ObjectId.get().toHexString());
        AttributeDefinition coreNameAttr = createAttribute("Name", new StringType(), coreEntityDef.getId());
        AttributeDefinition sinkNameAttr = createAttribute("Name", new StringType(), sinkEntityDef.getId());


        AttributeDefinition coreRevenueAttribute = createAttribute("Revenue", new DoubleType(), coreEntityDef.getId());
        AttributeDefinition coreQualityAttribute = createAttribute("Quality", new StringType(), coreEntityDef.getId());
        AttributeDefinition sinkRevenueAttribute = createAttribute("Revenue", new DoubleType(), sinkEntityDef.getId());
        AttributeDefinition sinkQualityAttribute = createAttribute("Sink Quality", new StringType(), sinkEntityDef.getId());

        MappingGraph entityGraph = createGraph(coreEntityDef.getId(),Scope.ENTITY);
        MappingNode coreNode = coreEntityNode(coreEntityDef, entityGraph);
        MappingNode sinkNode = coreSinkNode(sinkEntityDef,entityGraph);

        MappingNode setValueNode = new MappingNode().setScope(Scope.ENTITY).setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall()
                .setFunctionDefinition(functionService.findByNameAndScope("setValueOnEntity",Scope.ENTITY).get())
                .setParams(List.of(ParameterValue.string("output_"+coreNode.getId(),"input")))
                .setConfig(Map.of("attributeDefinitionId",coreQualityAttribute.getId(),"newValue","GOOD_RECORD"))
        )).setName("Set Value");
        setValueNode.setId(ObjectId.get().toHexString());
        entityGraph.getNodes().add(setValueNode);
        Edge coreToSetValueSink = edge(coreNode, setValueNode, entityGraph);
        coreToSetValueSink.setId(ObjectId.get().toHexString());
        Edge setValueToSink = edge(setValueNode, sinkNode, entityGraph);
        setValueToSink.setId(ObjectId.get().toHexString());

        MappingGraph nameAttrGraph = createGraph(coreNameAttr.getId(), Scope.ATTRIBUTE);
        MappingGraph revAttrGraph = createGraph(coreRevenueAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph qualiytAttrGraph = createGraph(coreQualityAttribute.getId(), Scope.ATTRIBUTE);

        MappingNode coreNameAttrNode = coreAttributeNode(coreNameAttr,nameAttrGraph);
        MappingNode sinkNameAttrNode = sinkAttributeNode(sinkNameAttr,nameAttrGraph);
        Edge coreAttrToSink = edge(coreNameAttrNode, sinkNameAttrNode, nameAttrGraph);

        MappingNode coreQAttrNode = coreAttributeNode(coreQualityAttribute,qualiytAttrGraph);
        MappingNode sinkQAttrNode = sinkAttributeNode(sinkQualityAttribute,qualiytAttrGraph);

        //incoming_change IS update
        Map<String, Object> predicateMap = new HashMap<>();
        var preidcates = List.of(Map.of(
                "left", Map.of("datatype","exact_match","type","variable","value","incoming_change"),
                "operator", "eq",
                "right",Map.of("type","literal","value","updates")
        ));
        predicateMap.put("predicate",Map.of("predicates",preidcates));
        predicateMap.put("operator","AND");
        MappingNode filterUpdates =
                new MappingNode().setScope(Scope.ATTRIBUTE).setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall()
                        .setFunctionDefinition(functionService.findByNameAndScope("filter",Scope.ATTRIBUTE).get())
                        .setParams(List.of(ParameterValue.string("output_"+coreQAttrNode.getId(),"input")))
                        .setConfig(predicateMap)
                )).setName("UpdatesOnly");
        filterUpdates.setId(ObjectId.get().toHexString());
        qualiytAttrGraph.getNodes().add(filterUpdates);
        Edge coreQToFilter = edge(coreQAttrNode, filterUpdates,qualiytAttrGraph);
        Edge filterToSinkQ = edge(filterUpdates,sinkQAttrNode, qualiytAttrGraph);
        var sinkRevAttrNode = sinkAttributeNode(sinkRevenueAttribute, revAttrGraph);
        var coreRevAttrNode = coreAttributeNode(coreRevenueAttribute, revAttrGraph);
        Edge revenueCoreToSink = edge(coreRevAttrNode, sinkRevAttrNode, revAttrGraph);

        sinkEntityDef.addField(sinkNameAttr);
        sinkEntityDef.addField(sinkRevenueAttribute);
        sinkEntityDef.addField(sinkQualityAttribute);
        TransactionLog log = new TransactionLog()
                .setBatchId("currentBatchId")
                .setEntityName("account")
                .setEntityId(coreEntityDef.getId())
                .setNew(true)
                .setSyncariId("syncariAcctId123")
                .setOccurredAt(System.currentTimeMillis())
                .addSource("my zendesk connector","externalZDId",System.currentTimeMillis())
                .addChange(new FieldChange().setFieldId(coreNameAttr.getId()).setOldValue(null).setNewValue("Account Name").setApiName("Name"))
                .addChange(new FieldChange().setFieldId(coreRevenueAttribute.getId()).setOldValue(null).setNewValue(300.0).setApiName("Revenue"));

        log.setId(ObjectId.get().toHexString());

        when(txLogRepo.findAllStream()).thenReturn(Stream.empty());
        when(transactionLogService.findMergesByBatchId("currentBatchId", Date.from(Instant.EPOCH), new PageCursor("", PageDirection.previous,500)))
                .thenReturn(new com.syncari.core.model.pagination.Page<TransactionLog>(new PageInfo(), new ArrayList<>()));
        when(txLogRepo.findByBatchId("currentBatchId", Pageable.unpaged())).thenReturn(Page.empty());
        when(txLogRepo.findByBatchIdAndSyncariIdIn("currentBatchId", List.of("syncariAcctId123"))).thenReturn(List.of(log));
        when(connectorService.get("my zendesk connector")).thenReturn(connector);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        when(currentBatch.getEntityBatch(sinkEntityDef)).thenReturn(new StagedBatch(sinkEntityDef.getApiName()).setConnectorId("my zendesk connector"));
        when(currentBatch.recordsBySyncariIdIterator()).thenReturn(recordsIter);
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(recordsIter.hasNext()).thenReturn(true, true, false);
        when(recordsIter.next()).thenReturn(new RecordsBySyncariId("syncariAcctId123"));

        List<EntityData> t = List.of(new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .setNew(false)
                .addValue("Name", "Account Name")
                .addValue("Revenue", 300.0));
        when(entityRepo.find(any(EntityDefinition.class), any(),any(PageCursor.class))).thenReturn(t);
        when(graphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(nameAttrGraph,revAttrGraph, qualiytAttrGraph));
        when(attributeDefinitionCache.findById(coreNameAttr.getId())).thenReturn(Optional.of(coreNameAttr));
        when(attributeDefinitionCache.findById(coreRevenueAttribute.getId())).thenReturn(Optional.of(coreRevenueAttribute));
        when(attributeDefinitionCache.findById(coreQualityAttribute.getId())).thenReturn(Optional.of(coreQualityAttribute));
        when(attributeDefinitionCache.findAllById(anyIterable())).thenReturn(List.of(coreNameAttr, coreRevenueAttribute, coreQualityAttribute));
        //no id mapping present
        when(idMappingRepo.findBySyncariIds("account", Set.of("syncariAcctId123"))).thenReturn(List.of());
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+setValueNode.getId(), Pair.of(new FunctionResult(t.get(0).addValue(coreQualityAttribute.getApiName(),"BAD_RECORD"), ObjectType.VALUE),setValueNode));
            return null;
        }).when(evaluator).evaluate(eq(sinkNode), eq(entityGraph),any(GraphContext.class),any(), any());
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+coreNameAttrNode.getId(),new FunctionResult("Account Name",StringType.VALUE));
            return null;
        }).when(evaluator).evaluate(eq(sinkNameAttrNode), eq(nameAttrGraph),any(GraphContext.class),eq(n->n.getType()== MappingNodeType.CORE_ATTRIBUTE), any());
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+coreRevAttrNode.getId(),new FunctionResult(300.0,DoubleType.VALUE));
            return null;
        }).when(evaluator).evaluate(eq(sinkRevAttrNode), eq(revAttrGraph),any(GraphContext.class),eq(n->n.getType()== MappingNodeType.CORE_ATTRIBUTE), any());

        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+filterUpdates.getId(),Pair.of(new FunctionResult("BAD_RECORD",StringType.VALUE),filterUpdates));
            return null;
        }).when(evaluator).evaluate(eq(sinkQAttrNode), eq(qualiytAttrGraph),any(GraphContext.class),any(), any());
        when(mockDataServiceFactory.getDataService(connector.getMetadata())).thenReturn(zendeskService);
        SyncResponse response = new SyncResponse();
        when(zendeskService.create(any())).thenReturn(response);
        when(schemaService.getEntity(sinkEntityDef.getId())).thenReturn(sinkEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);

        when(schemaService.refreshSynapseSchema(eq(sinkEntityDef.getConnectorId()), eq(sinkEntityDef), any())).thenReturn(List.of(sinkEntityDef));
        final GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph);
        graphContext.setSyncariEntity(coreEntityDef);

        saveToSink.execute(sinkEntityDef, context, graphContext);

        verify(connectorService).get("my zendesk connector");
        verify(schemaService).getEntity(sinkEntityDef.getId());
        verify(currentBatch).getSyncariEntityName();
        verify(transactionLogService).findMergesByBatchId(eq("currentBatchId"), any(), eq(new PageCursor("", PageDirection.previous, 500)));
        //verify(txLogRepo).findByBatchId("currentBatchId", Pageable.unpaged());//verify(txLogRepo).findByBatchId("currentBatchId", Pageable.unpaged());
        ArgumentCaptor<Map<String, Object>> contextCapture = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<SyncRequest> requestCapture = ArgumentCaptor.forClass(SyncRequest.class);

        verify(idMappingRepo).findBySyncariIds("account", Set.of("syncariAcctId123"));
        //verify(evaluator, times(3)).evaluate(any(MappingNode.class), any(MappingGraph.class), any(GraphContext.class),any(Predicate.class));
        verify(mockDataServiceFactory).getDataService(connector.getMetadata());

        verify(zendeskService).create(requestCapture.capture());

        assertEquals("Organization", requestCapture.getValue().getEntityName());
        assertNotNull(requestCapture.getValue().getData().get("my zendesk connector"));
        assertEquals("Account Name", requestCapture.getValue().getData().get("my zendesk connector").get(0).getValue("Name"));
        assertEquals("BAD_RECORD", requestCapture.getValue().getData().get("my zendesk connector").get(0).getValue(sinkQualityAttribute.getApiName()));
        assertEquals(300.0, requestCapture.getValue().getData().get("my zendesk connector").get(0).getValue("Revenue"));

    }

    @Test
    public void multipathDestinations() {

        ViperContext context = new ViperContext(new Organization(), new Instance(), new User());

        Connector connector = createConnector("my zendesk connector", "zendeskConnectorId");
        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());



        EntityDefinition sinkEntityDef = new EntityDefinition();
        sinkEntityDef.setConnectorId(connector.getId());
        sinkEntityDef.setApiName("Organization");
        sinkEntityDef.setDisplayName("Organization");
        sinkEntityDef.setStatus(Status.ACTIVE);
        sinkEntityDef.setId(ObjectId.get().toHexString());
        AttributeDefinition coreNameAttr = createAttribute("Name", new StringType(), coreEntityDef.getId());
        AttributeDefinition sinkNameAttr = createAttribute("Name", new StringType(), sinkEntityDef.getId());


        AttributeDefinition coreRevenueAttribute = createAttribute("Revenue", new DoubleType(), coreEntityDef.getId());
        AttributeDefinition coreQualityAttribute = createAttribute("Quality", new StringType(), coreEntityDef.getId());
        AttributeDefinition sinkRevenueAttribute = createAttribute("Revenue", new DoubleType(), sinkEntityDef.getId());
        AttributeDefinition sinkQualityAttribute = createAttribute("Sink Quality", new StringType(), sinkEntityDef.getId());

        MappingGraph entityGraph = createGraph(coreEntityDef.getId(),Scope.ENTITY);
        MappingNode coreNode = coreEntityNode(coreEntityDef, entityGraph);
        MappingNode sinkNode = coreSinkNode(sinkEntityDef,entityGraph);

        MappingNode setValueNode = new MappingNode().setScope(Scope.ENTITY).setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall()
                .setFunctionDefinition(functionService.findByNameAndScope("setValueOnEntity",Scope.ENTITY).get())
                .setParams(List.of(ParameterValue.string("output_"+coreNode.getId(),"input")))
                .setConfig(Map.of("attributeDefinitionId",coreQualityAttribute.getId(),"newValue","GOOD_RECORD"))
        )).setName("Set Value");
        setValueNode.setId(ObjectId.get().toHexString());
        entityGraph.getNodes().add(setValueNode);
        Edge coreToSetValueSink = edge(coreNode, setValueNode, entityGraph);
        coreToSetValueSink.setId(ObjectId.get().toHexString());
        Edge setValueToSink = edge(setValueNode, sinkNode, entityGraph);
        setValueToSink.setId(ObjectId.get().toHexString());

        MappingGraph nameAttrGraph = createGraph(coreNameAttr.getId(), Scope.ATTRIBUTE);
        MappingGraph revAttrGraph = createGraph(coreRevenueAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph qualiytAttrGraph = createGraph(coreQualityAttribute.getId(), Scope.ATTRIBUTE);

        MappingNode coreNameAttrNode = coreAttributeNode(coreNameAttr,nameAttrGraph);
        MappingNode sinkNameAttrNode = sinkAttributeNode(sinkNameAttr,nameAttrGraph);
        Edge coreAttrToSink = edge(coreNameAttrNode, sinkNameAttrNode, nameAttrGraph);

        MappingNode coreQAttrNode = coreAttributeNode(coreQualityAttribute,qualiytAttrGraph);
        MappingNode sinkQAttrNode = sinkAttributeNode(sinkQualityAttribute,qualiytAttrGraph);

        //incoming_change IS update
        Map<String, Object> predicateMap = new HashMap<>();
        var preidcates = List.of(Map.of(
                "left", Map.of("datatype","exact_match","type","variable","value","incoming_change"),
                "operator", "eq",
                "right",Map.of("type","literal","value","updates")
        ));
        predicateMap.put("predicate",Map.of("predicates",preidcates));
        predicateMap.put("operator","AND");
        MappingNode filterUpdates =
                new MappingNode().setScope(Scope.ATTRIBUTE).setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall()
                        .setFunctionDefinition(functionService.findByNameAndScope("filter",Scope.ATTRIBUTE).get())
                        .setParams(List.of(ParameterValue.string("output_"+coreQAttrNode.getId(),"input")))
                        .setConfig(predicateMap)
                )).setName("UpdatesOnly");
        filterUpdates.setId(ObjectId.get().toHexString());
        MappingNode filterFalse =
                new MappingNode().setScope(Scope.ATTRIBUTE).setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall()
                        .setFunctionDefinition(functionService.findByNameAndScope("isFalse",Scope.ATTRIBUTE).get())
                        .setParams(List.of(ParameterValue.string("output_"+filterUpdates.getId(),"input")))
                        .setConfig(predicateMap)
                )).setName("Is False?");
        filterFalse.setId(ObjectId.get().toHexString());
        MappingNode setValueOnQNode =
                new MappingNode().setScope(Scope.ATTRIBUTE).setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall()
                        .setFunctionDefinition(functionService.findByNameAndScope("setValue",Scope.ATTRIBUTE).get())
                        .setParams(List.of(ParameterValue.string("output_"+filterFalse.getId(),"input")))
                        .setConfig(Map.of("newValue","ALT_RECORD"))
                )).setName("Set to ALT_RECORD");
        setValueOnQNode.setId(ObjectId.get().toHexString());
        qualiytAttrGraph.getNodes().add(filterUpdates);
        Edge coreQToFilter = edge(coreQAttrNode, filterUpdates,qualiytAttrGraph);
        Edge filterToSinkQ = edge(filterUpdates,sinkQAttrNode, qualiytAttrGraph);
        Edge filterToFalse = edge(filterUpdates,filterFalse, qualiytAttrGraph);
        Edge falseToSetValueOnNode = edge(filterFalse,setValueOnQNode, qualiytAttrGraph);
        Edge setValueToSinkQ = edge(setValueOnQNode,sinkQAttrNode, qualiytAttrGraph);
        var sinkRevAttrNode = sinkAttributeNode(sinkRevenueAttribute, revAttrGraph);
        var coreRevAttrNode = coreAttributeNode(coreRevenueAttribute, revAttrGraph);
        Edge revenueCoreToSink = edge(coreRevAttrNode, sinkRevAttrNode, revAttrGraph);

        sinkEntityDef.addField(sinkNameAttr);
        sinkEntityDef.addField(sinkRevenueAttribute);
        sinkEntityDef.addField(sinkQualityAttribute);
        TransactionLog log = new TransactionLog()
                .setBatchId("currentBatchId")
                .setEntityName("account")
                .setEntityId(coreEntityDef.getId())
                .setNew(true)
                .setSyncariId("syncariAcctId123")
                .setOccurredAt(System.currentTimeMillis())
                .addSource("my zendesk connector","externalZDId",System.currentTimeMillis())
                .addChange(new FieldChange().setFieldId(coreNameAttr.getId()).setOldValue(null).setNewValue("Account Name").setApiName("Name"))
                .addChange(new FieldChange().setFieldId(coreRevenueAttribute.getId()).setOldValue(null).setNewValue(300.0).setApiName("Revenue"));

        log.setId(ObjectId.get().toHexString());

        when(txLogRepo.findAllStream()).thenReturn(Stream.empty());
        when(transactionLogService.findMergesByBatchId("currentBatchId", Date.from(Instant.EPOCH), new PageCursor("", PageDirection.previous,500)))
                .thenReturn(new com.syncari.core.model.pagination.Page<TransactionLog>(new PageInfo(), new ArrayList<>()));
        when(txLogRepo.findByBatchId("currentBatchId", Pageable.unpaged())).thenReturn(Page.empty());
        when(txLogRepo.findByBatchIdAndSyncariIdIn("currentBatchId", List.of("syncariAcctId123"))).thenReturn(List.of(log));
        when(connectorService.get("my zendesk connector")).thenReturn(connector);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        when(currentBatch.getEntityBatch(sinkEntityDef)).thenReturn(new StagedBatch(sinkEntityDef.getApiName()).setConnectorId("my zendesk connector"));
        when(currentBatch.recordsBySyncariIdIterator()).thenReturn(recordsIter);
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(recordsIter.hasNext()).thenReturn(true, true, false);
        when(recordsIter.next()).thenReturn(new RecordsBySyncariId("syncariAcctId123"));

        List<EntityData> t = List.of(new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .setNew(false)
                .addValue("Name", "Account Name")
                .addValue("Revenue", 300.0));
        when(entityRepo.find(any(EntityDefinition.class), any(),any(PageCursor.class))).thenReturn(t);
        when(graphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(nameAttrGraph,revAttrGraph, qualiytAttrGraph));
        when(attributeDefinitionCache.findById(coreNameAttr.getId())).thenReturn(Optional.of(coreNameAttr));
        when(attributeDefinitionCache.findById(coreRevenueAttribute.getId())).thenReturn(Optional.of(coreRevenueAttribute));
        when(attributeDefinitionCache.findById(coreQualityAttribute.getId())).thenReturn(Optional.of(coreQualityAttribute));
        when(attributeDefinitionCache.findAllById(anyIterable())).thenReturn(List.of(coreNameAttr, coreRevenueAttribute, coreQualityAttribute));
        //no id mapping present
        when(idMappingRepo.findBySyncariIds("account", Set.of("syncariAcctId123"))).thenReturn(List.of());
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+setValueNode.getId(), Pair.of(new FunctionResult(t.get(0).addValue(coreQualityAttribute.getApiName(),"BAD_RECORD"), ObjectType.VALUE),setValueNode));
            return null;
        }).when(evaluator).evaluate(eq(sinkNode), eq(entityGraph),any(GraphContext.class),any(), any());
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+coreNameAttrNode.getId(),new FunctionResult("Account Name",StringType.VALUE));
            return null;
        }).when(evaluator).evaluate(eq(sinkNameAttrNode), eq(nameAttrGraph),any(GraphContext.class),eq(n->n.getType()== MappingNodeType.CORE_ATTRIBUTE), any());
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+coreRevAttrNode.getId(),new FunctionResult(300.0,DoubleType.VALUE));
            return null;
        }).when(evaluator).evaluate(eq(sinkRevAttrNode), eq(revAttrGraph),any(GraphContext.class),eq(n->n.getType()== MappingNodeType.CORE_ATTRIBUTE), any());

        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+setValueOnQNode.getId(),Pair.of(new FunctionResult("ALT_RECORD",StringType.VALUE),filterUpdates));
            ctx.put("output_"+filterUpdates.getId(),Pair.of(new FunctionResult(new FilterFailedResult("BAD_RECORD"),StringType.VALUE),filterUpdates));
            return null;
        }).when(evaluator).evaluate(eq(sinkQAttrNode), eq(qualiytAttrGraph),any(GraphContext.class),any(), any());

        when(mockDataServiceFactory.getDataService(connector.getMetadata())).thenReturn(zendeskService);
        SyncResponse response = new SyncResponse();
        when(zendeskService.create(any())).thenReturn(response);
        when(schemaService.getEntity(sinkEntityDef.getId())).thenReturn(sinkEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);


        when(schemaService.refreshSynapseSchema(eq(sinkEntityDef.getConnectorId()), eq(sinkEntityDef), any())).thenReturn(List.of(sinkEntityDef));
        final GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph);
        graphContext.setSyncariEntity(coreEntityDef);

        saveToSink.execute(sinkEntityDef, context, graphContext);


        verify(connectorService).get("my zendesk connector");
        verify(schemaService).getEntity(sinkEntityDef.getId());
        verify(currentBatch).getSyncariEntityName();
        verify(transactionLogService).findMergesByBatchId(eq("currentBatchId"), any(), eq(new PageCursor("", PageDirection.previous, 500)));
        //verify(txLogRepo).findByBatchId("currentBatchId", Pageable.unpaged());
        ArgumentCaptor<Map<String, Object>> contextCapture = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<SyncRequest> requestCapture = ArgumentCaptor.forClass(SyncRequest.class);

        verify(idMappingRepo).findBySyncariIds("account", Set.of("syncariAcctId123"));
        //verify(evaluator, times(3)).evaluate(any(MappingNode.class), any(MappingGraph.class), any(GraphContext.class),any(Predicate.class));
        verify(mockDataServiceFactory).getDataService(connector.getMetadata());

        verify(zendeskService).create(requestCapture.capture());

        assertEquals("Organization", requestCapture.getValue().getEntityName());
        assertNotNull(requestCapture.getValue().getData().get("my zendesk connector"));
        assertEquals("Account Name", requestCapture.getValue().getData().get("my zendesk connector").get(0).getValue("Name"));
        assertEquals("ALT_RECORD", requestCapture.getValue().getData().get("my zendesk connector").get(0).getValue(sinkQualityAttribute.getApiName()));
        assertEquals(300.0, requestCapture.getValue().getData().get("my zendesk connector").get(0).getValue("Revenue"));

    }

    @Test
    public void sinkSideActions() {

        ViperContext context = new ViperContext(new Organization(), new Instance(), new User());

        Connector connector = createConnector("my zendesk connector", "zendeskConnectorId");
        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());



        EntityDefinition sinkEntityDef = new EntityDefinition();
        sinkEntityDef.setConnectorId(connector.getId());
        sinkEntityDef.setApiName("Organization");
        sinkEntityDef.setDisplayName("Organization");
        sinkEntityDef.setStatus(Status.ACTIVE);
        sinkEntityDef.setId(ObjectId.get().toHexString());
        AttributeDefinition coreNameAttr = createAttribute("Name", new StringType(), coreEntityDef.getId());
        AttributeDefinition sinkNameAttr = createAttribute("Name", new StringType(), sinkEntityDef.getId());


        AttributeDefinition coreRevenueAttribute = createAttribute("Revenue", new DoubleType(), coreEntityDef.getId());
        AttributeDefinition coreQualityAttribute = createAttribute("Quality", new StringType(), coreEntityDef.getId());
        AttributeDefinition sinkRevenueAttribute = createAttribute("Revenue", new DoubleType(), sinkEntityDef.getId());
        AttributeDefinition sinkQualityAttribute = createAttribute("Sink Quality", new StringType(), sinkEntityDef.getId());

        MappingGraph entityGraph = createGraph(coreEntityDef.getId(),Scope.ENTITY);
        MappingNode coreNode = coreEntityNode(coreEntityDef, entityGraph);
        MappingNode sinkNode = coreSinkNode(sinkEntityDef,entityGraph);

        MappingNode setValueNode = new MappingNode().setScope(Scope.ENTITY).setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall()
                .setFunctionDefinition(functionService.findByNameAndScope("setValueOnEntity",Scope.ENTITY).get())
                .setParams(List.of(ParameterValue.string("output_"+coreNode.getId(),"input")))
                .setConfig(Map.of("attributeDefinitionId",coreQualityAttribute.getId(),"newValue","GOOD_RECORD"))
        )).setName("Set Value");
        setValueNode.setId(ObjectId.get().toHexString());
        MappingNode addToSFDCCampaignNode = new MappingNode().setScope(Scope.ENTITY).setConfiguration(new GenericActionConfig()
                .setConfigMap(new HashMap<>(Map.of("campaignId","campaign1","synapseId","synapse1","status","CustomCampaignStatus")))
        ).setName("Add To Campaign");
        addToSFDCCampaignNode.setId(ObjectId.get().toHexString());
        entityGraph.getNodes().add(setValueNode);
        entityGraph.getNodes().add(addToSFDCCampaignNode);
        Edge coreToSetValueSink = edge(coreNode, setValueNode, entityGraph);
        coreToSetValueSink.setId(ObjectId.get().toHexString());
        Edge setValueToSink = edge(setValueNode, sinkNode, entityGraph);
        setValueToSink.setId(ObjectId.get().toHexString());
        Edge sinkToAction = edge(sinkNode, addToSFDCCampaignNode, entityGraph);
        sinkToAction.setId(ObjectId.get().toHexString());

        MappingGraph nameAttrGraph = createGraph(coreNameAttr.getId(), Scope.ATTRIBUTE);
        MappingGraph revAttrGraph = createGraph(coreRevenueAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph qualiytAttrGraph = createGraph(coreQualityAttribute.getId(), Scope.ATTRIBUTE);

        MappingNode coreNameAttrNode = coreAttributeNode(coreNameAttr,nameAttrGraph);
        MappingNode sinkNameAttrNode = sinkAttributeNode(sinkNameAttr,nameAttrGraph);
        Edge coreAttrToSink = edge(coreNameAttrNode, sinkNameAttrNode, nameAttrGraph);

        MappingNode coreQAttrNode = coreAttributeNode(coreQualityAttribute,qualiytAttrGraph);
        MappingNode sinkQAttrNode = sinkAttributeNode(sinkQualityAttribute,qualiytAttrGraph);
        Edge coreQToSinkQ = edge(coreQAttrNode, sinkQAttrNode,qualiytAttrGraph);

        var sinkRevAttrNode = sinkAttributeNode(sinkRevenueAttribute, revAttrGraph);
        var coreRevAttrNode = coreAttributeNode(coreRevenueAttribute, revAttrGraph);
        Edge revenueCoreToSink = edge(coreRevAttrNode, sinkRevAttrNode, revAttrGraph);

        sinkEntityDef.addField(sinkNameAttr);
        sinkEntityDef.addField(sinkRevenueAttribute);
        sinkEntityDef.addField(sinkQualityAttribute);
        TransactionLog log = new TransactionLog()
                .setBatchId("currentBatchId")
                .setEntityName("account")
                .setEntityId(coreEntityDef.getId())
                .setNew(true)
                .setSyncariId("syncariAcctId123")
                .setOccurredAt(System.currentTimeMillis())
                .addSource("my zendesk connector","externalZDId",System.currentTimeMillis())
                .addChange(new FieldChange().setFieldId(coreNameAttr.getId()).setOldValue(null).setNewValue("Account Name").setApiName("Name"))
                .addChange(new FieldChange().setFieldId(coreRevenueAttribute.getId()).setOldValue(null).setNewValue(300.0).setApiName("Revenue"));

        log.setId(ObjectId.get().toHexString());

        when(txLogRepo.findAllStream()).thenReturn(Stream.empty());
        when(transactionLogService.findMergesByBatchId("currentBatchId", Date.from(Instant.EPOCH), new PageCursor("", PageDirection.previous,500)))
                .thenReturn(new com.syncari.core.model.pagination.Page<TransactionLog>(new PageInfo(), new ArrayList<>()));
        when(txLogRepo.findByBatchId("currentBatchId", Pageable.unpaged())).thenReturn(Page.empty());
        when(txLogRepo.findByBatchIdAndSyncariIdIn("currentBatchId", List.of("syncariAcctId123"))).thenReturn(List.of(log));
        when(connectorService.get("my zendesk connector")).thenReturn(connector);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        when(currentBatch.getEntityBatch(sinkEntityDef)).thenReturn(new StagedBatch(sinkEntityDef.getApiName()).setConnectorId("my zendesk connector"));
        when(currentBatch.recordsBySyncariIdIterator()).thenReturn(recordsIter);
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(recordsIter.hasNext()).thenReturn(true, true, false);
        when(recordsIter.next()).thenReturn(new RecordsBySyncariId("syncariAcctId123"));
        when(stagedBatchRecordRepo.findByStagedBatchIdAndSyncariIdsAndEntity(currentBatch.getCurrentBatchId(), List.of("syncariAcctId123"), sinkEntityDef.getId())).thenReturn(
                List.of(new StagedBatchRecord().setSyncariId("syncariAcctId123").setEntityData(new EntityData().setName("Organization").addValue("Name","SomeName").addValue("_source","my zendesk connector")))
        );
        List<EntityData> t = List.of(new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("Name", "Account Name")
                .addValue("Revenue", 300.0));
        when(entityRepo.find(any(EntityDefinition.class), any(),any(PageCursor.class))).thenReturn(t);
        when(graphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(nameAttrGraph,revAttrGraph, qualiytAttrGraph));
        when(attributeDefinitionCache.findById(coreNameAttr.getId())).thenReturn(Optional.of(coreNameAttr));
        when(attributeDefinitionCache.findById(coreRevenueAttribute.getId())).thenReturn(Optional.of(coreRevenueAttribute));
        when(attributeDefinitionCache.findById(coreQualityAttribute.getId())).thenReturn(Optional.of(coreQualityAttribute));
        when(attributeDefinitionCache.findAllById(anyIterable())).thenReturn(List.of(coreNameAttr, coreRevenueAttribute, coreQualityAttribute));
        //no id mapping present
        when(idMappingRepo.findBySyncariIds("account", Set.of("syncariAcctId123"))).thenReturn(List.of());
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+setValueNode.getId(), Pair.of(new FunctionResult(t.get(0).addValue(coreQualityAttribute.getApiName(),"BAD_RECORD"), ObjectType.VALUE),setValueNode));
            return null;
        }).when(evaluator).evaluate(eq(sinkNode), eq(entityGraph),any(GraphContext.class),any(), any());
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+coreNameAttrNode.getId(),Pair.of(new FunctionResult("Account Name",StringType.VALUE),sinkNameAttrNode));
            return null;
        }).when(evaluator).evaluate(eq(sinkNameAttrNode), eq(nameAttrGraph),any(GraphContext.class),any(), any());
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+coreRevAttrNode.getId(),Pair.of(new FunctionResult(300.0,DoubleType.VALUE),coreRevAttrNode));
            return null;
        }).when(evaluator).evaluate(eq(sinkRevAttrNode), eq(revAttrGraph),any(GraphContext.class),any(), any());
        doAnswer((Answer<Void>) m -> null).when(evaluator).evaluate(eq(addToSFDCCampaignNode), eq(entityGraph),any(GraphContext.class),any(), any());
        when(mockDataServiceFactory.getDataService(connector.getMetadata())).thenReturn(zendeskService);
        SyncResponse response = new SyncResponse();
        when(zendeskService.create(any())).thenReturn(response);
        when(schemaService.getEntity(sinkEntityDef.getId())).thenReturn(sinkEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);

        when(schemaService.refreshSynapseSchema(eq(sinkEntityDef.getConnectorId()), eq(sinkEntityDef), any())).thenReturn(List.of(sinkEntityDef));
        final GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph);
        graphContext.setSyncariEntity(coreEntityDef);

        saveToSink.execute(sinkEntityDef, context, graphContext);


        verify(connectorService).get("my zendesk connector");
        verify(schemaService).getEntity(sinkEntityDef.getId());
        verify(currentBatch).getSyncariEntityName();
        verify(transactionLogService).findMergesByBatchId(eq("currentBatchId"), any(), eq(new PageCursor("", PageDirection.previous, 500)));
        verify(stagedBatchRecordRepo, times(1)).findByStagedBatchIdAndSyncariIdsAndEntity(currentBatch.getCurrentBatchId(), List.of("syncariAcctId123"), sinkEntityDef.getId());
        //verify(txLogRepo).findByBatchId("currentBatchId", Pageable.unpaged());
        ArgumentCaptor<GraphContext> contextCapture = ArgumentCaptor.forClass(GraphContext.class);
        ArgumentCaptor<SyncRequest> requestCapture = ArgumentCaptor.forClass(SyncRequest.class);

        verify(idMappingRepo).findBySyncariIds("account", Set.of("syncariAcctId123"));
        //verify(evaluator, times(3)).evaluate(any(MappingNode.class), any(MappingGraph.class), any(GraphContext.class),any(Predicate.class));
        verify(mockDataServiceFactory).getDataService(connector.getMetadata());

        verify(zendeskService).create(requestCapture.capture());
        verify(evaluator).evaluate(eq(sinkNode), eq(entityGraph), any(GraphContext.class), any(), any());
        verify(evaluator).evaluate(eq(sinkRevAttrNode), eq(revAttrGraph), any(GraphContext.class), any(), any());
        verify(evaluator).evaluate(eq(sinkNameAttrNode), eq(nameAttrGraph), any(GraphContext.class), any(), any());
        verify(evaluator).evaluate(eq(addToSFDCCampaignNode), eq(entityGraph), contextCapture.capture(), any(), any());
        //Make sure the graphContext has "record" thats needed for actions
        assertNotNull(contextCapture.getValue().get("record"));
        assertNotNull(contextCapture.getValue().get("incoming_record"));
        assertEquals("Organization", ((EntityData)contextCapture.getValue().get("incoming_record")).getName());
        assertEquals("Organization", requestCapture.getValue().getEntityName());
        assertNotNull(requestCapture.getValue().getData().get("my zendesk connector"));
        assertEquals("Account Name", requestCapture.getValue().getData().get("my zendesk connector").get(0).getValue("Name"));
        assertEquals("BAD_RECORD", requestCapture.getValue().getData().get("my zendesk connector").get(0).getValue(sinkQualityAttribute.getApiName()));
        assertEquals(300.0, requestCapture.getValue().getData().get("my zendesk connector").get(0).getValue("Revenue"));

    }

    @Test
    public void sinkSideActionsOnFieldPipeline() {

        ViperContext context = new ViperContext(new Organization(), new Instance(), new User());

        Connector connector = createConnector("myConnector", "myConnectorId", "myConnectorMetaId");
        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        var coreField1 = SchemaHelper.createAttribute("corefield1", StringType.VALUE, coreEntity.getId());
        var coreField2 = SchemaHelper.createAttribute("corefield2", StringType.VALUE, coreEntity.getId());
        coreEntity.addField(coreField1);
        coreEntity.addField(coreField2);

        EntityDefinition srcEntity = SchemaHelper.createEntityDef("srcAccount", "Source Account", connector);
        var srcField1 = SchemaHelper.createAttribute("srcfield1", StringType.VALUE, srcEntity.getId());
        var srcField2 = SchemaHelper.createAttribute("srcfield2", StringType.VALUE, srcEntity.getId());
        srcEntity.addField(srcField1);
        srcEntity.addField(srcField2);

        EntityDefinition sinkEntity = SchemaHelper.createEntityDef("sinkAccount", "Sink Account", connector);
        var sinkField1 = SchemaHelper.createAttribute("sinkfield1", StringType.VALUE, sinkEntity.getId());
        var sinkField2 = SchemaHelper.createAttribute("sinkfield2", StringType.VALUE, sinkEntity.getId());
        sinkEntity.addField(sinkField1);
        sinkEntity.addField(sinkField2);

        // Case1: No config
        MappingGraph entityGraph = newGraph(coreEntity, null, actionDefinitionRepo)
                .src(srcEntity)
                .dest(sinkEntity)
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "sinkAccount").getGraph();

        MappingGraph fieldGraph1 = newGraph(coreField1, functionService)
                .src(srcField1).dest(sinkField1)
                .action("addToSfdcCampaign", "Add To Campaign")
                .dest(sinkField1)
                .connect("srcfield1", "corefield1")
                .connect("corefield1", "Add To Campaign")
                .connect("Add To Campaign", "sinkfield1")
                .getGraph();

        MappingNode actionNode = fieldGraph1.findNodeByName("Add To Campaign").get();

        TransactionLog log = new TransactionLog()
                .setBatchId("currentBatchId")
                .setEntityName("coreAccount")
                .setEntityId(coreEntity.getId())
                .setNew(true)
                .setSyncariId("syncariAcctId123")
                .setOccurredAt(System.currentTimeMillis())
                .addSource("myConnectorId","externalZDId",System.currentTimeMillis())
                .addChange(new FieldChange().setFieldId(coreField1.getId()).setOldValue(null).setNewValue("Name").setApiName("corefield1"))
                .addChange(new FieldChange().setFieldId(coreField2.getId()).setOldValue(null).setNewValue("ABC").setApiName("corefield2"));

        log.setId(ObjectId.get().toHexString());

        when(txLogRepo.findAllStream()).thenReturn(Stream.empty());
        when(transactionLogService.findMergesByBatchId("currentBatchId", Date.from(Instant.EPOCH), new PageCursor("", PageDirection.previous,500)))
                .thenReturn(new com.syncari.core.model.pagination.Page<TransactionLog>(new PageInfo(), new ArrayList<>()));
        when(txLogRepo.findByBatchId("currentBatchId", Pageable.unpaged())).thenReturn(Page.empty());
        when(txLogRepo.findByBatchIdAndSyncariIdIn("currentBatchId", List.of("syncariAcctId123"))).thenReturn(List.of(log));
        when(connectorService.get("myConnectorId")).thenReturn(connector);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        when(currentBatch.getEntityBatch(sinkEntity)).thenReturn(new StagedBatch(sinkEntity.getApiName()).setConnectorId("myConnectorId"));
        when(currentBatch.recordsBySyncariIdIterator()).thenReturn(recordsIter);
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.getSyncariEntityName()).thenReturn("coreAccount");
        when(recordsIter.hasNext()).thenReturn(true, true, false);
        when(recordsIter.next()).thenReturn(new RecordsBySyncariId("syncariAcctId123"));
        when(stagedBatchRecordRepo.findByStagedBatchIdAndSyncariId(currentBatch.getCurrentBatchId(), "syncariAcctId123")).thenReturn(
                List.of(new StagedBatchRecord().setEntityData(new EntityData().setName("coreAccount").addValue("coreField1","SomeName").addValue("_source","myConnector")))
        );
        List<EntityData> t = List.of(new EntityData("coreAccount")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("corefield1", "Account Name")
                .addValue("corefield2", "ABC"));
        when(entityRepo.find(any(EntityDefinition.class), any(),any(PageCursor.class))).thenReturn(t);
        when(graphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(fieldGraph1));
        when(attributeDefinitionCache.findById(coreField1.getId())).thenReturn(Optional.of(coreField1));
        when(attributeDefinitionCache.findById(coreField2.getId())).thenReturn(Optional.of(coreField2));
        when(attributeDefinitionCache.findAllById(anyIterable())).thenReturn(List.of(coreField1, coreField2));
        //no id mapping present
        when(idMappingRepo.findBySyncariIds("coreAccount", Set.of("syncariAcctId123"))).thenReturn(List.of());
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+entityGraph.getCoreNode().getId(), Pair.of(new FunctionResult(t.get(0).addValue(coreField2.getApiName(),"BAD_RECORD"), ObjectType.VALUE),entityGraph.getCoreNode()));
            return null;
        }).when(evaluator).evaluate(eq(entityGraph.getConnectedSinks().findFirst().get()), eq(entityGraph),any(GraphContext.class),any(), any());
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+fieldGraph1.getCoreNode().getId(),Pair.of(new FunctionResult("Account Name",StringType.VALUE),fieldGraph1.getCoreNode()));
            ctx.getBatchActionContext().updateBatchContext(actionNode, null);
            return null;
        }).when(evaluator).evaluate(eq(fieldGraph1.getConnectedSinks().findFirst().get()), eq(fieldGraph1),any(GraphContext.class),any(), any());

        doAnswer((Answer<Void>) m -> null).when(evaluator).evaluate(eq(actionNode), eq(fieldGraph1),any(GraphContext.class),any(), any());
        when(mockDataServiceFactory.getDataService(connector.getMetadata())).thenReturn(salesforceService);
        SyncResponse response = new SyncResponse();
        when(salesforceService.create(any())).thenReturn(response);
        when(schemaService.getEntity(sinkEntity.getId())).thenReturn(sinkEntity);
        when(schemaService.getEntity(coreEntity.getId())).thenReturn(coreEntity);
        when(schemaService.getEntity(srcEntity.getId())).thenReturn(srcEntity);
        when(schemaService.refreshSynapseSchema(eq(sinkEntity.getConnectorId()), eq(sinkEntity), any())).thenReturn(List.of(sinkEntity));
        final GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph);
        graphContext.setSyncariEntity(coreEntity);

        saveToSink.execute(sinkEntity, context, graphContext);

        verify(connectorService).get("myConnectorId");
        verify(schemaService).getEntity(sinkEntity.getId());
        verify(currentBatch).getSyncariEntityName();
        
        ArgumentCaptor<GraphContext> contextCapture = ArgumentCaptor.forClass(GraphContext.class);
        ArgumentCaptor<SyncRequest> requestCapture = ArgumentCaptor.forClass(SyncRequest.class);

        verify(idMappingRepo).findBySyncariIds("coreAccount", Set.of("syncariAcctId123"));

        verify(evaluator).evaluate(eq(entityGraph.getConnectedSinks().findFirst().get()), eq(entityGraph), any(GraphContext.class), any(), any());
        verify(evaluator).evaluate(eq(fieldGraph1.getConnectedSinks().findFirst().get()), eq(fieldGraph1), any(GraphContext.class), any(), any());

        verify(evaluator).evaluate(eq(actionNode), eq(fieldGraph1), contextCapture.capture(), any(), any());
    }

    @Test
    public void getDefaultValue() {
        MappingNode currentNode = new MappingNode();
        currentNode.setId(ObjectId.get().toHexString());
        GraphContext context = new GraphContext().setCurrentNode(currentNode)
                .setCurrentBatch(new CurrentBatch(null).setSyncariEntityName("lead"));
        String syncariId = ObjectId.get().toHexString();
        EntityData lead = new EntityData("lead");
        lead.addValue("name", "unit-tests");
        lead.setId(syncariId);
        context.put("record", lead);

        final EntityDefinition entityDef = SchemaHelper.createEntityDefinition("customObj")
                .string("name")
                .id().getEntityDefinition();
        final AttributeDefinition sinkAttribute = entityDef.getFieldByName("name");
        final AttributeSinkNodeConfig sinkNodeConfig = new AttributeSinkNodeConfig();

        sinkNodeConfig.setDefaultValue("{{record.values.name}}");
        //doReturn("{{record.values.name}}").when(sinkNodeConfig).getDefaultValue();

        Object resolvedValue = saveToSink.getDefaultValue(sinkNodeConfig, sinkAttribute, context);
        assertEquals("unit-tests", resolvedValue.toString());

        sinkNodeConfig.setDefaultValue(null);

        resolvedValue = saveToSink.getDefaultValue(sinkNodeConfig, sinkAttribute, context);
        assertNull(resolvedValue);
        sinkAttribute.setMultiValueField(true);
        sinkNodeConfig.setDefaultValue("");
        assertEquals(List.of(), saveToSink.getDefaultValue(sinkNodeConfig, sinkAttribute, context));
    }

    @Test
    public void skipWatermarkUpdateOnError() {
        ViperContext context = new ViperContext(new Organization(), new Instance(), new User());

        final Connector connector = createConnector("my zendesk connector", "zendeskConnectorId");
        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());

        EntityDefinition sinkEntityDef = new EntityDefinition();
        sinkEntityDef.setConnectorId(connector.getId());
        sinkEntityDef.setApiName("Organization");
        sinkEntityDef.setDisplayName("Organization");
        sinkEntityDef.setStatus(Status.ACTIVE);
        sinkEntityDef.setId(ObjectId.get().toHexString());
        AttributeDefinition coreNameAttr = createAttribute("Name", new StringType(), coreEntityDef.getId());
        AttributeDefinition sinkNameAttr = createAttribute("Name", new StringType(), sinkEntityDef.getId());


        AttributeDefinition coreRevenueAttribute = createAttribute("Revenue", new DoubleType(), coreEntityDef.getId());
        AttributeDefinition coreQualityAttribute = createAttribute("Quality", new StringType(), coreEntityDef.getId());
        // Make revenue field as non-updateable
        AttributeDefinition sinkRevenueAttribute = createAttribute("Revenue", new DoubleType(), sinkEntityDef.getId()).setUpdatable(false);
        AttributeDefinition sinkQualityAttribute = createAttribute("Sink Quality", new StringType(), sinkEntityDef.getId());

        AttributeDefinition sinkCreateOnlyAttribute = createAttribute("CreateOnly", new StringType(), sinkEntityDef.getId()).setCreateOnly(true);
        AttributeDefinition coreCreateOnlyAttribute = createAttribute("CreateOnly", new StringType(), coreEntityDef.getId());

        MappingGraph entityGraph = createGraph(coreEntityDef.getId(), Scope.ENTITY);
        MappingNode coreNode = coreEntityNode(coreEntityDef, entityGraph);
        MappingNode sinkNode = coreSinkNode(sinkEntityDef, entityGraph);

        Edge coreToSink = edge(coreNode, sinkNode, entityGraph);
        coreToSink.setId(ObjectId.get().toHexString());

        MappingGraph nameAttrGraph = createGraph(coreNameAttr.getId(), Scope.ATTRIBUTE);
        MappingGraph revAttrGraph = createGraph(coreRevenueAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph qualiytAttrGraph = createGraph(coreQualityAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph createOnlytAttrGraph = createGraph(coreCreateOnlyAttribute.getId(), Scope.ATTRIBUTE);

        MappingNode coreNameAttrNode = coreAttributeNode(coreNameAttr, nameAttrGraph);
        MappingNode sinkNameAttrNode = sinkAttributeNode(sinkNameAttr, nameAttrGraph);
        Edge coreAttrToSink = edge(coreNameAttrNode, sinkNameAttrNode, nameAttrGraph);

        MappingNode coreQAttrNode = coreAttributeNode(coreQualityAttribute, qualiytAttrGraph);
        MappingNode sinkQAttrNode = sinkAttributeNode(sinkQualityAttribute, qualiytAttrGraph);
        Edge coreQToSinkQ = edge(coreQAttrNode, sinkQAttrNode, qualiytAttrGraph);

        var sinkRevAttrNode = sinkAttributeNode(sinkRevenueAttribute, revAttrGraph);
        var coreRevAttrNode = coreAttributeNode(coreRevenueAttribute, revAttrGraph);
        Edge revenueCoreToSink = edge(coreRevAttrNode, sinkRevAttrNode, revAttrGraph);

        var sinkCreateOnlyAttrNode = sinkAttributeNode(sinkCreateOnlyAttribute, createOnlytAttrGraph);
        var coreCreateOnlyAttrNode = coreAttributeNode(coreCreateOnlyAttribute, createOnlytAttrGraph);
        Edge createOnlyCoreToSink = edge(coreCreateOnlyAttrNode, sinkCreateOnlyAttrNode, createOnlytAttrGraph);

        sinkEntityDef.addField(sinkNameAttr);
        sinkEntityDef.addField(sinkRevenueAttribute);
        sinkEntityDef.addField(sinkQualityAttribute);
        sinkEntityDef.addField(sinkCreateOnlyAttribute);
        TransactionLog log = new TransactionLog()
                .setEntityName("account")
                .setEntityId(coreEntityDef.getId())
                .setNew(false)
                .setSyncariId("syncariAcctId123")
                .setOccurredAt(System.currentTimeMillis())
                .addSource("my zendesk connector", "my zendesk connector", "externalDefnitionId", "externalZDId", System.currentTimeMillis())
                .addChange(new FieldChange().setFieldId(coreNameAttr.getId()).setOldValue("Old Name").setNewValue("Account Name").setApiName("Name"))
                .addChange(new FieldChange().setFieldId(coreRevenueAttribute.getId()).setOldValue(100.0).setNewValue(300.0).setApiName("Revenue"));
        log.setId(ObjectId.get().toHexString());
        when(txLogRepo.findAllStream()).thenReturn(Stream.empty());
        when(transactionLogService.findMergesByBatchId("currentBatchId", Date.from(Instant.EPOCH), new PageCursor("", PageDirection.previous,500)))
                .thenReturn(new com.syncari.core.model.pagination.Page<TransactionLog>(new PageInfo(), new ArrayList<>()));
        when(txLogRepo.findByBatchId("currentBatchId", Pageable.unpaged())).thenReturn(Page.empty());
        when(txLogRepo.findByBatchIdAndSyncariIdIn("currentBatchId", List.of("syncariAcctId123"))).thenReturn(List.of());
        when(txLogRepo.findById(log.getId())).thenReturn(Optional.of(log));
        when(connectorService.get("my zendesk connector")).thenReturn(connector);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        when(currentBatch.getEntityBatch(sinkEntityDef)).thenReturn(new StagedBatch(sinkEntityDef.getApiName()).setConnectorId("my zendesk connector"));
        when(currentBatch.recordsBySyncariIdIterator()).thenReturn(recordsIter);
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(recordsIter.hasNext()).thenReturn(true, true, false);
        when(recordsIter.next()).thenReturn(new RecordsBySyncariId("syncariAcctId123"));

        List<EntityData> t = List.of(new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("Name", "Account Name")
                .addValue("Revenue", 300.0)
                .addValue("CreateOnly", "This should be discarded")
                .setLastTransactionLogId(log.getId())
                .setNew(false));
        when(entityRepo.find(any(EntityDefinition.class), any(), any(PageCursor.class))).thenReturn(t);
        when(graphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(nameAttrGraph, revAttrGraph, qualiytAttrGraph, createOnlytAttrGraph));
        when(attributeDefinitionCache.findById(coreNameAttr.getId())).thenReturn(Optional.of(coreNameAttr));
        when(attributeDefinitionCache.findById(coreRevenueAttribute.getId())).thenReturn(Optional.of(coreRevenueAttribute));
        when(attributeDefinitionCache.findById(coreQualityAttribute.getId())).thenReturn(Optional.of(coreQualityAttribute));
        when(attributeDefinitionCache.findById(coreCreateOnlyAttribute.getId())).thenReturn(Optional.of(coreCreateOnlyAttribute));
        when(attributeDefinitionCache.findAllById(anyIterable())).thenReturn(List.of(coreNameAttr, coreRevenueAttribute, coreQualityAttribute, coreCreateOnlyAttribute));
        //id mapping present to make it an update operation
        when(idMappingRepo.findBySyncariIds("account", Set.of("syncariAcctId123")))
                .thenReturn(List.of(new IdMapping().setEntityName("account").setSyncariId("syncariAcctId123").setMappings(List.of(
                        IdMapping.mapping(connector.getId(), "syncariAcctId123", sinkEntityDef.getId(), "syncariAcctId123")))));
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx = m.getArgument(2);
            ctx.put("output_" + coreNameAttrNode.getId(), new FunctionResult("Account Name", StringType.VALUE));
            return null;
        }).when(evaluator).evaluate(eq(sinkNameAttrNode), eq(nameAttrGraph), any(GraphContext.class), eq(n -> n.getType() == MappingNodeType.CORE_ATTRIBUTE), any());
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx = m.getArgument(2);
            ctx.put("output_" + coreRevAttrNode.getId(), new FunctionResult(300.0, DoubleType.VALUE));
            return null;
        }).when(evaluator).evaluate(eq(sinkRevAttrNode), eq(revAttrGraph), any(GraphContext.class), eq(n -> n.getType() == MappingNodeType.CORE_ATTRIBUTE), any());
        when(mockDataServiceFactory.getDataService(connector.getMetadata())).thenReturn(zendeskService);
        SyncResponse response = new SyncResponse();
        when(zendeskService.update(any())).thenThrow(new RetriableException(ErrorCodes.LOGIN_ERROR, "", ""));
        when(schemaService.getEntity(sinkEntityDef.getId())).thenReturn(sinkEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(schemaService.refreshSynapseSchema(eq(sinkEntityDef.getConnectorId()), eq(sinkEntityDef), any())).thenReturn(List.of(sinkEntityDef));
        final GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph);
        graphContext.setSyncariEntity(coreEntityDef);

        saveToSink.execute(sinkEntityDef, context, graphContext);
        assertFalse(context.updateWatermark());
    }

    @Test
    public void testSyncErrorMetric() {
        List<SyncError> syncErrors = List.of(
                new SyncError().setErrorDetails("Sync Error1").setErrorCode("ERROR1").setOperation(Operation.create.name()),
                new SyncError().setErrorDetails("Sync Error1").setErrorCode("ERROR1").setOperation(Operation.create.name()),
                new SyncError().setErrorDetails("Sync Error2").setErrorCode("ERROR2").setOperation(Operation.update.name()),
                new SyncError().setErrorDetails("Sync Error2").setErrorCode("ERROR2").setOperation(Operation.update.name()),
                new SyncError().setErrorDetails("Sync Error3").setErrorCode("ERROR3").setOperation(Operation.update.name())
        );

        // protected Stream<EntitySyncErrorMetric> getSyncErrorMetrics(List<SyncError> syncErrors, String nodeId, String externalEntityId, int insertCount, int updateCount, int deleteCount) {
        List<EntitySyncErrorMetric> errorMetrics = saveToSink.getSyncErrorMetrics(syncErrors, "nodeId1", "externalEntityId1", 5, 3, 0)
                .collect(Collectors.toList());

        assertEquals(3, errorMetrics.size());
        assertEquals(2, errorMetrics.stream().filter(e -> e.getErrorMessage().equals("ERROR1")).findFirst().get().getErrorCount());
        assertEquals(5, errorMetrics.stream().filter(e -> e.getErrorMessage().equals("ERROR1")).findFirst().get().getTotalCount());
        assertEquals("nodeId1", errorMetrics.stream().filter(e -> e.getErrorMessage().equals("ERROR1")).findFirst().get().getNodeId());
        assertEquals("externalEntityId1", errorMetrics.stream().filter(e -> e.getErrorMessage().equals("ERROR1")).findFirst().get().getTargetId());
        //assertEquals();

        assertEquals(2, errorMetrics.stream().filter(e -> e.getErrorMessage().equals("ERROR2")).findFirst().get().getErrorCount());
        assertEquals(3, errorMetrics.stream().filter(e -> e.getErrorMessage().equals("ERROR2")).findFirst().get().getTotalCount());
        assertEquals("nodeId1", errorMetrics.stream().filter(e -> e.getErrorMessage().equals("ERROR2")).findFirst().get().getNodeId());
        assertEquals("externalEntityId1", errorMetrics.stream().filter(e -> e.getErrorMessage().equals("ERROR2")).findFirst().get().getTargetId());


        assertEquals(1, errorMetrics.stream().filter(e -> e.getErrorMessage().equals("ERROR3")).findFirst().get().getErrorCount());
        assertEquals(3, errorMetrics.stream().filter(e -> e.getErrorMessage().equals("ERROR3")).findFirst().get().getTotalCount());
        assertEquals("nodeId1", errorMetrics.stream().filter(e -> e.getErrorMessage().equals("ERROR3")).findFirst().get().getNodeId());
        assertEquals("externalEntityId1", errorMetrics.stream().filter(e -> e.getErrorMessage().equals("ERROR3")).findFirst().get().getTargetId());
    }
    public void testTransactionsLogBySyncariIds() {

        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());

        List<String> syncariIds = List.of("syncariId1", "syncariId2", "syncariId3");

        List<TransactionLog> transactionLogs = List.of(
                new TransactionLog().setSyncariId(syncariIds.get(0)).setOperation(Operation.create),
                new TransactionLog().setSyncariId(syncariIds.get(0)).setOperation(Operation.update),
                new TransactionLog().setSyncariId(syncariIds.get(0)).setOperation(Operation.delete),
                new TransactionLog().setSyncariId(syncariIds.get(1)).setOperation(Operation.merge),
                new TransactionLog().setSyncariId(syncariIds.get(1)).setOperation(Operation.disconnect),
                new TransactionLog().setSyncariId(syncariIds.get(1)).setOperation(Operation.delete),
                new TransactionLog().setSyncariId(syncariIds.get(2)).setOperation(Operation.create),
                new TransactionLog().setSyncariId(syncariIds.get(2)).setOperation(Operation.update),
                new TransactionLog().setSyncariId(syncariIds.get(2)).setOperation(Operation.update),
                new TransactionLog().setSyncariId(syncariIds.get(2)).setOperation(Operation.update)
        );

        Watermark watermark = new Watermark().setStart(Instant.now().toEpochMilli());
        when(transactionLogService.findTransactions(coreEntityDef, syncariIds, watermark.getStart())).thenReturn(transactionLogs);

        Map<String, List<TransactionLog>> txnLogsBySyncariId = saveToSink.getTxnLogsBySyncariId(coreEntityDef, syncariIds, watermark);
        assertEquals(2, txnLogsBySyncariId.size());
        assertEquals(2, txnLogsBySyncariId.get(syncariIds.get(0)).size());
        assertEquals(4, txnLogsBySyncariId.get(syncariIds.get(2)).size());
    }

    @Test
    public void syncOnlyOnTxnLogWithTxnLog() {

        ViperContext context = new ViperContext(new Organization(), new Instance(), new User());

        final Connector connector = createConnector("my zendesk connector", "zendeskConnectorId");
        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());

        EntityDefinition sinkEntityDef = new EntityDefinition();
        sinkEntityDef.setConnectorId(connector.getId());
        sinkEntityDef.setApiName("Organization");
        sinkEntityDef.setDisplayName("Organization");
        sinkEntityDef.setStatus(Status.ACTIVE);
        sinkEntityDef.setId(ObjectId.get().toHexString());
        AttributeDefinition coreNameAttr = createAttribute("Name", new StringType(), coreEntityDef.getId());
        AttributeDefinition sinkNameAttr = createAttribute("Name", new StringType(), sinkEntityDef.getId());


        AttributeDefinition coreRevenueAttribute = createAttribute("Revenue", new DoubleType(), coreEntityDef.getId());
        AttributeDefinition coreQualityAttribute = createAttribute("Quality", new StringType(), coreEntityDef.getId());
        // Make revenue field as non-updateable
        AttributeDefinition sinkRevenueAttribute = createAttribute("Revenue", new DoubleType(), sinkEntityDef.getId()).setUpdatable(false);
        AttributeDefinition sinkQualityAttribute = createAttribute("Sink Quality", new StringType(), sinkEntityDef.getId());

        AttributeDefinition sinkCreateOnlyAttribute = createAttribute("CreateOnly", new StringType(), sinkEntityDef.getId()).setCreateOnly(true);
        AttributeDefinition coreCreateOnlyAttribute = createAttribute("CreateOnly", new StringType(), coreEntityDef.getId());

        MappingGraph entityGraph = createGraph(coreEntityDef.getId(),Scope.ENTITY);
        MappingNode coreNode = coreEntityNode(coreEntityDef, entityGraph);
        MappingNode sinkNode = coreSinkNode(sinkEntityDef,entityGraph);
        EntitySinkNodeConfig sinkNodeConfig = sinkNode.getTypedConfiguration();
        sinkNodeConfig.setSyncOnTxnLog(true);

        Edge coreToSink = edge(coreNode, sinkNode, entityGraph);
        coreToSink.setId(ObjectId.get().toHexString());

        MappingGraph nameAttrGraph = createGraph(coreNameAttr.getId(), Scope.ATTRIBUTE);
        MappingGraph revAttrGraph = createGraph(coreRevenueAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph qualiytAttrGraph = createGraph(coreQualityAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph createOnlytAttrGraph = createGraph(coreCreateOnlyAttribute.getId(), Scope.ATTRIBUTE);

        MappingNode coreNameAttrNode = coreAttributeNode(coreNameAttr,nameAttrGraph);
        MappingNode sinkNameAttrNode = sinkAttributeNode(sinkNameAttr,nameAttrGraph);
        Edge coreAttrToSink = edge(coreNameAttrNode, sinkNameAttrNode, nameAttrGraph);

        MappingNode coreQAttrNode = coreAttributeNode(coreQualityAttribute,qualiytAttrGraph);
        MappingNode sinkQAttrNode = sinkAttributeNode(sinkQualityAttribute,qualiytAttrGraph);
        Edge coreQToSinkQ = edge(coreQAttrNode, sinkQAttrNode,qualiytAttrGraph);

        var sinkRevAttrNode = sinkAttributeNode(sinkRevenueAttribute, revAttrGraph);
        var coreRevAttrNode = coreAttributeNode(coreRevenueAttribute, revAttrGraph);
        Edge revenueCoreToSink = edge(coreRevAttrNode, sinkRevAttrNode, revAttrGraph);

        var sinkCreateOnlyAttrNode = sinkAttributeNode(sinkCreateOnlyAttribute, createOnlytAttrGraph);
        var coreCreateOnlyAttrNode = coreAttributeNode(coreCreateOnlyAttribute, createOnlytAttrGraph);
        Edge createOnlyCoreToSink = edge(coreCreateOnlyAttrNode, sinkCreateOnlyAttrNode, createOnlytAttrGraph);

        sinkEntityDef.addField(sinkNameAttr);
        sinkEntityDef.addField(sinkRevenueAttribute);
        sinkEntityDef.addField(sinkQualityAttribute);
        sinkEntityDef.addField(sinkCreateOnlyAttribute);
        TransactionLog log = new TransactionLog()
                .setEntityName("account")
                .setOperation(Operation.update)
                .setEntityId(coreEntityDef.getId())
                .setNew(false)
                .setSyncariId("syncariAcctId123")
                .setOccurredAt(System.currentTimeMillis())
                .addSource("my zendesk connector", "my zendesk connector", "externalDefnitionId", "externalZDId",System.currentTimeMillis())
                .addChange(new FieldChange().setFieldId(coreNameAttr.getId()).setOldValue("Old Name").setNewValue("Account Name").setApiName("Name"))
                .addChange(new FieldChange().setFieldId(coreRevenueAttribute.getId()).setOldValue(100.0).setNewValue(300.0).setApiName("Revenue"));
        log.setId(ObjectId.get().toHexString());
        when(schemaService.refreshSynapseSchema(eq(sinkEntityDef.getConnectorId()), eq(sinkEntityDef), any()))
                .thenReturn(List.of(sinkEntityDef));


        when(txLogRepo.findAllStream()).thenReturn(Stream.empty());
        when(transactionLogService.findMergesByBatchId("currentBatchId", Date.from(Instant.EPOCH), new PageCursor("", PageDirection.previous,500)))
                .thenReturn(new com.syncari.core.model.pagination.Page<TransactionLog>(new PageInfo(), new ArrayList<>()));
        when(transactionLogService.findTransactions(coreEntityDef, List.of("syncariAcctId123"), 0)).thenReturn(List.of(log));
        when(txLogRepo.findByBatchId("currentBatchId", Pageable.unpaged())).thenReturn(Page.empty());
        when(txLogRepo.findByBatchIdAndSyncariIdIn("currentBatchId", List.of("syncariAcctId123"))).thenReturn(List.of());
        when(txLogRepo.findById(log.getId())).thenReturn(Optional.of(log));
        when(connectorService.get("my zendesk connector")).thenReturn(connector);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        when(currentBatch.getEntityBatch(sinkEntityDef)).thenReturn(new StagedBatch(sinkEntityDef.getApiName()).setConnectorId("my zendesk connector"));
        when(currentBatch.recordsBySyncariIdIterator()).thenReturn(recordsIter);
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(recordsIter.hasNext()).thenReturn(true, true, false);
        when(recordsIter.next()).thenReturn(new RecordsBySyncariId("syncariAcctId123"));
        when(transactionLogService.findMergesByBatchId("currentBatchId", Date.from(Instant.EPOCH), new PageCursor("", PageDirection.previous,500)))
                .thenReturn(new com.syncari.core.model.pagination.Page<TransactionLog>(new PageInfo(), new ArrayList<>()));
        when(txLogRepo.findByBatchId("currentBatchId", Pageable.unpaged())).thenReturn(Page.empty());
        when(txLogRepo.findByBatchIdAndSyncariIdIn("currentBatchId", List.of("syncariAcctId123"))).thenReturn(List.of());
        when(txLogRepo.findById(log.getId())).thenReturn(Optional.of(log));
        when(connectorService.get("my zendesk connector")).thenReturn(connector);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        when(currentBatch.getEntityBatch(sinkEntityDef)).thenReturn(new StagedBatch(sinkEntityDef.getApiName()).setConnectorId("my zendesk connector"));
        when(currentBatch.recordsBySyncariIdIterator()).thenReturn(recordsIter);
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        //when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(recordsIter.hasNext()).thenReturn(true, true, false);
        when(recordsIter.next()).thenReturn(new RecordsBySyncariId("syncariAcctId123"));

        List<EntityData> t = List.of(new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("Name", "Account Name")
                .addValue("Revenue", 300.0)
                .addValue("CreateOnly", "This should be discarded")
                .setLastTransactionLogId(log.getId())
                .setNew(false));
        when(entityRepo.find(any(EntityDefinition.class), any(),any(PageCursor.class))).thenReturn(t);
        when(graphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(nameAttrGraph,revAttrGraph, qualiytAttrGraph, createOnlytAttrGraph));
        when(attributeDefinitionCache.findById(coreNameAttr.getId())).thenReturn(Optional.of(coreNameAttr));
        when(attributeDefinitionCache.findById(coreRevenueAttribute.getId())).thenReturn(Optional.of(coreRevenueAttribute));
        when(attributeDefinitionCache.findById(coreQualityAttribute.getId())).thenReturn(Optional.of(coreQualityAttribute));
        when(attributeDefinitionCache.findById(coreCreateOnlyAttribute.getId())).thenReturn(Optional.of(coreCreateOnlyAttribute));
        when(attributeDefinitionCache.findAllById(anyIterable())).thenReturn(List.of(coreNameAttr, coreRevenueAttribute, coreQualityAttribute, coreCreateOnlyAttribute));
        //id mapping present to make it an update operation
        when(idMappingRepo.findBySyncariIds("account", Set.of("syncariAcctId123")))
                .thenReturn(List.of(new IdMapping().setEntityName("account").setSyncariId("syncariAcctId123").setMappings(List.of(
                        IdMapping.mapping(connector.getId(), "syncariAcctId123", sinkEntityDef.getId(), "syncariAcctId123")))));
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+coreNameAttrNode.getId(),new FunctionResult("Account Name",StringType.VALUE));
            return null;
        }).when(evaluator).evaluate(eq(sinkNameAttrNode), eq(nameAttrGraph),any(GraphContext.class),eq(n->n.getType()== MappingNodeType.CORE_ATTRIBUTE), any());
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+coreRevAttrNode.getId(),new FunctionResult(300.0,DoubleType.VALUE));
            return null;
        }).when(evaluator).evaluate(eq(sinkRevAttrNode), eq(revAttrGraph),any(GraphContext.class),eq(n->n.getType()== MappingNodeType.CORE_ATTRIBUTE), any());
        when(mockDataServiceFactory.getDataService(connector.getMetadata())).thenReturn(zendeskService);
        SyncResponse response = new SyncResponse();
        response.setResults(List.of(new Result(true, "1", "syncariAcctId123")));
        when(zendeskService.update(any())).thenReturn(response);
        when(schemaService.getEntity(sinkEntityDef.getId())).thenReturn(sinkEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);

        final GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph);
        graphContext.setSyncariEntity(coreEntityDef);
        saveToSink.execute(sinkEntityDef, context, graphContext);

        verify(connectorService).get("my zendesk connector");
        verify(schemaService).getEntity(sinkEntityDef.getId());
        verify(currentBatch, atLeastOnce()).getSyncariEntityName();

        verify(transactionLogService).findMergesByBatchId(eq("currentBatchId"), any(), eq(new PageCursor("", PageDirection.previous, 500)));
        //verify(txLogRepo).findByBatchId("currentBatchId", Pageable.unpaged());
        ArgumentCaptor<Map<String, Object>> contextCapture = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<SyncRequest> requestCapture = ArgumentCaptor.forClass(SyncRequest.class);
        ArgumentCaptor<List<FieldChange>> fieldChangeCapture = ArgumentCaptor.forClass(List.class);

        verify(idMappingRepo).findBySyncariIds("account", Set.of("syncariAcctId123"));
        //verify(evaluator, times(3)).evaluate(any(MappingNode.class), any(MappingGraph.class), any(GraphContext.class),any(Predicate.class));
        verify(mockDataServiceFactory).getDataService(connector.getMetadata());

        verify(zendeskService, never()).create(requestCapture.capture());
        verify(zendeskService).update(requestCapture.capture());
    }

    @Test
    public void testExternalDeleteTxnsSyncariDelete() {

        ViperContext context = new ViperContext(new Organization(), new Instance(), new User());

        Connector connector = createConnector("my zendesk connector", "zendeskConnectorId");

        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());

        EntityDefinition sinkEntityDef = new EntityDefinition();
        sinkEntityDef.setConnectorId(connector.getId());
        sinkEntityDef.setApiName("Organization");
        sinkEntityDef.setDisplayName("Organization");
        sinkEntityDef.setStatus(Status.ACTIVE);
        sinkEntityDef.setId(ObjectId.get().toHexString());
        AttributeDefinition coreNameAttr = createAttribute("Name", new StringType(), coreEntityDef.getId());
        AttributeDefinition sinkNameAttr = createAttribute("Name", new StringType(), sinkEntityDef.getId());

        AttributeDefinition coreRevenueAttribute = createAttribute("Revenue", new DoubleType(), coreEntityDef.getId());
        AttributeDefinition coreQualityAttribute = createAttribute("Quality", new StringType(), coreEntityDef.getId());
        // Make revenue field as non-updateable
        AttributeDefinition sinkRevenueAttribute = createAttribute("Revenue", new DoubleType(), sinkEntityDef.getId()).setUpdatable(false);
        AttributeDefinition sinkQualityAttribute = createAttribute("Sink Quality", new StringType(), sinkEntityDef.getId());

        AttributeDefinition sinkCreateOnlyAttribute = createAttribute("CreateOnly", new StringType(), sinkEntityDef.getId()).setCreateOnly(true);
        AttributeDefinition coreCreateOnlyAttribute = createAttribute("CreateOnly", new StringType(), coreEntityDef.getId());

        MappingGraph entityGraph = createGraph(coreEntityDef.getId(),Scope.ENTITY);
        MappingNode coreNode = coreEntityNode(coreEntityDef, entityGraph);
        MappingNode sinkNode = coreSinkNode(sinkEntityDef,entityGraph);
        EntitySinkNodeConfig sinkNodeConfig = sinkNode.getTypedConfiguration();

        Edge coreToSink = edge(coreNode, sinkNode, entityGraph);
        coreToSink.setId(ObjectId.get().toHexString());

        MappingGraph nameAttrGraph = createGraph(coreNameAttr.getId(), Scope.ATTRIBUTE);
        MappingGraph revAttrGraph = createGraph(coreRevenueAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph qualiytAttrGraph = createGraph(coreQualityAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph createOnlytAttrGraph = createGraph(coreCreateOnlyAttribute.getId(), Scope.ATTRIBUTE);

        MappingNode coreNameAttrNode = coreAttributeNode(coreNameAttr,nameAttrGraph);
        MappingNode sinkNameAttrNode = sinkAttributeNode(sinkNameAttr,nameAttrGraph);
        Edge coreAttrToSink = edge(coreNameAttrNode, sinkNameAttrNode, nameAttrGraph);

        MappingNode coreQAttrNode = coreAttributeNode(coreQualityAttribute,qualiytAttrGraph);
        MappingNode sinkQAttrNode = sinkAttributeNode(sinkQualityAttribute,qualiytAttrGraph);
        Edge coreQToSinkQ = edge(coreQAttrNode, sinkQAttrNode,qualiytAttrGraph);

        var sinkRevAttrNode = sinkAttributeNode(sinkRevenueAttribute, revAttrGraph);
        var coreRevAttrNode = coreAttributeNode(coreRevenueAttribute, revAttrGraph);
        Edge revenueCoreToSink = edge(coreRevAttrNode, sinkRevAttrNode, revAttrGraph);

        var sinkCreateOnlyAttrNode = sinkAttributeNode(sinkCreateOnlyAttribute, createOnlytAttrGraph);
        var coreCreateOnlyAttrNode = coreAttributeNode(coreCreateOnlyAttribute, createOnlytAttrGraph);
        Edge createOnlyCoreToSink = edge(coreCreateOnlyAttrNode, sinkCreateOnlyAttrNode, createOnlytAttrGraph);

        sinkEntityDef.addField(sinkNameAttr);
        sinkEntityDef.addField(sinkRevenueAttribute);
        sinkEntityDef.addField(sinkQualityAttribute);
        sinkEntityDef.addField(sinkCreateOnlyAttribute);
        when(connectorService.get("my zendesk connector")).thenReturn(connector);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        when(currentBatch.getEntityBatch(sinkEntityDef)).thenReturn(new StagedBatch(sinkEntityDef.getApiName()).setConnectorId("my zendesk connector"));
        when(currentBatch.recordsBySyncariIdIterator()).thenReturn(recordsIter);
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(recordsIter.hasNext()).thenReturn(true, true, false);
        when(recordsIter.next()).thenReturn(new RecordsBySyncariId("syncariAcctId123"));

        List<EntityData> t = List.of(new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("Name", "Account Name")
                .addValue("Revenue", 300.0)
                .addValue("CreateOnly", "This should be discarded")
                .setNew(false).setDeleted(true));

        when(entityRepo.find(any(EntityDefinition.class), any(),any(PageCursor.class))).thenReturn(t);
        when(graphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(nameAttrGraph,revAttrGraph, qualiytAttrGraph, createOnlytAttrGraph));
        when(attributeDefinitionCache.findById(coreNameAttr.getId())).thenReturn(Optional.of(coreNameAttr));
        when(attributeDefinitionCache.findById(coreRevenueAttribute.getId())).thenReturn(Optional.of(coreRevenueAttribute));
        when(attributeDefinitionCache.findById(coreQualityAttribute.getId())).thenReturn(Optional.of(coreQualityAttribute));
        when(attributeDefinitionCache.findById(coreCreateOnlyAttribute.getId())).thenReturn(Optional.of(coreCreateOnlyAttribute));
        when(attributeDefinitionCache.findAllById(anyIterable())).thenReturn(List.of(coreNameAttr, coreRevenueAttribute, coreQualityAttribute, coreCreateOnlyAttribute));
        //id mapping present to make it an update operation
        when(idMappingRepo.findBySyncariIds("account", Set.of("syncariAcctId123")))
                .thenReturn(List.of(new IdMapping().setEntityName("account").setSyncariId("syncariAcctId123").setMappings(List.of(
                        IdMapping.mapping(connector.getId(), "syncariAcctId123", sinkEntityDef.getId(), "syncariAcctId123")))));
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+coreNameAttrNode.getId(),new FunctionResult("Account Name",StringType.VALUE));
            return null;
        }).when(evaluator).evaluate(eq(sinkNameAttrNode), eq(nameAttrGraph),any(GraphContext.class),eq(n->n.getType()== MappingNodeType.CORE_ATTRIBUTE), any());
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+coreRevAttrNode.getId(),new FunctionResult(300.0,DoubleType.VALUE));
            return null;
        }).when(evaluator).evaluate(eq(sinkRevAttrNode), eq(revAttrGraph),any(GraphContext.class),eq(n->n.getType()== MappingNodeType.CORE_ATTRIBUTE), any());
        when(mockDataServiceFactory.getDataService(connector.getMetadata())).thenReturn(zendeskService);
        SyncResponse response = new SyncResponse();
        response.setResults(List.of(new Result(true, "1", "syncariAcctId123")));
        when(zendeskService.update(any())).thenReturn(response);
        when(schemaService.getEntity(sinkEntityDef.getId())).thenReturn(sinkEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(zendeskService.delete(any())).thenReturn(new SyncResponse(true).setResults(List.of(new Result(true, "1", "syncariAcctId123"))));
        when(schemaService.refreshSynapseSchema(eq(sinkEntityDef.getConnectorId()), eq(sinkEntityDef), any())).thenReturn(List.of(sinkEntityDef));
        final GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph);
        graphContext.setSyncariEntity(coreEntityDef);

        saveToSink.execute(sinkEntityDef, context, graphContext);

        ArgumentCaptor<List<TransactionLog>> txnLogs = ArgumentCaptor.forClass(List.class);
        verify(transactionLogService, times(1)).log(txnLogs.capture());
        assertEquals(1, txnLogs.getValue().size());
        assertEquals("syncariAcctId123", txnLogs.getValue().get(0).getSyncariId());
        assertEquals(Operation.external_delete, txnLogs.getValue().get(0).getOperation());
        assertEquals(true, ((ExternalDeleteInfo)txnLogs.getValue().get(0).getAdditionalInfo().get("deleteInfo")).isSyncariDeleted());
        assertEquals("Organization", ((ExternalDeleteInfo)txnLogs.getValue().get(0).getAdditionalInfo().get("deleteInfo")).getDeletedId().getApiName());
        assertEquals("my zendesk connector", ((ExternalDeleteInfo)txnLogs.getValue().get(0).getAdditionalInfo().get("deleteInfo")).getDeletedId().getConnectorName());
        assertEquals("1", ((ExternalDeleteInfo)txnLogs.getValue().get(0).getAdditionalInfo().get("deleteInfo")).getDeletedId().getId());
        assertTrue(((ExternalDeleteInfo)txnLogs.getValue().get(0).getAdditionalInfo().get("deleteInfo")).getDisconnectedSources().isEmpty());

        Connector salesforceConn = createConnector("my salesforce connector", ObjectId.get().toHexString(), "salesforceConnectorId");


        EntityDefinition sinkEntityDef1 = new EntityDefinition();
        sinkEntityDef1.setConnectorId(salesforceConn.getId());
        sinkEntityDef1.setApiName("Company");
        sinkEntityDef1.setDisplayName("Company");
        sinkEntityDef1.setStatus(Status.ACTIVE);
        sinkEntityDef1.setId(ObjectId.get().toHexString());


        MappingNode sinkNode1 = coreSinkNode(sinkEntityDef1,entityGraph);
        Edge coreToSink1 = edge(coreNode, sinkNode1, entityGraph);
        coreToSink1.setId(ObjectId.get().toHexString());
        AttributeDefinition sink1NameAttr = createAttribute("Name", new StringType(), sinkEntityDef1.getId());
        AttributeDefinition sink1RevenueAttribute = createAttribute("Revenue", new DoubleType(), sinkEntityDef1.getId()).setUpdatable(false);
        AttributeDefinition sink1QualityAttribute = createAttribute("Sink Quality", new StringType(), sinkEntityDef1.getId());

        MappingNode sink1NameAttrNode = sinkAttributeNode(sink1NameAttr,nameAttrGraph);
        edge(coreNameAttrNode, sink1NameAttrNode, nameAttrGraph);
        MappingNode sink1QAttrNode = sinkAttributeNode(sink1QualityAttribute,qualiytAttrGraph);
        edge(coreQAttrNode, sink1QAttrNode,qualiytAttrGraph);

        var sink1RevAttrNode = sinkAttributeNode(sink1RevenueAttribute, revAttrGraph);
        edge(coreRevAttrNode, sink1RevAttrNode, revAttrGraph);

        sinkEntityDef1.addField(sink1NameAttr);
        sinkEntityDef1.addField(sink1RevenueAttribute);
        sinkEntityDef1.addField(sink1QualityAttribute);
        when(connectorService.get(salesforceConn.getId())).thenReturn(salesforceConn);
        when(connectorService.refreshAuthentication(salesforceConn)).thenReturn(salesforceConn);
        when(currentBatch.getEntityBatch(sinkEntityDef1)).thenReturn(new StagedBatch(sinkEntityDef1.getApiName()).setConnectorId("my salesforce connector"));
        when(currentBatch.recordsBySyncariIdIterator()).thenReturn(recordsIter);
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(recordsIter.hasNext()).thenReturn(true, true, false);
        when(recordsIter.next()).thenReturn(new RecordsBySyncariId("syncariAcctId123"));

        when(idMappingRepo.findBySyncariIds("account", Set.of("syncariAcctId123")))
                .thenReturn(List.of(new IdMapping().setEntityName("account").setSyncariId("syncariAcctId123").setMappings(List.of(
                        IdMapping.mapping(salesforceConn.getId(), "salesforceId", sinkEntityDef1.getId(), "syncariAcctId123")))));

        when(mockDataServiceFactory.getDataService(salesforceConn.getMetadata())).thenReturn(salesforceService);
        response = new SyncResponse();
        response.setResults(List.of(new Result(true, "salesforceId", "syncariAcctId123")));
        when(schemaService.getEntity(sinkEntityDef1.getId())).thenReturn(sinkEntityDef1);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(salesforceService.delete(any())).thenReturn(new SyncResponse(true).setResults(List.of(new Result(true, "salesforceId", "syncariAcctId123"))));
        when(schemaService.refreshSynapseSchema(eq(sinkEntityDef.getConnectorId()), eq(sinkEntityDef), any())).thenReturn(List.of(sinkEntityDef));
        when(schemaService.refreshSynapseSchema(eq(sinkEntityDef1.getConnectorId()), eq(sinkEntityDef1), any())).thenReturn(List.of(sinkEntityDef1));
        graphContext.setSyncariEntity(coreEntityDef);

        saveToSink.execute(sinkEntityDef, context, graphContext);

        final GraphContext graphContext1 = new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph);
        graphContext1.setSyncariEntity(coreEntityDef);
        saveToSink.execute(sinkEntityDef1, context, graphContext1);
        txnLogs = ArgumentCaptor.forClass(List.class);
        verify(transactionLogService, times(2)).log(txnLogs.capture());
        assertEquals(1, txnLogs.getValue().size());
        assertEquals("syncariAcctId123", txnLogs.getValue().get(0).getSyncariId());
        assertEquals(Operation.external_delete, txnLogs.getValue().get(0).getOperation());
        assertEquals(true, ((ExternalDeleteInfo)txnLogs.getValue().get(0).getAdditionalInfo().get("deleteInfo")).isSyncariDeleted());
        assertEquals("Company", ((ExternalDeleteInfo)txnLogs.getValue().get(0).getAdditionalInfo().get("deleteInfo")).getDeletedId().getApiName());
        assertEquals("my salesforce connector", ((ExternalDeleteInfo)txnLogs.getValue().get(0).getAdditionalInfo().get("deleteInfo")).getDeletedId().getConnectorName());
        assertEquals("salesforceId", ((ExternalDeleteInfo)txnLogs.getValue().get(0).getAdditionalInfo().get("deleteInfo")).getDeletedId().getId());
        assertTrue(((ExternalDeleteInfo)txnLogs.getValue().get(0).getAdditionalInfo().get("deleteInfo")).getDisconnectedSources().isEmpty());
    }

    @Test
    public void testExternalDeleteTxnSourceDisconnect() {

        ViperContext context = new ViperContext(new Organization(), new Instance(), new User());

        Connector connector = createConnector("my zendesk connector", "zendeskConnectorId");
        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());

        EntityDefinition sinkEntityDef = new EntityDefinition();
        sinkEntityDef.setConnectorId(connector.getId());
        sinkEntityDef.setApiName("Organization");
        sinkEntityDef.setDisplayName("Organization");
        sinkEntityDef.setStatus(Status.ACTIVE);
        sinkEntityDef.setId(ObjectId.get().toHexString());
        AttributeDefinition coreNameAttr = createAttribute("Name", new StringType(), coreEntityDef.getId());
        AttributeDefinition sinkNameAttr = createAttribute("Name", new StringType(), sinkEntityDef.getId());

        AttributeDefinition coreRevenueAttribute = createAttribute("Revenue", new DoubleType(), coreEntityDef.getId());
        AttributeDefinition coreQualityAttribute = createAttribute("Quality", new StringType(), coreEntityDef.getId());
        // Make revenue field as non-updateable
        AttributeDefinition sinkRevenueAttribute = createAttribute("Revenue", new DoubleType(), sinkEntityDef.getId()).setUpdatable(false);
        AttributeDefinition sinkQualityAttribute = createAttribute("Sink Quality", new StringType(), sinkEntityDef.getId());

        AttributeDefinition sinkCreateOnlyAttribute = createAttribute("CreateOnly", new StringType(), sinkEntityDef.getId()).setCreateOnly(true);
        AttributeDefinition coreCreateOnlyAttribute = createAttribute("CreateOnly", new StringType(), coreEntityDef.getId());

        MappingGraph entityGraph = createGraph(coreEntityDef.getId(),Scope.ENTITY);
        MappingNode coreNode = coreEntityNode(coreEntityDef, entityGraph);
        MappingNode sinkNode = coreSinkNode(sinkEntityDef,entityGraph);
        EntitySinkNodeConfig sinkNodeConfig = sinkNode.getTypedConfiguration();

        Edge coreToSink = edge(coreNode, sinkNode, entityGraph);
        coreToSink.setId(ObjectId.get().toHexString());

        MappingGraph nameAttrGraph = createGraph(coreNameAttr.getId(), Scope.ATTRIBUTE);
        MappingGraph revAttrGraph = createGraph(coreRevenueAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph qualiytAttrGraph = createGraph(coreQualityAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph createOnlytAttrGraph = createGraph(coreCreateOnlyAttribute.getId(), Scope.ATTRIBUTE);

        MappingNode coreNameAttrNode = coreAttributeNode(coreNameAttr,nameAttrGraph);
        MappingNode sinkNameAttrNode = sinkAttributeNode(sinkNameAttr,nameAttrGraph);

        MappingNode coreQAttrNode = coreAttributeNode(coreQualityAttribute,qualiytAttrGraph);

        var sinkRevAttrNode = sinkAttributeNode(sinkRevenueAttribute, revAttrGraph);
        var coreRevAttrNode = coreAttributeNode(coreRevenueAttribute, revAttrGraph);

        var sinkCreateOnlyAttrNode = sinkAttributeNode(sinkCreateOnlyAttribute, createOnlytAttrGraph);
        var coreCreateOnlyAttrNode = coreAttributeNode(coreCreateOnlyAttribute, createOnlytAttrGraph);

        sinkEntityDef.addField(sinkNameAttr);
        sinkEntityDef.addField(sinkRevenueAttribute);
        sinkEntityDef.addField(sinkQualityAttribute);
        sinkEntityDef.addField(sinkCreateOnlyAttribute);
        when(connectorService.get("my zendesk connector")).thenReturn(connector);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        when(connectorService.find(connector.getId(), false)).thenReturn(Optional.of(connector));
        when(currentBatch.getEntityBatch(sinkEntityDef)).thenReturn(new StagedBatch(sinkEntityDef.getApiName()).setConnectorId("my zendesk connector"));
        when(currentBatch.recordsBySyncariIdIterator()).thenReturn(recordsIter);
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(recordsIter.hasNext()).thenReturn(true, true, false);
        when(recordsIter.next()).thenReturn(new RecordsBySyncariId("syncariAcctId123"));

        List<EntityData> t = List.of(new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("Name", "Account Name")
                .addValue("Revenue", 300.0)
                .addValue("CreateOnly", "This should be discarded")
                .setNew(false).setDeleted(true));

        when(entityRepo.find(any(EntityDefinition.class), any(),any(PageCursor.class))).thenReturn(t);
        when(graphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(nameAttrGraph,revAttrGraph, qualiytAttrGraph, createOnlytAttrGraph));
        when(attributeDefinitionCache.findById(coreNameAttr.getId())).thenReturn(Optional.of(coreNameAttr));
        when(attributeDefinitionCache.findById(coreRevenueAttribute.getId())).thenReturn(Optional.of(coreRevenueAttribute));
        when(attributeDefinitionCache.findById(coreQualityAttribute.getId())).thenReturn(Optional.of(coreQualityAttribute));
        when(attributeDefinitionCache.findById(coreCreateOnlyAttribute.getId())).thenReturn(Optional.of(coreCreateOnlyAttribute));
        when(attributeDefinitionCache.findAllById(anyIterable())).thenReturn(List.of(coreNameAttr, coreRevenueAttribute, coreQualityAttribute, coreCreateOnlyAttribute));

        Connector salesforceConn = new Connector("my salesforce connector", "salesforceConnectorId",
                "https://someendpoint");
        salesforceConn.setId(ObjectId.get().toHexString());
        salesforceConn.setMetadata(new ConnectorMetadata("salesforceConnectorId"));

        salesforceConn.setStatus(ConnectorStatus.ACTIVE);
        EntityDefinition sinkEntityDef1 = new EntityDefinition();
        sinkEntityDef1.setConnectorId(salesforceConn.getId());
        sinkEntityDef1.setApiName("Company");
        sinkEntityDef1.setDisplayName("Company");
        sinkEntityDef1.setStatus(Status.ACTIVE);
        sinkEntityDef1.setId(ObjectId.get().toHexString());


        MappingNode sinkNode1 = coreSinkNode(sinkEntityDef1,entityGraph);
        EntitySinkNodeConfig config = sinkNode1.getTypedConfiguration();
        config.setAcceptsDeletesFrom(List.of(sinkEntityDef.getId()));
        Edge coreToSink1 = edge(coreNode, sinkNode1, entityGraph);
        coreToSink1.setId(ObjectId.get().toHexString());
        AttributeDefinition sink1NameAttr = createAttribute("Name", new StringType(), sinkEntityDef1.getId());
        AttributeDefinition sink1RevenueAttribute = createAttribute("Revenue", new DoubleType(), sinkEntityDef1.getId()).setUpdatable(false);
        AttributeDefinition sink1QualityAttribute = createAttribute("Sink Quality", new StringType(), sinkEntityDef1.getId());

        MappingNode sink1NameAttrNode = sinkAttributeNode(sink1NameAttr,nameAttrGraph);
        edge(coreNameAttrNode, sink1NameAttrNode, nameAttrGraph);
        MappingNode sink1QAttrNode = sinkAttributeNode(sink1QualityAttribute,qualiytAttrGraph);
        edge(coreQAttrNode, sink1QAttrNode,qualiytAttrGraph);

        var sink1RevAttrNode = sinkAttributeNode(sink1RevenueAttribute, revAttrGraph);
        edge(coreRevAttrNode, sink1RevAttrNode, revAttrGraph);

        sinkEntityDef1.addField(sink1NameAttr);
        sinkEntityDef1.addField(sink1RevenueAttribute);
        sinkEntityDef1.addField(sink1QualityAttribute);
        when(connectorService.get(salesforceConn.getId())).thenReturn(salesforceConn);
        when(connectorService.refreshAuthentication(salesforceConn)).thenReturn(salesforceConn);
        when(currentBatch.getEntityBatch(sinkEntityDef1)).thenReturn(new StagedBatch(sinkEntityDef1.getApiName()).setConnectorId("my salesforce connector"));
        when(currentBatch.recordsBySyncariIdIterator()).thenReturn(recordsIter);
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(recordsIter.hasNext()).thenReturn(true, true, false);
        when(recordsIter.next()).thenReturn(new RecordsBySyncariId("syncariAcctId123"));

        when(idMappingRepo.findBySyncariIds("account", Set.of("syncariAcctId123")))
                    .thenReturn(List.of(new IdMapping().setEntityName("account").setSyncariId("syncariAcctId123").setMappings(List.of(
                            IdMapping.mapping(salesforceConn.getId(), "salesforceId", sinkEntityDef1.getId(), "syncariAcctId123"),
                            IdMapping.mapping(connector.getId(), "1", sinkEntityDef.getId(), true)))));

            when(mockDataServiceFactory.getDataService(salesforceConn.getMetadata())).thenReturn(salesforceService);
        //id mapping present to make it an update operation
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+coreNameAttrNode.getId(),new FunctionResult("Account Name",StringType.VALUE));
            return null;
        }).when(evaluator).evaluate(eq(sinkNameAttrNode), eq(nameAttrGraph),any(GraphContext.class),eq(n->n.getType()== MappingNodeType.CORE_ATTRIBUTE), any());
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx =m.getArgument(2);
            ctx.put("output_"+coreRevAttrNode.getId(),new FunctionResult(300.0,DoubleType.VALUE));
            return null;
        }).when(evaluator).evaluate(eq(sinkRevAttrNode), eq(revAttrGraph),any(GraphContext.class),eq(n->n.getType()== MappingNodeType.CORE_ATTRIBUTE), any());
        when(mockDataServiceFactory.getDataService(salesforceConn.getMetadata())).thenReturn(salesforceService);
        SyncResponse response = new SyncResponse();
        response.setResults(List.of(new Result(true, "salesforceId", "syncariAcctId123")));
        when(schemaService.getEntity(sinkEntityDef1.getId())).thenReturn(sinkEntityDef1);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(schemaService.getEntity(sinkEntityDef.getId())).thenReturn(sinkEntityDef);
        when(schemaService.findEntity(sinkEntityDef.getId())).thenReturn(Optional.of(sinkEntityDef));
        when(salesforceService.delete(any())).thenReturn(new SyncResponse(true).setResults(List.of(new Result(true, "salesforceId", "syncariAcctId123"))));

        when(schemaService.refreshSynapseSchema(eq(sinkEntityDef1.getConnectorId()), eq(sinkEntityDef1), any())).thenReturn(List.of(sinkEntityDef1));
        final GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph);
        graphContext.setSyncariEntity(coreEntityDef);

        saveToSink.execute(sinkEntityDef1, context, graphContext);

        ArgumentCaptor<List<TransactionLog>> txnLogs = ArgumentCaptor.forClass(List.class);
        verify(transactionLogService, times(1)).log(txnLogs.capture());
        assertEquals(1, txnLogs.getValue().size());
        assertEquals("syncariAcctId123", txnLogs.getValue().get(0).getSyncariId());
        assertEquals(Operation.external_delete, txnLogs.getValue().get(0).getOperation());
        assertTrue( ((ExternalDeleteInfo)txnLogs.getValue().get(0).getAdditionalInfo().get("deleteInfo")).isSyncariDeleted());
        assertEquals("Company", ((ExternalDeleteInfo)txnLogs.getValue().get(0).getAdditionalInfo().get("deleteInfo")).getDeletedId().getApiName());
        assertEquals("my salesforce connector", ((ExternalDeleteInfo)txnLogs.getValue().get(0).getAdditionalInfo().get("deleteInfo")).getDeletedId().getConnectorName());
        assertEquals("salesforceId", ((ExternalDeleteInfo)txnLogs.getValue().get(0).getAdditionalInfo().get("deleteInfo")).getDeletedId().getId());
        assertTrue(!((ExternalDeleteInfo)txnLogs.getValue().get(0).getAdditionalInfo().get("deleteInfo")).getDisconnectedSources().isEmpty());
        assertEquals("my zendesk connector", ((ExternalDeleteInfo)txnLogs.getValue().get(0).getAdditionalInfo().get("deleteInfo")).getDisconnectedSources().get(0).getConnectorName());
        assertEquals("Organization", ((ExternalDeleteInfo)txnLogs.getValue().get(0).getAdditionalInfo().get("deleteInfo")).getDisconnectedSources().get(0).getDisplayName());
        assertEquals("Organization", ((ExternalDeleteInfo)txnLogs.getValue().get(0).getAdditionalInfo().get("deleteInfo")).getDisconnectedSources().get(0).getApiName());
        assertEquals("1", ((ExternalDeleteInfo)txnLogs.getValue().get(0).getAdditionalInfo().get("deleteInfo")).getDisconnectedSources().get(0).getId());
    }

    @Test
    public void testPostDestinationEP() {

        ViperContext context = new ViperContext(new Organization(), new Instance(), new User());

        final Connector connector = createConnector("my zendesk connector", "zendeskConnectorId");
        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());

        EntityDefinition sinkEntityDef = new EntityDefinition();
        sinkEntityDef.setConnectorId(connector.getId());
        sinkEntityDef.setApiName("Organization");
        sinkEntityDef.setDisplayName("Organization");
        sinkEntityDef.setStatus(Status.ACTIVE);
        sinkEntityDef.setId(ObjectId.get().toHexString());

        EntityDefinition sourceEntityDef = sinkEntityDef;
        AttributeDefinition coreNameAttr = createAttribute("Name", new StringType(), coreEntityDef.getId());
        AttributeDefinition sinkNameAttr = createAttribute("Name", new StringType(), sinkEntityDef.getId());
        AttributeDefinition sinkIdAttr = createAttribute("Id", new StringType(), sinkEntityDef.getId()).setIdField(true);

        AttributeDefinition coreRevenueAttribute = createAttribute("Revenue", new DoubleType(), coreEntityDef.getId());
        AttributeDefinition coreQualityAttribute = createAttribute("Quality", new StringType(), coreEntityDef.getId());
        // Make revenue field as non-updateable
        AttributeDefinition sinkRevenueAttribute = createAttribute("Revenue", new DoubleType(), sinkEntityDef.getId()).setUpdatable(false);
        AttributeDefinition sinkQualityAttribute = createAttribute("Sink Quality", new StringType(), sinkEntityDef.getId());
        coreEntityDef.addField(coreNameAttr);
        coreEntityDef.addField(coreRevenueAttribute);
        coreEntityDef.addField(coreQualityAttribute);

        sinkEntityDef.addField(sinkIdAttr);
        sinkEntityDef.addField(sinkNameAttr);
        sinkEntityDef.addField(sinkRevenueAttribute);
        sinkEntityDef.addField(sinkQualityAttribute);

        Map<String, Object> predicateMap = new HashMap<>();
        var predicates = List.of(new HashMap<>( Map.of(
                "left", Map.of("datatype", "boolean", "type", "variable", "value", "destination_status"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", true)
        )
        ));
        predicateMap.put("predicate", Map.of("predicates", predicates, "operator", "AND"));

        // entity graph
        var entityGraph = newGraph(coreEntityDef, functionService, actionDefinitionRepo)
                .src(sourceEntityDef, "sourceEntity")
                .dest(sinkEntityDef, "sinkEntity")
                .function("filter", "Is Write Success", predicateMap)
                .function("predicate", "Is True", Map.of("value", true))
                .function("setValueOnEntity", "setValueOnEntity", Map.of("newValue", "new", "attributeDefinitionId", sinkQualityAttribute.getId()))
                .action("sendEmail", "sendEmail", Map.of("recipients", List.of("test@syncari.com"), "subject", "{{record.values.Name}}",
                        "body", new String(Base64.getEncoder().encode("{{external_record.values.Name}}".getBytes(StandardCharsets.UTF_8)),
                                StandardCharsets.UTF_8)))
                .connect("sourceEntity", coreEntityDef.getApiName())
                .connect(coreEntityDef.getApiName(), "sinkEntity")
                .connect("sinkEntity", "Is Write Success")
                .connect("Is Write Success", "Is True")
                .connect("Is True", "setValueOnEntity")
                .connect("setValueOnEntity", "sendEmail").getGraph();

        // add one attribute graph.
        var nameAttrGraph = newGraph(coreNameAttr)
                .src(sinkNameAttr, "sourceField")
                .dest(sinkNameAttr, "sinkField")
                .connect("sourceField", coreNameAttr.getApiName())
                .connect(coreNameAttr.getApiName(), "sinkField").getGraph();

        when(connectorService.get("my zendesk connector")).thenReturn(connector);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        when(connectorService.find(connector.getId(), false)).thenReturn(Optional.of(connector));
        when(currentBatch.getEntityBatch(sinkEntityDef)).thenReturn(new StagedBatch(sinkEntityDef.getApiName()).setConnectorId("my zendesk connector"));
        when(currentBatch.recordsBySyncariIdIterator()).thenReturn(recordsIter);
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(recordsIter.hasNext()).thenReturn(true, true, false);
        when(recordsIter.next()).thenReturn(new RecordsBySyncariId("syncariAcctId123"));
        when(schemaService.refreshSynapseSchema(eq(sinkEntityDef.getConnectorId()), eq(sinkEntityDef), any())).
                thenReturn(List.of(sinkEntityDef));


        List<EntityData> t = List.of(new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("Name", "Account Name")
                .addValue("Revenue", 300.0)
                .addValue("CreateOnly", "This should be discarded")
                .setNew(true).setDeleted(false));

        when(entityRepo.find(any(EntityDefinition.class), any(),any(PageCursor.class))).thenReturn(t);
        when(graphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(nameAttrGraph));
        when(attributeDefinitionCache.findById(coreNameAttr.getId())).thenReturn(Optional.of(coreNameAttr));
        when(attributeDefinitionCache.findById(coreRevenueAttribute.getId())).thenReturn(Optional.of(coreRevenueAttribute));
        when(attributeDefinitionCache.findById(coreQualityAttribute.getId())).thenReturn(Optional.of(coreQualityAttribute));
        when(attributeDefinitionCache.findAllById(anyIterable())).thenReturn(List.of(coreNameAttr, coreRevenueAttribute, coreQualityAttribute));

        when(idMappingRepo.findBySyncariIds("account", Set.of("syncariAcctId123")))
                .thenReturn(List.of(new IdMapping().setEntityName("account").setSyncariId("syncariAcctId123")));

        when(mockDataServiceFactory.getDataService(connector.getMetadata())).thenReturn(zendeskService);
        SyncResponse response = new SyncResponse();
        response.setResults(List.of(new Result(true, "123", "syncariAcctId123")));
        when(zendeskService.create(any())).thenReturn(response);
        when(schemaService.getEntity(sinkEntityDef.getId())).thenReturn(sinkEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);

        var coreNameAttrNode = nameAttrGraph.getCoreNode();

        var oldEvaluator = evaluator;
        saveToSink.evaluator = new JTwigPipelineEvaluator(environment, tokenHelper, actions, pipelineNodeAuditService, featureService);
        GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph);
        graphContext.put("output_"+coreNameAttrNode.getId(),new FunctionResult("Account Name",StringType.VALUE));
        graphContext.setSyncariEntity(coreEntityDef);
        try {
            saveToSink.execute(sinkEntityDef, context,graphContext);
        } finally {
            saveToSink.evaluator = oldEvaluator;
        }

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendHtml(any(), subject.capture(), body.capture());
        assertEquals("Account Name", subject.getValue());
        assertEquals("Account Name", body.getValue());
    }

    @Test
    public void testPostDestinationFP() {

        ViperContext context = new ViperContext(new Organization(), new Instance(), new User());

        final Connector connector = createConnector("my zendesk connector", "zendeskConnectorId");
        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());

        EntityDefinition sinkEntityDef = new EntityDefinition();
        sinkEntityDef.setConnectorId(connector.getId());
        sinkEntityDef.setApiName("Organization");
        sinkEntityDef.setDisplayName("Organization");
        sinkEntityDef.setStatus(Status.ACTIVE);
        sinkEntityDef.setId(ObjectId.get().toHexString());

        EntityDefinition sourceEntityDef = sinkEntityDef;
        AttributeDefinition coreNameAttr = createAttribute("Name", new StringType(), coreEntityDef.getId());
        AttributeDefinition sinkNameAttr = createAttribute("Name", new StringType(), sinkEntityDef.getId());
        AttributeDefinition sinkIdAttr = createAttribute("Id", new StringType(), sinkEntityDef.getId()).setIdField(true);

        AttributeDefinition coreRevenueAttribute = createAttribute("Revenue", new DoubleType(), coreEntityDef.getId());
        AttributeDefinition coreQualityAttribute = createAttribute("Quality", new StringType(), coreEntityDef.getId());
        // Make revenue field as non-updateable
        AttributeDefinition sinkRevenueAttribute = createAttribute("Revenue", new DoubleType(), sinkEntityDef.getId()).setUpdatable(false);
        AttributeDefinition sinkQualityAttribute = createAttribute("Sink Quality", new StringType(), sinkEntityDef.getId());
        coreEntityDef.addField(coreNameAttr);
        coreEntityDef.addField(coreRevenueAttribute);
        coreEntityDef.addField(coreQualityAttribute);

        sinkEntityDef.addField(sinkIdAttr);
        sinkEntityDef.addField(sinkNameAttr);
        sinkEntityDef.addField(sinkRevenueAttribute);
        sinkEntityDef.addField(sinkQualityAttribute);

        Map<String, Object> predicateMap = new HashMap<>();
        var predicates = List.of(new HashMap<>( Map.of(
                "left", Map.of("datatype", "boolean", "type", "variable", "value", "destination_status"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", true)
        )
        ));
        predicateMap.put("predicate", Map.of("predicates", predicates, "operator", "AND"));

        // entity graph
        var entityGraph = newGraph(coreEntityDef, functionService, actionDefinitionRepo)
                .src(sourceEntityDef, "sourceEntity")
                .dest(sinkEntityDef, "sinkEntity")
                .connect("sourceEntity", coreEntityDef.getApiName())
                .connect(coreEntityDef.getApiName(), "sinkEntity").getGraph();


        // add one attribute graph.
        var nameAttrGraph = newGraph(coreNameAttr)
                .src(sinkNameAttr, "sourceField")
                .dest(sinkNameAttr, "sinkField")
                .function("filter", "Is Write Success", predicateMap)
                .function("predicate", "Is True", Map.of("value", true))
                .function("setValue", "setValue", Map.of("newValue", "new", "attributeDefinitionId", sinkQualityAttribute.getId()))
                .action("sendEmail", "sendEmail", Map.of("recipients", List.of("test@syncari.com"), "subject", "{{record.values.Name}}",
                        "body", new String(Base64.getEncoder().encode("{{external_record.values.Name}} {{external_record.Id}}".getBytes(StandardCharsets.UTF_8)),
                                StandardCharsets.UTF_8)))
                .connect("sourceField", coreNameAttr.getApiName())
                .connect(coreNameAttr.getApiName(), "sinkField")
                .connect("sinkField", "Is Write Success")
                .connect("Is Write Success", "Is True")
                .connect("Is True", "setValue")
                .connect("setValue", "sendEmail").getGraph();


        when(connectorService.get("my zendesk connector")).thenReturn(connector);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        when(connectorService.find(connector.getId(), false)).thenReturn(Optional.of(connector));
        when(currentBatch.getEntityBatch(sinkEntityDef)).thenReturn(new StagedBatch(sinkEntityDef.getApiName()).setConnectorId("my zendesk connector"));
        when(currentBatch.recordsBySyncariIdIterator()).thenReturn(recordsIter);
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(recordsIter.hasNext()).thenReturn(true, true, false);
        when(recordsIter.next()).thenReturn(new RecordsBySyncariId("syncariAcctId123"));

        List<EntityData> t = List.of(new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("Name", "Account Name")
                .addValue("Revenue", 300.0)
                .addValue("CreateOnly", "This should be discarded")
                .setNew(true).setDeleted(false));

        when(entityRepo.find(any(EntityDefinition.class), any(),any(PageCursor.class))).thenReturn(t);
        when(graphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(nameAttrGraph));
        when(attributeDefinitionCache.findById(coreNameAttr.getId())).thenReturn(Optional.of(coreNameAttr));
        when(attributeDefinitionCache.findById(coreRevenueAttribute.getId())).thenReturn(Optional.of(coreRevenueAttribute));
        when(attributeDefinitionCache.findById(coreQualityAttribute.getId())).thenReturn(Optional.of(coreQualityAttribute));
        when(attributeDefinitionCache.findAllById(anyIterable())).thenReturn(List.of(coreNameAttr, coreRevenueAttribute, coreQualityAttribute));

        when(idMappingRepo.findBySyncariIds("account", Set.of("syncariAcctId123")))
                .thenReturn(List.of(new IdMapping().setEntityName("account").setSyncariId("syncariAcctId123")));

        when(idMappingRepo.saveIdMapping(any(), any(), any(), any())).thenReturn(List.of(new IdMapping().setEntityName("account").setSyncariId("syncariAcctId123")
                .setMappings(List.of(new IdMapping.Mapping(connector.getId(), "123", sinkEntityDef.getId(), "syncariAcctId123", false)))));

        when(mockDataServiceFactory.getDataService(connector.getMetadata())).thenReturn(zendeskService);
        SyncResponse response = new SyncResponse();
        response.setResults(List.of(new Result(true, "123", "syncariAcctId123")));
        when(zendeskService.create(any())).thenReturn(response);
        when(schemaService.getEntity(sinkEntityDef.getId())).thenReturn(sinkEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);

        var coreNameAttrNode = nameAttrGraph.getCoreNode();

        var oldEvaluator = evaluator;
        saveToSink.evaluator = new JTwigPipelineEvaluator(environment, tokenHelper, actions, pipelineNodeAuditService, featureService);
        GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph);
        graphContext.put("output_"+coreNameAttrNode.getId(),new FunctionResult("Account Name",StringType.VALUE));
        when(schemaService.refreshSynapseSchema(eq(sinkEntityDef.getConnectorId()), eq(sinkEntityDef), any())).thenReturn(List.of(sinkEntityDef));
        graphContext.setSyncariEntity(coreEntityDef);

        try {
            saveToSink.execute(sinkEntityDef, context,graphContext);
        } finally {
            saveToSink.evaluator = oldEvaluator;
        }

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendHtml(any(), subject.capture(), body.capture());
        assertEquals("Account Name", subject.getValue());
        assertEquals("Account Name 123", body.getValue());
    }

    private static Connector createConnector(String connectorName, String connectorMetaId) {
        return createConnector(connectorName, connectorName, connectorMetaId);
    }

    private static Connector createConnector(String connectorName, String connectorId, String connectorMetaId) {
        Connector connector = new Connector(connectorName, connectorMetaId,
                "https://someendpoint");
        connector.setId(connectorId);
        connector.setMetadata(new ConnectorMetadata(connectorMetaId));
        connector.setStatus(ConnectorStatus.ACTIVE);
        return connector;
    }

    @Test
    public void testPostDestinationTwoSourcesFP() {

        ViperContext context = new ViperContext(new Organization(), new Instance(), new User());

        Connector salesforceConn = new Connector("my salesforce connector", "salesforceConnectorId",
                "https://someendpoint");

        salesforceConn.setId("my salesforce connector");
        salesforceConn.setMetadata(new ConnectorMetadata("salesforceConnectorId"));
        salesforceConn.setStatus(ConnectorStatus.ACTIVE);
        Connector zendeskConn = new Connector("my zendesk connector", "zendeskConnectorId",
                "https://someendpoint");

        zendeskConn.setId("my zendesk connector");
        zendeskConn.setMetadata(new ConnectorMetadata("zendeskConnectorId"));
        zendeskConn.setStatus(ConnectorStatus.ACTIVE);

        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());

        EntityDefinition salesforceAccount = new EntityDefinition();
        salesforceAccount.setConnectorId(salesforceConn.getId());
        salesforceAccount.setApiName("Organization");
        salesforceAccount.setDisplayName("Organization");
        salesforceAccount.setStatus(Status.ACTIVE);
        salesforceAccount.setId(ObjectId.get().toHexString());

        EntityDefinition zendeskAccount = new EntityDefinition();
        zendeskAccount.setConnectorId(zendeskConn.getId());
        zendeskAccount.setApiName("Organization");
        zendeskAccount.setDisplayName("Organization");
        zendeskAccount.setStatus(Status.ACTIVE);
        zendeskAccount.setId(ObjectId.get().toHexString());

        AttributeDefinition coreNameAttr = createAttribute("Name", new StringType(), coreEntityDef.getId());
        AttributeDefinition salesforceName = createAttribute("Name", new StringType(), salesforceAccount.getId());
        AttributeDefinition zendeskName = createAttribute("Name", new StringType(), zendeskAccount.getId());

        AttributeDefinition salesforceIdAttr = createAttribute("Id", new StringType(), salesforceAccount.getId()).setIdField(true);
        AttributeDefinition zendeskIdAttr = createAttribute("Id", new StringType(), zendeskAccount.getId()).setIdField(true);

        AttributeDefinition coreRevenueAttribute = createAttribute("Revenue", new DoubleType(), coreEntityDef.getId());
        AttributeDefinition salesforceRevenue = createAttribute("Revenue", new DoubleType(), salesforceAccount.getId());
        AttributeDefinition zendeskRevenue = createAttribute("Revenue", new StringType(), zendeskAccount.getId());


        coreEntityDef.addField(coreNameAttr);
        coreEntityDef.addField(coreRevenueAttribute);

        salesforceAccount.addField(salesforceIdAttr);
        salesforceAccount.addField(salesforceName);
        salesforceAccount.addField(salesforceRevenue);

        zendeskAccount.addField(zendeskIdAttr);
        zendeskAccount.addField(zendeskName);
        zendeskAccount.addField(zendeskRevenue);

        Map<String, Object> predicateMap = new HashMap<>();
        var predicates = List.of(new HashMap<>( Map.of(
                "left", Map.of("datatype", "boolean", "type", "variable", "value", "destination_status"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", true)
        )
        ));
        predicateMap.put("predicate", Map.of("predicates", predicates, "operator", "AND"));

        // entity graph
        var entityGraph = newGraph(coreEntityDef, functionService, actionDefinitionRepo)
                .src(salesforceAccount, "salesforceSourceAccount")
                .src(zendeskAccount, "zendeskSourceAccount")
                .dest(salesforceAccount, "salesforceDestAccount")
                .dest(zendeskAccount, "zendeskDestAccount")
                .connect("salesforceSourceAccount", coreEntityDef.getApiName())
                .connect("zendeskSourceAccount", coreEntityDef.getApiName())
                .connect(coreEntityDef.getApiName(), "salesforceDestAccount")
                .connect(coreEntityDef.getApiName(), "zendeskDestAccount").getGraph();


        // add one attribute graph.
        var nameAttrGraph = newGraph(coreNameAttr)
                .src(salesforceName, "salesforceSrcName")
                .src(zendeskName, "zendeskSrcName")
                .dest(salesforceName, "salesforceDestName")
                .dest(zendeskName, "zendeskDestName")
                .function("filter", "Is Write Success SF", predicateMap)
                .function("filter", "Is Write Success ZD", predicateMap)
                .function("predicate", "Is True SF", Map.of("value", true))
                .function("predicate", "Is True ZD", Map.of("value", true))
                .action("sendEmail", "sendEmailSF", Map.of("recipients", List.of("test@syncari.com"), "subject", "{{record.values.Name}}",
                        "body", new String(Base64.getEncoder().encode("{{my_zendesk_connector.Organization.Name}} {{my_zendesk_connector.Organization.Id}}".getBytes(StandardCharsets.UTF_8)),
                                StandardCharsets.UTF_8)))
                .action("sendEmail", "sendEmailZD", Map.of("recipients", List.of("test@syncari.com"), "subject", "{{record.values.Name}}",
                        "body", new String(Base64.getEncoder().encode("{{external_record.values.Name}} {{external_record.Id}}".getBytes(StandardCharsets.UTF_8)),
                                StandardCharsets.UTF_8)))
                .connect("salesforceSrcName", coreNameAttr.getApiName())
                .connect("zendeskSrcName", coreNameAttr.getApiName())
                .connect(coreNameAttr.getApiName(), "salesforceDestName")
                .connect(coreNameAttr.getApiName(), "zendeskDestName")
                .connect("salesforceDestName", "Is Write Success SF")
                .connect("zendeskDestName", "Is Write Success ZD")
                .connect("Is Write Success SF", "Is True SF")
                .connect("Is Write Success ZD", "Is True ZD")
                .connect("Is True SF", "sendEmailSF").connect("Is True ZD", "sendEmailZD").getGraph();

        when(connectorService.get("my zendesk connector")).thenReturn(zendeskConn);
        when(connectorService.get("my salesforce connector")).thenReturn(salesforceConn);
        when(connectorService.refreshAuthentication(salesforceConn)).thenReturn(salesforceConn);
        when(connectorService.refreshAuthentication(zendeskConn)).thenReturn(zendeskConn);

        when(connectorService.find(salesforceConn.getId(), false)).thenReturn(Optional.of(salesforceConn));
        when(connectorService.find(zendeskConn.getId(), false)).thenReturn(Optional.of(zendeskConn));

        when(currentBatch.getEntityBatch(salesforceAccount)).thenReturn(new StagedBatch(salesforceAccount.getApiName()).setConnectorId("my salesforce connector"));
        when(currentBatch.recordsBySyncariIdIterator()).thenReturn(recordsIter);
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(recordsIter.hasNext()).thenReturn(true, true, false);
        when(recordsIter.next()).thenReturn(new RecordsBySyncariId("syncariAcctId123"));

        List<EntityData> t = List.of(new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("Name", "Account Name")
                .addValue("Revenue", 300.0)
                .addValue("CreateOnly", "This should be discarded")
                .setNew(true).setDeleted(false));

        when(entityRepo.find(any(EntityDefinition.class), any(),any(PageCursor.class))).thenReturn(t);

        when(graphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(nameAttrGraph));
        when(attributeDefinitionCache.findById(coreNameAttr.getId())).thenReturn(Optional.of(coreNameAttr));
        when(attributeDefinitionCache.findById(coreRevenueAttribute.getId())).thenReturn(Optional.of(coreRevenueAttribute));
        when(attributeDefinitionCache.findAllById(anyIterable())).thenReturn(List.of(coreNameAttr, coreRevenueAttribute));

        when(idMappingRepo.findBySyncariIds("account", Set.of("syncariAcctId123")))
                .thenReturn(List.of(new IdMapping().setEntityName("account").setSyncariId("syncariAcctId123")));

        when(idMappingRepo.saveIdMapping(any(), any(), any(), any())).thenReturn(List.of(new IdMapping().setEntityName("account").setSyncariId("syncariAcctId123")
                .setMappings(List.of(new IdMapping.Mapping(zendeskConn.getId(), "123", zendeskAccount.getId(), "syncariAcctId123", false)))));

        when(mockDataServiceFactory.getDataService(salesforceConn.getMetadata())).thenReturn(zendeskService);
        SyncResponse response = new SyncResponse();
        response.setResults(List.of(new Result(true, "123", "syncariAcctId123")));
        when(zendeskService.create(any())).thenReturn(response);
        when(schemaService.getEntity(salesforceAccount.getId())).thenReturn(salesforceAccount);
        when(schemaService.getEntity(zendeskAccount.getId())).thenReturn(zendeskAccount);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);

        var coreNameAttrNode = nameAttrGraph.getCoreNode();

        var oldEvaluator = evaluator;
        saveToSink.evaluator = new JTwigPipelineEvaluator(environment, tokenHelper, actions, pipelineNodeAuditService, featureService);
        GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph);
        graphContext.put("output_"+coreNameAttrNode.getId(),new FunctionResult("Account Name",StringType.VALUE));
        when(schemaService.refreshSynapseSchema(eq(zendeskAccount.getConnectorId()), eq(zendeskAccount), any())).thenReturn(List.of(zendeskAccount));
        graphContext.setSyncariEntity(coreEntityDef);

        try {
            saveToSink.execute(zendeskAccount, context ,graphContext);
        } finally {
            saveToSink.evaluator = oldEvaluator;
        }

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendHtml(any(), subject.capture(), body.capture());
        assertEquals("Account Name", subject.getValue());
        assertEquals("Account Name 123", body.getValue());
    }

    @Test
    public void testMergeCollapse() {

        ViperContext context = new ViperContext(new Organization(), new Instance(), new User());

        Connector connector = createConnector("my zendesk connector", "zendeskConnectorId");

        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());

        EntityDefinition sinkEntityDef = new EntityDefinition();
        sinkEntityDef.setConnectorId(connector.getId());
        sinkEntityDef.setApiName("Organization");
        sinkEntityDef.setDisplayName("Organization");
        sinkEntityDef.setStatus(Status.ACTIVE);
        sinkEntityDef.setId(ObjectId.get().toHexString());

        EntityDefinition sourceEntityDef = sinkEntityDef;
        AttributeDefinition coreNameAttr = createAttribute("Name", new StringType(), coreEntityDef.getId());
        AttributeDefinition sinkNameAttr = createAttribute("Name", new StringType(), sinkEntityDef.getId());
        AttributeDefinition sinkIdAttr = createAttribute("Id", new StringType(), sinkEntityDef.getId()).setIdField(true);

        AttributeDefinition coreRevenueAttribute = createAttribute("Revenue", new DoubleType(), coreEntityDef.getId());
        AttributeDefinition coreQualityAttribute = createAttribute("Quality", new StringType(), coreEntityDef.getId());
        // Make revenue field as non-updateable
        AttributeDefinition sinkRevenueAttribute = createAttribute("Revenue", new DoubleType(), sinkEntityDef.getId()).setUpdatable(false);
        AttributeDefinition sinkQualityAttribute = createAttribute("Sink Quality", new StringType(), sinkEntityDef.getId());
        coreEntityDef.addField(coreNameAttr);
        coreEntityDef.addField(coreRevenueAttribute);
        coreEntityDef.addField(coreQualityAttribute);

        sinkEntityDef.addField(sinkIdAttr);
        sinkEntityDef.addField(sinkNameAttr);
        sinkEntityDef.addField(sinkRevenueAttribute);
        sinkEntityDef.addField(sinkQualityAttribute);

        Map<String, Object> predicateMap = new HashMap<>();
        var predicates = List.of(new HashMap<>( Map.of(
                "left", Map.of("datatype", "boolean", "type", "variable", "value", "destination_status"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", true)
        )
        ));
        predicateMap.put("predicate", Map.of("predicates", predicates, "operator", "AND"));

        // entity graph
        var entityGraph = newGraph(coreEntityDef, functionService, actionDefinitionRepo)
                .src(sourceEntityDef, "sourceEntity")
                .dest(sinkEntityDef, "sinkEntity")
                .function("filter", "Is Write Success", predicateMap)
                .function("predicate", "Is True", Map.of("value", true))
                .function("setValueOnEntity", "setValueOnEntity", Map.of("newValue", "new", "attributeDefinitionId", sinkQualityAttribute.getId()))
                .action("sendEmail", "sendEmail", Map.of("recipients", List.of("test@syncari.com"), "subject", "{{record.values.Name}} {{destination_operation}} {{destination_status}}",
                        "body", new String(Base64.getEncoder().encode("{{external_record.values.Name}}".getBytes(StandardCharsets.UTF_8)),
                                StandardCharsets.UTF_8)))
                .connect("sourceEntity", coreEntityDef.getApiName())
                .connect(coreEntityDef.getApiName(), "sinkEntity")
                .connect("sinkEntity", "Is Write Success")
                .connect("Is Write Success", "Is True")
                .connect("Is True", "setValueOnEntity")
                .connect("setValueOnEntity", "sendEmail").getGraph();

        // add one attribute graph.
        var nameAttrGraph = newGraph(coreNameAttr)
                .src(sinkNameAttr, "sourceField")
                .dest(sinkNameAttr, "sinkField")
                .connect("sourceField", coreNameAttr.getApiName())
                .connect(coreNameAttr.getApiName(), "sinkField").getGraph();

        when(entityRepo.find(any(EntityDefinition.class), any(), any(PageCursor.class))).thenReturn(List.of(new EntityData()), List.of());
        when(connectorService.get("my zendesk connector")).thenReturn(connector);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        when(connectorService.find(connector.getId(), false)).thenReturn(Optional.of(connector));
        when(currentBatch.getEntityBatch(sinkEntityDef)).thenReturn(new StagedBatch(sinkEntityDef.getApiName()).setConnectorId("my zendesk connector"));
        when(currentBatch.recordsBySyncariIdIterator()).thenReturn(recordsIter);
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(recordsIter.hasNext()).thenReturn(true, true, false);
        when(recordsIter.next()).thenReturn(new RecordsBySyncariId("syncariAcctId123"));

        // generate merge operations
        List<TransactionLog> logs = IntStream.range(1, 21).mapToObj(i -> {
            EntityData winner = new EntityData("account")
                    .setSyncariEntityId("syncariAcctId" + i)
                    .addValue("Name", "Account Name")
                    .addValue("Revenue", 300.0)
                    .setNew(false).setDeleted(false);

            List<EntityData> losers = List.of(new EntityData("account")
                    .setSyncariEntityId("syncariAcctId" + (i - 1))
                    .addValue("Name", "Account Name")
                    .addValue("Revenue", 250)
                    .setNew(false).setDeleted(false));

            MergeOperation operation = new MergeOperation();
            operation.setWinningRecord(winner);
            operation.setLosingRecords(losers);
            operation.setBatchId(currentBatch.getCurrentBatchId());
            return new TransactionLog().setEntityName("account").setEntityId(coreEntityDef.getId()).setOperation(Operation.merge)
                    .setSyncariId("syncariAcctId123").setAdditionalInfo(Map.of("mergeDetails", operation));

        }).collect(Collectors.toList());

        com.syncari.core.model.pagination.Page page = new com.syncari.core.model.pagination.Page();
        page.setRecords(logs);

        doReturn(page, new com.syncari.core.model.pagination.Page(new PageInfo(), new ArrayList())).when(transactionLogService)
                .findMergesByBatchId(eq("currentBatchId"), any(), eq(new PageCursor("", PageDirection.previous,500)));

        //when(transactionLogService.findMergesByBatchId(any(), any())).thenReturn(page, new com.syncari.core.model.pagination.Page());
        when(graphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(nameAttrGraph));
        when(attributeDefinitionCache.findById(coreNameAttr.getId())).thenReturn(Optional.of(coreNameAttr));
        when(attributeDefinitionCache.findById(coreRevenueAttribute.getId())).thenReturn(Optional.of(coreRevenueAttribute));
        when(attributeDefinitionCache.findById(coreQualityAttribute.getId())).thenReturn(Optional.of(coreQualityAttribute));
        when(attributeDefinitionCache.findAllById(anyIterable())).thenReturn(List.of(coreNameAttr, coreRevenueAttribute, coreQualityAttribute));
        IntStream.range(1, 21).forEach(i -> {
            when(idMappingRepo.findExistingMapping("account", "syncariAcctId" + i, connector.getId(), sinkEntityDef.getId()))
                    .thenReturn(Optional.of(new IdMapping().setSyncariId("syncariAcctId" + i).addMapping(connector.getId(), "externalId_" + i ,sinkEntityDef.getId())));
        });
        when(idMappingRepo.findBySyncariIds("account", Set.of("syncariAcctId123")))
                .thenReturn(List.of(new IdMapping().setEntityName("account").setSyncariId("syncariAcctId123")));

        when(mockDataServiceFactory.getDataService(connector.getMetadata())).thenReturn(zendeskService);
        MergeResponse response = new MergeResponse();
        when(zendeskService.merge(any(MergeRequest.class))).thenReturn(response);
        when(schemaService.getEntity(sinkEntityDef.getId())).thenReturn(sinkEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);

        var coreNameAttrNode = nameAttrGraph.getCoreNode();

        var oldEvaluator = evaluator;
        saveToSink.evaluator = new JTwigPipelineEvaluator(environment, tokenHelper, actions, pipelineNodeAuditService, featureService);
        GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph);
        graphContext.put("output_"+coreNameAttrNode.getId(),new FunctionResult("Account Name",StringType.VALUE));
        when(schemaService.refreshSynapseSchema(eq(sinkEntityDef.getConnectorId()), eq(sinkEntityDef), any())).thenReturn(List.of(sinkEntityDef));
        graphContext.setSyncariEntity(coreEntityDef);



        try {
            saveToSink.execute(sinkEntityDef, context,graphContext);
        } finally {
            saveToSink.evaluator = oldEvaluator;
        }

        ArgumentCaptor<List> mergeRequests = ArgumentCaptor.forClass(List.class);
        verify(zendeskService, times(1)).merge(mergeRequests.capture());
        assertEquals(1, mergeRequests.getValue().size());
        assertEquals("syncariAcctId20", ((MergeRequest)mergeRequests.getValue().get(0)).getWinner().getSyncariEntityId());
    }

    @Test
    public void testMergeCollapseSameWinner() {

        ViperContext context = new ViperContext(new Organization(), new Instance(), new User());

        final Connector connector = createConnector("my zendesk connector", "zendeskConnectorId");
        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());

        EntityDefinition sinkEntityDef = new EntityDefinition();
        sinkEntityDef.setConnectorId(connector.getId());
        sinkEntityDef.setApiName("Organization");
        sinkEntityDef.setDisplayName("Organization");
        sinkEntityDef.setStatus(Status.ACTIVE);
        sinkEntityDef.setId(ObjectId.get().toHexString());

        EntityDefinition sourceEntityDef = sinkEntityDef;
        AttributeDefinition coreNameAttr = createAttribute("Name", new StringType(), coreEntityDef.getId());
        AttributeDefinition sinkNameAttr = createAttribute("Name", new StringType(), sinkEntityDef.getId());
        AttributeDefinition sinkIdAttr = createAttribute("Id", new StringType(), sinkEntityDef.getId()).setIdField(true);

        AttributeDefinition coreRevenueAttribute = createAttribute("Revenue", new DoubleType(), coreEntityDef.getId());
        AttributeDefinition coreQualityAttribute = createAttribute("Quality", new StringType(), coreEntityDef.getId());
        // Make revenue field as non-updateable
        AttributeDefinition sinkRevenueAttribute = createAttribute("Revenue", new DoubleType(), sinkEntityDef.getId()).setUpdatable(false);
        AttributeDefinition sinkQualityAttribute = createAttribute("Sink Quality", new StringType(), sinkEntityDef.getId());
        coreEntityDef.addField(coreNameAttr);
        coreEntityDef.addField(coreRevenueAttribute);
        coreEntityDef.addField(coreQualityAttribute);

        sinkEntityDef.addField(sinkIdAttr);
        sinkEntityDef.addField(sinkNameAttr);
        sinkEntityDef.addField(sinkRevenueAttribute);
        sinkEntityDef.addField(sinkQualityAttribute);

        Map<String, Object> predicateMap = new HashMap<>();
        var predicates = List.of(new HashMap<>( Map.of(
                "left", Map.of("datatype", "boolean", "type", "variable", "value", "destination_status"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", true)
        )
        ));
        predicateMap.put("predicate", Map.of("predicates", predicates, "operator", "AND"));

        // entity graph
        var entityGraph = newGraph(coreEntityDef, functionService, actionDefinitionRepo)
                .src(sourceEntityDef, "sourceEntity")
                .dest(sinkEntityDef, "sinkEntity")
                .function("filter", "Is Write Success", predicateMap)
                .function("predicate", "Is True", Map.of("value", true))
                .function("setValueOnEntity", "setValueOnEntity", Map.of("newValue", "new", "attributeDefinitionId", sinkQualityAttribute.getId()))
                .action("sendEmail", "sendEmail", Map.of("recipients", List.of("test@syncari.com"), "subject", "{{record.values.Name}} {{destination_operation}} {{destination_status}}",
                        "body", new String(Base64.getEncoder().encode("{{external_record.values.Name}}".getBytes(StandardCharsets.UTF_8)),
                                StandardCharsets.UTF_8)))
                .connect("sourceEntity", coreEntityDef.getApiName())
                .connect(coreEntityDef.getApiName(), "sinkEntity")
                .connect("sinkEntity", "Is Write Success")
                .connect("Is Write Success", "Is True")
                .connect("Is True", "setValueOnEntity")
                .connect("setValueOnEntity", "sendEmail").getGraph();

        // add one attribute graph.
        var nameAttrGraph = newGraph(coreNameAttr)
                .src(sinkNameAttr, "sourceField")
                .dest(sinkNameAttr, "sinkField")
                .connect("sourceField", coreNameAttr.getApiName())
                .connect(coreNameAttr.getApiName(), "sinkField").getGraph();

        when(connectorService.get("my zendesk connector")).thenReturn(connector);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        when(connectorService.find(connector.getId(), false)).thenReturn(Optional.of(connector));
        when(currentBatch.getEntityBatch(sinkEntityDef)).thenReturn(new StagedBatch(sinkEntityDef.getApiName()).setConnectorId("my zendesk connector"));
        when(currentBatch.recordsBySyncariIdIterator()).thenReturn(recordsIter);
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(recordsIter.hasNext()).thenReturn(true, true, false);
        when(recordsIter.next()).thenReturn(new RecordsBySyncariId("syncariAcctId123"));
        when(entityRepo.find(any(EntityDefinition.class), any(), any(PageCursor.class))).thenReturn(List.of(new EntityData()), List.of());
        // generate merge operations
        List<TransactionLog> logs = IntStream.range(1, 21).mapToObj(i -> {
            EntityData winner = new EntityData("account")
                    .setSyncariEntityId("syncariAcctId0")
                    .addValue("Name", "Account Name")
                    .addValue("Revenue", 300.0)
                    .setNew(false).setDeleted(false);

            List<EntityData> losers = List.of(new EntityData("account")
                    .setSyncariEntityId("syncariAcctId" + (i))
                    .addValue("Name", "Account Name")
                    .addValue("Revenue", 250)
                    .setNew(false).setDeleted(false));

            MergeOperation operation = new MergeOperation();
            operation.setWinningRecord(winner);
            operation.setLosingRecords(losers);
            operation.setBatchId(currentBatch.getCurrentBatchId());
            return new TransactionLog().setEntityName("account").setEntityId(coreEntityDef.getId()).setOperation(Operation.merge)
                    .setSyncariId("syncariAcctId123").setAdditionalInfo(Map.of("mergeDetails", operation));

        }).collect(Collectors.toList());

        com.syncari.core.model.pagination.Page page = new com.syncari.core.model.pagination.Page();
        page.setRecords(logs);

        doReturn(page, new com.syncari.core.model.pagination.Page(new PageInfo(), new ArrayList())).when(transactionLogService)
                .findMergesByBatchId(eq("currentBatchId"), any(), eq(new PageCursor("", PageDirection.previous,500)));

        //when(transactionLogService.findMergesByBatchId(any(), any())).thenReturn(page, new com.syncari.core.model.pagination.Page());
        when(graphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(nameAttrGraph));
        when(attributeDefinitionCache.findById(coreNameAttr.getId())).thenReturn(Optional.of(coreNameAttr));
        when(attributeDefinitionCache.findById(coreRevenueAttribute.getId())).thenReturn(Optional.of(coreRevenueAttribute));
        when(attributeDefinitionCache.findById(coreQualityAttribute.getId())).thenReturn(Optional.of(coreQualityAttribute));
        when(attributeDefinitionCache.findAllById(anyIterable())).thenReturn(List.of(coreNameAttr, coreRevenueAttribute, coreQualityAttribute));

        when(idMappingRepo.findBySyncariIds("account", Set.of("syncariAcctId123")))
                .thenReturn(List.of(new IdMapping().setEntityName("account").setSyncariId("syncariAcctId123")));
        when(idMappingRepo.findExistingMapping("account", "syncariAcctId0", connector.getId(), sinkEntityDef.getId()))
                .thenReturn(Optional.of(new IdMapping().setSyncariId("syncariAcctId0").addMapping(connector.getId(), "externalId0",sinkEntityDef.getId())));

        when(mockDataServiceFactory.getDataService(connector.getMetadata())).thenReturn(zendeskService);
        MergeResponse response = new MergeResponse();
        when(zendeskService.merge(any(MergeRequest.class))).thenReturn(response);
        when(schemaService.getEntity(sinkEntityDef.getId())).thenReturn(sinkEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);

        var coreNameAttrNode = nameAttrGraph.getCoreNode();

        var oldEvaluator = evaluator;
        saveToSink.evaluator = new JTwigPipelineEvaluator(environment, tokenHelper, actions, pipelineNodeAuditService, featureService);
        GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph);
        graphContext.put("output_"+coreNameAttrNode.getId(),new FunctionResult("Account Name",StringType.VALUE));
        when(schemaService.refreshSynapseSchema(eq(sinkEntityDef.getConnectorId()), eq(sinkEntityDef), any())).thenReturn(List.of(sinkEntityDef));

        graphContext.setSyncariEntity(coreEntityDef);

        try {
            saveToSink.execute(sinkEntityDef, context,graphContext);
        } finally {
            saveToSink.evaluator = oldEvaluator;
        }

        ArgumentCaptor<List> mergeRequests = ArgumentCaptor.forClass(List.class);
        verify(zendeskService, times(1)).merge(mergeRequests.capture());
        assertEquals(20, mergeRequests.getValue().size());
        assertTrue(mergeRequests.getValue().stream().allMatch(m -> ((MergeRequest)m).getWinner().getSyncariEntityId().equals("syncariAcctId0")));
    }

    @Test
    public void testMergePostDestination() {

        ViperContext context = new ViperContext(new Organization(), new Instance(), new User());

        Connector connector = createConnector("my zendesk connector", "zendeskConnectorId");
        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());

        EntityDefinition sinkEntityDef = new EntityDefinition();
        sinkEntityDef.setConnectorId(connector.getId());
        sinkEntityDef.setApiName("Organization");
        sinkEntityDef.setDisplayName("Organization");
        sinkEntityDef.setStatus(Status.ACTIVE);
        sinkEntityDef.setId(ObjectId.get().toHexString());

        EntityDefinition sourceEntityDef = sinkEntityDef;
        AttributeDefinition coreNameAttr = createAttribute("Name", new StringType(), coreEntityDef.getId());
        AttributeDefinition sinkNameAttr = createAttribute("Name", new StringType(), sinkEntityDef.getId());
        AttributeDefinition sinkIdAttr = createAttribute("Id", new StringType(), sinkEntityDef.getId()).setIdField(true);

        AttributeDefinition coreRevenueAttribute = createAttribute("Revenue", new DoubleType(), coreEntityDef.getId());
        AttributeDefinition coreQualityAttribute = createAttribute("Quality", new StringType(), coreEntityDef.getId());
        // Make revenue field as non-updateable
        AttributeDefinition sinkRevenueAttribute = createAttribute("Revenue", new DoubleType(), sinkEntityDef.getId()).setUpdatable(false);
        AttributeDefinition sinkQualityAttribute = createAttribute("Sink Quality", new StringType(), sinkEntityDef.getId());
        coreEntityDef.addField(coreNameAttr);
        coreEntityDef.addField(coreRevenueAttribute);
        coreEntityDef.addField(coreQualityAttribute);

        sinkEntityDef.addField(sinkIdAttr);
        sinkEntityDef.addField(sinkNameAttr);
        sinkEntityDef.addField(sinkRevenueAttribute);
        sinkEntityDef.addField(sinkQualityAttribute);

        Map<String, Object> predicateMap = new HashMap<>();
        var predicates = List.of(new HashMap<>( Map.of(
                "left", Map.of("datatype", "boolean", "type", "variable", "value", "destination_status"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", true)
        )
        ));
        predicateMap.put("predicate", Map.of("predicates", predicates, "operator", "AND"));

        // entity graph
        var entityGraph = newGraph(coreEntityDef, functionService, actionDefinitionRepo)
                .src(sourceEntityDef, "sourceEntity")
                .dest(sinkEntityDef, "sinkEntity")
                .function("filter", "Is Write Success", predicateMap)
                .function("predicate", "Is True", Map.of("value", true))
                .function("setValueOnEntity", "setValueOnEntity", Map.of("newValue", "new", "attributeDefinitionId", sinkQualityAttribute.getId()))
                .action("sendEmail", "sendEmail", Map.of("recipients", List.of("test@syncari.com"), "subject", "{{record.values.Name}} {{destination_operation}} {{destination_status}}",
                        "body", new String(Base64.getEncoder().encode("{{external_record.values.Name}}".getBytes(StandardCharsets.UTF_8)),
                                StandardCharsets.UTF_8)))
                .connect("sourceEntity", coreEntityDef.getApiName())
                .connect(coreEntityDef.getApiName(), "sinkEntity")
                .connect("sinkEntity", "Is Write Success")
                .connect("Is Write Success", "Is True")
                .connect("Is True", "setValueOnEntity")
                .connect("setValueOnEntity", "sendEmail").getGraph();

        // add one attribute graph.
        var nameAttrGraph = newGraph(coreNameAttr)
                .src(sinkNameAttr, "sourceField")
                .dest(sinkNameAttr, "sinkField")
                .connect("sourceField", coreNameAttr.getApiName())
                .connect(coreNameAttr.getApiName(), "sinkField").getGraph();

        when(entityRepo.find(any(EntityDefinition.class), any(), any(PageCursor.class))).thenReturn(List.of(new EntityData()), List.of());
        when(connectorService.get("my zendesk connector")).thenReturn(connector);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        when(connectorService.find(connector.getId(), false)).thenReturn(Optional.of(connector));
        when(currentBatch.getEntityBatch(sinkEntityDef)).thenReturn(new StagedBatch(sinkEntityDef.getApiName()).setConnectorId("my zendesk connector"));
        when(currentBatch.recordsBySyncariIdIterator()).thenReturn(recordsIter);
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(recordsIter.hasNext()).thenReturn(true, true, false);
        when(recordsIter.next()).thenReturn(new RecordsBySyncariId("syncariAcctId123"));


        EntityData winner = new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("Name", "Account Name")
                .addValue("Revenue", 300.0)
                .setNew(false).setDeleted(false);

        List<EntityData> losers = List.of(new EntityData("account")
                .setSyncariEntityId("syncariAcctId345")
                .addValue("Name", "Account Name")
                .addValue("Revenue", 250)
                .setNew(false).setDeleted(false));


        MergeOperation operation = new MergeOperation();
        operation.setWinningRecord(winner);
        operation.setLosingRecords(losers);
        operation.setBatchId(currentBatch.getCurrentBatchId());
        TransactionLog log = new TransactionLog().setEntityName("account").setEntityId(coreEntityDef.getId()).setOperation(Operation.merge)
                .setSyncariId("syncariAcctId123").setAdditionalInfo(Map.of("mergeDetails", operation));
        com.syncari.core.model.pagination.Page page = new com.syncari.core.model.pagination.Page();
        page.setRecords(List.of(log));

        doReturn(page, new com.syncari.core.model.pagination.Page(new PageInfo(), new ArrayList())).when(transactionLogService)
                .findMergesByBatchId(eq("currentBatchId"), any(), eq(new PageCursor("", PageDirection.previous,500)));

        //when(transactionLogService.findMergesByBatchId(any(), any())).thenReturn(page, new com.syncari.core.model.pagination.Page());
        when(graphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(nameAttrGraph));
        when(attributeDefinitionCache.findById(coreNameAttr.getId())).thenReturn(Optional.of(coreNameAttr));
        when(attributeDefinitionCache.findById(coreRevenueAttribute.getId())).thenReturn(Optional.of(coreRevenueAttribute));
        when(attributeDefinitionCache.findById(coreQualityAttribute.getId())).thenReturn(Optional.of(coreQualityAttribute));
        when(attributeDefinitionCache.findAllById(anyIterable())).thenReturn(List.of(coreNameAttr, coreRevenueAttribute, coreQualityAttribute));

        when(idMappingRepo.findBySyncariIds("account", Set.of("syncariAcctId123")))
                .thenReturn(List.of(new IdMapping().setEntityName("account").setSyncariId("syncariAcctId123")));

        when(idMappingRepo.findExistingMapping("account", "syncariAcctId123", connector.getId(), sinkEntityDef.getId()))
                .thenReturn(Optional.of(new IdMapping().setSyncariId("syncariAcctId123").addMapping(connector.getId(), "externalId_123",sinkEntityDef.getId())));

        when(mockDataServiceFactory.getDataService(connector.getMetadata())).thenReturn(zendeskService);
        MergeResponse response = new MergeResponse();
        when(zendeskService.merge(any(MergeRequest.class))).thenReturn(response);
        when(schemaService.getEntity(sinkEntityDef.getId())).thenReturn(sinkEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);

        var coreNameAttrNode = nameAttrGraph.getCoreNode();

        var oldEvaluator = evaluator;
        saveToSink.evaluator = new JTwigPipelineEvaluator(environment, tokenHelper, actions, pipelineNodeAuditService, featureService);
        GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph);
        graphContext.put("output_"+coreNameAttrNode.getId(),new FunctionResult("Account Name",StringType.VALUE));
        when(schemaService.refreshSynapseSchema(eq(sinkEntityDef.getConnectorId()), eq(sinkEntityDef), any())).thenReturn(List.of(sinkEntityDef));
        graphContext.setSyncariEntity(coreEntityDef);


        try {
            saveToSink.execute(sinkEntityDef, context,graphContext);
        } finally {
            saveToSink.evaluator = oldEvaluator;
        }

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendHtml(any(), subject.capture(), body.capture());
        assertEquals("Account Name merge true", subject.getValue());
        assertEquals("Account Name", body.getValue());
    }

    @Test
    public void testMergeNewWinner() {

        ViperContext context = new ViperContext(new Organization(), new Instance(), new User());

        Connector connector = new Connector("my zendesk connector", "zendeskConnectorId",
                "https://someendpoint");
        connector.setId("my zendesk connector");
        connector.setMetadata(new ConnectorMetadata("zendeskConnectorId"));
        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());

        EntityDefinition sinkEntityDef = new EntityDefinition();
        sinkEntityDef.setConnectorId(connector.getId());
        sinkEntityDef.setApiName("Organization");
        sinkEntityDef.setDisplayName("Organization");
        sinkEntityDef.setStatus(Status.ACTIVE);
        sinkEntityDef.setId(ObjectId.get().toHexString());

        EntityDefinition sourceEntityDef = sinkEntityDef;
        AttributeDefinition coreNameAttr = createAttribute("Name", new StringType(), coreEntityDef.getId());
        AttributeDefinition sinkNameAttr = createAttribute("Name", new StringType(), sinkEntityDef.getId());
        AttributeDefinition sinkIdAttr = createAttribute("Id", new StringType(), sinkEntityDef.getId()).setIdField(true);

        AttributeDefinition coreRevenueAttribute = createAttribute("Revenue", new DoubleType(), coreEntityDef.getId());
        AttributeDefinition coreQualityAttribute = createAttribute("Quality", new StringType(), coreEntityDef.getId());
        // Make revenue field as non-updateable
        AttributeDefinition sinkRevenueAttribute = createAttribute("Revenue", new DoubleType(), sinkEntityDef.getId()).setUpdatable(false);
        AttributeDefinition sinkQualityAttribute = createAttribute("Sink Quality", new StringType(), sinkEntityDef.getId());
        coreEntityDef.addField(coreNameAttr);
        coreEntityDef.addField(coreRevenueAttribute);
        coreEntityDef.addField(coreQualityAttribute);

        sinkEntityDef.addField(sinkIdAttr);
        sinkEntityDef.addField(sinkNameAttr);
        sinkEntityDef.addField(sinkRevenueAttribute);
        sinkEntityDef.addField(sinkQualityAttribute);

        Map<String, Object> predicateMap = new HashMap<>();
        var predicates = List.of(new HashMap<>( Map.of(
                "left", Map.of("datatype", "boolean", "type", "variable", "value", "destination_status"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", true)
        )
        ));
        predicateMap.put("predicate", Map.of("predicates", predicates, "operator", "AND"));

        // entity graph
        var entityGraph = newGraph(coreEntityDef, functionService, actionDefinitionRepo)
                .src(sourceEntityDef, "sourceEntity")
                .dest(sinkEntityDef, "sinkEntity")
                .function("filter", "Is Write Success", predicateMap)
                .function("predicate", "Is True", Map.of("value", true))
                .function("setValueOnEntity", "setValueOnEntity", Map.of("newValue", "new", "attributeDefinitionId", sinkQualityAttribute.getId()))
                .action("sendEmail", "sendEmail", Map.of("recipients", List.of("test@syncari.com"), "subject", "{{record.values.Name}} {{destination_operation}} {{destination_status}}",
                        "body", new String(Base64.getEncoder().encode("{{external_record.values.Name}}".getBytes(StandardCharsets.UTF_8)),
                                StandardCharsets.UTF_8)))
                .connect("sourceEntity", coreEntityDef.getApiName())
                .connect(coreEntityDef.getApiName(), "sinkEntity")
                .connect("sinkEntity", "Is Write Success")
                .connect("Is Write Success", "Is True")
                .connect("Is True", "setValueOnEntity")
                .connect("setValueOnEntity", "sendEmail").getGraph();

        // add one attribute graph.
        var nameAttrGraph = newGraph(coreNameAttr)
                .src(sinkNameAttr, "sourceField")
                .dest(sinkNameAttr, "sinkField")
                .connect("sourceField", coreNameAttr.getApiName())
                .connect(coreNameAttr.getApiName(), "sinkField").getGraph();

        when(connectorService.get("my zendesk connector")).thenReturn(connector);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        when(connectorService.find(connector.getId(), false)).thenReturn(Optional.of(connector));
        when(currentBatch.getEntityBatch(sinkEntityDef)).thenReturn(new StagedBatch(sinkEntityDef.getApiName()).setConnectorId("my zendesk connector"));
        when(currentBatch.recordsBySyncariIdIterator()).thenReturn(recordsIter);
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(recordsIter.hasNext()).thenReturn(true, true, false);
        when(recordsIter.next()).thenReturn(new RecordsBySyncariId("syncariAcctIdWinner"));

        // generate merge operations
        EntityData winner = new EntityData("account")
                .setSyncariEntityId("syncariAcctIdWinner")
                .addValue("Name", "Account Name")
                .addValue("Revenue", 300.0)
                .setNew(false).setDeleted(false);

        List<EntityData> losers = List.of(new EntityData("account")
                .setSyncariEntityId("syncariAcctIdLoser")
                .addValue("Name", "Account Name")
                .addValue("Revenue", 250)
                .setNew(false).setDeleted(false));

        MergeOperation operation = new MergeOperation();
        operation.setWinningRecord(winner);
        operation.setLosingRecords(losers);
        operation.setBatchId(currentBatch.getCurrentBatchId());
        var log = new TransactionLog().setEntityName("account").setEntityId(coreEntityDef.getId()).setOperation(Operation.merge)
                .setSyncariId("syncariAcctIdWinner").setAdditionalInfo(Map.of("mergeDetails", operation));

        com.syncari.core.model.pagination.Page page = new com.syncari.core.model.pagination.Page();
        page.setRecords(List.of(log));

        doReturn(page, new com.syncari.core.model.pagination.Page(new PageInfo(), new ArrayList())).when(transactionLogService)
                .findMergesByBatchId(eq("currentBatchId"), any(), eq(new PageCursor("", PageDirection.previous,500)));

        //when(transactionLogService.findMergesByBatchId(any(), any())).thenReturn(page, new com.syncari.core.model.pagination.Page());
        when(graphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(nameAttrGraph));
        when(attributeDefinitionCache.findById(coreNameAttr.getId())).thenReturn(Optional.of(coreNameAttr));
        when(attributeDefinitionCache.findById(coreRevenueAttribute.getId())).thenReturn(Optional.of(coreRevenueAttribute));
        when(attributeDefinitionCache.findById(coreQualityAttribute.getId())).thenReturn(Optional.of(coreQualityAttribute));
        when(attributeDefinitionCache.findAllById(anyIterable())).thenReturn(List.of(coreNameAttr, coreRevenueAttribute, coreQualityAttribute));

        when(idMappingRepo.findBySyncariIds("account", Set.of("syncariAcctIdWinner")))
                .thenReturn(List.of(new IdMapping().setEntityName("account").setSyncariId("syncariAcctIdWinner")));

        when(mockDataServiceFactory.getDataService(connector.getMetadata())).thenReturn(zendeskService);
        MergeResponse response = new MergeResponse();
        when(zendeskService.merge(any(MergeRequest.class))).thenReturn(response);
        when(schemaService.getEntity(sinkEntityDef.getId())).thenReturn(sinkEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);

        var coreNameAttrNode = nameAttrGraph.getCoreNode();

        var oldEvaluator = evaluator;
        saveToSink.evaluator = new JTwigPipelineEvaluator(environment, tokenHelper, actions, pipelineNodeAuditService, featureService);
        GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph);
        graphContext.put("output_"+coreNameAttrNode.getId(),new FunctionResult("Account Name",StringType.VALUE));

        try {
            saveToSink.execute(sinkEntityDef, context,graphContext);
        } finally {
            saveToSink.evaluator = oldEvaluator;
        }

        verify(zendeskService, times(0)).merge(anyList());
    }

    @Test
    public void createOnlyFieldUpdate() {
        ViperContext context = new ViperContext(new Organization(), new Instance(), new User());

        Connector connector = new Connector("my zendesk connector", "zendeskConnectorId",
                "https://someendpoint");
        connector.setId("my zendesk connector");
        connector.setMetadata(new ConnectorMetadata("zendeskConnectorId"));
        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());

        EntityDefinition sinkEntityDef = new EntityDefinition();
        sinkEntityDef.setConnectorId(connector.getId());
        sinkEntityDef.setApiName("Organization");
        sinkEntityDef.setDisplayName("Organization");
        sinkEntityDef.setStatus(Status.ACTIVE);
        sinkEntityDef.setId(ObjectId.get().toHexString());
        AttributeDefinition coreNameAttr = createAttribute("Name", new StringType(), coreEntityDef.getId());
        AttributeDefinition sinkNameAttr = createAttribute("Name", new StringType(), sinkEntityDef.getId()).setUpdatable(false);


        AttributeDefinition coreRevenueAttribute = createAttribute("Revenue", new DoubleType(), coreEntityDef.getId());
        AttributeDefinition coreQualityAttribute = createAttribute("Quality", new StringType(), coreEntityDef.getId());
        // Make revenue field as non-updateable
        AttributeDefinition sinkRevenueAttribute = createAttribute("Revenue", new DoubleType(), sinkEntityDef.getId()).setUpdatable(false);

        AttributeDefinition sinkCreateOnlyAttribute = createAttribute("CreateOnly", new StringType(), sinkEntityDef.getId()).setUpdatable(false);
        AttributeDefinition coreCreateOnlyAttribute = createAttribute("CreateOnly", new StringType(), coreEntityDef.getId());

        MappingGraph entityGraph = createGraph(coreEntityDef.getId(), Scope.ENTITY);
        MappingNode coreNode = coreEntityNode(coreEntityDef, entityGraph);
        MappingNode sinkNode = coreSinkNode(sinkEntityDef, entityGraph);

        Edge coreToSink = edge(coreNode, sinkNode, entityGraph);
        coreToSink.setId(ObjectId.get().toHexString());

        MappingGraph nameAttrGraph = createGraph(coreNameAttr.getId(), Scope.ATTRIBUTE);
        MappingGraph revAttrGraph = createGraph(coreRevenueAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph qualiytAttrGraph = createGraph(coreQualityAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph createOnlytAttrGraph = createGraph(coreCreateOnlyAttribute.getId(), Scope.ATTRIBUTE);

        MappingNode coreNameAttrNode = coreAttributeNode(coreNameAttr, nameAttrGraph);
        MappingNode sinkNameAttrNode = sinkAttributeNode(sinkNameAttr, nameAttrGraph);
        Edge coreAttrToSink = edge(coreNameAttrNode, sinkNameAttrNode, nameAttrGraph);

        MappingNode coreQAttrNode = coreAttributeNode(coreQualityAttribute, qualiytAttrGraph);
        //MappingNode sinkQAttrNode = sinkAttributeNode(sinkQualityAttribute, qualiytAttrGraph);
        //Edge coreQToSinkQ = edge(coreQAttrNode, sinkQAttrNode, qualiytAttrGraph);

        var sinkRevAttrNode = sinkAttributeNode(sinkRevenueAttribute, revAttrGraph);
        var coreRevAttrNode = coreAttributeNode(coreRevenueAttribute, revAttrGraph);
        Edge revenueCoreToSink = edge(coreRevAttrNode, sinkRevAttrNode, revAttrGraph);

        var sinkCreateOnlyAttrNode = sinkAttributeNode(sinkCreateOnlyAttribute, createOnlytAttrGraph);
        var coreCreateOnlyAttrNode = coreAttributeNode(coreCreateOnlyAttribute, createOnlytAttrGraph);
        Edge createOnlyCoreToSink = edge(coreCreateOnlyAttrNode, sinkCreateOnlyAttrNode, createOnlytAttrGraph);

        sinkEntityDef.addField(sinkNameAttr);
        sinkEntityDef.addField(sinkRevenueAttribute);
        //sinkEntityDef.addField(sinkQualityAttribute);
        sinkEntityDef.addField(sinkCreateOnlyAttribute);
        TransactionLog log = new TransactionLog()
                .setEntityName("account")
                .setEntityId(coreEntityDef.getId())
                .setNew(false)
                .setSyncariId("syncariAcctId123")
                .setOccurredAt(System.currentTimeMillis())
                .addSource("my zendesk connector", "my zendesk connector", "externalDefnitionId", "externalZDId", System.currentTimeMillis())
                .addChange(new FieldChange().setFieldId(coreCreateOnlyAttribute.getId()).setOldValue("This is value").setNewValue("This should be discarded").setApiName(coreCreateOnlyAttribute.getApiName()));
        log.setId(ObjectId.get().toHexString());
        when(txLogRepo.findAllStream()).thenReturn(Stream.empty());
        when(transactionLogService.findMergesByBatchId("currentBatchId", Date.from(Instant.EPOCH), new PageCursor("", PageDirection.previous,500)))
                .thenReturn(new com.syncari.core.model.pagination.Page<TransactionLog>(new PageInfo(), new ArrayList<>()));
        when(txLogRepo.findByBatchId("currentBatchId", Pageable.unpaged())).thenReturn(Page.empty());
        when(txLogRepo.findByBatchIdAndSyncariIdIn("currentBatchId", List.of("syncariAcctId123"))).thenReturn(List.of());
        when(txLogRepo.findById(log.getId())).thenReturn(Optional.of(log));
        when(connectorService.get("my zendesk connector")).thenReturn(connector);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        when(currentBatch.getEntityBatch(sinkEntityDef)).thenReturn(new StagedBatch(sinkEntityDef.getApiName()).setConnectorId("my zendesk connector"));
        when(currentBatch.recordsBySyncariIdIterator()).thenReturn(recordsIter);
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(recordsIter.hasNext()).thenReturn(true, true, false);
        when(recordsIter.next()).thenReturn(new RecordsBySyncariId("syncariAcctId123"));

        List<EntityData> t = List.of(new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("Revenue", 300.0)
                .addValue("CreateOnly", "This should be discarded")
                .setLastTransactionLogId(log.getId())
                .setNew(false));
        when(entityRepo.find(any(EntityDefinition.class), any(), any(PageCursor.class))).thenReturn(t);
        when(graphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(nameAttrGraph, revAttrGraph, qualiytAttrGraph, createOnlytAttrGraph));
        when(attributeDefinitionCache.findById(coreNameAttr.getId())).thenReturn(Optional.of(coreNameAttr));
        when(attributeDefinitionCache.findById(coreRevenueAttribute.getId())).thenReturn(Optional.of(coreRevenueAttribute));
        when(attributeDefinitionCache.findById(coreQualityAttribute.getId())).thenReturn(Optional.of(coreQualityAttribute));
        when(attributeDefinitionCache.findById(coreCreateOnlyAttribute.getId())).thenReturn(Optional.of(coreCreateOnlyAttribute));
        when(attributeDefinitionCache.findAllById(anyIterable())).thenReturn(List.of(coreNameAttr, coreRevenueAttribute, coreQualityAttribute, coreCreateOnlyAttribute));
        //id mapping present to make it an update operation
        when(idMappingRepo.findBySyncariIds("account", Set.of("syncariAcctId123")))
                .thenReturn(List.of(new IdMapping().setEntityName("account").setSyncariId("syncariAcctId123").setMappings(List.of(
                        IdMapping.mapping(connector.getId(), "syncariAcctId123", sinkEntityDef.getId(), "syncariAcctId123")))));
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx = m.getArgument(2);
            ctx.put("output_" + coreNameAttrNode.getId(), new FunctionResult("Account Name", StringType.VALUE));
            return null;
        }).when(evaluator).evaluate(eq(sinkNameAttrNode), eq(nameAttrGraph), any(GraphContext.class), eq(n -> n.getType() == MappingNodeType.CORE_ATTRIBUTE), any());
        doAnswer((Answer<Void>) m -> {
            GraphContext ctx = m.getArgument(2);
            ctx.put("output_" + coreRevAttrNode.getId(), new FunctionResult(300.0, DoubleType.VALUE));
            return null;
        }).when(evaluator).evaluate(eq(sinkRevAttrNode), eq(revAttrGraph), any(GraphContext.class), eq(n -> n.getType() == MappingNodeType.CORE_ATTRIBUTE), any());
        when(mockDataServiceFactory.getDataService(connector.getMetadata())).thenReturn(zendeskService);
        SyncResponse response = new SyncResponse();
        //when(zendeskService.update(any())).thenThrow(new RetriableException(ErrorCodes.LOGIN_ERROR, "", ""));
        when(schemaService.getEntity(sinkEntityDef.getId())).thenReturn(sinkEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        saveToSink.execute(sinkEntityDef, context, new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph));
        verify(zendeskService, never()).update(any());
    }

    private MappingGraph createGraph(String targetId, Scope scope) {
        MappingGraph attrGraph = new MappingGraph();
        attrGraph.setId(ObjectId.get().toHexString());
        attrGraph.setTargetId(targetId);
        attrGraph.setScope(scope);
        return attrGraph;
    }

    private MappingNode coreSinkNode(EntityDefinition sinkEntityDef, MappingGraph entityGraph) {
        MappingNode sinkNode = new MappingNode().setScope(Scope.ENTITY).setConfiguration(new EntitySinkNodeConfig().setEntityDefinition(sinkEntityDef));
        sinkNode.setId(ObjectId.get().toHexString());
        entityGraph.getNodes().add(sinkNode);
        return sinkNode;
    }

    private MappingNode function(Scope scope, FunctionDefinition function, MappingGraph graph, String... paramNames) {
        FunctionCall functionCall = new FunctionCall().setFunctionDefinition(function);
        List<ParameterValue> inputs = Seq.zip(Arrays.asList(paramNames).stream(), function.getPositionalParams().stream()).map(t -> new ParameterValue(t.v2.getDatatype(), t.v1, "input")).collect(Collectors.toList());
        functionCall.setParams(inputs);
        MappingNode node = new MappingNode().setScope(scope).setName(function.getDisplayName()).setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(functionCall));
        node.setId(ObjectId.get().toHexString());
        graph.getNodes().add(node);
        return node;
    }

    private MappingNode coreEntityNode(EntityDefinition coreEntityDef, MappingGraph entityGraph) {
        MappingNode coreNode = new MappingNode().setScope(Scope.ENTITY).setConfiguration(new CoreEntityNodeConfig().setEntityDefinition(coreEntityDef));
        coreNode.setId(ObjectId.get().toHexString());
        entityGraph.getNodes().add(coreNode);
        return coreNode;
    }

    private Edge edge(MappingNode from, MappingNode to, MappingGraph graph) {
        Edge edge = new Edge().setDestinationStage(to)
                .setInput(to.getConfiguration().getInputPorts()
                        .get(0)).setSourceStage(from).setOutput(from.getConfiguration().getOutputPorts().get(0));
        edge.setId(ObjectId.get().toHexString());
        graph.getEdges().add(edge);
        return edge;
    }

    private MappingNode sinkAttributeNode(AttributeDefinition attribute, MappingGraph graph) {
        MappingNode sinkAttrNode = new MappingNode().setScope(Scope.ATTRIBUTE).setConfiguration(new AttributeSinkNodeConfig().setAttributeDefinition(attribute))
                .setName(attribute.getApiName()).setApiName(attribute.getApiName());
        sinkAttrNode.setId(ObjectId.get().toHexString());
        graph.getNodes().add(sinkAttrNode);
        return sinkAttrNode;
    }

    private MappingNode coreAttributeNode(AttributeDefinition coreAttribute,MappingGraph graph) {
        MappingNode coreAttrNode = new MappingNode().setScope(Scope.ATTRIBUTE).setConfiguration(new CoreAttributeNodeConfig()
                .setAttributeDefinition(coreAttribute)).setName(coreAttribute.getApiName());

        coreAttrNode.setId(ObjectId.get().toHexString());
        graph.getNodes().add(coreAttrNode);
        return coreAttrNode;
    }

    private AttributeDefinition createAttribute(String name, Datatype datatype, String entityId) {
        var attr = new AttributeDefinition();
        attr.setApiName(name);
        attr.setDataType(datatype);
        attr.setEntityId(entityId);
        attr.setStatus(Status.ACTIVE);
        attr.setId(ObjectId.get().toHexString());
        return attr;
    }

    private AttributeDefinition createReferenceAttribute(String name, String entityId) {
        var attr = new AttributeDefinition();
        attr.setApiName(name);
        attr.setReferenceTo("user");
        attr.setDataType(ReferenceType.VALUE);
        attr.setEntityId(entityId);
        attr.setId(ObjectId.get().toHexString());
        return attr;
    }

}
