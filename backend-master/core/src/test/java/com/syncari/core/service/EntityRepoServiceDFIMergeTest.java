package com.syncari.core.service;

import com.syncari.connector.EntityData;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.Features;
import com.syncari.core.datatype.StringType;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.service.DFIExecutorService;
import com.syncari.core.dfiv2.DFIResultManager;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.EntityDataResponse;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.repositories.customer.DataQualityRuleRepo;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.repositories.customer.MappingGraphRepo;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.time.Instant;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

public class EntityRepoServiceDFIMergeTest extends AbstractSyncariTest {

    @Autowired
    EntityRepoService entityRepoService;

    @Autowired
    EntityRepo entityRepo;

    @Autowired
    SchemaService schemaService;

    @Autowired
    ConnectorService connectorService;

    @SpyBean
    RecordMergeService recordMergeService;

    @SpyBean
    DFIExecutorService dfiExecutorService;

    @SpyBean
    DataQualityService dataQualityService;

    @MockBean
    MappingGraphService mappingGraphService;

    @MockBean
    FeatureService featureService;

    private Connector syncariConnector;
    private EntityDefinition accountEntity;
    private MappingGraph testGraph;
    private DataQualityRule testDFIRule;

    @Before
    public void setUp() {
        super.setUp();
        entityRepo.deleteAll("account");

        syncariConnector = connectorService.getSyncariConnector();
        accountEntity = schemaService.getEntity(syncariConnector.getId(), "account");

        when(featureService.isEnabled(any())).thenReturn(true);

        testGraph = createTestGraphWithDedupeConfig();
        testDFIRule = createTestDFIRule("Test Rule", "Name", "record.Name != null");

        // Mock mappingGraphService to return our test graph
        when(mappingGraphService.retrieveApprovedEntityGraph(accountEntity.getId()))
            .thenReturn(Optional.of(testGraph));
        when(mappingGraphService.retrieveDraftEntityGraph(accountEntity.getId()))
            .thenReturn(Optional.of(testGraph));
    }

    @After
    public void tearDown() {
        entityRepo.deleteAll("account");
        super.tearDown();
    }

    @Test
    public void testCreateWithoutDFIAndMerge() {
        doReturn(Collections.emptyList()).when(dataQualityService).getAllRules(any(MappingGraph.class));

        EntityData record = createTestRecord("Test Account", "San Francisco");

        EntityDataResponse response = entityRepoService.create(record, accountEntity, false, false);

        assertTrue("Create failed with errors: " + response.getErrors(), response.isSuccess());
        assertNotNull(response.getRecord());
        assertNotNull(response.getRecord().getId());
        assertEquals("Test Account", response.getRecord().getValue("Name"));

        verify(recordMergeService, never()).advancedDedupeMerge(any(), any(), any(), any(), any(), any(), any());
        verify(dfiExecutorService, never()).evaluateRule(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void testCreateWithMergeEnabled_MergeOccurs() {
        doReturn(Collections.emptyList()).when(dataQualityService).getAllRules(any(MappingGraph.class));

        // Set empty dedupe config to avoid complex validation
        // The merge logic itself is tested in RecordMergeServiceTest
        // This test verifies the integration point
        AdvancedDedupeConfig dedupeConfig = new AdvancedDedupeConfig();

        updateGraphWithDedupeConfig(dedupeConfig);

        EntityData newRecord = createTestRecord("Acme Inc", "San Francisco");

        EntityDataResponse response = entityRepoService.create(newRecord, accountEntity, false, true);

        assertTrue(response.isSuccess());
        assertNotNull(response.getRecord());

        // Verify advancedDedupeMerge was called
        verify(recordMergeService, times(1)).advancedDedupeMerge(
            any(AdvancedDedupeConfig.class),
            any(EntityData.class),
            any(EntityDefinition.class),
            any(GraphContext.class),
            isNull(),
            any(),
            any()
        );
    }

    @Test
    public void testCreateWithMergeEnabled_NoMergeNeeded() {
        doReturn(Collections.emptyList()).when(dataQualityService).getAllRules(any(MappingGraph.class));

        AdvancedDedupeConfig dedupeConfig = new AdvancedDedupeConfig()
            .setFindDupes(Collections.emptyMap());
        updateGraphWithDedupeConfig(dedupeConfig);

        EntityData newRecord = createTestRecord("Unique Account", "Boston");

        EntityDataResponse response = entityRepoService.create(newRecord, accountEntity, false, true);

        assertTrue(response.isSuccess());
        assertNotNull(response.getRecord());

        verify(recordMergeService, times(1)).advancedDedupeMerge(
            any(AdvancedDedupeConfig.class),
            any(EntityData.class),
            any(EntityDefinition.class),
            any(GraphContext.class),
            isNull(),
            any(),
            any()
        );

        verify(recordMergeService, never()).apply(any(MergeOperation.class), any(GraphContext.class));

        long totalRecords = entityRepo.count(accountEntity.getApiName(), false);
        assertEquals(1, totalRecords);
    }

    @Test
    public void testCreateWithDFIEnabled() {
        // Mock dataQualityService to return our test rule using doReturn for better Spring mock compatibility
        doReturn(List.of(testDFIRule)).when(dataQualityService).getAllRules(any(MappingGraph.class));

        EntityData newRecord = createTestRecord("Valid Account", "Seattle");

        EntityDataResponse response = entityRepoService.create(newRecord, accountEntity, true, false);

        assertTrue(response.isSuccess());
        assertNotNull(response.getRecord());

        // Verify DFI was executed
        ArgumentCaptor<String> recordIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(dfiExecutorService, atLeastOnce()).evaluateRule(
            recordIdCaptor.capture(),
            any(),
            any(),
            eq(testDFIRule),
            any(),
            any()
        );

        verify(dfiExecutorService, times(1)).sendDFIResultNotification(any(DFIResultManager.class));

        // Verify the record ID passed to evaluateRule matches the saved record
        assertEquals(response.getRecord().getId(), recordIdCaptor.getValue());
    }

    @Test
    public void testCreateWithBothDFIAndMergeEnabled() {
        AdvancedDedupeConfig dedupeConfig = new AdvancedDedupeConfig();
        updateGraphWithDedupeConfig(dedupeConfig);

        // Mock dataQualityService to return our test rule
        doReturn(List.of(testDFIRule)).when(dataQualityService).getAllRules(any(MappingGraph.class));

        EntityData newRecord = createTestRecord("Tech Corp", "Austin");

        EntityDataResponse response = entityRepoService.create(newRecord, accountEntity, true, true);

        assertTrue(response.isSuccess());
        assertNotNull(response.getRecord());

        // Verify both merge and DFI were called
        verify(recordMergeService, times(1)).advancedDedupeMerge(
            any(AdvancedDedupeConfig.class),
            any(EntityData.class),
            any(EntityDefinition.class),
            any(GraphContext.class),
            isNull(),
            any(),
            any()
        );

        verify(dfiExecutorService, atLeastOnce()).evaluateRule(
            any(),
            any(),
            any(),
            eq(testDFIRule),
            any(),
            any()
        );
    }

    @Test
    public void testUpdateWithMergeEnabled() {
        doReturn(Collections.emptyList()).when(dataQualityService).getAllRules(any(MappingGraph.class));

        EntityData existingRecord = entityRepo.save(accountEntity,
            createTestRecord("Original Name", "Denver"));

        AdvancedDedupeConfig dedupeConfig = new AdvancedDedupeConfig();
        updateGraphWithDedupeConfig(dedupeConfig);

        EntityData updateData = new EntityData("account")
            .setId(existingRecord.getId())
            .addValue("Name", "Updated Name")
            .addValue("BillingCity", "Denver");

        EntityDataResponse response = entityRepoService.update(updateData, accountEntity, false, true);

        assertTrue(response.isSuccess());
        assertNotNull(response.getRecord());

        verify(recordMergeService, times(1)).advancedDedupeMerge(
            any(AdvancedDedupeConfig.class),
            any(EntityData.class),
            any(EntityDefinition.class),
            any(GraphContext.class),
            isNull(),
            any(),
            any()
        );
    }

    @Test
    public void testUpdateWithDFIEnabled() {
        // Mock dataQualityService to return our test rule
        doReturn(List.of(testDFIRule)).when(dataQualityService).getAllRules(any(MappingGraph.class));

        EntityData existingRecord = entityRepo.save(accountEntity,
            createTestRecord("Test Company", "Portland"));

        EntityData updateData = new EntityData("account")
            .setId(existingRecord.getId())
            .addValue("BillingCity", "Seattle");

        EntityDataResponse response = entityRepoService.update(updateData, accountEntity, true, false);

        assertTrue(response.isSuccess());
        assertNotNull(response.getRecord());
        assertEquals("Seattle", response.getRecord().getValue("BillingCity"));

        verify(dfiExecutorService, atLeastOnce()).evaluateRule(
            any(),
            any(),
            any(),
            eq(testDFIRule),
            any(),
            any()
        );

        verify(dfiExecutorService, times(1)).sendDFIResultNotification(any(DFIResultManager.class));
    }

    @Test
    public void testCreateWithDFI_NoRulesConfigured() {
        doReturn(Collections.emptyList()).when(dataQualityService).getAllRules(any(MappingGraph.class));

        EntityData newRecord = createTestRecord("No Rules Account", "Phoenix");

        EntityDataResponse response = entityRepoService.create(newRecord, accountEntity, true, false);

        assertTrue(response.isSuccess());
        assertNotNull(response.getRecord());

        // Verify evaluateRule was never called since no rules exist
        verify(dfiExecutorService, never()).evaluateRule(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void testCreateWithMerge_NoGraphConfigured() {
        doReturn(Collections.emptyList()).when(dataQualityService).getAllRules(any(MappingGraph.class));

        // Mock mappingGraphService to return empty (no graph found)
        when(mappingGraphService.retrieveApprovedEntityGraph(accountEntity.getId()))
            .thenReturn(Optional.empty());
        when(mappingGraphService.retrieveDraftEntityGraph(accountEntity.getId()))
            .thenReturn(Optional.empty());

        EntityData newRecord = createTestRecord("No Graph Account", "Miami");

        EntityDataResponse response = entityRepoService.create(newRecord, accountEntity, false, true);

        assertTrue(response.isSuccess());
        assertNotNull(response.getRecord());

        // Verify merge was never called since no graph exists
        verify(recordMergeService, never()).advancedDedupeMerge(any(), any(), any(), any(), any(), any(), any());
    }

    private EntityData createTestRecord(String name, String city) {
        long now = Instant.now().toEpochMilli();
        String userId = ObjectId.get().toHexString();

        return new EntityData("account")
            .setConnectorId(syncariConnector.getId())
            .setSyncariEntityId(ObjectId.get().toHexString())
            .setId(ObjectId.get().toHexString())
            .setLastModified(now)
            .setCreatedAt(now)
            .setNew(true)
            .addValue("Name", name)
            .addValue("BillingCity", city)
            .addValue("LastModifiedDate", now)
            .addValue("IsDeleted", false)
            .addValue("CreatedById", userId)
            .addValue("OwnerId", userId)
            .addValue("CreatedDate", now)
            .addValue("LastModifiedById", userId)
            .addValue("SystemModstamp", now);
    }

    private MappingGraph createTestGraphWithDedupeConfig() {
        // Create a minimal in-memory graph
        MappingGraph graph = new MappingGraph();
        graph.setId(ObjectId.get().toHexString());
        graph.setName("Test Graph");
        graph.setTargetId(accountEntity.getId());
        graph.setDraftStatus(DraftStatus.APPROVED);
        graph.setScope(Scope.ENTITY);

        CoreEntityNodeConfig coreConfig = new CoreEntityNodeConfig();
        coreConfig.setEntityDefinition(accountEntity);
        coreConfig.setAdvancedDedupeConfig(new AdvancedDedupeConfig()); // Default empty config

        MappingNode coreNode = new MappingNode();
        coreNode.setId(ObjectId.get().toHexString());
        coreNode.setScope(Scope.ENTITY);
        coreNode.setName(accountEntity.getApiName());
        coreNode.setApiName(accountEntity.getApiName());
        coreNode.setMappingGraphId(graph.getId());
        coreNode.setConfiguration(coreConfig);

        graph.addNode(coreNode);

        return graph;
    }

    private void updateGraphWithDedupeConfig(AdvancedDedupeConfig dedupeConfig) {
        // Update the in-memory graph's dedupe config
        MappingNode coreNode = testGraph.getCoreNode();
        CoreEntityNodeConfig coreConfig = (CoreEntityNodeConfig) coreNode.getConfiguration();
        coreConfig.setAdvancedDedupeConfig(dedupeConfig);
    }

    private DataQualityRule createTestDFIRule(String name, String fieldName, String expression) {
        DataQualityRule rule = new DataQualityRule();
        rule.setId(ObjectId.get().toHexString());
        rule.setName(name);
        rule.setEntityId(accountEntity.getId());
        rule.setMappingGraphId(testGraph.getId());
        rule.setPolicy("WARN");
        rule.setCategory(ObjectId.get().toHexString());
        rule.setIsDeleted(false);

        Map<String, Object> ruleConfig = new HashMap<>();
        ruleConfig.put("expression", expression);
        rule.setRuleConfig(ruleConfig);

        // Use record-level scope to avoid field lookup issues in tests
        rule.setScope(List.of("record"));
        rule.setScopeType("record");

        return rule;
    }

}
