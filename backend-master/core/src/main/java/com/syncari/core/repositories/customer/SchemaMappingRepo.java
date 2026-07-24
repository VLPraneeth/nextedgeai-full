package com.syncari.core.repositories.customer;

import java.util.List;

import org.springframework.data.mongodb.repository.Query;

import com.syncari.core.model.SchemaMapping;
import com.syncari.core.repositories.SyncariRepo;

public interface SchemaMappingRepo extends SyncariRepo<SchemaMapping> {

    @Query(value = "{'connectorId' : ?0, 'synapseObjectId' : ?1, 'scope' : ?2}")
    List<SchemaMapping> findByConnectorAndSynapseObject(String connectorId, String synapseObjectId, String scope);
    
    @Query(value = "{'connectorId' : ?0, 'syncariId' : ?1, 'scope' : ?2}")
    List<SchemaMapping> findByConnectorAndSyncariObject(String connectorId, String syncariId, String scope);

}