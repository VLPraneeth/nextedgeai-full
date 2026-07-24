package com.syncari.viper.streams.stages;

import com.syncari.AbstractSyncariTest;
import com.syncari.connector.EntityData;
import com.syncari.core.datatype.DoubleType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.*;
import com.syncari.core.model.util.Scope;
import com.syncari.core.pipeline.BatchActionContext;
import com.syncari.core.pipeline.BatchedOperations;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.repositories.customer.IdMappingRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.FunctionService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.sync.CurrentBatch;
import com.syncari.core.sync.RecordsBySyncariId;
import com.syncari.core.utils.SchemaHelper;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.util.*;
import java.util.function.BiFunction;

import static com.syncari.core.utils.GraphHelper.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;


public class FieldPipelineTest extends AbstractSyncariTest {
    @MockBean
    IdMappingRepo idMappingRepo;
    @MockBean
    SchemaService schemaService;
    @MockBean
    EntityRepo entityRepo;
    @MockBean
    ConnectorService connectorService;
    @Autowired
    ExecuteFieldPipeline executeFieldPipeline;
    @Autowired
    FunctionService functionService;
    FieldPipelineTestHelper helper;
    @MockBean
    BiFunction<EntityDefinition, Document, EntityData> entityCreate;


    private Connector syncariConnector;

    @Before
    public void init() {
        helper = new FieldPipelineTestHelper(functionService, schemaService,entityRepo, connectorService,executeFieldPipeline);
        doNothing().when(eventService).log(any());
    }

    @Override
    public void setUp() {
        if(syncariConnector == null){
            syncariConnector = createConnector("syncari", "syncariConnId", "syncariConnMetaId");
        }
        when(connectorService.getSyncariConnector()).thenReturn(syncariConnector);
        when(connectorService.refreshAuthentication(any(Connector.class))).then(returnsFirstArg());
        super.setUp();
    }

    @Test
    public void simpleUpperCase() {

        Connector connector = createConnector("my zendesk connector", "my zendesk connector", "zendeskConnectorId");
        EntityDefinition coreEntityDef = SchemaHelper.createEntityDef("account", "Account", null);

        EntityDefinition srcEntityDef = SchemaHelper.createEntityDef("Organization", "Organization", connector);

        AttributeDefinition coreNameAttr = SchemaHelper.createAttribute("Name", new StringType(), coreEntityDef.getId());

        AttributeDefinition srcNameAttr = SchemaHelper.createAttribute("Name", new StringType(), srcEntityDef.getId());


        AttributeDefinition coreRevenueAttribute = SchemaHelper.createAttribute("Revenue", new DoubleType(), coreEntityDef.getId());

        AttributeDefinition coreQualityAttribute = SchemaHelper.createAttribute("Quality", new StringType(), coreEntityDef.getId());

        AttributeDefinition srcRevenueAttribute = SchemaHelper.createAttribute("Revenue", new DoubleType(), srcEntityDef.getId());

        AttributeDefinition srcQualityAttribute = SchemaHelper.createAttribute("Sink Quality", new StringType(), srcEntityDef.getId());
        coreEntityDef.addField(coreNameAttr);
        coreEntityDef.addField(coreQualityAttribute);
        coreEntityDef.addField(coreRevenueAttribute);

        MappingGraph entityGraph = createGraph(coreEntityDef.getId(), Scope.ENTITY);
        MappingNode coreNode = coreEntityNode(coreEntityDef, entityGraph);
        MappingNode srcNode = srcEntityNode(srcEntityDef, entityGraph);

        Edge srcToCore = edge(srcNode, coreNode, entityGraph);

        MappingGraph nameAttrGraph = createGraph(coreNameAttr.getId(), Scope.ATTRIBUTE);
        MappingGraph revAttrGraph = createGraph(coreRevenueAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph qualiytAttrGraph = createGraph(coreQualityAttribute.getId(), Scope.ATTRIBUTE);

        MappingNode coreNameAttrNode = coreAttributeNode(coreNameAttr, nameAttrGraph);
        MappingNode srcNameAttrNode = srcAttributeNode(srcNameAttr, nameAttrGraph);
        Edge srcToCoreNameAttr = edge(srcNameAttrNode, coreNameAttrNode, nameAttrGraph);

        MappingNode coreQAttrNode = coreAttributeNode(coreQualityAttribute, qualiytAttrGraph);
        MappingNode srcQAttrNode = srcAttributeNode(srcQualityAttribute, qualiytAttrGraph);

        MappingNode uppercase = createFunctionNode(srcQAttrNode, func("upper", Scope.ATTRIBUTE), Scope.ATTRIBUTE, Map.of(), srcQualityAttribute.getDataType());
        qualiytAttrGraph.getNodes().add(uppercase);
        Edge srcToFilterQ = edge(srcQAttrNode, uppercase, qualiytAttrGraph);
        Edge filterToCoreQ = edge(uppercase, coreQAttrNode, qualiytAttrGraph);
        var srcRevAttrNode = srcAttributeNode(srcRevenueAttribute, revAttrGraph);
        var coreRevAttrNode = coreAttributeNode(coreRevenueAttribute, revAttrGraph);
        Edge srcToCoreRev = edge(srcRevAttrNode, coreRevAttrNode, revAttrGraph);
        String syncariId = ObjectId.get().toHexString();
        srcEntityDef.addField(srcNameAttr);
        srcEntityDef.addField(srcRevenueAttribute);
        srcEntityDef.addField(srcQualityAttribute);

        EntityData entityData = new EntityData("account")
                .setSyncariEntityId(syncariId)
                .addValue("Name", "Account Name")
                .addValue("Revenue", 55)
                .addValue("Quality", "BAD");
        List<EntityData> t = List.of(entityData);

        when(schemaService.getEntity(srcEntityDef.getId())).thenReturn(srcEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(connector);
        when(entityRepo.findById(coreEntityDef, syncariId)).thenReturn(Optional.of(entityData));
        when(schemaService.getAttribute(srcNameAttr.getId())).thenReturn(srcNameAttr);
        when(schemaService.getAttribute(srcQualityAttribute.getId())).thenReturn(srcQualityAttribute);
        when(schemaService.getAttribute(srcRevenueAttribute.getId())).thenReturn(srcRevenueAttribute);


        GraphContext currentContext = new GraphContext();
        currentContext.setCurrentBatch(createCurrentBatch());
        currentContext.set("field_" + srcQualityAttribute.getId(), "good");
        currentContext.set("field_" + srcRevenueAttribute.getId(), 300.0d);
        currentContext.set("field_" + srcNameAttr.getId(), "Account Name");
        currentContext.setGraph(entityGraph);
        RecordsBySyncariId recordsBySyncariId = new RecordsBySyncariId(syncariId);

        FieldsGraphRequest request = new FieldsGraphRequest().setEntityName("account")
                .setRecords(recordsBySyncariId)
                .setGraphContext(currentContext)
                .setAttributeDAGs(Map.of(coreQualityAttribute, qualiytAttrGraph))
                .setSyncariEntityDef(coreEntityDef)
                .setAttributeBatchActionContext(new BatchActionContext())
                .setBatchedOperations(new BatchedOperations()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>());

        Change change = executeFieldPipeline.createSyncariEntityWithGraph(request);

        assertTrue(change.getChanges().has("Quality"));
        assertEquals("GOOD", change.getChanges().getValue("Quality"));
        assertTrue(change.getTransactionLog().getChange(coreQualityAttribute.getId()).isPresent());

    }

    @Test
    public void regex() {

        Connector connector = createConnector("my zendesk connector", "my zendesk connector", "zendeskConnectorId");
        EntityDefinition coreEntityDef = SchemaHelper.createEntityDef("account", "Account", null);

        EntityDefinition srcEntityDef = SchemaHelper.createEntityDef("Organization", "Organization", connector);

        AttributeDefinition coreNameAttr = SchemaHelper.createAttribute("Name", new StringType(), coreEntityDef.getId());

        AttributeDefinition srcNameAttr = SchemaHelper.createAttribute("Name", new StringType(), srcEntityDef.getId());


        AttributeDefinition coreRevenueAttribute = SchemaHelper.createAttribute("Revenue", new DoubleType(), coreEntityDef.getId());

        AttributeDefinition coreQualityAttribute = SchemaHelper.createAttribute("Quality", new StringType(), coreEntityDef.getId());

        AttributeDefinition srcRevenueAttribute = SchemaHelper.createAttribute("Revenue", new DoubleType(), srcEntityDef.getId());

        AttributeDefinition srcQualityAttribute = SchemaHelper.createAttribute("Sink Quality", new StringType(), srcEntityDef.getId());
        coreEntityDef.addField(coreNameAttr);
        coreEntityDef.addField(coreQualityAttribute);
        coreEntityDef.addField(coreRevenueAttribute);

        MappingGraph entityGraph = createGraph(coreEntityDef.getId(), Scope.ENTITY);
        MappingNode coreNode = coreEntityNode(coreEntityDef, entityGraph);
        MappingNode srcNode = srcEntityNode(srcEntityDef, entityGraph);

        Edge srcToCore = edge(srcNode, coreNode, entityGraph);

        MappingGraph nameAttrGraph = createGraph(coreNameAttr.getId(), Scope.ATTRIBUTE);
        MappingGraph revAttrGraph = createGraph(coreRevenueAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph qualiytAttrGraph = createGraph(coreQualityAttribute.getId(), Scope.ATTRIBUTE);

        MappingNode coreNameAttrNode = coreAttributeNode(coreNameAttr, nameAttrGraph);
        MappingNode srcNameAttrNode = srcAttributeNode(srcNameAttr, nameAttrGraph);
        Edge srcToCoreNameAttr = edge(srcNameAttrNode, coreNameAttrNode, nameAttrGraph);

        MappingNode coreQAttrNode = coreAttributeNode(coreQualityAttribute, qualiytAttrGraph);
        MappingNode srcQAttrNode = srcAttributeNode(srcQualityAttribute, qualiytAttrGraph);

        MappingNode replace = createFunctionNode(srcQAttrNode, func("replace", Scope.ATTRIBUTE), Scope.ATTRIBUTE, Map.of("searchExpression","[^0-9]","replaceWith",""), srcQualityAttribute.getDataType());
        qualiytAttrGraph.getNodes().add(replace);
        Edge srcToFilterQ = edge(srcQAttrNode, replace, qualiytAttrGraph);
        Edge filterToCoreQ = edge(replace, coreQAttrNode, qualiytAttrGraph);
        var srcRevAttrNode = srcAttributeNode(srcRevenueAttribute, revAttrGraph);
        var coreRevAttrNode = coreAttributeNode(coreRevenueAttribute, revAttrGraph);
        Edge srcToCoreRev = edge(srcRevAttrNode, coreRevAttrNode, revAttrGraph);
        String syncariId = ObjectId.get().toHexString();
        srcEntityDef.addField(srcNameAttr);
        srcEntityDef.addField(srcRevenueAttribute);
        srcEntityDef.addField(srcQualityAttribute);

        EntityData entityData = new EntityData("account")
                .setSyncariEntityId(syncariId)
                .addValue("Name", "Account Name")
                .addValue("Revenue", 55)
                .addValue("Quality", "BAD");
        List<EntityData> t = List.of(entityData);

        when(schemaService.getEntity(srcEntityDef.getId())).thenReturn(srcEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(connector);
        when(entityRepo.findById(coreEntityDef, syncariId)).thenReturn(Optional.of(entityData));
        when(schemaService.getAttribute(srcNameAttr.getId())).thenReturn(srcNameAttr);
        when(schemaService.getAttribute(srcQualityAttribute.getId())).thenReturn(srcQualityAttribute);
        when(schemaService.getAttribute(srcRevenueAttribute.getId())).thenReturn(srcRevenueAttribute);


        GraphContext currentContext = new GraphContext();
        currentContext.setCurrentBatch(createCurrentBatch());
        currentContext.set("field_" + srcQualityAttribute.getId(), "901 - some name");
        currentContext.set("field_" + srcRevenueAttribute.getId(), 300.0d);
        currentContext.set("field_" + srcNameAttr.getId(), "Account Name");
        currentContext.setGraph(entityGraph);
        RecordsBySyncariId recordsBySyncariId = new RecordsBySyncariId(syncariId);
        FieldsGraphRequest request = new FieldsGraphRequest().setEntityName("account")
                .setRecords(recordsBySyncariId)
                .setGraphContext(currentContext)
                .setAttributeDAGs(Map.of(coreQualityAttribute, qualiytAttrGraph))
                .setSyncariEntityDef(coreEntityDef)
                .setAttributeBatchActionContext(new BatchActionContext())
                .setBatchedOperations(new BatchedOperations()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>());

        Change change = executeFieldPipeline.createSyncariEntityWithGraph(request);

        assertTrue(change.getChanges().has("Quality"));
        assertEquals("901", change.getChanges().getValue("Quality"));
        assertTrue(change.getTransactionLog().getChange(coreQualityAttribute.getId()).isPresent());

    }

    @Test
    public void testDataAuthorityLog() {
        /*
           1. Two source entities and core entity
           2. Field1 - Connector1 - winner
           3. Field2 - Connector2 - winner
           4. Field3 - No winner, latest date?
         */

        Connector connector1 = createConnector("my zendesk connector 1", "my zendesk connector1", "zendeskConnectorId");
        Connector connector2 = createConnector("my zendesk connector 2", "my zendesk connector2", "zendeskConnectorId");


        EntityDefinition coreEntityDef = SchemaHelper.createEntityDef("account", "Account", null);

        EntityDefinition srcEntityDef1 = SchemaHelper.createEntityDef("Organization1", "Organization 1", connector1);
        EntityDefinition srcEntityDef2 = SchemaHelper.createEntityDef("Organization2", "Organization 2", connector2);

        AttributeDefinition coreNameAttr = SchemaHelper.createAttribute("Name", new StringType(), coreEntityDef.getId());

        AttributeDefinition srcNameAttr1 = SchemaHelper.createAttribute("Name", new StringType(), srcEntityDef1.getId());
        AttributeDefinition srcNameAttr2 = SchemaHelper.createAttribute("Name", new StringType(), srcEntityDef2.getId());

        AttributeDefinition coreRevenueAttribute = SchemaHelper.createAttribute("Revenue", new DoubleType(), coreEntityDef.getId());
        AttributeDefinition coreQualityAttribute = SchemaHelper.createAttribute("Quality", new StringType(), coreEntityDef.getId());

        AttributeDefinition srcRevenueAttribute1 = SchemaHelper.createAttribute("Revenue", new DoubleType(), srcEntityDef1.getId());
        AttributeDefinition srcRevenueAttribute2 = SchemaHelper.createAttribute("Revenue", new DoubleType(), srcEntityDef2.getId());

        AttributeDefinition srcQualityAttribute1 = SchemaHelper.createAttribute("Sink Quality", new StringType(), srcEntityDef1.getId());
        AttributeDefinition srcQualityAttribute2 = SchemaHelper.createAttribute("Sink Quality", new StringType(), srcEntityDef2.getId());

        coreEntityDef.addField(coreNameAttr);
        coreEntityDef.addField(coreQualityAttribute);
        coreEntityDef.addField(coreRevenueAttribute);

        srcEntityDef1.addField(srcNameAttr1);
        srcEntityDef1.addField(srcRevenueAttribute1);
        srcEntityDef1.addField(srcQualityAttribute1);

        srcEntityDef2.addField(srcNameAttr2);
        srcEntityDef2.addField(srcRevenueAttribute2);
        srcEntityDef2.addField(srcQualityAttribute2);

        MappingGraph entityGraph = createGraph(coreEntityDef.getId(), Scope.ENTITY);
        MappingNode coreNode = coreEntityNode(coreEntityDef, entityGraph);
        MappingNode srcNode1 = srcEntityNode(srcEntityDef1, entityGraph);
        MappingNode srcNode2 = srcEntityNode(srcEntityDef2, entityGraph);

        Edge srcToCore1 = edge(srcNode1, coreNode, entityGraph);
        Edge srcToCore2 = edge(srcNode2, coreNode, entityGraph);

        MappingGraph nameAttrGraph = createGraph(coreNameAttr.getId(), Scope.ATTRIBUTE);
        MappingGraph revAttrGraph = createGraph(coreRevenueAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph qualiytAttrGraph = createGraph(coreQualityAttribute.getId(), Scope.ATTRIBUTE);

        MappingNode coreNameAttrNode = coreAttributeNode(coreNameAttr, nameAttrGraph);
        ((CoreAttributeNodeConfig)coreNameAttrNode.getConfiguration()).setDataAuthority(DataAuthority.selectedConnector(connector2.getId()));

        MappingNode coreRevenueAttrNode = coreAttributeNode(coreRevenueAttribute, revAttrGraph);
        ((CoreAttributeNodeConfig)coreRevenueAttrNode.getConfiguration()).setDataAuthority(DataAuthority.latest());

        MappingNode coreQualityAttrNode = coreAttributeNode(coreQualityAttribute, qualiytAttrGraph);
        ((CoreAttributeNodeConfig)coreQualityAttrNode.getConfiguration()).setDataAuthority(DataAuthority.none());

        MappingNode srcNameAttrNode1 = srcAttributeNode(srcNameAttr1, nameAttrGraph);
        MappingNode srcNameAttrNode2 = srcAttributeNode(srcNameAttr2, nameAttrGraph);
        edge(srcNameAttrNode1, coreNameAttrNode, nameAttrGraph);
        edge(srcNameAttrNode2, coreNameAttrNode, nameAttrGraph);

        MappingNode srcRevenueAttrNode1 = srcAttributeNode(srcRevenueAttribute1, revAttrGraph);
        MappingNode srcRevenueAttrNode2 = srcAttributeNode(srcRevenueAttribute2, revAttrGraph);
        edge(srcRevenueAttrNode1, coreRevenueAttrNode, revAttrGraph);
        edge(srcRevenueAttrNode2, coreRevenueAttrNode, revAttrGraph);

        MappingNode srcQualityAttrNode1 = srcAttributeNode(srcQualityAttribute1, qualiytAttrGraph);
        MappingNode srcQualityAttrNode2 = srcAttributeNode(srcQualityAttribute2, qualiytAttrGraph);
        edge(srcQualityAttrNode1, coreQualityAttrNode, qualiytAttrGraph);
        edge(srcQualityAttrNode2, coreQualityAttrNode, qualiytAttrGraph);

        String syncariId = ObjectId.get().toHexString();
        EntityData entityData1 = new EntityData("account")
                .setSyncariEntityId(syncariId)
                .setConnectorId(connector1.getId())
                .addValue("Name", "Account Name 1")
                .addValue("Revenue", 300.0d)
                .addValue("Quality", "901 - some name")
                .setLastModified(Instant.now().toEpochMilli());

        EntityData entityData2 = new EntityData("account")
                .setSyncariEntityId(syncariId)
                .setConnectorId(connector2.getId())
                .addValue("Name", "Account Name 2")
                .addValue("Revenue", 400.0d)
                .addValue("Quality", "902 - some name")
                .setLastModified(Instant.now().minusSeconds(1).toEpochMilli());

        StagedBatchRecord stagedRecord1 = new StagedBatchRecord().setEntityData(entityData1).
                setExternalEntityDefinitionId(srcEntityDef1.getId())
                .setExternalRecordId("externalId1").setSyncariId(syncariId);
        StagedBatchRecord stagedRecord2 = new StagedBatchRecord().setEntityData(entityData2).
                setExternalEntityDefinitionId(srcEntityDef2.getId())
                .setExternalRecordId("externalId2").setSyncariId(syncariId);

        when(schemaService.getEntity(srcEntityDef1.getId())).thenReturn(srcEntityDef1);
        when(schemaService.getEntity(srcEntityDef2.getId())).thenReturn(srcEntityDef2);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(srcEntityDef1.getConnectorId())).thenReturn(connector1);
        when(connectorService.get(srcEntityDef2.getConnectorId())).thenReturn(connector2);
        //when(entityRepo.findById(coreEntityDef, syncariId)).thenReturn(Optional.of(entityData));
        when(schemaService.getAttribute(srcNameAttr1.getId())).thenReturn(srcNameAttr1);
        when(schemaService.getAttribute(srcNameAttr2.getId())).thenReturn(srcNameAttr2);
        when(schemaService.getAttribute(srcQualityAttribute1.getId())).thenReturn(srcQualityAttribute1);
        when(schemaService.getAttribute(srcQualityAttribute2.getId())).thenReturn(srcQualityAttribute2);
        when(schemaService.getAttribute(srcRevenueAttribute1.getId())).thenReturn(srcRevenueAttribute1);
        when(schemaService.getAttribute(srcRevenueAttribute2.getId())).thenReturn(srcRevenueAttribute2);


        GraphContext currentContext = new GraphContext();
        currentContext.setCurrentBatch(createCurrentBatch());
        currentContext.set("field_" + srcQualityAttribute1.getId(), "901 - some name");
        currentContext.set("field_" + srcRevenueAttribute1.getId(), 300.0d);
        currentContext.set("field_" + srcNameAttr1.getId(), "Account Name 1");
        currentContext.set("field_" + srcQualityAttribute2.getId(), "902 - some name");
        currentContext.set("field_" + srcRevenueAttribute2.getId(), 400.0d);
        currentContext.set("field_" + srcNameAttr2.getId(), "Account Name 2");

        currentContext.setGraph(entityGraph);
        RecordsBySyncariId recordsBySyncariId = new RecordsBySyncariId(syncariId);
        recordsBySyncariId.addRecord(stagedRecord1).addRecord(stagedRecord2);
        FieldsGraphRequest request = new FieldsGraphRequest().setEntityName("account")
                .setRecords(recordsBySyncariId)
                .setGraphContext(currentContext)
                .setAttributeDAGs(Map.of(coreNameAttr, nameAttrGraph))
                .setSyncariEntityDef(coreEntityDef)
                .setAttributeBatchActionContext(new BatchActionContext())
                .setBatchedOperations(new BatchedOperations()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>());

        Change change = executeFieldPipeline.createSyncariEntityWithGraph(request);

        assertEquals("Account Name 2", change.getTransactionLog().getChanges().get(coreNameAttr.getId()).getAuthoritativeSource().getValue());
        assertEquals(connector2.getId(), change.getTransactionLog().getChanges().get(coreNameAttr.getId()).getAuthoritativeSource().getConnectorId());
        assertEquals(connector2.getName(), change.getTransactionLog().getChanges().get(coreNameAttr.getId()).getAuthoritativeSource().getConnectorName());

        request = new FieldsGraphRequest().setEntityName("account")
                .setRecords(recordsBySyncariId)
                .setGraphContext(currentContext)
                .setAttributeDAGs(Map.of(coreRevenueAttribute, revAttrGraph))
                .setSyncariEntityDef(coreEntityDef)
                .setAttributeBatchActionContext(new BatchActionContext())
                .setBatchedOperations(new BatchedOperations()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>());

        change = executeFieldPipeline.createSyncariEntityWithGraph(request);

        assertEquals(300.0d, change.getTransactionLog().getChanges().get(coreRevenueAttribute.getId()).getAuthoritativeSource().getValue());
        assertEquals(connector1.getId(), change.getTransactionLog().getChanges().get(coreRevenueAttribute.getId()).getAuthoritativeSource().getConnectorId());
        assertEquals(connector1.getName(), change.getTransactionLog().getChanges().get(coreRevenueAttribute.getId()).getAuthoritativeSource().getConnectorName());

        request = new FieldsGraphRequest().setEntityName("account")
                .setRecords(recordsBySyncariId)
                .setGraphContext(currentContext)
                .setAttributeDAGs(Map.of(coreQualityAttribute, qualiytAttrGraph))
                .setSyncariEntityDef(coreEntityDef)
                .setAttributeBatchActionContext(new BatchActionContext())
                .setBatchedOperations(new BatchedOperations()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>());

        change = executeFieldPipeline.createSyncariEntityWithGraph(request);

        assertEquals("901 - some name", change.getTransactionLog().getChanges().get(coreQualityAttribute.getId()).getAuthoritativeSource().getValue());
        assertEquals(connector1.getId(), change.getTransactionLog().getChanges().get(coreQualityAttribute.getId()).getAuthoritativeSource().getConnectorId());
        assertEquals(connector1.getName(), change.getTransactionLog().getChanges().get(coreQualityAttribute.getId()).getAuthoritativeSource().getConnectorName());
    }

    // Field Pipeline test

    public FunctionDefinition func(String name, Scope scope) {
        return functionService.findByNameAndScope(name, scope).get();
    }

    private static CurrentBatch createCurrentBatch() {
        return new CurrentBatch(null).setCurrentBatchId(UUID.randomUUID().toString());
    }

}
