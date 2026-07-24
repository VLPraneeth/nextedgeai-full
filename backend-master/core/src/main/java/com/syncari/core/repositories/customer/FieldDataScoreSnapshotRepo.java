package com.syncari.core.repositories.customer;

import java.util.List;

import org.springframework.data.mongodb.repository.Query;

import com.syncari.core.model.FieldDataScoreSnapshot;
import com.syncari.core.repositories.SyncariRepo;

public interface FieldDataScoreSnapshotRepo extends SyncariRepo<FieldDataScoreSnapshot> {

    @Query("{ 'entityDefId' : ?0 , 'computedDay' : ?1 }")
    List<FieldDataScoreSnapshot> findByEntityDefIdAndComputedDay(String entityDefId, String computedDay);
}
