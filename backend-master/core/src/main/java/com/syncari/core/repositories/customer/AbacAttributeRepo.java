package com.syncari.core.repositories.customer;

import java.util.List;
import java.util.Optional;
import com.syncari.core.model.abac.AbacAttribute;
import com.syncari.core.model.abac.ResourceType;
import com.syncari.core.repositories.SyncariRepo;

public interface AbacAttributeRepo extends SyncariRepo<AbacAttribute> {
  List<AbacAttribute> findByResourceType(ResourceType type);
  List<AbacAttribute> findByResourceTypeAndResourceId(ResourceType type, String id);
  Optional<AbacAttribute> findByResourceTypeAndResourceIdAndApiName(ResourceType type, String id, String apiName);
}
