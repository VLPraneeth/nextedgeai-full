package com.syncari.core.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.repositories.customer.MappingGraphRepo;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import com.syncari.core.repositories.customer.PipelineTestRepo;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class PipelineTestServiceTest extends AbstractSyncariTest {

    @Autowired
    PipelineTestService pipelineTestService;

    @Autowired
    StreamService streamService;

    @Autowired
    MappingGraphService mappingGraphService;

    @Autowired
    PipelineTestRepo pipelineTestRepo;

    @Autowired
    private MappingNodeRepo nodeRepo;

    @Autowired
	MappingGraphRepo mappingGraphRepo;

    @Autowired
    EntityDefinitionRepo entityProxyRepo;
    
    @Autowired
	ConnectorService connectorService;

    @Autowired
    SchemaService schemaService;

    private MappingGraph testGraph;
    private PipelineTest pipelineTest;
    private Instant start;
    private Instant end;

    @Override
    public void setUp() {
        if (testGraph == null) {
            super.setUp();
            EntityDefinition syncariEntity = entityProxyRepo
                .findByConnectorId(connectorService.findSyncariConnector().getId()).get(0);
            testGraph = mappingGraphRepo.save(new MappingGraph().setName("PipelineTestService Test Map")
                .setScope(Scope.ENTITY).setTargetId("entityId"));
            testGraph.setTargetId(syncariEntity.getId());
            mappingGraphRepo.save(testGraph);
        }
        start = Instant.now().minus(1, ChronoUnit.DAYS);
        end = Instant.now();
        pipelineTest = pipelineTestService.getNewTestInstanceForGraph(testGraph, start, end, 10, 
            new HashMap<>(), SyncStream.Status.READY, null);
    }

    @Override
	public void tearDown() {
        pipelineTestService.deleteTest(pipelineTest.getId());
        //super.tearDown();
    }
    
    @Test(expected = SyncariValidationException.class)
    public void getNewTestForGraphWithoutWM_RecordsThrowsException() {
        PipelineTest pipelineTest = pipelineTestService.getNewTestInstanceForGraph(testGraph, null, null, 10, 
            new HashMap<>(), SyncStream.Status.READY, null);
    }
    
    @Test
    public void getNewTestForGraph_InputNotTrimmed() {
    	HashMap<String, List<String>> recordIds = new HashMap<>();
    	List<String> ids = new ArrayList<>();
    	ids.add(" 234");
    	ids.add("123 ");
    	ids.add(" 456 ");
    	recordIds.put("123", ids);
    	EntityDefinition syncariEntity = entityProxyRepo
    			.findByConnectorId(connectorService.findSyncariConnector().getId()).get(0);
    	MappingGraph testGraphTrim = mappingGraphRepo.save(new MappingGraph().setName("PipelineTestService Test Map Trim")
    			.setScope(Scope.ENTITY).setTargetId("entityId"));
    	testGraphTrim.setTargetId(syncariEntity.getId());
    	mappingGraphRepo.save(testGraphTrim);
		PipelineTest pipelineTest = pipelineTestService.getNewTestInstanceForGraph(testGraphTrim, null, null, 10, 
    			recordIds, SyncStream.Status.READY, null);
		assertEquals(" 234", pipelineTest.getRecordIds().get("123").get(0));
		assertEquals("123 ", pipelineTest.getRecordIds().get("123").get(1));
		assertEquals(" 456 ", pipelineTest.getRecordIds().get("123").get(2));
    }
    
    @Test
    public void getNewTestForGraph() {
        assertNotNull(pipelineTest);
        assertEquals(Status.NEW, pipelineTest.getStatus());
        assertEquals(testGraph.getId(), pipelineTest.getGraphId());
        assertEquals(start, pipelineTest.getStartTime());
        assertEquals(end, pipelineTest.getEndTime());
        assertTrue(pipelineTest.getRecordIds().isEmpty());
        assertEquals(10, pipelineTest.getLimit());
        assertEquals(SyncStream.Status.READY, pipelineTest.getOriginalStreamStatus());
    }

    @Test
    public void getNewTestForGraphAlreadyScheduled_ThrowsException() {
        assertNotNull(pipelineTest);
        try {
            // Should throw validation exception.
            pipelineTest = pipelineTestService.getNewTestInstanceForGraph(testGraph, start, end, 10, 
                new HashMap<>(), SyncStream.Status.READY, null);
        } catch (SyncariValidationException e) {
            "Cannot create a test for graph since there is already a test in progress.".equalsIgnoreCase(e.getMessage());
        }
    }

    @Test
    public void getTestForProcessing() {
        try {
            assertNotNull(pipelineTest);
            Optional<PipelineTest> test = pipelineTestService.getTestForProcessing(pipelineTest.getId());
            assertTrue(test.isPresent());
            assertEquals(Status.PROCESSING, test.get().getStatus());
            assertTrue(test.get().getCheckin().toEpochMilli() > end.toEpochMilli());

            // Try getting same test for processing, should not return any, since test is already in progress.
            test = pipelineTestService.getTestForProcessing(pipelineTest.getId());
            assertFalse(test.isPresent());
        }  finally {
            pipelineTestService.deleteTest(pipelineTest.getId());
        }
    }

    @Test
    public void hasTestInProgress() {
        pipelineTestService.deleteTest(pipelineTest.getId());
        assertFalse(pipelineTestService.hasTestInProgress(testGraph));
        List<PipelineTest> tests = pipelineTestService.getActiveTestPipelineForGraphs(List.of(testGraph.getId()));
        assertTrue(tests.isEmpty());
        pipelineTest = pipelineTestService.getNewTestInstanceForGraph(testGraph, end.minus(1, ChronoUnit.DAYS), 
            end, 10, new HashMap<>(), SyncStream.Status.READY, null);
        assertNotNull(pipelineTest);
        assertTrue(pipelineTestService.hasTestInProgress(testGraph));
        Optional<PipelineTest> test = pipelineTestService.getTestForProcessing(pipelineTest.getId());
        assertTrue(pipelineTestService.hasTestInProgress(testGraph));
    }

    @Test
    public void getActiveTestPipelineForGraphs() {
        pipelineTestService.deleteTest(pipelineTest.getId());
        List<PipelineTest> tests = pipelineTestService.getActiveTestPipelineForGraphs(List.of(testGraph.getId()));
        assertTrue(tests.isEmpty());

        Optional<PipelineTest> test = pipelineTestService.getTestByIdAndGraphId(testGraph.getId(), "latest");
        assertTrue(test.isEmpty());

        pipelineTest = pipelineTestService.getNewTestInstanceForGraph(testGraph, end.minus(1, ChronoUnit.DAYS), 
            end, 10, new HashMap<>(), SyncStream.Status.READY, null);
        assertNotNull(pipelineTest);
        tests = pipelineTestService.getActiveTestPipelineForGraphs(List.of(testGraph.getId()));
        assertFalse(tests.isEmpty());
        assertEquals(1, tests.size());
        assertEquals(pipelineTest.getId(), tests.get(0).getId());

        test = pipelineTestService.getTestByIdAndGraphId(testGraph.getId(), "latest");
        assertFalse(test.isEmpty());

        test = pipelineTestService.getTestByIdAndGraphId(testGraph.getId(), pipelineTest.getId());
        assertFalse(test.isEmpty());

        pipelineTestService.finishTestRun(pipelineTest, testGraph, null);
        // finishing a 'NEW' test wont do anything
        tests = pipelineTestService.getActiveTestPipelineForGraphs(List.of(testGraph.getId()));
        assertFalse(tests.isEmpty());

        // Process it.
        test = pipelineTestService.getTestForProcessing(pipelineTest.getId());
        pipelineTestService.finishTestRun(pipelineTest, testGraph, null);
        // Now, we actually finished the test.
        tests = pipelineTestService.getActiveTestPipelineForGraphs(List.of(testGraph.getId()));
        assertTrue(tests.isEmpty());
    }

    @Test
    public void finishTestRunSuccess() {
        pipelineTestService.finishTestRun(pipelineTest, testGraph, null);
        Optional<PipelineTest> test = pipelineTestRepo.findById(pipelineTest.getId());
        // A new test cannot be finished.
        assertEquals(Status.NEW, test.get().getStatus());

        test = pipelineTestService.getTestForProcessing(pipelineTest.getId());
        assertEquals(Status.PROCESSING, test.get().getStatus());

        pipelineTestService.finishTestRun(pipelineTest, testGraph, null);
        test = pipelineTestRepo.findById(pipelineTest.getId());
        assertEquals(Status.COMPLETED, test.get().getStatus());

        test = pipelineTestService.getTestForProcessing(pipelineTest.getId());
        assertFalse(test.isPresent());
    }

    @Test
    public void finishTestRunError() {
        pipelineTestService.finishTestRun(pipelineTest, testGraph, new RuntimeException("Mock Exception"));
        Optional<PipelineTest> test = pipelineTestRepo.findById(pipelineTest.getId());
        // A new test cannot be finished.
        assertEquals(Status.NEW, test.get().getStatus());

        test = pipelineTestService.getTestForProcessing(pipelineTest.getId());
        assertEquals(Status.PROCESSING, test.get().getStatus());

        pipelineTestService.finishTestRun(pipelineTest, testGraph, new RuntimeException("Mock Exception"));
        test = pipelineTestRepo.findById(pipelineTest.getId());
        assertEquals(Status.ERROR, test.get().getStatus());

        test = pipelineTestService.getTestForProcessing(pipelineTest.getId());
        assertFalse(test.isPresent());
    }

    @Test
    public void finishTestRunSuccess_StreamTestDone() {
        testGraph.setName("finishTestRunSuccess_StreamTestDone");
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();

        var coreNode = nodeRepo.save(new MappingNode().setName("Zzzz").setApiName("Zzzz").setScope(Scope.ENTITY)
                .setConfiguration(new CoreEntityNodeConfig().setEntityDefinition(syncariEntity))
                .setMappingGraphId(testGraph.getId()));
        var newDraft = mappingGraphService.createDraftFor(testGraph);
        SyncStream stream = streamService.getOrCreateReadyStream(testGraph.getId());

        Instant start = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant end = Instant.now();
        PipelineTest pTest = pipelineTestService.getNewTestInstanceForGraph(newDraft, start, end, 10, 
            new HashMap<>(), SyncStream.Status.READY, null);
        
        Optional<PipelineTest> test = pipelineTestService.getTestForProcessing(pTest.getId());
        assertEquals(Status.PROCESSING, test.get().getStatus());

        stream = streamService.pause(stream.getId());
        assertEquals(SyncStream.Status.PAUSED, stream.getStatus());

        pipelineTestService.finishTestRun(pTest, newDraft, null);
        test = pipelineTestRepo.findById(pTest.getId());
        assertEquals(Status.COMPLETED, test.get().getStatus());

        stream = streamService.getById(stream.getId());
        assertEquals(SyncStream.Status.PAUSED, stream.getStatus());

        // Now test for stream non-PAUSED state.
        test = pipelineTestService.getTestForProcessing(pTest.getId());
        stream = streamService.running(stream.getId());
        // issue pause for the stream.
        boolean success = streamService.issuePause(testGraph.getId());
        assertTrue(success);
        pipelineTestService.finishTestRun(pTest, newDraft, null);
        test = pipelineTestRepo.findById(pTest.getId());
        assertEquals(Status.COMPLETED, test.get().getStatus());

        stream = streamService.getById(stream.getId());
        // Not set to READY by notityResult.finally.testDone because we issued a PAUSING state to the stream
        assertEquals(SyncStream.Status.PAUSING, stream.getStatus());
    }

    @Test
    public void testResumeStatus_StreamTestDone() {
        assertEquals(SyncStream.Status.PAUSED, testNewStatus(SyncStream.Status.PAUSED));
        assertEquals(SyncStream.Status.READY, testNewStatus(SyncStream.Status.READY));
        assertEquals(SyncStream.Status.RUNNING, testNewStatus(SyncStream.Status.RUNNING));
        assertEquals(SyncStream.Status.CLAIMED, testNewStatus(SyncStream.Status.CLAIMED));
    }

    private SyncStream.Status testNewStatus(SyncStream.Status originalStatus) {
        testGraph.setName("finishTestRunSuccess_StreamTestDone" + new Random().nextInt());
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();

        var coreNode = nodeRepo.save(new MappingNode().setName("Zzzz").setApiName("Zzzz").setScope(Scope.ENTITY)
                .setConfiguration(new CoreEntityNodeConfig().setEntityDefinition(syncariEntity))
                .setMappingGraphId(testGraph.getId()));
        var newDraft = mappingGraphService.createDraftFor(testGraph);
        SyncStream stream = streamService.getOrCreateReadyStream(testGraph.getId());
        stream.setStatus(originalStatus);
        streamService.save(stream);

        Instant start = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant end = Instant.now();
        PipelineTest pTest = pipelineTestService.getNewTestInstanceForGraph(newDraft, start, end, 10,
                new HashMap<>(), SyncStream.Status.READY, null);

        Optional<PipelineTest> test = pipelineTestService.getTestForProcessing(pTest.getId());
        assertEquals(Status.PROCESSING, test.get().getStatus());

        pipelineTestService.finishTestRun(pTest, newDraft, null);

        mappingGraphService.discardDraft(newDraft);

        stream = streamService.getById(stream.getId());
        return stream.getStatus();
    }

    @Test
    public void buryTheDead() {
        // The active test just had a checkin so this should do nothing.
        pipelineTestService.buryTheDead();
        Optional<PipelineTest> test = pipelineTestRepo.findById(pipelineTest.getId());
        assertTrue(test.isPresent());
        assertEquals(Status.NEW, test.get().getStatus());

        // Now start processing
        test = pipelineTestService.getTestForProcessing(pipelineTest.getId());
        assertEquals(Status.PROCESSING, test.get().getStatus());
        // The test is now in processing state so invoking buryTheDead should do nothing yet.
        pipelineTestService.buryTheDead();
        test = pipelineTestRepo.findById(pipelineTest.getId());
        assertEquals(Status.PROCESSING, test.get().getStatus());

        // Finish the test and invoke buryTheDead, nothing should happen because the test finished
        pipelineTestService.finishTestRun(pipelineTest, testGraph, null);
        pipelineTestService.buryTheDead();
        test = pipelineTestRepo.findById(pipelineTest.getId());
        assertEquals(Status.COMPLETED, test.get().getStatus());

        // Get a new test and move it to ERROR state (logical error not due to dead state),
        // Then run buryTheDead, still there should not be any impact on the ERROR state test.
        pipelineTestService.deleteTest(pipelineTest.getId());
        Instant buryTheDeadTestInstant = Instant.now();
        pipelineTest = pipelineTestService.getNewTestInstanceForGraph(testGraph, buryTheDeadTestInstant.minus(1, ChronoUnit.DAYS), 
            buryTheDeadTestInstant, 10, new HashMap<>(), SyncStream.Status.READY, null);
        assertNotNull(pipelineTest);
        test = pipelineTestService.getTestForProcessing(pipelineTest.getId());
        pipelineTestService.finishTestRun(pipelineTest, testGraph, new RuntimeException("Mock Exception"));
        pipelineTestService.buryTheDead();
        test = pipelineTestRepo.findById(pipelineTest.getId());
        assertEquals(Status.ERROR, test.get().getStatus());
        // This assertion is to make sure the test was not processed by buryTheDead. the checkin here is much closer than
        // the heart beat expiry for buryTheDead to pick it up.
        assertTrue(test.get().getCheckin().toEpochMilli() > buryTheDeadTestInstant.toEpochMilli());

        // finally, test true positive scenario.
        pipelineTestService.deleteTest(pipelineTest.getId());
        buryTheDeadTestInstant = Instant.now();
        pipelineTest = pipelineTestService.getNewTestInstanceForGraph(testGraph, buryTheDeadTestInstant.minus(1, ChronoUnit.DAYS), 
            buryTheDeadTestInstant, 10, new HashMap<>(), SyncStream.Status.READY, null);
        assertNotNull(pipelineTest);
        test = pipelineTestService.getTestForProcessing(pipelineTest.getId());
        assertEquals(Status.PROCESSING, test.get().getStatus());
        // Just move the test checkin to a past value, so buryTheDead will detect it as stuck test.
        pipelineTest = test.get();
        pipelineTest.setCheckin(Instant.now().minus(2, ChronoUnit.HOURS));
        pipelineTestRepo.save(pipelineTest);

        pipelineTestService.buryTheDead();
        test = pipelineTestRepo.findById(pipelineTest.getId());
        assertEquals(Status.ERROR, test.get().getStatus());
    }
    
}
