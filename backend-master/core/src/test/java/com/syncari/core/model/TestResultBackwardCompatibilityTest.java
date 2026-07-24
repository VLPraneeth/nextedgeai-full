package com.syncari.core.model;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.misc.PipelineTestStatus;
import com.syncari.core.model.misc.test.TestNodeResult;
import com.syncari.core.repositories.customer.TestNodeResultRepo;
import com.syncari.core.repositories.customer.TestResultRepo;
import com.syncari.core.service.TestResultLoader;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;

public class TestResultBackwardCompatibilityTest extends AbstractSyncariTest {

    @Autowired
    private TestResultRepo testResultRepo;

    @Autowired
    private TestNodeResultRepo testNodeResultRepo;

    @Autowired
    private TestResultLoader testResultLoader;

    @After
    @Override
    public void tearDown() {
        testResultRepo.deleteAll();
        testNodeResultRepo.deleteAll();
        super.tearDown();
    }

    @Test
    public void testLoadLegacyEmbeddedNodeResults() {
        // Simulate an old document with embedded nodeResults
        TestResult legacyTestResult = new TestResult();
        legacyTestResult.setStatus(PipelineTestStatus.success);
        legacyTestResult.setPipelineTestId("legacy-test-1");

        // Create embedded node results (old format)
        TestNodeResult embeddedNode1 = new TestNodeResult();
        embeddedNode1.setNodeId("legacy-node-1");
        embeddedNode1.setNodeName("Legacy Node 1");
        embeddedNode1.setStatus(TestNodeResult.Status.SUCCESS);

        TestNodeResult embeddedNode2 = new TestNodeResult();
        embeddedNode2.setNodeId("legacy-node-2");
        embeddedNode2.setNodeName("Legacy Node 2");
        embeddedNode2.setStatus(TestNodeResult.Status.FAILED);

        List<TestNodeResult> embeddedResults = new ArrayList<>();
        embeddedResults.add(embeddedNode1);
        embeddedResults.add(embeddedNode2);
        legacyTestResult.setNodeResults(embeddedResults);

        // Save the legacy document
        legacyTestResult = testResultRepo.save(legacyTestResult);

        // Reload from database
        TestResult reloadedResult = testResultRepo.findById(legacyTestResult.getId()).orElse(null);
        assertNotNull(reloadedResult);

        // Load node results using the loader
        testResultLoader.loadNodeResults(reloadedResult);

        // Verify legacy embedded data loads correctly
        List<TestNodeResult> loadedResults = reloadedResult.getNodeResults();
        assertNotNull(loadedResults);
        assertEquals(2, loadedResults.size());
        assertEquals("legacy-node-1", loadedResults.get(0).getNodeId());
        assertEquals("legacy-node-2", loadedResults.get(1).getNodeId());
        assertEquals(TestNodeResult.Status.SUCCESS, loadedResults.get(0).getStatus());
        assertEquals(TestNodeResult.Status.FAILED, loadedResults.get(1).getStatus());

        // Verify no external storage was used
        long externalCount = testNodeResultRepo.countByTestResultId(reloadedResult.getId());
        assertEquals(0L, externalCount);
    }

    @Test
    public void testMigrationScenario() {
        // Create one old-style document with embedded nodeResults
        TestResult oldStyleResult = new TestResult();
        oldStyleResult.setStatus(PipelineTestStatus.success);
        oldStyleResult.setPipelineTestId("old-style-test");

        TestNodeResult oldNode = new TestNodeResult();
        oldNode.setNodeId("old-node");
        oldNode.setNodeName("Old Style Node");
        oldNode.setStatus(TestNodeResult.Status.SUCCESS);

        List<TestNodeResult> embeddedList = new ArrayList<>();
        embeddedList.add(oldNode);
        oldStyleResult.setNodeResults(embeddedList);
        oldStyleResult = testResultRepo.save(oldStyleResult);

        // Create one new-style document with external storage
        TestResult newStyleResult = new TestResult();
        newStyleResult.setStatus(PipelineTestStatus.success);
        newStyleResult.setPipelineTestId("new-style-test");
        newStyleResult.setNodeResults(null); // External storage
        newStyleResult = testResultRepo.save(newStyleResult);

        TestNodeResult newNode = new TestNodeResult();
        newNode.setTestResultId(newStyleResult.getId());
        newNode.setSequence(0);
        newNode.setNodeId("new-node");
        newNode.setNodeName("New Style Node");
        newNode.setStatus(TestNodeResult.Status.SUCCESS);
        testNodeResultRepo.save(newNode);

        // Load both and verify they work correctly
        TestResult reloadedOld = testResultRepo.findById(oldStyleResult.getId()).orElse(null);
        TestResult reloadedNew = testResultRepo.findById(newStyleResult.getId()).orElse(null);

        testResultLoader.loadNodeResults(reloadedOld);
        testResultLoader.loadNodeResults(reloadedNew);

        // Verify old style still works
        assertEquals(1, reloadedOld.getNodeResults().size());
        assertEquals("old-node", reloadedOld.getNodeResults().get(0).getNodeId());

        // Verify new style works
        assertEquals(1, reloadedNew.getNodeResults().size());
        assertEquals("new-node", reloadedNew.getNodeResults().get(0).getNodeId());

        // Verify old has no external storage
        assertEquals(0L, testNodeResultRepo.countByTestResultId(reloadedOld.getId()));

        // Verify new has external storage
        assertEquals(1L, testNodeResultRepo.countByTestResultId(reloadedNew.getId()));
    }

    @Test
    public void testGetNodeResults_FallsBackToEmbedded() {
        // Create a TestResult with embedded nodeResults (legacy)
        TestResult testResult = new TestResult();
        testResult.setStatus(PipelineTestStatus.success);
        testResult.setPipelineTestId("fallback-test");

        TestNodeResult embeddedNode = new TestNodeResult();
        embeddedNode.setNodeId("fallback-node");
        embeddedNode.setNodeName("Fallback Node");
        embeddedNode.setStatus(TestNodeResult.Status.SUCCESS);

        testResult.setNodeResults(Arrays.asList(embeddedNode));
        testResult = testResultRepo.save(testResult);

        // Reload from database
        TestResult reloaded = testResultRepo.findById(testResult.getId()).orElse(null);
        assertNotNull(reloaded);

        // Call getNodeResults without loading first (tests the fallback logic)
        List<TestNodeResult> nodeResults = reloaded.getNodeResults();

        // Should fallback to embedded nodeResults
        assertNotNull(nodeResults);
        assertEquals(1, nodeResults.size());
        assertEquals("fallback-node", nodeResults.get(0).getNodeId());
    }

    @Test
    public void testFindNodeResult_WorksWithBothStorageTypes() {
        // Create legacy TestResult with embedded storage
        TestResult legacyResult = new TestResult();
        legacyResult.setStatus(PipelineTestStatus.success);
        legacyResult.setPipelineTestId("legacy-find-test");

        TestNodeResult legacyNode1 = new TestNodeResult();
        legacyNode1.setNodeId("legacy-target");
        legacyNode1.setNodeName("Legacy Target Node");
        legacyNode1.setStatus(TestNodeResult.Status.SUCCESS);

        TestNodeResult legacyNode2 = new TestNodeResult();
        legacyNode2.setNodeId("legacy-other");
        legacyNode2.setNodeName("Legacy Other Node");
        legacyNode2.setStatus(TestNodeResult.Status.SUCCESS);

        legacyResult.setNodeResults(Arrays.asList(legacyNode1, legacyNode2));
        legacyResult = testResultRepo.save(legacyResult);

        // Create new TestResult with external storage
        TestResult newResult = new TestResult();
        newResult.setStatus(PipelineTestStatus.success);
        newResult.setPipelineTestId("new-find-test");
        newResult.setNodeResults(null);
        newResult = testResultRepo.save(newResult);

        TestNodeResult newNode1 = new TestNodeResult();
        newNode1.setTestResultId(newResult.getId());
        newNode1.setSequence(0);
        newNode1.setNodeId("new-target");
        newNode1.setNodeName("New Target Node");
        newNode1.setStatus(TestNodeResult.Status.SUCCESS);

        TestNodeResult newNode2 = new TestNodeResult();
        newNode2.setTestResultId(newResult.getId());
        newNode2.setSequence(1);
        newNode2.setNodeId("new-other");
        newNode2.setNodeName("New Other Node");
        newNode2.setStatus(TestNodeResult.Status.SUCCESS);

        testNodeResultRepo.saveAll(Arrays.asList(newNode1, newNode2));

        // Reload both
        TestResult reloadedLegacy = testResultRepo.findById(legacyResult.getId()).orElse(null);
        TestResult reloadedNew = testResultRepo.findById(newResult.getId()).orElse(null);

        // Load node results
        testResultLoader.loadNodeResults(reloadedLegacy);
        testResultLoader.loadNodeResults(reloadedNew);

        // Find specific nodes in both storage types
        Optional<TestNodeResult> legacyFound = reloadedLegacy.findNodeResult("legacy-target");
        Optional<TestNodeResult> newFound = reloadedNew.findNodeResult("new-target");

        // Verify both work
        assertTrue(legacyFound.isPresent());
        assertEquals("legacy-target", legacyFound.get().getNodeId());
        assertEquals("Legacy Target Node", legacyFound.get().getNodeName());

        assertTrue(newFound.isPresent());
        assertEquals("new-target", newFound.get().getNodeId());
        assertEquals("New Target Node", newFound.get().getNodeName());

        // Verify non-existent nodes return empty
        assertFalse(reloadedLegacy.findNodeResult("non-existent").isPresent());
        assertFalse(reloadedNew.findNodeResult("non-existent").isPresent());
    }
}
