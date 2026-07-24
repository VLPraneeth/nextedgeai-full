package com.syncari.core.repositories.customer;

import java.util.List;
import java.util.Optional;
import com.syncari.core.model.abac.AbacPolicy;
import com.syncari.core.model.abac.ResourceType;
import com.syncari.core.repositories.SyncariRepo;

public interface AbacPolicyRepo extends SyncariRepo<AbacPolicy> {
  long countByResourceType(ResourceType type);
  List<AbacPolicy> findByResourceTypeAndResourceId(ResourceType type, String id);
  Optional<AbacPolicy> findByApiName(String apiName);
  List<AbacPolicy> findByResourceType(ResourceType type);
}
