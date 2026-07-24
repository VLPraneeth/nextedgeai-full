package com.syncari.core.abac;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.syncari.core.exceptions.AbacException;
import com.syncari.core.model.abac.AbacAttribute;
import com.syncari.core.model.abac.AbacAttributeValue;
import com.syncari.core.model.abac.ResourceType;
import com.syncari.core.repositories.customer.AbacAttributeRepo;
import com.syncari.core.repositories.customer.AbacAttributeValueRepo;

@Component
public class AbacGlobalResourceService extends AbacResourceService {

  @Autowired
  private AbacService abac;
  @Autowired
  private AbacAttributeValueRepo valueRepo;
  @Autowired
  private AbacAttributeRepo attributeRepo;

  @Override
  public Object checkSingle(AbacContext context, Object data) {
    if (context.getAction() == null) {
      if (context.isThrowException()) {
        throw new AbacException(context.getThrowExceptionMessage());
      }
      return data;
    }
    context.setResourceType(
        context.getResourceType() != null ? context.getResourceType() : ResourceType.GLOBAL);
    context.setResource(context.getResource() != null ? context.getResource()
        : ResourceType.GLOBAL.name().toLowerCase());
    context.setUserAttributes(getUserAttributeValuesAsMap());
    Map<String, Map<String, Object>> resourceAttributes = new HashMap<>();
    context.setResourceAttributes(resourceAttributes);
    resourceAttributes.put("global", getAttributeValuesAsMap(ResourceType.GLOBAL, data));
    var abacRes = abac.check(context);
    boolean allowed = abacRes.getOrDefault("global", false);
    if (context.isThrowException()) {
      if (!allowed) {
        throw new AbacException(context.getThrowExceptionMessage());
      }
      return data;
    }
    return allowed ? data : (data instanceof Optional<?> ? Optional.empty() : null);
  }

  @Override
  public List<Object> checkList(AbacContext context, Iterable<Object> data) {
    throw new UnsupportedOperationException("checkList is not supported");
  }
  
  public Map<String, Object> getAttributeValuesAsMap(ResourceType type, Object def) {
    List<AbacAttribute> attribs = attributeRepo.findByResourceType(type);
    List<AbacAttributeValue> values = valueRepo.findByResourceId("__global__");
    Map<String, Object> ret = new HashMap<String, Object>();
    values.forEach(val -> {
      mapAttribute(type, ret, val, Map.of());
    });
    attribs.forEach(attr -> {
      if (!ret.containsKey(attr.getApiName())) {
        if (List.of("text", "string", "enumeration").contains(attr.getDataType())) {
          ret.put(attr.getApiName(), "");
        }
      }
    });
    return ret;
  }

}
