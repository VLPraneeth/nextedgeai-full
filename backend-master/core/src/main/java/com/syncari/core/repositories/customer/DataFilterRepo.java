package com.syncari.core.repositories.customer;

import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.Query;

import com.syncari.core.model.DataFilter;
import com.syncari.core.repositories.SyncariRepo;

public interface DataFilterRepo extends SyncariRepo<DataFilter> {
    @Query("{ 'syncariEntityId' : ?0 , '_id' :{ $in : ?1 } }")
    List<DataFilter> findBySyncariEntityIdAndIdsIn(String syncariEntityId, List<ObjectId> ids);
}
