package com.syncari.core.service;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.SyncariContext;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.PipelineException;
import com.syncari.core.model.ErrorCategory;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.PipelineSettings;
import com.syncari.core.model.SyncStream;
import com.syncari.core.model.misc.PipelineError;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.MappingGraphRepo;
import com.syncari.core.repositories.customer.StreamRepo;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

public class StreamServiceTest extends AbstractSyncariTest {
    @Autowired
    StreamService streamService;

    @Autowired
    StreamRepo streamRepo;
    @Autowired
    MappingGraphRepo graphRepo;

    public void tearDown() {
        resetRepos(streamRepo);
    }

    public void setUp() {
        super.setUp();
        //clean dangling streams from other tests!
        //TODO: Find which tests are not cleaning up
        resetRepos(streamRepo);
    }
    @Test
    public void isIdleWithoutCheckins(){
        SyncStream activeStream1 = streamRepo.save(new SyncStream()
                .setGraphId(ObjectId.get().toHexString())
                .setStatus(SyncStream.Status.PAUSING));
        assertFalse(streamService.isIdle(activeStream1.getId()));

    }

    @Test
    public void orphansAndStuckIgnoreRTPipelines() {
        MappingGraph standard = graphRepo.save(new MappingGraph().setName("Account Map")
                .setScope(Scope.ENTITY)
                .setTargetId("entityId"));
        MappingGraph standard2 = graphRepo.save(new MappingGraph().setName("Account Map Std2")
                .setScope(Scope.ENTITY)
                .setTargetId("standard2"));
        MappingGraph realtime1 = graphRepo.save(new MappingGraph().setName("Account Map1")
                .setScope(Scope.ENTITY)
                .setTargetId("entityId1")
                .setSettings(new PipelineSettings().setRealtimePipeline(true))
        );
        MappingGraph realtime2 = new MappingGraph().setName("Account Map2")
                .setScope(Scope.ENTITY)
                .setTargetId("entityId2")
                .setSettings(new PipelineSettings().setRealtimePipeline(true).setRealtimeEndpointSuffix("suffix1"));
        realtime2.setDraftStatus(DraftStatus.APPROVED);
        realtime2 = graphRepo.save(realtime2);
        SyncStream runningStream1 = streamRepo.save(new SyncStream()
                .setGraphId(standard.getId())
                .setCheckin(Instant.now().minus(300, ChronoUnit.SECONDS))
                .setStatus(SyncStream.Status.RUNNING));
        SyncStream stuckStream1 = streamRepo.save(new SyncStream()
                .setGraphId(standard2.getId())
                .setCheckin(Instant.now().minus(300, ChronoUnit.SECONDS))
                .setStatus(SyncStream.Status.CLAIMED));

        SyncStream runningStream2 = streamRepo.save(new SyncStream()
                .setGraphId(realtime1.getId())
                .setCheckin(Instant.now().minus(300, ChronoUnit.SECONDS))
                .setStatus(SyncStream.Status.RUNNING));

        SyncStream runningStream3 = streamRepo.save(new SyncStream()
                .setGraphId(realtime2.getId())
                .setCheckin(Instant.now().minus(300, ChronoUnit.SECONDS))
                .setStatus(SyncStream.Status.RUNNING)
                .setProcessorId("processor1"));
        final List<SyncStream> orphans = streamService.orphans(10 * 1000l);
        assertEquals(1, orphans.size());
        assertEquals(runningStream1.getId(), orphans.get(0).getId());

        final List<SyncStream> stuck = streamService.stuckStreams(10 * 1000l);
        assertEquals(2, stuck.size());
        assertEquals(Set.of(runningStream1.getId(), stuckStream1.getId()), stuck.stream().map(s -> s.getId()).collect(Collectors.toSet()));

    }


    @Test
    public void claimStreams() {
        SyncStream activeStream1 = streamRepo.save(new SyncStream()
                .setGraphId(ObjectId.get().toHexString())
                .setStatus(SyncStream.Status.READY));

        SyncStream activeStream2 = streamRepo.save(new SyncStream()
                .setGraphId(ObjectId.get().toHexString())
                .setStatus(SyncStream.Status.READY));

        SyncStream claimed = streamRepo.save(new SyncStream()
                .setGraphId(ObjectId.get().toHexString())
                .setStatus(SyncStream.Status.CLAIMED)
                .setProcessorId("processor1"));

        Instant lastCheckin = Instant.now().minusSeconds(30);
        SyncStream inProgress = streamRepo.save(new SyncStream()
                .setGraphId(ObjectId.get().toHexString())
                .setStatus(SyncStream.Status.RUNNING)
                .setProcessorId("processor1")
                .setCheckin(lastCheckin));

        SyncStream activeStream3 = streamRepo.save(new SyncStream()
                .setGraphId(ObjectId.get().toHexString())
                .setProcessorId("processor2")
                .setStatus(SyncStream.Status.READY));

        assertEquals(1, streamRepo.findByStatus(SyncStream.Status.CLAIMED, Pageable.unpaged()).getTotalElements());

        assertEquals(1, streamService.readyFor("processor2").size());
        streamService.claim("processor1", 1);
        assertEquals(2, streamRepo.findByStatus(SyncStream.Status.CLAIMED, Pageable.unpaged()).getTotalElements());
        streamService.claim("processor1", 1);
        assertEquals(3, streamRepo.findByStatus(SyncStream.Status.CLAIMED, Pageable.unpaged()).getTotalElements());
        streamService.relinquish("processor1", List.of(claimed.getId(), activeStream1.getId(), activeStream2.getId()));
        assertEquals(0, streamRepo.findByStatus(SyncStream.Status.CLAIMED, Pageable.unpaged()).getTotalElements());
        assertEquals(4, streamRepo.findByStatus(SyncStream.Status.READY, Pageable.unpaged()).getTotalElements());
        var orphans = streamService.orphans(10 * 1000);
        assertEquals(1, orphans.size());
        assertEquals(inProgress.getId(), orphans.get(0).getId());
        boolean checkedIn = streamService.checkin("processor1", inProgress.getId());
        assertTrue(checkedIn);
        orphans = streamService.orphans(10 * 1000);
        assertEquals(0, orphans.size());
        SyncStream inProgressCheckedIn = streamRepo.findById(inProgress.getId()).get();
        assertTrue(lastCheckin.isBefore(inProgressCheckedIn.getCheckin()));
        assertTrue(inProgressCheckedIn.getCheckin().minusMillis(lastCheckin.toEpochMilli()).toEpochMilli() > 30000);
        streamService.relinquish("processor1", List.of(inProgress.getId()));
        assertEquals(5, streamRepo.findByStatus(SyncStream.Status.READY, Pageable.unpaged()).getTotalElements());

        List<SyncStream> processor1 = streamService.claim("processor1", 5);
        assertEquals(5,processor1.size());
        List<SyncStream> processor2 = streamService.claim("processor2", 5);
        assertEquals(0,processor2.size());
    }


    @Test
    public void staleStreamClaimedBySingleProcessor() {
        Instant lastCheckin = Instant.now().minus(1, ChronoUnit.HOURS);
        SyncStream staleRunningStream1 = streamRepo.save(new SyncStream()
                .setGraphId(ObjectId.get().toHexString())
                .setCheckin(lastCheckin)
                .setStatus(SyncStream.Status.RUNNING));
        SyncStream staleRunningStream2 = streamRepo.save(new SyncStream()
                .setGraphId(ObjectId.get().toHexString())
                .setCheckin(lastCheckin)
                .setStatus(SyncStream.Status.RUNNING));


        List<SyncStream> processor1 = streamService.claim("processor1", 5);
        assertEquals(2,processor1.size());
        List<SyncStream> processor2 = streamService.claim("processor2", 5);
        assertEquals(0,processor2.size());
    }

    @Test
    public void pausedBySet() {
        Instant lastCheckin = Instant.now().minus(1, ChronoUnit.HOURS);
        SyncStream stream = streamRepo.save(new SyncStream()
                .setGraphId(ObjectId.get().toHexString())
                .setCheckin(lastCheckin)
                .setStatus(SyncStream.Status.RUNNING));

        boolean paused = streamService.issuePause(stream.getGraphId());
        Optional<SyncStream> byId = streamRepo.findById(stream.getId());
        assertEquals("test@email.com", byId.get().getPausedBy());
    }

    @Test
    public void orphansClaimed() {
        SyncStream activeStream1 = streamRepo.save(new SyncStream()
                .setGraphId(ObjectId.get().toHexString())
                .setStatus(SyncStream.Status.READY));

        Instant lastCheckin = Instant.now().minusSeconds(30 * 60 * 1000);//30 minutes back
        SyncStream inProgress = streamRepo.save(new SyncStream()
                .setGraphId(ObjectId.get().toHexString())
                .setStatus(SyncStream.Status.RUNNING)
                .setProcessorId("processor1")
                .setCheckin(lastCheckin));
        var orphans = streamService.orphans(StreamService.MAX_ALLOWED_CHECKIN_INTERVAL_MS);
        assertEquals(1, orphans.size());
        //Orphaned by processor1
        assertEquals("processor1", orphans.get(0).getProcessorId());
        List<SyncStream> claimed = streamService.claim("processor2", 5);
        assertEquals(2, claimed.size());
        assertEquals("processor2", claimed.get(0).getProcessorId());
        SyncStream claimedOrphan = claimed.get(1);
        assertEquals("processor2", claimedOrphan.getProcessorId());
        //orphan now claimed by processor 2
        assertEquals(orphans.get(0).getId(), claimedOrphan.getId());

    }

    @Test
    public void resolveStreamRetry() {
        SyncStream activeStream1 = streamRepo.save(new SyncStream()
                .setGraphId(ObjectId.get().toHexString())
                .setErrorDetail(new PipelineError().setStartTime(Instant.now()).setStatus(Status.ACTIVE))
                .setStatus(SyncStream.Status.READY));

        activeStream1 = streamService.resolveStreamRetry(activeStream1);
        assertFalse(activeStream1.getErrorDetail().isActive());
        assertNull(activeStream1.getErrorDetail().getEndTime());
        assertNull(activeStream1.getErrorDetail().getStartTime());
        assertEquals(0, activeStream1.getErrorDetail().getCount());

        activeStream1.setErrorDetail(null);
        activeStream1 = streamService.resolveStreamRetry(activeStream1);

        //test resolved within 3 hours
        activeStream1 = streamRepo.save(new SyncStream()
                .setGraphId(ObjectId.get().toHexString())
                .setErrorDetail(new PipelineError().setStartTime(Instant.now()).setStatus(Status.ACTIVE))
                .setStatus(SyncStream.Status.READY));
        assertTrue(activeStream1.getErrorDetail().resolvedWithinThreshold(Instant.now()));
        assertFalse(activeStream1.getErrorDetail().resolvedWithinThreshold(Instant.now().plus(4, ChronoUnit.HOURS)));
        assertFalse(activeStream1.getErrorDetail().resolvedWithinThreshold(null));
        activeStream1.getErrorDetail().setStartTime(null);
        assertFalse(activeStream1.getErrorDetail().resolvedWithinThreshold(Instant.now()));
    }

    @Test
    public void initiateStreamRetry() {
        SyncStream activeStream1 = streamRepo.save(new SyncStream()
                .setGraphId(ObjectId.get().toHexString())
                .setStatus(SyncStream.Status.READY));

        activeStream1 = streamService.initiateStreamRetry(activeStream1, new PipelineException(new RuntimeException("Nullpointer exception")), ErrorCategory.PIPELINE);
        assertTrue(activeStream1.getErrorDetail().isActive());
        assertNotNull(activeStream1.getErrorDetail().getStartTime());
        assertEquals("Nullpointer exception", activeStream1.getErrorDetail().getMessage());
    }

    @Test
    public void concurrentClaimsDontStepOverEachOther_WhenStreamsMoreThanProcessors() {

        List<SyncStream> streams =
                IntStream.range(0, 100).mapToObj(i -> streamRepo.save(new SyncStream().setGraphId("graph" + i).setStatus(SyncStream.Status.READY)))
                        .collect(Collectors.toList());
        var user = SyncariContext.getUser();
        var org = SyncariContext.getOrganziation();
        var instance = SyncariContext.getInstance();

        List<Thread> processors = IntStream.range(0, 5).mapToObj(i -> new Thread(() -> {
            SyncariContext.setUser(user);
            SyncariContext.setInstance(instance);
            SyncariContext.setOrganziation(org);
            System.out.println("Processor Thread# "+Thread.currentThread().getName());
            streamService.claim("Processor" + i, 20);
            SyncariContext.resetAll();
        }
        )).collect(Collectors.toList());
        processors.parallelStream().forEach(t -> t.start());
        processors.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        SyncariContext.setUser(user);
        SyncariContext.setInstance(instance);
        SyncariContext.setOrganziation(org);
        Page<SyncStream> allClaimed = streamRepo.findByStatus(SyncStream.Status.CLAIMED, Pageable.unpaged());
        //All 100 are claimed now
        assertEquals(100, allClaimed.getTotalElements());
        //Each processor has 20, its max
        IntStream.range(0, 5).forEach(i->{
            var claimedByProcessor = streamRepo.findByProcessorIdAndStatus("Processor"+i,SyncStream.Status.CLAIMED);
            assertEquals(20, claimedByProcessor.size());
        });

    }

    @Test
    public void concurrentClaimedOrphans() {

        SyncStream claimed = streamRepo.save(new SyncStream()
                .setGraphId(ObjectId.get().toHexString())
                .setStatus(SyncStream.Status.CLAIMED));

        Instant lastCheckin = Instant.now().minusSeconds(30 * 60 * 1000);//30 minutes back
        claimed.setCheckin(lastCheckin);
        streamRepo.save(claimed);

        var user = SyncariContext.getUser();
        var org = SyncariContext.getOrganziation();
        var instance = SyncariContext.getInstance();

        final AtomicInteger count = new AtomicInteger(0);

        List<Thread> processors = IntStream.range(0, 5).mapToObj(i -> new Thread(() -> {
            SyncariContext.setUser(user);
            SyncariContext.setInstance(instance);
            SyncariContext.setOrganziation(org);
            System.out.println("Processor Thread# "+Thread.currentThread().getName());
            count.addAndGet(streamService.claim("Processor" + i, 1).size());
            SyncariContext.resetAll();
        }
        )).collect(Collectors.toList());
        processors.parallelStream().forEach(t -> t.start());
        processors.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        SyncariContext.setUser(user);
        SyncariContext.setInstance(instance);
        SyncariContext.setOrganziation(org);
        List<SyncStream> allClaimed = streamRepo.findByStatusIn(List.of(SyncStream.Status.CLAIMED));
        assertTrue(allClaimed.size() == 1);
        assertTrue(count.get() == 1);
    }


    @Test
    public void isIdle(){
        SyncStream stream = streamRepo.save(new SyncStream()
                .setGraphId(ObjectId.get().toHexString())
                .setStatus(SyncStream.Status.PAUSING)
                .setCheckin(Instant.now()));
        assertFalse(streamService.isIdle(stream.getId()));

        stream = streamRepo.save(new SyncStream()
                .setGraphId(ObjectId.get().toHexString())
                .setStatus(SyncStream.Status.PAUSING)
                .setCheckin(Instant.now().minusMillis(StreamService.IDLE_STATUS_TIMEOUT_MS + 1l)));
        assertTrue(streamService.isIdle(stream.getId()));
    }

    @Test
    public void getAllPausingStream(){

        SyncStream stream1 = streamRepo.save(new SyncStream()
                .setGraphId(ObjectId.get().toHexString())
                .setStatus(SyncStream.Status.PAUSING)
                .setCheckin(Instant.now()));

        SyncStream stream2 = streamRepo.save(new SyncStream()
                .setGraphId(ObjectId.get().toHexString())
                .setStatus(SyncStream.Status.READY)
                .setCheckin(Instant.now()));

        assertEquals(1, streamService.getAllPausingStreams().size());
        assertEquals(SyncStream.Status.PAUSING, streamService.getAllPausingStreams().get(0).getStatus());
        assertEquals(stream1.getGraphId(), streamService.getAllPausingStreams().get(0).getGraphId());

        streamRepo.delete(stream1);
        assertTrue(streamService.getAllPausingStreams().isEmpty());

        List<SyncStream> streams =
                IntStream.range(0, 200).mapToObj(i -> streamRepo.save(new SyncStream().setGraphId("graph" + i)
                        .setStatus(SyncStream.Status.PAUSING))).collect(Collectors.toList());

        assertEquals(200, streamService.getAllPausingStreams().size());
    }
    @Test
    public void unclaimed() {
        SyncStream readyStream1 = streamRepo.save(new SyncStream()
                .setGraphId(ObjectId.get().toHexString())
                .setStatus(SyncStream.Status.READY).setCheckin(Instant.now().minus(5, ChronoUnit.MINUTES)));

        SyncStream readyStream2 = streamRepo.save(new SyncStream()
                .setGraphId(ObjectId.get().toHexString())
                .setStatus(SyncStream.Status.READY).setCheckin(Instant.now().minus(2, ChronoUnit.MINUTES)));

        SyncStream claimed = streamRepo.save(new SyncStream()
                .setGraphId(ObjectId.get().toHexString())
                .setStatus(SyncStream.Status.CLAIMED)
                .setProcessorId("processor1"));

        Instant lastCheckin = Instant.now().minusSeconds(30);
        SyncStream inProgress = streamRepo.save(new SyncStream()
                .setGraphId(ObjectId.get().toHexString())
                .setStatus(SyncStream.Status.RUNNING)
                .setProcessorId("processor1")
                .setCheckin(lastCheckin));

        SyncStream readyStream3 = streamRepo.save(new SyncStream()
                .setGraphId(ObjectId.get().toHexString())
                .setProcessorId("processor2")
                .setStatus(SyncStream.Status.READY).setCheckin(Instant.now()));

        var streams = streamService.unclaimed(3 * 60 * 1000);
        assertEquals(1, streams.size());
        streams = streamService.unclaimed(1 * 60 * 1000);
        assertEquals(2, streams.size());

    }

}
