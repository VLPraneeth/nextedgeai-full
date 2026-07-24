package com.syncari.core.repositories.customer;

import com.syncari.core.model.InstanceConfiguration;
import com.syncari.core.repositories.SyncariRepo;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface InstanceConfigurationRepo extends SyncariRepo<InstanceConfiguration> {
    Optional<InstanceConfiguration> findByKey(String key);

    @Query("{ 'key' : { $in: ?0 } }")
    List<InstanceConfiguration> findByKeyIn(List<String> keys);
}

