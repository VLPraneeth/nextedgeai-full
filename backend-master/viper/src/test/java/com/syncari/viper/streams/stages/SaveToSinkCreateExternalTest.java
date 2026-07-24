package com.syncari.viper.streams.stages;

import com.syncari.AbstractSyncariTest;
import com.syncari.connector.EntityData;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DoubleType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.*;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.repositories.customer.IdMappingRepo;
import com.syncari.core.repositories.customer.UnresolvedRecordRepo;
import com.syncari.core.service.FunctionService;
import com.syncari.core.sync.CurrentBatch;
import com.syncari.core.utils.GraphHelper;
import com.syncari.viper.ViperContext;
import org.bson.types.ObjectId;
import org.jooq.lambda.Seq;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


public class SaveToSinkCreateExternalTest extends AbstractSyncariTest {
    @MockBean
    IdMappingRepo idMappingRepo;

    @Autowired
    UnresolvedRecordRepo unresolvedRecordRepo;
    @Autowired
    SaveToSink saveToSink;
    @Autowired
    FunctionService functionService;
    @Before
    public void init() {

        doNothing().when(eventService).log(any());
    }

    @Test
    public void processRecordResolutionsDeletesResolvedRecords(){
        UnresolvedRecord unresolvedRecord1 = new UnresolvedRecord().setConnectorId("c1").setSyncariId("syncariRecord1").setExternalEntityDefinitionId("externalEntity1").setSyncariEntityDefinitionId("syncariEntity1").setUnresolvedFieldIds(Set.of("feild1")).setStatus(UnresolvedRecord.UnResolvedRecordStatus.UNRESOLVED);
        UnresolvedRecord unresolvedRecord2 = new UnresolvedRecord().setConnectorId("c1").setSyncariId("syncariRecord2").setExternalEntityDefinitionId("externalEntity1").setSyncariEntityDefinitionId("syncariEntity1").setUnresolvedFieldIds(Set.of("feild2")).setStatus(UnresolvedRecord.UnResolvedRecordStatus.UNRESOLVED);
        //both records are marked as unresolved
        saveToSink.processRecordResolutions(List.of(unresolvedRecord1,unresolvedRecord2));
        assertEquals(2,unresolvedRecordRepo.findUnresolved("externalEntity1").size());
        unresolvedRecord1.setUnresolvedFieldIds(Set.of());
        //one is removed, other is upserted, even when no id is present
        saveToSink.processRecordResolutions(List.of(unresolvedRecord1,unresolvedRecord2));
        assertEquals(1,unresolvedRecordRepo.findUnresolved("externalEntity1").size());
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
                "right",Map.of("type","literal","value","update")
        ));
        predicateMap.put("predicate",Map.of("predicates",preidcates,"operator","AND"));
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
                .addChange(new FieldChange().setFieldId(coreRevenueAttribute.getId()).setOldValue(null).setNewValue(300.0).setApiName("Revenue"))
                .addChange(new FieldChange().setFieldId(coreQualityAttribute.getId()).setOldValue(null).setNewValue("GOOD_RECORD").setApiName("Quality"));



        EntityData entityData = new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("Name", "Account Name")
                .addValue("Revenue", 300.0)
                .addValue("Sync Quality", "GOOD");
        List<EntityData> t = List.of(entityData);
        when(idMappingRepo.findExistingMapping("account", "syncariAcctId123", "my zendesk connector",sinkEntityDef.getId())).thenReturn(Optional
                .of(new IdMapping().addMapping("my zendesk connector","someexternalid",sinkEntityDef.getId())));
        List<Record> externalEntityFOrGraphs = saveToSink.createExternalEntitiesForGraphs(t.get(0), sinkEntityDef.getConnectorId(), coreEntityDef, createContextWithBatch(), Map.of(coreQualityAttribute, qualiytAttrGraph)
                , sinkEntityDef);
        assertEquals(1,externalEntityFOrGraphs.size());
        assertTrue(externalEntityFOrGraphs.get(0).getEntityData().has("Sink Quality"));
        assertEquals("someexternalid",externalEntityFOrGraphs.get(0).getEntityData().getId());

        verify(idMappingRepo).findExistingMapping("account", "syncariAcctId123", "my zendesk connector",sinkEntityDef.getId());

    }

    @Test
    public void filterFailfulOnIncomingChangeField() {
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

        //incoming_change IS update
        Map<String, Object> predicateMap = new HashMap<>();
        var preidcates = List.of(Map.of(
                "left", Map.of("datatype","exact_match","type","variable","value","incoming_change"),
                "operator", "eq",
                "right",Map.of("type","literal","value","update")
        ));
        predicateMap.put("predicate",Map.of("predicates",preidcates,"operator","AND"));
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
                .addChange(new FieldChange().setFieldId(coreRevenueAttribute.getId()).setOldValue(null).setNewValue(300.0).setApiName("Revenue"))
                .addChange(new FieldChange().setFieldId(coreQualityAttribute.getId()).setOldValue(null).setNewValue("GOOD_RECORD").setApiName("Quality"));


        EntityData entityData = new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("Name", "Account Name")
                .addValue("Revenue", 300.0)
                .addValue("Sync Quality", "GOOD");
        List<EntityData> t = List.of(entityData);
        when(idMappingRepo.findExistingMapping("account", "syncariAcctId123", "my zendesk connector", sinkEntityDef.getId())).thenReturn(Optional.empty());
        final GraphContext currentContext = createContextWithBatch();
        currentContext.setCurrentBatch(new CurrentBatch(null).setCurrentBatchId(UUID.randomUUID().toString()));
        EntityData externalEntityFOrGraphs = saveToSink.createExternalEntitiesForGraphs(t.get(0), sinkEntityDef.getConnectorId(), coreEntityDef, currentContext, Map.of(coreQualityAttribute, qualiytAttrGraph)
                , sinkEntityDef).get(0).getEntityData();
        assertFalse(externalEntityFOrGraphs.has("Sink Quality"));
        verify(idMappingRepo).findExistingMapping("account", "syncariAcctId123", "my zendesk connector", sinkEntityDef.getId());

    }
    @Test
    public void multipleIdMappingsHandled() {
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


        MappingGraph qualiytAttrGraph = GraphHelper.newGraph(coreQualityAttribute,functionService)
                .dest(sinkQualityAttribute)
                .connect(coreQualityAttribute.getApiName(),sinkQualityAttribute.getApiName())
                .getGraph();

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
                .addChange(new FieldChange().setFieldId(coreRevenueAttribute.getId()).setOldValue(null).setNewValue(300.0).setApiName("Revenue"))
                .addChange(new FieldChange().setFieldId(coreQualityAttribute.getId()).setOldValue(null).setNewValue("GOOD_RECORD").setApiName("Quality"));



        EntityData entityData = new EntityData("account")
                .setSyncariEntityId("syncariAcctId123")
                .addValue("Name", "Account Name")
                .addValue("Revenue", 300.0)
                .addValue(coreQualityAttribute.getApiName(), "GOOD");
        List<EntityData> t = List.of(entityData);
        IdMapping idMapping = new IdMapping().setSyncariId("syncariAcctId123").setEntityName("account")
                .addMapping("my zendesk connector", "externalZDId", sinkEntityDef.getId())
                //add a dupe
                .addMapping("my zendesk connector", "externalZDId", sinkEntityDef.getId());

        when(idMappingRepo.findExistingMapping("account", "syncariAcctId123", "my zendesk connector", sinkEntityDef.getId())).thenReturn(Optional.of(idMapping));
        List<Record> records = saveToSink.createExternalEntitiesForGraphs(t.get(0), sinkEntityDef.getConnectorId(), coreEntityDef, createContextWithBatch(), Map.of(coreQualityAttribute, qualiytAttrGraph)
                , sinkEntityDef);
        assertEquals(1, records.size());
        EntityData externalEntityFOrGraphs = records.get(0).getEntityData();
        assertTrue(externalEntityFOrGraphs.has(sinkQualityAttribute.getApiName()));
        assertEquals("GOOD", externalEntityFOrGraphs.getValueAsString(sinkQualityAttribute.getApiName()));
        verify(idMappingRepo).findExistingMapping("account", "syncariAcctId123", "my zendesk connector", sinkEntityDef.getId());
    }

    private static GraphContext createContextWithBatch() {
        return new GraphContext()
                .setCurrentBatch(new CurrentBatch(null)
                        .setCurrentBatchId(UUID.randomUUID().toString()));
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
                .setName(attribute.getApiName());;
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
        attr.setId(ObjectId.get().toHexString());
        return attr;
    }


}
