package com.syncari.core.abac;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import com.syncari.core.model.abac.AbacAttribute;
import com.syncari.core.model.abac.AbacAttributeValue;
import com.syncari.core.model.abac.AbacPolicy;
import com.syncari.core.model.abac.ResourceType;
import com.syncari.utils.KeyValue;

@Service
public interface AbacService {
  List<AbacResource> listResources();
  List<AbacResource> listResources(ResourceType type);
  List<AbacResource> listResourcesForValues(ResourceType type);
  Optional<AbacResource> getResource(ResourceType type, String resourceId);
  Optional<AbacResource> getResourceForValues(ResourceType type, String resourceId);

  List<AbacAttribute> listAttributes();
  List<AbacAttribute> listAttributes(ResourceType type, String id);
  Optional<AbacAttribute> getAttribute(String id);
  AbacAttribute saveAttribute(AbacAttribute attr);
  void deleteAttribute(String id);

  List<AbacPolicy> listPolicies();
  Optional<AbacPolicy> getPolicy(String id);
  AbacPolicy savePolicy(AbacPolicy policy);
  void deletePolicy(String id);

  List<AbacAttributeValue> listAttributeValues();
  Optional<AbacAttributeValue> getAttributeValue(String id);
  List<AbacAttributeValue> saveAttributeValues(List<AbacAttributeValue> attribs);
  AbacAttributeValue saveAttributeValue(AbacAttributeValue attr);
  void deleteAttributeValue(String id);
  void deleteAttributeValues(List<String> ids);

  Map<String, List<KeyValue>> listAttributeTokens(ResourceType type, String id);

  Map<String, Boolean> check(AbacContext context);
  Object check(AbacContext context, Object data);
}
