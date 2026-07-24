package com.syncari.core.repositories.customer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.PipelineTest;
import com.syncari.core.model.util.Status;

import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class PipelineTestRepoTest extends AbstractSyncariTest {
    @Autowired
    private PipelineTestRepo pipelineTestRepo;

    @After
    public void tearDown(){
        resetRepos(pipelineTestRepo);
    }

    @Test
    public void basicTests() {
        String graph1 = "myEntity1::" + System.currentTimeMillis();
        String graph2 = "myEntity2::" + System.currentTimeMillis();

        PipelineTest newPipelineTest = pipelineTestRepo.save(new PipelineTest()
                .setGraphId(graph1)
                .setStatus(Status.NEW));

        PipelineTest processingPipelineTest = pipelineTestRepo.save(new PipelineTest()
                .setGraphId(graph2)
                .setStatus(Status.PROCESSING)
                .setProcessorId("processor1")
                .setCheckin(Instant.now().minusSeconds(30)));

        List<PipelineTest> tests = pipelineTestRepo.findByGraphIdAndStatusIn(graph1, List.of(Status.NEW));
        assertEquals(newPipelineTest, tests.get(0));
        assertEquals(1, tests.size());

        tests = pipelineTestRepo.findByGraphIdInAndStatusIn(List.of(graph1), List.of(Status.PROCESSING));
        assertTrue(tests.isEmpty());
        tests = pipelineTestRepo.findByGraphIdInAndStatusIn(List.of(graph1, graph2), List.of(Status.PROCESSING));
        assertEquals(Status.PROCESSING, tests.get(0).getStatus());
        assertEquals(1, tests.size());

        // process and finish
        Optional<PipelineTest> updated = pipelineTestRepo.process(newPipelineTest.getId(), PipelineTest.class);
        assertTrue(updated.isPresent());
        assertEquals(Status.PROCESSING, updated.get().getStatus());
        // process again should fail.
        Optional<PipelineTest> processing = pipelineTestRepo.process(updated.get().getId(), PipelineTest.class);
        assertFalse(processing.isPresent());

        updated = pipelineTestRepo.finish(updated.get().getId(), PipelineTest.class);
        assertEquals(Status.COMPLETED, updated.get().getStatus());
        // process again on finished should fail.
        Optional<PipelineTest> updated2 = pipelineTestRepo.process(updated.get().getId(), PipelineTest.class);
        assertFalse(updated2.isPresent());
        // finish again on finished should fail.
        Optional<PipelineTest> completed2 = pipelineTestRepo.finish(updated.get().getId(), PipelineTest.class);
        assertFalse(completed2.isPresent());
        // finishWithError on finished should fail.
        Optional<PipelineTest> completed3 = pipelineTestRepo.process(updated.get().getId(), PipelineTest.class);
        assertFalse(completed3.isPresent());
        
        assertTrue(processingPipelineTest.getCheckin().toEpochMilli() < Instant.now().minusSeconds(30).toEpochMilli());
        updated = pipelineTestRepo.checkin(processingPipelineTest.getId(), PipelineTest.class);
        assertTrue(updated.get().getCheckin().toEpochMilli() > Instant.now().minusSeconds(30).toEpochMilli());

        List<PipelineTest> stuckTests = pipelineTestRepo.getStuck(120000, PipelineTest.class);
        assertEquals(0, stuckTests.size());
        processingPipelineTest.setCheckin(Instant.now().minusSeconds(121));
        pipelineTestRepo.save(processingPipelineTest);
        stuckTests = pipelineTestRepo.getStuck(120000, PipelineTest.class);
        assertEquals(1, stuckTests.size());

        pipelineTestRepo.deleteById(processingPipelineTest.getId());
        tests = pipelineTestRepo.findByGraphIdAndStatusIn(graph2, List.of(Status.PROCESSING));
        assertEquals(0, tests.size());

        Optional<PipelineTest> claimed3 = pipelineTestRepo.process(newPipelineTest.getId(), PipelineTest.class);
        Optional<PipelineTest> modified = pipelineTestRepo.checkin(newPipelineTest.getId(), PipelineTest.class);
        // This will be 0, becuase the claim would not process, the earlier relinquish would set it to INACTIVE status.
        assertFalse(modified.isPresent());

        newPipelineTest = pipelineTestRepo.save(new PipelineTest()
                .setGraphId(graph1)
                .setStatus(Status.NEW));

        Optional<PipelineTest> claimed4 = pipelineTestRepo.process(newPipelineTest.getId(), PipelineTest.class);
        modified = pipelineTestRepo.checkin(newPipelineTest.getId(), PipelineTest.class);
        // We deleted the processingPipelineTest test case, so just one that was processed is expected.
        assertTrue(modified.isPresent());

        Optional<PipelineTest> finishedWithError = pipelineTestRepo.finishWithError(newPipelineTest.getId(), "Dummy ErrorMsg", PipelineTest.class);
        assertTrue(finishedWithError.isPresent());
        assertEquals(Status.ERROR, finishedWithError.get().getStatus());
        // process again on finishedWithError should fail.
        finishedWithError = pipelineTestRepo.process(updated.get().getId(), PipelineTest.class);
        assertFalse(finishedWithError.isPresent());
        // finishWithError again on finishedWithError should fail.
        finishedWithError = pipelineTestRepo.finishWithError(updated.get().getId(), "Dummy ErrorMsg", PipelineTest.class);
        assertFalse(finishedWithError.isPresent());
        // finishWithError on finishedWithError should fail.
        finishedWithError = pipelineTestRepo.process(updated.get().getId(), PipelineTest.class);
        assertFalse(finishedWithError.isPresent());

        // No NEW, No PROCESSING
        tests = pipelineTestRepo.findByGraphIdAndStatusIn(graph1, List.of(Status.NEW));
        assertEquals(0, tests.size());
        tests = pipelineTestRepo.findByGraphIdAndStatusIn(graph1, List.of(Status.PROCESSING));
        assertEquals(0, tests.size());

        processingPipelineTest = pipelineTestRepo.save(new PipelineTest()
                .setGraphId(graph2)
                .setStatus(Status.PROCESSING)
                .setProcessorId("processor1")
                .setCheckin(Instant.now().minusSeconds(121)));

        stuckTests = pipelineTestRepo.getStuck(120000, PipelineTest.class);
        assertEquals(1, stuckTests.size());
        assertEquals(Status.PROCESSING, stuckTests.get(0).getStatus());
        pipelineTestRepo.clearTheDead(stuckTests.get(0).getId(), "Dummy ErrorMsg", PipelineTest.class);

        tests = pipelineTestRepo.findByGraphIdAndStatusIn(graph1, List.of(Status.ERROR));
        assertTrue(tests.size() > 0);
    }
}

