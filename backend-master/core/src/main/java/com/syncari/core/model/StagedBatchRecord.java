package com.syncari.core.model;

import com.syncari.connector.EntityData;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class StagedBatchRecord extends UUIDAuditModel {
    private String stagedBatchId;
    private String externalEntityDefinitionId;
    private String externalRecordId;
    private String syncariId;
    private EntityData entityData;
    private boolean isNew;
    private boolean modifiedByPipeline;
    private boolean deleted = false;
    private boolean isRequeued = false;
    private RequeueRequest requeueRequest;

    public boolean expiredRecordMarkedForProcessing() {
        return requeueRequest != null && requeueRequest.isProcessExpiredRecord();
    }

}
