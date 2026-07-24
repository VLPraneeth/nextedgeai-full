package com.syncari.core.service;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.TestResult;
import com.syncari.core.model.misc.PipelineTestStatus;
import com.syncari.core.model.misc.test.TestNodeResult;
import com.syncari.core.repositories.customer.TestNodeResultRepo;
import com.syncari.core.repositories.customer.TestResultRepo;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class TestResultLoaderTest extends AbstractSyncariTest {

    @Autowired
    private TestResultLoader testResultLoader;

    @Autowired
    private TestResultRepo testResultRepo;

    @Autowired
    private TestNodeResultRepo testNodeResultRepo;

    @After
    @Override
    public void tearDown() {
        testResultRepo.deleteAll();
        testNodeResultRepo.deleteAll();
        super.tearDown();
    }

    @Test
    public void testLoadNodeResults_WithExternalStorage() {
        // Create a TestResult with external storage (nodeResults = null)
        TestResult testResult = new TestResult();
        testResult.setStatus(PipelineTestStatus.success);
        testResult.setPipelineTestId("test-pipeline-1");
        testResult.setNodeResults(null); // External storage mode
        testResult = testResultRepo.save(testResult);

        // Create external node results
        TestNodeResult nodeResult1 = new TestNodeResult();
        nodeResult1.setTestResultId(testResult.getId());
        nodeResult1.setSequence(0);
        nodeResult1.setNodeId("node-1");
        nodeResult1.setNodeName("Test Node 1");
        nodeResult1.setStatus(TestNodeResult.Status.SUCCESS);

        TestNodeResult nodeResult2 = new TestNodeResult();
        nodeResult2.setTestResultId(testResult.getId());
        nodeResult2.setSequence(1);
        nodeResult2.setNodeId("node-2");
        nodeResult2.setNodeName("Test Node 2");
        nodeResult2.setStatus(TestNodeResult.Status.SUCCESS);

        testNodeResultRepo.saveAll(Arrays.asList(nodeResult1, nodeResult2));

        // Load node results
        testResultLoader.loadNodeResults(testResult);

        // Verify
        List<TestNodeResult> loadedResults = testResult.getNodeResults();
        assertNotNull(loadedResults);
        assertEquals(2, loadedResults.size());
        assertEquals("node-1", loadedResults.get(0).getNodeId());
        assertEquals("node-2", loadedResults.get(1).getNodeId());
        assertEquals(Integer.valueOf(0), loadedResults.get(0).getSequence());
        assertEquals(Integer.valueOf(1), loadedResults.get(1).getSequence());
    }

    @Test
    public void testLoadNodeResults_WithEmbeddedStorage() {
        // Create a TestResult with embedded storage (old format)
        TestResult testResult = new TestResult();
        testResult.setStatus(PipelineTestStatus.success);
        testResult.setPipelineTestId("test-pipeline-2");

        // Create embedded node results (legacy format)
        TestNodeResult nodeResult1 = new TestNodeResult();
        nodeResult1.setNodeId("node-1");
        nodeResult1.setNodeName("Embedded Node 1");
        nodeResult1.setStatus(TestNodeResult.Status.SUCCESS);

        TestNodeResult nodeResult2 = new TestNodeResult();
        nodeResult2.setNodeId("node-2");
        nodeResult2.setNodeName("Embedded Node 2");
        nodeResult2.setStatus(TestNodeResult.Status.FAILED);

        List<TestNodeResult> embeddedResults = new ArrayList<>();
        embeddedResults.add(nodeResult1);
        embeddedResults.add(nodeResult2);
        testResult.setNodeResults(embeddedResults);

        testResult = testResultRepo.save(testResult);

        // Load node results
        testResultLoader.loadNodeResults(testResult);

        // Verify - should load from embedded field
        List<TestNodeResult> loadedResults = testResult.getNodeResults();
        assertNotNull(loadedResults);
        assertEquals(2, loadedResults.size());
        assertEquals("node-1", loadedResults.get(0).getNodeId());
        assertEquals("node-2", loadedResults.get(1).getNodeId());
        assertEquals(TestNodeResult.Status.SUCCESS, loadedResults.get(0).getStatus());
        assertEquals(TestNodeResult.Status.FAILED, loadedResults.get(1).getStatus());
    }

    @Test
    public void testLoadNodeResults_EmptyResults() {
        // Create a TestResult with no node results
        TestResult testResult = new TestResult();
        testResult.setStatus(PipelineTestStatus.success);
        testResult.setPipelineTestId("test-pipeline-3");
        testResult.setNodeResults(null);
        testResult = testResultRepo.save(testResult);

        // Load node results
        testResultLoader.loadNodeResults(testResult);

        // Verify - should return empty list
        List<TestNodeResult> loadedResults = testResult.getNodeResults();
        assertNotNull(loadedResults);
        assertEquals(0, loadedResults.size());
    }

    @Test
    public void testLoadNodeResults_AlreadyLoaded() {
        // Create a TestResult with external storage
        TestResult testResult = new TestResult();
        testResult.setStatus(PipelineTestStatus.success);
        testResult.setPipelineTestId("test-pipeline-4");
        testResult.setNodeResults(null);
        testResult = testResultRepo.save(testResult);

        // Create external node result
        TestNodeResult nodeResult = new TestNodeResult();
        nodeResult.setTestResultId(testResult.getId());
        nodeResult.setSequence(0);
        nodeResult.setNodeId("node-1");
        nodeResult.setNodeName("Test Node");
        nodeResult.setStatus(TestNodeResult.Status.SUCCESS);
        testNodeResultRepo.save(nodeResult);

        // Load node results first time
        testResultLoader.loadNodeResults(testResult);
        List<TestNodeResult> firstLoad = testResult.getNodeResults();
        assertEquals(1, firstLoad.size());

        // Add another node result to external storage
        TestNodeResult nodeResult2 = new TestNodeResult();
        nodeResult2.setTestResultId(testResult.getId());
        nodeResult2.setSequence(1);
        nodeResult2.setNodeId("node-2");
        nodeResult2.setNodeName("Test Node 2");
        nodeResult2.setStatus(TestNodeResult.Status.SUCCESS);
        testNodeResultRepo.save(nodeResult2);

        // Load again - should NOT reload (idempotency check)
        testResultLoader.loadNodeResults(testResult);
        List<TestNodeResult> secondLoad = testResult.getNodeResults();

        // Should still have 1 result (not reloaded)
        assertEquals(1, secondLoad.size());
    }

    @Test
    public void testLoadNodeResults_MultipleTestResults() {
        // Create multiple TestResults
        TestResult testResult1 = new TestResult();
        testResult1.setStatus(PipelineTestStatus.success);
        testResult1.setPipelineTestId("test-pipeline-5");
        testResult1.setNodeResults(null);
        testResult1 = testResultRepo.save(testResult1);

        TestResult testResult2 = new TestResult();
        testResult2.setStatus(PipelineTestStatus.success);
        testResult2.setPipelineTestId("test-pipeline-6");
        testResult2.setNodeResults(null);
        testResult2 = testResultRepo.save(testResult2);

        // Create external node results for both
        TestNodeResult nodeResult1 = new TestNodeResult();
        nodeResult1.setTestResultId(testResult1.getId());
        nodeResult1.setSequence(0);
        nodeResult1.setNodeId("node-1-1");
        nodeResult1.setNodeName("Test Node 1-1");
        nodeResult1.setStatus(TestNodeResult.Status.SUCCESS);

        TestNodeResult nodeResult2 = new TestNodeResult();
        nodeResult2.setTestResultId(testResult2.getId());
        nodeResult2.setSequence(0);
        nodeResult2.setNodeId("node-2-1");
        nodeResult2.setNodeName("Test Node 2-1");
        nodeResult2.setStatus(TestNodeResult.Status.SUCCESS);

        testNodeResultRepo.saveAll(Arrays.asList(nodeResult1, nodeResult2));

        // Load multiple test results
        List<TestResult> testResults = Arrays.asList(testResult1, testResult2);
        testResultLoader.loadNodeResults(testResults);

        // Verify both loaded correctly
        assertEquals(1, testResult1.getNodeResults().size());
        assertEquals("node-1-1", testResult1.getNodeResults().get(0).getNodeId());

        assertEquals(1, testResult2.getNodeResults().size());
        assertEquals("node-2-1", testResult2.getNodeResults().get(0).getNodeId());
    }

    @Test
    public void testLoadNodeResult_SingleNodeById() {
        // Create a TestResult with external storage
        TestResult testResult = new TestResult();
        testResult.setStatus(PipelineTestStatus.success);
        testResult.setPipelineTestId("test-pipeline-7");
        testResult.setNodeResults(null);
        testResult = testResultRepo.save(testResult);

        // Create external node results
        TestNodeResult nodeResult1 = new TestNodeResult();
        nodeResult1.setTestResultId(testResult.getId());
        nodeResult1.setSequence(0);
        nodeResult1.setNodeId("target-node");
        nodeResult1.setNodeName("Target Node");
        nodeResult1.setStatus(TestNodeResult.Status.SUCCESS);

        TestNodeResult nodeResult2 = new TestNodeResult();
        nodeResult2.setTestResultId(testResult.getId());
        nodeResult2.setSequence(1);
        nodeResult2.setNodeId("other-node");
        nodeResult2.setNodeName("Other Node");
        nodeResult2.setStatus(TestNodeResult.Status.SUCCESS);

        testNodeResultRepo.saveAll(Arrays.asList(nodeResult1, nodeResult2));

        // Load specific node result by ID
        TestNodeResult loadedNode = testResultLoader.loadNodeResult(testResult, "target-node");

        // Verify correct node loaded
        assertNotNull(loadedNode);
        assertEquals("target-node", loadedNode.getNodeId());
        assertEquals("Target Node", loadedNode.getNodeName());
    }
}
