package com.syncari.core.model.misc;

import com.syncari.core.model.util.Scope;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@Accessors(chain = true)
public class PipelineErrorSummary {
    private String syncariEntityId;
    private String syncCycleId;
    private Error error;
    private List<Warning> warnings = new ArrayList<>();

    public PipelineErrorSummary(){}

    public PipelineErrorSummary(String syncariEntityId) {
        this.syncariEntityId = syncariEntityId;
    }

    @Data
    public static class Error {
        private String errorMessage;
        private String errorDetail;
        private String nodeId;
        private String targetId;
        private Scope level;
        private int retryCount;
    }

    @Data
    public static class Warning {

        private String errorMessage;
        private String errorDetail;
        private String nodeId;
        private String targetId;
        private Scope level;
        private Integer errorCount;
        private Integer totalCount;
        private ErrorType errorType;
    }

}
