package com.syncari.core.service;

import com.syncari.connector.data.BatchJob;
import com.syncari.connector.data.BatchJobStatus;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.JobDetail;
import com.syncari.core.repositories.customer.JobDetailRepo;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class BatchJobServiceTest extends AbstractSyncariTest {
    @Autowired
    BatchJobService batchJobService;
    @Autowired
    JobDetailRepo repo;

    @Test
    public void removeConsumedIsNoOpForNonMatching(){
        assertEquals(0, repo.count());
        batchJobService.removeConsumed("connector","lead");
        assertEquals(0, repo.count());
    }

    @Test
    public void findersSetInternalIdOnJob(){
        BatchJob job1 = new BatchJob().setStatus(BatchJobStatus.PENDING).setJobId("Job1").setConnectorId("con1").setExternalEntityName("account").setJobDetails(Map.of());
        BatchJob job2 = new BatchJob().setStatus(BatchJobStatus.PENDING).setJobId("Job2").setConnectorId("con1").setExternalEntityName("account").setJobDetails(Map.of());
        BatchJob job3 = new BatchJob().setStatus(BatchJobStatus.PENDING).setJobId("Job3").setConnectorId("con1").setExternalEntityName("account").setJobDetails(Map.of());
        batchJobService.upsert(List.of(job1,job2, job3));
        List<JobDetail> unprocessed = batchJobService.findUnprocessed("con1", "account");
        assertEquals(3, unprocessed.size());
        unprocessed.forEach(u ->assertEquals(u.getId(),u.getJob().getInternalId()));
    }
    @Test
    public void upsert(){
        BatchJob oldJob = new BatchJob().setStatus(BatchJobStatus.PENDING).setJobId("Job3").setConnectorId("con1").setExternalEntityName("account").setJobDetails(Map.of());
        batchJobService.upsert(List.of(oldJob));
        JobDetail retrieved = batchJobService.findUnprocessed("con1", "account").get(0);
        BatchJob job1 = new BatchJob().setStatus(BatchJobStatus.PENDING).setJobId("Job1").setConnectorId("con1").setExternalEntityName("account").setJobDetails(Map.of());
        BatchJob job2 = new BatchJob().setStatus(BatchJobStatus.PENDING).setJobId("Job2").setConnectorId("con1").setExternalEntityName("account").setJobDetails(Map.of());
        batchJobService.upsert(List.of(job1,job2, retrieved.getJob()));
        List<JobDetail> unprocessed = batchJobService.findUnprocessed("con1", "account");
        assertEquals(3, unprocessed.size());
        unprocessed.forEach(u ->assertEquals(u.getId(),u.getJob().getInternalId()));
    }

}