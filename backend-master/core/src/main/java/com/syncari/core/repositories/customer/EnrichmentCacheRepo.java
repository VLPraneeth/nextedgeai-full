package com.syncari.core.repositories.customer;

import java.util.Optional;

import com.syncari.core.model.EnrichmentCache;
import com.syncari.core.repositories.SyncariRepo;

public interface EnrichmentCacheRepo extends SyncariRepo<EnrichmentCache> {
    Optional<EnrichmentCache> findByServiceIdAndEntityNameAndEnrichKey(String serviceCredentialId,
            String entityName, String enrichKey);
}
