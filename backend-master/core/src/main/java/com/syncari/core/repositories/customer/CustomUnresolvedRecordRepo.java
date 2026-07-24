package com.syncari.core.repositories.customer;

import com.syncari.core.model.UnresolvedRecord;

import java.util.List;

public interface CustomUnresolvedRecordRepo {

    void upsert(List<UnresolvedRecord> unresolvedEntities);

    void delete(List<UnresolvedRecord> unresolvedEntities);
}
