package com.syncari.viper;

import akka.Done;
import akka.actor.ActorSystem;
import akka.actor.Cancellable;
import akka.stream.ActorMaterializer;
import akka.stream.ActorMaterializerSettings;
import akka.stream.KillSwitches;
import akka.stream.UniqueKillSwitch;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.syncari.AbstractSyncariTest;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.QuotaExceededException;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.SyncStream;
import com.syncari.core.repositories.customer.StreamRepo;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.StreamService;
import com.syncari.core.service.WatermarkService;
import com.syncari.utils.Pair;
import net.sf.ehcache.concurrent.Sync;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import scala.concurrent.Promise;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class StreamManagerUpdateStreamTest extends AbstractSyncariTest {

    @Autowired
    StreamManager streamManager;

    @Autowired
    StreamService streamService;

    @Autowired
    StreamRepo streamRepo;

    ViperContext context;

    @Mock
    MappingGraphService graphService;

    @Before
    public void setUp(){
        super.setUp();
        context = new ViperContext(SyncariContext.getOrganziation(), SyncariContext.getInstance(), SyncariContext.getUser());
    }

    @After
    public void tearDown(){
        streamRepo.reset();
        super.tearDown();
    }

    @Test
    public void resetStreamToPause_SameViperInstance(){

        // create graph
        MappingGraph graph = new MappingGraph();
        graph.setId("myEntity");
        graph.setName("myGraph");

        when(graphService.retrieve("myEntity")).thenReturn(Optional.of(graph));
        streamManager.graphService = graphService;
        // create SyncStream
        SyncStream stream = streamRepo.save(new SyncStream()
                .setGraphId("myEntity")
                .setCheckin(Instant.now())
                .setStatus(SyncStream.Status.PAUSING));

        // assign stream affinity to viper instance
        streamManager.switches.put(Pair.of(SyncariContext.getInstance().getSyncariId(), stream.getId()),
                akka.japi.Pair.create(null, CompletableFuture.completedFuture(Done.done())));

        streamManager.updateStreams(context, List.of(stream));

        assertFalse(streamService.isIdle(stream.getId()));
        var updatedStream = streamRepo.findById(stream.getId()).get();
        assertEquals(SyncStream.Status.PAUSED, updatedStream.getStatus());
    }

    @Test
    public void resetStreamToPause_DifferentViperInstance(){

        // create graph
        MappingGraph graph = new MappingGraph();
        graph.setId("myEntity");
        graph.setName("myGraph");

        when(graphService.retrieve("myEntity")).thenReturn(Optional.of(graph));
        streamManager.graphService = graphService;

        // create SyncStream
        SyncStream stream = streamRepo.save(new SyncStream()
                .setGraphId("myEntity")
                .setCheckin(Instant.now())
                .setStatus(SyncStream.Status.PAUSING));

        // don't assign any affinity to viper instance
        streamManager.updateStreams(context, List.of(stream));

        // since checkin time is within 15 min window, stream is not idle
        assertFalse(streamService.isIdle(stream.getId()));
        stream= streamRepo.findById(stream.getId()).get();
        // stream is not updated as its not processed by current viper instance and hasn't exceeded the idle period of 15 mins
        assertNotEquals(SyncStream.Status.PAUSED, stream.getStatus());


        // change the checkin time to exceed over idle time period
        stream.setCheckin(Instant.now().minusMillis(StreamService.IDLE_STATUS_TIMEOUT_MS));
        stream = streamRepo.save(stream);
        // since checkin time has exceeded 15 min window, stream is idle
        assertTrue(streamService.isIdle(stream.getId()));
        streamManager.updateStreams(context, List.of(stream));

        stream = streamRepo.findById(stream.getId()).get();
        // stream is updated to PAUSED as its idle
        assertEquals(SyncStream.Status.PAUSED, stream.getStatus());
    }

    @Test
    public void streamCompletionQuotaExceededException() throws InterruptedException {
        var orgSyncService = streamManager.syncService;
        try {

            ActorSystem viper = ActorSystem.create("viper");
            ActorMaterializer actorMaterializer = ActorMaterializer.create(ActorMaterializerSettings.apply(viper).withInputBuffer(1, 1), viper);

            var now = Instant.now().getEpochSecond();
            var mockSyncService = mock(WatermarkService.class);
            doNothing().when(mockSyncService).updateNextSyncAtForAllEntitiesOfConnector(anyString(), anyLong(), anyBoolean());
            streamManager.syncService = mockSyncService;
            SyncStream claim = streamRepo.save(new SyncStream()
                    .setGraphId("myEntity")
                    .setCheckin(Instant.now())
                    .setStatus(SyncStream.Status.RUNNING));

            Source<Integer, Cancellable> tickSource =Source.tick(Duration.ofSeconds(0), Duration.ofSeconds(1),0);
            Sink<Integer, CompletionStage<Done>> finalSink = Sink.foreach(pair -> System.out.println(pair));
            var tickCompletionStage = tickSource.map(f  -> {
                if(true) {
                    throw new QuotaExceededException("ERROR_CODE", "message", "STATUS_CODE", "connectorId", now);
                }
                return 0;
            })
                    .viaMat(KillSwitches.single(), Keep.right())
                    .toMat(finalSink, Keep.both())
                    .run(actorMaterializer);

            streamManager.attacheStreamCompletionHandler(claim, tickCompletionStage, context);
            Thread.sleep(5000);
            verify(mockSyncService).updateNextSyncAtForAllEntitiesOfConnector(anyString(), anyLong(), anyBoolean());
        } finally {
            streamManager.syncService = orgSyncService;
        }

    }

    @Test
    public void streamCompletionNonRetriableException() throws InterruptedException {
        var orgSyncService = streamManager.syncService;
        try {
            ActorSystem viper = ActorSystem.create("viper");
            ActorMaterializer actorMaterializer = ActorMaterializer.create(ActorMaterializerSettings.apply(viper).withInputBuffer(1, 1), viper);

            var now = Instant.now().getEpochSecond();
            var mockSyncService = mock(WatermarkService.class);
            doNothing().when(mockSyncService).updateNextSyncAtForAllEntitiesOfConnector(anyString(), anyLong(), eq(true));
            streamManager.syncService = mockSyncService;
            SyncStream claim = streamRepo.save(new SyncStream()
                    .setGraphId("myEntity")
                    .setCheckin(Instant.now())
                    .setStatus(SyncStream.Status.RUNNING));

            Source<Integer, Cancellable> tickSource =Source.tick(Duration.ofSeconds(0), Duration.ofSeconds(1),0);
            Sink<Integer, CompletionStage<Done>> finalSink = Sink.foreach(pair -> System.out.println(pair));
            var tickCompletionStage = tickSource.map(f  -> {
                if(true) {
                    throw new NonRetriableException("ERROR_CODE", "message", "STATUS_CODE");
                }
                return 0;
            })
                    .viaMat(KillSwitches.single(), Keep.right())
                    .toMat(finalSink, Keep.both())
                    .run(actorMaterializer);
            streamManager.switches.put(Pair.of(context.getInstance().getSyncariId(),claim.getId()),tickCompletionStage);
            streamManager.attacheStreamCompletionHandler(claim, tickCompletionStage, context);
            Thread.sleep(5000);
            assertEquals(0,streamManager.switches.size());
            verify(mockSyncService, never()).updateNextSyncAtForAllEntitiesOfConnector(anyString(), anyLong(), eq(true));
        } finally {
            streamManager.syncService = orgSyncService;
        }

    }

    @Test
    public void streamRemovedOnSuccesfullCompletion() throws InterruptedException {
        var orgSyncService = streamManager.syncService;
        try {
            ActorSystem viper = ActorSystem.create("viper");
            ActorMaterializer actorMaterializer = ActorMaterializer.create(ActorMaterializerSettings.apply(viper).withInputBuffer(1, 1), viper);

            var now = Instant.now().getEpochSecond();
            var mockSyncService = mock(WatermarkService.class);
            doNothing().when(mockSyncService).updateNextSyncAtForAllEntitiesOfConnector(anyString(), anyLong(), anyBoolean());
            streamManager.syncService = mockSyncService;
            SyncStream claim = streamRepo.save(new SyncStream()
                    .setGraphId("myEntity")
                    .setCheckin(Instant.now())
                    .setStatus(SyncStream.Status.RUNNING));

            Source<Integer, Cancellable> tickSource =Source.tick(Duration.ofSeconds(0), Duration.ofSeconds(1),0);
            Sink<Integer, CompletionStage<Done>> finalSink = Sink.foreach(pair -> System.out.println(pair));
            var tickCompletionStage = tickSource.map(f  -> 0)
                    .viaMat(KillSwitches.single(), Keep.right())
                    .toMat(finalSink, Keep.both())
                    .run(actorMaterializer);
            streamManager.switches.put(Pair.of(context.getInstance().getSyncariId(),claim.getId()),tickCompletionStage);
            streamManager.attacheStreamCompletionHandler(claim, tickCompletionStage, context);
            tickCompletionStage.first().shutdown();
            Thread.sleep(5000);
            assertEquals(0,streamManager.switches.size());
            verify(mockSyncService, never()).updateNextSyncAtForAllEntitiesOfConnector(anyString(), anyLong(), anyBoolean());
        } finally {
            streamManager.syncService = orgSyncService;
        }

    }

}
