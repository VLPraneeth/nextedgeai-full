package com.syncari.api.rest.controllers.data.insights;

import com.syncari.core.model.insights.dataset.DatasetExport;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;

@Data
@Accessors(chain = true)
public class DatasetExportJobDTO {

    private String userName;
    private Instant requestedTime;
    private Instant expiredTime;
    private Long numberOfRecords;
    private String status;
    private String exportJobId;
    private Boolean expiryStatus;
}
