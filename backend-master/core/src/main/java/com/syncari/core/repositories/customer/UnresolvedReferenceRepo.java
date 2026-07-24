package com.syncari.core.repositories.customer;

import com.syncari.core.model.UnresolvedReference;
import com.syncari.core.repositories.SyncariRepo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface UnresolvedReferenceRepo extends SyncariRepo<UnresolvedReference>, CustomUnresolvedReferenceRepo {

    @Query(value = "{ 'syncariEntityDefId' :?0, 'syncariRecordId' :{$in:?1} }", delete = true)
    List<UnresolvedReference> deleteBySyncariEntityIdAndRecordIds(String syncariEntityDefId, List<String> recordIds);

    @Query(value = "{ 'syncariEntityDefId' :?0 }", delete = true)
    void deleteBySyncariEntityDefId(String syncariEntityDefId);

    @Query(value = "{ 'syncariEntityDefId' :?0 }")
    List<UnresolvedReference> getBySyncariEntityDefId(String syncariEntityDefId);

    @Query(value = "{ 'connectorId' :?0, 'externalRefEntityName':?1, 'resolvedSyncariValue':null}")
    Page<UnresolvedReference> findUnResolvedReferencesBy(String connectorId, String externalEntityDefinitionName, Pageable page);

    @Query(value = "{ 'connectorId' :?0, 'externalRefEntityName':?1, 'externalRefRecordId':{$in:?2}}", delete = true)
    void deleteUnResolvedReferencesBy(String connectorId, String externalEntityDefinitionName, List<String> externalRecordIds);

    @Query(value = "{ 'syncariEntityDefId' :?0,'resolvedSyncariValue':null}")
    List<UnresolvedReference> findUnresolvedReferenceBy(String syncariEntityDefId);
    
}
