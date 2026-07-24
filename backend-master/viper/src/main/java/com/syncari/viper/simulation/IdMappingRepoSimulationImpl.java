package com.syncari.viper.simulation;

import com.syncari.core.model.IdMapping;
import com.syncari.core.repositories.customer.IdMappingRepo;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;


public class IdMappingRepoSimulationImpl extends BaseRepoSimulationImpl<IdMapping> implements IdMappingRepo {

    @Override
    public List<IdMapping> findByExternalIds(String entityName, String connectorId, Collection<String> entityIds) {
        return Collections.emptyList();
    }

    @Override
    public List<IdMapping> findByExternalIds(String entityName, String connectorId, String externalEntityDefinitionId, Collection<String> entityIds) {
        return Collections.emptyList();
    }

    @Override
    public Optional<IdMapping> findByExternalId(String entityName, String connectorId, String externalEntityDefinitionId, String entityId) {
        return Optional.empty();
    }

    @Override
    public Optional<IdMapping> findBySyncariId(String syncariEntityName, String syncariId) {
        return Optional.empty();
    }

    @Override
    public Optional<IdMapping> findExistingMapping(String syncariEntityName, String syncariId, String connectorId, String externalEntityDefinitionId) {
        return Optional.empty();
    }

    @Override
    public List<IdMapping> findBySyncariIds(String syncariEntityName, Collection<String> syncariId) {
        return Collections.emptyList();
    }

    @Override
    public void upsert(List<IdMapping> idMappings) {
        //No-op
    }

    @Override
    public List<IdMapping> findOrphans(String syncariEntityName, Instant ts) {
        return List.of();
    }

    @Override
    public void deleteByEntityName(String syncariEntityName) {
        // No-op
    }

	@Override
	public void removeExternalIdRef(String connectorId) {
		// No-op
		
	}

}
