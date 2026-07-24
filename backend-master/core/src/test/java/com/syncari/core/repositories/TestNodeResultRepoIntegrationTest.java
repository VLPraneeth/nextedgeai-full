package com.syncari.core.repositories;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.TestResult;
import com.syncari.core.model.misc.PipelineTestStatus;
import com.syncari.core.model.misc.test.TestNodeResult;
import com.syncari.core.model.misc.test.TestNodeResultAttributeValue;
import com.syncari.core.repositories.customer.TestNodeResultRepo;
import com.syncari.core.repositories.customer.TestResultRepo;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class TestNodeResultRepoIntegrationTest extends AbstractSyncariTest {

    @Autowired
    private TestNodeResultRepo testNodeResultRepo;

    @Autowired
    private TestResultRepo testResultRepo;

    @After
    @Override
    public void tearDown() {
        testNodeResultRepo.deleteAll();
        testResultRepo.deleteAll();
        super.tearDown();
    }

    @Test
    public void testFindByTestResultIdOrderBySequenceAsc() {
        // Create a TestResult
        TestResult testResult = new TestResult();
        testResult.setStatus(PipelineTestStatus.success);
        testResult.setPipelineTestId("test-pipeline-1");
        testResult = testResultRepo.save(testResult);

        // Create node results with specific sequence order (insert out of order)
        TestNodeResult nodeResult3 = new TestNodeResult();
        nodeResult3.setTestResultId(testResult.getId());
        nodeResult3.setSequence(2);
        nodeResult3.setNodeId("node-3");
        nodeResult3.setNodeName("Third Node");
        nodeResult3.setStatus(TestNodeResult.Status.SUCCESS);

        TestNodeResult nodeResult1 = new TestNodeResult();
        nodeResult1.setTestResultId(testResult.getId());
        nodeResult1.setSequence(0);
        nodeResult1.setNodeId("node-1");
        nodeResult1.setNodeName("First Node");
        nodeResult1.setStatus(TestNodeResult.Status.SUCCESS);

        TestNodeResult nodeResult2 = new TestNodeResult();
        nodeResult2.setTestResultId(testResult.getId());
        nodeResult2.setSequence(1);
        nodeResult2.setNodeId("node-2");
        nodeResult2.setNodeName("Second Node");
        nodeResult2.setStatus(TestNodeResult.Status.FAILED);

        // Save in random order
        testNodeResultRepo.saveAll(Arrays.asList(nodeResult3, nodeResult1, nodeResult2));

        // Retrieve ordered by sequence
        List<TestNodeResult> orderedResults = testNodeResultRepo.findByTestResultIdOrderBySequenceAsc(testResult.getId());

        // Verify correct ordering
        assertEquals(3, orderedResults.size());
        assertEquals("node-1", orderedResults.get(0).getNodeId());
        assertEquals("First Node", orderedResults.get(0).getNodeName());
        assertEquals(Integer.valueOf(0), orderedResults.get(0).getSequence());

        assertEquals("node-2", orderedResults.get(1).getNodeId());
        assertEquals("Second Node", orderedResults.get(1).getNodeName());
        assertEquals(Integer.valueOf(1), orderedResults.get(1).getSequence());

        assertEquals("node-3", orderedResults.get(2).getNodeId());
        assertEquals("Third Node", orderedResults.get(2).getNodeName());
        assertEquals(Integer.valueOf(2), orderedResults.get(2).getSequence());
    }

    @Test
    public void testDeleteByTestResultId() {
        // Create two TestResults
        TestResult testResult1 = new TestResult();
        testResult1.setStatus(PipelineTestStatus.success);
        testResult1.setPipelineTestId("test-pipeline-2");
        testResult1 = testResultRepo.save(testResult1);

        TestResult testResult2 = new TestResult();
        testResult2.setStatus(PipelineTestStatus.success);
        testResult2.setPipelineTestId("test-pipeline-3");
        testResult2 = testResultRepo.save(testResult2);

        // Create node results for both
        TestNodeResult nodeResult1_1 = new TestNodeResult();
        nodeResult1_1.setTestResultId(testResult1.getId());
        nodeResult1_1.setSequence(0);
        nodeResult1_1.setNodeId("node-1-1");
        nodeResult1_1.setNodeName("Test Node 1-1");
        nodeResult1_1.setStatus(TestNodeResult.Status.SUCCESS);

        TestNodeResult nodeResult1_2 = new TestNodeResult();
        nodeResult1_2.setTestResultId(testResult1.getId());
        nodeResult1_2.setSequence(1);
        nodeResult1_2.setNodeId("node-1-2");
        nodeResult1_2.setNodeName("Test Node 1-2");
        nodeResult1_2.setStatus(TestNodeResult.Status.SUCCESS);

        TestNodeResult nodeResult2_1 = new TestNodeResult();
        nodeResult2_1.setTestResultId(testResult2.getId());
        nodeResult2_1.setSequence(0);
        nodeResult2_1.setNodeId("node-2-1");
        nodeResult2_1.setNodeName("Test Node 2-1");
        nodeResult2_1.setStatus(TestNodeResult.Status.SUCCESS);

        testNodeResultRepo.saveAll(Arrays.asList(nodeResult1_1, nodeResult1_2, nodeResult2_1));

        // Verify initial state
        assertEquals(2, testNodeResultRepo.findByTestResultIdOrderBySequenceAsc(testResult1.getId()).size());
        assertEquals(1, testNodeResultRepo.findByTestResultIdOrderBySequenceAsc(testResult2.getId()).size());

        // Delete node results for testResult1
        testNodeResultRepo.deleteByTestResultId(testResult1.getId());

        // Verify deletion
        assertEquals(0, testNodeResultRepo.findByTestResultIdOrderBySequenceAsc(testResult1.getId()).size());
        assertEquals(1, testNodeResultRepo.findByTestResultIdOrderBySequenceAsc(testResult2.getId()).size());

        // Verify testResult2's node results are intact
        List<TestNodeResult> remainingResults = testNodeResultRepo.findByTestResultIdOrderBySequenceAsc(testResult2.getId());
        assertEquals("node-2-1", remainingResults.get(0).getNodeId());
    }

    @Test
    public void testCountByTestResultId() {
        // Create a TestResult
        TestResult testResult = new TestResult();
        testResult.setStatus(PipelineTestStatus.success);
        testResult.setPipelineTestId("test-pipeline-4");
        testResult = testResultRepo.save(testResult);

        // Initially no node results
        long initialCount = testNodeResultRepo.countByTestResultId(testResult.getId());
        assertEquals(0L, initialCount);

        // Add 10 node results
        for (int i = 0; i < 10; i++) {
            TestNodeResult nodeResult = new TestNodeResult();
            nodeResult.setTestResultId(testResult.getId());
            nodeResult.setSequence(i);
            nodeResult.setNodeId("node-" + i);
            nodeResult.setNodeName("Test Node " + i);
            nodeResult.setStatus(TestNodeResult.Status.SUCCESS);
            testNodeResultRepo.save(nodeResult);
        }

        // Verify count
        long count = testNodeResultRepo.countByTestResultId(testResult.getId());
        assertEquals(10L, count);

        // Add more node results
        for (int i = 10; i < 25; i++) {
            TestNodeResult nodeResult = new TestNodeResult();
            nodeResult.setTestResultId(testResult.getId());
            nodeResult.setSequence(i);
            nodeResult.setNodeId("node-" + i);
            nodeResult.setNodeName("Test Node " + i);
            nodeResult.setStatus(TestNodeResult.Status.SUCCESS);
            testNodeResultRepo.save(nodeResult);
        }

        // Verify updated count
        long updatedCount = testNodeResultRepo.countByTestResultId(testResult.getId());
        assertEquals(25L, updatedCount);
    }

    @Test
    public void testNodeResultPersistence() {
        // Create a TestResult
        TestResult testResult = new TestResult();
        testResult.setStatus(PipelineTestStatus.success);
        testResult.setPipelineTestId("test-pipeline-5");
        testResult = testResultRepo.save(testResult);

        // Create a comprehensive node result with all fields
        TestNodeResult nodeResult = new TestNodeResult();
        nodeResult.setTestResultId(testResult.getId());
        nodeResult.setSequence(0);
        nodeResult.setNodeId("comprehensive-node");
        nodeResult.setNodeName("Comprehensive Test Node");
        nodeResult.setStatus(TestNodeResult.Status.SUCCESS);

        // Add input attribute values
        nodeResult.addInput("firstName", new TestNodeResultAttributeValue("firstName", "First Name", "String", "John"));
        nodeResult.addInput("lastName", new TestNodeResultAttributeValue("lastName", "Last Name", "String", "Doe"));
        nodeResult.addInput("email", new TestNodeResultAttributeValue("email", "Email", "String", "john.doe@example.com"));

        // Add output attribute values
        nodeResult.addOutput("fullName", new TestNodeResultAttributeValue("fullName", "Full Name", "String", "John Doe"));
        nodeResult.addOutput("processedEmail", new TestNodeResultAttributeValue("processedEmail", "Processed Email", "String", "JOHN.DOE@EXAMPLE.COM"));

        // Save
        testNodeResultRepo.save(nodeResult);

        // Retrieve and verify all fields persisted correctly
        List<TestNodeResult> results = testNodeResultRepo.findByTestResultIdOrderBySequenceAsc(testResult.getId());
        assertEquals(1, results.size());

        TestNodeResult retrieved = results.get(0);
        assertEquals("comprehensive-node", retrieved.getNodeId());
        assertEquals("Comprehensive Test Node", retrieved.getNodeName());
        assertEquals(TestNodeResult.Status.SUCCESS, retrieved.getStatus());
        assertEquals(testResult.getId(), retrieved.getTestResultId());
        assertEquals(Integer.valueOf(0), retrieved.getSequence());

        // Verify input maps
        assertNotNull(retrieved.getInputs());
        assertEquals(3, retrieved.getInputs().size());
        assertEquals("John", retrieved.getInputs().get("firstName").getValue());
        assertEquals("Doe", retrieved.getInputs().get("lastName").getValue());
        assertEquals("john.doe@example.com", retrieved.getInputs().get("email").getValue());

        // Verify output maps
        assertNotNull(retrieved.getOutputs());
        assertEquals(2, retrieved.getOutputs().size());
        assertEquals("John Doe", retrieved.getOutputs().get("fullName").getValue());
        assertEquals("JOHN.DOE@EXAMPLE.COM", retrieved.getOutputs().get("processedEmail").getValue());

        // Verify it extends UUIDAuditModel (has ID and audit fields)
        assertNotNull(retrieved.getId());
        assertNotNull(retrieved.getCreatedAt());
    }
}
