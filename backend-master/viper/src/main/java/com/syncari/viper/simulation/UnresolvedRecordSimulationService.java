package com.syncari.viper.simulation;

import com.syncari.connector.EntityData;
import com.syncari.core.model.UnresolvedRecord;
import com.syncari.core.service.UnresolvedRecordService;

import java.util.List;

/**
 * A Noop implementation for UnresolvedRecordService
 */
public class UnresolvedRecordSimulationService extends UnresolvedRecordService {
    @Override
    public Iterable<EntityData> getUnresolvedEntities(String syncariEntityDefinitionId, String externalEntityDefinitionId) {
        return List.of();
    }

    @Override
    public List<UnresolvedRecord> getUnresolvedRecords(String externalEntityDefinitionId) {
        return List.of();
    }

    @Override
    public void delete(List<UnresolvedRecord> unresolvedEntities) {

    }

    @Override
    public void upsert(List<UnresolvedRecord> unresolvedEntities) {
    }

    @Override
    protected void markPermanentlyUnresolved(List<UnresolvedRecord> unresolvedEntities) {
    }
}
