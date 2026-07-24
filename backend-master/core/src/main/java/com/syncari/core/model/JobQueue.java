package com.syncari.core.model;

import com.syncari.core.model.util.JobQueueStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

@Data
@Accessors(chain = true)
public class JobQueue extends UUIDAuditModel {
    private String jobType;
    private JobQueueStatus status;
    private Map<String, Object> jobDetails;

}
