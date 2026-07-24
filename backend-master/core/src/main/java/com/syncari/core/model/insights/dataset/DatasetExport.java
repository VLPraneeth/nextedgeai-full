package com.syncari.core.model.insights.dataset;

import com.syncari.core.model.UUIDAuditModel;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;

@Data
@Accessors(chain = true)
public class DatasetExport extends UUIDAuditModel {

    private String userId;
    private String userName; // Getting this from SyncariContext.getUser
    private String datasetId;
    private Dataset datasetToBeExported;
    private Instant requestedTime;
    private Instant expiredTime;
    private Long numberOfRecords;
    private DatasetExportStatus status = DatasetExportStatus.PENDING;
    private String exportedFileLink;

    public enum DatasetExportStatus {
        PENDING, INPROGRESS, COMPLETED, ERROR,CANCELLED
    }
}




