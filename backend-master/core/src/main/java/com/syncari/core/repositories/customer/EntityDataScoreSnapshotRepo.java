package com.syncari.core.repositories.customer;

import java.util.Optional;

import org.springframework.data.mongodb.repository.Query;

import com.syncari.core.model.EntityDataScoreSnapshot;
import com.syncari.core.repositories.SyncariRepo;

public interface EntityDataScoreSnapshotRepo extends SyncariRepo<EntityDataScoreSnapshot> {
    
    @Query("{ 'entityDefId' : ?0 }")
    Optional<EntityDataScoreSnapshot> findByEntityDefId(String entityDefId);
    
    @Query("{ 'entityDefId' : ?0 , 'computedDay' : ?1 }")
    Optional<EntityDataScoreSnapshot> findByEntityDefIdAndComputedDay(String entityDefId, String computedDay);

}
