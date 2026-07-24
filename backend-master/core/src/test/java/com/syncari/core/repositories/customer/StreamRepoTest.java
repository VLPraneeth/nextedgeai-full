package com.syncari.core.repositories.customer;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.SyncStream;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StreamRepoTest extends AbstractSyncariTest {
    @Autowired
    private StreamRepo streamRepo;

    @After
    public void tearDown(){
        resetRepos(streamRepo);
    }

    @Test
    public void basicTests() {

        SyncStream newStream = streamRepo.save(new SyncStream()
                .setGraphId("myEntity1")
                .setStatus(SyncStream.Status.NEW));
        SyncStream activeStream = streamRepo.save(new SyncStream()
                .setGraphId("myEntity2")
                .setStatus(SyncStream.Status.READY));

        SyncStream inactiveStream = streamRepo.save(new SyncStream()
                .setGraphId("myEntity3")
                .setStatus(SyncStream.Status.INACTIVE));

        SyncStream claimed = streamRepo.save(new SyncStream()
                .setGraphId("myEntity4")
                .setStatus(SyncStream.Status.CLAIMED)
                .setProcessorId("processor1"));


        SyncStream inProgress = streamRepo.save(new SyncStream()
                .setGraphId("myEntity5")
                .setStatus(SyncStream.Status.RUNNING)
                .setProcessorId("processor1")
                .setCheckin(Instant.now().minusSeconds(30)));


        List<SyncStream> newStreams = streamRepo.findByStatus(SyncStream.Status.NEW, Pageable.unpaged()).getContent();
        assertEquals(newStream, newStreams.get(0));
        assertEquals(1, newStreams.size());

        assertEquals(2, streamRepo.findByProcessorId("processor1").size());

        Optional<SyncStream> updated = streamRepo.changeStatus(claimed.getId(), "processor1", SyncStream.Status.READY, SyncStream.Status.CLAIMED);
        assertTrue(updated.isEmpty());
        updated = streamRepo.changeStatus(claimed.getId(), "processor1", SyncStream.Status.CLAIMED, SyncStream.Status.RUNNING);
        assertEquals(SyncStream.Status.RUNNING, updated.get().getStatus());
        long relinquished = streamRepo.relinquish("processor1", List.of(updated.get().getId()));
        assertEquals(1, relinquished);
        assertEquals(SyncStream.Status.READY, streamRepo.findById(claimed.getId()).get().getStatus());

        assertEquals(1, streamRepo.orphans(10000).size());

    }


}
