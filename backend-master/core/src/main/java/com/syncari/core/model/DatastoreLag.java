package com.syncari.core.model;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

@ToString
@Data
@Accessors(chain = true)
public class DatastoreLag {

    private String entityId;
    private String entityName;
    private long pendingRecords;
    private String dataStoreCurrentTimestamp;
    private String error;
}
