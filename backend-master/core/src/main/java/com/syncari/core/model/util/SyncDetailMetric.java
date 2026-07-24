package com.syncari.core.model.util;

import com.syncari.core.model.UUIDAuditModel;
import com.syncari.core.model.misc.EntitySynchStatusMetricSummary;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.temporal.ChronoUnit;

@Data
@Document
public class SyncDetailMetric extends UUIDAuditModel {
    private String syncariEntityId;
    private String entityName;
    private String apiName;
    private EntitySynchStatusMetricSummary summary;
    private boolean testMode;
    private boolean historicalSync;
    private String syncCycleId;
    private float duration;
    private ChronoUnit durationUnit = ChronoUnit.MILLIS;
    private Integer recordsProcessedInLastStage;


    public SyncDetailMetric setSummary(EntitySynchStatusMetricSummary summary) {
        this.summary = summary;
        return this;
    }

    public SyncDetailMetric(String syncariEntityId,String entityName,String apiName,
                            boolean historicalSync, boolean testMode, String syncCycleId, Integer recordsProcessedInLastStage) {
        this.syncariEntityId = syncariEntityId;
        this.entityName = entityName;
        this.historicalSync = historicalSync;
        this.testMode = testMode;
        this.syncCycleId = syncCycleId;
        this.apiName = apiName;
        this.recordsProcessedInLastStage = recordsProcessedInLastStage;
    }

}
