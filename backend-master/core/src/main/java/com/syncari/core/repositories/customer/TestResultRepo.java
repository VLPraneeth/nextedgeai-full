package com.syncari.core.repositories.customer;

import com.syncari.core.model.TestResult;
import com.syncari.core.repositories.SyncariRepo;

import java.util.List;

public interface TestResultRepo extends SyncariRepo<TestResult> {

    public List<TestResult> findBySimulationRunId(String simulationRunId);

    public List<TestResult> findByPipelineTestId(String pipelineTestId);
}
