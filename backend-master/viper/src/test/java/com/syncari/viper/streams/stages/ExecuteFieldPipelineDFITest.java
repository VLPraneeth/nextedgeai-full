package com.syncari.viper.streams.stages;

import com.syncari.AbstractSyncariTest;
import com.syncari.connector.EntityData;
import com.syncari.core.Features;
import com.syncari.core.dfiv2.DFIRuleExecutionResult;
import com.syncari.core.model.*;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.repositories.customer.IdMappingRepo;
import com.syncari.core.repositories.customer.StagedBatchRecordRepo;
import com.syncari.core.schema.Schema;
import com.syncari.core.service.*;
import com.syncari.core.sync.CurrentBatch;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.viper.DFIRuleExecutor;
import com.syncari.viper.ViperContext;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.util.*;
import java.util.function.BiFunction;

import static com.syncari.core.utils.GraphHelper.createConnector;
import static com.syncari.core.utils.GraphHelper.newGraph;
import static org.junit.Assert.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class for ExecuteFieldPipeline.execute() with DFI (Data Fitness Index) enabled.
 * Tests the scenario where canExecuteDFI is true and DFI rules are executed.
 */
public class ExecuteFieldPipelineDFITest extends AbstractSyncariTest {

    @Autowired
    ExecuteFieldPipeline executeFieldPipeline;

    @MockBean
    SchemaService schemaService;

    @MockBean
    EntityRepo entityRepo;

    @MockBean
    ConnectorService connectorService;

    @MockBean
    MappingGraphService graphService;

    @MockBean
    FeatureService featureService;

    @MockBean
    DFIRuleExecutor dfiRuleExecutor;

    @MockBean
    DFIExecutorService dfiExecutorService;

    @MockBean
    DataQualityService dataQualityService;

    @MockBean
    BiFunction<EntityDefinition, Document, EntityData> entityCreate;

    @MockBean
    IdMappingService idMappingService;

    @MockBean
    EntityRepoService entityRepoService;

    @Autowired
    FunctionService functionService;

    @Autowired
    IdMappingRepo idMappingRepo;

    @Autowired
    StagedBatchRecordRepo recordRepo;

    private Connector syncariConnector;
    private EntityDefinition accountEntityDef;

    @Before
    public void init() {
        doNothing().when(eventService).log(any());
    }

    @Override
    public void setUp() {
        // Reset mocks to ensure clean state when running full test suite.
        // This prevents intermittent failures caused by stale stubs from other tests
        // (e.g., returnsFirstArg() being applied to wrong method like list()).
        Mockito.reset(connectorService, schemaService, featureService, dfiExecutorService,
                dataQualityService, dfiRuleExecutor, graphService, entityRepo,
                idMappingService, entityRepoService, entityCreate);

        // Setup syncari connector BEFORE super.setUp()
        if (syncariConnector == null) {
            syncariConnector = createConnector("syncari", "syncariConnId", "syncariConnMetaId");
        }

        // Create account entity definition with attributes
        accountEntityDef = SchemaHelper.createEntityDefinition("account")
                .id()
                .string("Name")
                .string("BillingCity")
                .watermark()
                .getEntityDefinition();
        accountEntityDef.setId(ObjectId.get().toHexString());
        accountEntityDef.setConnectorId(syncariConnector.getId());

        // Mock schema service BEFORE super.setUp()
        when(schemaService.getSyncariSchema()).thenReturn(new Schema());
        when(schemaService.getEntity(anyString())).thenReturn(accountEntityDef);
        when(schemaService.getSyncariEntityByName("account")).thenReturn(Optional.of(accountEntityDef));
        when(schemaService.getSyncariEntityById(eq(accountEntityDef.getId()))).thenReturn(Optional.of(accountEntityDef));
        when(schemaService.getSyncariEntityByName("lead")).thenReturn(Optional.empty());
        when(schemaService.getSyncariEntityByName("contact")).thenReturn(Optional.empty());
        when(schemaService.getReferringAttributes(any(EntityDefinition.class))).thenReturn(Collections.emptyList());

        // Mock connector service BEFORE super.setUp()
        when(connectorService.getSyncariConnector()).thenReturn(syncariConnector);
        when(connectorService.get(anyString())).thenReturn(syncariConnector);
        when(connectorService.refreshAuthentication(any(Connector.class))).then(returnsFirstArg());

        // Mock feature service - Enable DFI feature
        when(featureService.isEnabled(Features.DfiV2Provisioning)).thenReturn(true);

        // Mock DFI executor service
        doNothing().when(dfiExecutorService).sendDFIResultNotification(any());

        // Call super LAST
        super.setUp();
    }

    @Test
    public void testExecuteWithDFIEnabled() {
        // Setup test data
        String syncariId = ObjectId.get().toHexString();
        String externalRecordId = "ext_account_1";
        String externalEntityDefId = ObjectId.get().toHexString();

        // Create entity data
        EntityData entityData = new EntityData("account")
                .setId(ObjectId.get().toHexString())
                .setSyncariEntityId(syncariId)
                .setConnectorId(syncariConnector.getId())
                .setName("account")
                .setNew(true)
                .setCreatedAt(Instant.now().toEpochMilli())
                .setLastModified(Instant.now().toEpochMilli());
        entityData.addValue("Name", "Test Account");
        entityData.addValue("BillingCity", "San Francisco");
        entityData.addValue("_source", syncariConnector.getId());

        // Create staged batch
        String stagedBatchId = ObjectId.get().toHexString();
        StagedBatch stagedBatch = new StagedBatch("account");
        stagedBatch.setId(stagedBatchId);
        stagedBatch.setConnectorId(syncariConnector.getId())
                .setCurrentBatchId("batch_123")
                .setSourceEntityName("account")
                .setSourceEntityDefinitionId(accountEntityDef.getId());

        // Create staged batch record
        StagedBatchRecord stagedRecord = new StagedBatchRecord()
                .setSyncariId(syncariId)
                .setExternalRecordId(externalRecordId)
                .setExternalEntityDefinitionId(externalEntityDefId)
                .setStagedBatchId(stagedBatchId)
                .setEntityData(entityData);
        recordRepo.save(stagedRecord);

        // Mock IdMappingService and EntityRepoService
        when(idMappingService.findBySyncariIds(anyString(), any())).thenReturn(Collections.emptyList());
        when(entityRepoService.findRecordsByIds(any(EntityDefinition.class), any())).thenReturn(Collections.emptyList());

        // Create current batch with all required services
        CurrentBatch currentBatch = new CurrentBatch(recordRepo, null, idMappingService, entityRepoService, null, null);
        currentBatch.setCurrentBatchId("batch_123");
        currentBatch.setSyncariEntityName("account");
        currentBatch.setSyncariEntity(accountEntityDef);
        currentBatch.getEntityBatches().put(accountEntityDef, stagedBatch);

        // Create mapping graph with DFI enabled
        MappingGraph graph = newGraph(accountEntityDef, functionService).getGraph();
        graph.setId(ObjectId.get().toHexString());

        // Enable data quality in graph settings
        PipelineSettings settings = new PipelineSettings();
        settings.setDataQuality(true);
        graph.setSettings(settings);

        // Create graph context
        GraphContext graphContext = new GraphContext();
        graphContext.setGraph(graph);
        graphContext.setCurrentBatch(currentBatch);

        // Create viper context
        ViperContext viperContext = new ViperContext(new Organization(), new Instance(), new User());

        // Mock entity repo - return entity when saved
        when(entityRepo.save(any(EntityDefinition.class), any(EntityData.class)))
                .thenReturn(entityData);
        when(entityRepo.count(any(EntityDefinition.class), any())).thenReturn(0L);

        // Mock graph service
        when(graphService.retrieveAttributeGraphsForEntityGraph(anyString()))
                .thenReturn(Collections.emptyList());

        // Create mock DFI rules
        DataQualityRule recordRule = createMockDataQualityRule("Record Rule", "record", "system", graph.getId());
        DataQualityRule fieldRule = createMockDataQualityRule("Field Rule", "Name", "attribute", graph.getId());

        // Mock DataQualityService - return all rules when fetched once, then filter in memory
        List<DataQualityRule> allRules = Arrays.asList(recordRule, fieldRule);
        when(dataQualityService.getAllRules(eq(graph.getId()))).thenReturn(allRules);
        when(dataQualityService.getRecordRules(any(List.class))).thenReturn(Collections.singletonList(recordRule));
        when(dataQualityService.getRulesByAttribute(anyString(), any(List.class)))
                .thenReturn(Collections.singletonList(fieldRule));

        // Mock DFI rule execution results
        DFIRuleExecutionResult recordRuleResult = new DFIRuleExecutionResult()
                .setRuleId(recordRule.getId())
                .setSyncariRecordId(syncariId)
                .setResult(true)
                .setRuleName("Record Rule")
                .setCategoryId("cat1")
                .setCategoryName("Completeness");

        DFIRuleExecutionResult fieldRuleResult = new DFIRuleExecutionResult()
                .setRuleId(fieldRule.getId())
                .setSyncariRecordId(syncariId)
                .setSyncariAttributeId(accountEntityDef.getFieldByName("Name").getId())
                .setResult(true)
                .setRuleName("Field Rule")
                .setCategoryId("cat1")
                .setCategoryName("Completeness");

        when(dfiRuleExecutor.executeDFIRecordRules(eq(syncariId), any(GraphContext.class), any(), any()))
                .thenReturn(Collections.singletonList(recordRuleResult));
        when(dfiRuleExecutor.executeDFIFieldRules(eq(syncariId), any(AttributeDefinition.class), any(GraphContext.class), any(), any()))
                .thenReturn(Collections.singletonList(fieldRuleResult));

        // Execute the pipeline
        GraphContext result = executeFieldPipeline.execute(viperContext, graphContext);

        // Verify DFI rules were fetched once (optimization)
        verify(dataQualityService, times(1)).getAllRules(eq(graph.getId()));

        // 1. Verify the SAME list returned by getAllRules is passed to filtering methods
        ArgumentCaptor<List<DataQualityRule>> recordRulesListCaptor = ArgumentCaptor.forClass(List.class);
        verify(dataQualityService, times(1)).getRecordRules(recordRulesListCaptor.capture());
        assertSame("getRecordRules should receive the same list from getAllRules", allRules, recordRulesListCaptor.getValue());

        ArgumentCaptor<List<DataQualityRule>> attrRulesListCaptor = ArgumentCaptor.forClass(List.class);
        verify(dataQualityService, atLeastOnce()).getRulesByAttribute(anyString(), attrRulesListCaptor.capture());
        // Verify all calls to getRulesByAttribute received the same allRules list
        attrRulesListCaptor.getAllValues().forEach(capturedList ->
                assertSame("getRulesByAttribute should receive the same list from getAllRules", allRules, capturedList)
        );

        // 2. Verify non-empty filtered rules reach execution methods
        ArgumentCaptor<List<DataQualityRule>> recordRulesExecutionCaptor = ArgumentCaptor.forClass(List.class);
        verify(dfiRuleExecutor, atLeastOnce()).executeDFIRecordRules(
                eq(syncariId),
                any(GraphContext.class),
                any(),
                recordRulesExecutionCaptor.capture()
        );
        assertFalse("executeDFIRecordRules should receive non-empty rule list", recordRulesExecutionCaptor.getValue().isEmpty());
        assertEquals(1, recordRulesExecutionCaptor.getValue().size());

        ArgumentCaptor<List<DataQualityRule>> attrRulesExecutionCaptor = ArgumentCaptor.forClass(List.class);
        verify(dfiRuleExecutor, atLeastOnce()).executeDFIFieldRules(
                eq(syncariId),
                any(AttributeDefinition.class),
                any(GraphContext.class),
                any(),
                attrRulesExecutionCaptor.capture()
        );
        assertFalse("executeDFIFieldRules should receive non-empty rule list", attrRulesExecutionCaptor.getValue().isEmpty());

        // 3. Verify attribute rules called for each attribute (account has 4 attributes: id, Name, BillingCity, watermark)
        int expectedAttributeCount = accountEntityDef.getAttributes().size();
        verify(dataQualityService, times(expectedAttributeCount)).getRulesByAttribute(anyString(), any(List.class));

        // Verify DFI results were sent for notification
        verify(dfiExecutorService, times(1)).sendDFIResultNotification(any());

        // Assert result context is returned
        assertNotNull(result);
        assertEquals(graph.getId(), result.getGraph().getId());
    }

    @Test
//    @Ignore
    public void testExecuteWithDFIDisabledAtFeatureLevel() {
        // Setup similar to above but with DFI feature disabled
        String syncariId = ObjectId.get().toHexString();
        EntityData entityData = new EntityData("account")
                .setId(ObjectId.get().toHexString())
                .setSyncariEntityId(syncariId)
                .setConnectorId(syncariConnector.getId())
                .setName("account")
                .setNew(true)
                .setCreatedAt(Instant.now().toEpochMilli())
                .setLastModified(Instant.now().toEpochMilli());
        entityData.addValue("Name", "Test Account");
        entityData.addValue("_source", syncariConnector.getId());

        // Create staged batch
        String stagedBatchId = ObjectId.get().toHexString();
        StagedBatch stagedBatch = new StagedBatch("account");
        stagedBatch.setId(stagedBatchId);
        stagedBatch.setConnectorId(syncariConnector.getId())
                .setCurrentBatchId("batch_123")
                .setSourceEntityName("account")
                .setSourceEntityDefinitionId(accountEntityDef.getId());

        StagedBatchRecord stagedRecord = new StagedBatchRecord()
                .setSyncariId(syncariId)
                .setExternalRecordId("ext_1")
                .setExternalEntityDefinitionId(ObjectId.get().toHexString())
                .setStagedBatchId(stagedBatchId)
                .setEntityData(entityData);
        recordRepo.save(stagedRecord);

        // Mock IdMappingService and EntityRepoService
        when(idMappingService.findBySyncariIds(anyString(), any())).thenReturn(Collections.emptyList());
        when(entityRepoService.findRecordsByIds(any(EntityDefinition.class), any())).thenReturn(Collections.emptyList());

        // Create current batch with all required services
        CurrentBatch currentBatch = new CurrentBatch(recordRepo, null, idMappingService, entityRepoService, null, null);
        currentBatch.setCurrentBatchId("batch_123");
        currentBatch.setSyncariEntityName("account");
        currentBatch.setSyncariEntity(accountEntityDef);
        currentBatch.getEntityBatches().put(accountEntityDef, stagedBatch);

        MappingGraph graph = newGraph(accountEntityDef, functionService).getGraph();
        graph.setId(ObjectId.get().toHexString());

        // Enable data quality in graph settings but disable at feature level
        PipelineSettings settings = new PipelineSettings();
        settings.setDataQuality(true);
        graph.setSettings(settings);

        GraphContext graphContext = new GraphContext();
        graphContext.setGraph(graph);
        graphContext.setCurrentBatch(currentBatch);

        // Disable DFI feature
        when(featureService.isEnabled(Features.DfiV2Provisioning)).thenReturn(false);
        when(entityRepo.save(any(EntityDefinition.class), any(EntityData.class))).thenReturn(entityData);
        when(entityRepo.count(any(EntityDefinition.class), any())).thenReturn(0L);
        when(graphService.retrieveAttributeGraphsForEntityGraph(anyString())).thenReturn(Collections.emptyList());

        // Execute
        GraphContext result = executeFieldPipeline.execute(new ViperContext(new Organization(), new Instance(), new User()), graphContext);

        // Verify DFI rules were NOT executed
        verify(dataQualityService, never()).getAllRules(anyString());
        verify(dfiRuleExecutor, never()).executeDFIRecordRules(anyString(), any(), any(), any());
        verify(dfiRuleExecutor, never()).executeDFIFieldRules(anyString(), any(), any(), any(), any());
        verify(dfiExecutorService, never()).sendDFIResultNotification(any());

        assertNotNull(result);
    }
    private static CurrentBatch createCurrentBatch() {
        return new CurrentBatch(null).setCurrentBatchId(UUID.randomUUID().toString());
    }

    @Test
//    @Ignore
    public void testExecuteWithDFIDisabledAtGraphLevel() {
        // Setup with DFI feature enabled but graph settings disabled
        String syncariId = ObjectId.get().toHexString();
        EntityData entityData = new EntityData("account")
                .setId(ObjectId.get().toHexString())
                .setSyncariEntityId(syncariId)
                .setConnectorId(syncariConnector.getId())
                .setName("account")
                .setNew(true)
                .setCreatedAt(Instant.now().toEpochMilli())
                .setLastModified(Instant.now().toEpochMilli());
        entityData.addValue("Name", "Test Account");
        entityData.addValue("_source", syncariConnector.getId());

        // Create staged batch
        String stagedBatchId = ObjectId.get().toHexString();
        StagedBatch stagedBatch = new StagedBatch("account");
        stagedBatch.setId(stagedBatchId);
        stagedBatch.setConnectorId(syncariConnector.getId())
                .setCurrentBatchId("batch_123")
                .setSourceEntityName("account")
                .setSourceEntityDefinitionId(accountEntityDef.getId());

        StagedBatchRecord stagedRecord = new StagedBatchRecord()
                .setSyncariId(syncariId)
                .setExternalRecordId("ext_1")
                .setExternalEntityDefinitionId(ObjectId.get().toHexString())
                .setStagedBatchId(stagedBatchId)
                .setEntityData(entityData);
        recordRepo.save(stagedRecord);

        // Mock IdMappingService and EntityRepoService
        when(idMappingService.findBySyncariIds(anyString(), any())).thenReturn(Collections.emptyList());
        when(entityRepoService.findRecordsByIds(any(EntityDefinition.class), any())).thenReturn(Collections.emptyList());

        // Create current batch with all required services
        CurrentBatch currentBatch = new CurrentBatch(recordRepo, null, idMappingService, entityRepoService, null, null);
        currentBatch.setCurrentBatchId("batch_123");
        currentBatch.setSyncariEntityName("account");
        currentBatch.setSyncariEntity(accountEntityDef);
        currentBatch.getEntityBatches().put(accountEntityDef, stagedBatch);

        MappingGraph graph = newGraph(accountEntityDef, functionService).getGraph();
        graph.setId(ObjectId.get().toHexString());

        // Disable data quality in graph settings
        PipelineSettings settings = new PipelineSettings();
        settings.setDataQuality(false);
        graph.setSettings(settings);

        GraphContext graphContext = new GraphContext();
        graphContext.setGraph(graph);
        graphContext.setCurrentBatch(currentBatch);

        // Enable DFI feature but disable at graph level
        when(featureService.isEnabled(Features.DfiV2Provisioning)).thenReturn(true);
        when(entityRepo.save(any(EntityDefinition.class), any(EntityData.class))).thenReturn(entityData);
        when(entityRepo.count(any(EntityDefinition.class), any())).thenReturn(0L);
        when(graphService.retrieveAttributeGraphsForEntityGraph(anyString())).thenReturn(Collections.emptyList());

        // Execute
        GraphContext result = executeFieldPipeline.execute(new ViperContext(new Organization(), new Instance(), new User()), graphContext);

        // Verify DFI rules were NOT executed
        verify(dataQualityService, never()).getAllRules(anyString());
        verify(dfiRuleExecutor, never()).executeDFIRecordRules(anyString(), any(), any(), any());
        verify(dfiRuleExecutor, never()).executeDFIFieldRules(anyString(), any(), any(), any(), any());
        verify(dfiExecutorService, never()).sendDFIResultNotification(any());

        assertNotNull(result);
    }

    /**
     * Helper method to create a mock DataQualityRule
     */
    private DataQualityRule createMockDataQualityRule(String name, String scope, String scopeType, String graphId) {
        DataQualityRule rule = new DataQualityRule();
        rule.setId(ObjectId.get().toHexString());
        rule.setName(name);
        rule.setScope(Collections.singletonList(scope));
        rule.setScopeType(scopeType);
        rule.setCategory("cat1");
        rule.setPolicy("policy1");
        rule.setIsDeleted(false);
        rule.setMappingGraphId(graphId);
        rule.setEntityId(accountEntityDef.getId());
        rule.setRuleConfig(new HashMap<>());
        return rule;
    }
}
