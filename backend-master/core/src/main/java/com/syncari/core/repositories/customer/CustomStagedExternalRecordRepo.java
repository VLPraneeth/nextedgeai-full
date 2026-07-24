package com.syncari.core.repositories.customer;

import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.IdMapping;
import com.syncari.core.model.StagedExternalRecord;

import java.time.Instant;
import java.util.List;


public interface CustomStagedExternalRecordRepo {
    void upsert(List<StagedExternalRecord> records, EntityDefinition entity);
}