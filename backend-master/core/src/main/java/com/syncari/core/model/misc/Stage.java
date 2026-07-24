package com.syncari.core.model.misc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;


@Data
@AllArgsConstructor
@Accessors(chain = true)
public class Stage {
    private String title;
    private String subtitle;
    private Integer totalProcessedRecordsCount;
    private String recordCountSuffix = "records";
    private Instant lastProcessed;
    private Float durationWithoutConversion;
    private Float duration;
    private ChronoUnit durationUnit = ChronoUnit.MILLIS;
    private Status status = Status.NOT_STARTED;
    private Map<String, EntitySyncStatusMetric> details;

    public Stage(){}

    public enum Status {
        NOT_STARTED,
        IN_PROGRESS,
        COMPLETED
    }
}
