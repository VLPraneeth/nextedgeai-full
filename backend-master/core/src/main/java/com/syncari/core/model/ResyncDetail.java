package com.syncari.core.model;

import com.syncari.core.model.misc.ResyncStatus;
import com.syncari.core.model.misc.Watermark;

import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class ResyncDetail extends UUIDAuditModel {
    Map<String, ResyncStatus> entitiesToResync = new HashMap<>();
    @NotNull(message = "Syncari Entity id is required")
    String syncariEntityId;
    String syncariEntityName;
    @NotNull(message = "Start time is required")
    Instant startTime;
    @NotNull(message = "End time is required")
    Instant endTime;
    ResyncStatus status = ResyncStatus.NEW;
    String errorMsg;
    Mode mode = Mode.RESYNC;
    Map<String, Watermark> originalSyncWatermarks = new HashMap<>();

    public static List<ResyncStatus> RESYNC_COMPLETION_STATUSES = List.of(ResyncStatus.SUCCESS,
            ResyncStatus.ERROR,
            ResyncStatus.CANCELLED);

    public enum Mode {
        RESYNC,
        INITIALSYNC
    }

    public boolean isSourceInProgress(String entityId) {
        return entitiesToResync.containsKey(entityId) && !RESYNC_COMPLETION_STATUSES.contains(entitiesToResync.get(entityId));
    }

    public boolean isComplete() {
        return RESYNC_COMPLETION_STATUSES.contains(status);
    }

    public boolean isCompleteForAllSources() {
        return entitiesToResync.values().stream().allMatch(status -> RESYNC_COMPLETION_STATUSES.contains(status));
    }
}

