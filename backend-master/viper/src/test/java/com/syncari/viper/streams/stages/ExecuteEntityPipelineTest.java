package com.syncari.viper.streams.stages;

import com.syncari.AbstractSyncariTest;
import com.syncari.connector.EntityData;
import com.syncari.core.actions.Actions;
import com.syncari.core.datatype.*;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.Watermark;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.PipelineEvaluator;
import com.syncari.core.repositories.customer.*;
import com.syncari.core.service.*;
import com.syncari.core.sync.CurrentBatch;
import com.syncari.core.sync.EntitySourceHelper;
import com.syncari.core.utils.GraphHelper;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.viper.ViperContext;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.syncari.core.utils.GraphHelper.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DirtiesContext
public class ExecuteEntityPipelineTest extends  AbstractSyncariTest{
    @Autowired
    PipelineEvaluator evaluator;
    @Autowired
    FunctionService functionService;
    @Autowired
    ActionDefinitionRepo actionDefinitionRepo;
    @Autowired
    EntityRepo entityRepo;
    @Autowired
    AttributeRepo attributeProxyRepo;
    @Autowired
    SyncDetailMetricService syncDetailMetricService;
    @Autowired
    EntitySourceHelper helper;

    @MockBean
    Actions actions;

    @Test
    public void compareExistingValuesFilter() {
        ExecuteEntityPipeline entityPipeline = new ExecuteEntityPipeline();
        entityPipeline.evaluator = evaluator;
        entityPipeline.syncDetailMetricService = syncDetailMetricService;
        entityPipeline.helper = helper;

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

        Map<String, Object> predicateMap = new HashMap<>();
        var predicates = List.of(Map.of(
                "left", Map.of("type", "variable", "value", coreRevenueAttribute.getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "200")
        ));
        predicateMap.put("predicate", Map.of("predicates", predicates, "operator", "AND"));
        MappingNode filterUpdates =
                new MappingNode().setScope(Scope.ENTITY).setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall()
                        .setFunctionDefinition(new FunctionDefinition()
                                .setName("filter").setOutputType(ObjectType.VALUE)
                                .setEngineType(EngineType.FUNCTION)
                                .setPositionalParams(List.of(new Parameter("input", ObjectType.VALUE, false))))
                        .setParams(List.of(ParameterValue.string("output_" + srcNode.getId() + ".x.typedValue", "input")))
                        .setConfig(predicateMap)

                )).setName("UpdatesOnly");
        filterUpdates.setId(ObjectId.get().toHexString());
        entityGraph.addNode(filterUpdates);
        edge(srcNode, filterUpdates, entityGraph);
        edge(filterUpdates, coreNode, entityGraph);

        CurrentBatch currentBatch = mock(CurrentBatch.class);

        EntityData entityData = new EntityData("account")
                .setSyncariEntityId("recordId1")
                .addValue("Name", "Account Name")
                .addValue("Revenue", 55)
                .addValue("Quality", "GOOD");
        EntityData incoming = new EntityData("account")
                .setSyncariEntityId("recordId1")
                .setId("externalRecordId1")
                .addValue(srcNameAttr.getApiName(), "Account Name")
                .addValue(srcRevenueAttribute.getApiName(), 300.0)
                .addValue(srcQualityAttribute.getApiName(), "GOOD");

        var schemaService = mock(SchemaService.class);
        var connectorService = mock(ConnectorService.class);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        var entityRepoService = mock(EntityRepoService.class);
        var idMappingRepo = mock(IdMappingRepo.class);
        var unresolvedReferenceRepo = mock(UnresolvedReferenceRepo.class);
        entityPipeline.connectorService = connectorService;
        entityPipeline.schemaService = schemaService;
        entityPipeline.entityRepoService = entityRepoService;
        entityPipeline.idMappingRepo = idMappingRepo;
        entityPipeline.unresolvedReferenceRepo = unresolvedReferenceRepo;
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(connector.getId())).thenReturn(connector);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        when(idMappingRepo.findByExternalIds(any(), any(), any(), any())).thenReturn(List.of(new IdMapping()
                .setSyncariId("recordId1").addMapping(connector.getId(),"externalRecordId1",srcEntityDef.getId())));
        when(entityRepoService.findByIds(any(), anySet())).thenReturn(List.of(entityData));
        StagedBatch stagedBatch = new StagedBatch();
        when(currentBatch.getEntityBatches()).thenReturn(Map.of(srcEntityDef, stagedBatch));
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.update(any())).thenAnswer((Answer<List<StagedBatchRecord>>) invocation -> invocation.getArgument(0));
        StagedBatchRecord stagedBatchRecord = new StagedBatchRecord();
        stagedBatchRecord.setEntityData(incoming);
        stagedBatchRecord.setExternalEntityDefinitionId(srcEntityDef.getId());
        stagedBatchRecord.setExternalRecordId("externalRecordId1");

        when(currentBatch.iterator(eq(stagedBatch))).thenReturn(List.of(List.of(stagedBatchRecord)).iterator());
        entityPipeline.execute(new ViperContext(new Organization(), new Instance(), new User()), new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph));
        assertTrue(stagedBatchRecord.isDeleted());

        predicates = List.of(Map.of(
                "left", Map.of("type", "variable", "value", coreRevenueAttribute.getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "55")
        ));
        predicateMap.put("predicate", Map.of("predicates", predicates, "operator", "AND"));
        stagedBatchRecord.setDeleted(false);
        when(currentBatch.iterator(eq(stagedBatch))).thenReturn(List.of(List.of(stagedBatchRecord)).iterator());
        entityPipeline.execute(new ViperContext(new Organization(), new Instance(), new User()), new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph));
        assertFalse(stagedBatchRecord.isDeleted());

        predicates = List.of(Map.of(
                "left", Map.of("type", "variable", "value", coreRevenueAttribute.getId()),
                "operator", "gt",
                "right", Map.of("type", "literal", "value", "100")
        ));
        predicateMap.put("predicate", Map.of("predicates", predicates, "operator", "AND"));
        stagedBatchRecord.setDeleted(false);
        when(currentBatch.iterator(eq(stagedBatch))).thenReturn(List.of(List.of(stagedBatchRecord)).iterator());
        entityPipeline.execute(new ViperContext(new Organization(), new Instance(), new User()), new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph));
        assertTrue(stagedBatchRecord.isDeleted());
    }

    @Test
    public void incomingChangeFilr() {
        ExecuteEntityPipeline entityPipeline = new ExecuteEntityPipeline();
        entityPipeline.evaluator = evaluator;
        entityPipeline.syncDetailMetricService = syncDetailMetricService;
        entityPipeline.helper = helper;

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

        Map<String, Object> predicateMap = new HashMap<>();
        var predicates = List.of(Map.of(
                "left", Map.of("type", "variable", "value", "incoming_change"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "insert")
        ));
        predicateMap.put("predicate", Map.of("predicates", predicates, "operator", "AND"));
        MappingNode filterUpdates =
                new MappingNode().setScope(Scope.ENTITY).setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall()
                        .setFunctionDefinition(new FunctionDefinition()
                                .setName("filter").setOutputType(ObjectType.VALUE)
                                .setEngineType(EngineType.FUNCTION)
                                .setPositionalParams(List.of(new Parameter("input", ObjectType.VALUE, false))))
                        .setParams(List.of(ParameterValue.string("output_" + srcNode.getId() + ".x.typedValue", "input")))
                        .setConfig(predicateMap)

                )).setName("UpdatesOnly");
        filterUpdates.setId(ObjectId.get().toHexString());
        entityGraph.addNode(filterUpdates);
        edge(srcNode, filterUpdates, entityGraph);
        edge(filterUpdates, coreNode, entityGraph);

        CurrentBatch currentBatch = mock(CurrentBatch.class);

        EntityData entityData = new EntityData("account")
                .setSyncariEntityId("recordId1")
                .addValue("Name", "Account Name")
                .addValue("Revenue", 55)
                .addValue("Quality", "GOOD");
        EntityData incoming = new EntityData("account")
                .setSyncariEntityId("recordId1")
                .setId("externalRecordId1")
                .addValue(srcNameAttr.getApiName(), "Account Name")
                .addValue(srcRevenueAttribute.getApiName(), 300.0)
                .addValue(srcQualityAttribute.getApiName(), "GOOD");

        var schemaService = mock(SchemaService.class);
        var connectorService = mock(ConnectorService.class);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        var entityRepoService = mock(EntityRepoService.class);
        var idMappingRepo = mock(IdMappingRepo.class);
        var unresolvedReferenceRepo = mock(UnresolvedReferenceRepo.class);
        entityPipeline.connectorService = connectorService;
        entityPipeline.schemaService = schemaService;
        entityPipeline.entityRepoService = entityRepoService;
        entityPipeline.idMappingRepo = idMappingRepo;
        entityPipeline.unresolvedReferenceRepo = unresolvedReferenceRepo;

        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(connector.getId())).thenReturn(connector);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        when(idMappingRepo.findByExternalIds(any(), any(), any(), any())).thenReturn(List.of(new IdMapping()
                .setSyncariId("recordId1").addMapping(connector.getId(),"externalRecordId1",srcEntityDef.getId())));
        when(entityRepoService.findByIds(any(), anySet())).thenReturn(List.of(entityData));
        StagedBatch stagedBatch = new StagedBatch();
        when(currentBatch.getEntityBatches()).thenReturn(Map.of(srcEntityDef, stagedBatch));
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.update(any())).thenAnswer((Answer<List<StagedBatchRecord>>) invocation -> invocation.getArgument(0));
        StagedBatchRecord stagedBatchRecord = new StagedBatchRecord();
        stagedBatchRecord.setEntityData(incoming);
        stagedBatchRecord.setExternalEntityDefinitionId(srcEntityDef.getId());
        stagedBatchRecord.setExternalRecordId("externalRecordId1");
        stagedBatchRecord.setNew(false);

        when(currentBatch.iterator(eq(stagedBatch))).thenReturn(List.of(List.of(stagedBatchRecord)).iterator());
        entityPipeline.execute(new ViperContext(new Organization(), new Instance(), new User()), new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph));
        assertTrue(stagedBatchRecord.isDeleted());

        predicates = List.of(Map.of(
                "left", Map.of("type", "variable", "value", coreRevenueAttribute.getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "55")
        ));
        predicateMap.put("predicate", Map.of("predicates", predicates, "operator", "AND"));
        stagedBatchRecord.setDeleted(false);
        when(currentBatch.iterator(eq(stagedBatch))).thenReturn(List.of(List.of(stagedBatchRecord)).iterator());
        entityPipeline.execute(new ViperContext(new Organization(), new Instance(), new User()), new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph));
        assertFalse(stagedBatchRecord.isDeleted());

        predicates = List.of(Map.of(
                "left", Map.of("type", "variable", "value", "incoming_change"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "insert")
        ));
        predicateMap.put("predicate", Map.of("predicates", predicates, "operator", "AND"));
        stagedBatchRecord.setDeleted(false);
        stagedBatchRecord.setNew(false);
        when(currentBatch.iterator(eq(stagedBatch))).thenReturn(List.of(List.of(stagedBatchRecord)).iterator());
        entityPipeline.execute(new ViperContext(new Organization(), new Instance(), new User()), new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph));
        assertTrue(stagedBatchRecord.isDeleted());
    }
    @Test
    public void deletedRecordsRestoredCorrectly() {
        ExecuteEntityPipeline entityPipeline = new ExecuteEntityPipeline();
        entityPipeline.evaluator = evaluator;
        entityPipeline.syncDetailMetricService = syncDetailMetricService;
        entityPipeline.helper = helper;

        Connector connector = new Connector("my zendesk connector", "zendeskConnectorId",
                "https://someendpoint");
        connector.setId("my zendesk connector");
        connector.setMetadata(new ConnectorMetadata("zendeskConnectorId"));
        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());
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

        MappingGraph entityGraph = GraphHelper.newGraph(coreEntityDef,functionService).src(srcEntityDef).connect(srcEntityDef.getApiName(),coreEntityDef.getApiName()).getGraph();

        CurrentBatch currentBatch = mock(CurrentBatch.class);

        EntityData entityData = new EntityData("account")
                .setSyncariEntityId("recordId1")
                .addValue("Name", "Account Name")
                .addValue("Revenue", 55)
                .addValue("Quality", "GOOD")
                .setDeleted(true);
        EntityData incoming = new EntityData("account")
                .setSyncariEntityId("recordId1")
                .setId("externalRecordId1")
                .addValue(srcNameAttr.getApiName(), "Account Name")
                .addValue(srcRevenueAttribute.getApiName(), 300.0)
                .addValue(srcQualityAttribute.getApiName(), "GOOD");

        var schemaService = mock(SchemaService.class);
        var connectorService = mock(ConnectorService.class);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        var entityRepoService = mock(EntityRepoService.class);
        var idMappingRepo = mock(IdMappingRepo.class);
        var unresolvedReferenceRepo = mock(UnresolvedReferenceRepo.class);
        entityPipeline.connectorService = connectorService;
        entityPipeline.schemaService = schemaService;
        entityPipeline.entityRepoService = entityRepoService;
        entityPipeline.idMappingRepo = idMappingRepo;
        entityPipeline.unresolvedReferenceRepo = unresolvedReferenceRepo;

        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(connector.getId())).thenReturn(connector);
        when(idMappingRepo.findByExternalIds(any(), any(), any(), any())).thenReturn(List.of(new IdMapping()
                .setSyncariId("recordId1").addMapping(connector.getId(),"externalRecordId1",srcEntityDef.getId())));
        when(entityRepoService.findByIds(any(), anySet())).thenReturn(List.of(entityData));
        when(entityRepoService.save(coreEntityDef,entityData)).thenReturn(entityData);
        StagedBatch stagedBatch = new StagedBatch();
        when(currentBatch.getEntityBatches()).thenReturn(Map.of(srcEntityDef, stagedBatch));
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.update(any())).thenAnswer((Answer<List<StagedBatchRecord>>) invocation -> invocation.getArgument(0));
        StagedBatchRecord stagedBatchRecord = new StagedBatchRecord();
        stagedBatchRecord.setEntityData(incoming);
        stagedBatchRecord.setExternalEntityDefinitionId(srcEntityDef.getId());
        stagedBatchRecord.setExternalRecordId("externalRecordId1");
        assertTrue(entityData.isDeleted());
        when(currentBatch.iterator(eq(stagedBatch))).thenReturn(List.of(List.of(stagedBatchRecord)).iterator());
        entityPipeline.execute(new ViperContext(new Organization(), new Instance(), new User()), new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph));

        assertFalse(entityData.isDeleted());
    }

    @Test
    public void ignoreUnprocessedDeletedRecord() {
        ExecuteEntityPipeline entityPipeline = new ExecuteEntityPipeline();
        entityPipeline.evaluator = evaluator;
        entityPipeline.syncDetailMetricService = syncDetailMetricService;
        entityPipeline.helper = helper;


        Connector connector = new Connector("my zendesk connector", "zendeskConnectorId",
                "https://someendpoint");
        connector.setId("my zendesk connector");
        connector.setMetadata(new ConnectorMetadata("zendeskConnectorId"));
        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());
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

        MappingGraph entityGraph = GraphHelper.newGraph(coreEntityDef,functionService)
                .src(srcEntityDef)
                .dest(srcEntityDef, "dest_" + srcEntityDef.getApiName())
                .connect(srcEntityDef.getApiName(),coreEntityDef.getApiName())
                .connect(coreEntityDef.getApiName(), "dest_" + srcEntityDef.getApiName())
                .getGraph();

        CurrentBatch currentBatch = mock(CurrentBatch.class);
        var currentTime = Instant.now();

        EntityData entityData = new EntityData("account")
                .setSyncariEntityId("recordId1")
                .addValue("Name", "Account Name")
                .addValue("Revenue", 55)
                .addValue("Quality", "GOOD")
                .setSyncariTimestamp(currentTime.toEpochMilli())
                .setDeleted(true);
        EntityData incoming = new EntityData("account")
                .setSyncariEntityId("recordId1")
                .setId("externalRecordId1")
                .addValue(srcNameAttr.getApiName(), "Account Name")
                .addValue(srcRevenueAttribute.getApiName(), 300.0)
                .addValue(srcQualityAttribute.getApiName(), "GOOD");

        var schemaService = mock(SchemaService.class);
        var connectorService = mock(ConnectorService.class);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        var entityRepoService = mock(EntityRepoService.class);
        var idMappingRepo = mock(IdMappingRepo.class);
        var unresolvedReferenceRepo = mock(UnresolvedReferenceRepo.class);
        var watermarkService = mock(WatermarkService.class);
        entityPipeline.connectorService = connectorService;
        entityPipeline.schemaService = schemaService;
        entityPipeline.entityRepoService = entityRepoService;
        entityPipeline.idMappingRepo = idMappingRepo;
        entityPipeline.unresolvedReferenceRepo = unresolvedReferenceRepo;
        entityPipeline.watermarkService = watermarkService;

        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(connector.getId())).thenReturn(connector);
        when(idMappingRepo.findByExternalIds(any(), any(), any())).thenReturn(List.of(new IdMapping()
                .setSyncariId("recordId1").addMapping(connector.getId(),"externalRecordId1",srcEntityDef.getId())));
        when(entityRepoService.findByIds(any(), anySet())).thenReturn(List.of(entityData));
        when(entityRepoService.save(coreEntityDef,entityData)).thenReturn(entityData);

        SyncDetail syncDetail = new SyncDetail();
        syncDetail.setWatermark(new Watermark().setEnd(currentTime.minus(1, ChronoUnit.MINUTES).toEpochMilli()));
        when(watermarkService.getDownstreamWatermark(coreEntityDef.getApiName(), srcEntityDef)).thenReturn(Optional.of(syncDetail));

        StagedBatch stagedBatch = new StagedBatch();
        when(currentBatch.getEntityBatches()).thenReturn(Map.of(srcEntityDef, stagedBatch));
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.update(any())).thenAnswer((Answer<List<StagedBatchRecord>>) invocation -> invocation.getArgument(0));
        StagedBatchRecord stagedBatchRecord = new StagedBatchRecord();
        stagedBatchRecord.setEntityData(incoming);
        stagedBatchRecord.setExternalEntityDefinitionId(srcEntityDef.getId());
        stagedBatchRecord.setExternalRecordId("externalRecordId1");
        assertTrue(entityData.isDeleted());
        when(currentBatch.iterator(eq(stagedBatch))).thenReturn(List.of(List.of(stagedBatchRecord)).iterator());
        entityPipeline.execute(new ViperContext(new Organization(), new Instance(), new User()), new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph));

        assertTrue(entityData.isDeleted());

        currentBatch = mock(CurrentBatch.class);
        currentTime = Instant.now();

        entityData = new EntityData("account")
                .setSyncariEntityId("recordId1")
                .addValue("Name", "Account Name")
                .addValue("Revenue", 55)
                .addValue("Quality", "GOOD")
                .setSyncariTimestamp(currentTime.toEpochMilli())
                .setDeleted(true);
        incoming = new EntityData("account")
                .setSyncariEntityId("recordId1")
                .setId("externalRecordId1")
                .addValue(srcNameAttr.getApiName(), "Account Name")
                .addValue(srcRevenueAttribute.getApiName(), 300.0)
                .addValue(srcQualityAttribute.getApiName(), "GOOD");

        schemaService = mock(SchemaService.class);
        connectorService = mock(ConnectorService.class);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        entityRepoService = mock(EntityRepoService.class);
        idMappingRepo = mock(IdMappingRepo.class);
        unresolvedReferenceRepo = mock(UnresolvedReferenceRepo.class);
        watermarkService = mock(WatermarkService.class);
        entityPipeline.connectorService = connectorService;
        entityPipeline.schemaService = schemaService;
        entityPipeline.entityRepoService = entityRepoService;
        entityPipeline.idMappingRepo = idMappingRepo;
        entityPipeline.unresolvedReferenceRepo = unresolvedReferenceRepo;
        entityPipeline.watermarkService = watermarkService;

        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(connector.getId())).thenReturn(connector);
        when(idMappingRepo.findByExternalIds(any(), any(), any(), any())).thenReturn(List.of(new IdMapping()
                .setSyncariId("recordId1").addMapping(connector.getId(),"externalRecordId1",srcEntityDef.getId())));
        when(entityRepoService.findByIds(any(), anySet())).thenReturn(List.of(entityData));
        when(entityRepoService.save(coreEntityDef,entityData)).thenReturn(entityData);

        syncDetail = new SyncDetail();
        syncDetail.setWatermark(new Watermark().setEnd(currentTime.toEpochMilli()));
        when(watermarkService.getDownstreamWatermark(coreEntityDef.getApiName(), srcEntityDef)).thenReturn(Optional.of(syncDetail));

        stagedBatch = new StagedBatch();
        when(currentBatch.getEntityBatches()).thenReturn(Map.of(srcEntityDef, stagedBatch));
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.update(any())).thenAnswer((Answer<List<StagedBatchRecord>>) invocation -> invocation.getArgument(0));
        stagedBatchRecord = new StagedBatchRecord();
        stagedBatchRecord.setEntityData(incoming);
        stagedBatchRecord.setExternalEntityDefinitionId(srcEntityDef.getId());
        stagedBatchRecord.setExternalRecordId("externalRecordId1");
        assertTrue(entityData.isDeleted());
        when(currentBatch.iterator(eq(stagedBatch))).thenReturn(List.of(List.of(stagedBatchRecord)).iterator());

        syncDetail.getWatermark().setEnd(currentTime.toEpochMilli());
        when(watermarkService.getDownstreamWatermark(coreEntityDef.getApiName(), srcEntityDef)).thenReturn(Optional.of(syncDetail));
        entityPipeline.execute(new ViperContext(new Organization(), new Instance(), new User()), new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph));
        assertFalse(entityData.isDeleted());

    }

    @Test
    public void sourceSideActionsExecutedCorrectly() {
        ExecuteEntityPipeline entityPipeline = new ExecuteEntityPipeline();
        entityPipeline.evaluator = evaluator;
        entityPipeline.syncDetailMetricService = syncDetailMetricService;
        entityPipeline.helper = helper;

        Connector connector = new Connector("my zendesk connector", "zendeskConnectorId",
                "https://someendpoint");
        connector.setId("my zendesk connector");
        connector.setMetadata(new ConnectorMetadata("zendeskConnectorId"));
        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());
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
        Map<String, Object> predicateMap = new HashMap<>();
        var predicates = List.of(Map.of(
                "left", Map.of("type", "variable", "value", srcRevenueAttribute.getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "200")
        ));
        predicateMap.put("predicate", Map.of("predicates", predicates, "operator", "AND"));

        MappingGraph entityGraph = GraphHelper
                .newGraph(coreEntityDef,functionService, actionDefinitionRepo)
                .src(srcEntityDef)
                .function("filter","filter",predicateMap)
                .function("isFalse")
                .action("sendEmail")
                .connect(srcEntityDef.getApiName(),"filter")
                .connect("filter","isFalse")
                .connect("isFalse","sendEmail")
                .connect("filter",coreEntityDef.getApiName())
                .getGraph();

        CurrentBatch currentBatch = mock(CurrentBatch.class);

        EntityData entityData = new EntityData("account")
                .setSyncariEntityId("recordId1")
                .addValue("Name", "Account Name")
                .addValue("Revenue", 55)
                .addValue("Quality", "GOOD")
                .setDeleted(true);
        EntityData incoming = new EntityData("account")
                .setSyncariEntityId("recordId1")
                .setId("externalRecordId1")
                .addValue(srcNameAttr.getApiName(), "Account Name")
                .addValue(srcRevenueAttribute.getApiName(), 100)
                .addValue(srcQualityAttribute.getApiName(), "GOOD");

        var schemaService = mock(SchemaService.class);
        var connectorService = mock(ConnectorService.class);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        var entityRepoService = mock(EntityRepoService.class);
        var idMappingRepo = mock(IdMappingRepo.class);
        var unresolvedReferenceRepo = mock(UnresolvedReferenceRepo.class);
        entityPipeline.connectorService = connectorService;
        entityPipeline.schemaService = schemaService;
        entityPipeline.entityRepoService = entityRepoService;
        entityPipeline.idMappingRepo = idMappingRepo;
        entityPipeline.unresolvedReferenceRepo = unresolvedReferenceRepo;

        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(connector.getId())).thenReturn(connector);
        when(idMappingRepo.findByExternalIds(any(), any(), any())).thenReturn(List.of(new IdMapping()
                .setSyncariId("recordId1").addMapping(connector.getId(),"externalRecordId1",srcEntityDef.getId())));
        when(entityRepoService.findByIds(any(), anySet())).thenReturn(List.of(entityData));
        when(entityRepoService.save(coreEntityDef,entityData)).thenReturn(entityData);
        StagedBatch stagedBatch = new StagedBatch();
        when(currentBatch.getEntityBatches()).thenReturn(Map.of(srcEntityDef, stagedBatch));
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.update(any())).thenAnswer((Answer<List<StagedBatchRecord>>) invocation -> invocation.getArgument(0));
        StagedBatchRecord stagedBatchRecord = new StagedBatchRecord();
        stagedBatchRecord.setEntityData(incoming);
        stagedBatchRecord.setExternalEntityDefinitionId(srcEntityDef.getId());
        stagedBatchRecord.setExternalRecordId("externalRecordId1");
        when(currentBatch.iterator(eq(stagedBatch))).thenReturn(List.of(List.of(stagedBatchRecord)).iterator());
        when(actions.isValidAction(any(), any())).thenCallRealMethod();
        when(actions.dispatch(any(), any(), any())).thenCallRealMethod();
        entityPipeline.execute(new ViperContext(new Organization(), new Instance(), new User()), new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph));
        verify(actions, times(1)).sendEmail(any(),any());
    }
    @Test
    public void inlineActionsExecutedCorrectly() {
        ExecuteEntityPipeline entityPipeline = new ExecuteEntityPipeline();
        entityPipeline.evaluator = evaluator;
        entityPipeline.syncDetailMetricService = syncDetailMetricService;
        entityPipeline.helper = helper;

        Connector connector = new Connector("my zendesk connector", "zendeskConnectorId",
                "https://someendpoint");
        connector.setId("my zendesk connector");
        connector.setMetadata(new ConnectorMetadata("zendeskConnectorId"));
        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());
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
        Map<String, Object> predicateMap = new HashMap<>();
        var predicates = List.of(Map.of(
                "left", Map.of("type", "variable", "value", srcRevenueAttribute.getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "200")
        ));
        predicateMap.put("predicate", Map.of("predicates", predicates, "operator", "AND"));

        MappingGraph entityGraph = GraphHelper
                .newGraph(coreEntityDef,functionService, actionDefinitionRepo)
                .src(srcEntityDef)
                .function("filter","filter",predicateMap)
                .action("sendEmail")
                .connect(srcEntityDef.getApiName(),"sendEmail")
                .connect("sendEmail","filter")
                .connect("filter",coreEntityDef.getApiName())
                .getGraph();


        CurrentBatch currentBatch = mock(CurrentBatch.class);

        EntityData entityData = new EntityData("account")
                .setSyncariEntityId("recordId1")
                .addValue("Name", "Account Name")
                .addValue("Revenue", 55)
                .addValue("Quality", "GOOD")
                .setDeleted(true);
        EntityData incoming = new EntityData("account")
                .setSyncariEntityId("recordId1")
                .setId("externalRecordId1")
                .addValue(srcNameAttr.getApiName(), "Account Name")
                .addValue(srcRevenueAttribute.getApiName(), 200)
                .addValue(srcQualityAttribute.getApiName(), "GOOD");

        var schemaService = mock(SchemaService.class);
        var connectorService = mock(ConnectorService.class);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        var entityRepoService = mock(EntityRepoService.class);
        var idMappingRepo = mock(IdMappingRepo.class);
        var unresolvedReferenceRepo = mock(UnresolvedReferenceRepo.class);
        entityPipeline.connectorService = connectorService;
        entityPipeline.schemaService = schemaService;
        entityPipeline.entityRepoService = entityRepoService;
        entityPipeline.idMappingRepo = idMappingRepo;
        entityPipeline.unresolvedReferenceRepo = unresolvedReferenceRepo;

        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(connector.getId())).thenReturn(connector);
        when(idMappingRepo.findByExternalIds(any(), any(), any())).thenReturn(List.of(new IdMapping()
                .setSyncariId("recordId1").addMapping(connector.getId(),"externalRecordId1",srcEntityDef.getId())));
        when(entityRepoService.findByIds(any(), anySet())).thenReturn(List.of(entityData));
        when(entityRepoService.save(coreEntityDef,entityData)).thenReturn(entityData);
        StagedBatch stagedBatch = new StagedBatch();
        when(currentBatch.getEntityBatches()).thenReturn(Map.of(srcEntityDef, stagedBatch));
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.update(any())).thenAnswer((Answer<List<StagedBatchRecord>>) invocation -> invocation.getArgument(0));
        StagedBatchRecord stagedBatchRecord = new StagedBatchRecord();
        stagedBatchRecord.setEntityData(incoming);
        stagedBatchRecord.setExternalEntityDefinitionId(srcEntityDef.getId());
        stagedBatchRecord.setExternalRecordId("externalRecordId1");
        when(currentBatch.iterator(eq(stagedBatch))).thenReturn(List.of(List.of(stagedBatchRecord)).iterator());
        when(actions.isValidAction(any(), any())).thenCallRealMethod();
        when(actions.dispatch(any(), any(), any())).thenCallRealMethod();

        entityPipeline.execute(new ViperContext(new Organization(), new Instance(), new User()), new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph));
        assertEquals(200, (int)stagedBatchRecord.getEntityData().getTypedValue(srcRevenueAttribute.getApiName()));
        verify(actions, times(1)).sendEmail(any(),any());
    }


    @Test
    public void epDoesNotExecuteCoreConnectedActions() {
        ExecuteEntityPipeline entityPipeline = new ExecuteEntityPipeline();
        entityPipeline.evaluator = evaluator;
        entityPipeline.syncDetailMetricService = syncDetailMetricService;
        entityPipeline.helper = helper;

        Connector connector = new Connector("my zendesk connector", "zendeskConnectorId",
                "https://someendpoint");
        connector.setId("my zendesk connector");
        connector.setMetadata(new ConnectorMetadata("zendeskConnectorId"));
        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());
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
        Map<String, Object> predicateMap = new HashMap<>();
        var predicates = List.of(Map.of(
                "left", Map.of("type", "variable", "value", srcRevenueAttribute.getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "200")
        ));
        predicateMap.put("predicate", Map.of("predicates", predicates, "operator", "AND"));

        MappingGraph entityGraph = GraphHelper
                .newGraph(coreEntityDef,functionService, actionDefinitionRepo)
                .src(srcEntityDef)
                .function("filter","filter",predicateMap)
                .function("isFalse")
                .action("sendEmail")
                .connect(srcEntityDef.getApiName(),coreEntityDef.getApiName())
                .connect(coreEntityDef.getApiName(),"filter")
                .connect("filter","isFalse")
                .connect("isFalse","sendEmail")
                .getGraph();


        CurrentBatch currentBatch = mock(CurrentBatch.class);

        EntityData entityData = new EntityData("account")
                .setSyncariEntityId("recordId1")
                .addValue("Name", "Account Name")
                .addValue("Revenue", 55)
                .addValue("Quality", "GOOD")
                .setDeleted(true);
        EntityData incoming = new EntityData("account")
                .setSyncariEntityId("recordId1")
                .setId("externalRecordId1")
                .addValue(srcNameAttr.getApiName(), "Account Name")
                .addValue(srcRevenueAttribute.getApiName(), 100)
                .addValue(srcQualityAttribute.getApiName(), "GOOD");

        var schemaService = mock(SchemaService.class);
        var connectorService = mock(ConnectorService.class);
        var entityRepoService = mock(EntityRepoService.class);
        var idMappingRepo = mock(IdMappingRepo.class);
        var unresolvedReferenceRepo = mock(UnresolvedReferenceRepo.class);
        entityPipeline.connectorService = connectorService;
        entityPipeline.schemaService = schemaService;
        entityPipeline.entityRepoService = entityRepoService;
        entityPipeline.idMappingRepo = idMappingRepo;
        entityPipeline.unresolvedReferenceRepo = unresolvedReferenceRepo;

        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(connector.getId())).thenReturn(connector);
        when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
        when(idMappingRepo.findByExternalIds(any(), any(), any())).thenReturn(List.of(new IdMapping()
                .setSyncariId("recordId1").addMapping(connector.getId(),"externalRecordId1",srcEntityDef.getId())));
        when(entityRepoService.findByIds(any(), anySet())).thenReturn(List.of(entityData));
        when(entityRepoService.save(coreEntityDef,entityData)).thenReturn(entityData);
        StagedBatch stagedBatch = new StagedBatch();
        when(currentBatch.getEntityBatches()).thenReturn(Map.of(srcEntityDef, stagedBatch));
        when(currentBatch.getSyncariEntityName()).thenReturn("account");
        when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
        when(currentBatch.update(any())).thenAnswer((Answer<List<StagedBatchRecord>>) invocation -> invocation.getArgument(0));
        StagedBatchRecord stagedBatchRecord = new StagedBatchRecord();
        stagedBatchRecord.setEntityData(incoming);
        stagedBatchRecord.setExternalEntityDefinitionId(srcEntityDef.getId());
        stagedBatchRecord.setExternalRecordId("externalRecordId1");
        when(currentBatch.iterator(eq(stagedBatch))).thenReturn(List.of(List.of(stagedBatchRecord)).iterator());

        entityPipeline.execute(new ViperContext(new Organization(), new Instance(), new User()), new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph));
        verify(actions, times(0)).sendEmail(any(),any());
    }

    @Test
    public void countOnLookup() {
        Connector connector = new Connector("my zendesk connector", "zendeskConnectorId",
                "https://someendpoint");
        connector.setId("my zendesk connector");
        connector.setMetadata(new ConnectorMetadata("zendeskConnectorId"));

        EntityDefinition srcEntityDef = SchemaHelper.createEntityDef("Organization", "Organization", connector);
        AttributeDefinition srcCountAttribute = attributeProxyRepo.save(SchemaHelper.createAttribute("count", new IntegerType(), srcEntityDef.getId()));
        try {
            ExecuteEntityPipeline entityPipeline = new ExecuteEntityPipeline();
            entityPipeline.evaluator = evaluator;
            entityPipeline.syncDetailMetricService = syncDetailMetricService;
            entityPipeline.helper = helper;

            EntityDefinition coreEntityDef = new EntityDefinition();
            coreEntityDef.setApiName("account");
            coreEntityDef.setDisplayName("Account");
            coreEntityDef.setStatus(Status.ACTIVE);
            coreEntityDef.setId(ObjectId.get().toHexString());
            AttributeDefinition coreNameAttr = SchemaHelper.createAttribute("Name", new StringType(), coreEntityDef.getId());
            AttributeDefinition srcNameAttr = SchemaHelper.createAttribute("Name", new StringType(), srcEntityDef.getId());

            AttributeDefinition coreRevenueAttribute = SchemaHelper.createAttribute("Revenue", new DoubleType(), coreEntityDef.getId());
            AttributeDefinition coreQualityAttribute = SchemaHelper.createAttribute("Quality", new StringType(), coreEntityDef.getId());
            AttributeDefinition srcRevenueAttribute = SchemaHelper.createAttribute("Revenue", new DoubleType(), srcEntityDef.getId());

            AttributeDefinition srcQualityAttribute = SchemaHelper.createAttribute("Sink Quality", new StringType(), srcEntityDef.getId());
            srcEntityDef.addField(srcCountAttribute);

            coreEntityDef.addField(coreNameAttr);
            coreEntityDef.addField(coreQualityAttribute);
            coreEntityDef.addField(coreRevenueAttribute);
            var lookupPredicate = List.of(Map.of(
                    "left", Map.of("type", "variable", "value", coreRevenueAttribute.getId()),
                    "operator", "eq",
                    "right", Map.of("type", "literal", "value", "200")
            ));
            Map<String, Object> lookupConfig = Map.of("predicate", Map.of("predicates", lookupPredicate, "operator", "AND"), "count", "true", "syncariEntityDefId", coreEntityDef.getId());

            Map<String, Object> predicateMap = new HashMap<>();
            predicateMap.put("predicate", Map.of("predicates", Map.of(), "operator", "AND"));

            MappingGraph entityGraph = GraphHelper
                    .newGraph(coreEntityDef, functionService, actionDefinitionRepo)
                    .src(srcEntityDef)
                    .function("advancedLookUpSyncariRecord", "lookupRecords", lookupConfig)
                    .function("filter", "filter", predicateMap)
                    .function("setValueOnEntity", "setValueOnEntity", Map.of("attributeDefinitionId", srcCountAttribute.getId(), "newValue", "{{previousLookupCount}}"))
                    .connect(srcEntityDef.getApiName(), "lookupRecords")
                    .connect("lookupRecords", "filter")
                    .connect("filter", "setValueOnEntity")
                    .connect("setValueOnEntity", coreEntityDef.getApiName())
                    .getGraph();

            var filterPredicate = List.of(Map.of(
                    "left", Map.of("type", "variable", "value", "output_" + entityGraph.getNodes().stream().filter(n -> n.getName().equals("lookupRecords")).findFirst().get().getId() + ".x.lookupCount"),
                    "operator", "eq",
                    "right", Map.of("type", "literal", "value", "3")
            ));
            //Update predicate
            predicateMap.put("predicate", Map.of("predicates", filterPredicate, "operator", "AND"));
            CurrentBatch currentBatch = mock(CurrentBatch.class);

            entityRepo.save(new EntityData("account")
                    .addValue("Name", "Account Name1")
                    .addValue("Revenue", 200)
                    .addValue("Quality", "GOOD"));
            entityRepo.save(new EntityData("account")
                    .addValue("Name", "Account Name2")
                    .addValue("Revenue", 200)
                    .addValue("Quality", "GOOD"));
            entityRepo.save(new EntityData("account")
                    .addValue("Name", "Account Name3")
                    .addValue("Revenue", 200)
                    .addValue("Quality", "GOOD"));

            EntityData incoming = new EntityData("account")
                    .setSyncariEntityId("recordId1")
                    .setId("externalRecordId1")
                    .addValue(srcNameAttr.getApiName(), "Account Name")
                    .addValue(srcRevenueAttribute.getApiName(), 100)
                    .addValue(srcQualityAttribute.getApiName(), "GOOD");

            var schemaService = mock(SchemaService.class);
            var connectorService = mock(ConnectorService.class);
            var entityRepoService = mock(EntityRepoService.class);
            var idMappingRepo = mock(IdMappingRepo.class);
            var unresolvedReferenceRepo = mock(UnresolvedReferenceRepo.class);
            entityPipeline.connectorService = connectorService;
            entityPipeline.schemaService = schemaService;
            entityPipeline.entityRepoService = entityRepoService;
            entityPipeline.idMappingRepo = idMappingRepo;
            entityPipeline.unresolvedReferenceRepo = unresolvedReferenceRepo;

            when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
            when(connectorService.get(connector.getId())).thenReturn(connector);
            when(connectorService.refreshAuthentication(connector)).thenReturn(connector);
            when(idMappingRepo.findByExternalIds(any(), any(), any())).thenReturn(List.of(new IdMapping()
                    .setSyncariId("recordId1").addMapping(connector.getId(), "externalRecordId1", srcEntityDef.getId())));
            when(entityRepoService.findByIds(any(), anySet())).thenReturn(List.of());
            when(entityRepoService.save(eq(coreEntityDef),any(EntityData.class))).thenAnswer(r -> ((EntityData) r.getArgument(0)).setSyncariEntityId(ObjectId.get().toHexString()));
            StagedBatch stagedBatch = new StagedBatch();
            when(currentBatch.getEntityBatches()).thenReturn(Map.of(srcEntityDef, stagedBatch));
            when(currentBatch.getSyncariEntityName()).thenReturn("account");
            when(currentBatch.getCurrentBatchId()).thenReturn("currentBatchId");
            when(currentBatch.update(any())).thenAnswer((Answer<List<StagedBatchRecord>>) invocation -> invocation.getArgument(0));
            StagedBatchRecord stagedBatchRecord = new StagedBatchRecord();
            stagedBatchRecord.setEntityData(incoming);
            stagedBatchRecord.setExternalEntityDefinitionId(srcEntityDef.getId());
            stagedBatchRecord.setExternalRecordId("externalRecordId1");
            when(currentBatch.iterator(eq(stagedBatch))).thenReturn(List.of(List.of(stagedBatchRecord)).iterator());

            GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch).setGraph(entityGraph);
            graphContext.cache(coreEntityDef.getId(), coreEntityDef);
            entityPipeline.execute(new ViperContext(new Organization(), new Instance(), new User()), graphContext);

            assertFalse(stagedBatchRecord.isDeleted());
            assertEquals(3L, stagedBatchRecord.getEntityData().getValue("count"));
        } finally {
            attributeProxyRepo.deleteById(srcCountAttribute.getId());
        }
    }

    @Test
    public void testAttachSyncariIds_withExistingMapping() {
        // Setup
        ExecuteEntityPipeline entityPipeline = new ExecuteEntityPipeline();
        entityPipeline.helper = helper;

        Connector connector = new Connector("testConnector", "testConnectorType", "https://test");
        connector.setId("connectorId1");

        EntityDefinition entityDefinition = new EntityDefinition();
        entityDefinition.setId("entityDefId1");
        entityDefinition.setConnectorId("connectorId1");
        entityDefinition.setApiName("Account");

        CurrentBatch currentBatch = mock(CurrentBatch.class);
        when(currentBatch.getSyncariEntityName()).thenReturn("Account");
        when(currentBatch.getCurrentBatchId()).thenReturn("batchId1");
        when(currentBatch.update(any())).thenAnswer((Answer<List<StagedBatchRecord>>) invocation -> invocation.getArgument(0));

        IdMappingRepo idMappingRepo = mock(IdMappingRepo.class);
        entityPipeline.idMappingRepo = idMappingRepo;

        // Create test records
        EntityData entityData1 = new EntityData("Account").setId("externalId1");
        StagedBatchRecord record1 = new StagedBatchRecord();
        record1.setEntityData(entityData1);
        record1.setExternalEntityDefinitionId("entityDefId1");
        record1.setExternalRecordId("externalId1");

        List<StagedBatchRecord> records = List.of(record1);

        // Create IdMapping that matches
        IdMapping idMapping = new IdMapping()
            .setEntityName("Account")
            .setSyncariId("syncariId1")
            .addMapping("connectorId1", "externalId1", "entityDefId1");

        when(idMappingRepo.findByExternalIds("Account", "connectorId1", "entityDefId1", List.of("externalId1")))
            .thenReturn(List.of(idMapping));

        // Execute
        List<StagedBatchRecord> result = entityPipeline.attachSyncariIds(currentBatch, entityDefinition, connector, records);

        // Verify
        assertEquals(1, result.size());
        assertEquals("syncariId1", result.get(0).getSyncariId());
        assertEquals("syncariId1", result.get(0).getEntityData().getSyncariEntityId());
        assertFalse(result.get(0).isNew());
        verify(idMappingRepo).findByExternalIds("Account", "connectorId1", "entityDefId1", List.of("externalId1"));
    }

    @Test
    public void testAttachSyncariIds_withNoExistingMapping() {
        // Setup
        ExecuteEntityPipeline entityPipeline = new ExecuteEntityPipeline();
        entityPipeline.helper = helper;

        Connector connector = new Connector("testConnector", "testConnectorType", "https://test");
        connector.setId("connectorId1");

        EntityDefinition entityDefinition = new EntityDefinition();
        entityDefinition.setId("entityDefId1");
        entityDefinition.setConnectorId("connectorId1");
        entityDefinition.setApiName("Account");

        CurrentBatch currentBatch = mock(CurrentBatch.class);
        when(currentBatch.getSyncariEntityName()).thenReturn("Account");
        when(currentBatch.getCurrentBatchId()).thenReturn("batchId1");
        when(currentBatch.update(any())).thenAnswer((Answer<List<StagedBatchRecord>>) invocation -> invocation.getArgument(0));

        IdMappingRepo idMappingRepo = mock(IdMappingRepo.class);
        entityPipeline.idMappingRepo = idMappingRepo;

        // Create test records
        EntityData entityData1 = new EntityData("Account").setId("externalId1");
        StagedBatchRecord record1 = new StagedBatchRecord();
        record1.setEntityData(entityData1);
        record1.setExternalEntityDefinitionId("entityDefId1");
        record1.setExternalRecordId("externalId1");

        List<StagedBatchRecord> records = List.of(record1);

        // No existing mappings
        when(idMappingRepo.findByExternalIds("Account", "connectorId1", "entityDefId1", List.of("externalId1")))
            .thenReturn(List.of());

        // Execute
        List<StagedBatchRecord> result = entityPipeline.attachSyncariIds(currentBatch, entityDefinition, connector, records);

        // Verify
        assertEquals(1, result.size());
        assertNotNull(result.get(0).getSyncariId());
        assertNotNull(result.get(0).getEntityData().getSyncariEntityId());
        assertTrue(result.get(0).isNew());
        verify(idMappingRepo).findByExternalIds("Account", "connectorId1", "entityDefId1", List.of("externalId1"));
    }

    @Test
    public void testAttachSyncariIds_multipleRecordsSomeMapped() {
        // Setup
        ExecuteEntityPipeline entityPipeline = new ExecuteEntityPipeline();
        entityPipeline.helper = helper;

        Connector connector = new Connector("testConnector", "testConnectorType", "https://test");
        connector.setId("connectorId1");

        EntityDefinition entityDefinition = new EntityDefinition();
        entityDefinition.setId("entityDefId1");
        entityDefinition.setConnectorId("connectorId1");
        entityDefinition.setApiName("Account");

        CurrentBatch currentBatch = mock(CurrentBatch.class);
        when(currentBatch.getSyncariEntityName()).thenReturn("Account");
        when(currentBatch.getCurrentBatchId()).thenReturn("batchId1");
        when(currentBatch.update(any())).thenAnswer((Answer<List<StagedBatchRecord>>) invocation -> invocation.getArgument(0));

        IdMappingRepo idMappingRepo = mock(IdMappingRepo.class);
        entityPipeline.idMappingRepo = idMappingRepo;

        // Create test records - 3 records, 2 have existing mappings
        EntityData entityData1 = new EntityData("Account").setId("externalId1");
        StagedBatchRecord record1 = new StagedBatchRecord();
        record1.setEntityData(entityData1);
        record1.setExternalEntityDefinitionId("entityDefId1");
        record1.setExternalRecordId("externalId1");

        EntityData entityData2 = new EntityData("Account").setId("externalId2");
        StagedBatchRecord record2 = new StagedBatchRecord();
        record2.setEntityData(entityData2);
        record2.setExternalEntityDefinitionId("entityDefId1");
        record2.setExternalRecordId("externalId2");

        EntityData entityData3 = new EntityData("Account").setId("externalId3");
        StagedBatchRecord record3 = new StagedBatchRecord();
        record3.setEntityData(entityData3);
        record3.setExternalEntityDefinitionId("entityDefId1");
        record3.setExternalRecordId("externalId3");

        List<StagedBatchRecord> records = List.of(record1, record2, record3);

        // Create IdMappings - only for externalId1 and externalId3
        IdMapping idMapping1 = new IdMapping()
            .setEntityName("Account")
            .setSyncariId("syncariId1")
            .addMapping("connectorId1", "externalId1", "entityDefId1");

        IdMapping idMapping3 = new IdMapping()
            .setEntityName("Account")
            .setSyncariId("syncariId3")
            .addMapping("connectorId1", "externalId3", "entityDefId1");

        when(idMappingRepo.findByExternalIds("Account", "connectorId1", "entityDefId1", List.of("externalId1", "externalId2", "externalId3")))
            .thenReturn(List.of(idMapping1, idMapping3));

        // Execute
        List<StagedBatchRecord> result = entityPipeline.attachSyncariIds(currentBatch, entityDefinition, connector, records);

        // Verify
        assertEquals(3, result.size());

        // Record 1 should have existing mapping
        assertEquals("syncariId1", result.get(0).getSyncariId());
        assertFalse(result.get(0).isNew());

        // Record 2 should be new
        assertNotNull(result.get(1).getSyncariId());
        assertTrue(result.get(1).isNew());

        // Record 3 should have existing mapping
        assertEquals("syncariId3", result.get(2).getSyncariId());
        assertFalse(result.get(2).isNew());
    }

    @Test
    public void testAttachSyncariIds_idMappingWithMultipleEntityDefinitions() {
        // This is the specific case we discussed - IdMapping with mappings for different entityDefinitionIds
        // The 4-parameter query should NOT return this IdMapping because it doesn't match our entityDefinitionId

        // Setup
        ExecuteEntityPipeline entityPipeline = new ExecuteEntityPipeline();
        entityPipeline.helper = helper;

        Connector connector = new Connector("testConnector", "testConnectorType", "https://test");
        connector.setId("connectorId1");

        EntityDefinition entityDefinition = new EntityDefinition();
        entityDefinition.setId("entityDefId1");
        entityDefinition.setConnectorId("connectorId1");
        entityDefinition.setApiName("Account");

        CurrentBatch currentBatch = mock(CurrentBatch.class);
        when(currentBatch.getSyncariEntityName()).thenReturn("Account");
        when(currentBatch.getCurrentBatchId()).thenReturn("batchId1");
        when(currentBatch.update(any())).thenAnswer((Answer<List<StagedBatchRecord>>) invocation -> invocation.getArgument(0));

        IdMappingRepo idMappingRepo = mock(IdMappingRepo.class);
        entityPipeline.idMappingRepo = idMappingRepo;

        // Create test record looking for externalId "123"
        EntityData entityData1 = new EntityData("Account").setId("123");
        StagedBatchRecord record1 = new StagedBatchRecord();
        record1.setEntityData(entityData1);
        record1.setExternalEntityDefinitionId("entityDefId1");
        record1.setExternalRecordId("123");

        List<StagedBatchRecord> records = List.of(record1);

        // The 4-parameter query correctly filters - no mappings returned because:
        // - If an IdMapping has mapping A (connectorId1, entityDefId2, 123) - doesn't match our entityDefId1
        // - If an IdMapping has mapping B (connectorId1, entityDefId1, 999) - doesn't match our entityId 123
        // The query requires ALL fields to match within the same $elemMatch
        when(idMappingRepo.findByExternalIds("Account", "connectorId1", "entityDefId1", List.of("123")))
            .thenReturn(List.of()); // Correctly returns empty because no mapping matches all 4 criteria

        // Execute
        List<StagedBatchRecord> result = entityPipeline.attachSyncariIds(currentBatch, entityDefinition, connector, records);

        // Verify - record should be treated as new since no matching mapping was found
        assertEquals(1, result.size());
        assertNotNull(result.get(0).getSyncariId());
        assertTrue(result.get(0).isNew());
        verify(idMappingRepo).findByExternalIds("Account", "connectorId1", "entityDefId1", List.of("123"));
    }

    @Test
    public void testAttachSyncariIds_idMappingWithMultipleMappingsForSameEntity() {
        // IdMapping with multiple mappings for the same entityDefinitionId
        // This can happen when the same Syncari record is linked to multiple external IDs

        // Setup
        ExecuteEntityPipeline entityPipeline = new ExecuteEntityPipeline();
        entityPipeline.helper = helper;

        Connector connector = new Connector("testConnector", "testConnectorType", "https://test");
        connector.setId("connectorId1");

        EntityDefinition entityDefinition = new EntityDefinition();
        entityDefinition.setId("entityDefId1");
        entityDefinition.setConnectorId("connectorId1");
        entityDefinition.setApiName("Account");

        CurrentBatch currentBatch = mock(CurrentBatch.class);
        when(currentBatch.getSyncariEntityName()).thenReturn("Account");
        when(currentBatch.getCurrentBatchId()).thenReturn("batchId1");
        when(currentBatch.update(any())).thenAnswer((Answer<List<StagedBatchRecord>>) invocation -> invocation.getArgument(0));

        IdMappingRepo idMappingRepo = mock(IdMappingRepo.class);
        entityPipeline.idMappingRepo = idMappingRepo;

        // Create test records for two different external IDs
        EntityData entityData1 = new EntityData("Account").setId("externalId1");
        StagedBatchRecord record1 = new StagedBatchRecord();
        record1.setEntityData(entityData1);
        record1.setExternalEntityDefinitionId("entityDefId1");
        record1.setExternalRecordId("externalId1");

        EntityData entityData2 = new EntityData("Account").setId("externalId2");
        StagedBatchRecord record2 = new StagedBatchRecord();
        record2.setEntityData(entityData2);
        record2.setExternalEntityDefinitionId("entityDefId1");
        record2.setExternalRecordId("externalId2");

        List<StagedBatchRecord> records = List.of(record1, record2);

        // Create IdMapping with multiple mappings for the same entityDefinition
        // Both external IDs map to the same Syncari ID
        IdMapping idMapping = new IdMapping()
            .setEntityName("Account")
            .setSyncariId("syncariId1")
            .addMapping("connectorId1", "externalId1", "entityDefId1")
            .addMapping("connectorId1", "externalId2", "entityDefId1");

        when(idMappingRepo.findByExternalIds("Account", "connectorId1", "entityDefId1", List.of("externalId1", "externalId2")))
            .thenReturn(List.of(idMapping));

        // Execute
        List<StagedBatchRecord> result = entityPipeline.attachSyncariIds(currentBatch, entityDefinition, connector, records);

        // Verify - both records should map to the same syncariId
        assertEquals(2, result.size());
        assertEquals("syncariId1", result.get(0).getSyncariId());
        assertEquals("syncariId1", result.get(1).getSyncariId());
        assertFalse(result.get(0).isNew());
        assertFalse(result.get(1).isNew());
    }

    @Test
    public void testAttachSyncariIds_disconnectedMapping() {
        // Test that disconnected mappings are still used for attaching syncariIds
        // The comment says "we need to look at all mappings, and not just connected ones"

        // Setup
        ExecuteEntityPipeline entityPipeline = new ExecuteEntityPipeline();
        entityPipeline.helper = helper;

        Connector connector = new Connector("testConnector", "testConnectorType", "https://test");
        connector.setId("connectorId1");

        EntityDefinition entityDefinition = new EntityDefinition();
        entityDefinition.setId("entityDefId1");
        entityDefinition.setConnectorId("connectorId1");
        entityDefinition.setApiName("Account");

        CurrentBatch currentBatch = mock(CurrentBatch.class);
        when(currentBatch.getSyncariEntityName()).thenReturn("Account");
        when(currentBatch.getCurrentBatchId()).thenReturn("batchId1");
        when(currentBatch.update(any())).thenAnswer((Answer<List<StagedBatchRecord>>) invocation -> invocation.getArgument(0));

        IdMappingRepo idMappingRepo = mock(IdMappingRepo.class);
        entityPipeline.idMappingRepo = idMappingRepo;

        // Create test record
        EntityData entityData1 = new EntityData("Account").setId("externalId1");
        StagedBatchRecord record1 = new StagedBatchRecord();
        record1.setEntityData(entityData1);
        record1.setExternalEntityDefinitionId("entityDefId1");
        record1.setExternalRecordId("externalId1");

        List<StagedBatchRecord> records = List.of(record1);

        // Create IdMapping with disconnected mapping
        IdMapping idMapping = new IdMapping()
            .setEntityName("Account")
            .setSyncariId("syncariId1")
            .addMapping("connectorId1", "externalId1", "entityDefId1", true); // disconnected = true

        when(idMappingRepo.findByExternalIds("Account", "connectorId1", "entityDefId1", List.of("externalId1")))
            .thenReturn(List.of(idMapping));

        // Execute
        List<StagedBatchRecord> result = entityPipeline.attachSyncariIds(currentBatch, entityDefinition, connector, records);

        // Verify - disconnected mapping should still be used
        assertEquals(1, result.size());
        assertEquals("syncariId1", result.get(0).getSyncariId());
        assertFalse(result.get(0).isNew());
    }

    @Test
    public void testAttachSyncariIds_emptyRecordsList() {
        // Test edge case with empty records list

        // Setup
        ExecuteEntityPipeline entityPipeline = new ExecuteEntityPipeline();
        entityPipeline.helper = helper;

        Connector connector = new Connector("testConnector", "testConnectorType", "https://test");
        connector.setId("connectorId1");

        EntityDefinition entityDefinition = new EntityDefinition();
        entityDefinition.setId("entityDefId1");
        entityDefinition.setConnectorId("connectorId1");
        entityDefinition.setApiName("Account");

        CurrentBatch currentBatch = mock(CurrentBatch.class);
        when(currentBatch.getSyncariEntityName()).thenReturn("Account");
        when(currentBatch.getCurrentBatchId()).thenReturn("batchId1");
        when(currentBatch.update(any())).thenAnswer((Answer<List<StagedBatchRecord>>) invocation -> invocation.getArgument(0));

        IdMappingRepo idMappingRepo = mock(IdMappingRepo.class);
        entityPipeline.idMappingRepo = idMappingRepo;

        List<StagedBatchRecord> records = List.of();

        // Execute
        List<StagedBatchRecord> result = entityPipeline.attachSyncariIds(currentBatch, entityDefinition, connector, records);

        // Verify
        assertEquals(0, result.size());
        // Should still call the repo with empty list
        verify(idMappingRepo).findByExternalIds("Account", "connectorId1", "entityDefId1", List.of());
    }
}
