package com.syncari.core.model.misc;

import java.time.Instant;
import java.util.List;

import lombok.Data;

@Data
public class PipelineTestResult {
    String graphId;
    PipelineTestStatus status;
    Instant startTime;
    Instant endTime;
    List<SourceData> inputData;
    List<SourceData> successData;
    List<SourceData> failedData;
    String errorDetails;
}

class SourceData {
    String connectorId;
    List<String> entityIds;
}