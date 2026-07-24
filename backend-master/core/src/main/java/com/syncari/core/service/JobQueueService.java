package com.syncari.core.service;

import com.syncari.core.exception.NotFoundException;
import com.syncari.core.model.JobQueue;
import com.syncari.core.model.util.JobQueueStatus;
import com.syncari.core.repositories.customer.JobQueueRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component
public class JobQueueService {

    @Autowired
    JobQueueRepo jobQueueRepo;

    public JobQueue createJobQueue (String jobQueueId, String jobType, JobQueueStatus status, Map<String, Object> jobDetails) {
        JobQueue jobQueue = new JobQueue();
        jobQueue.setId(jobQueueId);
        jobQueue.setJobType(jobType);
        jobQueue.setStatus(status);
        jobQueue.setJobDetails(jobDetails);
        return jobQueueRepo.save(jobQueue);
    }

    public JobQueue updateJobQueue (String jobQueueId, JobQueueStatus status, Map<String, Object> jobDetails) {
        JobQueue jobQueue = getJobQueue(jobQueueId);

        if(status != null)
            jobQueue.setStatus(status);

        if (jobDetails != null)
            jobQueue.setJobDetails(jobDetails);

        return jobQueueRepo.save(jobQueue);
    }

    public JobQueue getJobQueue (String jobQueueId) {
        JobQueue jobQueue = jobQueueRepo.findById(jobQueueId).orElseThrow(() ->
                new NotFoundException(i18n("job_queue_not_found", jobQueueId)));
        return jobQueue;
    }

    public void deleteJobQueue(String jobQueueId){
        if (null != getJobQueue(jobQueueId)){
            jobQueueRepo.deleteById(jobQueueId);
        }
    }
}
