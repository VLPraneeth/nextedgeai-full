package com.syncari.core.model.misc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
@AllArgsConstructor
public class EntitySynchStatusMetricSummary {
    Map<String, EntitySyncStatusMetric> refreshSources = new HashMap<>();
    Map<String, EntitySyncStatusMetric> auotSyncSources = new HashMap<>();
    Map<String, EntitySyncStatusMetric> sources = new HashMap<>();
    Map<String, EntitySyncStatusMetric> sourceEp = new HashMap<>();
    Map<String, EntitySyncStatusMetric> sourceFp = new HashMap<>();
    Map<String, EntitySyncStatusMetric> sourceDsWrites = new HashMap<>();
    Map<String, EntitySyncStatusMetric> sinksEp = new HashMap<>();
    Map<String, EntitySyncStatusMetric> sinksFp = new HashMap<>();
    Map<String, EntitySyncStatusMetric> sinkWrites = new HashMap<>();
    List<EntitySyncErrorMetric> errors = new ArrayList<>();

    private Stage processingStage;

    public EntitySynchStatusMetricSummary(){}

    public enum Stage{
        //Reading data from sources
        REFRESH_SOURCE_SCHEMA_STAGE(-2),
        //Reading data from sources
        AUTO_SYNC_STAGE(-1),
        //Reading data from sources
        READING_SOURCE_SAVES_STAGE(0),
        // Processing entity pipeline.
        PROCESSING_SOURCE_ENTITY_PIPELINE(1),
        // Processing field pipeline.
        PROCESSING_SOURCE_FIELD_PIPELINE(2),
        // Processing field pipeline.
        PROCESSING_DATASTORE_WRITES(3),
        //Processing sink entity pipeline
        PROCESSING_SINK_ENTITY_PIPELINE(4),
        // Processing sink field pipeline.
        PROCESSING_SINK_FIELD_PIPELINE(5),
        // Processing sink writes to destinations.
        WRITING_DATA_TO_DESTINATION(6),
        // Processed all previous stages.
        FINISHED_PIPELINE_EXECUTION(7);

        private final int value;

        Stage(int value){
            this.value = value;
        }
        public int getValue() { return value; }
    }
}
