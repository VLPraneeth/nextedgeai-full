package com.syncari.viper.simulation;

import com.syncari.core.model.UnresolvedReference;
import com.syncari.core.repositories.customer.UnresolvedReferenceRepo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;

public class UnresolvedReferenceSimulationRepo extends BaseRepoSimulationImpl<UnresolvedReference> implements UnresolvedReferenceRepo {
    @Override
    public List<UnresolvedReference> deleteBySyncariEntityIdAndRecordIds(String syncariEntityDefId, List<String> recordIds) {
        return Collections.emptyList();
    }

    @Override
    public void deleteBySyncariEntityDefId(String syncariEntityDefId) {

    }

    @Override
    public List<UnresolvedReference> getBySyncariEntityDefId(String syncariEntityDefId) {
        return Collections.emptyList();
    }

    @Override
    public Page<UnresolvedReference> findUnResolvedReferencesBy(String connectorId, String externalEntityDefinitionName, Pageable page) {
        return Page.empty();
    }

    @Override
    public List<UnresolvedReference> findUnResolvedReferencesBy(String nextId, String connectorId, String externalEntityDefinitionName, int pageSize) {
        return Collections.emptyList();
    }

    @Override
    public void deleteUnResolvedReferencesBy(String connectorId, String externalEntityDefinitionName, List<String> externalRecordIds) {

    }

    @Override
    public List<UnresolvedReference> findUnresolvedReferenceBy(String syncariEntityDefId) {
        return Collections.emptyList();
    }

    @Override
    public void updateSyncariValues(List<UnresolvedReference> values) {

    }

    @Override
    public void upsertUnResolved(List<UnresolvedReference> unresolvedReferences) {

    }

    @Override
    public List<UnresolvedReference> findResolvedReferenceBy(String syncariEntityDefId) {
        return Collections.emptyList();
    }

    @Override
    public long reparentLoserReferences(List<String> loserIds, String winnerId) {
        return 0;
    }

    @Override
    public void markUnresolvable(List<UnresolvedReference> unresolvedReferences) {

    }
}
