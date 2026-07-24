package com.syncari.core.repositories.customer;

import com.syncari.core.model.IdMapping;

import java.time.Instant;
import java.util.List;


public interface CustomIdMappingRepo  {
    /**
     * returns inserted count
     * @param idMappings
     * @return
     */
    void upsert(List<IdMapping> idMappings);

    List<IdMapping> findOrphans(String syncariEntityName, Instant ts);
    void removeExternalIdRef(String connectorId);
}