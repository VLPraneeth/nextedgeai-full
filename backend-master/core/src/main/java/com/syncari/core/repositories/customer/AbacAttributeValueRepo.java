package com.syncari.core.repositories.customer;

import java.util.List;
import java.util.Optional;
import com.syncari.core.model.abac.AbacAttributeValue;
import com.syncari.core.repositories.SyncariRepo;

public interface AbacAttributeValueRepo extends SyncariRepo<AbacAttributeValue> {
  List<AbacAttributeValue> findByResourceId(String resourceId);
  Optional<AbacAttributeValue> findByResourceIdAndAttributeId(String resourceId, String attributeId);
  long countByAttributeId(String attributeId);
  List<AbacAttributeValue> findByResourceIdIn(List<String> resourceIds);
  long countByResourceId(String resourceId);
}
