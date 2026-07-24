package com.syncari.restutils.data;

import com.syncari.core.model.ResyncDetail;
import com.syncari.core.model.SyncStream;
import com.syncari.core.model.misc.ResyncStatus;
import com.syncari.core.model.misc.StreamInfo.Status;
import com.syncari.core.service.SchemaService;
import com.syncari.core.service.SyncStatusService;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Data
@Accessors(chain = true)
public class ResyncDetailDTO {

    Map<String, ResyncStatus> entitiesToResync = new HashMap<>();
    Instant startTime;
    Instant endTime;
    ResyncStatus status;
    String errorMsg;
    Instant lastResyncTime;
    Status syncStatus;

    public ResyncDetailDTO(ResyncDetail resyncDetail, ResyncStatus status, SchemaService schemaService,
                           SyncStatusService syncStatusService, SyncStream syncStream) {
        if (resyncDetail != null) {
            this.startTime = resyncDetail.getStartTime();
            this.endTime = resyncDetail.getEndTime();
            this.status = status;
            this.errorMsg = resyncDetail.getErrorMsg();
            this.lastResyncTime = resyncDetail.getUpdatedAt().toInstant();
            this.entitiesToResync = entitiesToResync;
            resyncDetail.getEntitiesToResync().forEach((entityId, resyncStatus) -> {
                this.entitiesToResync.put(schemaService.getEntity(entityId).getApiName(), resyncStatus);
            });
            // resync status is governed by the latest resync issued on the stream
            this.syncStatus = resyncDetail.isComplete()
                    ? syncStatusService.mapSyncStreamStatus(syncStream, Optional.of(resyncDetail)) // if resync is complete use syncStatus
                    : Status.RESYNCING; // if resync is in progress always use RESYNCING irrespective of syncStatus
            log.debug("ResyncDetailDTO status {} syncStatus {}", status, syncStatus);
        }
    }
}
