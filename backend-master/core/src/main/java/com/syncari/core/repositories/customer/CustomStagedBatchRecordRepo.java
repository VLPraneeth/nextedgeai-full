package com.syncari.core.repositories.customer;

import java.util.List;
import java.util.Optional;

import com.syncari.core.model.StagedBatchRecord;

public interface CustomStagedBatchRecordRepo {
    List<StagedBatchRecord> findByStagedBatchIdIn(List<String> stagedBatchIds, String marker, int limit);

    Optional<StagedBatchRecord> findFirstByExternalEntityDefinitionIdAndExternalRecordId(String externalEntityDefinitionId, String externalRecordId);

    List<StagedBatchRecord> updateMany(List<StagedBatchRecord> records);

    boolean exists(List<String> stagedBatchId, String syncariId, String key, Object value);

    List<StagedBatchRecord> getStagedRecordBySyncariId(String syncariId, List<String> stagedBatchIds);
}
