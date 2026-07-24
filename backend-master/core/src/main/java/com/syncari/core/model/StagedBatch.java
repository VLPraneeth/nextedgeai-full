package com.syncari.core.model;

import com.syncari.core.model.misc.Watermark;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class StagedBatch extends UUIDAuditModel {
    private String entityName;
    private String connectorId;
    private String currentBatchId;
    private String sourceEntityName;
    private String sourceEntityDefinitionId;
    private Watermark watermark;


    public StagedBatch() {
    }

    public StagedBatch(String entityName) {
        this.entityName = entityName;
    }

    public void setReadOffsetWatermark(long start, long end, long offset){
        watermark.setStart(start).setEnd(end).setOffset(offset);
    }

    public void setChangeStream(String changeStream) {
        watermark.setChangeStream(changeStream);
    }
}
