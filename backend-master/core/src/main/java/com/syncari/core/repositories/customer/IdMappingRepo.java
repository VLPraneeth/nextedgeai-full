package com.syncari.core.repositories.customer;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.repository.Query;

import com.syncari.core.model.IdMapping;
import com.syncari.core.repositories.SyncariRepo;


public interface IdMappingRepo extends SyncariRepo<IdMapping>, CustomIdMappingRepo {

    @Query("{ 'entityName' : ?0, 'mappings': {'$elemMatch':{'connectorId' : ?1, 'entityId' : { '$in' : ?2 }}} }")
    List<IdMapping> findByExternalIds(String entityName, String connectorId, Collection<String> entityIds);

    @Query("{ 'entityName' : ?0, 'mappings': {'$elemMatch':{'connectorId' : ?1, 'entityDefinitionId': ?2, 'entityId' : { '$in' : ?3 }}} }")
    List<IdMapping> findByExternalIds(String entityName, String connectorId, String externalEntityDefinitionId, Collection<String> entityIds);

    @Query("{ 'entityName' : ?0, 'mappings': {'$elemMatch':{'connectorId' : ?1, 'entityDefinitionId': ?2, 'entityId' : ?3 }} }")
    Optional<IdMapping> findByExternalId(String entityName, String connectorId, String externalEntityDefinitionId, String entityId);

    @Query("{ 'entityName' : ?0, 'syncariId': ?1}")
    Optional<IdMapping> findBySyncariId(String syncariEntityName, String syncariId);

    @Query("{ 'entityName' : ?0, 'syncariId': ?1, 'mappings': {'$elemMatch': { 'connectorId': ?2, 'entityDefinitionId':?3 }}}")
    Optional<IdMapping> findExistingMapping(String syncariEntityName, String syncariId, String connectorId, String externalEntityDefinitionId);

    @Query("{ 'entityName' : ?0, 'syncariId': {'$in':?1}}")
    List<IdMapping> findBySyncariIds(String syncariEntityName, Collection<String> syncariId);
    
    @Query(value = "{ 'entityName' : ?0 }", delete = true)
    void deleteByEntityName(String syncariEntityName);
}
