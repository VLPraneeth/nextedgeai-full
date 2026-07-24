package com.syncari.connector;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Set;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AttachRecordData {

    String pipelineId;
    String syncariEntityId;
    String nodeId;
    Set<String> fields;
    String hashValue;
    String batchId;
}
