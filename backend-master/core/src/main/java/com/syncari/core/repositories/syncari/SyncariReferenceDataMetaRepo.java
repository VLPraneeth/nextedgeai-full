package com.syncari.core.repositories.syncari;

import java.util.Optional;

import com.syncari.core.model.ReferenceDataMeta;
import com.syncari.core.repositories.SyncariRepo;

public interface SyncariReferenceDataMetaRepo extends SyncariRepo<ReferenceDataMeta> {
    
    Optional<ReferenceDataMeta> findByName(String name);
    
}
