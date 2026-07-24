package com.syncari.core.repositories.customer;

import com.syncari.core.model.StagedBatch;
import com.syncari.core.repositories.SyncariRepo;

import java.util.List;
import java.util.Optional;

public interface StagedBatchRepo extends SyncariRepo<StagedBatch> {
    List<StagedBatch> findByCurrentBatchId(String currentBatchId);
    Optional<StagedBatch> findFirstByEntityNameOrderByCreatedAtDesc(String entityName);
}
