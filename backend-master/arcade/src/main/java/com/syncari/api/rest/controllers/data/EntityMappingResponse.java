package com.syncari.api.rest.controllers.data;

import com.syncari.core.model.util.Status;
import com.syncari.core.model.util.SyncDirection;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EntityMappingResponse {
    String syncariEntityId;
    String syncariEntityName;
    String externalEntityId;
    String externalEntityName;
    SyncDirection direction = SyncDirection.INBOUND;
    Status status;
    
    public EntityMappingResponse() {}

}
