package com.syncari.core.model.misc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Data
@AllArgsConstructor
@Accessors(chain = true)
public class SyncMetric {

    private String syncariEntityId;
    private Instant lastProcessed;
    private Float duration;
    private ChronoUnit durationUnit = ChronoUnit.SECONDS;
    private String title;
    private String entityName;
    private String apiName;
    private Instant lastSyncTime;
    private boolean emptyLastSync;
    List<Stage> allStages;
    private int errorCount = 0;
    private int warningCount;
    public SyncMetric(){}
}
