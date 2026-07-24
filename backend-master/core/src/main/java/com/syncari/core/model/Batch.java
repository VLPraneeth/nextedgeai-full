package com.syncari.core.model;

import java.util.HashMap;
import java.util.Map;

import javax.validation.constraints.NotNull;

import com.syncari.connector.Operation;
import com.syncari.core.model.util.Status;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Batch extends  UUIDAuditModel {
    @NotNull(message = "Batch status is required")
    private Status status = Status.NEW;
    @NotNull(message = "Entity id is required")
    private String entityId;
    private Operation operation;
    private Map<String, Object> config = new HashMap<String, Object>();
    private long rowsAffected;
    private long failedCount;
    private long rowsTotal;
    
    public boolean isCancelled() {
    	return getStatus() == Status.CANCELLED;
    }
}
