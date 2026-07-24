package com.syncari.core.repositories.customer;

import com.syncari.core.model.StagedBatchRecord;
import com.syncari.core.model.StagedExternalRecord;
import com.syncari.core.repositories.SyncariRepo;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface StagedExternalRecordRepo extends SyncariRepo<StagedExternalRecord>, CustomStagedExternalRecordRepo {

    @Query("{externalEntityDefinitionId:?0, externalRecordId:?1}")
    Optional<StagedExternalRecord> findByExternalRecordIdAndExternalEntityDefinitionId(String externalEntityDefinitionId, String externalRecordId);

}
