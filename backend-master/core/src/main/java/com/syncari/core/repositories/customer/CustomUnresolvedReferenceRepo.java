package com.syncari.core.repositories.customer;

import java.util.List;

import com.syncari.core.model.UnresolvedReference;

public interface CustomUnresolvedReferenceRepo {
    void updateSyncariValues(List<UnresolvedReference> values);

    void upsertUnResolved(List<UnresolvedReference> unresolvedReferences);

    List<UnresolvedReference> findResolvedReferenceBy(String syncariEntityDefId);

    long reparentLoserReferences(List<String> loserIds, String winnerId);

    List<UnresolvedReference> findUnResolvedReferencesBy(String nextId, String connectorId, String externalRefEntityName, int pageSize);

    void markUnresolvable(List<UnresolvedReference> unresolvedReferences);

}
