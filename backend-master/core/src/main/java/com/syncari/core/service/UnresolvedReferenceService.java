package com.syncari.core.service;

import com.syncari.core.model.UnresolvedReference;
import com.syncari.core.repositories.customer.CustomUnresolvedRecordRepoImpl;
import com.syncari.core.repositories.customer.UnresolvedReferenceRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class UnresolvedReferenceService {
    public static final int PAGE_SIZE=1000;
    @Autowired
    UnresolvedReferenceRepo unresolvedReferenceRepo;
    @Autowired
    CustomUnresolvedRecordRepoImpl unresolvedReferenceRepoImpl;

    public List<UnresolvedReference> getBySyncariEntityDefId(String syncariEntityDefId){

        return unresolvedReferenceRepo.getBySyncariEntityDefId(syncariEntityDefId);
    }

    /**
     * Returns at most 1000 unresolved references at a time
     * @param nextId
     * @param connectorId
     * @param externalRefEntityName
     * @param pageSize
     * @return list of documents for current range and limited to 1000
     */
    public List<UnresolvedReference> getUnresolvedReferencesFor(String nextId, String connectorId, String externalRefEntityName,
            int pageSize) {
        return unresolvedReferenceRepo.findUnResolvedReferencesBy(nextId, connectorId, externalRefEntityName, pageSize);
    }

    public void removeBy(String syncariEntityDefId){
        unresolvedReferenceRepo.deleteBySyncariEntityDefId(syncariEntityDefId);
    }

    public void removeBy(String syncariEntityDefId, List<String> recordIds){
        unresolvedReferenceRepo.deleteBySyncariEntityIdAndRecordIds(syncariEntityDefId, recordIds);
    }

    /**
     * Reparent merge loser ids in unresolved reference list to winninig record when merge happens
     * The dependent pipelines will correctly associated with the winner if this method is called
     * as part of merge
     * @param loserIds
     * @param winnerId
     * @return nmber of records reparented
     */
    public long reparentLoserReferences(List<String> loserIds, String winnerId) {
        return unresolvedReferenceRepo.reparentLoserReferences(loserIds, winnerId);
    }
}
