package com.syncari.core.model;

import com.syncari.connector.Operation;
import com.syncari.connector.data.Result;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Captures data about writes to sinks
 */
@Data
@Accessors(chain = true)
public class SinkLog extends UUIDAuditModel {
    private String batchId;
    private String txLogId;
    private String connectorId;
    private String entityName;
    private Operation operation;
    private Result result;
}
