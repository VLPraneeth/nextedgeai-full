package com.syncari.viper.streams.stages;

import com.syncari.AbstractSyncariTest;
import com.syncari.connector.EntityData;
import com.syncari.core.actions.Actions;
import com.syncari.core.datatype.ChildType;
import com.syncari.core.datatype.DoubleType;
import com.syncari.core.datatype.ReferenceType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.*;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import com.syncari.core.pipeline.BatchActionContext;
import com.syncari.core.pipeline.BatchedOperations;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.repositories.customer.ActionDefinitionRepo;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.repositories.customer.IdMappingRepo;
import com.syncari.core.schema.Schema;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.FunctionService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.sync.CurrentBatch;
import com.syncari.core.sync.RecordsBySyncariId;
import com.syncari.core.utils.GraphHelper;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.viper.ViperContext;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.util.*;
import java.util.function.BiFunction;

import static com.syncari.core.utils.GraphHelper.*;
import static org.junit.Assert.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


public class ExecuteFieldPipelineCreateEntityTest extends AbstractSyncariTest {

    IdMappingRepo idMappingRepo;

    @Autowired
    IdMappingRepo originalIdMappingRepo;
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
    @MockBean
    MappingGraphService graphService;
    @MockBean
    BiFunction<EntityDefinition, Document, EntityData> entityCreate;


    @Autowired
    ActionDefinitionRepo actionDefinitionRepo;

    @MockBean
    Actions actions;
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
        idMappingRepo = mock(IdMappingRepo.class);
        when(schemaService.getSyncariSchema()).thenReturn(new Schema());
        when(connectorService.getSyncariConnector()).thenReturn(syncariConnector);
        when(connectorService.refreshAuthentication(any(Connector.class))).then(returnsFirstArg());
        executeFieldPipeline.idMappingRepo = idMappingRepo;
        super.setUp();
    }

    public void tearDown(){
        executeFieldPipeline.idMappingRepo=originalIdMappingRepo;
    }


    @Test
    public void findSyncariFk_ReturnsNull_ForUnresolved_SingleValued_Attribute(){

        EntityDefinition entityDefinition = new EntityDefinition().setApiName("contact").setConnectorId("connnectorId");
        entityDefinition.setId("entityId");
        AttributeDefinition syncariAttribute = new AttributeDefinition();
        syncariAttribute.setDataType(new ReferenceType());
        syncariAttribute.setReferenceTo("account");
        ResolvedReference syncariFk = executeFieldPipeline.findSyncariFk("contact", syncariAttribute, null, entityDefinition, new GraphContext(), Map.of("test", Map.of("test", "test")));
        assertFalse(syncariFk.hasResolvedReferences());
        assertFalse(syncariFk.hasUnresolvedReferences());
    }

    @Test
        public void findSyncariFk_ResolvesFK_For_SingleValued_Attribute(){
        EntityDefinition entityDefinition = new EntityDefinition().setApiName("contact").setConnectorId("connnectorId");
        entityDefinition.setId("entityId");

        IdMapping existingMapping = new IdMapping();
        existingMapping.setEntityName("account").setSyncariId("syncariId1").addMapping("connnectorId","accountId1","entityId");
        existingMapping.setId("mappingId1");

        EntityDefinition accountEntity = new EntityDefinition().setApiName("account").setConnectorId("connnectorId");
        accountEntity.setId("accountID");
        when(schemaService.getSyncariEntityByName("account")).thenReturn(Optional.of(accountEntity));
        when(entityRepo.count(accountEntity, Optional.empty())).thenReturn(1l);
        when(graphService.retrieveApprovedEntityGraph(any())).thenReturn(Optional.of(new MappingGraph()));

        when(idMappingRepo.findByExternalIds("account","connnectorId","entityId",List.of("accountId1"))).thenReturn(List.of(existingMapping));
        AttributeDefinition syncariAttribute = new AttributeDefinition();
        syncariAttribute.setDataType(new ReferenceType());
        syncariAttribute.setReferenceTo("account");
        ResolvedReference syncariFk = executeFieldPipeline.findSyncariFk("contact", syncariAttribute, "accountId1", entityDefinition, new GraphContext(), Map.of("test", Map.of("test", "test")));
        assertTrue(syncariFk.hasResolvedReferences());
        assertFalse(syncariFk.hasUnresolvedReferences());
        assertEquals("syncariId1",syncariFk.getResolvedReference());
        verify(idMappingRepo).findByExternalIds("account","connnectorId","entityId",List.of("accountId1"));
        syncariFk = executeFieldPipeline.findSyncariFk("contact", syncariAttribute, "accountId1", entityDefinition, new GraphContext(), Map.of("account#connectorId", Map.of("accountId1", "syncariId1")));
        assertTrue(syncariFk.hasResolvedReferences());
        assertFalse(syncariFk.hasUnresolvedReferences());
        assertEquals("syncariId1",syncariFk.getResolvedReference());
    }

    @Test
    public void findSyncariFk_ResolvesFK_For_MultiValuedValued_Attribute(){
        EntityDefinition entityDefinition = new EntityDefinition().setApiName("contact").setConnectorId("connnectorId");
        entityDefinition.setId("accountEntityDefId");

        EntityDefinition accountEntity = new EntityDefinition().setApiName("account").setConnectorId("connnectorId");
        accountEntity.setId("accountID");
        when(schemaService.getSyncariEntityByName("account")).thenReturn(Optional.of(accountEntity));
        when(entityRepo.count(accountEntity, Optional.empty())).thenReturn(1l);
        when(graphService.retrieveApprovedEntityGraph(any())).thenReturn(Optional.of(new MappingGraph()));

        IdMapping existingMapping1 = new IdMapping().setEntityName("account").setSyncariId("syncariId1")
                .addMapping("connnectorId","accountId1","accountEntityDefId");
        existingMapping1.setId("mappingId1");
        IdMapping existingMapping2 = new IdMapping().setEntityName("account").setSyncariId("syncariId2")
                .addMapping("connnectorId","accountId2","accountEntityDefId");
        existingMapping2.setId("mappingId2");

        when(idMappingRepo.findByExternalIds("account","connnectorId","accountEntityDefId",List.of("accountId1","accountId2","accountId3"))).thenReturn(List.of(existingMapping1,existingMapping2));
        AttributeDefinition syncariAttribute = new AttributeDefinition();
        syncariAttribute.setDataType(new ReferenceType());
        syncariAttribute.setReferenceTo("account");
        syncariAttribute.setMultiValueField(true);
        ResolvedReference syncariFk = executeFieldPipeline.findSyncariFk("contact", syncariAttribute, List.of("accountId1","accountId2","accountId3"), entityDefinition, new GraphContext(), Map.of("test", Map.of("test", "test")));
        assertTrue(syncariFk.hasResolvedReferences());
        assertTrue(syncariFk.hasUnresolvedReferences());
        assertEquals(List.of("syncariId1","syncariId2"),syncariFk.getResolvedReferences());
        assertEquals(Set.of("accountId3"),syncariFk.getUnresolvedReferences());
        verify(idMappingRepo).findByExternalIds("account","connnectorId","accountEntityDefId",List.of("accountId1","accountId2","accountId3"));
        when(idMappingRepo.findByExternalIds("account","connnectorId","accountEntityDefId",List.of("accountId1","accountId2","accountId3"))).thenReturn(List.of());
        syncariFk = executeFieldPipeline.findSyncariFk("contact", syncariAttribute, List.of("accountId1","accountId2","accountId3"), entityDefinition, new GraphContext(), Map.of("account#connnectorId", Map.of("accountId1", "syncariId1", "accountId2", "syncariId2")));
        assertTrue(syncariFk.hasResolvedReferences());
        assertTrue(syncariFk.hasUnresolvedReferences());
        assertEquals(List.of("syncariId1","syncariId2"),syncariFk.getResolvedReferences());
        assertEquals(Set.of("accountId3"),syncariFk.getUnresolvedReferences());
    }

    @Test
    public void findSyncariFk_Handles_unresolvesFK_For_MultiValuedValued_Attribute(){
        EntityDefinition entityDefinition = new EntityDefinition().setApiName("contact").setConnectorId("connnectorId");
        entityDefinition.setId("accountEntityDefId");

        EntityDefinition accountEntity = new EntityDefinition().setApiName("account").setConnectorId("connnectorId");
        accountEntity.setId("accountID");
        when(schemaService.getSyncariEntityByName("account")).thenReturn(Optional.of(accountEntity));
        when(entityRepo.count(accountEntity, Optional.empty())).thenReturn(1l);
        when(graphService.retrieveApprovedEntityGraph(any())).thenReturn(Optional.of(new MappingGraph()));

        when(idMappingRepo.findByExternalIds("account","connnectorId","accountEntityDefId",List.of("accountId1","accountId2","accountId3")))
                .thenReturn(List.of());
        AttributeDefinition syncariAttribute = new AttributeDefinition();
        syncariAttribute.setDataType(new ReferenceType());
        syncariAttribute.setReferenceTo("account");
        syncariAttribute.setDataType(ReferenceType.VALUE);
        syncariAttribute.setMultiValueField(true);
        ResolvedReference syncariFk = executeFieldPipeline.findSyncariFk("contact", syncariAttribute, List.of("accountId1","accountId2","accountId3"), entityDefinition, new GraphContext(), Map.of("test", Map.of("test", "test")));
        assertFalse(syncariFk.hasResolvedReferences());
        assertTrue(syncariFk.hasUnresolvedReferences());
        assertEquals(Set.of("accountId1","accountId2","accountId3"),syncariFk.getUnresolvedReferences());
        verify(idMappingRepo).findByExternalIds("account","connnectorId","accountEntityDefId",List.of("accountId1","accountId2","accountId3"));
    }

    @Test
    public void filterSuccessfulOnIncomingChangeField() {
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


        EntityDefinition srcEntityDef = new EntityDefinition();
        srcEntityDef.setConnectorId(connector.getId());
        srcEntityDef.setApiName("Organization");
        srcEntityDef.setDisplayName("Organization");
        srcEntityDef.setStatus(Status.ACTIVE);
        srcEntityDef.setId(ObjectId.get().toHexString());

        AttributeDefinition coreNameAttr = SchemaHelper.createAttribute("Name", new StringType(), coreEntityDef.getId());

        AttributeDefinition srcNameAttr = SchemaHelper.createAttribute("Name", new StringType(), srcEntityDef.getId());


        AttributeDefinition coreRevenueAttribute = SchemaHelper.createAttribute("Revenue", new DoubleType(), coreEntityDef.getId());

        AttributeDefinition coreQualityAttribute = SchemaHelper.createAttribute("Quality", new StringType(), coreEntityDef.getId());

        AttributeDefinition srcRevenueAttribute = SchemaHelper.createAttribute("Revenue", new DoubleType(), srcEntityDef.getId());

        AttributeDefinition srcQualityAttribute = SchemaHelper.createAttribute("Sink Quality", new StringType(), srcEntityDef.getId());

        MappingGraph entityGraph = createGraph(coreEntityDef.getId(), Scope.ENTITY);
        MappingNode coreNode = coreEntityNode(coreEntityDef, entityGraph);
        MappingNode srcNode = srcEntityNode(srcEntityDef, entityGraph);

        Edge srcToCore = edge(srcNode, coreNode, entityGraph);
        srcToCore.setId(ObjectId.get().toHexString());

        MappingGraph nameAttrGraph = createGraph(coreNameAttr.getId(), Scope.ATTRIBUTE);
        MappingGraph revAttrGraph = createGraph(coreRevenueAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph qualiytAttrGraph = createGraph(coreQualityAttribute.getId(), Scope.ATTRIBUTE);

        MappingNode coreNameAttrNode = coreAttributeNode(coreNameAttr, nameAttrGraph);
        MappingNode srcNameAttrNode = srcAttributeNode(srcNameAttr, nameAttrGraph);
        Edge srcToCoreNameAttr = edge(srcNameAttrNode, coreNameAttrNode, nameAttrGraph);

        MappingNode coreQAttrNode = coreAttributeNode(coreQualityAttribute, qualiytAttrGraph);
        MappingNode srcQAttrNode = srcAttributeNode(srcQualityAttribute, qualiytAttrGraph);

        //incoming_change IS update
        Map<String, Object> predicateMap = new HashMap<>();
        var preidcates = List.of(Map.of(
                "left", Map.of("datatype", "exact_match", "type", "variable", "value", "incoming_change"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "update")
        ));
        predicateMap.put("predicate", Map.of("predicates", preidcates, "operator", "AND"));
        MappingNode filterUpdates =
                new MappingNode().setScope(Scope.ATTRIBUTE).setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall()
                        .setFunctionDefinition(functionService.findByNameAndScope("filter", Scope.ATTRIBUTE).get())
                        .setParams(List.of(ParameterValue.string("output_" + srcQAttrNode.getId()+".x.typedValue", "input")))
                        .setConfig(predicateMap)
                )).setName("UpdatesOnly");
        filterUpdates.setId(ObjectId.get().toHexString());
        qualiytAttrGraph.getNodes().add(filterUpdates);
        Edge srcToFilterQ = edge(srcQAttrNode, filterUpdates, qualiytAttrGraph);
        Edge filterToCoreQ = edge(filterUpdates, coreQAttrNode, qualiytAttrGraph);
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
                .addValue("Revenue", 300.0)
                .addValue("Sync Quality", "GOOD");
        List<EntityData> t = List.of(entityData);

        when(schemaService.getEntity(srcEntityDef.getId())).thenReturn(srcEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(connector);
        when(entityRepo.findById(coreEntityDef,syncariId)).thenReturn(Optional.of(entityData));
        when(schemaService.getAttribute(srcNameAttr.getId())).thenReturn(srcNameAttr);
        when(schemaService.getAttribute(srcQualityAttribute.getId())).thenReturn(srcQualityAttribute);
        when(schemaService.getAttribute(srcRevenueAttribute.getId())).thenReturn(srcRevenueAttribute);

        GraphContext currentContext = new GraphContext();
        currentContext.setCurrentBatch(createCurrentBatch());
        currentContext.set("field_" + srcQualityAttribute.getId(), "GOOD");
        currentContext.set("field_"+srcRevenueAttribute.getId(),300.0d);
        currentContext.set("field_"+srcNameAttr.getId(),"Account Name");
        currentContext.setGraph(entityGraph);
        RecordsBySyncariId recordsBySyncariId = new RecordsBySyncariId(syncariId);
        recordsBySyncariId.setExistingRecord(entityData);
        FieldsGraphRequest request = new FieldsGraphRequest().setEntityName("account")
                .setRecords(recordsBySyncariId)
                .setGraphContext(currentContext)
                .setAttributeDAGs(Map.of(coreQualityAttribute, qualiytAttrGraph))
                .setSyncariEntityDef(coreEntityDef)
                .setAttributeBatchActionContext(new BatchActionContext())
                .setBatchedOperations(new BatchedOperations()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>());

        Change change = executeFieldPipeline.createSyncariEntityWithGraph(request);

        assertTrue(change.getChanges().has("Sync Quality"));
        assertTrue(change.getTransactionLog().getChange(coreQualityAttribute.getId()).isPresent());
        var incomingValue = change.getTransactionLog().getChange(coreQualityAttribute.getId()).get().getIncomingExternalValues().get(srcQualityAttribute.getId());
        assertEquals("Sink Quality", incomingValue.getApiName());
        assertEquals("Sink Quality", incomingValue.getDisplayName());
        assertEquals("my zendesk connector", incomingValue.getConnectorName());
        assertEquals("my zendesk connector", incomingValue.getConnectorId());
        assertEquals("GOOD", change.getTransactionLog().getChange(coreQualityAttribute.getId()).get().getNewValue());

    }

    @Test
    public void testFilterStartsWith() {
        Connector connector = getConnector();
        EntityDefinition coreEntityDef = SchemaHelper.createEntityDef("account", "Account", null);
        EntityDefinition srcEntityDef = SchemaHelper.createEntityDef("Organization", "Organization", connector);
        AttributeDefinition coreNameAttr = SchemaHelper.createAttribute("Name", new StringType(), coreEntityDef.getId());
        AttributeDefinition srcNameAttr = SchemaHelper.createAttribute("AccountName", new StringType(), srcEntityDef.getId());
        MappingGraph entityGraph = GraphHelper.newGraph(coreEntityDef,functionService).src(srcEntityDef).connect("Organization","account").getGraph();
        Map<String, Object> predicateMap = new HashMap<>();
        var preidcates = List.of(Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", srcNameAttr.getId()),
                "operator", "starts_with",
                "right", Map.of("type", "literal", "value", "organization")
        ));
        predicateMap.put("predicate", Map.of("predicates", preidcates, "operator", "AND"));
        MappingGraph nameAttrGraph = GraphHelper.newGraph(coreNameAttr,functionService)
                .src(srcNameAttr)
                .function("filter","filter",predicateMap)
                .function("setValue","setValue",Map.of("newValue","https://example.com/{{previous}}"))
                .function("isFalse")
                .connect(srcNameAttr.getApiName(),"filter")
                .connect("filter","isFalse")
                .connect("filter","setValue")
                .connect("setValue",coreNameAttr.getApiName())
                .connect("isFalse",coreNameAttr.getApiName()).getGraph();
        String syncariId = ObjectId.get().toHexString();
        srcEntityDef.addField(srcNameAttr);
        when(schemaService.getEntity(srcEntityDef.getId())).thenReturn(srcEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(connector);
        when(entityRepo.findById(coreEntityDef,syncariId)).thenReturn(Optional.empty());
        when(schemaService.getAttribute(srcNameAttr.getId())).thenReturn(srcNameAttr);

        GraphContext currentContext = new GraphContext();
        currentContext.setCurrentBatch(createCurrentBatch());
        currentContext.set("field_" + srcNameAttr.getId(), "organization/abc");
        currentContext.setGraph(entityGraph);
        RecordsBySyncariId recordsBySyncariId = new RecordsBySyncariId(syncariId);
        FieldsGraphRequest request = new FieldsGraphRequest().setEntityName("account")
                .setRecords(recordsBySyncariId)
                .setGraphContext(currentContext)
                .setAttributeDAGs(Map.of(coreNameAttr, nameAttrGraph))
                .setSyncariEntityDef(coreEntityDef)
                .setAttributeBatchActionContext(new BatchActionContext())
                .setBatchedOperations(new BatchedOperations()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>());

        Change change = executeFieldPipeline.createSyncariEntityWithGraph(request);
        assertTrue(change.getChanges().has(coreNameAttr.getApiName()));
        assertTrue(change.getTransactionLog().getChange(coreNameAttr.getId()).isPresent());
        assertEquals("https://example.com/organization/abc", change.getTransactionLog().getChange(coreNameAttr.getId()).get().getNewValue());
        var incomingValue = change.getTransactionLog().getChange(coreNameAttr.getId()).get().getIncomingExternalValues().get(srcNameAttr.getId());
        assertEquals("AccountName", incomingValue.getApiName());
        assertEquals("AccountName", incomingValue.getDisplayName());
        assertEquals("my zendesk connector", incomingValue.getConnectorName());
        assertEquals("my zendesk connector", incomingValue.getConnectorId());
    }

    @Test
    public void updatesForcedInTestMode() {
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


        EntityDefinition srcEntityDef = new EntityDefinition();
        srcEntityDef.setConnectorId(connector.getId());
        srcEntityDef.setApiName("Organization");
        srcEntityDef.setDisplayName("Organization");
        srcEntityDef.setStatus(Status.ACTIVE);
        srcEntityDef.setId(ObjectId.get().toHexString());


        AttributeDefinition coreQualityAttribute = SchemaHelper.createAttribute("Quality", new StringType(), coreEntityDef.getId());

        AttributeDefinition srcQualityAttribute = SchemaHelper.createAttribute("Sink Quality", new StringType(), srcEntityDef.getId());

        MappingGraph entityGraph = createGraph(coreEntityDef.getId(), Scope.ENTITY);
        MappingNode coreNode = coreEntityNode(coreEntityDef, entityGraph);
        MappingNode srcNode = srcEntityNode(srcEntityDef, entityGraph);

        Edge srcToCore = edge(srcNode, coreNode, entityGraph);
        srcToCore.setId(ObjectId.get().toHexString());

        MappingGraph qualiytAttrGraph = createGraph(coreQualityAttribute.getId(), Scope.ATTRIBUTE);

        MappingNode coreQAttrNode = coreAttributeNode(coreQualityAttribute, qualiytAttrGraph);
        MappingNode srcQAttrNode = srcAttributeNode(srcQualityAttribute, qualiytAttrGraph);


        Edge srcToCoreQ = edge(srcQAttrNode, coreQAttrNode, qualiytAttrGraph);
        String syncariId = ObjectId.get().toHexString();
        srcEntityDef.addField(srcQualityAttribute);

        EntityData entityData = new EntityData("account")
                .setSyncariEntityId(syncariId)
                .addValue(coreQualityAttribute.getApiName(), "GOOD");
        List<EntityData> t = List.of(entityData);

        when(schemaService.getEntity(srcEntityDef.getId())).thenReturn(srcEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(connector);
        when(entityRepo.findById(coreEntityDef,syncariId)).thenReturn(Optional.of(entityData));
        when(schemaService.getAttribute(srcQualityAttribute.getId())).thenReturn(srcQualityAttribute);

        GraphContext currentContext = new GraphContext();
        currentContext.setCurrentBatch(createCurrentBatch());
        currentContext.set("field_" + srcQualityAttribute.getId(), "GOOD");
        currentContext.setGraph(entityGraph);
        RecordsBySyncariId recordsBySyncariId = new RecordsBySyncariId(syncariId);
        recordsBySyncariId.setExistingRecord(entityData);
        FieldsGraphRequest request = new FieldsGraphRequest().setEntityName("account")
                .setRecords(recordsBySyncariId)
                .setGraphContext(currentContext)
                .setAttributeDAGs(Map.of(coreQualityAttribute, qualiytAttrGraph))
                .setSyncariEntityDef(coreEntityDef)
                .setAttributeBatchActionContext(new BatchActionContext())
                .setBatchedOperations(new BatchedOperations()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>());

        Change change = executeFieldPipeline.createSyncariEntityWithGraph(request);


        assertTrue(change.getChanges().has(coreQualityAttribute.getApiName()));
        assertEquals("GOOD",change.getChanges().getValue(coreQualityAttribute.getApiName()));
        assertFalse(change.getTransactionLog().getChange(coreQualityAttribute.getId()).isPresent());

        currentContext.setTestMode(true);
        change = executeFieldPipeline.createSyncariEntityWithGraph(request);

        assertTrue(change.getChanges().has(coreQualityAttribute.getApiName()));
        assertTrue(change.getTransactionLog().getChange(coreQualityAttribute.getId()).isPresent());
        assertEquals("GOOD", change.getTransactionLog().getChange(coreQualityAttribute.getId()).get().getNewValue());
        var incomingValue = change.getTransactionLog().getChange(coreQualityAttribute.getId()).get().getIncomingExternalValues().get(srcQualityAttribute.getId());
        assertEquals("Sink Quality", incomingValue.getApiName());
        assertEquals("Sink Quality", incomingValue.getDisplayName());
        assertEquals("my zendesk connector", incomingValue.getConnectorName());
        assertEquals("my zendesk connector", incomingValue.getConnectorId());
    }
    @Test
    public void compareAgainstExistingField() {
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

        EntityDefinition srcEntityDef = new EntityDefinition();
        srcEntityDef.setConnectorId(connector.getId());
        srcEntityDef.setApiName("Organization");
        srcEntityDef.setDisplayName("Organization");
        srcEntityDef.setStatus(Status.ACTIVE);
        srcEntityDef.setId(ObjectId.get().toHexString());

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
        srcToCore.setId(ObjectId.get().toHexString());

        MappingGraph nameAttrGraph = createGraph(coreNameAttr.getId(), Scope.ATTRIBUTE);
        MappingGraph revAttrGraph = createGraph(coreRevenueAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph qualiytAttrGraph = createGraph(coreQualityAttribute.getId(), Scope.ATTRIBUTE);

        MappingNode coreNameAttrNode = coreAttributeNode(coreNameAttr, nameAttrGraph);
        MappingNode srcNameAttrNode = srcAttributeNode(srcNameAttr, nameAttrGraph);
        Edge srcToCoreNameAttr = edge(srcNameAttrNode, coreNameAttrNode, nameAttrGraph);

        MappingNode coreQAttrNode = coreAttributeNode(coreQualityAttribute, qualiytAttrGraph);
        MappingNode srcQAttrNode = srcAttributeNode(srcQualityAttribute, qualiytAttrGraph);

        //incoming_change IS update
        Map<String, Object> predicateMap = new HashMap<>();
        var preidcates = List.of(Map.of(
                "left", Map.of("type", "variable", "value", coreRevenueAttribute.getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "200")
        ));
        predicateMap.put("predicate", Map.of("predicates", preidcates, "operator", "AND"));
        MappingNode filterUpdates =
                new MappingNode().setScope(Scope.ATTRIBUTE).setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall()
                        .setFunctionDefinition(functionService.findByNameAndScope("filter", Scope.ATTRIBUTE).get())
                        .setParams(List.of(ParameterValue.string("output_" + srcQAttrNode.getId()+".x.typedValue", "input")))
                        .setConfig(predicateMap)
                )).setName("UpdatesOnly");
        filterUpdates.setId(ObjectId.get().toHexString());
        qualiytAttrGraph.getNodes().add(filterUpdates);
        Edge srcToFilterQ = edge(srcQAttrNode, filterUpdates, qualiytAttrGraph);
        Edge filterToCoreQ = edge(filterUpdates, coreQAttrNode, qualiytAttrGraph);
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
        when(entityRepo.findById(coreEntityDef,syncariId)).thenReturn(Optional.of(entityData));
        when(schemaService.getAttribute(srcNameAttr.getId())).thenReturn(srcNameAttr);
        when(schemaService.getAttribute(srcQualityAttribute.getId())).thenReturn(srcQualityAttribute);
        when(schemaService.getAttribute(srcRevenueAttribute.getId())).thenReturn(srcRevenueAttribute);

        GraphContext currentContext = new GraphContext();
        currentContext.setCurrentBatch(createCurrentBatch());
        currentContext.set("field_" + srcQualityAttribute.getId(), "GOOD");
        currentContext.set("field_"+srcRevenueAttribute.getId(),300.0d);
        currentContext.set("field_"+srcNameAttr.getId(),"Account Name");
        currentContext.setGraph(entityGraph);
        RecordsBySyncariId recordsBySyncariId = new RecordsBySyncariId(syncariId);
        recordsBySyncariId.setExistingRecord(entityData);
        FieldsGraphRequest request = new FieldsGraphRequest().setEntityName("account")
                .setRecords(recordsBySyncariId)
                .setGraphContext(currentContext)
                .setAttributeDAGs(Map.of(coreQualityAttribute, qualiytAttrGraph))
                .setSyncariEntityDef(coreEntityDef)
                .setAttributeBatchActionContext(new BatchActionContext())
                .setBatchedOperations(new BatchedOperations()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>());


        Change change = executeFieldPipeline.createSyncariEntityWithGraph(request);

        assertTrue(change.getChanges().has("Quality"));
        assertEquals("BAD",change.getChanges().getValue("Quality"));
        assertFalse(change.getTransactionLog().getChange(coreQualityAttribute.getId()).isPresent());

        preidcates = List.of(Map.of(
                "left", Map.of("type", "variable", "value", coreRevenueAttribute.getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "55")
        ));
        predicateMap.put("predicate", Map.of("predicates", preidcates, "operator", "AND"));

        currentContext = new GraphContext();
        currentContext.setCurrentBatch(createCurrentBatch());
        currentContext.set("field_" + srcQualityAttribute.getId(), "GOOD");
        currentContext.set("field_"+srcRevenueAttribute.getId(),300.0d);
        currentContext.set("field_"+srcNameAttr.getId(),"Account Name");
        currentContext.setGraph(entityGraph);
        recordsBySyncariId = new RecordsBySyncariId(syncariId);
        change = executeFieldPipeline.createSyncariEntityWithGraph(request);

        assertTrue(change.getChanges().has("Quality"));
        assertEquals("GOOD",change.getChanges().getValue("Quality"));
        assertTrue(change.getTransactionLog().getChange(coreQualityAttribute.getId()).isPresent());

        preidcates = List.of(Map.of(
                "left", Map.of("type", "variable", "value", coreRevenueAttribute.getId()),
                "operator", "lt",
                "right", Map.of("type", "literal", "value", "100")
        ));
        predicateMap.put("predicate", Map.of("predicates", preidcates, "operator", "AND"));

        currentContext = new GraphContext();
        currentContext.setCurrentBatch(createCurrentBatch());
        currentContext.set("field_" + srcQualityAttribute.getId(), "GOOD");
        currentContext.set("field_"+srcRevenueAttribute.getId(),300.0d);
        currentContext.set("field_"+srcNameAttr.getId(),"Account Name");
        currentContext.setGraph(entityGraph);
        recordsBySyncariId = new RecordsBySyncariId(syncariId);
        change = executeFieldPipeline.createSyncariEntityWithGraph(request);

        assertTrue(change.getChanges().has("Quality"));
        assertEquals("GOOD",change.getChanges().getValue("Quality"));
        assertTrue(change.getTransactionLog().getChange(coreQualityAttribute.getId()).isPresent());
        var incomingValue = change.getTransactionLog().getChange(coreQualityAttribute.getId()).get().getIncomingExternalValues().get(srcQualityAttribute.getId());
        assertEquals("Sink Quality", incomingValue.getApiName());
        assertEquals("Sink Quality", incomingValue.getDisplayName());
        assertEquals("my zendesk connector", incomingValue.getConnectorName());
        assertEquals("my zendesk connector", incomingValue.getConnectorId());

    }

    @Test
    public void multiSourceDataAuthority() {
        ViperContext context = new ViperContext(new Organization(), new Instance(), new User());

        Connector connector = new Connector("my zendesk connector", "zendeskConnectorId",
                "https://someendpoint");
        connector.setId("my zendesk connector");
        connector.setMetadata(new ConnectorMetadata("zendeskConnectorId"));

        Connector connector2 = new Connector("my salesforce connector", "salesforceConnectorId",
                "https://someendpoint");
        connector2.setId("my salesforce connector");
        connector2.setMetadata(new ConnectorMetadata("salesforceConnectorId"));

        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());


        EntityDefinition zendeskAccount = new EntityDefinition();
        zendeskAccount.setConnectorId(connector.getId());
        zendeskAccount.setApiName("Organization");
        zendeskAccount.setDisplayName("Organization");
        zendeskAccount.setStatus(Status.ACTIVE);
        zendeskAccount.setId(ObjectId.get().toHexString());

        EntityDefinition sfdcAccount = new EntityDefinition();
        sfdcAccount.setConnectorId(connector2.getId());
        sfdcAccount.setApiName("Account");
        sfdcAccount.setDisplayName("Account");
        sfdcAccount.setStatus(Status.ACTIVE);
        sfdcAccount.setId(ObjectId.get().toHexString());


        AttributeDefinition coreNameAttr = SchemaHelper.createAttribute("Name", new StringType(), coreEntityDef.getId());

        AttributeDefinition zendeskName = SchemaHelper.createAttribute("Name", new StringType(), zendeskAccount.getId());

        AttributeDefinition sfdcName = SchemaHelper.createAttribute("AccountName", new StringType(), sfdcAccount.getId());


        zendeskAccount.addField(zendeskName);
        sfdcAccount.addField(sfdcName);

        MappingGraph entityGraph = createGraph(coreEntityDef.getId(), Scope.ENTITY);
        MappingNode coreNode = coreEntityNode(coreEntityDef, entityGraph);
        MappingNode zendeskNode = srcEntityNode(zendeskAccount, entityGraph);
        MappingNode sfdcNode = srcEntityNode(sfdcAccount, entityGraph);

        Edge zendeskToCore = edge(zendeskNode, coreNode, entityGraph);
        zendeskToCore.setId(ObjectId.get().toHexString());

        Edge sfdcToCore = edge(sfdcNode, coreNode, entityGraph);
        sfdcToCore.setId(ObjectId.get().toHexString());

        MappingGraph nameAttrGraph = createGraph(coreNameAttr.getId(), Scope.ATTRIBUTE);

        MappingNode coreNameAttrNode = coreAttributeNode(coreNameAttr, nameAttrGraph);
        MappingNode zendeskNameAttrNode = srcAttributeNode(zendeskName, nameAttrGraph);
        MappingNode sfdcNameAttrNode = srcAttributeNode(sfdcName, nameAttrGraph);

        Edge zendeskNameToCoreNameAttr = edge(zendeskNameAttrNode, coreNameAttrNode, nameAttrGraph);
        Edge sfdcNameToCoreNameAttr = edge(sfdcNameAttrNode, coreNameAttrNode, nameAttrGraph);

        //incoming_change IS update
        String syncariId = ObjectId.get().toHexString();

        EntityData zendeskRecord = new EntityData("account")
                .setSyncariEntityId(syncariId)
                .setConnectorId(connector.getId())
                .setLastModified(Instant.now().minusSeconds(200).toEpochMilli())
                .addValue("Name", "Zendesk Account Name");

        EntityData sfdcRecord = new EntityData("Account")
                .setSyncariEntityId(syncariId)
                .setConnectorId(connector2.getId())
                .setLastModified(Instant.now().minusSeconds(150).toEpochMilli())
                .addValue("AccountName", "SFDC Account Name");

        EntityData syncariRecord = new EntityData("Account")
                .setSyncariEntityId(syncariId)
                .setLastModified(Instant.now().minusSeconds(100).toEpochMilli())

                .addValue("Name", "Base Account Name");

        when(schemaService.getEntity(zendeskAccount.getId())).thenReturn(zendeskAccount);
        when(schemaService.getEntity(sfdcAccount.getId())).thenReturn(sfdcAccount);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(zendeskAccount.getConnectorId())).thenReturn(connector);
        when(connectorService.get(sfdcAccount.getConnectorId())).thenReturn(connector2);
        when(entityRepo.findById(coreEntityDef,syncariId)).thenReturn(Optional.of(syncariRecord));
        when(schemaService.getAttribute(zendeskName.getId())).thenReturn(zendeskName);
        when(schemaService.getAttribute(sfdcName.getId())).thenReturn(sfdcName);


        GraphContext currentContext = new GraphContext();
        currentContext.setCurrentBatch(createCurrentBatch());
        currentContext.set("field_" + zendeskName.getId(), "Zendesk Account Name");
        currentContext.set("field_"+sfdcName.getId(),"SFDC Account Name");
        currentContext.setGraph(entityGraph);
        RecordsBySyncariId recordsBySyncariId = new RecordsBySyncariId(syncariId);
        StagedBatchRecord sfdcStagedRecord = new StagedBatchRecord().setEntityData(sfdcRecord).
                setExternalEntityDefinitionId(sfdcAccount.getId())
                .setExternalRecordId(sfdcRecord.getId()).setSyncariId(syncariId);
        StagedBatchRecord zendeskStagedRecord = new StagedBatchRecord().setEntityData(zendeskRecord).
                setExternalEntityDefinitionId(zendeskAccount.getId())
                .setExternalRecordId(zendeskRecord.getId()).setSyncariId(syncariId);
        recordsBySyncariId.addRecord(sfdcStagedRecord);
        recordsBySyncariId.addRecord(zendeskStagedRecord);
        FieldsGraphRequest request = new FieldsGraphRequest().setEntityName("account")
                .setRecords(recordsBySyncariId)
                .setGraphContext(currentContext)
                .setAttributeDAGs(Map.of(coreNameAttr, nameAttrGraph))
                .setSyncariEntityDef(coreEntityDef)
                .setAttributeBatchActionContext(new BatchActionContext())
                .setBatchedOperations(new BatchedOperations()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>());


        Change change = executeFieldPipeline.createSyncariEntityWithGraph(request);

        assertTrue(change.getChanges().has("Name"));
        assertTrue(change.getTransactionLog().getChange(coreNameAttr.getId()).isPresent());
        assertEquals("SFDC Account Name", change.getTransactionLog().getChange(coreNameAttr.getId()).get().getNewValue());
        var incomingValues = change.getTransactionLog().getChange(coreNameAttr.getId()).get().getIncomingExternalValues();
        var sfdcValue = incomingValues.get(sfdcName.getId());
        assertEquals("AccountName", sfdcValue.getApiName());
        assertEquals("AccountName", sfdcValue.getDisplayName());
        assertEquals("my salesforce connector", sfdcValue.getConnectorName());
        assertEquals("my salesforce connector", sfdcValue.getConnectorId());

        var zendeskValue = incomingValues.get(zendeskName.getId());
        assertEquals("Name", zendeskValue.getApiName());
        assertEquals("Name", zendeskValue.getDisplayName());
        assertEquals("my zendesk connector", zendeskValue.getConnectorName());
        assertEquals("my zendesk connector", zendeskValue.getConnectorId());

    }

    @Test
    public void filterFailOnIncomingChangeField() {
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


        EntityDefinition srcEntityDef = new EntityDefinition();
        srcEntityDef.setConnectorId(connector.getId());
        srcEntityDef.setApiName("Organization");
        srcEntityDef.setDisplayName("Organization");
        srcEntityDef.setStatus(Status.ACTIVE);
        srcEntityDef.setId(ObjectId.get().toHexString());

        AttributeDefinition coreNameAttr = SchemaHelper.createAttribute("Name", new StringType(), coreEntityDef.getId());

        AttributeDefinition srcNameAttr = SchemaHelper.createAttribute("Name", new StringType(), srcEntityDef.getId());


        AttributeDefinition coreRevenueAttribute = SchemaHelper.createAttribute("Revenue", new DoubleType(), coreEntityDef.getId());

        AttributeDefinition coreQualityAttribute = SchemaHelper.createAttribute("Quality", new StringType(), coreEntityDef.getId());

        AttributeDefinition srcRevenueAttribute = SchemaHelper.createAttribute("Revenue", new DoubleType(), srcEntityDef.getId());

        AttributeDefinition srcQualityAttribute = SchemaHelper.createAttribute("Sink Quality", new StringType(), srcEntityDef.getId());

        MappingGraph entityGraph = createGraph(coreEntityDef.getId(), Scope.ENTITY);
        MappingNode coreNode = coreEntityNode(coreEntityDef, entityGraph);
        MappingNode srcNode = srcEntityNode(srcEntityDef, entityGraph);

        Edge srcToCore = edge(srcNode, coreNode, entityGraph);
        srcToCore.setId(ObjectId.get().toHexString());

        MappingGraph nameAttrGraph = createGraph(coreNameAttr.getId(), Scope.ATTRIBUTE);
        MappingGraph revAttrGraph = createGraph(coreRevenueAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph qualiytAttrGraph = createGraph(coreQualityAttribute.getId(), Scope.ATTRIBUTE);

        MappingNode coreNameAttrNode = coreAttributeNode(coreNameAttr, nameAttrGraph);
        MappingNode srcNameAttrNode = srcAttributeNode(srcNameAttr, nameAttrGraph);
        Edge srcToCoreNameAttr = edge(srcNameAttrNode, coreNameAttrNode, nameAttrGraph);

        MappingNode coreQAttrNode = coreAttributeNode(coreQualityAttribute, qualiytAttrGraph);
        MappingNode srcQAttrNode = srcAttributeNode(srcQualityAttribute, qualiytAttrGraph);

        //incoming_change IS update
        Map<String, Object> predicateMap = new HashMap<>();
        var preidcates = List.of(Map.of(
                "left", Map.of("datatype", "exact_match", "type", "variable", "value", "incoming_change"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "update")
        ));
        predicateMap.put("predicate", Map.of("predicates", preidcates, "operator", "AND"));
        MappingNode filterUpdates =
                new MappingNode().setScope(Scope.ATTRIBUTE).setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall()
                        .setFunctionDefinition(functionService.findByNameAndScope("filter", Scope.ATTRIBUTE).get())
                        .setParams(List.of(ParameterValue.string("output_" + srcQAttrNode.getId()+".x.typedValue", "input")))
                        .setConfig(predicateMap)
                )).setName("UpdatesOnly");
        filterUpdates.setId(ObjectId.get().toHexString());
        qualiytAttrGraph.getNodes().add(filterUpdates);
        Edge srcToFilterQ = edge(srcQAttrNode, filterUpdates, qualiytAttrGraph);
        Edge filterToCoreQ = edge(filterUpdates, coreQAttrNode, qualiytAttrGraph);
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
                .addValue("Revenue", 300.0)
                .addValue("Sync Quality", "GOOD");
        List<EntityData> t = List.of(entityData);

        when(schemaService.getEntity(srcEntityDef.getId())).thenReturn(srcEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(connector);
        when(entityRepo.findById(coreEntityDef,syncariId)).thenReturn(Optional.empty());


        GraphContext currentContext = new GraphContext();
        currentContext.setCurrentBatch(createCurrentBatch());
        currentContext.set("field_" + srcQualityAttribute.getId(), "GOOD");
        currentContext.set("field_"+srcRevenueAttribute.getId(),300.0d);
        currentContext.set("field_"+srcNameAttr.getId(),"Account Name");
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

        assertFalse(change.getChanges().has("Sync Quality"));
        assertTrue(change.getTransactionLog().getChange(coreQualityAttribute.getId()).isEmpty());

    }

    @Test
    public void cascadingFilterWithMultipleEdges() {
        ViperContext context = new ViperContext(new Organization(), new Instance(), new User());

        Connector connector = new Connector("my zendesk connector", "zendeskConnectorId",
                "https://someendpoint");
        connector.setId("my zendesk connector");
        connector.setMetadata(new ConnectorMetadata("zendeskConnectorId"));
        EntityDefinition coreEntityDef = SchemaHelper.createEntityDef("account", "Account", null);

        EntityDefinition srcEntityDef = SchemaHelper.createEntityDef("Organization", "Organization", connector);

        AttributeDefinition srcNameAttr = SchemaHelper.createAttribute("Name", new StringType(), srcEntityDef.getId());

        AttributeDefinition coreQualityAttribute = SchemaHelper.createAttribute("Quality", new StringType(), coreEntityDef.getId());

        AttributeDefinition srcRevenueAttribute = SchemaHelper.createAttribute("Revenue", new DoubleType(), srcEntityDef.getId());

        AttributeDefinition srcQualityAttribute = SchemaHelper.createAttribute("Sink Quality", new StringType(), srcEntityDef.getId());

        MappingGraph entityGraph = newGraph(coreEntityDef,functionService)
                .src(srcEntityDef).connect(srcEntityDef.getApiName(), coreEntityDef.getApiName()).getGraph();


        //incoming_change IS update
        Map<String, Object> predicateMap = new HashMap<>();
        var preidcates = List.of(Map.of(
                "left", Map.of("datatype", "exact_match", "type", "variable", "value", "incoming_change"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "update")
        ));
        predicateMap.put("predicate", Map.of("predicates", preidcates, "operator", "AND"));

        Map<String, Object> isFalseValue = new HashMap<>();
        var isFalseValuePredicates = List.of(Map.of(
                "left", Map.of( "type", "variable", "value", "previous"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "FIlter1False")
        ));
        isFalseValue.put("predicate", Map.of("predicates", isFalseValuePredicates, "operator", "AND"));


        MappingGraph qualiytAttrGraph= newGraph(coreQualityAttribute,functionService)
                .src(srcQualityAttribute)
                .function("filter","filter1",predicateMap)
                .connect(srcQualityAttribute.getApiName(),"filter1")
                .function("isFalse","isFilter1False")
                .connect("filter1","isFilter1False")
                .function("setValue","setQValue1",Map.of("newValue","FIlter1False"))
                .function("setValue","setQValue2",Map.of("newValue","FIlter1True"))
                .function("filter","isFalseValueFilter",isFalseValue)
                .connect("filter1","setQValue2")
                .connect("isFilter1False","setQValue1")
                .connect("setQValue1","isFalseValueFilter")
                .connect("setQValue2","isFalseValueFilter")
                .connect("isFalseValueFilter",coreQualityAttribute.getApiName()).getGraph();


        String syncariId = ObjectId.get().toHexString();
        srcEntityDef.addField(srcNameAttr);
        srcEntityDef.addField(srcRevenueAttribute);
        srcEntityDef.addField(srcQualityAttribute);

        EntityData entityData = new EntityData("account")
                .setSyncariEntityId(syncariId)
                .addValue("Name", "Account Name")
                .addValue("Revenue", 300.0)
                .addValue("Sync Quality", "GOOD");
        List<EntityData> t = List.of(entityData);

        when(schemaService.getEntity(srcEntityDef.getId())).thenReturn(srcEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(connector);
        when(entityRepo.findById(coreEntityDef,syncariId)).thenReturn(Optional.empty());
        when(schemaService.getAttribute(srcNameAttr.getId())).thenReturn(srcNameAttr);
        when(schemaService.getAttribute(srcQualityAttribute.getId())).thenReturn(srcQualityAttribute);
        when(schemaService.getAttribute(srcRevenueAttribute.getId())).thenReturn(srcRevenueAttribute);

        GraphContext currentContext = new GraphContext();
        currentContext.setCurrentBatch(createCurrentBatch());
        currentContext.set("field_" + srcQualityAttribute.getId(), "GOOD");
        currentContext.set("field_"+srcRevenueAttribute.getId(),300.0d);
        currentContext.set("field_"+srcNameAttr.getId(),"Account Name");
        currentContext.setGraph(entityGraph);
        RecordsBySyncariId recordsBySyncariId = new RecordsBySyncariId(syncariId);
        FieldsGraphRequest request = new FieldsGraphRequest().setEntityName("account")
                .setRecords(recordsBySyncariId)
                .setGraphContext(currentContext)
                .setAttributeDAGs(Map.of(coreQualityAttribute, qualiytAttrGraph))
                .setSyncariEntityDef(coreEntityDef)
                .setAttributeBatchActionContext(new BatchActionContext())
                .setBatchedOperations(new BatchedOperations())
                .setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>());

        Change change = executeFieldPipeline.createSyncariEntityWithGraph(request);
        assertEquals("FIlter1False",change.getChanges().getValueAsString("Quality"));
        assertEquals("FIlter1False",change.getTransactionLog().getChange(coreQualityAttribute.getId()).get().getNewValue());

    }

    @Test
    public void filterOnEmptyField() {
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


        EntityDefinition srcEntityDef = new EntityDefinition();
        srcEntityDef.setConnectorId(connector.getId());
        srcEntityDef.setApiName("Organization");
        srcEntityDef.setDisplayName("Organization");
        srcEntityDef.setStatus(Status.ACTIVE);
        srcEntityDef.setId(ObjectId.get().toHexString());

        AttributeDefinition coreNameAttr = SchemaHelper.createAttribute("Name", new StringType(), coreEntityDef.getId());

        AttributeDefinition srcNameAttr = SchemaHelper.createAttribute("Name", new StringType(), srcEntityDef.getId());


        AttributeDefinition coreRevenueAttribute = SchemaHelper.createAttribute("Revenue", new DoubleType(), coreEntityDef.getId());

        AttributeDefinition coreQualityAttribute = SchemaHelper.createAttribute("Quality", new StringType(), coreEntityDef.getId());

        AttributeDefinition srcRevenueAttribute = SchemaHelper.createAttribute("Revenue", new DoubleType(), srcEntityDef.getId());

        AttributeDefinition srcQualityAttribute = SchemaHelper.createAttribute("Sink Quality", new StringType(), srcEntityDef.getId());

        MappingGraph entityGraph = createGraph(coreEntityDef.getId(), Scope.ENTITY);
        MappingNode coreNode = coreEntityNode(coreEntityDef, entityGraph);
        MappingNode srcNode = srcEntityNode(srcEntityDef, entityGraph);

        Edge srcToCore = edge(srcNode, coreNode, entityGraph);
        srcToCore.setId(ObjectId.get().toHexString());

        MappingGraph nameAttrGraph = createGraph(coreNameAttr.getId(), Scope.ATTRIBUTE);
        MappingGraph revAttrGraph = createGraph(coreRevenueAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph qualiytAttrGraph = createGraph(coreQualityAttribute.getId(), Scope.ATTRIBUTE);

        MappingNode coreNameAttrNode = coreAttributeNode(coreNameAttr, nameAttrGraph);
        MappingNode srcNameAttrNode = srcAttributeNode(srcNameAttr, nameAttrGraph);
        Edge srcToCoreNameAttr = edge(srcNameAttrNode, coreNameAttrNode, nameAttrGraph);

        MappingNode coreQAttrNode = coreAttributeNode(coreQualityAttribute, qualiytAttrGraph);
        MappingNode srcQAttrNode = srcAttributeNode(srcQualityAttribute, qualiytAttrGraph);

        //incoming_change IS update
        Map<String, Object> predicateMap = new HashMap<>();
        var preidcates = List.of(Map.of(
                "left", Map.of( "type", "variable", "value", srcNameAttr.getId()),
                "operator", "empty"

        ));
        predicateMap.put("predicate", Map.of("predicates", preidcates, "operator", "AND"));
        MappingNode filterUpdates =
                new MappingNode().setScope(Scope.ATTRIBUTE).setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall()
                        .setFunctionDefinition(functionService.findByNameAndScope("filter", Scope.ATTRIBUTE).get())
                        .setParams(List.of(ParameterValue.string("output_" + srcQAttrNode.getId()+".x.typedValue", "input")))
                        .setConfig(predicateMap)
                )).setName("Is Empty");
        filterUpdates.setId(ObjectId.get().toHexString());

        qualiytAttrGraph.getNodes().add(filterUpdates);
        Edge srcToFilterQ = edge(srcQAttrNode, filterUpdates, qualiytAttrGraph);
        Edge filterToCoreQ = edge(filterUpdates, coreQAttrNode, qualiytAttrGraph);
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
                .addValue("Revenue", 300.0)
                .addValue("Sync Quality", "GOOD");
        List<EntityData> t = List.of(entityData);

        when(schemaService.getEntity(srcEntityDef.getId())).thenReturn(srcEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(connector);
        when(entityRepo.findById(coreEntityDef,syncariId)).thenReturn(Optional.empty());
        when(schemaService.getAttribute(srcNameAttr.getId())).thenReturn(srcNameAttr);
        when(schemaService.getAttribute(srcQualityAttribute.getId())).thenReturn(srcQualityAttribute);
        when(schemaService.getAttribute(srcRevenueAttribute.getId())).thenReturn(srcRevenueAttribute);

        GraphContext currentContext = new GraphContext();
        currentContext.setCurrentBatch(createCurrentBatch());
        currentContext.set("field_" + srcQualityAttribute.getId(), "BAD");
        currentContext.set("field_"+srcRevenueAttribute.getId(),300.0d);
        currentContext.setGraph(entityGraph);
        RecordsBySyncariId recordsBySyncariId = new RecordsBySyncariId(syncariId);
        FieldsGraphRequest request = new FieldsGraphRequest().setEntityName("account")
                .setRecords(recordsBySyncariId)
                .setGraphContext(currentContext)
                .setAttributeDAGs(Map.of(coreQualityAttribute, qualiytAttrGraph))
                .setSyncariEntityDef(coreEntityDef)
                .setAttributeBatchActionContext(new BatchActionContext())
                .setBatchedOperations(new BatchedOperations())
                .setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>());

        Change change = executeFieldPipeline.createSyncariEntityWithGraph(request);
        //is empty is true, so set value to BAD
        assertEquals("BAD",change.getChanges().getValue(coreQualityAttribute.getApiName()));

        currentContext = new GraphContext();
        currentContext.setCurrentBatch(createCurrentBatch());
        currentContext.set("field_" + srcQualityAttribute.getId(), "GOOD");
        currentContext.set("field_"+srcRevenueAttribute.getId(),300.0d);
        currentContext.set("field_"+srcNameAttr.getId(),"Account has Name");
        currentContext.setGraph(entityGraph);
        request.setGraphContext(currentContext);
        change = executeFieldPipeline.createSyncariEntityWithGraph(request);
        //is empty is false, so no change
        assertFalse(change.getChanges().has(coreQualityAttribute.getApiName()));

        currentContext = new GraphContext();
        currentContext.setCurrentBatch(createCurrentBatch());
        currentContext.set("field_" + srcQualityAttribute.getId(), "GOOD");
        currentContext.set("field_"+srcRevenueAttribute.getId(),300.0d);
        currentContext.setGraph(entityGraph);
        request.setGraphContext(currentContext);
        change = executeFieldPipeline.createSyncariEntityWithGraph(request);
        //is empty is now false agaon, so value is sets
        assertEquals("GOOD",change.getChanges().getValue(coreQualityAttribute.getApiName()));

    }

    @Test
    public void sourceConnectedAction() {
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


        EntityDefinition srcEntityDef = new EntityDefinition();
        srcEntityDef.setConnectorId(connector.getId());
        srcEntityDef.setApiName("Organization");
        srcEntityDef.setDisplayName("Organization");
        srcEntityDef.setStatus(Status.ACTIVE);
        srcEntityDef.setId(ObjectId.get().toHexString());

        AttributeDefinition coreNameAttr = SchemaHelper.createAttribute("Name", new StringType(), coreEntityDef.getId());

        AttributeDefinition srcNameAttr = SchemaHelper.createAttribute("SourceName", new StringType(), srcEntityDef.getId());

        MappingGraph entityGraph = GraphHelper.newGraph(coreEntityDef,functionService)
                .src(srcEntityDef)
                .connect(srcEntityDef.getApiName(),coreEntityDef.getApiName())
                .getGraph();

        MappingGraph nameAttrGraph = GraphHelper.newGraph(coreNameAttr,functionService, actionDefinitionRepo)
                .src(srcNameAttr)
                .action("sendEmail")
                .connect(srcNameAttr.getApiName(),"sendEmail")
                .connect(srcNameAttr.getApiName(),coreNameAttr.getApiName())
                .getGraph();


        String syncariId = ObjectId.get().toHexString();
        srcEntityDef.addField(srcNameAttr);
        coreEntityDef.addField(coreNameAttr);

        EntityData entityData = new EntityData("account")
                .setSyncariEntityId(syncariId)
                .addValue("Name", "Account Name1")
                .addValue("Revenue", 300.0)
                .addValue("Sync Quality", "GOOD");
        List<EntityData> t = List.of(entityData);

        when(schemaService.getEntity(srcEntityDef.getId())).thenReturn(srcEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(connector);
        when(entityRepo.findById(coreEntityDef,syncariId)).thenReturn(Optional.of(entityData));
        when(schemaService.getAttribute(srcNameAttr.getId())).thenReturn(srcNameAttr);
        when(actions.isValidAction(any(), any())).thenCallRealMethod();
        when(actions.dispatch(any(), any(), any())).thenCallRealMethod();

        GraphContext currentContext = new GraphContext();
        currentContext.set("field_"+srcNameAttr.getId(),"Account Name");
        currentContext.setGraph(entityGraph);
        currentContext.setCurrentBatch(createCurrentBatch());
        RecordsBySyncariId recordsBySyncariId = new RecordsBySyncariId(syncariId);
        recordsBySyncariId.setExistingRecord(entityData);
        FieldsGraphRequest request = new FieldsGraphRequest().setEntityName("account")
                .setRecords(recordsBySyncariId)
                .setGraphContext(currentContext)
                .setAttributeDAGs(Map.of(coreNameAttr, nameAttrGraph))
                .setSyncariEntityDef(coreEntityDef)
                .setAttributeBatchActionContext(new BatchActionContext())
                .setBatchedOperations(new BatchedOperations()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>());


        Change change = executeFieldPipeline.createSyncariEntityWithGraph(request);
        verify(actions, times(1)).sendEmail(any(), any());
        assertTrue(change.getChanges().has("Name"));
        assertTrue(change.getTransactionLog().getChange(coreNameAttr.getId()).isPresent());
        assertEquals("Account Name", change.getTransactionLog().getChange(coreNameAttr.getId()).get().getNewValue());
        assertEquals("Account Name1", change.getTransactionLog().getChange(coreNameAttr.getId()).get().getOldValue());

    }

    private static CurrentBatch createCurrentBatch() {
        return new CurrentBatch(null).setCurrentBatchId(UUID.randomUUID().toString());
    }

    @Test
    public void inlineActions() {
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


        EntityDefinition srcEntityDef = new EntityDefinition();
        srcEntityDef.setConnectorId(connector.getId());
        srcEntityDef.setApiName("Organization");
        srcEntityDef.setDisplayName("Organization");
        srcEntityDef.setStatus(Status.ACTIVE);
        srcEntityDef.setId(ObjectId.get().toHexString());

        AttributeDefinition coreNameAttr = SchemaHelper.createAttribute("Name", new StringType(), coreEntityDef.getId());

        AttributeDefinition srcNameAttr = SchemaHelper.createAttribute("SourceName", new StringType(), srcEntityDef.getId());

        MappingGraph entityGraph = GraphHelper.newGraph(coreEntityDef,functionService)
                .src(srcEntityDef)
                .connect(srcEntityDef.getApiName(),coreEntityDef.getApiName())
                .getGraph();

        MappingGraph nameAttrGraph = GraphHelper.newGraph(coreNameAttr,functionService, actionDefinitionRepo)
                .src(srcNameAttr)
                .function("lower")
                .action("sendEmail")
                .connect(srcNameAttr.getApiName(),"sendEmail")
                .connect("sendEmail","lower")
                .connect("lower",coreNameAttr.getApiName())
                .getGraph();


        String syncariId = ObjectId.get().toHexString();
        srcEntityDef.addField(srcNameAttr);
        coreEntityDef.addField(coreNameAttr);

        EntityData entityData = new EntityData("account")
                .setSyncariEntityId(syncariId)
                .addValue("Name", "Account Name1")
                .addValue("Revenue", 300.0)
                .addValue("Sync Quality", "GOOD");
        List<EntityData> t = List.of(entityData);

        when(schemaService.getEntity(srcEntityDef.getId())).thenReturn(srcEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(connector);
        when(entityRepo.findById(coreEntityDef,syncariId)).thenReturn(Optional.of(entityData));
        when(schemaService.getAttribute(srcNameAttr.getId())).thenReturn(srcNameAttr);
        when(actions.isValidAction(any(), any())).thenCallRealMethod();
        when(actions.dispatch(any(), any(), any())).thenCallRealMethod();


        GraphContext currentContext = new GraphContext();
        currentContext.setCurrentBatch(createCurrentBatch());
        currentContext.set("field_" + srcNameAttr.getId(), "Account Name");
        currentContext.setGraph(entityGraph);
        RecordsBySyncariId recordsBySyncariId = new RecordsBySyncariId(syncariId);
        recordsBySyncariId.setExistingRecord(entityData);
        FieldsGraphRequest request = new FieldsGraphRequest().setEntityName("account")
                .setRecords(recordsBySyncariId)
                .setGraphContext(currentContext)
                .setAttributeDAGs(Map.of(coreNameAttr, nameAttrGraph))
                .setSyncariEntityDef(coreEntityDef)
                .setAttributeBatchActionContext(new BatchActionContext())
                .setBatchedOperations(new BatchedOperations()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>());


        Change change = executeFieldPipeline.createSyncariEntityWithGraph(request);
        verify(actions,times(1)).sendEmail(any(),any());
        assertTrue(change.getChanges().has("Name"));
        assertTrue(change.getTransactionLog().getChange(coreNameAttr.getId()).isPresent());
        assertEquals("account name", change.getTransactionLog().getChange(coreNameAttr.getId()).get().getNewValue());
        assertEquals("Account Name1", change.getTransactionLog().getChange(coreNameAttr.getId()).get().getOldValue());

    }

    @Test
    public void sourceConnectedActionsFilterOnIncomingChange() {
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


        EntityDefinition srcEntityDef = new EntityDefinition();
        srcEntityDef.setConnectorId(connector.getId());
        srcEntityDef.setApiName("Organization");
        srcEntityDef.setDisplayName("Organization");
        srcEntityDef.setStatus(Status.ACTIVE);
        srcEntityDef.setId(ObjectId.get().toHexString());

        AttributeDefinition coreNameAttr = SchemaHelper.createAttribute("Name", new StringType(), coreEntityDef.getId());

        AttributeDefinition srcNameAttr = SchemaHelper.createAttribute("SourceName", new StringType(), srcEntityDef.getId());

        MappingGraph entityGraph = GraphHelper.newGraph(coreEntityDef,functionService)
                .src(srcEntityDef)
                .connect(srcEntityDef.getApiName(),coreEntityDef.getApiName())
                .getGraph();


        //incoming_change IS update
        Map<String, Object> predicateMap = new HashMap<>();
        var preidcates = List.of(Map.of(
                "left", Map.of("datatype", "exact_match", "type", "variable", "value", "incoming_change"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "update")
        ));
        predicateMap.put("predicate", Map.of("predicates", preidcates, "operator", "AND"));


        MappingGraph nameAttrGraph = GraphHelper.newGraph(coreNameAttr,functionService, actionDefinitionRepo)
                .src(srcNameAttr)
                .action("sendEmail")
                .function("filter","filter",predicateMap)
                .connect(srcNameAttr.getApiName(),"filter")
                .connect("filter","sendEmail")
                .connect(srcNameAttr.getApiName(),coreNameAttr.getApiName())
                .getGraph();

        String syncariId = ObjectId.get().toHexString();
        srcEntityDef.addField(srcNameAttr);

        EntityData entityData = new EntityData("account")
                .setSyncariEntityId(syncariId)
                .addValue("Name", "Account Name")
                .addValue("Revenue", 300.0)
                .addValue("Sync Quality", "GOOD");
        List<EntityData> t = List.of(entityData);

        when(schemaService.getEntity(srcEntityDef.getId())).thenReturn(srcEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(connector);
        when(entityRepo.findById(coreEntityDef,syncariId)).thenReturn(Optional.of(entityData),Optional.of(entityData),Optional.empty());
        when(schemaService.getAttribute(srcNameAttr.getId())).thenReturn(srcNameAttr);
        when(actions.isValidAction(any(), any())).thenCallRealMethod();
        when(actions.dispatch(any(), any(), any())).thenCallRealMethod();


        GraphContext currentContext = new GraphContext();
        currentContext.setCurrentBatch(createCurrentBatch());
        currentContext.set("field_" + srcNameAttr.getId(), "Account Name");
        currentContext.setGraph(entityGraph);
        RecordsBySyncariId recordsBySyncariId = new RecordsBySyncariId(syncariId);
        recordsBySyncariId.setExistingRecord(entityData);
        FieldsGraphRequest request = new FieldsGraphRequest().setEntityName("account")
                .setRecords(recordsBySyncariId)
                .setGraphContext(currentContext)
                .setAttributeDAGs(Map.of(coreNameAttr, nameAttrGraph))
                .setSyncariEntityDef(coreEntityDef)
                .setAttributeBatchActionContext(new BatchActionContext())
                .setBatchedOperations(new BatchedOperations()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>());


        Change change = executeFieldPipeline.createSyncariEntityWithGraph(request);
        //Incoming change is an update, so email action is called
        verify(actions,times(1)).sendEmail(any(),any());
        //Run again
        executeFieldPipeline.createSyncariEntityWithGraph(request);
        //sendEmail is  called again - total invocations is now 2
        verify(actions,times(1)).sendEmail(any(),any());
        //Run again, but this time, there is no existing entitys
        executeFieldPipeline.createSyncariEntityWithGraph(request);
        //sendEmail is not called - total invocations is still at 2
        verify(actions,times(1)).sendEmail(any(),any());

    }

    @Ignore
    @Test
    public void coreConnectedActionsFilterOnIncomingChange() {
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


        EntityDefinition srcEntityDef = new EntityDefinition();
        srcEntityDef.setConnectorId(connector.getId());
        srcEntityDef.setApiName("Organization");
        srcEntityDef.setDisplayName("Organization");
        srcEntityDef.setStatus(Status.ACTIVE);
        srcEntityDef.setId(ObjectId.get().toHexString());

        AttributeDefinition coreNameAttr = SchemaHelper.createAttribute("Name", new StringType(), coreEntityDef.getId());

        AttributeDefinition srcNameAttr = SchemaHelper.createAttribute("SourceName", new StringType(), srcEntityDef.getId());

        MappingGraph entityGraph = GraphHelper.newGraph(coreEntityDef,functionService)
                .src(srcEntityDef)
                .connect(srcEntityDef.getApiName(),coreEntityDef.getApiName())
                .getGraph();

        //incoming_change IS update
        Map<String, Object> predicateMap = new HashMap<>();
        var preidcates = List.of(Map.of(
                "left", Map.of("datatype", "exact_match", "type", "variable", "value", "incoming_change"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "update")
        ));
        predicateMap.put("predicate", Map.of("predicates", preidcates, "operator", "AND"));

        MappingGraph nameAttrGraph = GraphHelper.newGraph(coreNameAttr,functionService, actionDefinitionRepo)
                .src(srcNameAttr)
                .action("sendEmail")
                .function("filter","filter",predicateMap)
                .connect(srcNameAttr.getApiName(),coreNameAttr.getApiName())
                .connect(coreNameAttr.getApiName(),"filter")
                .connect("filter","sendEmail")
                .getGraph();

        String syncariId = ObjectId.get().toHexString();
        srcEntityDef.addField(srcNameAttr);

        EntityData entityData = new EntityData("account")
                .setSyncariEntityId(syncariId)
                .addValue("Name", "Account Name")
                .addValue("Revenue", 300.0)
                .addValue("Sync Quality", "GOOD");
        List<EntityData> t = List.of(entityData);

        when(schemaService.getEntity(srcEntityDef.getId())).thenReturn(srcEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(connector);
        when(entityRepo.findById(coreEntityDef,syncariId)).thenReturn(Optional.of(entityData),Optional.of(entityData),Optional.empty());
        when(schemaService.getAttribute(srcNameAttr.getId())).thenReturn(srcNameAttr);
        when(schemaService.getAttribute(srcNameAttr.getId())).thenReturn(srcNameAttr);

        GraphContext currentContext = new GraphContext();
        currentContext.setCurrentBatch(createCurrentBatch());
        currentContext.set("field_" + srcNameAttr.getId(), "Account Name");
        currentContext.setGraph(entityGraph);
        RecordsBySyncariId recordsBySyncariId = new RecordsBySyncariId(syncariId);
        recordsBySyncariId.setExistingRecord(entityData);
        when(actions.isValidAction(any(), any())).thenCallRealMethod();
        when(actions.dispatch(any(), any(), any())).thenCallRealMethod();

        FieldsGraphRequest request = new FieldsGraphRequest().setEntityName("account")
                .setRecords(recordsBySyncariId)
                .setGraphContext(currentContext)
                .setAttributeDAGs(Map.of(coreNameAttr, nameAttrGraph))
                .setSyncariEntityDef(coreEntityDef)
                .setAttributeBatchActionContext(new BatchActionContext())
                .setBatchedOperations(new BatchedOperations()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>());


        Change change = executeFieldPipeline.createSyncariEntityWithGraph(request);
        System.out.println("ONCE");
        //Incoming change is an update, so email action is called
        verify(actions,times(1)).sendEmail(any(),any());

        //Run again
        executeFieldPipeline.createSyncariEntityWithGraph(request);
        //sendEmail is  called again - total invocations is now 2
        verify(actions,times(1)).sendEmail(any(),any());
        //Run again, but this time, there is no existing entitys
        executeFieldPipeline.createSyncariEntityWithGraph(request);
        //sendEmail is not called - total invocations is still at 2
        verify(actions,times(1)).sendEmail(any(),any());

    }

    @Test
    public void emptyValueRejected() {
        ViperContext context = new ViperContext(new Organization(), new Instance(), new User());

        Connector connector = new Connector("my zendesk connector", "zendeskConnectorId",
                "https://someendpoint");
        connector.setId("my zendesk connector");
        connector.setMetadata(new ConnectorMetadata("zendeskConnectorId"));
        EntityDefinition coreEntityDef = SchemaHelper.createEntityDef("account", "Account", null);

        EntityDefinition srcEntityDef = SchemaHelper.createEntityDef("Organization", "Organization", connector);

        AttributeDefinition coreQualityAttribute = SchemaHelper.createAttribute("Quality", new StringType(), coreEntityDef.getId());
        AttributeDefinition srcQualityAttribute = SchemaHelper.createAttribute("Sink Quality", new StringType(), srcEntityDef.getId());

        MappingGraph entityGraph= newGraph(coreEntityDef,functionService)
                .src(srcEntityDef)
                .connect(srcEntityDef.getApiName(),coreEntityDef.getApiName()).getGraph();

        MappingGraph qualiytAttrGraph= newGraph(coreQualityAttribute,functionService)
                .src(srcQualityAttribute)
                .connect(srcQualityAttribute.getApiName(),coreQualityAttribute.getApiName()).getGraph();


        String syncariId = ObjectId.get().toHexString();
        srcEntityDef.addField(srcQualityAttribute);
        EntityData existingRecord = new EntityData("account")
                .setSyncariEntityId(syncariId)
                .addValue(coreQualityAttribute.getApiName(), "GOOD");

        EntityData entityData = new EntityData("account")
                .setSyncariEntityId(syncariId)
                .addValue(srcQualityAttribute.getApiName(), "");
        List<EntityData> t = List.of(entityData);

        when(schemaService.getEntity(srcEntityDef.getId())).thenReturn(srcEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(connector);
        when(entityRepo.findById(coreEntityDef,syncariId)).thenReturn(Optional.of(existingRecord));
        when(schemaService.getAttribute(srcQualityAttribute.getId())).thenReturn(srcQualityAttribute);
        when(schemaService.getAttribute(srcQualityAttribute.getId())).thenReturn(srcQualityAttribute);

        GraphContext currentContext = new GraphContext();
        currentContext.setCurrentBatch(createCurrentBatch());
        currentContext.setGraph(entityGraph);
        currentContext.set("field_"+srcQualityAttribute.getId(),"");
        RecordsBySyncariId recordsBySyncariId = new RecordsBySyncariId(syncariId);
        recordsBySyncariId.setExistingRecord(existingRecord);
        FieldsGraphRequest request = new FieldsGraphRequest().setEntityName("account")
                .setRecords(recordsBySyncariId)
                .setGraphContext(currentContext)
                .setAttributeDAGs(Map.of(coreQualityAttribute, qualiytAttrGraph))
                .setSyncariEntityDef(coreEntityDef)
                .setAttributeBatchActionContext(new BatchActionContext())
                .setBatchedOperations(new BatchedOperations()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>());

        Change change = executeFieldPipeline.createSyncariEntityWithGraph(request);
        assertEquals("GOOD",change.getChanges().getValueAsString("Quality"));

        CoreAttributeNodeConfig config = qualiytAttrGraph.getCoreNode().getTypedConfiguration();
        config.setRejectEmptyString(false);
        qualiytAttrGraph.getCoreNode().setConfiguration(config);
        request.setAttributeDAGs(Map.of(coreQualityAttribute, qualiytAttrGraph));
        currentContext = new GraphContext();
        currentContext.setCurrentBatch(createCurrentBatch());
        currentContext.setGraph(entityGraph);
        currentContext.set("field_"+srcQualityAttribute.getId(),"");
        recordsBySyncariId = new RecordsBySyncariId(syncariId);
        recordsBySyncariId.setExistingRecord(existingRecord);
        change = executeFieldPipeline.createSyncariEntityWithGraph(request);
        assertEquals("", change.getChanges().getValueAsString("Quality"));

    }

    @Test
    public void dataTypeHonoredInSyncari() {
        ViperContext context = new ViperContext(new Organization(), new Instance(), new User());

        Connector connector = new Connector("my zendesk connector", "zendeskConnectorId",
                "https://someendpoint");
        connector.setId("my zendesk connector");
        connector.setMetadata(new ConnectorMetadata("zendeskConnectorId"));
        EntityDefinition coreEntityDef = SchemaHelper.createEntityDef("account", "Account", null);

        EntityDefinition srcEntityDef = SchemaHelper.createEntityDef("Organization", "Organization", connector);

        AttributeDefinition coreQualityAttribute = SchemaHelper.createAttribute("Quality", new StringType(), coreEntityDef.getId());
        AttributeDefinition srcQualityAttribute = SchemaHelper.createAttribute("SourceQuality", new DoubleType(), srcEntityDef.getId());

        MappingGraph entityGraph= newGraph(coreEntityDef,functionService)
                .src(srcEntityDef)
                .connect(srcEntityDef.getApiName(),coreEntityDef.getApiName()).getGraph();

        MappingGraph qualiytAttrGraph= newGraph(coreQualityAttribute,functionService)
                .src(srcQualityAttribute)
                .connect(srcQualityAttribute.getApiName(),coreQualityAttribute.getApiName()).getGraph();


        String syncariId = ObjectId.get().toHexString();
        srcEntityDef.addField(srcQualityAttribute);
        EntityData existingRecord = new EntityData("account")
                .setSyncariEntityId(syncariId)
                .addValue(coreQualityAttribute.getApiName(), "50");

        EntityData entityData = new EntityData("account")
                .setSyncariEntityId(syncariId)
                .addValue(srcQualityAttribute.getApiName(), 300d);
        List<EntityData> t = List.of(entityData);

        when(schemaService.getEntity(srcEntityDef.getId())).thenReturn(srcEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(connector);
        when(entityRepo.findById(coreEntityDef,syncariId)).thenReturn(Optional.of(existingRecord));
        when(schemaService.getAttribute(srcQualityAttribute.getId())).thenReturn(srcQualityAttribute);
        when(schemaService.getAttribute(srcQualityAttribute.getId())).thenReturn(srcQualityAttribute);

        GraphContext currentContext = new GraphContext();
        currentContext.setCurrentBatch(createCurrentBatch());
        currentContext.setGraph(entityGraph);
        currentContext.set("field_"+srcQualityAttribute.getId(),300d);
        RecordsBySyncariId recordsBySyncariId = new RecordsBySyncariId(syncariId);
        recordsBySyncariId.setExistingRecord(entityData);
        FieldsGraphRequest request = new FieldsGraphRequest().setEntityName("account")
                .setRecords(recordsBySyncariId)
                .setGraphContext(currentContext)
                .setAttributeDAGs(Map.of(coreQualityAttribute, qualiytAttrGraph))
                .setSyncariEntityDef(coreEntityDef)
                .setAttributeBatchActionContext(new BatchActionContext())
                .setBatchedOperations(new BatchedOperations()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>());

        Change change = executeFieldPipeline.createSyncariEntityWithGraph(request);
        assertEquals("300",change.getChanges().getValue("Quality"));

    }
    @Test
    public void collectMultipleEdges() {
        ViperContext context = new ViperContext(new Organization(), new Instance(), new User());
        Connector connector = SchemaHelper.createConnector("my zendesk connector","my zendesk connector","zendeskConnectorId");
        EntityDefinition coreEntityDef = SchemaHelper.createEntityDefinition("account").id().string("name")
                .dbl("Revenue").string("Quality")
                .field("Child", ChildType.VALUE,true)
                .getEntityDefinition().setConnectorId("syncariConnectorId");

        EntityDefinition coreChildEntityDef =  SchemaHelper.createEntityDefinition("CoreChildSchema",connector)
                .id().string("ChildName").string("ChildEmail").string("ChildAddress").string("ChildPhone")
                .getEntityDefinition();

        EntityDefinition srcEntityDef =  SchemaHelper.createEntityDefinition("Organization",connector)
                .id().string("Name").dbl("Revenue").string("SrcQuality").field("SrcChild", ChildType.VALUE,true)
                .getEntityDefinition();

        EntityDefinition srcChildEntityDef =  SchemaHelper.createEntityDefinition("SrcChildSchema",connector)
                .id().string("SrcName").string("SrcEmail").string("SrcAddress").string("SrcPhone")
                .getEntityDefinition();

        AttributeDefinition coreChildAttr = coreEntityDef.getFieldByName("Child").setReferenceTo("CoreChildSchema");
        AttributeDefinition srcChildAttr = srcEntityDef.getFieldByName("SrcChild").setReferenceTo("SrcChildSchema");


        MappingGraph entityGraph = GraphHelper.newGraph(coreEntityDef, functionService).src(srcEntityDef).connect(srcEntityDef.getApiName(),coreEntityDef.getApiName()).getGraph();
        //incoming_change IS update
        Map<String, Object> predicateMap = new HashMap<>();
        var preidcates = List.of(new HashMap<>( Map.of(
                "left", Map.of("datatype", "exact_match", "type", "variable", "value", "TBD"),
                "operator", "ne",
                "right", Map.of("type", "literal", "value", "srcChildRecordName")
            )
        ));
        predicateMap.put("predicate", Map.of("predicates", preidcates, "operator", "AND"));


        Map<String, Map<String, String>> pair1 = Map.of(
                "setField",Map.of("value",coreChildEntityDef.getFieldByName("ChildName").getId()),
                "fieldValue",Map.of("value","{{previous}}")
        );
        Map<String, Map<String, String>> pair2 = Map.of(
                "setField",Map.of("value",coreChildEntityDef.getFieldByName("ChildEmail").getId()),
                "fieldValue",Map.of("value","{{previous}}")
        );
        Map<String, Map<String, String>> pair3 = Map.of(
                "setField",Map.of("value",coreChildEntityDef.getFieldByName("ChildAddress").getId()),
                "fieldValue",Map.of("value","{{previous}}")
        );
        Map<String, Map<String, String>> pair4 = Map.of(
                "setField",Map.of("value",coreChildEntityDef.getFieldByName("ChildPhone").getId()),
                "fieldValue",Map.of("value","{{previous}}")
        );

        var childGraph = GraphHelper.newGraph(coreChildAttr,functionService).src(srcChildAttr)
                .function("findValue","findValue1","fieldName","{{previous.values.SrcName}}")
                .function("findValue","findValue2","fieldName","{{previous.values.SrcEmail}}")
                .function("findValue","findValue3","fieldName","{{previous.values.SrcAddress}}")
                .function("findValue","findValue4","fieldName","{{previous.values.SrcPhone}}")
                .function("filter","filterV1",predicateMap)
                .function("setFields","setFields1",Map.of("setFields",List.of(pair1)))
                .function("setFields","setFields2",Map.of("setFields",List.of(pair2)))
                .function("setFields","setFields3",Map.of("setFields",List.of(pair3)))
                .function("setFields","setFields4",Map.of("setFields",List.of(pair4)))

                .connect(srcChildAttr.getApiName(),"findValue1")
                .connect(srcChildAttr.getApiName(),"findValue2")
                .connect(srcChildAttr.getApiName(),"findValue3")
                .connect(srcChildAttr.getApiName(),"findValue4")

                .connect("findValue1","filterV1")
                .connect("filterV1","setFields1")
                .connect("setFields1","Child")
                .connect("findValue2","setFields2")
                .connect("setFields2","Child")
                .connect("findValue3","setFields3")
                .connect("setFields3","Child")
                .connect("findValue4","setFields4")
                .connect("setFields4","Child")
                .getGraph();
        //update predicate
        MappingNode findValue1Node = childGraph.getNodes().stream().filter(n -> n.getName().equals("findValue1")).findFirst().get();
        preidcates.get(0).put("left",Map.of("datatype", "exact_match", "type", "variable", "value", "output_"+findValue1Node.getId()+".x.result"));

        String syncariId = ObjectId.get().toHexString();
        List<EntityData> srcChildRecords = List.of(
                new EntityData("SrcChildSchema").setId("srccChildRecordId")
                .addValue("SrcName", "srcChildRecordName")
                .addValue("SrcEmail", "srcChildRecordEmail")
                .addValue("SrcPhone", "srcChildRecordPhone")
                .addValue("SrcAddress", "srcChildRecordAddress"),
                new EntityData("SrcChildSchema").setId("srccChildRecordId1")
                        .addValue("SrcName", "srcChildRecordName1")
                        .addValue("SrcEmail", "srcChildRecordEmail1")
                        .addValue("SrcPhone", "srcChildRecordPhone1")
                        .addValue("SrcAddress", "srcChildRecordAddress1")

        );

        EntityData entityData = new EntityData("account")
                .setSyncariEntityId(syncariId)
                .addValue("SrcChild", srcChildRecords
                );
        List<EntityData> t = List.of(entityData);

        when(schemaService.getEntity(srcEntityDef.getId())).thenReturn(srcEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(schemaService.findChildEntity(srcChildEntityDef.getConnectorId(),srcChildEntityDef.getApiName())).thenReturn(Optional.of(srcChildEntityDef));
        when(schemaService.findChildEntity(coreChildEntityDef.getConnectorId(),coreChildEntityDef.getApiName())).thenReturn(Optional.of(coreChildEntityDef));
        when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(connector);
        when(entityRepo.findById(coreEntityDef,syncariId)).thenReturn(Optional.empty());
        when(schemaService.getAttribute(srcChildAttr.getId())).thenReturn(srcChildAttr);
        //when(schemaService.)

        GraphContext currentContext = new GraphContext();
        currentContext.set("field_"+srcChildAttr.getId(),srcChildRecords);
        currentContext.setGraph(entityGraph);
        currentContext.setCurrentBatch(createCurrentBatch());
        RecordsBySyncariId recordsBySyncariId = new RecordsBySyncariId(syncariId);
        recordsBySyncariId.setExistingRecord(entityData);
        currentContext.cache(coreChildEntityDef.getFieldByName("ChildEmail").getId(),coreChildEntityDef.getFieldByName("ChildEmail"));
        currentContext.cache(coreChildEntityDef.getFieldByName("ChildName").getId(),coreChildEntityDef.getFieldByName("ChildName"));
        currentContext.cache(coreChildEntityDef.getFieldByName("ChildAddress").getId(),coreChildEntityDef.getFieldByName("ChildAddress"));
        currentContext.cache(coreChildEntityDef.getFieldByName("ChildPhone").getId(),coreChildEntityDef.getFieldByName("ChildPhone"));
        currentContext.cache(coreChildEntityDef.getId(), coreChildEntityDef);

        FieldsGraphRequest request = new FieldsGraphRequest().setEntityName("account")
                .setRecords(recordsBySyncariId)
                .setGraphContext(currentContext)
                .setAttributeDAGs(Map.of(coreChildAttr, childGraph))
                .setSyncariEntityDef(coreEntityDef)
                .setAttributeBatchActionContext(new BatchActionContext())
                .setBatchedOperations(new BatchedOperations()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>());


        Change change = executeFieldPipeline.createSyncariEntityWithGraph(request);
        //is empty is true, so set value to BAD
        List<EntityData> childrenRecords = change.getChanges().getChildrenRecords(coreChildAttr.getApiName());
        assertEquals(2, childrenRecords.size());
        assertEquals(3, childrenRecords.get(0).getValues().size());
        assertEquals(4, childrenRecords.get(1).getValues().size());
    }
    @Test
    public void upsertIdMapping(){
        final CurrentBatch mock = mock(CurrentBatch.class);
        final Connector connector = SchemaHelper.createConnector("c1", "c1", "meta1");
        final EntityDefinition externalEntityDef = SchemaHelper.createEntityDefinition("externalAccount",connector).id().string("name").getEntityDefinition();
        when(mock.lookupConnectorIdByBatchId(anyString())).thenReturn(externalEntityDef);
        final RecordsBySyncariId records = new RecordsBySyncariId(ObjectId.get().toHexString());
        originalIdMappingRepo.save(
                new IdMapping().setSyncariId(records.getSyncariId()).addMapping("c1", "1234", externalEntityDef.getId()).setEntityName("account")
        );
        executeFieldPipeline.idMappingRepo = originalIdMappingRepo;
        records.addRecord(new StagedBatchRecord().setExternalRecordId("1234").setNew(true).setStagedBatchId("123")
                .setExternalEntityDefinitionId(externalEntityDef.getId()).setEntityData(new EntityData().setId("1234")));
        //should not throw an exception
        executeFieldPipeline.upsertIdMappings(mock,"account", records, new ArrayList<>(), Optional.empty(),
                null, externalEntityDef, Optional.empty());

    }
    private Connector getConnector() {
        Connector connector = new Connector("my zendesk connector", "zendeskConnectorId",
                "https://someendpoint");
        connector.setId("my zendesk connector");
        connector.setMetadata(new ConnectorMetadata("zendeskConnectorId"));
        return connector;
    }
}
