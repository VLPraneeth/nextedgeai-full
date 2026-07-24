package com.syncari.core.abac;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.syncari.core.model.abac.AbacAttribute;
import com.syncari.core.model.abac.AbacAttributeValue;
import com.syncari.core.model.abac.AbacPolicy;
import com.syncari.core.model.abac.ResourceType;
import com.syncari.utils.KeyValue;

public class AbacServiceMockImpl implements AbacService {

  @Override
  public List<AbacResource> listResources() {
    return List.of();
  }

  @Override
  public List<AbacResource> listResources(ResourceType type) {
    return List.of();
  }

  @Override
  public List<AbacResource> listResourcesForValues(ResourceType type) {
    return List.of();
  }

  @Override
  public Optional<AbacResource> getResource(ResourceType type, String resourceId) {
    return Optional.empty();
  }

  @Override
  public Optional<AbacResource> getResourceForValues(ResourceType type, String resourceId) {
    return Optional.empty();
  }

  @Override
  public List<AbacAttribute> listAttributes() {
    return List.of();
  }

  @Override
  public List<AbacAttribute> listAttributes(ResourceType type, String id) {
    return List.of();
  }

  @Override
  public Optional<AbacAttribute> getAttribute(String id) {
    return Optional.empty();
  }

  @Override
  public AbacAttribute saveAttribute(AbacAttribute attr) {
    return attr;
  }

  @Override
  public void deleteAttribute(String id) {
    // no-op
  }

  @Override
  public List<AbacPolicy> listPolicies() {
    return List.of();
  }

  @Override
  public Optional<AbacPolicy> getPolicy(String id) {
    return Optional.empty();
  }

  @Override
  public AbacPolicy savePolicy(AbacPolicy policy) {
    return policy;
  }

  @Override
  public void deletePolicy(String id) {
    // no-op
  }

  @Override
  public List<AbacAttributeValue> listAttributeValues() {
    return List.of();
  }

  @Override
  public Optional<AbacAttributeValue> getAttributeValue(String id) {
    return Optional.empty();
  }

  @Override
  public List<AbacAttributeValue> saveAttributeValues(List<AbacAttributeValue> attribs) {
    return attribs;
  }

  @Override
  public AbacAttributeValue saveAttributeValue(AbacAttributeValue attr) {
    return attr;
  }

  @Override
  public void deleteAttributeValue(String id) {
    // no-op
  }

  @Override
  public void deleteAttributeValues(List<String> ids) {
    // no-op
  }

  @Override
  public Map<String, List<KeyValue>> listAttributeTokens(ResourceType type, String id) {
    return Map.of();
  }

  @Override
  public Map<String, Boolean> check(AbacContext context) {
    return Map.of();
  }

  @Override
  public Object check(AbacContext context, Object data) {
    return data;
  }
}