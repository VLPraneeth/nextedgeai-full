package com.syncari.core.service;

import com.syncari.connector.data.BatchJob;
import com.syncari.connector.data.BatchJobStatus;
import com.syncari.core.model.JobDetail;
import com.syncari.core.repositories.customer.JobDetailRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class BatchJobService {
    @Autowired
    JobDetailRepo jobDetailRepo;

    public List<JobDetail> findByStatus(String connectorId, BatchJobStatus status) {
        return withInternalIds(jobDetailRepo.findByStatus(connectorId, status));
    }
    public List<JobDetail> findUnprocessed(String connectorId, String externalEntityName) {
        List<BatchJobStatus> unprocessedStates = List.of(BatchJobStatus.COMPLETED, BatchJobStatus.PENDING);
        return withInternalIds(jobDetailRepo.findByStatusIn(connectorId, externalEntityName, unprocessedStates));
    }

    private List<JobDetail> withInternalIds(List<JobDetail> byStatus) {
        byStatus.forEach(job -> job.getJob().setInternalId(job.getId()));
        return byStatus;
    }

    public void removeConsumed(String connectorId, String externalEntityName) {
        jobDetailRepo.removeConsumed(connectorId, externalEntityName);
    }

    public void upsert(List<BatchJob> updatedJobs) {
        if (updatedJobs.isEmpty()) return;
        List<JobDetail> jobs = updatedJobs.stream().map(j -> {
            JobDetail jobDetail = new JobDetail().setJob(j);
            jobDetail.setId(j.getInternalId());
            return jobDetail;
        }).collect(Collectors.toList());

        jobDetailRepo.saveAll(jobs);
    }
}
