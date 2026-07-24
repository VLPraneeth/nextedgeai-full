package com.syncari.core.repositories.customer;

import com.syncari.core.model.util.SyncDetailMetric;
import com.syncari.core.repositories.SyncariRepo;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SyncDetailMetricRepo extends SyncariRepo<SyncDetailMetric> {

    @Query(value="{'syncariEntityId':?0, 'entityName':?1, 'syncCycleId':?2}", sort ="{'createdAt':-1}")
    List<SyncDetailMetric> findSyncDetailMetricBy(String syncariEntityId,String entityName, String syncCycleId);

    Optional<SyncDetailMetric> findFirstBySyncariEntityIdAndSyncCycleId(String syncariEntityId, String syncCycleId, Sort sort);

    Optional<SyncDetailMetric> findFirstBySyncariEntityId(String syncariEntityId, Sort sort);

    Optional<SyncDetailMetric> findFirstBySyncariEntityIdAndRecordsProcessedInLastStageGreaterThanOrderByUpdatedAtDesc(String syncariEntityId,Integer recordProcessedInLastStage);

    @Query(value="{'syncariEntityId':?0, 'recordsProcessedInLastStage' :  {'$gt' :  0}, 'summary.processingStage':'FINISHED_PIPELINE_EXECUTION'}", sort ="{'updatedAt':-1}")
    List<SyncDetailMetric> findCompletedSyncMetric(String syncariEntityId, Pageable page);

    @Query(value = "{ 'syncariEntityId' : ?0 }", delete = true)
    void deleteBySyncariEntityId(String syncariEntityId);

}
