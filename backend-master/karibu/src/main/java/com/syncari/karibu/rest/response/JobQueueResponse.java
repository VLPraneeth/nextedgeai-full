package com.syncari.karibu.rest.response;

import com.syncari.core.model.JobQueue;
import com.syncari.core.model.UUIDAuditModel;
import lombok.Data;
import lombok.ToString;

import java.util.Map;

@Data
@ToString(callSuper=true)
public class JobQueueResponse {
    private String jobId;
    private String status;
    private Map<String, Object> jobDetails;

}
