package com.syncari.core.repositories.customer;

import com.syncari.core.model.misc.test.TestNodeResult;
import com.syncari.core.repositories.SyncariRepo;

import java.util.List;

public interface TestNodeResultRepo extends SyncariRepo<TestNodeResult> {

    /**
     * Find all node results for a test, ordered by sequence
     */
    List<TestNodeResult> findByTestResultIdOrderBySequenceAsc(String testResultId);

    /**
     * Delete all node results for a test
     */
    void deleteByTestResultId(String testResultId);

    /**
     * Count node results for a test
     */
    long countByTestResultId(String testResultId);
}
