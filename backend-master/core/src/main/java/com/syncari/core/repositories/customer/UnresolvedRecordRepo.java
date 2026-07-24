package com.syncari.core.repositories.customer;

import com.syncari.core.model.UnresolvedRecord;
import com.syncari.core.repositories.SyncariRepo;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface UnresolvedRecordRepo extends SyncariRepo<UnresolvedRecord>, CustomUnresolvedRecordRepo {

    @Query(value = "{ 'externalEntityDefinitionId' :?0, 'status' :'UNRESOLVED'}")
    List<UnresolvedRecord> findUnresolved(String externalEntityDefinitionId);
}
