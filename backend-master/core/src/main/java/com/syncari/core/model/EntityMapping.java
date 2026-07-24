package com.syncari.core.model;

import com.syncari.core.model.util.Status;
import com.syncari.core.model.util.SyncDirection;

import lombok.Data;

@Data
public class EntityMapping extends UUIDAuditModel {
    // Syncari entity name
    String syncariEntityId;
    String syncariEntityName;
    String externalEntityId;
    String externalEntityName;
    String connectorId;
    SyncDirection direction = SyncDirection.INBOUND;
    Status status;

}
