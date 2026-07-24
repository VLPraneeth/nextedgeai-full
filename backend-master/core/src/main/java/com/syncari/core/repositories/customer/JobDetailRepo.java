package com.syncari.core.repositories.customer;

import com.syncari.connector.data.BatchJobStatus;
import com.syncari.core.model.JobDetail;
import com.syncari.core.repositories.SyncariRepo;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface JobDetailRepo extends SyncariRepo<JobDetail> {
    @Query("{ 'job.connectorId' :?0, 'job.status':'COMPLETED'}")
    List<JobDetail> findByStatus(String connectorId, BatchJobStatus status);

    @Query("{ 'job.connectorId' :?0, 'job.externalEntityName': ?1, 'job.status':{$in:['COMPLETED','PENDING']}}")
    List<JobDetail> findByStatusIn(String connectorId, String externalEntityName ,List<BatchJobStatus> status);

    @Query(value = "{ 'job.connectorId' :?0, 'job.externalEntityName': ?1, 'job.status':'COMPLETED'}", delete = true)
    void removeConsumed(String connectorId, String externalEntityName);
}
