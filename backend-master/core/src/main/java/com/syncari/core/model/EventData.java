package com.syncari.core.model;

import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class EventData extends UUIDAuditModel {
    private EntityData data;
    private Operation operation;
    private String eventId;
    private String graphId;
    private String batchId;
    private String connectorId;

}
