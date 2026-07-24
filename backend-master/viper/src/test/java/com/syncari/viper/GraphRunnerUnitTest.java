package com.syncari.viper;

import com.syncari.core.SyncariContext;
import com.syncari.core.model.*;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.SyncDirection;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.repositories.customer.SyncDetailRepo;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.StreamService;

import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class GraphRunnerUnitTest {

    @Test
    public void checkinHandlerHonorsCheckinInterval() {
        StreamService mock = Mockito.mock(StreamService.class);
        SyncStream syncStream = new SyncStream();
        when(mock.checkin(eq("processorId"),eq("streamId"))).thenReturn(true);
        syncStream.setId("streamId");
        Function<Long,Long> checkinHandler = new GraphRunner().getCheckinHandler(mock, syncStream, "processorId");
        long lastCheckinJustOverInterval = System.currentTimeMillis() - (2 * 60 * 1000 + 1);
        Long result = checkinHandler.apply(lastCheckinJustOverInterval);
        assertTrue(result > lastCheckinJustOverInterval);
        Long lastCheckinJustUnderInterval = System.currentTimeMillis() - (2 * 60 * 1000 -1);
        Long unchanged = checkinHandler.apply(lastCheckinJustUnderInterval);
        assertEquals(lastCheckinJustUnderInterval,unchanged);
        //verify checkin called only once
        verify(mock,times(1)).checkin(eq("processorId"),eq("streamId"));
    }

    @Ignore
    @Test
    public void schedulableSources(){
        var graphRunner =new GraphRunner();
        var repo =mock(SyncDetailRepo.class);
        graphRunner.syncDetailRepo = repo;
        EntityDefinition coreEntity = new EntityDefinition().setApiName("coreEntity").setConnectorId("connector");
        coreEntity.setId("coreEntityId");
        EntityDefinition entityDefinition = new EntityDefinition().setApiName("entity").setConnectorId("connector");
        entityDefinition.setId("entityId");
        MappingNode noSchedule = new MappingNode().setConfiguration(new EntitySourceNodeConfig().setEntityDefinition(entityDefinition)).setName("No Schedule");
        MappingNode everyMinute = new MappingNode().setConfiguration(new EntitySourceNodeConfig().setSchedule("0 * * * * *").setEntityDefinition(entityDefinition)).setName("Every Minute");
        MappingNode everyMonday = new MappingNode().setConfiguration(new EntitySourceNodeConfig().setSchedule("0 0 12 * * 1").setEntityDefinition(entityDefinition)).setName("At 12 Every Monday");
        MappingNode everyHour = new MappingNode().setConfiguration(new EntitySourceNodeConfig().setSchedule("0 0 * * * *").setEntityDefinition(entityDefinition)).setName("Once an hour");
        MappingNode every30Mins = new MappingNode().setConfiguration(new EntitySourceNodeConfig().setSchedule("0 0,30 * * * *").setEntityDefinition(entityDefinition)).setName("Every 30 minutes");
        Instant instant = Instant.now();
        SyncDetail syncDetail = new SyncDetail();
        syncDetail.setNextSyncAt(instant.toEpochMilli());
        when(repo.findWatermark("entityId","coreEntity", SyncDirection.INBOUND)).thenReturn(Optional.of(syncDetail));
        //isSchedulabel returns true when nextSyncAt is in the past regardless of schedule string
        assertTrue(graphRunner.isSchedulable(coreEntity,noSchedule, SyncDirection.INBOUND));
        assertTrue(graphRunner.isSchedulable(coreEntity,everyMinute, SyncDirection.INBOUND));
        assertTrue(graphRunner.isSchedulable(coreEntity,everyHour, SyncDirection.INBOUND));
        assertTrue(graphRunner.isSchedulable(coreEntity,everyMonday, SyncDirection.INBOUND));


        Instant minuteLater =instant.plusSeconds(60);
        syncDetail.setNextSyncAt(minuteLater.toEpochMilli());
        //isSchedulabel returns false when nextSyncAt is in the future, regardless of schedule string
        assertFalse(graphRunner.isSchedulable(coreEntity,noSchedule, SyncDirection.INBOUND));
        assertFalse(graphRunner.isSchedulable(coreEntity,everyMinute, SyncDirection.INBOUND));
        assertFalse(graphRunner.isSchedulable(coreEntity,everyHour, SyncDirection.INBOUND));
        assertFalse(graphRunner.isSchedulable(coreEntity,everyMonday, SyncDirection.INBOUND));

        //Calculate schedules
        graphRunner.graphService= mock(MappingGraphService.class);

        MappingNode coreNode = new MappingNode().setConfiguration(new CoreEntityNodeConfig().setEntityDefinition(coreEntity)).setName("coreEntity");
        syncDetail.setNextSyncAt(0);
        MappingGraph entityGraph = new MappingGraph().setScope(Scope.ENTITY);
        entityGraph.setId("graphId");

        when(graphRunner.graphService.retrieve("graphId")).thenReturn(Optional.of(entityGraph));
        when(repo.save(any())).thenReturn(syncDetail);
        //no schedule does not change nextSyncAt
        entityGraph.setNodes(List.of(noSchedule,coreNode));
        graphRunner.updateScheduledSources(entityGraph, Set.of(entityDefinition.getId()));
        assertEquals(0,syncDetail.getNextSyncAt());

        //every minute
        entityGraph.setNodes(List.of(everyMinute,coreNode));
        graphRunner.updateScheduledSources(entityGraph, Set.of(entityDefinition.getId()));
        assertEquals(Instant.now().getEpochSecond(),Instant.ofEpochMilli(syncDetail.getNextSyncAt()).getEpochSecond());
        graphRunner.updateScheduledSources(entityGraph, Set.of(entityDefinition.getId()));
        assertEquals(Instant.now().truncatedTo(ChronoUnit.MINUTES).plusSeconds(60).getEpochSecond(),Instant.ofEpochMilli(syncDetail.getNextSyncAt()).getEpochSecond());

        //every hour
        syncDetail.setNextSyncAt(0);
        entityGraph.setNodes(List.of(everyHour,coreNode));
        graphRunner.updateScheduledSources(entityGraph, Set.of(entityDefinition.getId()));
        assertEquals(Instant.now().getEpochSecond(),Instant.ofEpochMilli(syncDetail.getNextSyncAt()).getEpochSecond());
        graphRunner.updateScheduledSources(entityGraph, Set.of(entityDefinition.getId()));
        assertEquals(Instant.now().truncatedTo(ChronoUnit.HOURS).plusSeconds(60*60).getEpochSecond(),Instant.ofEpochMilli(syncDetail.getNextSyncAt()).getEpochSecond());
        //Updating schedule does not affect future nextSyncAt dates
        graphRunner.updateScheduledSources(entityGraph, Set.of(entityDefinition.getId()));
        assertEquals(Instant.now().truncatedTo(ChronoUnit.HOURS).plusSeconds(60*60).getEpochSecond(),Instant.ofEpochMilli(syncDetail.getNextSyncAt()).getEpochSecond());

        //every monday 12 noon (UTC)
        syncDetail.setNextSyncAt(0);
        entityGraph.setNodes(List.of(everyMonday,coreNode));
        graphRunner.updateScheduledSources(entityGraph, Set.of(entityDefinition.getId()));
        assertEquals(Instant.now().getEpochSecond(),Instant.ofEpochMilli(syncDetail.getNextSyncAt()).getEpochSecond());
        ZonedDateTime today = ZonedDateTime.now();
        Instant nearestMonday =Instant.from(today.plusDays(8-today.getDayOfWeek().getValue()).truncatedTo(ChronoUnit.DAYS)).plus(12,ChronoUnit.HOURS);
        graphRunner.updateScheduledSources(entityGraph, Set.of(entityDefinition.getId()));
        assertEquals(nearestMonday,Instant.ofEpochMilli(syncDetail.getNextSyncAt()));

        //every 30 mins
        syncDetail.setNextSyncAt(0);
        entityGraph.setNodes(List.of(every30Mins,coreNode));
        graphRunner.updateScheduledSources(entityGraph, Set.of(entityDefinition.getId()));
        assertEquals(Instant.now().getEpochSecond(),Instant.ofEpochMilli(syncDetail.getNextSyncAt()).getEpochSecond());
        graphRunner.updateScheduledSources(entityGraph, Set.of(entityDefinition.getId()));
        var now = ZonedDateTime.ofInstant(Instant.now().truncatedTo(ChronoUnit.MINUTES), ZoneOffset.UTC);
        assertEquals(now.plus((60 - now.get(ChronoField.MINUTE_OF_HOUR)) % 30, ChronoUnit.MINUTES).toInstant(),Instant.ofEpochMilli(syncDetail.getNextSyncAt()));

    }


    @Test
    public void syncCycleAlertThresholdsHonored() {
        GraphRunner graphRunner = new GraphRunner();

        ViperContext context = new ViperContext(SyncariContext.getOrganziation(), SyncariContext.getInstance(), SyncariContext.getUser());
        context.setContextSyncRunId("dummycontextsyncrunid");
        context.setSyncStartTime(Instant.now().toEpochMilli());
        boolean isLag = graphRunner.captureAndAlertSyncDuration(context, Optional.empty());
        assertFalse(isLag);

        context.setSyncStartTime(Instant.now().minus(60, ChronoUnit.MINUTES).toEpochMilli());
        isLag = graphRunner.captureAndAlertSyncDuration(context, Optional.empty());
        assertTrue(isLag);
    }
}