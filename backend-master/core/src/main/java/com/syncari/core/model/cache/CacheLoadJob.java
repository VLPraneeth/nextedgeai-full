package com.syncari.core.model.cache;

import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.UUIDAuditModel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@AllArgsConstructor
@Accessors(chain = true)
@Getter
@Setter
public class CacheLoadJob extends UUIDAuditModel {

    public CacheLoadJob() {
    }

    private CacheLoadStatus status;
    private int recordsCached;

    private String instanceId;

    private String entityName;
    private long lastCacheWriteTimestamp;

    private String lastCacheWriteWatermark;

}
