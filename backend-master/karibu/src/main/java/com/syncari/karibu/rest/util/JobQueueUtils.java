package com.syncari.karibu.rest.util;

import com.syncari.core.model.JobQueue;
import com.syncari.karibu.rest.response.JobQueueResponse;
import org.springframework.stereotype.Component;

@Component
public class JobQueueUtils {

    public JobQueueResponse getJobQueueResponse(JobQueue jobQueue) {
        JobQueueResponse jobQueueResponse = new JobQueueResponse();

        jobQueueResponse.setJobId(jobQueue.getId());
        jobQueueResponse.setStatus(jobQueue.getStatus().name());
        jobQueueResponse.setJobDetails(jobQueue.getJobDetails());

        return jobQueueResponse;
    }

}
