package com.syncari.core.pipeline;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.TestResult;
import com.syncari.core.model.misc.PipelineTestStatus;
import com.syncari.core.model.misc.test.TestNodeResult;
import com.syncari.core.repositories.customer.TestNodeResultRepo;
import com.syncari.core.repositories.customer.TestResultRepo;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.Assert.*;

public class TestResultProcessorIntegrationTest extends AbstractSyncariTest {

    @Autowired
    private TestResultProcessor testResultProcessor;

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
    public void testSaveTestResult_NodeResultsSavedExternally() {
        // Create a TestResult with node results
        TestResult testResult = new TestResult();
        testResult.setStatus(PipelineTestStatus.success);
        testResult.setPipelineTestId("test-pipeline-1");

        // Add node results using the public API
        TestNodeResult nodeResult1 = new TestNodeResult();
        nodeResult1.setNodeId("node-1");
        nodeResult1.setNodeName("Test Node 1");
        nodeResult1.setStatus(TestNodeResult.Status.SUCCESS);
        testResult.addNodeResult(nodeResult1);

        TestNodeResult nodeResult2 = new TestNodeResult();
        nodeResult2.setNodeId("node-2");
        nodeResult2.setNodeName("Test Node 2");
        nodeResult2.setStatus(TestNodeResult.Status.SUCCESS);
        testResult.addNodeResult(nodeResult2);

        // Use reflection to call private saveTestResult method
        try {
            ReflectionTestUtils.invokeMethod(testResultProcessor, "saveTestResult", testResult);
        } catch (Exception e) {
            fail("Failed to invoke saveTestResult: " + e.getMessage());
        }

        // Verify external node results were saved
        List<TestNodeResult> externalResults = testNodeResultRepo.findByTestResultIdOrderBySequenceAsc(testResult.getId());
        assertNotNull(externalResults);
        assertEquals(2, externalResults.size());
        assertEquals("node-1", externalResults.get(0).getNodeId());
        assertEquals("node-2", externalResults.get(1).getNodeId());
    }

    @Test
    public void testSaveTestResult_NodeResultsNotEmbedded() {
        // Create a TestResult with node results
        TestResult testResult = new TestResult();
        testResult.setStatus(PipelineTestStatus.success);
        testResult.setPipelineTestId("test-pipeline-2");

        // Add node results
        TestNodeResult nodeResult = new TestNodeResult();
        nodeResult.setNodeId("node-1");
        nodeResult.setNodeName("Test Node");
        nodeResult.setStatus(TestNodeResult.Status.SUCCESS);
        testResult.addNodeResult(nodeResult);

        // Use reflection to call private saveTestResult method
        try {
            ReflectionTestUtils.invokeMethod(testResultProcessor, "saveTestResult", testResult);
        } catch (Exception e) {
            fail("Failed to invoke saveTestResult: " + e.getMessage());
        }

        // Reload TestResult from database
        TestResult savedTestResult = testResultRepo.findById(testResult.getId()).orElse(null);
        assertNotNull(savedTestResult);

        // Verify nodeResults field is null (using external storage)
        // Access the field directly via reflection to bypass the getter logic
        List<TestNodeResult> embeddedNodeResults = (List<TestNodeResult>)
            ReflectionTestUtils.getField(savedTestResult, "nodeResults");
        assertNull("nodeResults field should be null for external storage", embeddedNodeResults);
    }

    @Test
    public void testSaveTestResult_NodeResultsOrderPreserved() {
        // Create a TestResult with multiple node results
        TestResult testResult = new TestResult();
        testResult.setStatus(PipelineTestStatus.success);
        testResult.setPipelineTestId("test-pipeline-3");

        // Add node results in specific order
        for (int i = 0; i < 5; i++) {
            TestNodeResult nodeResult = new TestNodeResult();
            nodeResult.setNodeId("node-" + i);
            nodeResult.setNodeName("Test Node " + i);
            nodeResult.setStatus(TestNodeResult.Status.SUCCESS);
            testResult.addNodeResult(nodeResult);
        }

        // Use reflection to call private saveTestResult method
        try {
            ReflectionTestUtils.invokeMethod(testResultProcessor, "saveTestResult", testResult);
        } catch (Exception e) {
            fail("Failed to invoke saveTestResult: " + e.getMessage());
        }

        // Verify order is preserved via sequence field
        List<TestNodeResult> externalResults = testNodeResultRepo.findByTestResultIdOrderBySequenceAsc(testResult.getId());
        assertEquals(5, externalResults.size());

        for (int i = 0; i < 5; i++) {
            assertEquals("node-" + i, externalResults.get(i).getNodeId());
            assertEquals(Integer.valueOf(i), externalResults.get(i).getSequence());
        }
    }

    @Test
    public void testSaveTestResult_TestResultIdLinked() {
        // Create a TestResult with node results
        TestResult testResult = new TestResult();
        testResult.setStatus(PipelineTestStatus.success);
        testResult.setPipelineTestId("test-pipeline-4");

        // Add node results
        TestNodeResult nodeResult1 = new TestNodeResult();
        nodeResult1.setNodeId("node-1");
        nodeResult1.setNodeName("Test Node 1");
        nodeResult1.setStatus(TestNodeResult.Status.SUCCESS);
        testResult.addNodeResult(nodeResult1);

        TestNodeResult nodeResult2 = new TestNodeResult();
        nodeResult2.setNodeId("node-2");
        nodeResult2.setNodeName("Test Node 2");
        nodeResult2.setStatus(TestNodeResult.Status.FAILED);
        testResult.addNodeResult(nodeResult2);

        // Use reflection to call private saveTestResult method
        try {
            ReflectionTestUtils.invokeMethod(testResultProcessor, "saveTestResult", testResult);
        } catch (Exception e) {
            fail("Failed to invoke saveTestResult: " + e.getMessage());
        }

        // Verify testResultId foreign key is set correctly
        List<TestNodeResult> externalResults = testNodeResultRepo.findByTestResultIdOrderBySequenceAsc(testResult.getId());
        assertEquals(2, externalResults.size());

        for (TestNodeResult result : externalResults) {
            assertEquals(testResult.getId(), result.getTestResultId());
        }
    }

    @Test
    public void testSaveTestResult_MultipleNodeResults() {
        // Create a TestResult with many node results to test scalability
        TestResult testResult = new TestResult();
        testResult.setStatus(PipelineTestStatus.success);
        testResult.setPipelineTestId("test-pipeline-5");

        // Add 50 node results (simulating large test scenarios)
        for (int i = 0; i < 50; i++) {
            TestNodeResult nodeResult = new TestNodeResult();
            nodeResult.setNodeId("node-" + i);
            nodeResult.setNodeName("Test Node " + i);
            nodeResult.setStatus(i % 3 == 0 ? TestNodeResult.Status.FAILED : TestNodeResult.Status.SUCCESS);
            testResult.addNodeResult(nodeResult);
        }

        // Use reflection to call private saveTestResult method
        try {
            ReflectionTestUtils.invokeMethod(testResultProcessor, "saveTestResult", testResult);
        } catch (Exception e) {
            fail("Failed to invoke saveTestResult: " + e.getMessage());
        }

        // Verify all node results were saved
        List<TestNodeResult> externalResults = testNodeResultRepo.findByTestResultIdOrderBySequenceAsc(testResult.getId());
        assertEquals(50, externalResults.size());

        // Verify count method
        long count = testNodeResultRepo.countByTestResultId(testResult.getId());
        assertEquals(50L, count);

        // Verify ordering
        for (int i = 0; i < 50; i++) {
            assertEquals("node-" + i, externalResults.get(i).getNodeId());
            assertEquals(Integer.valueOf(i), externalResults.get(i).getSequence());
        }
    }
}
