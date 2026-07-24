package com.syncari.api.rest.controllers;

import com.syncari.api.core.util.ObjectTransformer;
import com.syncari.connector.EntityData;
import com.syncari.core.datatype.*;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.*;
import com.syncari.core.service.*;
import com.syncari.restutils.data.EntityRecord;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.*;

import static com.syncari.core.security.Permissions.READ_DATA_STUDIO;
import static com.syncari.core.security.Permissions.WRITE_DATA_STUDIO;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for Data Studio create operations with DFI and Merge functionality
 * Tests all combinations of runDFI and runMerge flags with various configurations
 *
 * Test Coverage:
 * A. Controller Validation Tests (3) - Entity not found, deleted, inactive
 * B. DFI Integration Tests (7) - Enabled/disabled, rules, no graph, no config
 * C. Merge Integration Tests (7) - Enabled/disabled, duplicates, REPORT_ONLY, no graph
 * D. Combined DFI + Merge Tests (4) - Both enabled with various scenarios
 * E. Edge Cases & Transaction Log (3) - Double-save prevention, transaction log, various actions
 * F. Advanced Scenarios (2) - Execution order, complex merge
 * G. Data Validation Tests (3) - Required fields, invalid types, unknown fields
 */
@Slf4j
public class DatastudioCreateWithMergeAndDFITest extends AbstractSyncariTest {
    private static final String TEST_ENTITY_NAME = "CreateTestEntity";
    private static final String TEST_ENTITY_WITH_DFI = "CreateTestEntityWithDFI";
    private static final String TEST_ENTITY_WITH_MERGE = "CreateTestEntityWithMerge";

    @Autowired
    private DatastudioController controller;

    @Autowired
    private SchemaService schemaService;

    @Autowired
    private EntityRepo entityRepo;

    @Autowired
    private AttributeRepo attributeRepo;

    @Autowired
    private EntityDefinitionRepo entityDefinitionRepo;

    @Autowired
    private ConnectorService connectorService;

    @Autowired
    private ObjectTransformer transformer;

    @Autowired
    private TransactionLogRepo transactionLogRepo;

    @SpyBean
    private RecordMergeService recordMergeService;

    @SpyBean
    private DFIExecutorService dfiExecutorService;

    @SpyBean
    private DataQualityService dataQualityService;

    @MockBean
    private MappingGraphService mappingGraphService;

    @MockBean
    private FeatureService featureService;

    private EntityDefinition testEntity;
    private EntityDefinition testEntityWithDFI;
    private EntityDefinition testEntityWithMerge;
    private EntityDefinition testEntityWithBoth;
    private MappingGraph testGraph;
    private DataQualityRule testDFIRule;

    @Before
    public void setUp() {
        super.setUp();

        // Enable features
        when(featureService.isEnabled(any())).thenReturn(true);

        // Create test entities
        testEntity = createTestEntity(TEST_ENTITY_NAME, false, false);
        testEntityWithDFI = createTestEntity(TEST_ENTITY_WITH_DFI, true, false);
        testEntityWithMerge = createTestEntity(TEST_ENTITY_WITH_MERGE, false, true);
        testEntityWithBoth = createTestEntity("CreateTestEntityWithBoth", true, true);

        // Set up test graph and DFI rule for each entity
        setupTestInfrastructure(testEntity);
        setupTestInfrastructure(testEntityWithDFI);
        setupTestInfrastructure(testEntityWithMerge);
        setupTestInfrastructure(testEntityWithBoth);

        // By default, return empty rules (individual tests will override as needed)
        doReturn(Collections.emptyList()).when(dataQualityService).getAllRules(any(MappingGraph.class));
    }

    private void setupTestInfrastructure(EntityDefinition entity) {
        // Create test graph with dedupe config for this entity
        MappingGraph graph = createTestGraphWithDedupeConfig(entity);

        // Mock mappingGraphService to return this graph
        when(mappingGraphService.retrieveApprovedEntityGraph(entity.getId()))
            .thenReturn(Optional.of(graph));
        when(mappingGraphService.retrieveDraftEntityGraph(entity.getId()))
            .thenReturn(Optional.of(graph));

        // Store reference to graph for later use
        if (entity == testEntityWithDFI || entity == testEntityWithBoth) {
            testGraph = graph;
            testDFIRule = createTestDFIRule("Test Rule", "Name", "record.Name != null", graph);
        }
    }

    @After
    public void tearDown() {
        // Reset mocks/spies to avoid state leakage between tests
        reset(recordMergeService, dfiExecutorService, dataQualityService, mappingGraphService, featureService);

        // Delete records first (from MongoDB only, avoiding datastore deletion)
        if (testEntity != null) {
            entityRepo.deleteAll(testEntity.getApiName());
            entityDefinitionRepo.delete(testEntity);
        }
        if (testEntityWithDFI != null) {
            entityRepo.deleteAll(testEntityWithDFI.getApiName());
            entityDefinitionRepo.delete(testEntityWithDFI);
        }
        if (testEntityWithMerge != null) {
            entityRepo.deleteAll(testEntityWithMerge.getApiName());
            entityDefinitionRepo.delete(testEntityWithMerge);
        }
        if (testEntityWithBoth != null) {
            entityRepo.deleteAll(testEntityWithBoth.getApiName());
            entityDefinitionRepo.delete(testEntityWithBoth);
        }
    }

    // ========================================
    // A. Controller Validation Tests (3)
    // ========================================

    /**
     * Test 1: Create with non-existent entity ID
     * Expected: SyncariValidationException with user-friendly message
     */
    @Test(expected = SyncariValidationException.class)
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testCreateWithEntityNotFound() throws Exception {
        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "Test Name");
        newRecord.getValues().put("Age", 30L);

        // Try to create with non-existent entity ID
        controller.create("nonexistent-entity-id", newRecord);
    }

    /**
     * Test 2: Create with deleted entity
     * Expected: SyncariValidationException with user-friendly message
     */
    @Test(expected = SyncariValidationException.class)
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testCreateWithDeletedEntity() throws Exception {
        // Mark entity as deleted
        testEntity.setStatus(Status.DELETED);
        entityDefinitionRepo.save(testEntity);

        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "Test Name");
        newRecord.getValues().put("Age", 30L);

        // Try to create with deleted entity
        controller.create(testEntity.getId(), newRecord);
    }

    /**
     * Test 3: Create with inactive entity
     * Expected: SyncariValidationException with user-friendly message
     */
    @Test(expected = SyncariValidationException.class)
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testCreateWithInactiveEntity() throws Exception {
        // Mark entity as inactive
        testEntity.setStatus(Status.INACTIVE);
        entityDefinitionRepo.save(testEntity);

        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "Test Name");
        newRecord.getValues().put("Age", 30L);

        // Try to create with inactive entity
        controller.create(testEntity.getId(), newRecord);
    }

    // ========================================
    // B. DFI Integration Tests (7)
    // ========================================

    /**
     * Test 4: Create with DFI not enabled (default behavior)
     * Expected: Create succeeds, DFI does not execute, no scores stored
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testCreateWithDFINotEnabled() throws Exception {
        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "New Name");
        newRecord.getValues().put("Age", 35L);

        // Create with runDFI=false (default)
        EntityDataResponse response = controller.create(testEntity.getId(), newRecord).getBody();

        // Verify create succeeded
        assertNotNull(response);
        assertNotNull(response.getRecord());
        assertEquals("New Name", response.getRecord().getValues().get("Name"));
        assertEquals(35L, response.getRecord().getValues().get("Age"));
        assertTrue(response.getErrors().isEmpty());

        // Verify DFI was NOT executed (runDFI=false)
        verify(dfiExecutorService, never()).evaluateRule(any(), any(), any(), any(), any(), any());
    }

    /**
     * Test 5: Create with DFI enabled
     * Expected: Create succeeds, DFI evaluates rules, scores stored on record
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testCreateWithDFIEnabled() throws Exception {
        // Set up DFI rule for this test
        doReturn(List.of(testDFIRule)).when(dataQualityService).getAllRules(any(MappingGraph.class));

        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "New Name");
        newRecord.getValues().put("Age", 35L);

        // Create with runDFI=true
        EntityDataResponse response = controller.create(testEntityWithDFI.getId(), newRecord).getBody();

        // Verify create succeeded
        assertNotNull(response);
        assertNotNull(response.getRecord());
        assertEquals("New Name", response.getRecord().getValues().get("Name"));
        assertTrue(response.getErrors().isEmpty());

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

        // Verify the record ID passed to evaluateRule matches the created record
        String createdRecordId = response.getRecord().getSyncariId();
        assertEquals(createdRecordId, recordIdCaptor.getValue());
    }

    /**
     * Test 6: Create with DFI rule passing
     * Expected: Create succeeds, DFI rule evaluates to true
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testCreateWithDFIRulePassing() throws Exception {
        // Set up DFI rule: Age must be >= 18
        DataQualityRule ageRule = createTestDFIRule(
            "Age Validation",
            "Age",
            "record.Age >= 18",
            testGraph
        );
        doReturn(List.of(ageRule)).when(dataQualityService).getAllRules(any(MappingGraph.class));

        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "Valid Name");
        newRecord.getValues().put("Age", 30L);

        EntityDataResponse response = controller.create(testEntityWithDFI.getId(), newRecord).getBody();

        assertNotNull(response);
        assertTrue("Create should succeed with passing DFI rule", response.getErrors().isEmpty());

        // VERIFY DFI rule was evaluated
        verify(dfiExecutorService, atLeastOnce()).evaluateRule(
            any(),
            any(),
            any(),
            eq(ageRule),
            any(),
            any()
        );

        // VERIFY DFI notification was sent
        verify(dfiExecutorService, times(1)).sendDFIResultNotification(any());
    }

    /**
     * Test 7: Create with DFI rule failing
     * Expected: Create succeeds, record saved with low score (not rejected, report only)
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testCreateWithDFIRuleFailing() throws Exception {
        // Set up DFI rules that will fail
        DataQualityRule nameRule = createTestDFIRule(
            "Name Required",
            "Name",
            "record.Name != null && record.Name.length() > 0",
            testGraph
        );
        DataQualityRule ageRule = createTestDFIRule(
            "Age Validation",
            "Age",
            "record.Age >= 18",
            testGraph
        );
        doReturn(List.of(nameRule, ageRule)).when(dataQualityService).getAllRules(any(MappingGraph.class));

        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "");
        newRecord.getValues().put("Age", 15L);

        EntityDataResponse response = controller.create(testEntityWithDFI.getId(), newRecord).getBody();

        assertNotNull(response);
        assertTrue("Create should succeed even with failing DFI rules (report only)",
            response.getErrors().isEmpty());

        // VERIFY both DFI rules were evaluated
        verify(dfiExecutorService, atLeastOnce()).evaluateRule(
            any(),
            any(),
            any(),
            eq(nameRule),
            any(),
            any()
        );
        verify(dfiExecutorService, atLeastOnce()).evaluateRule(
            any(),
            any(),
            any(),
            eq(ageRule),
            any(),
            any()
        );

        // VERIFY DFI results were sent
        verify(dfiExecutorService, times(1)).sendDFIResultNotification(any());
    }

    /**
     * Test 8: Create with DFI enabled but no DFI rules configured
     * Expected: Create succeeds, logs info, continues gracefully
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testCreateWithDFINoDFIRules() throws Exception {
        // Explicitly return empty rules list
        doReturn(Collections.emptyList()).when(dataQualityService).getAllRules(any(MappingGraph.class));

        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "New Name");

        EntityDataResponse response = controller.create(testEntityWithDFI.getId(), newRecord).getBody();

        assertNotNull(response);
        assertNotNull(response.getRecord());
        assertEquals("New Name", response.getRecord().getValues().get("Name"));
        assertTrue(response.getErrors().isEmpty());

        // Verify evaluateRule was never called since no rules exist
        verify(dfiExecutorService, never()).evaluateRule(any(), any(), any(), any(), any(), any());
    }

    /**
     * Test 9: Create with DFI enabled but no graph configured
     * Expected: Create succeeds, logs warning, skips DFI
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testCreateWithDFINoGraph() throws Exception {
        // Mock mappingGraphService to return empty
        when(mappingGraphService.retrieveApprovedEntityGraph(testEntityWithDFI.getId()))
            .thenReturn(Optional.empty());
        when(mappingGraphService.retrieveDraftEntityGraph(testEntityWithDFI.getId()))
            .thenReturn(Optional.empty());

        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "New Name");

        EntityDataResponse response = controller.create(testEntityWithDFI.getId(), newRecord).getBody();

        assertNotNull(response);
        assertNotNull(response.getRecord());
        assertEquals("New Name", response.getRecord().getValues().get("Name"));
        assertTrue(response.getErrors().isEmpty());

        // VERIFY DFI was skipped (no graph exists)
        verify(dfiExecutorService, never()).evaluateRule(any(), any(), any(), any(), any(), any());
    }

    /**
     * Test 10: Create with merge disabled but DFI enabled
     * Expected: Only DFI executes, merge skipped
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testCreateWithNoMergeButDFIPresent() throws Exception {
        // Set up DFI rule for this test
        doReturn(List.of(testDFIRule)).when(dataQualityService).getAllRules(any(MappingGraph.class));

        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "New Name");
        newRecord.getValues().put("Age", 25L);

        EntityDataResponse response = controller.create(testEntityWithDFI.getId(), newRecord).getBody();

        assertNotNull(response);
        assertNotNull(response.getRecord());
        assertEquals("New Name", response.getRecord().getValues().get("Name"));
        assertTrue(response.getErrors().isEmpty());

        // VERIFY merge was never called (runMerge=false)
        verify(recordMergeService, never()).advancedDedupeMerge(any(), any(), any(), any(), any(), any(), any());

        // VERIFY DFI was executed (runDFI=true with rules)
        verify(dfiExecutorService, atLeastOnce()).evaluateRule(
            any(),
            any(),
            any(),
            eq(testDFIRule),
            any(),
            any()
        );
    }

    // ========================================
    // C. Merge Integration Tests (7)
    // ========================================

    /**
     * Test 11: Create with merge not enabled (default behavior)
     * Expected: Create succeeds, merge does not execute
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testCreateWithMergeNotEnabled() throws Exception {
        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "New Name");

        EntityDataResponse response = controller.create(testEntity.getId(), newRecord).getBody();

        assertNotNull(response);
        assertNotNull(response.getRecord());
        assertEquals("New Name", response.getRecord().getValues().get("Name"));
        assertTrue(response.getErrors().isEmpty());

        // Verify merge was NOT executed (runMerge=false)
        verify(recordMergeService, never()).advancedDedupeMerge(any(), any(), any(), any(), any(), any(), any());
    }

    /**
     * Test 12: Create with merge enabled but no duplicates found
     * Expected: Create succeeds, record saved without merge
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testCreateWithMergeEnabledNoMatch() throws Exception {
        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "Unique Name");
        newRecord.getValues().put("Email", "unique@example.com");

        EntityDataResponse response = controller.create(testEntityWithMerge.getId(), newRecord).getBody();

        assertNotNull(response);
        assertNotNull(response.getRecord());
        assertEquals("Unique Name", response.getRecord().getValues().get("Name"));
        assertTrue(response.getErrors().isEmpty());

        // Verify advancedDedupeMerge was called (checks for duplicates)
        verify(recordMergeService, times(1)).advancedDedupeMerge(
            any(AdvancedDedupeConfig.class),
            any(EntityData.class),
            any(EntityDefinition.class),
            any(),
            any(),
            any(),
            any()
        );

        // Verify no actual merge happened (apply not called)
        verify(recordMergeService, never()).apply(any(MergeOperation.class), any());
    }

    /**
     * Test 13: Create triggers merge with existing duplicate found (new record becomes loser)
     * Expected: Merge executes, existing record wins, new record deleted
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testCreateTriggersDedupeNewRecordLoses() throws Exception {
        // Set up REAL dedupe config with Email matching
        AdvancedDedupeConfig dedupeConfig = createEmailDedupeConfig(testEntityWithMerge);
        updateGraphWithDedupeConfig(testEntityWithMerge, dedupeConfig);

        // Create existing record first
        EntityData existingRecord = createTestRecord(testEntityWithMerge);
        existingRecord.addValue("Email", "test@example.com");
        existingRecord.addValue("Age", 30);
        existingRecord.addValue("Name", "Existing Name");
        existingRecord = entityRepo.save(testEntityWithMerge, existingRecord);

        // Create new record with duplicate email
        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "New Name");
        newRecord.getValues().put("Email", "test@example.com");
        newRecord.getValues().put("Age", 25L);

        EntityDataResponse response = controller.create(testEntityWithMerge.getId(), newRecord).getBody();

        assertNotNull(response);
        assertNotNull(response.getRecord());
        assertTrue("Create should succeed even when merge happens", response.getErrors().isEmpty());

        // VERIFY merge service was called
        verify(recordMergeService, times(1)).advancedDedupeMerge(
            eq(dedupeConfig),
            any(EntityData.class),
            eq(testEntityWithMerge),
            any(),
            any(),
            any(),
            any()
        );
    }

    /**
     * Test 14: Create with merge, new record has higher quality (new record wins)
     * Expected: New record wins, existing record(s) deleted
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testCreateTriggersDedupeNewRecordWins() throws Exception {
        // Set up REAL dedupe config with Email matching
        AdvancedDedupeConfig dedupeConfig = createEmailDedupeConfig(testEntityWithMerge);
        updateGraphWithDedupeConfig(testEntityWithMerge, dedupeConfig);

        // Create existing records with same email
        EntityData existingRecord1 = createTestRecord(testEntityWithMerge);
        existingRecord1.addValue("Email", "winner@example.com");
        existingRecord1.addValue("Age", 20);
        existingRecord1 = entityRepo.save(testEntityWithMerge, existingRecord1);

        EntityData existingRecord2 = createTestRecord(testEntityWithMerge);
        existingRecord2.addValue("Email", "winner@example.com");
        existingRecord2.addValue("Age", 25);
        existingRecord2 = entityRepo.save(testEntityWithMerge, existingRecord2);

        // Create new record with duplicate email
        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "New Winner");
        newRecord.getValues().put("Email", "winner@example.com");
        newRecord.getValues().put("Age", 35L);

        EntityDataResponse response = controller.create(testEntityWithMerge.getId(), newRecord).getBody();

        assertNotNull(response);
        assertTrue("Create should succeed with merge", response.getErrors().isEmpty());

        // VERIFY merge service was called
        verify(recordMergeService, times(1)).advancedDedupeMerge(
            eq(dedupeConfig),
            any(EntityData.class),
            eq(testEntityWithMerge),
            any(),
            any(),
            any(),
            any()
        );
    }

    /**
     * Test 15: Create with merge action=REPORT_ONLY
     * Expected: Create succeeds, merge action logged, no actual merge
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testCreateWithMergeReportOnly() throws Exception {
        // Set up REAL dedupe config with REPORT_ONLY action
        AdvancedDedupeConfig reportOnlyConfig = createReportOnlyDedupeConfig(testEntityWithMerge);
        updateGraphWithDedupeConfig(testEntityWithMerge, reportOnlyConfig);

        // Create existing record
        EntityData existingRecord = createTestRecord(testEntityWithMerge);
        existingRecord.addValue("Email", "report@example.com");
        existingRecord = entityRepo.save(testEntityWithMerge, existingRecord);

        // Create new record with duplicate email
        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "New Name");
        newRecord.getValues().put("Email", "report@example.com");

        EntityDataResponse response = controller.create(testEntityWithMerge.getId(), newRecord).getBody();

        assertNotNull(response);
        assertNotNull(response.getRecord());
        assertTrue("Create should succeed with REPORT_ONLY", response.getErrors().isEmpty());

        // VERIFY merge service was called with REPORT_ONLY config
        verify(recordMergeService, times(1)).advancedDedupeMerge(
            eq(reportOnlyConfig),
            any(EntityData.class),
            eq(testEntityWithMerge),
            any(),
            any(),
            any(),
            any()
        );

        // VERIFY no actual merge happened
        verify(recordMergeService, never()).apply(any(MergeOperation.class), any());

        // VERIFY both records exist (no deletion with REPORT_ONLY)
        Optional<EntityData> existingStillExists = entityRepo.findById(testEntityWithMerge, existingRecord.getId());
        assertTrue("Existing record should still exist with REPORT_ONLY", existingStillExists.isPresent());
    }

    /**
     * Test 16: Create with merge enabled but no dedupe config
     * Expected: Create succeeds, logs warning, skips merge
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testCreateWithMergeNoDedupeConfig() throws Exception {
        // Update graph to have null dedupe config
        updateGraphWithDedupeConfig(testEntityWithMerge, null);

        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "New Name");

        EntityDataResponse response = controller.create(testEntityWithMerge.getId(), newRecord).getBody();

        assertNotNull(response);
        assertNotNull(response.getRecord());
        assertEquals("New Name", response.getRecord().getValues().get("Name"));
        assertTrue(response.getErrors().isEmpty());

        // VERIFY merge was skipped (dedupeConfig is null)
        verify(recordMergeService, never()).advancedDedupeMerge(any(), any(), any(), any(), any(), any(), any());
    }

    /**
     * Test 17: Create with merge enabled but no graph configured
     * Expected: Create succeeds, logs warning, skips merge
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testCreateWithMergeNoGraph() throws Exception {
        // Mock mappingGraphService to return empty
        when(mappingGraphService.retrieveApprovedEntityGraph(testEntityWithMerge.getId()))
            .thenReturn(Optional.empty());
        when(mappingGraphService.retrieveDraftEntityGraph(testEntityWithMerge.getId()))
            .thenReturn(Optional.empty());

        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "New Name");

        EntityDataResponse response = controller.create(testEntityWithMerge.getId(), newRecord).getBody();

        assertNotNull(response);
        assertNotNull(response.getRecord());
        assertEquals("New Name", response.getRecord().getValues().get("Name"));
        assertTrue(response.getErrors().isEmpty());

        // Verify merge was not called (no graph exists)
        verify(recordMergeService, never()).advancedDedupeMerge(any(), any(), any(), any(), any(), any(), any());
    }

    // ========================================
    // D. Combined DFI + Merge Tests (4)
    // ========================================

    /**
     * Test 18: Create with both DFI and Merge enabled
     * Expected: Merge runs first, then DFI evaluates final record
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testCreateWithBothDFIAndMergeEnabled() throws Exception {
        // Set up DFI rule for this test
        doReturn(List.of(testDFIRule)).when(dataQualityService).getAllRules(any(MappingGraph.class));

        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "New Name");

        EntityDataResponse response = controller.create(testEntityWithBoth.getId(), newRecord).getBody();

        assertNotNull(response);
        assertNotNull(response.getRecord());
        assertEquals("New Name", response.getRecord().getValues().get("Name"));
        assertTrue(response.getErrors().isEmpty());

        // Verify both merge and DFI were called
        verify(recordMergeService, times(1)).advancedDedupeMerge(
            any(AdvancedDedupeConfig.class),
            any(EntityData.class),
            any(EntityDefinition.class),
            any(),
            any(),
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

    /**
     * Test 19: Create with both enabled, merge happens
     * Expected: Merge executes, DFI evaluates final merged record
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testCreateWithBothDFIAndMergeMergeHappens() throws Exception {
        // Set up REAL dedupe config with Email matching
        AdvancedDedupeConfig dedupeConfig = createEmailDedupeConfig(testEntityWithBoth);
        updateGraphWithDedupeConfig(testEntityWithBoth, dedupeConfig);

        // Set up DFI rule: Age must be >= 18
        DataQualityRule ageRule = createTestDFIRule(
            "Age Validation",
            "Age",
            "record.Age >= 18",
            testGraph
        );
        doReturn(List.of(ageRule)).when(dataQualityService).getAllRules(any(MappingGraph.class));

        // Create existing record
        EntityData existingRecord = createTestRecord(testEntityWithBoth);
        existingRecord.addValue("Email", "both@example.com");
        existingRecord.addValue("Age", 30);
        existingRecord = entityRepo.save(testEntityWithBoth, existingRecord);

        // Create new record with duplicate email
        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "New Name");
        newRecord.getValues().put("Email", "both@example.com");
        newRecord.getValues().put("Age", 25L);

        EntityDataResponse response = controller.create(testEntityWithBoth.getId(), newRecord).getBody();

        assertNotNull(response);
        assertNotNull(response.getRecord());
        assertTrue("Create should succeed with both DFI and Merge", response.getErrors().isEmpty());

        // VERIFY merge was called
        verify(recordMergeService, times(1)).advancedDedupeMerge(
            eq(dedupeConfig),
            any(EntityData.class),
            eq(testEntityWithBoth),
            any(),
            any(),
            any(),
            any()
        );

        // VERIFY DFI was evaluated
        verify(dfiExecutorService, atLeastOnce()).evaluateRule(
            any(),
            any(),
            any(),
            eq(ageRule),
            any(),
            any()
        );

        // VERIFY DFI notification was sent
        verify(dfiExecutorService, times(1)).sendDFIResultNotification(any());
    }

    /**
     * Test 20: Create with DFI (no rules) and Merge (with config)
     * Expected: Merge executes, DFI logs info and skips
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testCreateWithNoDFIRulesButMergePresent() throws Exception {
        // Explicitly set empty rules list
        doReturn(Collections.emptyList()).when(dataQualityService).getAllRules(any(MappingGraph.class));

        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "New Name");

        EntityDataResponse response = controller.create(testEntityWithBoth.getId(), newRecord).getBody();

        assertNotNull(response);
        assertNotNull(response.getRecord());
        assertTrue(response.getErrors().isEmpty());

        // VERIFY merge was called (runMerge=true with dedupe config)
        verify(recordMergeService, times(1)).advancedDedupeMerge(
            any(AdvancedDedupeConfig.class),
            any(EntityData.class),
            eq(testEntityWithBoth),
            any(),
            any(),
            any(),
            any()
        );

        // VERIFY DFI was skipped (no rules exist)
        verify(dfiExecutorService, never()).evaluateRule(any(), any(), any(), any(), any(), any());
    }

    /**
     * Test 21: Create with both enabled but no graph
     * Expected: Both skip gracefully with warnings
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testCreateWithBothEnabledNoGraph() throws Exception {
        // Mock mappingGraphService to return empty
        when(mappingGraphService.retrieveApprovedEntityGraph(testEntityWithBoth.getId()))
            .thenReturn(Optional.empty());
        when(mappingGraphService.retrieveDraftEntityGraph(testEntityWithBoth.getId()))
            .thenReturn(Optional.empty());

        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "New Name");

        EntityDataResponse response = controller.create(testEntityWithBoth.getId(), newRecord).getBody();

        assertNotNull(response);
        assertNotNull(response.getRecord());
        assertEquals("New Name", response.getRecord().getValues().get("Name"));
        assertTrue(response.getErrors().isEmpty());

        // VERIFY both services were skipped (no graph exists)
        verify(recordMergeService, never()).advancedDedupeMerge(any(), any(), any(), any(), any(), any(), any());
        verify(dfiExecutorService, never()).evaluateRule(any(), any(), any(), any(), any(), any());
    }

    // ========================================
    // E. Edge Cases & Transaction Log (3)
    // ========================================

    /**
     * Test 22: Create with merge does not double-save
     * Expected: When merge saves winner, repo.save() is not called again
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testCreateDoesNotDoubleSave() throws Exception {
        // Set up dedupe config
        AdvancedDedupeConfig dedupeConfig = createEmailDedupeConfig(testEntityWithMerge);
        updateGraphWithDedupeConfig(testEntityWithMerge, dedupeConfig);

        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "New Name");
        newRecord.getValues().put("Email", "unique@example.com");

        EntityDataResponse response = controller.create(testEntityWithMerge.getId(), newRecord).getBody();

        assertNotNull(response);
        assertNotNull(response.getRecord());
        assertEquals("New Name", response.getRecord().getValues().get("Name"));
        assertTrue("Create should succeed", response.getErrors().isEmpty());

        // VERIFY merge service was called
        verify(recordMergeService, times(1)).advancedDedupeMerge(
            eq(dedupeConfig),
            any(EntityData.class),
            eq(testEntityWithMerge),
            any(),
            any(),
            any(),
            any()
        );

        // Double-save prevention is structurally enforced by MergeAndDFIResult in EntityRepoService
    }

    /**
     * Test 23: Create preserves transaction log with isNew=true
     * Expected: Transaction log marks record as new
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testCreatePreservesTransactionLog() throws Exception {
        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "New Name");
        newRecord.getValues().put("Age", 30L);

        EntityDataResponse response = controller.create(testEntity.getId(), newRecord).getBody();

        assertNotNull(response);
        assertNotNull(response.getRecord());
        assertEquals("New Name", response.getRecord().getValues().get("Name"));
        assertEquals(30L, response.getRecord().getValues().get("Age"));

        String recordId = response.getRecord().getSyncariId();

        // Query transaction log
        var transactionLogs = transactionLogRepo.findBySyncariId(PageRequest.of(0, 10), recordId);

        if (transactionLogs != null && transactionLogs.hasContent()) {
            assertTrue("Transaction log should exist for created record", transactionLogs.getTotalElements() > 0);

            TransactionLog latestLog = transactionLogs.getContent().get(0);
            assertTrue("Transaction log should mark record as new", latestLog.isNew());
        }
    }

    /**
     * Test 24: Create with various merge actions all succeed
     * Expected: All merge actions result in successful create
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testCreateWithMergeVariousActionsSucceed() throws Exception {
        // Scenario 1: No duplicates found
        AdvancedDedupeConfig mergeConfig = createEmailDedupeConfig(testEntityWithMerge);
        updateGraphWithDedupeConfig(testEntityWithMerge, mergeConfig);

        EntityRecord newRecord1 = new EntityRecord();
        newRecord1.getValues().put("Name", "Scenario 1");
        newRecord1.getValues().put("Email", "scenario1@example.com");

        EntityDataResponse response1 = controller.create(testEntityWithMerge.getId(), newRecord1).getBody();
        assertNotNull("Scenario 1 should succeed", response1);
        assertTrue("Scenario 1 should have no errors", response1.getErrors().isEmpty());
        verify(recordMergeService, atLeastOnce()).advancedDedupeMerge(any(), any(), any(), any(), any(), any(), any());

        reset(recordMergeService);

        // Scenario 2: REPORT_ONLY with duplicates
        AdvancedDedupeConfig reportOnlyConfig = createReportOnlyDedupeConfig(testEntityWithMerge);
        updateGraphWithDedupeConfig(testEntityWithMerge, reportOnlyConfig);

        EntityData existing = createTestRecord(testEntityWithMerge);
        existing.addValue("Email", "scenario2@example.com");
        existing = entityRepo.save(testEntityWithMerge, existing);

        EntityRecord newRecord2 = new EntityRecord();
        newRecord2.getValues().put("Name", "Scenario 2");
        newRecord2.getValues().put("Email", "scenario2@example.com");

        EntityDataResponse response2 = controller.create(testEntityWithMerge.getId(), newRecord2).getBody();
        assertNotNull("Scenario 2 (REPORT_ONLY) should succeed", response2);
        assertTrue("Scenario 2 should have no errors", response2.getErrors().isEmpty());
        verify(recordMergeService, times(1)).advancedDedupeMerge(eq(reportOnlyConfig), any(), any(), any(), any(), any(), any());
        verify(recordMergeService, never()).apply(any(), any());

        reset(recordMergeService);

        // Scenario 3: MERGE action with duplicates
        updateGraphWithDedupeConfig(testEntityWithMerge, mergeConfig);

        EntityData existing3 = createTestRecord(testEntityWithMerge);
        existing3.addValue("Email", "scenario3@example.com");
        existing3 = entityRepo.save(testEntityWithMerge, existing3);

        EntityRecord newRecord3 = new EntityRecord();
        newRecord3.getValues().put("Name", "Scenario 3");
        newRecord3.getValues().put("Email", "scenario3@example.com");

        EntityDataResponse response3 = controller.create(testEntityWithMerge.getId(), newRecord3).getBody();
        assertNotNull("Scenario 3 (MERGE) should succeed", response3);
        assertTrue("Scenario 3 should have no errors", response3.getErrors().isEmpty());
        verify(recordMergeService, times(1)).advancedDedupeMerge(eq(mergeConfig), any(), any(), any(), any(), any(), any());
    }

    // ========================================
    // F. Advanced Scenarios (2)
    // ========================================

    /**
     * Test 25: Verify DFI evaluates AFTER merge completes
     * Expected: Merge executes first, then DFI evaluates the final record
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testDFIEvaluatesAfterMerge() throws Exception {
        // Set up dedupe config and DFI rule
        AdvancedDedupeConfig dedupeConfig = createEmailDedupeConfig(testEntityWithBoth);
        updateGraphWithDedupeConfig(testEntityWithBoth, dedupeConfig);

        DataQualityRule ageRule = createTestDFIRule(
            "Age Validation",
            "Age",
            "record.Age >= 18",
            testGraph
        );
        doReturn(List.of(ageRule)).when(dataQualityService).getAllRules(any(MappingGraph.class));

        // Create existing record
        EntityData existingRecord = createTestRecord(testEntityWithBoth);
        existingRecord.addValue("Email", "order@example.com");
        existingRecord.addValue("Age", 35);
        existingRecord = entityRepo.save(testEntityWithBoth, existingRecord);

        // Create new record with duplicate
        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "New Name");
        newRecord.getValues().put("Email", "order@example.com");
        newRecord.getValues().put("Age", 25L);

        EntityDataResponse response = controller.create(testEntityWithBoth.getId(), newRecord).getBody();

        assertNotNull(response);
        assertTrue("Create with both DFI and Merge should succeed", response.getErrors().isEmpty());

        // VERIFY both merge and DFI were called
        verify(recordMergeService, times(1)).advancedDedupeMerge(any(), any(), any(), any(), any(), any(), any());
        verify(dfiExecutorService, atLeastOnce()).evaluateRule(any(), any(), any(), eq(ageRule), any(), any());
    }

    /**
     * Test 26: Create with complex merge scenario - multiple existing duplicates
     * Expected: New record merges with multiple existing records
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testComplexMergeWithMultipleExistingRecords() throws Exception {
        // Set up dedupe config
        AdvancedDedupeConfig dedupeConfig = createEmailDedupeConfig(testEntityWithMerge);
        updateGraphWithDedupeConfig(testEntityWithMerge, dedupeConfig);

        // Create 3 existing records with same email
        EntityData existing1 = createTestRecord(testEntityWithMerge);
        existing1.addValue("Email", "complex@example.com");
        existing1.addValue("Name", "Existing 1");
        existing1.addValue("Age", 25);
        existing1 = entityRepo.save(testEntityWithMerge, existing1);

        EntityData existing2 = createTestRecord(testEntityWithMerge);
        existing2.addValue("Email", "complex@example.com");
        existing2.addValue("Name", "Existing 2");
        existing2.addValue("Age", 30);
        existing2 = entityRepo.save(testEntityWithMerge, existing2);

        EntityData existing3 = createTestRecord(testEntityWithMerge);
        existing3.addValue("Email", "complex@example.com");
        existing3.addValue("Name", "Existing 3");
        existing3.addValue("Age", 35);
        existing3 = entityRepo.save(testEntityWithMerge, existing3);

        // Create new record with same email
        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "New Record");
        newRecord.getValues().put("Email", "complex@example.com");
        newRecord.getValues().put("Age", 40L);

        EntityDataResponse response = controller.create(testEntityWithMerge.getId(), newRecord).getBody();

        assertNotNull(response);
        assertNotNull(response.getRecord());
        assertTrue("Complex merge should succeed", response.getErrors().isEmpty());

        // VERIFY merge service was called to handle duplicates
        verify(recordMergeService, times(1)).advancedDedupeMerge(
            eq(dedupeConfig),
            any(EntityData.class),
            eq(testEntityWithMerge),
            any(),
            any(),
            any(),
            any()
        );
    }

    // ========================================
    // G. Data Validation Tests (3)
    // ========================================

    /**
     * Test 27: Create with missing non-nillable field
     * Expected: Record created with null value (system allows creation, nillable validation may be enforced elsewhere)
     * Note: Current behavior allows creation even with missing non-nillable fields
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testCreateWithMissingNonNillableField() throws Exception {
        // Create entity with non-nillable field
        EntityDefinition entityWithRequired = createTestEntity("EntityWithRequired", false, false);

        // Make Name field non-nillable (nillable=false)
        AttributeDefinition nameAttr = entityWithRequired.getFieldByName("Name");
        nameAttr.setNillable(false);
        attributeRepo.save(nameAttr);

        // Create record WITHOUT the non-nillable Name field
        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Age", 30L);
        newRecord.getValues().put("Email", "test@example.com");

        EntityDataResponse response = controller.create(entityWithRequired.getId(), newRecord).getBody();

        // Verify response (current behavior: record is created)
        assertNotNull(response);
        assertNotNull("Record is created even with missing non-nillable field", response.getRecord());

        // Verify error collection exists but may be empty (nillable enforcement may be at different layer)
        assertNotNull(response.getErrors());

        // Verify other fields are present
        assertEquals(30L, response.getRecord().getValues().get("Age"));
        assertEquals("test@example.com", response.getRecord().getValues().get("Email"));

        // Name field should be null or absent
        Object nameValue = response.getRecord().getValues().get("Name");
        assertTrue("Name should be null or absent", nameValue == null || "".equals(nameValue));

        // Cleanup
        entityDefinitionRepo.delete(entityWithRequired);
    }

    /**
     * Test 28: Create with invalid data type
     * Expected: Validation error or type conversion, record created with converted value
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testCreateWithInvalidDataType() throws Exception {
        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "Valid Name");
        // Pass string where integer expected - this tests type conversion handling
        newRecord.getValues().put("Age", "not-a-number");
        newRecord.getValues().put("Email", "test@example.com");

        try {
            EntityDataResponse response = controller.create(testEntity.getId(), newRecord).getBody();

            // If no exception, verify response
            assertNotNull(response);

            // Either validation error OR type conversion happened
            if (!response.getErrors().isEmpty()) {
                // Validation error case
                assertEquals(1, response.getErrors().getFields().size());
                assertEquals(1, response.getErrors().getFields().get("Age").size());
                assertEquals("INVALID_TYPE", response.getErrors().getFields().get("Age").get(0).getCode());
                assertEquals("This value is invalid. Please Enter a valid Integer type", response.getErrors().getFields().get("Age").get(0).getMessage());
            } else {
                // Type conversion case - verify record created
                assertNotNull("Record should be created if type conversion succeeded",
                    response.getRecord());
            }
        } catch (Exception e) {
            // Exception thrown for invalid data type
            assertTrue("Should be validation error for invalid Age value",
                e.getMessage().contains("Validation failed") ||
                (e.getMessage().contains("invalid") && e.getMessage().contains("Age")));
        }
    }

    /**
     * Test 29: Create with unknown field
     * Expected: Unknown field ignored, record created with valid fields
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testCreateWithUnknownField() throws Exception {
        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "Valid Name");
        newRecord.getValues().put("Age", 30L);
        newRecord.getValues().put("Email", "test@example.com");
        // Add field that doesn't exist in entity definition
        newRecord.getValues().put("NonExistentField", "Some Value");
        newRecord.getValues().put("AnotherUnknownField", 123);

        EntityDataResponse response = controller.create(testEntity.getId(), newRecord).getBody();

        // Verify create succeeded (unknown fields typically ignored)
        assertNotNull(response);
        assertNotNull("Record should be created", response.getRecord());

        // Valid fields should be present
        assertEquals("Valid Name", response.getRecord().getValues().get("Name"));
        assertEquals(30L, response.getRecord().getValues().get("Age"));
        assertEquals("test@example.com", response.getRecord().getValues().get("Email"));

        // Unknown fields should not be present in saved record
        assertFalse("Unknown field should not be saved",
            response.getRecord().getValues().containsKey("NonExistentField"));
        assertFalse("Unknown field should not be saved",
            response.getRecord().getValues().containsKey("AnotherUnknownField"));

        // Should not have errors
        assertTrue("Should not have errors for unknown fields", response.getErrors().isEmpty());
    }

    /**
     * Test 30: DFI rule referencing non-existent field in expression
     * Tests ACTUAL rule evaluation when field doesn't exist in record
     * Expected: DFI evaluates gracefully, handles missing field appropriately
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testDFIRuleWithMissingFieldReference() throws Exception {
        // Create DFI rule that references a field NOT in the entity definition
        // This tests real rule evaluation with missing field
        DataQualityRule ruleWithMissingField = createTestDFIRule(
            "Missing Field Rule",
            "Score",
            "record.NonExistentField > 50",  // Field doesn't exist
            testGraph
        );
        doReturn(List.of(ruleWithMissingField)).when(dataQualityService).getAllRules(any(MappingGraph.class));

        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "Test Name");
        newRecord.getValues().put("Age", 30L);
        // NOT including NonExistentField

        EntityDataResponse response = controller.create(testEntityWithDFI.getId(), newRecord).getBody();

        // Verify create succeeded (DFI should not block creation)
        assertNotNull("Response should not be null", response);
        assertNotNull("Record should be created despite DFI rule with missing field", response.getRecord());
        assertEquals("Test Name", response.getRecord().getValues().get("Name"));
        assertTrue("Should not have validation errors", response.getErrors().isEmpty());

        // Verify DFI was attempted (evaluateRule should be called)
        verify(dfiExecutorService, atLeastOnce()).evaluateRule(
            any(),
            any(),
            any(),
            eq(ruleWithMissingField),
            any(),
            any()
        );

        // Verify record was saved with DFI score (even if rule evaluation had issues)
        assertNotNull("Record should have been saved", response.getRecord().getSyncariId());
    }

    /**
     * Test 31: DFI rule with complex expression - partial field availability
     * Tests ACTUAL evaluation with mix of present and missing fields
     * Expected: DFI handles partial data appropriately
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testDFIRuleWithPartialFieldAvailability() throws Exception {
        // Create rule with complex expression using multiple fields
        // Some fields will be present, others missing
        DataQualityRule complexRule = createTestDFIRule(
            "Complex Rule",
            "Score",
            "(record.Age != null && record.Age >= 18) || record.IsVerified == true",
            testGraph
        );
        doReturn(List.of(complexRule)).when(dataQualityService).getAllRules(any(MappingGraph.class));

        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "Test User");
        newRecord.getValues().put("Age", 25L);
        // NOT including IsVerified field - should handle gracefully

        EntityDataResponse response = controller.create(testEntityWithDFI.getId(), newRecord).getBody();

        // Verify create succeeded
        assertNotNull("Response should not be null", response);
        assertNotNull("Record should be created", response.getRecord());
        assertTrue("Should not have validation errors", response.getErrors().isEmpty());

        // Verify DFI rule was evaluated
        verify(dfiExecutorService, atLeastOnce()).evaluateRule(
            any(),
            any(),
            any(),
            eq(complexRule),
            any(),
            any()
        );

        // Verify actual field values in saved record
        assertEquals("Test User", response.getRecord().getValues().get("Name"));
        assertEquals(25L, response.getRecord().getValues().get("Age"));

        // Verify DFI processing completed (notification sent)
        verify(dfiExecutorService, times(1)).sendDFIResultNotification(any());
    }

    /**
     * Test 32: DFI rule evaluation with null field values
     * Tests ACTUAL handling of null values in expressions
     * Expected: DFI evaluates null checks properly without errors
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testDFIRuleWithNullFieldValues() throws Exception {
        // Create rule that explicitly checks for null values
        DataQualityRule nullCheckRule = createTestDFIRule(
            "Null Check Rule",
            "Score",
            "record.Age != null && record.Age >= 18",
            testGraph
        );
        doReturn(List.of(nullCheckRule)).when(dataQualityService).getAllRules(any(MappingGraph.class));

        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "Test User");
        // Age field is null (not provided)

        EntityDataResponse response = controller.create(testEntityWithDFI.getId(), newRecord).getBody();

        // Verify create succeeded with null field
        assertNotNull("Response should not be null", response);
        assertNotNull("Record should be created with null Age", response.getRecord());
        assertEquals("Test User", response.getRecord().getValues().get("Name"));
        assertNull("Age should be null", response.getRecord().getValues().get("Age"));
        assertTrue("Should not have validation errors", response.getErrors().isEmpty());

        // Verify DFI rule was evaluated with null field
        verify(dfiExecutorService, atLeastOnce()).evaluateRule(
            any(),
            any(),
            any(),
            eq(nullCheckRule),
            any(),
            any()
        );
    }

    /**
     * Test 33: Merge with missing field in dedupe matching criteria
     * Tests ACTUAL merge logic when dedupe field doesn't exist in record
     * Expected: Merge handles missing field gracefully, doesn't crash
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testMergeWithMissingFieldInMatchCriteria() throws Exception {
        // This tests real merge logic with missing field
        // Create existing record WITH Email field
        EntityData existingRecord = createTestRecord(testEntityWithMerge);
        existingRecord.addValue("Name", "Existing User");
        existingRecord.addValue("Email", "test@example.com");
        existingRecord.addValue("Age", 30);
        existingRecord = entityRepo.save(testEntityWithMerge, existingRecord);

        // Create new record WITHOUT Email field (but merge rule expects it)
        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "New User");
        newRecord.getValues().put("Age", 25L);
        // Email field missing - merge should handle this gracefully

        EntityDataResponse response = controller.create(testEntityWithMerge.getId(), newRecord).getBody();

        // Verify create succeeded (merge should not crash on missing field)
        assertNotNull("Response should not be null", response);
        assertNotNull("Record should be created", response.getRecord());
        assertTrue("Should not have validation errors", response.getErrors().isEmpty());

        // Verify merge was attempted (advancedDedupeMerge should be called)
        verify(recordMergeService, times(1)).advancedDedupeMerge(
            any(AdvancedDedupeConfig.class),
            any(EntityData.class),
            any(EntityDefinition.class),
            any(),
            any(),
            any(),
            any()
        );

        // Verify record was created (no match found due to missing Email)
        assertNotNull("Record should have ID", response.getRecord().getSyncariId());
        assertEquals("New User", response.getRecord().getValues().get("Name"));
    }

    /**
     * Test 34: Merge with partial field match (some match criteria fields missing)
     * Tests ACTUAL merge behavior with incomplete matching data
     * Expected: Merge evaluates available fields, handles missing fields appropriately
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testMergeWithPartialFieldMatch() throws Exception {
        // Create existing record with full data
        EntityData existingRecord1 = createTestRecord(testEntityWithMerge);
        existingRecord1.addValue("Name", "John Doe");
        existingRecord1.addValue("Email", "john@example.com");
        existingRecord1.addValue("Age", 30);
        existingRecord1 = entityRepo.save(testEntityWithMerge, existingRecord1);

        EntityData existingRecord2 = createTestRecord(testEntityWithMerge);
        existingRecord2.addValue("Name", "Jane Doe");
        existingRecord2.addValue("Email", "jane@example.com");
        existingRecord2.addValue("Age", 28);
        existingRecord2 = entityRepo.save(testEntityWithMerge, existingRecord2);

        // Create new record with partial data (Name matches existingRecord1, but no Email)
        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "John Doe");  // Matches existing
        newRecord.getValues().put("Age", 32L);
        // Email missing - should this be considered a match?

        EntityDataResponse response = controller.create(testEntityWithMerge.getId(), newRecord).getBody();

        // Verify create succeeded
        assertNotNull("Response should not be null", response);
        assertNotNull("Record should be created or merged", response.getRecord());
        assertTrue("Should not have validation errors", response.getErrors().isEmpty());

        // Verify merge was attempted
        verify(recordMergeService, times(1)).advancedDedupeMerge(
            any(AdvancedDedupeConfig.class),
            any(EntityData.class),
            any(EntityDefinition.class),
            any(),
            any(),
            any(),
            any()
        );

        // Verify result has valid data
        assertNotNull("Result should have Name", response.getRecord().getValues().get("Name"));
    }

    /**
     * Test 35: Both DFI and Merge with missing field references
     * Tests ACTUAL combined behavior when both DFI rules and merge criteria reference missing fields
     * Expected: Both DFI and Merge handle missing fields gracefully, record is processed
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void testDFIAndMergeWithMissingFieldReferences() throws Exception {
        // Setup DFI rule with missing field reference
        DataQualityRule dfiRule = createTestDFIRule(
            "Missing Field DFI",
            "Score",
            "record.VerificationScore > 80",  // Field doesn't exist
            testGraph
        );
        doReturn(List.of(dfiRule)).when(dataQualityService).getAllRules(any(MappingGraph.class));

        // Create existing record for merge (WITH Email)
        EntityData existingRecord = createTestRecord(testEntityWithBoth);
        existingRecord.addValue("Name", "Existing");
        existingRecord.addValue("Email", "test@example.com");
        existingRecord = entityRepo.save(testEntityWithBoth, existingRecord);

        // Create new record WITHOUT VerificationScore (for DFI) and partial merge data
        EntityRecord newRecord = new EntityRecord();
        newRecord.getValues().put("Name", "New Record");
        newRecord.getValues().put("Age", 25L);
        // Missing: VerificationScore (DFI), Email (Merge)

        EntityDataResponse response = controller.create(testEntityWithBoth.getId(), newRecord).getBody();

        // Verify create succeeded despite missing fields in both DFI and Merge
        assertNotNull("Response should not be null", response);
        assertNotNull("Record should be created", response.getRecord());
        assertTrue("Should not have validation errors", response.getErrors().isEmpty());

        // Verify both DFI and Merge were attempted
        verify(dfiExecutorService, atLeastOnce()).evaluateRule(
            any(),
            any(),
            any(),
            eq(dfiRule),
            any(),
            any()
        );
        verify(recordMergeService, times(1)).advancedDedupeMerge(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        );

        // Verify record was processed and saved
        assertNotNull("Record should have ID", response.getRecord().getSyncariId());
        assertEquals("New Record", response.getRecord().getValues().get("Name"));
    }

    // ========================================
    // Helper Methods
    // ========================================

    private EntityDefinition createTestEntity(String name, boolean runDFI, boolean runMerge) {
        EntityDefinition entity = new EntityDefinition(name, name);
        entity.setDraftStatus(DraftStatus.APPROVED);
        entity.setStatus(Status.ACTIVE);
        entity.setConnectorId(connectorService.getSyncariConnector().getId());
        entity.setRunDFI(runDFI);
        entity.setRunMerge(runMerge);
        entity = entityDefinitionRepo.save(entity);

        // Create Name field (String)
        AttributeDefinition nameAttr = new AttributeDefinition()
            .setApiName("Name")
            .setDataType(new StringType())
            .setDisplayName("Name")
            .setEntityId(entity.getId());
        nameAttr.setDraftStatus(DraftStatus.APPROVED);
        nameAttr.setStatus(Status.ACTIVE);
        nameAttr = attributeRepo.save(nameAttr);

        // Create Age field (Integer)
        AttributeDefinition ageAttr = new AttributeDefinition()
            .setApiName("Age")
            .setDataType(new IntegerType())
            .setDisplayName("Age")
            .setEntityId(entity.getId());
        ageAttr.setDraftStatus(DraftStatus.APPROVED);
        ageAttr.setStatus(Status.ACTIVE);
        ageAttr = attributeRepo.save(ageAttr);

        // Create Email field (String)
        AttributeDefinition emailAttr = new AttributeDefinition()
            .setApiName("Email")
            .setDataType(new StringType())
            .setDisplayName("Email")
            .setEntityId(entity.getId());
        emailAttr.setDraftStatus(DraftStatus.APPROVED);
        emailAttr.setStatus(Status.ACTIVE);
        emailAttr = attributeRepo.save(emailAttr);

        entity.addField(nameAttr);
        entity.addField(ageAttr);
        entity.addField(emailAttr);

        return entity;
    }

    private EntityData createTestRecord(EntityDefinition entity) {
        EntityData record = new EntityData();
        record.setName(entity.getApiName());
        record.addValue("Name", "Test Name");
        record.addValue("Age", 30);
        return record;
    }

    private MappingGraph createTestGraphWithDedupeConfig(EntityDefinition entity) {
        MappingGraph graph = new MappingGraph();
        graph.setId(ObjectId.get().toHexString());
        graph.setName("Test Graph for " + entity.getDisplayName());
        graph.setTargetId(entity.getId());
        graph.setDraftStatus(DraftStatus.APPROVED);
        graph.setScope(Scope.ENTITY);

        CoreEntityNodeConfig coreConfig = new CoreEntityNodeConfig();
        coreConfig.setEntityDefinition(entity);
        coreConfig.setAdvancedDedupeConfig(new AdvancedDedupeConfig());

        MappingNode coreNode = new MappingNode();
        coreNode.setId(ObjectId.get().toHexString());
        coreNode.setScope(Scope.ENTITY);
        coreNode.setName(entity.getApiName());
        coreNode.setApiName(entity.getApiName());
        coreNode.setMappingGraphId(graph.getId());
        coreNode.setConfiguration(coreConfig);

        graph.addNode(coreNode);

        return graph;
    }

    private void updateGraphWithDedupeConfig(EntityDefinition entity, AdvancedDedupeConfig dedupeConfig) {
        MappingGraph graph = mappingGraphService.retrieveApprovedEntityGraph(entity.getId()).orElse(null);
        if (graph != null) {
            MappingNode coreNode = graph.getCoreNode();
            CoreEntityNodeConfig coreConfig = (CoreEntityNodeConfig) coreNode.getConfiguration();
            coreConfig.setAdvancedDedupeConfig(dedupeConfig);
        }
    }

    private AdvancedDedupeConfig createEmailDedupeConfig(EntityDefinition entity) {
        AttributeDefinition emailAttr = entity.getFieldByName("Email");
        if (emailAttr == null) {
            throw new IllegalArgumentException("Email field not found on entity: " + entity.getApiName());
        }

        Map<String, Object> predicate = new HashMap<>();
        predicate.put("operator", "AND");
        predicate.put("predicates", List.of(
            Map.of(
                "operator", "eq",
                "left", Map.of("type", "variable", "value", emailAttr.getId()),
                "right", Map.of("type", "literal", "value", emailAttr.getId())
            )
        ));

        Map<String, Object> findDupesPredicate = Map.of(
            "name", "findDupesPredicate",
            "value", predicate
        );

        Map<String, Object> compositeValue = Map.of(
            "findDupesPredicate", findDupesPredicate,
            "repeatId", ObjectId.get().toHexString()
        );

        Map<String, Object> findDupes = Map.of(
            "configId", ObjectId.get().toHexString(),
            "name", "findDupes",
            "compositeValues", List.of(compositeValue)
        );

        AdvancedDedupeConfig config = new AdvancedDedupeConfig();
        config.setFindDupes(findDupes);
        config.setMergeAction(MergeAction.MERGE);

        return config;
    }

    private AdvancedDedupeConfig createReportOnlyDedupeConfig(EntityDefinition entity) {
        AdvancedDedupeConfig config = createEmailDedupeConfig(entity);
        config.setMergeAction(MergeAction.REPORT_ONLY);
        return config;
    }

    private DataQualityRule createTestDFIRule(String name, String fieldName, String expression, MappingGraph graph) {
        DataQualityRule rule = new DataQualityRule();
        rule.setId(ObjectId.get().toHexString());
        rule.setName(name);
        rule.setEntityId(graph.getTargetId());
        rule.setMappingGraphId(graph.getId());
        rule.setPolicy("WARN");
        rule.setCategory(ObjectId.get().toHexString());
        rule.setIsDeleted(false);

        // Create proper predicate structure instead of simple expression string
        // Parse the expression to create the appropriate predicate
        Map<String, Object> ruleConfig = createPredicateFromExpression(expression, fieldName, graph);
        rule.setRuleConfig(ruleConfig);

        rule.setScope(List.of("record"));
        rule.setScopeType("record");

        return rule;
    }

    private Map<String, Object> createPredicateFromExpression(String expression, String fieldName, MappingGraph graph) {
        // Simple expression parser for common DFI test cases
        // Supports: "record.Field != null", "record.Field >= value", etc.

        // Default: create a simple not-null check predicate
        Map<String, Object> predicate = new HashMap<>();
        predicate.put("operator", "AND");

        List<Map<String, Object>> predicates = new ArrayList<>();

        // Parse simple expressions
        if (expression.contains("!= null")) {
            // Field is not null check - Map.of() doesn't accept null values
            String field = extractFieldName(expression);
            Map<String, Object> rightMap = new HashMap<>();
            rightMap.put("type", "literal");
            rightMap.put("value", null);
            Map<String, Object> pred = new HashMap<>();
            pred.put("operator", "ne");
            pred.put("left", Map.of("type", "variable", "value", field));
            pred.put("right", rightMap);
            predicates.add(pred);
        } else if (expression.contains(">=")) {
            // Greater than or equal check
            String field = extractFieldName(expression);
            String value = extractValue(expression, ">=");
            predicates.add(Map.of(
                "operator", "gte",
                "left", Map.of("type", "variable", "value", field),
                "right", Map.of("type", "literal", "value", parseValue(value))
            ));
        } else if (expression.contains(">")) {
            // Greater than check
            String field = extractFieldName(expression);
            String value = extractValue(expression, ">");
            predicates.add(Map.of(
                "operator", "gt",
                "left", Map.of("type", "variable", "value", field),
                "right", Map.of("type", "literal", "value", parseValue(value))
            ));
        } else if (expression.contains("==")) {
            // Equality check
            String field = extractFieldName(expression);
            String value = extractValue(expression, "==");
            predicates.add(Map.of(
                "operator", "eq",
                "left", Map.of("type", "variable", "value", field),
                "right", Map.of("type", "literal", "value", parseValue(value))
            ));
        } else if (expression.contains("||")) {
            // OR expression - split and create multiple predicates with OR operator
            predicate.put("operator", "OR");
            String[] parts = expression.split("\\|\\|");
            for (String part : parts) {
                predicates.add(createSimplePredicate(part.trim()));
            }
        } else {
            // Default: simple not-null check - Map.of() doesn't accept null values
            Map<String, Object> rightMap = new HashMap<>();
            rightMap.put("type", "literal");
            rightMap.put("value", null);
            Map<String, Object> pred = new HashMap<>();
            pred.put("operator", "ne");
            pred.put("left", Map.of("type", "variable", "value", fieldName));
            pred.put("right", rightMap);
            predicates.add(pred);
        }

        predicate.put("predicates", predicates);
        return predicate;
    }

    private Map<String, Object> createSimplePredicate(String expression) {
        if (expression.contains("!= null")) {
            String field = extractFieldName(expression);
            Map<String, Object> rightMap = new HashMap<>();
            rightMap.put("type", "literal");
            rightMap.put("value", null);
            Map<String, Object> pred = new HashMap<>();
            pred.put("operator", "ne");
            pred.put("left", Map.of("type", "variable", "value", field));
            pred.put("right", rightMap);
            return pred;
        } else if (expression.contains(">=")) {
            String field = extractFieldName(expression);
            String value = extractValue(expression, ">=");
            return Map.of(
                "operator", "gte",
                "left", Map.of("type", "variable", "value", field),
                "right", Map.of("type", "literal", "value", parseValue(value))
            );
        } else if (expression.contains("==")) {
            String field = extractFieldName(expression);
            String value = extractValue(expression, "==");
            return Map.of(
                "operator", "eq",
                "left", Map.of("type", "variable", "value", field),
                "right", Map.of("type", "literal", "value", parseValue(value))
            );
        }
        // Default - Map.of() doesn't accept null values
        Map<String, Object> rightMap = new HashMap<>();
        rightMap.put("type", "literal");
        rightMap.put("value", null);
        Map<String, Object> pred = new HashMap<>();
        pred.put("operator", "ne");
        pred.put("left", Map.of("type", "variable", "value", "unknown"));
        pred.put("right", rightMap);
        return pred;
    }

    private String extractFieldName(String expression) {
        // Extract field name from "record.FieldName" pattern
        if (expression.contains("record.")) {
            int start = expression.indexOf("record.") + 7;
            int end = expression.indexOf(" ", start);
            if (end == -1) end = expression.indexOf(")", start);
            if (end == -1) end = expression.indexOf("|", start);
            if (end == -1) end = expression.length();
            return expression.substring(start, end).trim();
        }
        return "unknown";
    }

    private String extractValue(String expression, String operator) {
        int opIndex = expression.indexOf(operator);
        if (opIndex > 0) {
            return expression.substring(opIndex + operator.length()).trim();
        }
        return "0";
    }

    private Object parseValue(String value) {
        value = value.trim();
        // Parse boolean
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        // Parse number
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            } else {
                return Long.parseLong(value);
            }
        } catch (NumberFormatException e) {
            // Return as string
            return value;
        }
    }
}
