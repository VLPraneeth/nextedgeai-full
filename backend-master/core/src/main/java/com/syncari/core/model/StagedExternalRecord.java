package com.syncari.core.model;

import com.syncari.connector.EntityData;
import com.syncari.core.model.misc.ExternalFieldChange;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class StagedExternalRecord extends UUIDAuditModel {
    private String lastUpdatedStagedBatchId;
    private String lastUpdatedGraphId;
    private String externalEntityDefinitionId;
    private String externalRecordId;
    private EntityData entityData;
    private boolean deleted = false;
    private List<ExternalFieldChange> fieldChanges;
}
