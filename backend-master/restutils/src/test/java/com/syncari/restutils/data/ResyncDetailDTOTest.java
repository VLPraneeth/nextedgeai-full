package com.syncari.restutils.data;

import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.ResyncDetail;
import com.syncari.core.model.SyncStream;
import com.syncari.core.model.misc.ResyncStatus;
import com.syncari.core.model.misc.StreamInfo;
import com.syncari.core.service.SchemaService;
import com.syncari.core.service.SyncStatusService;
import org.junit.Test;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class ResyncDetailDTOTest {

    @Test
    public void inProgressResyncDTO(){
        // case 1: resync with status NEW and syncStatus RUNNING
        ResyncDetail resync = new ResyncDetail()
                .setEntitiesToResync(Map.of("sourceEntityId", ResyncStatus.NEW))
                .setSyncariEntityId("syncariEntityId")
                .setSyncariEntityName("syncariEntityName")
                .setStartTime(Instant.ofEpochMilli(0))
                .setEndTime(Instant.ofEpochMilli(1))
                .setStatus(ResyncStatus.NEW);
        resync.setUpdatedAt(new Date(Instant.now().toEpochMilli()));

        SyncStream syncStream = new SyncStream().setGraphId("graphId").setProcessorId("processorId")
                .setStatus(SyncStream.Status.RUNNING).setCheckin(Instant.now());

        SyncStatusService syncStatusService = mock(SyncStatusService.class);
        doReturn(StreamInfo.Status.RUNNING).when(syncStatusService).mapSyncStreamStatus(syncStream, Optional.of(resync));

        SchemaService schemaService = mock(SchemaService.class);
        doReturn(new EntityDefinition("syncariEntityName", "Syncari Entity")).when(schemaService).getEntity("syncariEntityId");
        doReturn(new EntityDefinition("sourceEntityName", "Source Entity")).when(schemaService).getEntity("sourceEntityId");

        ResyncDetailDTO resyncDetailDTO = new ResyncDetailDTO(resync, resync.getStatus(), schemaService, syncStatusService, syncStream);
        assertEquals(resyncDetailDTO.getStatus(), resync.getStatus());
        assertEquals(resyncDetailDTO.getSyncStatus(), StreamInfo.Status.RESYNCING);
        verify(syncStatusService, never()).mapSyncStreamStatus(syncStream, Optional.of(resync));

        // case 2: resync with status PROCESSING and syncStatus RUNNING
        resync.setStatus(ResyncStatus.PROCESSING);
        resyncDetailDTO = new ResyncDetailDTO(resync, resync.getStatus(), schemaService, syncStatusService, syncStream);
        assertEquals(resyncDetailDTO.getStatus(), resync.getStatus());
        assertEquals(resyncDetailDTO.getSyncStatus(), StreamInfo.Status.RESYNCING);
        verify(syncStatusService, never()).mapSyncStreamStatus(syncStream, Optional.of(resync));

        // case 3: resync with status PROCESSING and syncStatus PAUSED
        resync.setStatus(ResyncStatus.PROCESSING);
        syncStream.setStatus(SyncStream.Status.PAUSED);
        doReturn(StreamInfo.Status.PAUSED).when(syncStatusService).mapSyncStreamStatus(syncStream, Optional.of(resync));
        resyncDetailDTO = new ResyncDetailDTO(resync, resync.getStatus(), schemaService, syncStatusService, syncStream);
        assertEquals(resyncDetailDTO.getStatus(), resync.getStatus());
        assertEquals(resyncDetailDTO.getSyncStatus(), StreamInfo.Status.RESYNCING);
        verify(syncStatusService, never()).mapSyncStreamStatus(syncStream, Optional.of(resync));

        // case 4: resync with status NEW and syncStatus READY
        resync.setStatus(ResyncStatus.NEW);
        syncStream.setStatus(SyncStream.Status.READY);
        doReturn(StreamInfo.Status.QUEUED).when(syncStatusService).mapSyncStreamStatus(syncStream, Optional.of(resync));
        resyncDetailDTO = new ResyncDetailDTO(resync, resync.getStatus(), schemaService, syncStatusService, syncStream);
        assertEquals(resyncDetailDTO.getStatus(), resync.getStatus());
        assertEquals(resyncDetailDTO.getSyncStatus(), StreamInfo.Status.RESYNCING);
        verify(syncStatusService, never()).mapSyncStreamStatus(syncStream, Optional.of(resync));
    }

    @Test
    public void completedResyncDTO(){
        // case 1: resync with status SUCCESS and syncStatus RUNNING
        ResyncDetail resync = new ResyncDetail()
                .setEntitiesToResync(Map.of("sourceEntityId", ResyncStatus.SUCCESS))
                .setSyncariEntityId("syncariEntityId")
                .setSyncariEntityName("syncariEntityName")
                .setStartTime(Instant.ofEpochMilli(0))
                .setEndTime(Instant.ofEpochMilli(1))
                .setStatus(ResyncStatus.SUCCESS);
        resync.setUpdatedAt(new Date(Instant.now().toEpochMilli()));

        SyncStream syncStream = new SyncStream().setGraphId("graphId").setProcessorId("processorId")
                .setStatus(SyncStream.Status.RUNNING).setCheckin(Instant.now());

        SyncStatusService syncStatusService = mock(SyncStatusService.class);
        doReturn(StreamInfo.Status.RUNNING).when(syncStatusService).mapSyncStreamStatus(syncStream, Optional.of(resync));

        SchemaService schemaService = mock(SchemaService.class);
        doReturn(new EntityDefinition("syncariEntityName", "Syncari Entity")).when(schemaService).getEntity("syncariEntityId");
        doReturn(new EntityDefinition("sourceEntityName", "Source Entity")).when(schemaService).getEntity("sourceEntityId");

        ResyncDetailDTO resyncDetailDTO = new ResyncDetailDTO(resync, resync.getStatus(), schemaService, syncStatusService, syncStream);
        assertEquals(resyncDetailDTO.getStatus(), resync.getStatus());
        assertEquals(resyncDetailDTO.getSyncStatus(), StreamInfo.Status.RUNNING);
        verify(syncStatusService, atLeastOnce()).mapSyncStreamStatus(syncStream, Optional.of(resync));

        // case 2: resync with status SUCCESS and syncStatus PAUSED
        resync.setStatus(ResyncStatus.SUCCESS);
        syncStream.setStatus(SyncStream.Status.PAUSED);
        doReturn(StreamInfo.Status.PAUSED).when(syncStatusService).mapSyncStreamStatus(syncStream, Optional.of(resync));
        resyncDetailDTO = new ResyncDetailDTO(resync, resync.getStatus(), schemaService, syncStatusService, syncStream);
        assertEquals(resyncDetailDTO.getStatus(), resync.getStatus());
        assertEquals(resyncDetailDTO.getSyncStatus(), StreamInfo.Status.PAUSED);
        verify(syncStatusService, atLeastOnce()).mapSyncStreamStatus(syncStream, Optional.of(resync));

        // case 4: resync with status CANCELLED and syncStatus READY
        resync.setStatus(ResyncStatus.CANCELLED);
        syncStream.setStatus(SyncStream.Status.READY);
        doReturn(StreamInfo.Status.QUEUED).when(syncStatusService).mapSyncStreamStatus(syncStream, Optional.of(resync));
        resyncDetailDTO = new ResyncDetailDTO(resync, resync.getStatus(), schemaService, syncStatusService, syncStream);
        assertEquals(resyncDetailDTO.getStatus(), resync.getStatus());
        assertEquals(resyncDetailDTO.getSyncStatus(), StreamInfo.Status.QUEUED);
        verify(syncStatusService, atLeastOnce()).mapSyncStreamStatus(syncStream, Optional.of(resync));
    }
}
