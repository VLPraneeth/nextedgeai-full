package com.syncari.core.event.store.model;

import com.syncari.core.abac.AbacContext;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotNull;
import java.time.Instant;

@Data
@Accessors(chain = true)
public class AbacAudit {
    String id;
    
    @NotNull
    Instant createdAt;
    
    @NotNull
    String resourceType;
    
    String resource;
    
    @NotNull
    String action;
    
    String user;
    
    @NotNull
    Boolean allowed;
    
    String policy;
}