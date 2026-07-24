package com.syncari.core.repositories.customer;

import com.syncari.core.model.StagedBatchRecord;
import com.syncari.core.repositories.SyncariRepo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Meta;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StagedBatchRecordRepo extends SyncariRepo<StagedBatchRecord>, CustomStagedBatchRecordRepo {

    Page<StagedBatchRecord> findByStagedBatchIdIn(List<String> stagedBatchIds, Pageable page);

    @Query("{stagedBatchId:{$in:?0}, 'deleted' : false}")
    @Meta(cursorBatchSize = 500)
    Page<StagedBatchRecord> findByStagedBatchIdUndeleted(List<String> stagedBatchIds, Pageable page);

    @Query("{stagedBatchId:{$in:?0}, 'isNew':?1, 'deleted' : false}")
    Page<StagedBatchRecord> findByStagedBatchIdInAndIsNew(List<String> stagedBatchIds, boolean isNew, Pageable page);

    Page<StagedBatchRecord> findByStagedBatchIdAndEntityDataName(String stagedBatchId, String entityName, Pageable page);

    @Query("{stagedBatchId:?0, 'entityData.name':?1, 'entityData.id':?2, deleted : false}")
    Optional<StagedBatchRecord> findExternalRecord(String stagedBatchId, String entityName, String externalId);

    @Query("{stagedBatchId:?0, 'entityData.name':?1, 'entityData.id':?2, deleted : false}")
    List<StagedBatchRecord> findExternalRecords(String stagedBatchId, String entityName, String externalId);

    @Query("{stagedBatchId:?0, 'syncariId':?1, 'deleted' : false}")
    List<StagedBatchRecord> findByStagedBatchIdAndSyncariId(String stagedBatchId,String syncariId);

    @Query("{stagedBatchId:?0, 'syncariId':{$in: ?1}, 'externalEntityDefinitionId' : ?2, 'deleted' : false}")
    List<StagedBatchRecord> findByStagedBatchIdAndSyncariIdsAndEntity(String stagedBatchId, List<String> syncariId, String externalEntityDefinitionId);

}
