package com.syncari.core.model;

import java.time.Instant;

import javax.validation.constraints.NotNull;

import org.springframework.data.mongodb.core.mapping.Document;

import com.syncari.core.model.misc.Watermark;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Document
public class DatastoreWatermark extends UUIDAuditModel {
    @NotNull(message = "Entity id is required")
    String entityId;
    String entityName;
    Watermark watermark;
    long iterationsPerCycle;
    boolean isDatastoreInitial;
    Status initialLoadStatus;

    public enum Status {
        INPROGRESS,
        COMPLETED,
        ERROR
    }

    public long lagInMillis() {
        return Instant.now().toEpochMilli() - watermark.getStart();
    }

    public boolean isInitialLoadCompleted(){
        return initialLoadStatus.equals(Status.COMPLETED);
    }
}
