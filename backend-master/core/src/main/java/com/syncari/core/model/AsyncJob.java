package com.syncari.core.model;

import java.time.Instant;

import com.syncari.core.model.util.Status;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AsyncJob extends UUIDAuditModel {
    private String type;
    private String description;
    Instant startTime;
    Instant endTime;
    Status status;
    Event event;
}
