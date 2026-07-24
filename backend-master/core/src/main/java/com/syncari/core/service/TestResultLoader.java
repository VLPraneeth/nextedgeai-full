package com.syncari.core.service;

import com.syncari.core.model.TestResult;
import com.syncari.core.model.misc.test.TestNodeResult;
import com.syncari.core.repositories.customer.TestNodeResultRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class TestResultLoader {

    @Autowired
    private TestNodeResultRepo testNodeResultRepo;

    /**
     * Load node results for a TestResult - handles both embedded and external storage.
     * Storage mode is inferred from nodeResults field:
     * - If nodeResults is null/empty: data is external (new documents)
     * - If nodeResults has data: data is embedded (old documents)
     */
    public void loadNodeResults(TestResult testResult) {
        if (testResult == null) {
            return;
        }

        // If already loaded in working list, skip
        if (testResult.getWorkingNodeResults() != null && !testResult.getWorkingNodeResults().isEmpty()) {
            return;
        }

        // Check if using embedded storage (backward compatibility)
        List<TestNodeResult> embeddedResults = testResult.getNodeResults();
        if (embeddedResults != null && !embeddedResults.isEmpty()) {
            // Old documents with embedded nodeResults
            testResult.setWorkingNodeResults(new ArrayList<>(embeddedResults));
            log.debug("Loaded {} embedded node results for TestResult {}",
                     embeddedResults.size(), testResult.getId());
        } else {
            // New documents with external storage (nodeResults is null)
            List<TestNodeResult> nodeResults =
                    testNodeResultRepo.findByTestResultIdOrderBySequenceAsc(testResult.getId());

            testResult.setWorkingNodeResults(nodeResults);

            log.debug("Loaded {} externalized node results for TestResult {}",
                     nodeResults.size(), testResult.getId());
        }
    }

    /**
     * Load node results for multiple TestResults.
     */
    public void loadNodeResults(List<TestResult> testResults) {
        if (testResults == null || testResults.isEmpty()) {
            return;
        }
        testResults.forEach(this::loadNodeResults);
    }

    /**
     * Load only specific node result by nodeId (efficient for single node lookup).
     */
    public TestNodeResult loadNodeResult(TestResult testResult, String nodeId) {
        loadNodeResults(testResult);
        return testResult.findNodeResult(nodeId).orElse(null);
    }
}
