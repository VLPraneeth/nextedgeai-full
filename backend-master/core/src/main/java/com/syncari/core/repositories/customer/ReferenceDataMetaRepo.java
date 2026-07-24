package com.syncari.core.repositories.customer;

import java.util.List;
import java.util.Optional;

import com.syncari.core.model.ReferenceDataMeta;
import com.syncari.core.repositories.SyncariRepo;
import org.springframework.data.mongodb.repository.Query;

public interface ReferenceDataMetaRepo extends SyncariRepo<ReferenceDataMeta> {
    Optional<ReferenceDataMeta> findByName(String name);

    long countByStatus(String status);
}
